package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMotionEngineTest {
    @Test
    fun frameAllocationPreservesExactDurationAndMinimumPerScene() {
        val scenes = listOf("الغرفة ساكنة والنافذة مغلقة", "ظهر شبح فجأة خلف الباب", "هرب الرجل في ممر طويل")
        val frames = OfflineMotionEngine.allocateFrames(scenes, totalFrames = 12 * 18, minimumFramesPerScene = 36)

        assertEquals(12 * 18, frames.sum())
        assertEquals(scenes.size, frames.size)
        assertTrue(frames.all { it >= 36 })
    }

    @Test
    fun revealReceivesMoreTimeThanQuietAtmosphere() {
        val scenes = listOf(
            "كانت الغرفة ساكنة والنافذة مغلقة",
            "ظهر شبح فجأة خلف الباب وانكشف وجهه"
        )
        val frames = OfflineMotionEngine.allocateFrames(scenes, totalFrames = 12 * 12, minimumFramesPerScene = 36)

        assertTrue("The reveal should receive a longer beat", frames[1] > frames[0])
        assertEquals(12 * 12, frames.sum())
    }

    @Test
    fun motionProfilesDistinguishSuspenseRevealAndChase() {
        val suspense = OfflineMotionEngine.profile("الغرفة ساكنة")
        val reveal = OfflineMotionEngine.profile("ظهر شبح فجأة")
        val chase = OfflineMotionEngine.profile("هرب الرجل وبدأت المطاردة")

        assertEquals(OfflineMotionEngine.Movement.SUSPENSE, suspense.movement)
        assertEquals(OfflineMotionEngine.Movement.REVEAL, reveal.movement)
        assertEquals(OfflineMotionEngine.Movement.CHASE, chase.movement)
        assertTrue(chase.shakeStrength > suspense.shakeStrength)
        assertTrue(reveal.pacingWeight > suspense.pacingWeight)
    }

    @Test
    fun allocationIsDeterministicForSameStory() {
        val scenes = listOf("طرق على الباب", "ظهر ظل خلف الستارة")
        val first = OfflineMotionEngine.allocateFrames(scenes, 240, 36)
        val second = OfflineMotionEngine.allocateFrames(scenes, 240, 36)

        assertEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun allocationRejectsFrameBudgetBelowMinimum() {
        OfflineMotionEngine.allocateFrames(
            captions = listOf("مشهد أول", "مشهد ثان"),
            totalFrames = 60,
            minimumFramesPerScene = 36
        )
    }
    @Test
    fun cameraMovesVaryAcrossShotsAndAdjacentScenes() {
        val caption = "الغرفة ساكنة ثم ظهر ظل خلف الباب"
        val moves = (0..2).map { shot ->
            OfflineMotionEngine.cameraFrame(0.65f, 0, caption, shot)
        } + (0..2).map { shot ->
            OfflineMotionEngine.cameraFrame(0.65f, 1, caption, shot)
        }

        val distinctOffsets = moves.map { (it.offsetX * 10000).toInt() to (it.offsetY * 10000).toInt() }.distinct()
        assertTrue("Camera compositions should vary between shots and scenes", distinctOffsets.size >= 4)
    }

    @Test
    fun cameraFramesStayInsideSafeMotionBounds() {
        val captions = listOf(
            "الغرفة ساكنة والنافذة مغلقة",
            "ظهر شبح فجأة خلف الباب",
            "هرب الرجل وبدأت المطاردة"
        )
        for (caption in captions) {
            for (scene in 0..11) {
                for (shot in 0..2) {
                    for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                        val frame = OfflineMotionEngine.cameraFrame(progress, scene, caption, shot)
                        assertTrue(frame.zoom in -0.012f..0.090f)
                        assertTrue(frame.offsetX in -0.12f..0.12f)
                        assertTrue(frame.offsetY in -0.10f..0.10f)
                        assertTrue(frame.rotationDegrees in -0.65f..0.65f)
                    }
                }
            }
        }
    }

    @Test
    fun cameraFrameIsDeterministicForSameInputs() {
        val first = OfflineMotionEngine.cameraFrame(0.4f, 3, "مطاردة في الممر", 2)
        val second = OfflineMotionEngine.cameraFrame(0.4f, 3, "مطاردة في الممر", 2)
        assertEquals(first, second)
    }

}
