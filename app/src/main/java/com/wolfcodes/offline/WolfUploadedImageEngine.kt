package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Isolated offline pipeline for user-supplied still images.
 * Story-aware camera choreography and atmospheric overlays are local to this engine.
 * It has no dependency on the procedural scene planner or renderer.
 */
object WolfUploadedImageEngine {
    enum class Result { DEPTH_LAYERS, ORIGINAL_IMAGE, SAFE_FRAME }
    enum class CameraMove { SLOW_PUSH, SLOW_PULL, LEFT_TRACK, RIGHT_TRACK, REVEAL, HANDHELD }
    enum class Atmosphere { QUIET, FOREST, CORRIDOR, DOOR, GHOST, CHASE, RAIN, CEMETERY, MIRROR }

    data class MotionPlan(
        val cameraMove: CameraMove,
        val atmosphere: Atmosphere,
        val intensity: Float,
        val focusX: Float,
        val focusY: Float
    )

    class PreparedImage internal constructor(
        internal val source: Bitmap,
        internal val layers: WolfImageDepthSegmenter.Layers?,
        val confidence: Float,
        val warning: String? = null
    ) {
        fun recycle() {
            runCatching { layers?.recycle() }
        }
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val atmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private const val MIN_CONFIDENCE = 0.12f
    private const val TAU = (2.0 * PI).toFloat()

    internal fun shouldUseDepthLayers(
        separated: Boolean,
        confidence: Float,
        semanticValidationPassed: Boolean = false
    ): Boolean = separated && confidence >= MIN_CONFIDENCE && semanticValidationPassed &&
            WolfImageDepthSegmenter.isSemanticSegmentationReady()

    /** Story text, shot identity and normalized shot progress determine deterministic motion. */
    internal fun planMotion(
        story: String,
        sceneIndex: Int,
        shotIndex: Int,
        durationProgress: Float
    ): MotionPlan {
        val text = story.lowercase()
        val atmosphere = when {
            hasAny(text, "مطاردة", "يطارد", "يجري", "يركض", "هرب", "لاحق", "يلاحق") -> Atmosphere.CHASE
            hasAny(text, "مطر", "تمطر", "عاصفة", "ماطر", "رعد") -> Atmosphere.RAIN
            hasAny(text, "مقبرة", "قبر", "مدفن", "جنازة") -> Atmosphere.CEMETERY
            hasAny(text, "غابة", "شجرة", "أشجار", "غصن", "غصون") -> Atmosphere.FOREST
            hasAny(text, "مرآة", "مراية", "انعكاس") -> Atmosphere.MIRROR
            hasAny(text, "شبح", "ظل", "كيان", "روح", "ظهر", "اختفى", "اختفت") -> Atmosphere.GHOST
            hasAny(text, "باب", "مقبض", "طرق", "خبط", "صرير") -> Atmosphere.DOOR
            hasAny(text, "ممر", "طرقة", "رصيف") -> Atmosphere.CORRIDOR
            else -> Atmosphere.QUIET
        }
        val move = when (atmosphere) {
            Atmosphere.CHASE -> when (Math.floorMod(shotIndex, 3)) {
                0 -> CameraMove.LEFT_TRACK
                1 -> CameraMove.HANDHELD
                else -> CameraMove.RIGHT_TRACK
            }
            Atmosphere.DOOR, Atmosphere.GHOST, Atmosphere.MIRROR -> when (Math.floorMod(shotIndex, 3)) {
                0 -> CameraMove.REVEAL
                1 -> CameraMove.SLOW_PUSH
                else -> if (sceneIndex % 2 == 0) CameraMove.LEFT_TRACK else CameraMove.RIGHT_TRACK
            }
            Atmosphere.CEMETERY, Atmosphere.FOREST -> when (Math.floorMod(shotIndex, 3)) {
                0 -> CameraMove.SLOW_PULL
                1 -> CameraMove.LEFT_TRACK
                else -> CameraMove.SLOW_PUSH
            }
            Atmosphere.RAIN, Atmosphere.CORRIDOR -> when (Math.floorMod(shotIndex, 3)) {
                0 -> CameraMove.SLOW_PUSH
                1 -> CameraMove.RIGHT_TRACK
                else -> CameraMove.SLOW_PULL
            }
            Atmosphere.QUIET -> when (Math.floorMod(sceneIndex + shotIndex, 4)) {
                0 -> CameraMove.SLOW_PUSH
                1 -> CameraMove.LEFT_TRACK
                2 -> CameraMove.SLOW_PULL
                else -> CameraMove.RIGHT_TRACK
            }
        }
        val progressPulse = 0.96f + 0.04f * sin((durationProgress.coerceIn(0f, 1f) * PI).toDouble()).toFloat()
        val intensity = when (atmosphere) {
            Atmosphere.CHASE -> 1.0f
            Atmosphere.GHOST, Atmosphere.DOOR, Atmosphere.MIRROR -> 0.78f
            Atmosphere.RAIN, Atmosphere.CORRIDOR -> 0.62f
            Atmosphere.FOREST, Atmosphere.CEMETERY -> 0.48f
            Atmosphere.QUIET -> 0.28f
        }
        val focusX = when (move) {
            CameraMove.LEFT_TRACK -> 0.40f
            CameraMove.RIGHT_TRACK -> 0.60f
            CameraMove.REVEAL -> if (shotIndex % 2 == 0) 0.43f else 0.57f
            else -> 0.50f
        }
        val focusY = when (atmosphere) {
            Atmosphere.GHOST, Atmosphere.MIRROR -> 0.44f
            Atmosphere.CEMETERY -> 0.58f
            else -> 0.50f
        }
        return MotionPlan(move, atmosphere, (intensity * progressPulse).coerceIn(0f, 1f), focusX, focusY)
    }

    private fun hasAny(text: String, vararg terms: String): Boolean = terms.any { text.contains(it) }

    /** Best-effort analysis; source remains caller-owned and unchanged. */
    fun prepare(source: Bitmap, onFailure: (Exception) -> Unit = {}): PreparedImage {
        return try {
            require(!source.isRecycled && source.width > 0 && source.height > 0) {
                "الصورة المرفوعة غير صالحة"
            }
            val candidate = WolfImageDepthSegmenter.segment(source)
            if (shouldUseDepthLayers(
                    separated = candidate.heuristicCandidateSeparated,
                    confidence = candidate.confidence,
                    semanticValidationPassed = candidate.semanticValidationPassed
                )
            ) {
                PreparedImage(source, candidate, candidate.confidence)
            } else {
                candidate.recycle()
                PreparedImage(source, null, 0f, "InsufficientSegmentationConfidence")
            }
        } catch (failure: OutOfMemoryError) {
            PreparedImage(source, null, 0f, "OutOfMemoryError")
        } catch (failure: Exception) {
            runCatching { onFailure(failure) }
            PreparedImage(source, null, 0f, failure.javaClass.simpleName)
        }
    }

    /**
     * Every uploaded-image frame is animated, including the no-segmentation fallback.
     * Atmospheric overlays are composed locally and never call the procedural renderer.
     */
    @Synchronized
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float,
        caption: String,
        prepared: PreparedImage
    ): Result {
        if (width <= 0 || height <= 0 || prepared.source.isRecycled) return safeFrame(canvas, width, height)
        return try {
            val t = progress.coerceIn(0f, 1f)
            if (prepared.layers?.safeForParallax != true) {
                // Without semantic validation preserve the user's image as a still:
                // no pan, zoom, rotation, parallax, or atmospheric overlay is applied.
                canvas.drawColor(Color.BLACK)
                drawOriginal(canvas, width, height, prepared.source, sceneIndex, shotIndex, t,
                    planMotion(caption, sceneIndex, shotIndex, t))
                return Result.ORIGINAL_IMAGE
            }
            val plan = planMotion(caption, sceneIndex, shotIndex, t)
            val renderedWithDepth = renderImage(canvas, width, height, sceneIndex, shotIndex, t, plan, prepared)
            if (renderedWithDepth) {
                drawAtmosphere(canvas, width, height, sceneIndex, shotIndex, t, plan)
                Result.DEPTH_LAYERS
            } else Result.ORIGINAL_IMAGE
        } catch (_: OutOfMemoryError) {
            try {
                canvas.drawColor(Color.BLACK)
                val t = progress.coerceIn(0f, 1f)
                val plan = planMotion(caption, sceneIndex, shotIndex, t)
                drawOriginal(canvas, width, height, prepared.source, sceneIndex, shotIndex, t, plan)
                Result.ORIGINAL_IMAGE
            } catch (_: Throwable) {
                safeFrame(canvas, width, height)
            }
        } catch (_: Exception) {
            try {
                canvas.drawColor(Color.BLACK)
                val t = progress.coerceIn(0f, 1f)
                val plan = planMotion(caption, sceneIndex, shotIndex, t)
                drawOriginal(canvas, width, height, prepared.source, sceneIndex, shotIndex, t, plan)
                drawAtmosphere(canvas, width, height, sceneIndex, shotIndex, t, plan)
                Result.ORIGINAL_IMAGE
            } catch (_: OutOfMemoryError) {
                safeFrame(canvas, width, height)
            } catch (_: Exception) {
                safeFrame(canvas, width, height)
            }
        }
    }

