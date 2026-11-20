package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Wolf25DCinematicImageEngineTest {
    @Test
    fun cameraPoseIsDeterministicAndBoundedAcrossDepthPlanes() {
        val move = WolfUploadedImageEngine.CameraMove.SLOW_PUSH
        val back = Wolf25DCinematicImageEngine.samplePose(move, 2, 0, 0.5f, 0.12f, 0.8f)
        val front = Wolf25DCinematicImageEngine.samplePose(move, 2, 0, 0.5f, 1f, 0.8f)
        val repeated = Wolf25DCinematicImageEngine.samplePose(move, 2, 0, 0.5f, 1f, 0.8f)

        assertTrue(back.zoom > 1f)
        assertTrue(front.depthParallax > back.depthParallax)
        assertEquals(front, repeated)
        assertTrue(front.rotationDegrees in -1f..1f)
        assertTrue(front.breathing in 0.99f..1.01f)
    }

    @Test
    fun progressIsClampedAndCameraMovesRemainFinite() {
        WolfUploadedImageEngine.CameraMove.values().forEach { move ->
            val before = Wolf25DCinematicImageEngine.samplePose(move, 0, 0, -4f, 0.5f, 1f)
            val after = Wolf25DCinematicImageEngine.samplePose(move, 0, 0, 4f, 0.5f, 1f)
            assertTrue(before.zoom.isFinite() && before.panX.isFinite())
            assertTrue(after.zoom.isFinite() && after.panY.isFinite())
            assertTrue(before.zoom > 0f && after.zoom > 0f)
        }
    }

    @Test
    fun cameraMotionDiffersByShotIdentity() {
        val first = Wolf25DCinematicImageEngine.samplePose(
            WolfUploadedImageEngine.CameraMove.HANDHELD, 3, 0, 0.4f, 0.8f, 1f
        )
        val second = Wolf25DCinematicImageEngine.samplePose(
            WolfUploadedImageEngine.CameraMove.HANDHELD, 3, 1, 0.4f, 0.8f, 1f
        )
        assertTrue(first.panX != second.panX || first.panY != second.panY)
    }
}
