package com.wolfcodes.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfAudioTranscoderTest {
    @Test
    fun aacTracksDoNotNeedConversion() {
        assertFalse(WolfAudioTranscoder.requiresTranscoding("audio/mp4a-latm"))
        assertFalse(WolfAudioTranscoder.requiresTranscoding("audio/aac"))
    }

    @Test
    fun mp3AndOtherTracksNeedConversion() {
        assertTrue(WolfAudioTranscoder.requiresTranscoding("audio/mpeg"))
        assertTrue(WolfAudioTranscoder.requiresTranscoding("audio/opus"))
    }
}
