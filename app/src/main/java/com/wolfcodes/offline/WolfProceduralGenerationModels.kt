package com.wolfcodes.offline

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class WolfGeneratorFamily {
    ATMOSPHERE,
    WEATHER,
    LIGHTING_EFFECT
}

data class WolfGeneratorHardLimits(
    val maxWorkingSetBytes: Long,
    val maxTemporaryStorageBytes: Long,
    val maxReusableOutputBytes: Long,
    val maxTransientElementsPerFrame: Long,
    val maxRetainedFrameCount: Long,
    val maxExecutionMillis: Long,
    val maxNativeGpuBytes: Long? = null
) {
    fun asCanonicalJson(): String {
        val values = linkedMapOf(
            "maxExecutionMillis" to maxExecutionMillis,
            "maxRetainedFrameCount" to maxRetainedFrameCount,
            "maxReusableOutputBytes" to maxReusableOutputBytes,
            "maxTemporaryStorageBytes" to maxTemporaryStorageBytes,
            "maxTransientElementsPerFrame" to maxTransientElementsPerFrame,
            "maxWorkingSetBytes" to maxWorkingSetBytes
        )
        maxNativeGpuBytes?.let { values["maxNativeGpuBytes"] = it }
        return WolfProceduralCanonicalJson.objectOf(values.mapValues { it.value.toString() })
    }

    fun isWellFormed(): Boolean {
        val required = listOf(
            maxWorkingSetBytes,
            maxTemporaryStorageBytes,
            maxReusableOutputBytes,
            maxTransientElementsPerFrame,
            maxRetainedFrameCount,
            maxExecutionMillis
        )
        return required.all { it > 0L } && (maxNativeGpuBytes == null || maxNativeGpuBytes > 0L)
    }
}

data class WolfGeneratorBinding(
    val assetId: String,
    val generatorVersion: String,
    val family: WolfGeneratorFamily,
    val parameterSchemaId: String,
    val parameterSchemaVersion: String,
    val outputKind: String,
    val implementationBindingId: String,
    val implementationVersion: String,
    val abiVersion: String,
    val seedAlgorithmId: String,
    val sourceRepositoryPath: String,
    val sourceRevision: String,
    val sourceSha256: String,
    val hardLimits: WolfGeneratorHardLimits
) {
    fun canonicalProfileJson(): String {
        val values = linkedMapOf(
            "abiVersion" to WolfProceduralCanonicalJson.quote(abiVersion),
            "family" to WolfProceduralCanonicalJson.quote(family.name),
            "generatorId" to WolfProceduralCanonicalJson.quote(assetId),
            "generatorVersion" to WolfProceduralCanonicalJson.quote(generatorVersion),
            "hardLimits" to hardLimits.asCanonicalJson(),
            "implementationBindingId" to WolfProceduralCanonicalJson.quote(implementationBindingId),
            "implementationVersion" to WolfProceduralCanonicalJson.quote(implementationVersion),
            "outputKind" to WolfProceduralCanonicalJson.quote(outputKind),
            "parameterSchemaId" to WolfProceduralCanonicalJson.quote(parameterSchemaId),
            "parameterSchemaVersion" to WolfProceduralCanonicalJson.quote(parameterSchemaVersion),
            "seedAlgorithmId" to WolfProceduralCanonicalJson.quote(seedAlgorithmId),
            "sourceRepositoryPath" to WolfProceduralCanonicalJson.quote(sourceRepositoryPath),
            "sourceRevision" to WolfProceduralCanonicalJson.quote(sourceRevision),
            "sourceSha256" to WolfProceduralCanonicalJson.quote(sourceSha256)
        )
        return WolfProceduralCanonicalJson.objectOf(values)
    }

    fun isWellFormed(): Boolean =
        assetId.matches(Regex("wolf_[a-z0-9][a-z0-9._-]{2,63}")) &&
            generatorVersion.isNotBlank() &&
            parameterSchemaId.isNotBlank() &&
            parameterSchemaVersion.isNotBlank() &&
            outputKind.isNotBlank() &&
            implementationBindingId.isNotBlank() &&
            implementationVersion.isNotBlank() &&
            abiVersion.isNotBlank() &&
            seedAlgorithmId == WolfSeedDerivation.ALGORITHM_ID &&
            sourceRepositoryPath.startsWith("app/src/main/java/") &&
            Regex("[0-9a-f]{40}").matches(sourceRevision) &&
            Regex("[0-9a-f]{64}").matches(sourceSha256) &&
            hardLimits.isWellFormed()
}

