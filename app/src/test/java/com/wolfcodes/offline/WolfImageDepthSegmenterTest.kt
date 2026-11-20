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

    @Test
    fun robustBorderModelSeparatesSubjectWithMildGradientAndBorderClutter() {
        val width = 96
        val height = 72
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val shade = (18 + x / 8 + y / 12).coerceIn(0, 255)
            (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
        // A high-contrast subject in the middle.
        for (y in 18 until 58) for (x in 32 until 65) {
            pixels[y * width + x] = 0xFFE8E8E8.toInt()
        }
        // A small amount of unrelated edge clutter must not dominate the median model.
        for (x in 0 until width step 13) pixels[x] = 0xFF707070.toInt()
        for (y in 0 until height step 11) pixels[y * width] = 0xFF555555.toInt()

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertTrue(result.separated)
        assertTrue(result.midgroundMask[35 * width + 48])
        assertFalse(result.foregroundMask[4 * width + 4])
        assertTrue(result.confidence >= 0.12f)
    }

    @Test
    fun transparentPixelsInsideAnEnclosedRegionAreNotInventedAsForeground() {
        val width = 40
        val height = 40
        val pixels = IntArray(width * height) { 0xFF101010.toInt() }
        for (y in 8 until 32) for (x in 8 until 32) {
            pixels[y * width + x] = 0xFFF0F0F0.toInt()
        }
        for (y in 18 until 22) for (x in 18 until 22) {
            pixels[y * width + x] = 0x00101010
        }

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertTrue(result.separated)
        assertFalse(result.foregroundMask[19 * width + 19])
        assertFalse(result.midgroundMask[19 * width + 19])
        assertFalse(result.foregroundPlaneMask[19 * width + 19])
    }

    @Test
    fun tinyDisconnectedNoiseDoesNotBecomeAnIndependentForegroundPlane() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { 0xFF101010.toInt() }
        for (y in 12 until 52) for (x in 16 until 48) {
            pixels[y * width + x] = 0xFFEFEFEF.toInt()
        }
        pixels[5 * width + 5] = 0xFFFFFFFF.toInt()
        pixels[6 * width + 6] = 0xFFFFFFFF.toInt()

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertTrue(result.separated)
        assertTrue(result.midgroundMask[30 * width + 30])
        assertFalse(result.foregroundPlaneMask[5 * width + 5])
        assertFalse(result.foregroundPlaneMask[6 * width + 6])
    }

    @Test
    fun analysisRejectsImagesWhereForegroundWouldConsumeAlmostTheWholeFrame() {
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        for (x in 0 until width) {
            pixels[x] = 0xFF101010.toInt()
            pixels[(height - 1) * width + x] = 0xFF101010.toInt()
        }
        for (y in 0 until height) {
            pixels[y * width] = 0xFF101010.toInt()
            pixels[y * width + width - 1] = 0xFF101010.toInt()
        }

        val result = WolfImageDepthSegmenter.analyze(width, height, pixels)

        assertFalse(result.separated)
        assertEquals(0f, result.confidence, 0f)
    }
}
