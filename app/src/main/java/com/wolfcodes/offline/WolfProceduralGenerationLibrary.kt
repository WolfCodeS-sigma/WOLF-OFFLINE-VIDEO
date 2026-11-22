package com.wolfcodes.offline

import android.content.Context

/**
 * Stage 10 cooperation core. It emits renderer-neutral, transient effect
 * descriptors only; it never creates or modifies cinematic source assets.
 */
class WolfProceduralGenerationLibrary internal constructor(
    private val registry: WolfAssetRegistry,
    private val generators: List<WolfBuiltInProceduralGenerator>,
    private val runtimeSnapshotProvider: (() -> WolfGeneratorRuntimeSnapshot)? = null
) {
    constructor(registry: WolfAssetRegistry) : this(registry, WolfBuiltInGeneratorCatalog.generators, null)

    fun generate(request: WolfProceduralGenerationRequest): WolfProceduralGenerationResult {
        if (!isRequestWellFormed(request)) {
            return failed(WolfProceduralFailureCode.INVALID_REQUEST)
        }
        val liveSnapshot = if (runtimeSnapshotProvider != null) {
            runCatching { runtimeSnapshotProvider.invoke() }.getOrNull()
                ?: return failed(WolfProceduralFailureCode.RUNTIME_UNSAFE)
        } else request.runtimeSnapshot
        val effectiveSnapshot = WolfGeneratorRuntimeSnapshot(
            availableMemoryBytes = minOf(request.runtimeSnapshot.availableMemoryBytes, liveSnapshot.availableMemoryBytes),
            freeStorageBytes = minOf(request.runtimeSnapshot.freeStorageBytes, liveSnapshot.freeStorageBytes),
            lowMemory = request.runtimeSnapshot.lowMemory || liveSnapshot.lowMemory,
            thermalStatus = maxOf(request.runtimeSnapshot.thermalStatus, liveSnapshot.thermalStatus)
        )
        if (!effectiveSnapshot.isSafe()) return failed(WolfProceduralFailureCode.RUNTIME_UNSAFE)

        val approvedEntries = registry.approvedAssets(request.assetUseRequest)
        val approvedGeneratorEntries = approvedEntries
            .filter { it.category == WolfAssetCategory.GENERATOR }
            .associateBy { it.assetId }
        val approvedSceneEntries = approvedEntries
            .filter { it.category == WolfAssetCategory.ENVIRONMENT }
            .associateBy { it.assetId }

        val approvedAssets = mutableListOf<WolfApprovedSceneAsset>()
        for (pin in request.sceneAssets) {
            val registered = registry.find(pin.assetId)
                ?: return failed(WolfProceduralFailureCode.SCENE_ASSET_NOT_REGISTERED)
            if (registered.version != pin.version ||
                registered.category != WolfAssetCategory.ENVIRONMENT
            ) {
                return failed(WolfProceduralFailureCode.SCENE_ASSET_NOT_APPROVED)
            }
            val approved = approvedSceneEntries[pin.assetId]
                ?: return failed(WolfProceduralFailureCode.SCENE_ASSET_NOT_APPROVED)
            approvedAssets += WolfApprovedSceneAsset(
                assetId = approved.assetId,
                version = approved.version,
                category = approved.category,
                tags = approved.tags.toSortedSet()
            )
        }
        if (approvedAssets.isEmpty() || approvedAssets.map { it.assetId }.distinct().size != approvedAssets.size) {
            return failed(WolfProceduralFailureCode.INVALID_REQUEST)
        }

        val selectedGenerators = mutableListOf<Pair<WolfBuiltInProceduralGenerator, WolfAssetRegistryEntry>>()
        for (generator in generators) {
            val binding = generator.binding
            val registered = registry.find(binding.assetId)
                ?: return failed(WolfProceduralFailureCode.GENERATOR_NOT_REGISTERED)
            if (registered.category != WolfAssetCategory.GENERATOR) {
                return failed(WolfProceduralFailureCode.GENERATOR_CATEGORY_MISMATCH)
            }
            if (!binding.isWellFormed()) {
                return failed(WolfProceduralFailureCode.GENERATOR_PROFILE_MISMATCH)
            }
            val approved = approvedGeneratorEntries[binding.assetId]
                ?: return failed(WolfProceduralFailureCode.GENERATOR_NOT_APPROVED)
            if (approved.version != binding.generatorVersion ||
                approved.technicalProfile["generatorProfile"] != binding.canonicalProfileJson() ||
                approved.technicalProfile["dependencyProfile"] !=
                    WolfProceduralCanonicalJson.dependencyProfile(approved.dependencies) ||
                approved.dependencies != expectedDependencies(binding)
            ) {
                return failed(WolfProceduralFailureCode.GENERATOR_PROFILE_MISMATCH)
            }
            if (approved.budgetStatus != WolfAssetBudgetStatus.PASS) {
                return failed(WolfProceduralFailureCode.GENERATOR_BUDGET_NOT_PASSED)
            }
            if (!hasValidResourceMeasurements(approved, binding)) {
                return failed(WolfProceduralFailureCode.GENERATOR_RESOURCE_MEASUREMENTS_INVALID)
            }
            if (!requestFitsGenerator(request, binding, effectiveSnapshot)) {
                return failed(WolfProceduralFailureCode.REQUEST_RESOURCE_LIMITS_INVALID)
            }
            selectedGenerators += generator to approved
        }

        val effects = mutableListOf<WolfGeneratedEffect>()
        return try {
            selectedGenerators.forEach { (generator, _) ->
                val binding = generator.binding
                val derivedSeed = WolfSeedDerivation.derive(
                    masterSeed = request.masterSeed,
                    sceneId = request.sceneId,
                    shotId = request.shotId,
                    generatorId = binding.assetId,
                    generatorVersion = binding.generatorVersion,
                    subGeneratorKey = binding.implementationBindingId
                )
                val context = WolfGeneratorExecutionContext(
                    request = request,
                    binding = binding,
                    derivedSeed = derivedSeed,
                    approvedAssets = approvedAssets.toList(),
                    previousEffects = effects.toList()
                )
                val startedAtNanos = System.nanoTime()
                val payload = generator.generate(context)
                val elapsedMillis = ((System.nanoTime() - startedAtNanos).coerceAtLeast(0L)) / 1_000_000L
                val deadlineMillis = minOf(request.resourceEnvelope.maxExecutionMillis, binding.hardLimits.maxExecutionMillis)
                if (elapsedMillis > deadlineMillis) {
                    return failed(WolfProceduralFailureCode.GENERATOR_EXECUTION_LIMIT_EXCEEDED)
                }
                if (!payloadMatchesBinding(payload, binding) || !payloadIsBounded(payload)) {
                    return failed(WolfProceduralFailureCode.GENERATION_FAILED)
                }
                val upstreamDigests = effects.map { it.outputDigest }
                val digest = WolfGeneratorOutputDigest.calculate(
                    binding = binding,
                    request = request,
                    seed = derivedSeed,
                    payload = payload,
                    assets = approvedAssets,
                    upstreamDigests = upstreamDigests
                )
                effects += WolfGeneratedEffect(
                    generatorId = binding.assetId,
                    generatorVersion = binding.generatorVersion,
                    implementationBindingId = binding.implementationBindingId,
                    sceneId = request.sceneId,
                    shotId = request.shotId,
                    frameIndex = request.frameIndex,
                    derivedSeed = derivedSeed,
                    payload = payload,
                    supportingAssets = approvedAssets.toList(),
                    upstreamOutputDigests = upstreamDigests,
                    outputDigest = digest
                )
            }
            WolfProceduralGenerationResult(effects = effects.toList())
        } catch (_: Exception) {
            failed(WolfProceduralFailureCode.GENERATION_FAILED)
        }
    }

    private fun isRequestWellFormed(request: WolfProceduralGenerationRequest): Boolean =
        request.sceneId.isNotBlank() &&
            request.sceneId.length <= 128 &&
            request.shotId.isNotBlank() &&
            request.shotId.length <= 128 &&
            request.frameIndex >= 0 &&
            request.totalFrameCount > 0 &&
            request.frameIndex < request.totalFrameCount &&
            request.frameRateNumerator > 0 &&
            request.frameRateDenominator > 0 &&
            request.assetUseRequest.targetRuntime.isNotBlank() &&
            request.assetUseRequest.targetRuntimeVersion.isNotBlank() &&
            request.assetUseRequest.intendedScope == "environment_background" &&
            request.sceneAssets.isNotEmpty() &&
            request.sceneAssets.size <= 16 &&
            request.parameters.areWithinBounds() &&
            request.resourceEnvelope.isWellFormed()

    private fun requestFitsGenerator(
        request: WolfProceduralGenerationRequest,
        binding: WolfGeneratorBinding,
        runtimeSnapshot: WolfGeneratorRuntimeSnapshot
    ): Boolean {
        val envelope = request.resourceEnvelope
        val limits = binding.hardLimits
        if (envelope.maxWorkingSetBytes > limits.maxWorkingSetBytes ||
            envelope.maxTemporaryStorageBytes > limits.maxTemporaryStorageBytes ||
            envelope.maxReusableOutputBytes > limits.maxReusableOutputBytes ||
            envelope.maxTransientElementsPerFrame > limits.maxTransientElementsPerFrame ||
            envelope.maxRetainedFrameCount > limits.maxRetainedFrameCount ||
            envelope.maxExecutionMillis > limits.maxExecutionMillis
        ) return false

        if (limits.maxNativeGpuBytes != null) {
            val requestedGpu = envelope.maxNativeGpuBytes ?: return false
            if (requestedGpu > limits.maxNativeGpuBytes) return false
        } else if (envelope.maxNativeGpuBytes != null) {
            return false
        }

        val requiredStorage = runCatching {
            Math.addExact(envelope.maxTemporaryStorageBytes, envelope.maxReusableOutputBytes)
        }.getOrNull() ?: return false

        return runtimeSnapshot.availableMemoryBytes >= envelope.maxWorkingSetBytes &&
            runtimeSnapshot.freeStorageBytes >= requiredStorage
    }

    private fun expectedDependencies(binding: WolfGeneratorBinding): Map<String, String> =
        when (binding.family) {
            WolfGeneratorFamily.ATMOSPHERE -> emptyMap()
            WolfGeneratorFamily.WEATHER -> mapOf(WolfBuiltInGeneratorBindings.ATMOSPHERE.assetId to WolfBuiltInGeneratorBindings.ATMOSPHERE.generatorVersion)
            WolfGeneratorFamily.LIGHTING_EFFECT -> mapOf(
                WolfBuiltInGeneratorBindings.ATMOSPHERE.assetId to WolfBuiltInGeneratorBindings.ATMOSPHERE.generatorVersion,
                WolfBuiltInGeneratorBindings.WEATHER.assetId to WolfBuiltInGeneratorBindings.WEATHER.generatorVersion
            )
        }

    private fun payloadMatchesBinding(payload: WolfProceduralEffectPayload, binding: WolfGeneratorBinding): Boolean =
        when (binding.family) {
            WolfGeneratorFamily.ATMOSPHERE -> binding.outputKind == "ATMOSPHERE_LAYER" && payload is WolfAtmosphereLayer
            WolfGeneratorFamily.WEATHER -> binding.outputKind == "WEATHER_LAYER" && payload is WolfWeatherLayer
            WolfGeneratorFamily.LIGHTING_EFFECT -> binding.outputKind == "LIGHTING_LAYER" && payload is WolfLightingLayer
        }

    private fun hasValidResourceMeasurements(
        entry: WolfAssetRegistryEntry,
        binding: WolfGeneratorBinding
    ): Boolean {
        val values = entry.resourceMeasurements
        val evidence = values["measurementEvidenceReference"]?.trim()
        if (evidence.isNullOrBlank() || evidence.uppercase() in setOf("UNKNOWN", "PENDING")) return false
        val measured = listOf(
            "peakWorkingSetBytes" to binding.hardLimits.maxWorkingSetBytes,
            "peakTemporaryStorageBytes" to binding.hardLimits.maxTemporaryStorageBytes,
            "maxReusableOutputBytesObserved" to binding.hardLimits.maxReusableOutputBytes,
            "maxTransientElementsPerFrameObserved" to binding.hardLimits.maxTransientElementsPerFrame,
            "maxRetainedFrameCountObserved" to binding.hardLimits.maxRetainedFrameCount,
            "maxExecutionMillisObserved" to binding.hardLimits.maxExecutionMillis
        )
        for ((key, maximum) in measured) {
            val value = values[key]?.toLongOrNull() ?: return false
            if (value < 0L || value > maximum) return false
        }
        if (binding.hardLimits.maxNativeGpuBytes != null) {
            val gpuBytes = values["peakNativeGpuBytes"]?.toLongOrNull() ?: return false
            if (gpuBytes < 0L || gpuBytes > binding.hardLimits.maxNativeGpuBytes) return false
        }
        return true
    }

    private fun payloadIsBounded(payload: WolfProceduralEffectPayload): Boolean = when (payload) {
        is WolfAtmosphereLayer ->
            payload.densityPermille in 0..1000 && payload.horizontalDriftPermille in 0..100
        is WolfWeatherLayer ->
            payload.rainStrengthPermille in 0..1000 &&
                payload.fogCouplingPermille in 0..125 &&
                payload.slantPermille in 180..280
        is WolfLightingLayer ->
            payload.exposurePermille in 500..1000 &&
                payload.fogScatteringPermille in 0..500 &&
                payload.wetReflectionPermille in 0..333
    }

    private fun failed(code: WolfProceduralFailureCode) =
        WolfProceduralGenerationResult(
            effects = emptyList(),
            failureCode = code,
            warnings = listOf("WOLF procedural generation was isolated; continue with the approved base-scene fallback.")
        )

    companion object {
        fun loadBundled(context: Context): WolfProceduralGenerationLibrary {
            val registry = WolfAssetRegistry.loadBundled(context)
            return WolfProceduralGenerationLibrary(registry, WolfBuiltInGeneratorCatalog.generators) {
                val snapshot = WolfResourceGuard.snapshot(context, context.cacheDir)
                WolfGeneratorRuntimeSnapshot(
                    availableMemoryBytes = Math.multiplyExact(snapshot.availableMemoryMb, 1024L * 1024L),
                    freeStorageBytes = snapshot.freeStorageBytes,
                    lowMemory = snapshot.lowMemory,
                    thermalStatus = snapshot.thermalStatus
                )
            }
        }
    }
}
