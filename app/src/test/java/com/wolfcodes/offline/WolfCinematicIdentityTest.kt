package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfCinematicIdentityTest {
    @Test
    fun introAndWatermarkShareTheApprovedBrandName() {
        assertEquals("WOLF CODE S", WolfCinematicIdentity.BRAND_NAME)
        assertEquals("شفرة الذئب S", WolfCinematicIdentity.ARABIC_BRAND_NAME)
        assertEquals("بصمة الذئب السينمائية", WolfCinematicIdentity.INTRO_SIGNATURE)
    }

    @Test
    fun introDurationAndFrameBudgetUseOneSharedClock() {
        assertEquals(3, WolfCinematicIdentity.introDurationSeconds(30, includeIntro = true))
        assertEquals(0, WolfCinematicIdentity.introDurationSeconds(30, includeIntro = false))
        assertEquals(0, WolfCinematicIdentity.introDurationSeconds(1, includeIntro = true))
        assertEquals(2, WolfCinematicIdentity.introDurationSeconds(3, includeIntro = true))
        assertEquals(36, WolfCinematicIdentity.introFrameCount(3, framesPerSecond = 12))
    }

    @Test
    fun disablingIntroKeepsApprovedNarrationAtTheFilmTimelineOrigin() {
        val timeline = WolfCinematicAudioTimeline.create(
            requestedDurationSeconds = 30,
            includeIntro = false,
            authoritativeNarrationDurationMs = 7_000L
        )

        assertEquals(0, timeline.introSeconds)
        assertEquals(0L, timeline.narrationOffsetUs)
        assertEquals(30, timeline.totalVideoSeconds)
        assertEquals(7_000_000L, timeline.narrationLimitUs)
    }

    @Test
    fun introFrameBudgetMatchesNarrationOffsetAndFilmTimeline() {
        val timeline = WolfCinematicAudioTimeline.create(
            requestedDurationSeconds = 30,
            includeIntro = true,
            authoritativeNarrationDurationMs = 7_000L
        )

        assertEquals(3, timeline.introSeconds)
        assertEquals(
            WolfCinematicIdentity.introFrameCount(timeline.introSeconds, 12),
            36
        )
        assertEquals(3_000_000L, timeline.narrationOffsetUs)
        assertEquals(30, timeline.totalVideoSeconds)
        assertEquals(7_000_000L, timeline.narrationLimitUs)
    }
}
