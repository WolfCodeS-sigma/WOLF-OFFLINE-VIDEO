package com.wolfcodes.offline

import kotlin.math.roundToInt

/**
 * Renderer-side timing helpers for verified story excerpts.
 *
 * The current mainline has no trusted ASR/word aligner, so this planner deliberately
 * returns no cues. It must fail closed instead of inventing generic horror phrases or
 * assigning scene-frame timestamps to words that were never verified in the narration.
 */
object WolfHorrorTypographyEngine {
    enum class Placement { UPPER_LEFT, UPPER_RIGHT, MID_LEFT, MID_RIGHT }

    enum class Motion { WHISPER_FADE, GLITCH_FLICKER }

    data class Cue(
        val text: String,
        val startFrame: Int,
        val endFrameExclusive: Int,
        val placement: Placement,
        val motion: Motion
    ) {
        val durationFrames: Int get() = (endFrameExclusive - startFrame).coerceAtLeast(0)
    }

    /**
     * No cue is emitted until a trusted speech-alignment result can supply literal
     * story text and audio-derived start/end boundaries. The manual Teleprompter mode
     * intentionally has no word recognition and therefore cannot produce such events.
     */
    @Suppress("UNUSED_PARAMETER")
    fun plan(
        scenes: List<String>,
        framesPerScene: List<Int>,
        fps: Int = 12,
        enabled: Boolean = true
    ): List<Cue?> = List(scenes.size) { null }

    /** Smooth fade envelope, returning zero outside a cue's scene-frame interval. */
    fun alphaAtFrame(cue: Cue, sceneFrame: Int, fps: Int = 12): Float {
        if (sceneFrame < cue.startFrame || sceneFrame >= cue.endFrameExclusive) return 0f
        val safeFps = fps.coerceIn(1, 60)
        val fadeFrames = (safeFps * 0.22f).roundToInt().coerceAtLeast(1)
        val elapsed = sceneFrame - cue.startFrame + 1
        val remaining = cue.endFrameExclusive - sceneFrame
        val fadeIn = smoothStep((elapsed.toFloat() / fadeFrames).coerceIn(0f, 1f))
        val fadeOut = smoothStep((remaining.toFloat() / fadeFrames).coerceIn(0f, 1f))
        return minOf(fadeIn, fadeOut)
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)
}
