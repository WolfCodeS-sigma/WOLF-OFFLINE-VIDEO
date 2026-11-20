package com.wolfcodes.offline

/**
 * One authoritative clock for the opening signature, narration, generated effects and MP4.
 * Narration is offset after the intro; the video timeline includes both.
 */
data class WolfCinematicAudioTimeline(
    val introSeconds: Int,
    val narrationSeconds: Int?,
    val totalVideoSeconds: Int,
    val narrationOffsetUs: Long,
    val narrationLimitUs: Long?
) {
    companion object {
        fun create(
            requestedDurationSeconds: Int,
            includeIntro: Boolean,
            authoritativeNarrationDurationMs: Long? = null
        ): WolfCinematicAudioTimeline {
            require(requestedDurationSeconds in 1..1800) { "مدة الفيلم غير صالحة" }
            val intro = if (includeIntro) minOf(3, (requestedDurationSeconds - 1).coerceAtLeast(0)) else 0
            val validNarration = authoritativeNarrationDurationMs
                ?.takeIf { it in 1_000L..1_800_000L }
            val narrationSeconds = validNarration?.let {
                ((it + 999L) / 1000L).toInt().coerceAtMost(1800 - intro).coerceAtLeast(1)
            }
            val bodySeconds = narrationSeconds ?: (requestedDurationSeconds - intro).coerceAtLeast(1)
            return WolfCinematicAudioTimeline(
                introSeconds = intro,
                narrationSeconds = narrationSeconds,
                totalVideoSeconds = (intro + bodySeconds).coerceIn(1, 1800),
                narrationOffsetUs = intro.toLong() * 1_000_000L,
                narrationLimitUs = validNarration?.coerceAtMost(bodySeconds.toLong() * 1000L)?.times(1000L)
            )
        }
    }
}
