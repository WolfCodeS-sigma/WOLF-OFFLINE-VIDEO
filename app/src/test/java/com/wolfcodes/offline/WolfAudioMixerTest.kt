package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfAudioMixerTest {
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
    fun narrationActivityDucksEffectsAndSilenceRestoresTheirLevel() {
        val quiet = WolfAudioMixer.effectsDuckingGain(0.0)
        val active = WolfAudioMixer.effectsDuckingGain(0.08)
        assertEquals(1.0, quiet, 0.0001)
        assertTrue(active < quiet)
        assertTrue(active >= 0.40)
    }

    @Test
    fun noiseGateReducesTheNoiseFloorButKeepsNormalVoiceLevels() {
        assertEquals(0.0, WolfAudioMixer.noiseGateGain(0.0), 0.0001)
        assertTrue(WolfAudioMixer.noiseGateGain(0.002) in 0.0..1.0)
        assertEquals(1.0, WolfAudioMixer.noiseGateGain(0.02), 0.0001)
    }

    @Test
    fun duckingDoesNotChangeNarrationLevel() {
        val unDucked = WolfAudioMixer.mixSample(0.25, 0.4, 0.92, 0.30, 1.0)
        val ducked = WolfAudioMixer.mixSample(0.25, 0.4, 0.92, 0.30, 0.42)
        assertTrue(unDucked > ducked)
        assertEquals(
            WolfAudioMixer.mixSample(0.25, 0.0, 0.92, 0.30, 0.42),
            WolfAudioMixer.mixSample(0.25, 0.8, 0.92, 0.0, 0.42)
        )
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