data class WolfGeneratorResourceEnvelope(
    val maxWorkingSetBytes: Long,
    val maxTemporaryStorageBytes: Long,
    val maxReusableOutputBytes: Long,
    val maxTransientElementsPerFrame: Long,
    val maxRetainedFrameCount: Long,
    val maxExecutionMillis: Long,
    val maxNativeGpuBytes: Long? = null
) {
    fun isWellFormed(): Boolean {
        val required = listOf(
            maxWorkingSetBytes,
            maxTemporaryStorageBytes,
            maxReusableOutputBytes,
            maxTransientElementsPerFrame,
            maxRetainedFrameCount,
            maxExecutionMillis
        )
        return required.all { it > 0L } && (maxNativeGpuBytes == null || maxNativeGpuBytes > 0L)
    }
}

data class WolfGeneratorRuntimeSnapshot(
    val availableMemoryBytes: Long,
    val freeStorageBytes: Long,
    val lowMemory: Boolean,
    val thermalStatus: Int
) {
    fun isSafe(): Boolean =
        availableMemoryBytes > 0L &&
            freeStorageBytes >= 0L &&
            !lowMemory &&
            thermalStatus in 0..2
}

data class WolfSceneAssetPin(
    val assetId: String,
    val version: String
)

data class WolfProceduralSceneParameters(
    val fogStrengthPermille: Int,
    val rainStrengthPermille: Int,
    val lightingBiasPermille: Int
) {
    fun areWithinBounds(): Boolean =
        fogStrengthPermille in 0..1000 &&
            rainStrengthPermille in 0..1000 &&
            lightingBiasPermille in 0..1000
}

data class WolfProceduralGenerationRequest(
    val assetUseRequest: WolfAssetUseRequest,
    val sceneId: String,
    val shotId: String,
    val frameIndex: Int,
    val totalFrameCount: Int,
    val frameRateNumerator: Int,
    val frameRateDenominator: Int,
    val masterSeed: Long,
    val sceneAssets: List<WolfSceneAssetPin>,
    val parameters: WolfProceduralSceneParameters,
    val resourceEnvelope: WolfGeneratorResourceEnvelope,
    val runtimeSnapshot: WolfGeneratorRuntimeSnapshot
)

data class WolfApprovedSceneAsset(
    val assetId: String,
    val version: String,
    val category: WolfAssetCategory,
    val tags: Set<String>
)

sealed interface WolfProceduralEffectPayload {
    fun canonicalFields(): List<String>
}

data class WolfAtmosphereLayer(
    val densityPermille: Int,
    val horizontalDriftPermille: Int
) : WolfProceduralEffectPayload {
    override fun canonicalFields(): List<String> =
        listOf("ATMOSPHERE", densityPermille.toString(), horizontalDriftPermille.toString())
}

data class WolfWeatherLayer(
    val rainStrengthPermille: Int,
    val fogCouplingPermille: Int,
    val slantPermille: Int
) : WolfProceduralEffectPayload {
    override fun canonicalFields(): List<String> =
        listOf(
            "WEATHER",
            rainStrengthPermille.toString(),
            fogCouplingPermille.toString(),
            slantPermille.toString()
        )
}

data class WolfLightingLayer(
    val exposurePermille: Int,
    val fogScatteringPermille: Int,
    val wetReflectionPermille: Int
) : WolfProceduralEffectPayload {
    override fun canonicalFields(): List<String> =
        listOf(
            "LIGHTING_EFFECT",
            exposurePermille.toString(),
            fogScatteringPermille.toString(),
            wetReflectionPermille.toString()
        )
}

data class WolfGeneratedEffect(
    val generatorId: String,
    val generatorVersion: String,
    val implementationBindingId: String,
    val sceneId: String,
    val shotId: String,
    val frameIndex: Int,
    val derivedSeed: Long,
    val payload: WolfProceduralEffectPayload,
    val supportingAssets: List<WolfApprovedSceneAsset>,
    val upstreamOutputDigests: List<String>,
    val outputDigest: String
)

