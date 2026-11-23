package com.wolfcodes.enginepoc

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies Godot host APIs and a rendered frame from the project bundled in this APK flavor. */
@RunWith(AndroidJUnit4::class)
class GodotCompatibilityTest {
    @Test
    fun androidEmbeddingHostTypesAreAvailable() {
        assertNotNull(Class.forName("org.godotengine.godot.GodotHost"))
        assertNotNull(Class.forName("org.godotengine.godot.GodotFragment"))
        assertNotNull(Class.forName("org.godotengine.godot.GodotActivity"))
    }

    @Test
    fun embeddedGodotProjectDrawsItsExpectedFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, GodotPocActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        var detected = false
        var lastSample = 0
        try {
            val deadline = SystemClock.uptimeMillis() + 20_000L
            while (SystemClock.uptimeMillis() < deadline && !detected) {
                SystemClock.sleep(500L)
                val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: continue
                try {
                    val sample = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    lastSample = sample
                    val red = Color.red(sample)
                    val green = Color.green(sample)
                    val blue = Color.blue(sample)
                    detected = red > 90 && red > green * 1.55 && red > blue * 1.35
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
        assertTrue(
            "Godot project did not render its expected crimson proof region; last ARGB sample=" + lastSample,
            detected
        )
    }
}
