package com.wolfcodes.offline

import android.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/** Small deterministic local helpers used by the offline frame renderer. */
object OfflineStoryEngine {
    fun split(story: String): List<String> {
        val normalized = story.trim().replace("\r\n", "\n")
        if (normalized.isBlank()) return listOf("بداية الحكاية")
        val paragraphs = normalized.split(Regex("\\n\\s*\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val parts = if (paragraphs.size > 1) paragraphs else normalized
            .split(Regex("(?<=[.!؟?؛])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = parts.flatMap { if (it.length > 520) it.chunked(420) else listOf(it) }
        if (chunks.size <= 12) return chunks.ifEmpty { listOf(normalized) }
        // Match StoryDirectorEngine and preserve every character in the final scene.
        return chunks.take(11) + chunks.drop(11).joinToString("\n")
    }
}

object OfflineLookEngine {
    fun skyColor(scene: Int, caption: String): Int {
        val palettes = listOf(
            Color.rgb(48, 5, 16),
            Color.rgb(24, 8, 25),
            Color.rgb(8, 17, 25),
            Color.rgb(38, 12, 10),
            Color.rgb(14, 12, 22)
        )
        return palettes[(scene + (caption.hashCode() and 0x7fffffff) % palettes.size) % palettes.size]
    }
}

object OfflineMotionEngine {
    enum class Movement { SUSPENSE, REVEAL, CHASE }

    data class MotionProfile(
        val movement: Movement,
        val pacingWeight: Double,
        val zoomStrength: Float,
        val panStrength: Float,
        val shakeStrength: Float
    )

    /** Deterministic local pacing and camera profile derived from each scene's actual events. */
    fun profile(caption: String): MotionProfile {
        val text = caption.lowercase(java.util.Locale.ROOT)
        fun has(vararg words: String) = words.any(text::contains)
        val lengthBoost = (caption.length.coerceAtMost(420) / 420.0) * 0.12

        val chase = has(
            "مطاردة", "يطارد", "هرب", "يهرب", "يجري", "ركض", "اندفع", "هاجم",
            "يطاردها", "يطارده", "chase", "running", "escape", "attack", "runs"
        )
        val reveal = has(
            "ظهر", "ظهرت", "فجأة", "انكشف", "كشف", "التفت", "خلفه", "شبح",
            "ظل", "وجه", "عينان", "صرخة", "appeared", "revealed", "ghost", "shadow"
        )
        val quiet = has("ساكنة", "هادئة", "فارغة", "صمت", "انتظار", "empty", "quiet", "silent")

        return when {
            chase -> MotionProfile(Movement.CHASE, 1.18 + lengthBoost, 0.115f, 0.19f, 0.030f)
            reveal -> MotionProfile(Movement.REVEAL, 1.42 + lengthBoost, 0.125f, 0.15f, 0.010f)
            quiet -> MotionProfile(Movement.SUSPENSE, 0.92 + lengthBoost, 0.070f, 0.095f, 0.004f)
            else -> MotionProfile(Movement.SUSPENSE, 1.0 + lengthBoost, 0.085f, 0.12f, 0.008f)
        }
    }

    /**
     * Allocates an exact frame budget while preserving a minimum duration per scene.
     * Larger narrative reveals receive more screen time; all scenes remain represented.
     */
    fun allocateFrames(captions: List<String>, totalFrames: Int, minimumFramesPerScene: Int): List<Int> {
        require(captions.isNotEmpty()) { "لا توجد مشاهد لتوزيع الإطارات" }
        val minimum = minimumFramesPerScene.coerceAtLeast(1)
        require(totalFrames >= captions.size * minimum) {
            "عدد الإطارات لا يكفي لتخصيص الحد الأدنى لكل مشهد"
        }

        val result = IntArray(captions.size) { minimum }
        val remaining = totalFrames - captions.size * minimum
        if (remaining == 0) return result.toList()

        val weights = captions.map { profile(it).pacingWeight.coerceAtLeast(0.1) }
        val totalWeight = weights.sum()
        val exactExtras = weights.map { remaining * it / totalWeight }
        var assigned = 0
        exactExtras.forEachIndexed { index, exact ->
            val wholeFrames = exact.toInt()
            result[index] += wholeFrames
            assigned += wholeFrames
        }

        val remainder = remaining - assigned
        val priority = exactExtras.indices.sortedWith(
            compareByDescending<Int> { index -> exactExtras[index] - exactExtras[index].toInt() }
                .thenBy { it }
        )
        for (index in priority.take(remainder)) result[index]++

        return result.toList()
    }

    /** Kept for callers that only need the original subtle drift. */
    fun cameraOffset(progress: Float, scene: Int): Float {
        val phase = progress.coerceIn(0f, 1f) * (2.0 * PI) + scene * 0.73
        return (sin(phase) * 0.075).toFloat()
    }

    fun cameraOffset(progress: Float, scene: Int, caption: String): Float {
        val t = progress.coerceIn(0f, 1f)
        val profile = profile(caption)
        val phase = t * (2.0 * PI) + scene * 0.73
        val envelope = sin(PI * t).toFloat().coerceAtLeast(0f)
        val drift = sin(phase).toFloat()
        return when (profile.movement) {
            Movement.CHASE ->
                (drift * 0.075f + sin(phase * 8.5).toFloat() * profile.shakeStrength * envelope +
                    (t - 0.5f) * profile.panStrength).coerceIn(-0.20f, 0.20f)
            Movement.REVEAL ->
                ((t - 0.5f) * profile.panStrength + drift * 0.018f).coerceIn(-0.16f, 0.16f)
            Movement.SUSPENSE ->
                (drift * profile.panStrength * 0.75f).coerceIn(-0.12f, 0.12f)
        }
    }

    data class CameraFrame(
        val zoom: Float,
        val offsetX: Float,
        val offsetY: Float,
        val rotationDegrees: Float
    )

    /**
     * Returns a deterministic, bounded camera move for one of the three virtual shots.
     * Scene and shot indices rotate through eight compositions so adjacent scenes do not
     * repeat the same pan/zoom pattern. The source remains a still image; this is 2.5D
     * camera motion, not generated character or object animation.
     */
    fun cameraFrame(progress: Float, scene: Int, caption: String, shotIndex: Int): CameraFrame {
        val t = progress.coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        val wave = sin(t * (2f * PI) + scene * 0.61).toFloat()
        val envelope = sin(PI * t).toFloat().coerceAtLeast(0f)
        val profile = profile(caption)
        val style = Math.floorMod(scene * 3 + shotIndex.coerceIn(0, 2), 8)

        var panX = 0f
        var panY = 0f
        var zoom = when (shotIndex.coerceIn(0, 2)) {
            0 -> 0.022f * eased
            1 -> 0.042f * eased
            else -> 0.064f * eased
        }
        var rotation = 0f

        when (style) {
            0 -> { // Slow forward drift
                panX = -0.018f + 0.036f * eased
                panY = 0.008f * wave * envelope
            }
            1 -> { // Left-to-right lateral track
                panX = -0.052f + 0.104f * eased
                panY = -0.012f * envelope
            }
            2 -> { // Right-to-left lateral track
                panX = 0.052f - 0.104f * eased
                panY = 0.010f * envelope
                zoom *= -0.20f
            }
            3 -> { // Rising reveal
                panX = 0.012f * wave * envelope
                panY = 0.038f - 0.076f * eased
            }
            4 -> { // Falling reveal
                panX = -0.010f * wave * envelope
                panY = -0.034f + 0.068f * eased
                zoom *= -0.16f
            }
            5 -> { // Diagonal creeping move
                panX = -0.030f + 0.060f * eased
                panY = 0.026f - 0.052f * eased
            }
            6 -> { // Delayed close-up reveal
                val reveal = ((t - 0.48f) / 0.52f).coerceIn(0f, 1f)
                val revealEase = reveal * reveal * (3f - 2f * reveal)
                panX = 0.018f * revealEase
                zoom += 0.026f * revealEase
            }
            else -> { // Restrained handheld drift
                panX = sin(t * (10.0 * PI) + scene).toFloat() * 0.012f * envelope
                panY = sin(t * (8.0 * PI) + scene * 0.4).toFloat() * 0.008f * envelope
                rotation = sin(t * (8.0 * PI) + scene).toFloat() * 0.22f * envelope
            }
        }

        when (profile.movement) {
            Movement.CHASE -> {
                panX += sin(t * (16.0 * PI) + scene).toFloat() * profile.shakeStrength * envelope * 0.55f
                panY += sin(t * (19.0 * PI) + scene * 0.7).toFloat() * profile.shakeStrength * envelope * 0.32f
                rotation += sin(t * (14.0 * PI) + scene).toFloat() * 0.32f * envelope
                zoom += 0.010f * eased
            }
            Movement.REVEAL -> {
                zoom += 0.018f * eased
            }
            Movement.SUSPENSE -> {
                zoom += wave * 0.004f * envelope
            }
        }

        return CameraFrame(
            zoom = zoom.coerceIn(-0.012f, 0.090f),
            offsetX = panX.coerceIn(-0.12f, 0.12f),
            offsetY = panY.coerceIn(-0.10f, 0.10f),
            rotationDegrees = rotation.coerceIn(-0.65f, 0.65f)
        )
    }

    fun cameraZoom(progress: Float, scene: Int, caption: String): Float {
        val t = progress.coerceIn(0f, 1f)
        val profile = profile(caption)
        val wave = sin(t * (2.0 * PI) + scene * 0.73).toFloat()
        return when (profile.movement) {
            Movement.CHASE -> t * profile.zoomStrength + kotlin.math.abs(wave) * 0.008f
            Movement.REVEAL -> t * profile.zoomStrength
            Movement.SUSPENSE -> (t - 0.5f) * profile.zoomStrength * 1.2f + wave * profile.zoomStrength * 0.18f
        }
    }
}
