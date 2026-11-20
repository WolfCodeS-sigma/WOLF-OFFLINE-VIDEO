package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfVoiceSegmentStoreTest {
    @Test
    fun segmentNamesAreStableAndOrdered() {
        assertEquals("project-1-segment-0001.m4a", WolfVoiceSegmentStore.segmentFileName("project-1", 1))
        assertEquals("project-1-segment-0012.m4a", WolfVoiceSegmentStore.segmentFileName("project-1", 12))
        assertEquals("project-1-segment-0256.m4a", WolfVoiceSegmentStore.segmentFileName("project-1", 256))
    }

    @Test
    fun totalDurationSumsAllSegmentsWithoutChangingTheirOrder() {
        assertEquals(
            45_500L,
            WolfVoiceSegmentStore.totalDurationMs(listOf(12_000L, 8_500L, 25_000L))
        )
    }

    @Test
    fun maximumNarrationDurationIsExactlyThirtyMinutes() {
        assertEquals(
            1_800_000L,
            WolfVoiceSegmentStore.totalDurationMs(listOf(900_000L, 900_000L))
        )
        assertTrue(runCatching {
            WolfVoiceSegmentStore.totalDurationMs(listOf(900_000L, 900_001L))
        }.isFailure)
    }
}
