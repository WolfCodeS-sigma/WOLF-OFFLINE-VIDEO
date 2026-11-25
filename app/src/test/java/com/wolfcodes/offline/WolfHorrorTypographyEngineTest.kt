package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfHorrorTypographyEngineTest {
    @Test
    fun choosesShortContextualPhraseInsteadOfCopyingStoryText() {
        val story = "وقفت قدام المراية، لكن الانعكاس اتحرك بعد ما أنا وقفت."
        val cue = WolfHorrorTypographyEngine.plan(listOf(story), listOf(120)).single()

        assertNotNull(cue)
        assertTrue(cue!!.text.length < story.length)
        assertFalse(story.contains(cue.text))
    }

    @Test
    fun unsupportedHorrorCueDoesNotReceiveUnfoundedPhrase() {
        val cues = WolfHorrorTypographyEngine.plan(
            listOf("القبو الرطب يضيق في سكون ثقيل، وتغلق العتمة آخر منفذ للضوء."), listOf(120)
        )

        assertNull(cues.single())
    }

    @Test
    fun limitsFrequencyAndLeavesAtLeastOneSceneBetweenCues() {
        val scenes = List(12) { index ->
            if (index % 2 == 0) "سمعوا طرقًا متكررًا على الباب في الممر رقم $index"
            else "سكن السرداب لحظة قبل أن تتحرك السلسلة خلف الباب."
        }
        val cues = WolfHorrorTypographyEngine.plan(scenes, List(12) { 120 })
        val cueIndexes = cues.mapIndexedNotNull { index, cue -> if (cue != null) index else null }

        assertTrue(cueIndexes.size <= 4)
        cueIndexes.zipWithNext().forEach { (first, second) ->
            assertTrue(second - first >= 2)
        }
    }

    @Test
    fun cueTimingStaysInsideItsSceneAndHasPositiveDuration() {
        val frames = 36
        val cue = WolfHorrorTypographyEngine.plan(
            listOf("سمعوا طرقًا على الباب."), listOf(frames)
        ).single()

        assertNotNull(cue)
        assertTrue(cue!!.startFrame >= 0)
        assertTrue(cue.endFrameExclusive <= frames)
        assertTrue(cue.durationFrames > 0)
    }

    @Test
    fun disabledEngineProducesNoCues() {
        val cues = WolfHorrorTypographyEngine.plan(
            listOf("ظهر ظل خلف الباب."), listOf(120), enabled = false
        )

        assertEquals(listOf(null), cues)
    }

    @Test
    fun fadeEnvelopeIsZeroOutsideAndVisibleWithinCue() {
        val cue = WolfHorrorTypographyEngine.plan(
            listOf("ظهر ظل خلف الباب."), listOf(120)
        ).single()!!

        assertEquals(0f, WolfHorrorTypographyEngine.alphaAtFrame(cue, cue.startFrame - 1), 0f)
        assertTrue(WolfHorrorTypographyEngine.alphaAtFrame(cue, cue.startFrame + 4) > 0f)
        assertTrue(WolfHorrorTypographyEngine.alphaAtFrame(cue, (cue.startFrame + cue.endFrameExclusive) / 2) > 0.8f)
        assertEquals(0f, WolfHorrorTypographyEngine.alphaAtFrame(cue, cue.endFrameExclusive), 0f)
    }
}
