package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfFilmProductionCoordinatorTest {
    @Test fun exposesTheApprovedFiveToThirtyMinutePresets() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30), WolfFilmProductionCoordinator.durationPresetsMinutes)
    }

    @Test fun keepsIntroInsideFiveAndThirtyMinuteDurationPresets() {
        assertEquals(297, WolfFilmProductionCoordinator.bodyDurationSeconds(300, 3, 12))
        assertEquals(1_797, WolfFilmProductionCoordinator.bodyDurationSeconds(1_800, 3, 12))
        assertEquals(1_800, WolfFilmProductionCoordinator.bodyDurationSeconds(1_800, 0, 12))
        assertEquals(36, WolfFilmProductionCoordinator.bodyDurationSeconds(5, 3, 12))
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

    @Test fun keepsWholeShotsAtChapterBoundariesWhenTheyFitAChapter() {
        val plan = WolfFilmProductionCoordinator.createPlan(
            framesPerScene = listOf(600),
            framesPerSecond = 1,
            introFrames = 0,
            chapterSeconds = 300
        )

        assertEquals(3, plan.chapters.size)
        plan.chapters.forEach { chapter ->
            assertEquals(1, chapter.slices.size)
            assertEquals(0, chapter.slices.single().shotStartFrame)
            assertEquals(200, chapter.slices.single().frameCount)
        }
    }

    @Test fun rejectsAnIntroThatCannotFitInsideTheFirstChapter() {
        assertTrue(runCatching {
            WolfFilmProductionCoordinator.createPlan(
                framesPerScene = listOf(600),
                framesPerSecond = 12,
                introFrames = 3_600,
                chapterSeconds = 300
            )
        }.isFailure)
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
        val plan = WolfFilmProductionCoordinator.createPlan(listOf(7_188, 7_188, 7_188), 12, 36)

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
        assertTrue(runCatching {
            WolfFilmProductionCoordinator.createPlan(listOf(100), 12, -1)
        }.isFailure)
    }

    @Test fun storyDirectorShotDurationsAllocateTheActualChapterFrames() {
        val sceneFrames = listOf(120)
        val shotBudgets = listOf(StoryDirectorEngine.allocateShotFrames(120, listOf(9, 3, 1)))
        val plan = WolfFilmProductionCoordinator.createPlan(
            framesPerScene = sceneFrames,
            framesPerSecond = 12,
            introFrames = 0,
            shotFramesPerScene = shotBudgets
        )
        val slices = plan.chapters.flatMap { it.slices }.sortedBy { it.shotIndex }

        assertEquals(120, slices.sumOf { it.frameCount })
        assertEquals(shotBudgets.single(), slices.map { it.shotTotalFrames })
        assertTrue(slices[0].shotTotalFrames > slices[1].shotTotalFrames)
        assertTrue(slices[1].shotTotalFrames > slices[2].shotTotalFrames)
        assertEquals(listOf(0, 0, 0), slices.map { it.shotStartFrame })
    }

}
