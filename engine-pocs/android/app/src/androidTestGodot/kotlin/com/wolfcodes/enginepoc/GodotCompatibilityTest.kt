package com.wolfcodes.enginepoc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the pinned Android library AAR and its documented host API are
 * present in an Android test APK. This is an API-linkage POC only: it does
 * not claim that a Godot game project has been exported or rendered.
 */
@RunWith(AndroidJUnit4::class)
class GodotCompatibilityTest {
    @Test
    fun androidEmbeddingHostTypesAreAvailable() {
        assertNotNull(Class.forName("org.godotengine.godot.GodotHost"))
        assertNotNull(Class.forName("org.godotengine.godot.GodotFragment"))
        assertNotNull(Class.forName("org.godotengine.godot.GodotActivity"))
    }
}
