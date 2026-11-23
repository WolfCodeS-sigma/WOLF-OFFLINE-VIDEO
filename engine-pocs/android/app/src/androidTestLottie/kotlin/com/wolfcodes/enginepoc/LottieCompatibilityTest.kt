package com.wolfcodes.enginepoc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LottieCompatibilityTest {
    @Test
    fun localJsonCompositionParsesAndRasterizesWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = context.assets.open("wolf_poc_motion.json")
            .bufferedReader()
            .use { it.readText() }
        val result = LottieCompositionFactory.fromJsonStringSync(json, "wolf-offline-poc")
        assertNotNull("Lottie failed to parse the bundled local animation", result.value)
        val composition = requireNotNull(result.value)
        val drawable = LottieDrawable().apply {
            setComposition(composition)
            bounds = Rect(0, 0, 160, 160)
            progress = 0.5f
        }
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))
        val hasRenderedPixel = (0 until bitmap.height).any { y ->
            (0 until bitmap.width).any { x -> (bitmap.getPixel(x, y) ushr 24) != 0 }
        }
        assertTrue("Lottie parsed the JSON but did not rasterize the local shape", hasRenderedPixel)
        bitmap.recycle()
        drawable.clearComposition()
    }
}
