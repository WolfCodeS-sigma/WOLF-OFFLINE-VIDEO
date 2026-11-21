package com.wolfcodes.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses only the existing instrumentation-only binary fixtures. They are not
 * visual/cinematic assets and are not included in the production application.
 */
@RunWith(AndroidJUnit4::class)
class WolfProceduralGenerationAndroidTest {

    @Test
    fun staticallyLinkedGeneratorsCooperateThroughAndroidAssetManagerQualityGate() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val registry = WolfAssetRegistry.fromJson(
            fixtureManifest().toString(),
            WolfAssetFileVerifier(assetReader = { path ->
                runCatching { testAssets.open(path) }.getOrNull()
            })
        )
        val request = WolfProceduralGenerationRequest(
            assetUseRequest = WolfAssetUseRequest(
                targetRuntime = "WOLF_ANDROID_CANVAS",
                targetRuntimeVersion = "stage10-instrumented-1",
                intendedScope = "environment_background"
            ),
            sceneId = "android-stage10-scene",
            shotId = "android-stage10-shot",
            frameIndex = 31,
            totalFrameCount = 120,
            frameRateNumerator = 24,
            frameRateDenominator = 1,
            masterSeed = 554433L,
            sceneAssets = listOf(WolfSceneAssetPin(ENVIRONMENT_ID, "1.0.0")),
            parameters = WolfProceduralSceneParameters(580, 390, 210),
            resourceEnvelope = WolfGeneratorResourceEnvelope(
                maxWorkingSetBytes = 65536L,
                maxTemporaryStorageBytes = 8192L,
                maxReusableOutputBytes = 4096L,
                maxTransientElementsPerFrame = 8L,
                maxRetainedFrameCount = 1L,
                maxExecutionMillis = 100L
            ),
            runtimeSnapshot = WolfGeneratorRuntimeSnapshot(
                availableMemoryBytes = 512L * 1024L * 1024L,
                freeStorageBytes = 8L * 1024L * 1024L,
                lowMemory = false,
                thermalStatus = 0
            )
        )

        assertEquals(3, registry.approvedAssets(request.assetUseRequest)
            .count { it.category == WolfAssetCategory.GENERATOR })
        val result = WolfProceduralGenerationLibrary(registry).generate(request)

