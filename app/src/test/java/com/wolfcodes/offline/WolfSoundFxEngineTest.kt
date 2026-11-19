package com.wolfcodes.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSoundFxEngineTest {
    @Test
    fun detectsDoorKnocksAndFootstepsFromArabicSceneText() {
        val plan = WolfSoundFxEngine.classifyScene("سمع طرقًا على الباب ثم اقتربت خطوات بطيئة")
        assertTrue(WolfSoundFxEngine.Cue.WIND in plan.cues)
        assertTrue(WolfSoundFxEngine.Cue.KNOCK in plan.cues)
        assertTrue(WolfSoundFxEngine.Cue.DOOR_CREAK in plan.cues)
        assertTrue(WolfSoundFxEngine.Cue.FOOTSTEPS in plan.cues)
    }

    @Test
    fun detectsWhispersGrowlsAndNonHumanVocalTextures() {
        val plan = WolfSoundFxEngine.classifyScene("ظهر كيان غير بشري يهمس ثم أطلق زئيرًا وصرخة")
        assertTrue(WolfSoundFxEngine.Cue.WHISPER_TEXTURE in plan.cues)
        assertTrue(WolfSoundFxEngine.Cue.GROWL in plan.cues)
        assertTrue(WolfSoundFxEngine.Cue.SHRIEK in plan.cues)
        assertFalse(WolfSoundFxEngine.Cue.NON_HUMAN_LAUGH in plan.cues)
        assertTrue(WolfSoundFxEngine.Cue.WOLF_HOWL in WolfSoundFxEngine.classifyScene("عواء الذئب يملأ المكان").cues)
        val laugh = WolfSoundFxEngine.classifyScene("ضحكة غامضة من الكيان")
        assertTrue(WolfSoundFxEngine.Cue.NON_HUMAN_LAUGH in laugh.cues)
        assertFalse(plan.cues.any { it.name.contains("MUSIC") })
    }

    @Test
    fun quietSceneReceivesOnlyLowIntensityWindBed() {
        val plan = WolfSoundFxEngine.classifyScene("كانت الغرفة فارغة والنافذة مغلقة")
        assertTrue(plan.cues == setOf(WolfSoundFxEngine.Cue.WIND))
        assertTrue(plan.intensity < 0.5)
    }

    @Test
    fun separatesSoundPlansForDifferentScenes() {
        val plans = WolfSoundFxEngine.classifyScenes(
            listOf("غرفة ساكنة", "طرق متكرر على الباب", "مخلوق يطلق زئيرًا")
        )
        assertTrue(plans[0].cues == setOf(WolfSoundFxEngine.Cue.WIND))
        assertTrue(WolfSoundFxEngine.Cue.KNOCK in plans[1].cues)
        assertTrue(WolfSoundFxEngine.Cue.GROWL in plans[2].cues)
    }
    @Test
    fun eventTimelineSchedulesKnocksAtOneBoundedPoint() {
        val plan = WolfSoundFxEngine.buildScenePlan("سمع طرقًا متكررًا على الباب ثم فتح الباب", 12)
        val knock = plan.events.single { it.cue == WolfSoundFxEngine.Cue.KNOCK }
        assertTrue(knock.startSeconds > 0.0)
        assertTrue(knock.startSeconds + knock.durationSeconds <= 12.0)
        assertTrue(knock.gain in 0.0..1.0)
    }

    @Test
    fun quietScenesDoNotReceiveUnrequestedEventEffects() {
        val plan = WolfSoundFxEngine.buildScenePlan("كانت الغرفة فارغة والنافذة مغلقة", 9)
        assertTrue(plan.cues == setOf(WolfSoundFxEngine.Cue.WIND))
        assertTrue(plan.events.isEmpty())
    }

    @Test
    fun shortSceneEventsRemainInsideTheirScene() {
        val plan = WolfSoundFxEngine.buildScenePlan("صرخ الوحش وارتطم الباب ثم سمعنا خطوات", 3)
        assertTrue(plan.events.isNotEmpty())
        assertTrue(plan.events.all { it.startSeconds >= 0.0 && it.durationSeconds > 0.0 &&
            it.startSeconds + it.durationSeconds <= 3.000001 })
    }

    @Test
    fun limiterKeepsLayeredSoundWithinSafePeakRange() {
        val values = listOf(-100.0, -4.0, -1.0, 0.0, 1.0, 4.0, 100.0)
        values.forEach { assertTrue(WolfSoundFxEngine.applyLimiter(it, 1.24) in -0.96..0.96) }
    }

    @Test
    fun strongerGainProducesHigherLevelBelowLimiterCeiling() {
        val quiet = WolfSoundFxEngine.applyLimiter(0.20, 0.78)
        val strong = WolfSoundFxEngine.applyLimiter(0.20, 1.24)
        assertTrue(strong > quiet)
    }
}
