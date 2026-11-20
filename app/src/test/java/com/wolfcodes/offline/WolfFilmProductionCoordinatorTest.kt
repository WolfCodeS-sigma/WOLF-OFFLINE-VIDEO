package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfFilmProductionCoordinatorTest {
    @Test fun exposesTheApprovedFiveToThirtyMinutePresets() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30), WolfFilmProductionCoordinator.durationPresetsMinutes)
    }

    @Test fun plansThreeShotsForEverySceneAndKeepsAllFrames() {
        val frames = List(12) { 1_797 }
        val plan = WolfFilmProductionCoordinator.createPlan(frames, 12, 36)

        assertEquals(12, plan.sceneCount)
        assertEquals(36, plan.shotCount)
        assertEquals(frames.sum() + 36, plan.totalFrames)
        assertEquals(frames.sum(), plan.chapters.sumOf { it.bodyFrameCount })
        assertTrue(plan.chapters.all { it.totalFrameCount <= 300 * 12 })
        assertTrue(plan.chapters.flatMap { it.slices }.all { it.shotIndex in 0..2 })
    }

    @Test fun splitShotResumesAtExactOffsetAndPreservesOriginalMotionDuration() {
        val plan = WolfFilmProductionCoordinator.createPlan(listOf(21_600), 12, 0)
        val slices = plan.chapters.flatMap { it.slices }.filter { it.shotIndex == 0 }

        assertEquals(2, slices.size)
        assertEquals(listOf(0, 3_600), slices.map { it.shotStartFrame })
        assertEquals(listOf(3_600, 3_600), slices.map { it.frameCount })
        assertTrue(slices.all { it.shotTotalFrames == 7_200 })
        assertEquals(listOf(0, 3_600), slices.map { it.startFrame })
    }

    @Test fun chaptersRemainSequentialAndIntroAppearsOnlyInFirstChapter() {
        val plan = WolfFilmProductionCoordinator.createPlan(listOf(7_200, 7_200, 7_200), 12, 36)

        assertEquals((1..plan.chapterCount).toList(), plan.chapters.map { it.number })
        assertEquals(36, plan.chapters.sumOf { it.introFrameCount })
        assertTrue(plan.chapters.drop(1).all { it.introFrameCount == 0 })
    }

    @Test fun rejectsMoreThanTwelveScenesAndInvalidChapterLengths() {
        assertTrue(runCatching {
            WolfFilmProductionCoordinator.createPlan(List(13) { 100 }, 12, 0)
        }.isFailure)
        assertTrue(runCatching {
            WolfFilmProductionCoordinator.createPlan(listOf(100), 12, 0, 301)
        }.isFailure)
    }
}
