package com.wolfcodes.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfRenderFallbackTest {
    @Test
    fun unvalidatedImageLayersKeepTheOriginalStillImageWithoutCameraWarping() {
        assertTrue(OfflineProductionEngine.useSafeStillImageFallback(semanticSegmentationValidated = false))
    }

    @Test
    fun onlySemanticallyValidatedLayersMayEnableDepthAwareMotion() {
        assertFalse(OfflineProductionEngine.useSafeStillImageFallback(semanticSegmentationValidated = true))
    }

    @Test
    fun heuristicColorSegmentationCannotEnableDepthAwareMotion() {
        val candidate = WolfImageDepthSegmenter.analyze(
            width = 20,
            height = 20,
            pixels = IntArray(400) { index ->
                if (index / 20 in 5..14 && index % 20 in 6..13) 0xFFEFEFEF.toInt()
                else 0xFF101010.toInt()
            }
        )
        assertTrue(candidate.heuristicCandidateSeparated)
        assertFalse(candidate.semanticValidationPassed)
        assertTrue(OfflineProductionEngine.useSafeStillImageFallback(candidate.safeForParallax))
    }
}