enum class WolfProceduralFailureCode {
    INVALID_REQUEST,
    GENERATOR_NOT_REGISTERED,
    GENERATOR_CATEGORY_MISMATCH,
    GENERATOR_NOT_APPROVED,
    GENERATOR_PROFILE_MISMATCH,
    GENERATOR_BUDGET_NOT_PASSED,
    GENERATOR_RESOURCE_MEASUREMENTS_INVALID,
    GENERATOR_EXECUTION_LIMIT_EXCEEDED,
    REQUEST_RESOURCE_LIMITS_INVALID,
    RUNTIME_UNSAFE,
    SCENE_ASSET_NOT_REGISTERED,
    SCENE_ASSET_NOT_APPROVED,
    GENERATION_FAILED
}

enum class WolfProceduralFallbackMode {
    NONE,
    BASE_SCENE_NO_EFFECT
}

data class WolfProceduralGenerationResult(
    val effects: List<WolfGeneratedEffect>,
    val failureCode: WolfProceduralFailureCode? = null,
    val warnings: List<String> = emptyList()
) {
    val succeeded: Boolean get() = failureCode == null
    val fallbackMode: WolfProceduralFallbackMode
        get() = if (succeeded) WolfProceduralFallbackMode.NONE
        else WolfProceduralFallbackMode.BASE_SCENE_NO_EFFECT
}

internal data class WolfGeneratorExecutionContext(
    val request: WolfProceduralGenerationRequest,
    val binding: WolfGeneratorBinding,
    val derivedSeed: Long,
    val approvedAssets: List<WolfApprovedSceneAsset>,
    val previousEffects: List<WolfGeneratedEffect>
)

internal interface WolfBuiltInProceduralGenerator {
    val binding: WolfGeneratorBinding
    fun generate(context: WolfGeneratorExecutionContext): WolfProceduralEffectPayload
}

internal object WolfProceduralCanonicalJson {
    fun quote(value: String): String {
        val output = StringBuilder(value.length + 2)
        output.append('"')
        value.forEach { character ->
            when (character) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000c' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        output.append("\\u")
                        output.append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        output.append(character)
                    }
                }
            }
        }
        output.append('"')
        return output.toString()
    }

    fun objectOf(encodedValues: Map<String, String>): String =
        encodedValues.toSortedMap().entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}"
        ) { quote(it.key) + ":" + it.value }

    fun dependencyProfile(dependencies: Map<String, String>): String =
        objectOf(dependencies.mapValues { quote(it.value) })
}

internal object WolfSeedDerivation {
    const val ALGORITHM_ID = "WOLF-SEED-1"

    fun derive(
        masterSeed: Long,
        sceneId: String,
        shotId: String,
        generatorId: String,
        generatorVersion: String,
        subGeneratorKey: String
    ): Long {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeLong(masterSeed)
            listOf(sceneId, shotId, generatorId, generatorVersion, subGeneratorKey).forEach { value ->
                val encoded = value.toByteArray(StandardCharsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        return ByteBuffer.wrap(digest, 0, java.lang.Long.BYTES).long
    }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

internal object WolfGeneratorOutputDigest {
    fun calculate(
        binding: WolfGeneratorBinding,
        request: WolfProceduralGenerationRequest,
        seed: Long,
        payload: WolfProceduralEffectPayload,
        assets: List<WolfApprovedSceneAsset>,
        upstreamDigests: List<String>
    ): String {
        val fields = mutableListOf(
            binding.assetId,
            binding.generatorVersion,
            binding.implementationBindingId,
            request.sceneId,
            request.shotId,
            request.frameIndex.toString(),
            request.totalFrameCount.toString(),
            request.frameRateNumerator.toString(),
            request.frameRateDenominator.toString(),
            java.lang.Long.toUnsignedString(seed)
        )
        fields += payload.canonicalFields()
        fields += assets.sortedBy { it.assetId }.flatMap {
            listOf(it.assetId, it.version, it.category.name, it.tags.sorted().joinToString(","))
        }
        fields += upstreamDigests
        return WolfSeedDerivation.sha256Hex(fields.joinToString("\n"))
    }
}
