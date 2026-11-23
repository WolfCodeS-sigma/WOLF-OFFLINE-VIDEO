package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfCinematicAudioTimelineTest {
    @Test
    fun narrationStartsAfterThreeSecondIntroAndVideoKeepsBothDurations() {
        val plan = WolfCinematicAudioTimeline.create(123, true, 120_000L)
        assertEquals(3, plan.introSeconds)
        assertEquals(120, plan.narrationSeconds)
        assertEquals(123, plan.totalVideoSeconds)
        assertEquals(3_000_000L, plan.narrationOffsetUs)
        assertEquals(120_000_000L, plan.narrationLimitUs)
    }

    @Test
    fun disablingIntroStartsNarrationAtZero() {
        val plan = WolfCinematicAudioTimeline.create(120, false, 120_000L)
        assertEquals(0, plan.introSeconds)
        assertEquals(0L, plan.narrationOffsetUs)
        assertEquals(120, plan.totalVideoSeconds)
    }

    @Test
    fun manualDurationIncludesIntroWithinTheSelectedFilmLength() {
        val plan = WolfCinematicAudioTimeline.create(300, true, null)
        assertEquals(3, plan.introSeconds)
        assertNull(plan.narrationSeconds)
        assertEquals(300, plan.totalVideoSeconds)
        assertNull(plan.narrationLimitUs)
    }

    @Test
    fun veryShortNarrationStillProducesTheMinimumFiveSecondFilm() {
        val plan = WolfCinematicAudioTimeline.create(5, true, 1_000L)
        assertEquals(3, plan.introSeconds)
        assertEquals(1, plan.narrationSeconds)
        assertEquals(5, plan.totalVideoSeconds)
        assertEquals(1_000_000L, plan.narrationLimitUs)
    }

    @Test
    fun fullThirtyMinuteNarrationIsPreservedWhenIntroIsDisabled() {
        val plan = WolfCinematicAudioTimeline.create(1800, false, 1_800_000L)
        assertEquals(1800, plan.narrationSeconds)
        assertEquals(1800, plan.totalVideoSeconds)
        assertEquals(1_800_000_000L, plan.narrationLimitUs)
    }

    @Test
    fun narrationIsNeverSilentlyCutToMakeRoomForTheIntro() {
        val failure = runCatching {
            WolfCinematicAudioTimeline.create(1800, true, 1_800_000L)
        }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message?.contains("تجاوز") == true)
    }

    @Test
    fun convertedNarrationRetainsIntroOffsetWhenMuxedFromTemporaryAac() {
        val timeline = WolfCinematicAudioTimeline.create(123, true, 120_000L)

        assertEquals(
            3_000_000L,
            OfflineProductionEngine.narrationOffsetForMux(
                originalNarrationSelected = true,
                narrationOffsetUs = timeline.narrationOffsetUs
            )
        )
    }

    @Test
    fun effectsOnlyAudioDoesNotInheritNarrationIntroOffset() {
        val timeline = WolfCinematicAudioTimeline.create(123, true, null)

        assertEquals(
            0L,
            OfflineProductionEngine.narrationOffsetForMux(
                originalNarrationSelected = false,
                narrationOffsetUs = timeline.narrationOffsetUs
            )
        )
    }
}
