package com.wolfcodes.offline

/** Single source of truth for the approved WOLF CODE S cinematic signature. */
object WolfCinematicIdentity {
    const val BRAND_NAME = "WOLF CODE S"
    const val ARABIC_BRAND_NAME = "شفرة الذئب S"
    const val INTRO_SIGNATURE = "بصمة الذئب السينمائية"
    const val INTRO_DURATION_SECONDS = 3

    /** Keeps the opening inside the selected film duration while reserving body time. */
    fun introDurationSeconds(requestedTotal: Int, includeIntro: Boolean): Int {
        val safeTotal = requestedTotal.coerceIn(1, 1800)
        return if (includeIntro) {
            minOf(INTRO_DURATION_SECONDS, (safeTotal - 1).coerceAtLeast(0))
        } else {
            0
        }
    }

    /** Returns the exact video-frame budget used by the visual opening. */
    fun introFrameCount(introSeconds: Int, framesPerSecond: Int): Int {
        require(introSeconds >= 0) { "مدة المقدمة لا يمكن أن تكون سالبة" }
        require(framesPerSecond in 1..60) { "معدل الإطارات غير صالح للمقدمة" }
        return Math.multiplyExact(introSeconds, framesPerSecond)
    }
}
