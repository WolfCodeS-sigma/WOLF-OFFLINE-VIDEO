package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfLongFilmEngineTest {
    @Test
    fun sixMinuteFilmSplitsIntoBoundedChaptersAndAccountsForIntro() {
        val fps = 12
        val bodyFrames = listOf(1_200, 1_200, 1_200, 720)
        val introFrames = 36
        val chapters = WolfLongFilmEngine.planChapters(bodyFrames, fps, introFrames)

        assertEquals(2, chapters.size)
        assertEquals(3_600, chapters.first().totalFrameCount)
        assertTrue(chapters.all { it.totalFrameCount <= 300 * fps })
        assertEquals(bodyFrames.sum(), chapters.sumOf { it.bodyFrameCount })
        assertEquals(introFrames, chapters.sumOf { it.introFrameCount })
    }

    @Test
    fun oversizedSceneIsSplitWithoutDroppingOrReorderingFrames() {
        val frames = listOf(7_400)
        val chapters = WolfLongFilmEngine.planChapters(frames, 12, 0, 300)

        assertEquals(3, chapters.size)
        assertEquals(frames.single(), chapters.sumOf { it.bodyFrameCount })
        val slices = chapters.flatMap { it.slices }
        assertEquals(listOf(0, 3_600, 7_200), slices.map { it.startFrame })
        assertEquals(listOf(3_600, 3_600, 200), slices.map { it.frameCount })
    }

    @Test
    fun smallFilmStillProducesOneChapter() {
        val chapters = WolfLongFilmEngine.planChapters(listOf(240, 360), 12, 36)

        assertEquals(1, chapters.size)
        assertEquals(636, chapters.single().totalFrameCount)
    }

    @Test
    fun zeroLengthScenesDoNotCreateEmptyChapters() {
        val chapters = WolfLongFilmEngine.planChapters(listOf(0, 100, 0, 200), 12)

        assertEquals(1, chapters.size)
        assertEquals(listOf(1, 3), chapters.single().slices.map { it.sceneIndex })
        assertEquals(300, chapters.single().bodyFrameCount)
    }

    @Test
    fun fingerprintChangesWhenStoryOrRenderedInputsChange() {
        val base = WolfLongFilmEngine.fingerprint("story", listOf(120, 120), 12, 720, 1280, 36, true)
        assertEquals(base, WolfLongFilmEngine.fingerprint("story", listOf(120, 120), 12, 720, 1280, 36, true))
        assertNotEquals(base, WolfLongFilmEngine.fingerprint("different", listOf(120, 120), 12, 720, 1280, 36, true))
        assertNotEquals(base, WolfLongFilmEngine.fingerprint("story", listOf(120, 120), 12, 720, 1280, 36, true, "new-audio"))
    }

    @Test
    fun onlyInterruptedOrFailedChaptersCanBeResetForRetry() {
        val plan = WolfLongFilmEngine.planChapters(listOf(100), 12).single()
        val failed = WolfLongFilmEngine.Checkpoint(
            plan = plan,
            status = WolfLongFilmEngine.ChapterStatus.RETRYABLE_FAILURE,
            attempt = 2,
            lastError = "temporary codec issue"
        )
        val retry = WolfLongFilmEngine.chapterStatusAfterRetry(failed)

        assertEquals(WolfLongFilmEngine.ChapterStatus.PENDING, retry.status)
        assertEquals(2, retry.attempt)
        assertEquals(null, retry.lastError)
    }
}