        assertTrue("Android AssetManager-backed Stage 08 gate should approve test fixtures", result.succeeded)
        assertEquals(3, result.effects.size)
        assertEquals(ENVIRONMENT_ID, result.effects.last().supportingAssets.single().assetId)
        assertEquals(
            result.effects.take(2).map { it.outputDigest },
            result.effects.last().upstreamOutputDigests
        )
    }

    private fun fixtureManifest(): JSONObject {
        val assets = JSONArray()
        WolfBuiltInGeneratorCatalog.bindings.forEach { binding ->
            assets.put(
                entry(
                    assetId = binding.assetId,
                    name = "Android Stage 10 generator metadata fixture",
                    version = binding.generatorVersion,
                    category = WolfAssetCategory.GENERATOR,
                    tags = listOf("procedural", "generator-fixture"),
                    relativePath = "fixtures/dependency.bin",
                    fileSize = 44L,
                    fileSha256 = DEPENDENCY_SHA256,
                    technicalProfile = mapOf(
                        "coordinateSystem" to "WOLF renderer-neutral descriptor",
                        "units" to "permille and bytes",
                        "importSettings" to "Static test binding metadata",
                        "rendererRequirements" to "WOLF_ANDROID_CANVAS",
                        "generatorProfile" to binding.canonicalProfileJson(),
                        "dependencyProfile" to "{}"
                    ),
                    budgetStatus = WolfAssetBudgetStatus.PASS
                )
            )
        }
        assets.put(
            entry(
                assetId = ENVIRONMENT_ID,
                name = "Android Stage 10 environment metadata fixture",
                version = "1.0.0",
                category = WolfAssetCategory.ENVIRONMENT,
                tags = listOf("realistic", "environment", "integration-fixture"),
                relativePath = "fixtures/primary.bin",
                fileSize = 41L,
                fileSha256 = PRIMARY_SHA256,
                technicalProfile = mapOf(
                    "coordinateSystem" to "Android Canvas 2D",
                    "units" to "pixels",
                    "importSettings" to "Read-only instrumentation fixture",
                    "rendererRequirements" to "WOLF_ANDROID_CANVAS",
                    "geometryProfile" to "Static test fixture metadata",
                    "materialProfile" to "Static test fixture metadata"
                ),
                budgetStatus = WolfAssetBudgetStatus.PENDING
            )
        )
        return JSONObject()
            .put("schemaVersion", WolfAssetRegistry.CURRENT_SCHEMA_VERSION)
            .put("assets", assets)
    }

    private fun entry(
        assetId: String,
        name: String,
        version: String,
        category: WolfAssetCategory,
        tags: List<String>,
        relativePath: String,
        fileSize: Long,
        fileSha256: String,
        technicalProfile: Map<String, String>,
        budgetStatus: WolfAssetBudgetStatus
    ): JSONObject {
        val now = System.currentTimeMillis() - 1000L
        val license = JSONObject()
            .put("creatorOrRightsHolder", "WOLF Stage 10 Android test fixture")
            .put("sourceName", "Instrumentation-only synthetic fixture")
            .put("sourceLocation", "offline:android-stage10-fixture")
            .put("licenseId", "WOLF-STAGE10-TEST-ONLY")
            .put("licenseName", "Test-only fixture metadata")
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
            .put("acquisitionMethod", "Generated only for instrumentation tests")
            .put("provenanceRecordReference", "test-only:provenance")
        val review = JSONObject()
            .put("reviewer", "WOLF Stage 10 Android automated test")
            .put("reviewedAtEpochMillis", now)
            .put("decisionReason", "Synthetic fixture validates Android registry and generator cooperation only")
            .put("reviewEvidenceReference", "test-only:review-evidence")
            .put("rightsPassed", true)
            .put("visualPassed", true)
            .put("technicalPassed", true)
            .put("realismScore", 5)
            .put("budgetPendingAccepted", true)
            .put("visualStyle", WolfAssetVisualStyle.CINEMATIC_REALISTIC.name)
            .put("approvedStyleScopes", JSONArray())
        val profile = JSONObject()
        technicalProfile.forEach { (key, value) -> profile.put(key, value) }
        val measurements = if (category == WolfAssetCategory.GENERATOR) {
            JSONObject()
                .put("peakWorkingSetBytes", "8192")
                .put("peakTemporaryStorageBytes", "256")
                .put("maxReusableOutputBytesObserved", "0")
                .put("maxTransientElementsPerFrameObserved", "1")
                .put("maxRetainedFrameCountObserved", "1")
                .put("maxExecutionMillisObserved", "12")
                .put("measurementEvidenceReference", "test-only:resource-measurement")
        } else {
            JSONObject()
        }
        val file = JSONObject()
            .put("relativePath", relativePath)
            .put("format", "application/octet-stream")
            .put("byteSize", fileSize)
            .put("sha256", fileSha256)
        return JSONObject()
            .put("assetId", assetId)
            .put("name", name)
            .put("version", version)
            .put("category", category.name)
            .put("tags", JSONArray(tags))
            .put("lifecycle", WolfAssetLifecycle.APPROVED.name)
            .put("license", license)
            .put("files", JSONArray().put(file))
            .put("dependencies", JSONObject())
            .put("technicalProfile", profile)
            .put("resourceMeasurements", measurements)
            .put("compatibleRuntimes", JSONObject().put("WOLF_ANDROID_CANVAS", "stage10-instrumented-1"))
            .put("approvedScopes", JSONArray().put("environment_background"))
            .put("budgetStatus", budgetStatus.name)
            .put("review", review)
    }

    private companion object {
        const val ENVIRONMENT_ID = "wolf_stage10_android_environment_fixture"
        const val PRIMARY_SHA256 = "16dfdd1fe6fdb3a4fc90d93491c4bb228c8d1cff4d38a4ff82c7f58eb3994ad2"
        const val DEPENDENCY_SHA256 = "cae08d4beb1c90dccddf768c3d01e8dc963fcaa9c0f42eacd259f1d29dfb1c61"
    }
}
