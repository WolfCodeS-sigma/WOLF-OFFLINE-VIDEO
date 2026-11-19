package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStorageEstimateTest {
    @Test
    fun reservesTwoVideoCopiesAndSafetyMarginForSilentExports() {
        val durationSeconds = 60
        val oneVideoCopy = durationSeconds * 1_600_000L / 8L
        val expected = oneVideoCopy * 2L + 128L * 1024L * 1024L

        assertEquals(expected, OfflineProductionEngine.estimatePeakStorageBytes(durationSeconds, false))
    }

    @Test
    fun reservesAdditionalSpaceForAudioExports() {
        val durationSeconds = 1800
        val withoutAudio = OfflineProductionEngine.estimatePeakStorageBytes(durationSeconds, false)
        val withAudio = OfflineProductionEngine.estimatePeakStorageBytes(durationSeconds, true)

        assertTrue(withAudio > withoutAudio)
        assertEquals(1800L * 512_000L / 8L, withAudio - withoutAudio)
    }

    @Test
    fun longFilmEstimateIncludesThePersistentChapterIntermediates() {
        val belowChapterLimit = OfflineProductionEngine.estimatePeakStorageBytes(300, false)
        val aboveChapterLimit = OfflineProductionEngine.estimatePeakStorageBytes(301, false)
        val expectedDifference =
            (301L * 1_600_000L / 8L) * 3L - (300L * 1_600_000L / 8L) * 2L
        assertEquals(expectedDifference, aboveChapterLimit - belowChapterLimit)
    }

    @Test
    fun estimateUsesTheMaximumSupportedVideoDurationIncludingIntro() {
        val capped = OfflineProductionEngine.estimatePeakStorageBytes(5000, false)
        val maximum = OfflineProductionEngine.estimatePeakStorageBytes(1803, false)

        assertEquals(maximum, capped)
    }
}
