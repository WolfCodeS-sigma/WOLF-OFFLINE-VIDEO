package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfAudioMixerTest {
    @Test
    fun mixedPcmAlwaysFillsTheRequestedFilmDurationWhenSourcesEndEarly() {
        val directory = createTempDir(prefix = "wolf-audio-duration-")
        try {
            val narration = File(directory, "narration.pcm").apply { writeBytes(ByteArray(200) { 40 }) }
            val effects = File(directory, "effects.pcm").apply { writeBytes(ByteArray(100) { 20 }) }
            val output = File(directory, "mixed.pcm")

            WolfAudioMixer.mixPcmFiles(
                narration, effects, output, 1_000_000L,
                narrationGain = 0.92, effectsGain = 0.30
            )

            assertEquals(44_100L, output.length())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun silenceRemainsSilent() {
        assertEquals(0, WolfAudioMixer.mixSample(0.0, 0.0).toInt())
    }

    @Test
    fun narrationAndEffectsAreAddedIntoOneMixedSample() {
        val voiceOnly = WolfAudioMixer.mixSample(0.24, 0.0).toInt()
        val layered = WolfAudioMixer.mixSample(0.24, 0.50).toInt()
        assertTrue(layered > voiceOnly)
    }

    @Test
    fun narrationHasHigherDefaultGainThanEffects() {
        val voiceOnly = WolfAudioMixer.mixSample(0.30, 0.0).toInt()
        val effectsOnly = WolfAudioMixer.mixSample(0.0, 0.30).toInt()
        assertTrue(voiceOnly > effectsOnly)
    }

    @Test
    fun limiterPreventsClippingForStrongLayeredSignals() {
        val samples = listOf(
            WolfAudioMixer.mixSample(1.0, 1.0),
            WolfAudioMixer.mixSample(-1.0, -1.0),
            WolfAudioMixer.mixSample(20.0, 20.0),
            WolfAudioMixer.mixSample(-20.0, -20.0)
        )
        samples.forEach { sample ->
            assertTrue(sample.toInt() in -32112..32112)
        }
    }

    @Test
    fun effectsCanBeMutedWithoutChangingNarrationGain() {
        val withEffects = WolfAudioMixer.mixSample(0.25, 0.75, 0.92, 0.30)
        val mutedEffects = WolfAudioMixer.mixSample(0.25, 0.75, 0.92, 0.0)
        val narrationOnly = WolfAudioMixer.mixSample(0.25, 0.0, 0.92, 0.0)
        assertTrue(withEffects > mutedEffects)
        assertEquals(narrationOnly, mutedEffects)
    }
}
