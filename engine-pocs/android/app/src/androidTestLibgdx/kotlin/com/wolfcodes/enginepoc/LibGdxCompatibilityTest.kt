package com.wolfcodes.enginepoc

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibGdxCompatibilityTest {
    @Test
    fun androidBackendStartsAndRendersAtLeastOneGlFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        LibGdxPocSignal.reset()
        val intent = Intent(context, LibGdxPocActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as LibGdxPocActivity
        try {
            assertTrue(
                "libGDX Android backend did not render a GL frame within 20 seconds",
                LibGdxPocSignal.awaitFirstFrame(20, TimeUnit.SECONDS)
            )
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
