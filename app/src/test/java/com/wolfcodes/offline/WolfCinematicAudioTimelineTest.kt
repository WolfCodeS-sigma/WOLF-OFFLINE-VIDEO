package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun narrationIsCappedSoTheIntroAndFilmNeverExceedThirtyMinutes() {
        val plan = WolfCinematicAudioTimeline.create(1800, true, 1_800_000L)
        assertEquals(1797, plan.narrationSeconds)
        assertEquals(1800, plan.totalVideoSeconds)
        assertEquals(1_797_000_000L, plan.narrationLimitUs)
    }
}