    private fun renderImage(
        canvas: Canvas, width: Int, height: Int, sceneIndex: Int, shotIndex: Int,
        progress: Float, plan: MotionPlan, prepared: PreparedImage
    ): Boolean {
        val layers = prepared.layers
        if (layers == null || layers.background.isRecycled ||
            layers.midground.isRecycled || layers.foreground.isRecycled
        ) {
            canvas.drawColor(Color.BLACK)
            drawOriginal(canvas, width, height, prepared.source, sceneIndex, shotIndex, progress, plan)
            return false
        }
        val crop = cropForAspect(prepared.source.width, prepared.source.height, width, height)
        val scale = layers.sourceScale
        val layerSource = Rect(
            (crop.left * scale).toInt().coerceIn(0, layers.background.width - 1),
            (crop.top * scale).toInt().coerceIn(0, layers.background.height - 1),
            (crop.right * scale).toInt().coerceIn(1, layers.background.width),
            (crop.bottom * scale).toInt().coerceIn(1, layers.background.height)
        )
        if (layerSource.width() <= 0 || layerSource.height() <= 0) {
            canvas.drawColor(Color.BLACK)
            drawOriginal(canvas, width, height, prepared.source, sceneIndex, shotIndex, progress, plan)
            return false
        }
        val rendered = Wolf25DCinematicImageEngine.render(
            canvas = canvas,
            width = width,
            height = height,
            sceneIndex = sceneIndex,
            shotIndex = shotIndex,
            progress = progress,
            plan = plan,
            layers = layers,
            source = layerSource
        )
        if (!rendered) {
            canvas.drawColor(Color.BLACK)
            drawOriginal(canvas, width, height, prepared.source, sceneIndex, shotIndex, progress, plan)
            return false
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawOriginal(
        canvas: Canvas, width: Int, height: Int, source: Bitmap, sceneIndex: Int,
        shotIndex: Int, progress: Float, plan: MotionPlan
    ) {
        val crop = cropForAspect(source.width, source.height, width, height)
        val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(source, crop, destination, imagePaint)
    }

    private fun drawDepthPlane(
        canvas: Canvas, width: Int, height: Int, sceneIndex: Int, shotIndex: Int,
        progress: Float, plan: MotionPlan, bitmap: Bitmap, source: Rect,
        destination: RectF, depth: Float, parallaxScale: Float
    ) {
        if (bitmap.isRecycled) return
        val saved = canvas.save()
        try {
            applyCamera(canvas, width, height, sceneIndex, shotIndex, progress, plan, depth)
            canvas.scale(parallaxScale, parallaxScale, width * 0.5f, height * 0.5f)
            canvas.drawBitmap(bitmap, source, destination, imagePaint)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun applyCamera(
        canvas: Canvas, width: Int, height: Int, sceneIndex: Int, shotIndex: Int,
        progress: Float, plan: MotionPlan, depth: Float
    ) {
        val t = smooth(progress)
        val wave = sin((sceneIndex * 0.71f + shotIndex * 0.43f + t * TAU).toDouble()).toFloat()
        val secondary = cos((sceneIndex * 0.37f + t * TAU * 0.72f).toDouble()).toFloat()
        val zoom = when (plan.cameraMove) {
            CameraMove.SLOW_PUSH -> 1.018f + 0.105f * t
            CameraMove.SLOW_PULL -> 1.112f - 0.092f * t
            CameraMove.LEFT_TRACK, CameraMove.RIGHT_TRACK -> 1.065f + 0.025f * sin((t * PI).toDouble()).toFloat()
            CameraMove.REVEAL -> 1.025f + 0.082f * t
            CameraMove.HANDHELD -> 1.055f + 0.022f * wave
        }
        val direction = when (plan.cameraMove) {
            CameraMove.LEFT_TRACK -> 1f
            CameraMove.RIGHT_TRACK -> -1f
            CameraMove.REVEAL -> if (shotIndex % 2 == 0) -1f else 1f
            else -> 0f
        }
        val panX = when (plan.cameraMove) {
            CameraMove.LEFT_TRACK, CameraMove.RIGHT_TRACK -> direction * width * 0.042f * (t - 0.5f)
            CameraMove.REVEAL -> direction * width * 0.024f * (t - 0.5f)
            CameraMove.HANDHELD -> wave * width * 0.009f
            else -> wave * width * 0.004f
        }
        val panY = when (plan.cameraMove) {
            CameraMove.REVEAL -> -height * 0.018f * (t - 0.5f)
            CameraMove.HANDHELD -> secondary * height * 0.007f
            else -> secondary * height * 0.0035f
        }
        val rotation = when (plan.cameraMove) {
            CameraMove.HANDHELD -> wave * 0.34f
            CameraMove.REVEAL -> wave * 0.12f
            else -> wave * 0.055f
        }
        val parallax = 0.42f + depth * 0.58f
        canvas.translate(width * 0.5f + panX * parallax, height * 0.5f + panY * parallax)
        canvas.scale(zoom, zoom)
        canvas.rotate(rotation)
        canvas.translate(-width * 0.5f, -height * 0.5f)
    }

    private fun drawAtmosphere(
        canvas: Canvas, width: Int, height: Int, sceneIndex: Int, shotIndex: Int,
        progress: Float, plan: MotionPlan
    ) {
        val t = progress.coerceIn(0f, 1f)
        val phase = sceneIndex * 0.83f + shotIndex * 0.47f + t * TAU
        val strength = plan.intensity
        val saved = canvas.save()
        try {
            when (plan.atmosphere) {
                Atmosphere.FOREST, Atmosphere.CEMETERY, Atmosphere.GHOST ->
                    drawFog(canvas, width, height, phase, strength, t)
                Atmosphere.CORRIDOR, Atmosphere.DOOR, Atmosphere.MIRROR ->
                    drawDust(canvas, width, height, phase, strength, t)
                Atmosphere.RAIN -> drawRain(canvas, width, height, phase, strength, t)
                Atmosphere.CHASE -> {
                    drawDust(canvas, width, height, phase, strength * 0.72f, t)
                    drawMotionStreaks(canvas, width, height, phase, strength)
                }
                Atmosphere.QUIET -> drawDust(canvas, width, height, phase, strength * 0.42f, t)
            }
            if (plan.atmosphere == Atmosphere.DOOR || plan.atmosphere == Atmosphere.GHOST ||
                plan.atmosphere == Atmosphere.MIRROR
            ) {
                val pulse = 0.5f + 0.5f * sin((phase * 1.7f).toDouble()).toFloat()
                val glowAlpha = (strength * pulse * 9f).toInt().coerceIn(0, 10)
                atmospherePaint.shader = null
                atmospherePaint.style = Paint.Style.FILL
                atmospherePaint.color = Color.argb(glowAlpha, 210, 18, 36)
                val glowRadius = max(width, height) * 0.22f
                canvas.drawCircle(width * plan.focusX, height * plan.focusY, glowRadius, atmospherePaint)
                atmospherePaint.color = Color.argb((glowAlpha * 0.55f).toInt(), 225, 22, 40)
                canvas.drawCircle(width * plan.focusX, height * plan.focusY, glowRadius * 0.62f, atmospherePaint)
            }
            drawVignette(canvas, width, height, strength)
        } finally {
            atmospherePaint.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun drawFog(canvas: Canvas, width: Int, height: Int, phase: Float, strength: Float, progress: Float) {
        val drift = progress * width * 0.12f
        val centers = floatArrayOf(
            width * (0.18f + 0.06f * sin(phase.toDouble()).toFloat()) + drift,
            width * (0.70f + 0.05f * cos(phase.toDouble()).toFloat()) - drift * 0.55f,
            width * (0.46f + 0.04f * sin((phase * 0.6f).toDouble()).toFloat()) + drift * 0.25f
        )
        val ys = floatArrayOf(height * 0.72f, height * 0.58f, height * 0.86f)
        atmospherePaint.shader = null
        atmospherePaint.style = Paint.Style.FILL
        for (index in centers.indices) {
            val radius = width * (0.24f + index * 0.045f)
            for (band in 0..2) {
                val spread = 1f + band * 0.24f
                val alpha = (strength * (7f - band * 1.8f)).toInt().coerceIn(1, 8)
                atmospherePaint.color = Color.argb(alpha, 178, 185, 194)
                canvas.drawOval(
                    centers[index] - radius * spread,
                    ys[index] - radius * 0.15f * spread,
                    centers[index] + radius * spread,
                    ys[index] + radius * 0.15f * spread,
                    atmospherePaint
                )
            }
        }
    }
    private fun drawDust(canvas: Canvas, width: Int, height: Int, phase: Float, strength: Float, progress: Float) {
        atmospherePaint.shader = null
        atmospherePaint.color = Color.argb((strength * 34f).toInt().coerceIn(0, 38), 210, 207, 190)
        atmospherePaint.style = Paint.Style.FILL
        for (index in 0 until 13) {
            val seed = index * 1.731f + phase
            val x = Math.floorMod((width * (0.08f + index * 0.071f) +
                sin(seed.toDouble()).toFloat() * width * 0.035f +
                progress * width * (0.025f + index * 0.001f)).toInt(), width).toFloat()
            val y = Math.floorMod((height * (0.16f + index * 0.061f) +
                cos((seed * 0.77f).toDouble()).toFloat() * height * 0.045f -
                progress * height * 0.018f).toInt(), height).toFloat()
            val radius = (width * (0.0012f + (index % 3) * 0.00055f)).coerceAtLeast(0.7f)
            canvas.drawCircle(x, y, radius, atmospherePaint)
        }
    }

    private fun drawRain(canvas: Canvas, width: Int, height: Int, phase: Float, strength: Float, progress: Float) {
        linePaint.shader = null
        linePaint.color = Color.argb((strength * 72f).toInt().coerceIn(0, 80), 190, 205, 218)
        linePaint.strokeWidth = (width * 0.0011f).coerceIn(0.7f, 1.5f)
        for (index in 0 until 34) {
            val seed = index * 2.17f + phase
            val x = Math.floorMod((width * (index * 0.037f) + progress * width * 0.22f).toInt(), width).toFloat()
            val y = Math.floorMod((height * (index * 0.071f) + progress * height * 0.82f +
                sin(seed.toDouble()).toFloat() * height * 0.04f).toInt(), height).toFloat()
            canvas.drawLine(x, y, x - width * 0.009f, y + height * 0.035f, linePaint)
        }
    }

    private fun drawMotionStreaks(canvas: Canvas, width: Int, height: Int, phase: Float, strength: Float) {
        linePaint.shader = null
        linePaint.color = Color.argb((strength * 28f).toInt().coerceIn(0, 32), 210, 215, 220)
        linePaint.strokeWidth = (width * 0.0013f).coerceIn(0.8f, 1.7f)
        for (index in 0 until 8) {
            val y = height * (0.18f + index * 0.095f) +
                sin((phase + index).toDouble()).toFloat() * height * 0.012f
            canvas.drawLine(width * 0.04f, y, width * 0.16f, y - height * 0.018f, linePaint)
        }
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, strength: Float) {
        atmospherePaint.shader = null
        atmospherePaint.style = Paint.Style.FILL
        atmospherePaint.color = Color.argb((strength * 13f).toInt().coerceIn(0, 14), 0, 0, 0)
        val edge = (minOf(width, height) * 0.045f).coerceAtLeast(2f)
        canvas.drawRect(0f, 0f, width.toFloat(), edge, atmospherePaint)
        canvas.drawRect(0f, height - edge, width.toFloat(), height.toFloat(), atmospherePaint)
        canvas.drawRect(0f, 0f, edge, height.toFloat(), atmospherePaint)
        canvas.drawRect(width - edge, 0f, width.toFloat(), height.toFloat(), atmospherePaint)
    }
    private fun cropForAspect(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Rect {
        val targetRatio = width.toFloat() / height.toFloat()
        val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropWidth = (sourceHeight * targetRatio).toInt().coerceIn(1, sourceWidth)
            val left = (sourceWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, sourceHeight)
        } else {
            val cropHeight = (sourceWidth / targetRatio).toInt().coerceIn(1, sourceHeight)
            val top = (sourceHeight - cropHeight) / 2
            Rect(0, top, sourceWidth, top + cropHeight)
        }
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun safeFrame(canvas: Canvas, width: Int, height: Int): Result {
        if (width > 0 && height > 0) runCatching { canvas.drawColor(Color.BLACK) }
        return Result.SAFE_FRAME
    }
}