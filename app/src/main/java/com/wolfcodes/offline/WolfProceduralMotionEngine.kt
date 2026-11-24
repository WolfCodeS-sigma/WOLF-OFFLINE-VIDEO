package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Deterministic procedural motion layers for WOLF OFFLINE VIDEO.
 *
 * This renderer adds independently animated environmental elements over the base artwork:
 * atmospheric fog, floating particles, wind-driven branches, rain and restrained
 * signal distortion. It does not synthesize human or creature silhouettes. Every animation is driven by the frame
 * progress supplied by the offline encoder, so the same project renders identically
 * on retry and after chapter recovery.
 */
object WolfProceduralMotionEngine {

    data class Keyframe(
        val position: Float,
        val value: Float
    )

    data class SceneFeatures(
        val hasForest: Boolean,
        val hasRain: Boolean,
        val hasGlitch: Boolean,
        val hasChase: Boolean,
        val hasReveal: Boolean
    )

    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val glitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Draws all procedural layers for one encoded frame.
     *
     * The base scene must be drawn before this call. Typography and the watermark
     * should be drawn after it so they remain readable above atmospheric effects.
     */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        caption: String,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float
    ) {
        if (width <= 0 || height <= 0) return

        val t = progress.coerceIn(0f, 1f)
        val features = featuresFor(caption)
        drawAtmosphere(canvas, width, height, sceneIndex, t)
        drawFloatingParticles(canvas, width, height, sceneIndex, t, features.hasChase)
        if (features.hasForest) {
            drawWindDrivenBranches(canvas, width, height, sceneIndex, t)
        }
        if (features.hasRain) {
            drawRain(canvas, width, height, sceneIndex, t)
        }
        if (features.hasReveal || features.hasChase) {
            drawEnvironmentalPulse(canvas, width, height, sceneIndex, t, features.hasChase)
        }
        if (features.hasGlitch) {
            drawSignalDistortion(canvas, width, height, sceneIndex, t)
        }
    }

    /**
     * Draws atmospheric effects and entities in separate depth passes. Background
     * haze, midground entities and foreground weather/glitch receive independent
     * deterministic transforms instead of sharing one camera movement.
     */
    fun renderDepthAware(
        canvas: Canvas,
        width: Int,
        height: Int,
        caption: String,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float,
        storyEvent: StoryDirectorEngine.StoryEvent? = null,
        continuityKey: Int = 0
    ) {
        if (width <= 0 || height <= 0) return
        val t = progress.coerceIn(0f, 1f)
        val features = featuresFor(caption, storyEvent)

        drawAtDepth(canvas, width, height, sceneIndex, t, 0.16f, continuityKey) {
            drawAtmosphere(this, width, height, sceneIndex, t)
            if (features.hasReveal || features.hasChase) {
                drawEnvironmentalPulse(this, width, height, sceneIndex, t, features.hasChase)
            }
        }

        drawAtDepth(canvas, width, height, sceneIndex, t, 0.54f, continuityKey) {
            drawFloatingParticles(this, width, height, sceneIndex, t, features.hasChase)
            if (features.hasForest) {
                drawWindDrivenBranches(this, width, height, sceneIndex, t)
            }
        }

        drawAtDepth(canvas, width, height, sceneIndex, t, 1.0f, continuityKey) {
            if (features.hasRain) {
                drawRain(this, width, height, sceneIndex, t)
            }
            if (features.hasGlitch) {
                drawSignalDistortion(this, width, height, sceneIndex, t)
            }
        }
    }

    private inline fun drawAtDepth(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float,
        factor: Float,
        continuityKey: Int = 0,
        draw: Canvas.() -> Unit
    ) {
        val continuityPhase = Math.floorMod(continuityKey, 1009) / 1009f * 6.2831853f
        val phase = sceneIndex * 0.73f + progress * 6.2831853f + continuityPhase
        val driftX = sin(phase.toDouble()).toFloat() * width * 0.018f * factor
        val driftY = cos((phase * 0.61f + 1.2f).toDouble()).toFloat() * height * 0.009f * factor
        val scale = 1.012f + 0.019f * factor * progress
        val saved = canvas.save()
        canvas.translate(width * 0.5f + driftX, height * 0.5f + driftY)
        canvas.scale(scale, scale)
        canvas.translate(-width * 0.5f, -height * 0.5f)
        try {
            draw.invoke(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    fun featuresFor(
        caption: String,
        storyEvent: StoryDirectorEngine.StoryEvent? = null
    ): SceneFeatures {
        val text = caption.lowercase(java.util.Locale.ROOT)
        fun containsAny(vararg terms: String): Boolean = terms.any(text::contains)

        val forest = containsAny(
            "شجرة", "أشجار", "غابة", "غابه", "أغصان", "أوراق الشجر", "حديقة",
            "tree", "trees", "forest", "woods", "branches", "leaves"
        )
        val rain = containsAny(
            "مطر", "تمطر", "عاصفة", "عاصفه", "ماطر", "مياه تتساقط",
            "rain", "raining", "storm", "downpour"
        )
        val glitch = containsAny(
            "تشوش", "تشويش", "خلل", "وميض", "تقطع", "اهتزاز الصورة",
            "glitch", "static", "signal", "distortion", "flicker"
        )
        val chase = storyEvent == StoryDirectorEngine.StoryEvent.CHASE || containsAny(
            "مطاردة", "يطارد", "هرب", "يهرب", "يجري", "ركض", "اندفع", "هاجم",
            "chase", "running", "escape", "attack", "runs"
        )
        val reveal = storyEvent == StoryDirectorEngine.StoryEvent.REVEAL || containsAny(
            "ظهر", "ظهرت", "فجأة", "انكشف", "كشف", "التفت", "خلفه", "صرخة",
            "appeared", "revealed", "suddenly", "turned around", "behind"
        )

        return SceneFeatures(
            hasForest = forest,
            hasRain = rain,
            hasGlitch = glitch,
            hasChase = chase,
            hasReveal = reveal
        )
    }

    /**
     * Piecewise-linear keyframe sampling with clamped endpoints.
     * Keyframes must be supplied in ascending position order.
     */
    fun sampleKeyframes(progress: Float, keyframes: List<Keyframe>): Float {
        require(keyframes.isNotEmpty()) { "يجب توفير إطار مفتاحي واحد على الأقل" }
        require(keyframes.zipWithNext().all { (left, right) -> left.position <= right.position }) {
            "يجب ترتيب الإطارات المفتاحية تصاعديًا"
        }

        val t = progress.coerceIn(0f, 1f)
        if (t <= keyframes.first().position) return keyframes.first().value
        if (t >= keyframes.last().position) return keyframes.last().value

        for (index in 0 until keyframes.lastIndex) {
            val start = keyframes[index]
            val end = keyframes[index + 1]
            if (t <= end.position) {
                val span = end.position - start.position
                if (span <= 0f) return end.value
                val local = (t - start.position) / span
                return start.value + (end.value - start.value) * local
            }
        }
        return keyframes.last().value
    }

    private fun drawAtmosphere(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float
    ) {
        val scenePhase = sceneIndex * 0.47f
        val baseY = height * 0.69f
        val waveCount = 4
        val path = Path()

        for (layer in 0 until waveCount) {
            val phase = scenePhase + layer * 1.31f
            val amplitude = height * (0.012f + layer * 0.003f)
            val drift = progress * (0.55f + layer * 0.13f)
            val y = baseY + layer * height * 0.055f
            path.reset()
            path.moveTo(-width * 0.08f, y + wave(amplitude, phase + drift))
            path.cubicTo(
                width * 0.20f,
                y - amplitude * 1.8f + wave(amplitude, phase + drift + 1.1f),
                width * 0.56f,
                y + amplitude * 1.5f + wave(amplitude, phase + drift + 2.2f),
                width * 1.08f,
                y + wave(amplitude, phase + drift + 3.4f)
            )

            fogPaint.color = Color.argb(12 + layer * 3, 176, 183, 196)
            fogPaint.strokeWidth = height * (0.034f + layer * 0.009f)
            canvas.drawPath(path, fogPaint)
        }

        val vignetteRadius = max(width, height) * 0.78f
        val vignette = RadialGradient(
            width * 0.5f,
            height * 0.48f,
            vignetteRadius,
            intArrayOf(Color.TRANSPARENT, Color.argb(48, 0, 0, 0)),
            floatArrayOf(0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = vignette }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        vignettePaint.shader = null
    }

    private fun drawFloatingParticles(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float,
        fastMotion: Boolean
    ) {
        val count = if (fastMotion) 24 else 17
        val motionRate = if (fastMotion) 1.85f else 0.72f
        for (index in 0 until count) {
            val seed = sceneIndex * 37 + index * 19
            val startX = unit(seed * 17 + 11)
            val startY = unit(seed * 29 + 7)
            val drift = progress * motionRate
            val x = wrap01(startX + drift * (0.035f + unit(seed + 3) * 0.065f))
            val y = wrap01(startY - drift * (0.045f + unit(seed + 5) * 0.11f))
            val pulse = 0.45f + 0.55f * sin((progress * 2f * PI + seed).toDouble()).toFloat().let(::abs)
            val radius = width * (0.0011f + unit(seed + 13) * 0.0021f)
            val alpha = (10f + pulse * 24f).toInt().coerceIn(8, 38)
            particlePaint.color = Color.argb(alpha, 210, 205, 195)
            canvas.drawCircle(x * width, y * height, radius, particlePaint)
        }
    }

    private fun drawWindDrivenBranches(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float
    ) {
        val wind = sin((progress * 2.0 * PI + sceneIndex * 0.8).toDouble()).toFloat()
        val branchColor = Color.argb(112, 7, 8, 12)
        branchPaint.color = branchColor
        branchPaint.strokeWidth = max(2f, width * 0.006f)

        val leftSaved = canvas.save()
        canvas.translate(width * 0.035f, height * 0.78f)
        canvas.rotate(wind * 3.2f, 0f, 0f)
        drawBranchShape(canvas, height, branchPaint)
        canvas.restoreToCount(leftSaved)

        val rightSaved = canvas.save()
        canvas.translate(width * 0.97f, height * 0.82f)
        canvas.scale(-0.78f, 0.88f)
        canvas.rotate(-wind * 2.7f, 0f, 0f)
        drawBranchShape(canvas, height, branchPaint)
        canvas.restoreToCount(rightSaved)
    }

    private fun drawBranchShape(canvas: Canvas, height: Int, paint: Paint) {
        val scale = height / 620f
        val path = Path().apply {
            moveTo(0f, 0f)
            cubicTo(18f * scale, -92f * scale, 45f * scale, -190f * scale, 82f * scale, -312f * scale)
            moveTo(31f * scale, -120f * scale)
            cubicTo(-18f * scale, -160f * scale, -42f * scale, -206f * scale, -61f * scale, -252f * scale)
            moveTo(51f * scale, -205f * scale)
            cubicTo(98f * scale, -245f * scale, 121f * scale, -286f * scale, 135f * scale, -338f * scale)
            moveTo(65f * scale, -264f * scale)
            cubicTo(22f * scale, -301f * scale, 8f * scale, -340f * scale, 2f * scale, -378f * scale)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawRain(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float
    ) {
        val count = 76
        rainPaint.color = Color.argb(48, 175, 190, 204)
        rainPaint.strokeWidth = max(1f, width * 0.0012f)
        for (index in 0 until count) {
            val seed = sceneIndex * 53 + index * 31
            val x = wrap01(unit(seed + 2) + progress * 0.42f) * width
            val y = wrap01(unit(seed + 9) + progress * 1.35f) * height
            val length = height * (0.012f + unit(seed + 17) * 0.018f)
            canvas.drawLine(x, y, x - length * 0.24f, y + length, rainPaint)
        }
    }

    private fun drawEnvironmentalPulse(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float,
        intense: Boolean
    ) {
        val attack = if (intense) 0.30f else 0.18f
        val envelope = sin((progress * PI).toDouble()).toFloat().coerceAtLeast(0f)
        val pulse = abs(sin((progress * (if (intense) 9.0 else 3.0) * PI + sceneIndex).toDouble()).toFloat())
        val alpha = (envelope * pulse * if (intense) 18f else 9f).toInt().coerceIn(0, 20)
        if (alpha == 0) return

        val glow = RadialGradient(
            width * (0.50f + 0.16f * sin((progress * PI).toDouble()).toFloat()),
            height * 0.47f,
            max(width, height) * attack,
            intArrayOf(Color.argb(alpha, 112, 10, 28), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawSignalDistortion(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float
    ) {
        val pulse = abs(sin((progress * 8.0 * PI + sceneIndex * 0.91).toDouble()).toFloat())
        if (pulse < 0.62f) return

        val strength = ((pulse - 0.62f) / 0.38f).coerceIn(0f, 1f)
        val bandHeight = max(2f, height * 0.006f)
        val offset = sin((progress * 31.0 * PI + sceneIndex).toDouble()).toFloat() * width * 0.018f
        val y = height * (0.24f + 0.48f * unit(sceneIndex * 11 + (progress * 1000f).toInt()))
        glitchPaint.color = Color.argb((strength * 22f).toInt(), 190, 18, 38)
        canvas.drawRect(offset, y, width.toFloat() + offset, y + bandHeight, glitchPaint)
        glitchPaint.color = Color.argb((strength * 12f).toInt(), 190, 205, 218)
        canvas.drawRect(0f, y + bandHeight * 1.7f, width.toFloat(), y + bandHeight * 2.2f, glitchPaint)
    }

    private fun wave(amplitude: Float, phase: Float): Float =
        sin(phase.toDouble()).toFloat() * amplitude

    private fun unit(seed: Int): Float {
        val value = abs(seed.toLong() * 1103515245L + 12345L)
        return ((value % 100000L).toFloat() / 100000f).coerceIn(0f, 0.99999f)
    }

    private fun wrap01(value: Float): Float {
        val wrapped = value % 1f
        return if (wrapped < 0f) wrapped + 1f else wrapped
    }
}
