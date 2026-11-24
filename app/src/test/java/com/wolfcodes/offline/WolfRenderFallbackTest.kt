package com.wolfcodes.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfRenderFallbackTest {
    @Test
    fun missingDepthLayersUseTheOriginalStillImageWithoutCameraWarping() {
        assertTrue(OfflineProductionEngine.useSafeStillImageFallback(depthLayersSeparated = false))
    }

    @Test
    fun validDepthLayersKeepTheDepthAwareMotionPathAvailable() {
        assertFalse(OfflineProductionEngine.useSafeStillImageFallback(depthLayersSeparated = true))
    }
}
