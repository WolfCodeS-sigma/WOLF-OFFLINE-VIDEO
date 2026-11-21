package com.wolfcodes.offline

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WolfProceduralGenerationLibraryTest {
    private lateinit var root: File
    private lateinit var verifier: WolfAssetFileVerifier
    private lateinit var environmentFile: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("wolf-stage10-cooperation").toFile()
        environmentFile = File(root, "environment/approved-fixture.bin").apply {
            parentFile?.mkdirs()
            writeText("WOLF STAGE 10 TEST FIXTURE ONLY; NOT A CINEMATIC ASSET")
        }
        verifier = WolfAssetFileVerifier(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun approvedGeneratorsCooperateWithAnApprovedEnvironmentAsset() {
        val registry = createRegistry()
        val environmentBefore = environmentFile.readBytes()

        val result = stage10Library(registry).generate(request())

        assertTrue(result.succeeded)
        assertEquals(WolfProceduralFallbackMode.NONE, result.fallbackMode)
        assertEquals(
            listOf(
                WolfBuiltInGeneratorBindings.ATMOSPHERE.assetId,
                WolfBuiltInGeneratorBindings.WEATHER.assetId,
                WolfBuiltInGeneratorBindings.LIGHTING.assetId
            ),
            result.effects.map { it.generatorId }
        )
        assertEquals(3, result.effects.size)
        result.effects.forEach { effect ->
            assertEquals(listOf(ENVIRONMENT_ID), effect.supportingAssets.map { it.assetId })
            assertEquals(setOf("realistic", "environment", "fixture-only"), effect.supportingAssets.single().tags)
            assertTrue(Regex("[a-f0-9]{64}").matches(effect.outputDigest))
        }

        val atmosphere = result.effects[0]
        val weather = result.effects[1]
        val lighting = result.effects[2]
        val atmospherePayload = atmosphere.payload as WolfAtmosphereLayer
        val weatherPayload = weather.payload as WolfWeatherLayer
        val lightingPayload = lighting.payload as WolfLightingLayer

        assertEquals(atmosphere.outputDigest, weather.upstreamOutputDigests.single())
        assertEquals(
            listOf(atmosphere.outputDigest, weather.outputDigest),
            lighting.upstreamOutputDigests
        )
        assertEquals(atmospherePayload.densityPermille / 8, weatherPayload.fogCouplingPermille)
        assertEquals(weatherPayload.rainStrengthPermille / 3, lightingPayload.wetReflectionPermille)
        assertEquals(environmentBefore.toList(), environmentFile.readBytes().toList())
        assertFalse(result.effects.any { it.payload.toString().contains("CAMERA_MOTION") })
    }

    @Test
    fun identicalSeedAndFrameIdentityRegenerateTheSameEffects() {
        val library = stage10Library(createRegistry())
        val first = library.generate(request())
        val repeated = library.generate(request())

        assertTrue(first.succeeded)
        assertEquals(first.effects, repeated.effects)

        val recoveredFrameRequest = request().copy(frameIndex = 47)
        val recoveredFirst = library.generate(recoveredFrameRequest)
        val recoveredAgain = library.generate(recoveredFrameRequest)
        assertEquals(recoveredFirst.effects, recoveredAgain.effects)
        assertEquals(47, recoveredFirst.effects.first().frameIndex)
    }

    @Test
    fun differentMasterSeedsChangeDeterministicProceduralOutput() {
        val library = stage10Library(createRegistry())
        val first = library.generate(request(seed = 7L))
        val second = library.generate(request(seed = 987654321L))

        assertTrue(first.succeeded)
        assertTrue(second.succeeded)
        assertNotEquals(
            first.effects.map { it.outputDigest },
            second.effects.map { it.outputDigest }
        )
    }

    @Test
    fun emptyGeneratorSetFailsClosedAndKeepsTheBaseSceneFallback() {
        val result = stage10Library(
            createRegistry(includeGenerators = false)
        ).generate(request())

        assertFalse(result.succeeded)
        assertEquals(WolfProceduralFailureCode.GENERATOR_NOT_REGISTERED, result.failureCode)
        assertEquals(WolfProceduralFallbackMode.BASE_SCENE_NO_EFFECT, result.fallbackMode)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun nonGeneratorRegistryEntryCannotBeExecutedAsGeneratorCode() {
        val id = WolfBuiltInGeneratorBindings.ATMOSPHERE.assetId
        val result = stage10Library(
            createRegistry(categoryOverrideId = id)
        ).generate(request())

        assertFalse(result.succeeded)
        assertEquals(WolfProceduralFailureCode.GENERATOR_CATEGORY_MISMATCH, result.failureCode)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun stage08MayApprovePendingBudgetButWpglStillBlocksProductionExecution() {
        val id = WolfBuiltInGeneratorBindings.WEATHER.assetId
        val registry = createRegistry(pendingGeneratorId = id)
        assertTrue(registry.evaluate(id, request().assetUseRequest).approved)

        val result = stage10Library(registry).generate(request())

        assertFalse(result.succeeded)
        assertEquals(WolfProceduralFailureCode.GENERATOR_BUDGET_NOT_PASSED, result.failureCode)
    }

    @Test
    fun profileMismatchCannotAuthorizeAnotherBuiltInImplementation() {
        val id = WolfBuiltInGeneratorBindings.ATMOSPHERE.assetId
        val registry = createRegistry(profileOverrideId = id)
        assertTrue(registry.evaluate(id, request().assetUseRequest).approved)

        val result = stage10Library(registry).generate(request())

        assertFalse(result.succeeded)
        assertEquals(WolfProceduralFailureCode.GENERATOR_PROFILE_MISMATCH, result.failureCode)
    }

    @Test
    fun resourceMeasurementAbovePinnedLimitBlocksGeneration() {
        val id = WolfBuiltInGeneratorBindings.LIGHTING.assetId
        val registry = createRegistry(
            measurementOverrideId = id,
            measurementOverride = "peakWorkingSetBytes" to "999999999"
        )
        assertTrue(registry.evaluate(id, request().assetUseRequest).approved)

        val result = stage10Library(registry).generate(request())

        assertFalse(result.succeeded)
        assertEquals(
            WolfProceduralFailureCode.GENERATOR_RESOURCE_MEASUREMENTS_INVALID,
            result.failureCode
        )
    }

    @Test
    fun missingOrPendingMeasurementEvidenceBlocksGeneration() {
        val id = WolfBuiltInGeneratorBindings.WEATHER.assetId
        val registry = createRegistry(
            measurementOverrideId = id,
            measurementOverride = "measurementEvidenceReference" to "PENDING"
        )

        val result = stage10Library(registry).generate(request())

        assertFalse(result.succeeded)
        assertEquals(
            WolfProceduralFailureCode.GENERATOR_RESOURCE_MEASUREMENTS_INVALID,
            result.failureCode
        )
    }

    @Test
    fun callerResourceEnvelopeCannotExceedReviewedGeneratorLimits() {
        val oversized = request().copy(
            resourceEnvelope = request().resourceEnvelope.copy(maxWorkingSetBytes = 999999L)
        )

        val result = stage10Library(createRegistry()).generate(oversized)

        assertFalse(result.succeeded)
        assertEquals(WolfProceduralFailureCode.REQUEST_RESOURCE_LIMITS_INVALID, result.failureCode)
    }

    @Test
    fun unsafeRuntimeSnapshotBlocksBeforeAnyGeneratorRuns() {
        val unsafe = request().copy(
            runtimeSnapshot = WolfGeneratorRuntimeSnapshot(
                availableMemoryBytes = 1024L * 1024L,
                freeStorageBytes = 1024L * 1024L,
                lowMemory = false,
                thermalStatus = 3
            )
        )

        val result = stage10Library(createRegistry()).generate(unsafe)

        assertFalse(result.succeeded)
        assertEquals(WolfProceduralFailureCode.RUNTIME_UNSAFE, result.failureCode)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun unapprovedOrVersionMismatchedSceneAssetCannotBeUsed() {
        val unapprovedRegistry = createRegistry(environmentTags = setOf("cartoon", "environment"))
        val unapproved = stage10Library(unapprovedRegistry).generate(request())
        assertFalse(unapproved.succeeded)
        assertEquals(WolfProceduralFailureCode.SCENE_ASSET_NOT_APPROVED, unapproved.failureCode)

        val wrongVersion = stage10Library(createRegistry())
            .generate(request().copy(sceneAssets = listOf(WolfSceneAssetPin(ENVIRONMENT_ID, "9.9.9"))))
        assertFalse(wrongVersion.succeeded)
        assertEquals(WolfProceduralFailureCode.SCENE_ASSET_NOT_APPROVED, wrongVersion.failureCode)
    }

    @Test
    fun generatorStagesRequireTheirUpstreamOutputs() {
        val req = request()
        val assets = listOf(
            WolfApprovedSceneAsset(
                assetId = ENVIRONMENT_ID,
                version = "1.0.0",
                category = WolfAssetCategory.ENVIRONMENT,
                tags = setOf("environment")
            )
        )
        val weatherContext = WolfGeneratorExecutionContext(
            request = req,
            binding = WolfBuiltInGeneratorBindings.WEATHER,
            derivedSeed = 1L,
            approvedAssets = assets,
            previousEffects = emptyList()
        )
        val lightingContext = weatherContext.copy(
            binding = WolfBuiltInGeneratorBindings.LIGHTING
        )

        assertTrue(runCatching { WolfWeatherProceduralGenerator.generate(weatherContext) }.isFailure)
        assertTrue(runCatching { WolfLightingProceduralGenerator.generate(lightingContext) }.isFailure)
    }

    @Test
    fun staticCatalogContainsOnlyPinnedInAppEnvironmentalGenerators() {
        assertEquals(
            listOf(
                "wolf.binding.atmosphere.fog.v1",
                "wolf.binding.weather.rain.v1",
                "wolf.binding.lighting.interplay.v1"
            ),
            WolfBuiltInGeneratorCatalog.bindings.map { it.implementationBindingId }
        )
        assertTrue(WolfBuiltInGeneratorCatalog.bindings.all { !it.isWellFormed() })
        assertTrue(WolfBuiltInGeneratorCatalog.bindings.all { !it.hardLimits.isWellFormed() })
        assertFalse(
            WolfBuiltInGeneratorCatalog.bindings.any {
                it.family == WolfGeneratorFamily.valueOf("ATMOSPHERE") &&
                    it.assetId.contains("camera")
            }
        )
    }

    @Test
    fun pinnedSourceBlobRevisionAndSha256MatchTheCompiledGeneratorSources() {
        testBindings().forEach { binding ->
            val relativePath = binding.sourceRepositoryPath.removePrefix("app/")
            val workingDirectory = File(System.getProperty("user.dir") ?: ".")
            val candidates = listOf(
                File(workingDirectory, relativePath),
                File(workingDirectory, binding.sourceRepositoryPath)
            )
            val source = candidates.firstOrNull { it.isFile }
            assertNotNull("Generator source file not found for " + binding.assetId, source)
            val bytes = source!!.readBytes()
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(binding.sourceSha256, sha256)

            val blobHeader = ("blob " + bytes.size + "\u0000")
                .toByteArray(StandardCharsets.UTF_8)
            val blobContent = blobHeader + bytes
            val blobSha = MessageDigest.getInstance("SHA-1").digest(blobContent)
                .joinToString("") { "%02x".format(it) }
            assertEquals(binding.sourceRevision, blobSha)
        }
    }

    private fun createRegistry(
        includeGenerators: Boolean = true,
        pendingGeneratorId: String? = null,
        profileOverrideId: String? = null,
        categoryOverrideId: String? = null,
        measurementOverrideId: String? = null,
        measurementOverride: Pair<String, String>? = null,
        environmentTags: Set<String> = setOf("realistic", "environment", "fixture-only")
    ): WolfAssetRegistry {
        val assets = JSONArray()
        if (includeGenerators) {
            testBindings().forEach { binding ->
                val file = generatorDescriptorFile(binding)
                val profile = if (binding.assetId == profileOverrideId) {
                    binding.canonicalProfileJson() + " "
                } else {
                    binding.canonicalProfileJson()
                }
                val category = if (binding.assetId == categoryOverrideId) {
                    WolfAssetCategory.ENVIRONMENT
                } else {
                    WolfAssetCategory.GENERATOR
                }
                val measurements = defaultMeasurements().toMutableMap()
                if (binding.assetId == measurementOverrideId && measurementOverride != null) {
                    measurements[measurementOverride.first] = measurementOverride.second
                }
                assets.put(
                    entryJson(
                        assetId = binding.assetId,
                        name = "Stage 10 generator fixture " + binding.family.name,
                        version = binding.generatorVersion,
                        category = category,
                        tags = setOf("procedural", "generator-fixture"),
                        file = file,
                        technicalProfile = buildMap {
                            put("coordinateSystem", "WOLF renderer-neutral descriptor")
                            put("units", "permille and bytes")
                            put("importSettings", "Static test binding metadata")
                            put("rendererRequirements", "WOLF_ANDROID_CANVAS")
                            put("generatorProfile", profile)
                            put("dependencyProfile", "{}")
                            if (category == WolfAssetCategory.ENVIRONMENT) {
                                put("geometryProfile", "Test-only environment metadata")
                                put("materialProfile", "Test-only environment metadata")
                            }
                        },
                        resourceMeasurements = measurements,
                        budgetStatus = if (binding.assetId == pendingGeneratorId) {
                            WolfAssetBudgetStatus.PENDING
                        } else {
                            WolfAssetBudgetStatus.PASS
                        }
                    )
                )
            }
        }

        val environment = environmentFileEntry(environmentTags)
        assets.put(environment)
        val manifest = JSONObject()
            .put("schemaVersion", WolfAssetRegistry.CURRENT_SCHEMA_VERSION)
            .put("assets", assets)
        return WolfAssetRegistry.fromJson(manifest.toString(), verifier)
    }

    private fun generatorDescriptorFile(binding: WolfGeneratorBinding): WolfAssetFile {
        val file = File(root, "generators/" + binding.assetId + ".descriptor.json")
        file.parentFile?.mkdirs()
        file.writeText(binding.canonicalProfileJson())
        return WolfAssetFile(
            relativePath = "generators/" + binding.assetId + ".descriptor.json",
            format = "application/json",
            byteSize = file.length(),
            sha256 = WolfAssetFileVerifier.sha256(file)
        )
    }

    private fun environmentFileEntry(tags: Set<String>): JSONObject {
        val file = WolfAssetFile(
            relativePath = "environment/approved-fixture.bin",
            format = "application/octet-stream",
            byteSize = environmentFile.length(),
            sha256 = WolfAssetFileVerifier.sha256(environmentFile)
        )
        return entryJson(
            assetId = ENVIRONMENT_ID,
            name = "Approved environment test fixture",
            version = "1.0.0",
            category = WolfAssetCategory.ENVIRONMENT,
            tags = tags,
            file = file,
            technicalProfile = mapOf(
                "coordinateSystem" to "WOLF Android Canvas 2D",
                "units" to "pixels",
                "importSettings" to "Read-only test fixture",
                "rendererRequirements" to "WOLF_ANDROID_CANVAS",
                "geometryProfile" to "Test-only static environment metadata",
                "materialProfile" to "Test-only static environment metadata"
            ),
            resourceMeasurements = emptyMap(),
            budgetStatus = WolfAssetBudgetStatus.PENDING
        )
    }

    private fun entryJson(
        assetId: String,
        name: String,
        version: String,
        category: WolfAssetCategory,
        tags: Set<String>,
        file: WolfAssetFile,
        technicalProfile: Map<String, String>,
        resourceMeasurements: Map<String, String>,
        budgetStatus: WolfAssetBudgetStatus
    ): JSONObject {
        val now = System.currentTimeMillis() - 1000L
        val license = JSONObject()
            .put("creatorOrRightsHolder", "WOLF Stage 10 test fixture")
            .put("sourceName", "Local synthetic test fixture")
            .put("sourceLocation", "offline:stage10-test-fixture")
            .put("licenseId", "WOLF-STAGE10-TEST-ONLY")
            .put("licenseName", "Test-only metadata fixture")
            .put("licenseVersion", "1")
            .put("evidenceReference", "test-only:license-evidence")
            .put("verified", true)
            .put("commercialUse", true)
            .put("modificationAllowed", true)
            .put("redistributionAllowed", true)
            .put("appBundlingAllowed", true)
            .put("attributionRequired", false)
            .put("attributionText", "")
            .put("restrictions", "Test-only; not a production asset or license grant")
            .put("acquiredAtEpochMillis", now)
            .put("acquisitionMethod", "Generated only for automated tests")
            .put("provenanceRecordReference", "test-only:provenance")
        val review = JSONObject()
            .put("reviewer", "WOLF Stage 10 automated test")
            .put("reviewedAtEpochMillis", now)
            .put("decisionReason", "Synthetic fixture validates software gate integration only")
            .put("reviewEvidenceReference", "test-only:review-evidence")
            .put("rightsPassed", true)
            .put("visualPassed", true)
            .put("technicalPassed", true)
            .put("realismScore", 5)
            .put("budgetPendingAccepted", true)
            .put("visualStyle", WolfAssetVisualStyle.CINEMATIC_REALISTIC.name)
            .put("approvedStyleScopes", JSONArray())
        val fileJson = JSONObject()
            .put("relativePath", file.relativePath)
            .put("format", file.format)
            .put("byteSize", file.byteSize)
            .put("sha256", file.sha256)
        val profileJson = JSONObject()
        technicalProfile.forEach { (key, value) -> profileJson.put(key, value) }
        val measurementsJson = JSONObject()
        resourceMeasurements.forEach { (key, value) -> measurementsJson.put(key, value) }

        return JSONObject()
            .put("assetId", assetId)
            .put("name", name)
            .put("version", version)
            .put("category", category.name)
            .put("tags", JSONArray().apply { tags.forEach { put(it) } })
            .put("lifecycle", WolfAssetLifecycle.APPROVED.name)
            .put("license", license)
            .put("files", JSONArray().put(fileJson))
            .put("dependencies", JSONObject())
            .put("technicalProfile", profileJson)
            .put("resourceMeasurements", measurementsJson)
            .put("compatibleRuntimes", JSONObject().put("WOLF_ANDROID_CANVAS", "stage10-test-1"))
            .put("approvedScopes", JSONArray().put("environment_background"))
            .put("budgetStatus", budgetStatus.name)
            .put("review", review)
    }

    private fun defaultMeasurements(): Map<String, String> = mapOf(
        "peakWorkingSetBytes" to "8192",
        "peakTemporaryStorageBytes" to "256",
        "maxReusableOutputBytesObserved" to "0",
        "maxTransientElementsPerFrameObserved" to "1",
        "maxRetainedFrameCountObserved" to "1",
        "maxExecutionMillisObserved" to "12",
        "measurementEvidenceReference" to "test-only:measured-resource-fixture"
    )

    private fun request(seed: Long = 12345L) = WolfProceduralGenerationRequest(
        assetUseRequest = WolfAssetUseRequest(
            targetRuntime = "WOLF_ANDROID_CANVAS",
            targetRuntimeVersion = "stage10-test-1",
            intendedScope = "environment_background"
        ),
        sceneId = "scene-stage10-001",
        shotId = "shot-stage10-001",
        frameIndex = 24,
        totalFrameCount = 100,
        frameRateNumerator = 24,
        frameRateDenominator = 1,
        masterSeed = seed,
        sceneAssets = listOf(WolfSceneAssetPin(ENVIRONMENT_ID, "1.0.0")),
        parameters = WolfProceduralSceneParameters(
            fogStrengthPermille = 620,
            rainStrengthPermille = 430,
            lightingBiasPermille = 260
        ),
        resourceEnvelope = WolfGeneratorResourceEnvelope(
            maxWorkingSetBytes = 65536L,
            maxTemporaryStorageBytes = 8192L,
            maxReusableOutputBytes = 4096L,
            maxTransientElementsPerFrame = 8L,
            maxRetainedFrameCount = 1L,
            maxExecutionMillis = 100L
        ),
        runtimeSnapshot = WolfGeneratorRuntimeSnapshot(
            availableMemoryBytes = 131072L,
            freeStorageBytes = 32768L,
            lowMemory = false,
            thermalStatus = 0
        )
    )

    private fun testBindings(): List<WolfGeneratorBinding> = WolfBuiltInGeneratorCatalog.bindings.map {
        it.copy(hardLimits = TEST_ONLY_LIMITS)
    }

    private fun testGenerators(): List<WolfBuiltInProceduralGenerator> {
        val bindings = testBindings()
        return WolfBuiltInGeneratorCatalog.generators.mapIndexed { index, delegate ->
            object : WolfBuiltInProceduralGenerator {
                override val binding: WolfGeneratorBinding = bindings[index]
                override fun generate(context: WolfGeneratorExecutionContext): WolfProceduralEffectPayload =
                    delegate.generate(context.copy(binding = binding))
            }
        }
    }

    private fun stage10Library(registry: WolfAssetRegistry) =
        WolfProceduralGenerationLibrary(registry, testGenerators())

    private companion object {
        // Synthetic ceilings used only by automated fixtures, never by the production catalog.
        val TEST_ONLY_LIMITS = WolfGeneratorHardLimits(
            maxWorkingSetBytes = 131072L,
            maxTemporaryStorageBytes = 65536L,
            maxReusableOutputBytes = 16384L,
            maxTransientElementsPerFrame = 64L,
            maxRetainedFrameCount = 1L,
            maxExecutionMillis = 250L
        )
        const val ENVIRONMENT_ID = "wolf_stage10_environment_fixture"
    }
}
