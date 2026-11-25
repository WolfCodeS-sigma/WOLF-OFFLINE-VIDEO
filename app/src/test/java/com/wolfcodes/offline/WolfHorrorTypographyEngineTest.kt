package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfHorrorTypographyEngineTest {
    @Test
    fun doesNotInventPhrasesFromHorrorKeywordsWithoutVerifiedSpeechAlignment() {
        val scenes = listOf(
            "وقف الحارس أمام مرآة الممر، ثم سمع طرقات خلف باب الغرفة.",
            "ظل أسود يطارد الحارس داخل المشرحة المهجورة.",
            "في القبو الرطب تردد همس خافت خلف الجدار."
        )
        val cues = WolfHorrorTypographyEngine.plan(scenes, List(scenes.size) { 120 })

        assertEquals(List(scenes.size) { null }, cues)
    }

    @Test
    fun disabledTypographyStillProducesNoCues() {
        val cues = WolfHorrorTypographyEngine.plan(
            listOf("ظهر ظل خلف باب القبو."),
            listOf(120),
            enabled = false
        )

        assertEquals(listOf(null), cues)
    }

    @Test
    fun emptyScenesProduceAnEmptyCueList() {
        assertTrue(WolfHorrorTypographyEngine.plan(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun fadeEnvelopeRemainsBoundedForAnExplicitVerifiedCue() {
        val cue = WolfHorrorTypographyEngine.Cue(
            text = "النص الحرفي المعتمد",
            startFrame = 10,
            endFrameExclusive = 30,
            placement = WolfHorrorTypographyEngine.Placement.UPPER_LEFT,
            motion = WolfHorrorTypographyEngine.Motion.WHISPER_FADE
        )

        assertEquals(0f, WolfHorrorTypographyEngine.alphaAtFrame(cue, 9), 0f)
        assertTrue(WolfHorrorTypographyEngine.alphaAtFrame(cue, 15) > 0f)
        assertEquals(0f, WolfHorrorTypographyEngine.alphaAtFrame(cue, 30), 0f)
    }
}
