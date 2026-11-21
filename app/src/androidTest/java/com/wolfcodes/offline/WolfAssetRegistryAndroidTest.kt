package com.wolfcodes.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loads a populated manifest and its referenced files through the Android
 * AssetManager belonging to the instrumentation APK. Fixtures never enter the
 * production application's cinematic asset library.
 */
@RunWith(AndroidJUnit4::class)
class WolfAssetRegistryAndroidTest {

    private val testAssets
        get() = InstrumentationRegistry.getInstrumentation().context.assets

    private val request = WolfAssetUseRequest(
        targetRuntime = "WOLF_ANDROID_CANVAS",
        targetRuntimeVersion = "stage08-fixture-1",
        intendedScope = "environment_background"
    )

    @Test
    fun populatedManifestLoadsFromAndroidAssetManagerAndApprovesPinnedDependency() {
        val registry = WolfAssetRegistry.loadFromAssetManager(
            testAssets,
            "fixtures/populated_registry.json"
        )

        assertEquals(2, registry.size)
        assertTrue(registry.evaluate(PRIMARY_ID, request).approved)
        assertEquals(
            setOf(PRIMARY_ID, DEPENDENCY_ID),
            registry.approvedAssets(request).map { it.assetId }.toSet()
        )
    }

    @Test
    fun populatedManifestRejectsCorruptedPackagedFileHash() {
        val root = JSONObject(readPopulatedManifest())
        findAsset(root, PRIMARY_ID).getJSONArray("files").getJSONObject(0)
            .put("sha256", "0000000000000000000000000000000000000000000000000000000000000000")

        val registry = loadModifiedRegistry(root.toString())

        assertFalse(registry.evaluate(PRIMARY_ID, request).approved)
        assertTrue(registry.evaluate(DEPENDENCY_ID, request).approved)
        assertTrue(registry.evaluate(PRIMARY_ID, request).errors.any {
            it.contains("الملف المعبأ")
        })
    }

    @Test
    fun populatedManifestRejectsDependencyVersionMismatch() {
        val root = JSONObject(readPopulatedManifest())
        findAsset(root, PRIMARY_ID).getJSONObject("dependencies")
            .put(DEPENDENCY_ID, "9.9.9")

        val result = loadModifiedRegistry(root.toString()).evaluate(PRIMARY_ID, request)

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("نسخة الاعتمادية") })
    }

    @Test
    fun populatedManifestRejectsMissingDependency() {
        val root = JSONObject(readPopulatedManifest())
        findAsset(root, PRIMARY_ID).getJSONObject("dependencies")
            .put("wolf_fixture_missing", "1.0.0")

        val result = loadModifiedRegistry(root.toString()).evaluate(PRIMARY_ID, request)

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("اعتمادية غير موجودة") })
    }

    private fun readPopulatedManifest(): String =
        testAssets.open("fixtures/populated_registry.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private fun loadModifiedRegistry(json: String): WolfAssetRegistry =
        WolfAssetRegistry.fromJson(
            json,
            WolfAssetFileVerifier(assetReader = { path ->
                runCatching { testAssets.open(path) }.getOrNull()
            })
        )

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
