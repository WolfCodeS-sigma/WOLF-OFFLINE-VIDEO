package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfImageDepthSegmenterTest {

    @Test
    fun segmentationFailureReturnsFallbackAndReportsFailureInsteadOfThrowing() {
        var failureReported = false

        val result = WolfImageDepthSegmenter.attemptSegmentation<String>(
            operation = { throw IllegalStateException("synthetic segmentation failure") },
            onFailure = { failureReported = true }
        )

        assertEquals(null, result)
        assertTrue(failureReported)
    }

    @Test
    fun successfulSegmentationResultIsPreserved() {
        var failureReported = false

        val result = WolfImageDepthSegmenter.attemptSegmentation(
            operation = { "separated-layers" },
            onFailure = { failureReported = true }
        )

        assertEquals("separated-layers", result)
        assertFalse(failureReported)
    }

    @Test
    fun separatesHighContrastForegroundFromBorderConnectedBackground() {
        val width = 40
        val height = 40
        val pixels = IntArray(width * height) { 0xFF101010.toInt() }
        for (y in 8 until 31) {
            for (x in 12 until 28) {
                pixels[y * width + x] = 0xFFF0F0F0.toInt()
            }
        }

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertTrue(result.separated)
        assertTrue(result.confidence > 0f)
        assertTrue(result.midgroundMask[18 * width + 18])
        assertFalse(result.midgroundMask[2 * width + 2])
        assertTrue(result.foregroundMask[18 * width + 18])
        assertFalse(result.foregroundMask[2 * width + 2])
    }

    @Test
    fun assignsDisconnectedForegroundComponentsToDifferentDepthPlanes() {
        val width = 64
        val height = 48
        val pixels = IntArray(width * height) { 0xFF101010.toInt() }
        for (y in 5 until 34) {
            for (x in 7 until 30) {
                pixels[y * width + x] = 0xFFEFEFEF.toInt()
            }
        }
        for (y in 10 until 24) {
            for (x in 42 until 59) {
                pixels[y * width + x] = 0xFFB0B0B0.toInt()
            }
        }

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertTrue(result.separated)
        assertTrue(result.midgroundMask[15 * width + 15])
        assertTrue(result.foregroundPlaneMask[16 * width + 50])
        assertFalse(result.foregroundPlaneMask[15 * width + 15])
    }

    @Test
    fun uniformImageFallsBackWithoutInventingForegroundObjects() {
        val width = 24
        val height = 24
        val pixels = IntArray(width * height) { 0xFF252525.toInt() }

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertFalse(result.separated)
        assertEquals(0f, result.confidence, 0f)
        assertFalse(result.foregroundMask.any { it })
        assertFalse(result.midgroundMask.any { it })
        assertFalse(result.foregroundPlaneMask.any { it })
    }

    @Test
    fun segmentationIsDeterministic() {
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { 0xFF080808.toInt() }
        for (y in 7 until 26) {
            for (x in 9 until 23) {
                pixels[y * width + x] = 0xFFDADADA.toInt()
            }
        }

        val first = WolfImageDepthSegmenter.analyze(width, height, pixels)
        val second = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertEquals(first.foregroundMask.toList(), second.foregroundMask.toList())
        assertEquals(first.midgroundMask.toList(), second.midgroundMask.toList())
        assertEquals(first.foregroundPlaneMask.toList(), second.foregroundPlaneMask.toList())
        assertEquals(first.confidence, second.confidence, 0f)
    }
}
