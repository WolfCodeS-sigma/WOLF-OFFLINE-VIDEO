package com.wolfcodes.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises a populated registry through Android's real AssetManager stream
 * path. All binary fixtures live in the instrumentation APK, never in the
 * production application's cinematic asset library.
 */
@RunWith(AndroidJUnit4::class)
class WolfAssetRegistryAndroidTest {

    private val request = WolfAssetUseRequest(
        targetRuntime = "WOLF_ANDROID_CANVAS",
        targetRuntimeVersion = "stage08-fixture-1",
        intendedScope = "environment_background"
    )

    @Test
    fun populatedManifestLoadsAndApprovesVerifiedAssetsWithPinnedDependency() {
        val registry = loadRegistry(manifestJson())

        assertEquals(2, registry.size)
        assertTrue(registry.evaluate(PRIMARY_ID, request).approved)
        assertEquals(
            setOf(PRIMARY_ID, DEPENDENCY_ID),
            registry.approvedAssets(request).map { it.assetId }.toSet()
        )
    }

    @Test
    fun populatedManifestRejectsCorruptedPackagedFileHash() {
        val root = JSONObject(manifestJson())
        val primary = findAsset(root, PRIMARY_ID)
        primary.getJSONArray("files").getJSONObject(0)
            .put("sha256", "0000000000000000000000000000000000000000000000000000000000000000")

        val registry = loadRegistry(root.toString())

        assertFalse(registry.evaluate(PRIMARY_ID, request).approved)
        assertTrue(registry.evaluate(DEPENDENCY_ID, request).approved)
        assertTrue(
            registry.evaluate(PRIMARY_ID, request).errors.any {
                it.contains("الملف المعبأ")
            }
        )
    }

    @Test
    fun populatedManifestRejectsDependencyVersionMismatch() {
        val root = JSONObject(manifestJson())
        findAsset(root, PRIMARY_ID).getJSONObject("dependencies")
            .put(DEPENDENCY_ID, "9.9.9")

        val registry = loadRegistry(root.toString())
        val result = registry.evaluate(PRIMARY_ID, request)

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("نسخة الاعتمادية") })
    }

    @Test
    fun populatedManifestRejectsMissingDependency() {
        val root = JSONObject(manifestJson())
        findAsset(root, PRIMARY_ID).getJSONObject("dependencies")
            .put("wolf_fixture_missing", "1.0.0")

        val registry = loadRegistry(root.toString())
        val result = registry.evaluate(PRIMARY_ID, request)

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("اعتمادية غير موجودة") })
    }

    private fun loadRegistry(json: String): WolfAssetRegistry {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        return WolfAssetRegistry.fromJson(
            json,
            WolfAssetFileVerifier(assetReader = { path ->
                runCatching { testAssets.open(path) }.getOrNull()
            })
        )
    }

    private fun manifestJson(): String {
        val primaryDependencies = JSONObject().put(DEPENDENCY_ID, "1.0.0")
        val assets = JSONArray()
            .put(
                assetEntry(
                    assetId = PRIMARY_ID,
                    name = "Android integration primary environment fixture",
                    relativePath = "fixtures/primary.bin",
                    byteSize = 41L,
                    sha256 = "16dfdd1fe6fdb3a4fc90d93491c4bb228c8d1cff4d38a4ff82c7f58eb3994ad2",
                    dependencies = primaryDependencies
                )
            )
            .put(
                assetEntry(
                    assetId = DEPENDENCY_ID,
                    name = "Android integration dependency environment fixture",
                    relativePath = "fixtures/dependency.bin",
                    byteSize = 44L,
                    sha256 = "cae08d4beb1c90dccddf768c3d01e8dc963fcaa9c0f42eacd259f1d29dfb1c61",
                    dependencies = JSONObject()
                )
            )

        return JSONObject()
            .put("schemaVersion", WolfAssetRegistry.CURRENT_SCHEMA_VERSION)
            .put("assets", assets)
            .toString()
    }

    private fun assetEntry(
        assetId: String,
        name: String,
        relativePath: String,
        byteSize: Long,
        sha256: String,
        dependencies: JSONObject
    ): JSONObject {
        val now = System.currentTimeMillis() - 1_000L
        val license = JSONObject()
            .put("creatorOrRightsHolder", "WOLF Android integration test fixture")
            .put("sourceName", "Local instrumentation fixture")
            .put("sourceLocation", "offline:android-test-fixture")
            .put("licenseId", "WOLF-TEST-LICENSE-001")
            .put("licenseName", "Test-only fixture license")
            .put("licenseVersion", "1.0")
            .put("evidenceReference", "test-only:license-evidence")
            .put("verified", true)
            .put("commercialUse", true)
            .put("modificationAllowed", true)
            .put("redistributionAllowed", true)
            .put("appBundlingAllowed", true)
            .put("attributionRequired", false)
            .put("attributionText", "")
            .put("restrictions", "Test-only; not a production asset")
            .put("acquiredAtEpochMillis", now)
            .put("acquisitionMethod", "Generated as a deterministic instrumentation fixture")
            .put("provenanceRecordReference", "test-only:provenance")
        val review = JSONObject()
            .put("reviewer", "WOLF Android integration test")
            .put("reviewedAtEpochMillis", now)
            .put("decisionReason", "Fixture validates registry integration only")
            .put("reviewEvidenceReference", "test-only:review-evidence")
            .put("rightsPassed", true)
            .put("visualPassed", true)
            .put("technicalPassed", true)
            .put("realismScore", 5)
            .put("budgetPendingAccepted", true)
            .put("visualStyle", "CINEMATIC_REALISTIC")
            .put("approvedStyleScopes", JSONArray())
        val file = JSONObject()
            .put("relativePath", relativePath)
            .put("format", "application/octet-stream")
            .put("byteSize", byteSize)
            .put("sha256", sha256)
        val technicalProfile = JSONObject()
            .put("coordinateSystem", "Android Canvas 2D")
            .put("units", "pixels")
            .put("importSettings", "Read-only instrumentation fixture")
            .put("rendererRequirements", "WOLF_ANDROID_CANVAS")
            .put("geometryProfile", "Static fixture data")
            .put("materialProfile", "Not applicable to registry integration fixture")
        return JSONObject()
            .put("assetId", assetId)
            .put("name", name)
            .put("version", "1.0.0")
            .put("category", "ENVIRONMENT")
            .put("tags", JSONArray().put("realistic").put("environment").put("integration-fixture"))
            .put("lifecycle", "APPROVED")
            .put("license", license)
            .put("files", JSONArray().put(file))
            .put("dependencies", dependencies)
            .put("technicalProfile", technicalProfile)
            .put("resourceMeasurements", JSONObject())
            .put("compatibleRuntimes", JSONObject().put("WOLF_ANDROID_CANVAS", "stage08-fixture-1"))
            .put("approvedScopes", JSONArray().put("environment_background"))
            .put("budgetStatus", "PENDING")
            .put("review", review)
    }

    private fun findAsset(root: JSONObject, assetId: String): JSONObject {
        val assets = root.getJSONArray("assets")
        for (index in 0 until assets.length()) {
            val item = assets.getJSONObject(index)
            if (item.getString("assetId") == assetId) return item
        }
        throw AssertionError("Fixture asset not found: " + assetId)
    }

    private companion object {
        const val PRIMARY_ID = "wolf_fixture_primary"
        const val DEPENDENCY_ID = "wolf_fixture_dependency"
    }
}
