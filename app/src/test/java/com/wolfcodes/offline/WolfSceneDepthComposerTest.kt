package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneDepthComposerTest {

    @Test
    fun depthPlanesHaveIncreasingParallaxStrength() {
        val background = WolfSceneDepthComposer.parallaxFactor(WolfSceneDepthComposer.Plane.BACKGROUND)
        val midground = WolfSceneDepthComposer.parallaxFactor(WolfSceneDepthComposer.Plane.MIDGROUND)
        val foreground = WolfSceneDepthComposer.parallaxFactor(WolfSceneDepthComposer.Plane.FOREGROUND)

        assertTrue(background > 0f)
        assertTrue(background < midground)
        assertTrue(midground < foreground)
        assertEquals(1.0f, foreground, 0f)
    }

    @Test
    fun depthPlanesHaveDistinctPerspectiveScales() {
        val background = WolfSceneDepthComposer.depthScale(0.5f, WolfSceneDepthComposer.Plane.BACKGROUND)
        val midground = WolfSceneDepthComposer.depthScale(0.5f, WolfSceneDepthComposer.Plane.MIDGROUND)
        val foreground = WolfSceneDepthComposer.depthScale(0.5f, WolfSceneDepthComposer.Plane.FOREGROUND)

        assertTrue(background > 1f)
        assertTrue(background < midground)
        assertTrue(midground < foreground)
    }

    @Test
    fun depthOffsetsAreDeterministicForTheSameFrame() {
        val first = WolfSceneDepthComposer.depthOffset(
            720, 1280, 0.42f, 3, WolfSceneDepthComposer.Plane.FOREGROUND
        )
        val second = WolfSceneDepthComposer.depthOffset(
            720, 1280, 0.42f, 3, WolfSceneDepthComposer.Plane.FOREGROUND
        )

        assertEquals(first.first, second.first, 0f)
        assertEquals(first.second, second.second, 0f)
    }

    @Test
    fun depthOffsetsDifferBetweenPlanes() {
        val background = WolfSceneDepthComposer.depthOffset(
            720, 1280, 0.42f, 3, WolfSceneDepthComposer.Plane.BACKGROUND
        )
        val foreground = WolfSceneDepthComposer.depthOffset(
            720, 1280, 0.42f, 3, WolfSceneDepthComposer.Plane.FOREGROUND
        )

        assertNotEquals(background, foreground)
        assertTrue(kotlin.math.abs(foreground.first) >= kotlin.math.abs(background.first))
        assertTrue(kotlin.math.abs(foreground.second) >= kotlin.math.abs(background.second))
    }

    @Test
    fun depthScaleRemainsWithinSafeOverscanRange() {
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (plane in WolfSceneDepthComposer.Plane.values()) {
                val scale = WolfSceneDepthComposer.depthScale(progress, plane)
                assertTrue(scale >= 1.018f)
                assertTrue(scale <= 1.077f)
            }
        }
    }
}
