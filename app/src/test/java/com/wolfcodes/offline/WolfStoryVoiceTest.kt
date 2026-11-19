package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfStoryVoiceTest {
    private fun session() = WolfStoryVoiceSession(
        projectId = "project-1",
        sourceStory = "قصة المصدر",
        draftStory = "قصة المصدر",
        finalStory = "قصة المصدر",
        scriptApproved = false,
        voiceFilePath = null,
        audioDurationMs = 0L,
        voiceApproved = false,
        takeNumber = 0,
        updatedAt = 1L
    )

    @Test
    fun approvingScriptDoesNotCreateOrModifyVoiceRecording() {
        val approved = WolfStoryVoiceStateMachine.approveScript(session(), "النص النهائي المعتمد", 2L)
        assertTrue(approved.scriptApproved)
        assertEquals("قصة المصدر", approved.sourceStory)
        assertEquals("النص النهائي المعتمد", approved.finalStory)
        assertEquals(null, approved.voiceFilePath)
        assertFalse(approved.voiceApproved)
    }

    @Test
    fun editingFinalScriptInvalidatesPreviousVoiceApproval() {
        val script = WolfStoryVoiceStateMachine.approveScript(session(), "النص الأول", 2L)
        val take = WolfStoryVoiceStateMachine.saveRecording(script, "/private/voice.m4a", 12000L, 3L)
        val approved = WolfStoryVoiceStateMachine.approveRecording(take, true, 4096L, 4L)
        val edited = WolfStoryVoiceStateMachine.edit(approved, "مسودة جديدة", "نص نهائي مختلف", 5L)
        assertFalse(edited.scriptApproved)
        assertFalse(edited.voiceApproved)
        assertEquals(null, edited.voiceFilePath)
        assertEquals(0L, edited.audioDurationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun voiceCannotBeRecordedBeforeScriptApproval() {
        WolfStoryVoiceStateMachine.saveRecording(session(), "/private/voice.m4a", 5000L, 2L)
    }

    @Test
    fun userCanApproveAndRepeatAnAudioTakeWithoutChangingStory() {
        val script = WolfStoryVoiceStateMachine.approveScript(session(), "قصة ثابتة", 2L)
        val take = WolfStoryVoiceStateMachine.saveRecording(script, "/private/voice.m4a", 25000L, 3L)
        val approved = WolfStoryVoiceStateMachine.approveRecording(take, true, 8192L, 4L)
        val repeated = WolfStoryVoiceStateMachine.requestRepeat(approved, 5L)
        assertEquals("قصة ثابتة", repeated.finalStory)
        assertTrue(repeated.scriptApproved)
        assertFalse(repeated.voiceApproved)
        assertEquals(null, repeated.voiceFilePath)
        assertEquals(0L, repeated.audioDurationMs)
    }

    @Test
    fun narrationDurationRoundsUpToWholeSecondForFramePlanning() {
        assertEquals(1, WolfStoryVoiceStateMachine.durationSecondsCeil(1L))
        assertEquals(69, WolfStoryVoiceStateMachine.durationSecondsCeil(68001L))
        assertEquals(69, WolfStoryVoiceStateMachine.durationSecondsCeil(69000L))
    }

    @Test
    fun silenceDetectorWaitsUntilSpeechWasActuallyHeard() {
        val detector = WolfSilenceDetector(amplitudeThreshold = 500, quietWindowMs = 2000L)
        assertFalse(detector.sample(0, 2000L))
        assertFalse(detector.sample(100, 4000L))
        assertFalse(detector.sample(700, 5000L))
        assertFalse(detector.sample(100, 6500L))
        assertTrue(detector.sample(100, 7000L))
    }

    @Test
    fun freshVoiceActivityResetsSilenceCountdown() {
        val detector = WolfSilenceDetector(amplitudeThreshold = 500, quietWindowMs = 2000L)
        assertFalse(detector.sample(900, 1000L))
        assertFalse(detector.sample(100, 2500L))
        assertFalse(detector.sample(900, 3000L))
        assertFalse(detector.sample(100, 4500L))
        assertTrue(detector.sample(100, 5000L))
    }
}
