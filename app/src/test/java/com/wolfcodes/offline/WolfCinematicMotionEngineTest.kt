package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfCinematicMotionEngineTest {
    @Test
    fun poseIsDeterministicForSameShotAndProgress() {
        val first = WolfCinematicMotionEngine.samplePose(0.42f, 3, 1)
        val repeated = WolfCinematicMotionEngine.samplePose(0.42f, 3, 1)
        assertEquals(first, repeated)
    }

    @Test
    fun poseChannelsStayWithinDesignedBounds() {
        for (index in 0..100) {
            val pose = WolfCinematicMotionEngine.samplePose(index / 100f, 2, 0)
            assertTrue(pose.figureSway in -2.81f..2.81f)
            assertTrue(pose.figureBob in -0.013f..0.013f)
            assertTrue(pose.armSwing in -7.51f..7.51f)
            assertTrue(pose.doorAngle in -18f..0f)
            assertTrue(pose.curtainWave in -0.076f..0.076f)
            assertTrue(pose.branchAngle in -3.41f..3.41f)
            assertTrue(pose.shadowAlpha in 0f..0.461f)
        }
    }

    @Test
    fun progressIsClampedAtBothEnds() {
        assertEquals(
            WolfCinematicMotionEngine.samplePose(0f, 1, 2),
            WolfCinematicMotionEngine.samplePose(-10f, 1, 2)
        )
        assertEquals(
            WolfCinematicMotionEngine.samplePose(1f, 1, 2),
            WolfCinematicMotionEngine.samplePose(10f, 1, 2)
        )
    }

    @Test
    fun captionDetectionSelectsIndependentRigAndProps() {
        val features = WolfCinematicMotionEngine.featuresFor("ظهر شبح عند باب قديم والستائر تتحرك قرب الأشجار")
        assertTrue(features.figure)
        assertTrue(features.ghost)
        assertTrue(features.door)
        assertTrue(features.curtain)
        assertTrue(features.branches)
    }

    @Test
    fun ordinaryCaptionDoesNotInventAnimatedProps() {
        val features = WolfCinematicMotionEngine.featuresFor("غرفة هادئة وفارغة")
        assertFalse(features.figure)
        assertFalse(features.door)
        assertFalse(features.curtain)
        assertFalse(features.branches)
    }
}
