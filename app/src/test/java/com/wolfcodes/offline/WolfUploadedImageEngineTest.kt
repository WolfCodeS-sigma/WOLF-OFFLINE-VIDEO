package com.wolfcodes.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfUploadedImageEngineTest {
    @Test
    fun depthLayersRequireBothSeparationAndMinimumConfidence() {
        assertTrue(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.30f))
        assertTrue(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.82f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(false, 0.99f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.29f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(false, 0f))
    }
}
