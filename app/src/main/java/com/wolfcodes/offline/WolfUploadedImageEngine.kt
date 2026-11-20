package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Isolated offline pipeline for user-supplied still images.
 *
 * This component deliberately has no dependency on the procedural scene planner,
 * procedural artwork renderer, story classifier, network, or remote models.
 * Its failures degrade to the original bitmap and never escape its public render API.
 */
object WolfUploadedImageEngine {

    enum class Result { DEPTH_LAYERS, ORIGINAL_IMAGE, SAFE_FRAME }

    class PreparedImage internal constructor(
        internal val source: Bitmap,
        internal val layers: WolfImageDepthSegmenter.Layers?,
        val confidence: Float,
        val warning: String? = null
    ) {
        fun recycle() {
            layers?.recycle()
        }
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    internal fun shouldUseDepthLayers(separated: Boolean, confidence: Float): Boolean =
        separated && confidence >= MIN_CONFIDENCE

    private const val MIN_CONFIDENCE = 0.30f

    /** Analysis is best-effort: the source bitmap remains caller-owned and untouched. */
    fun prepare(source: Bitmap, onFailure: (Exception) -> Unit = {}): PreparedImage {
        require(!source.isRecycled && source.width > 0 && source.height > 0) {
            "الصورة المرفوعة غير صالحة"
        }
        return try {
            val candidate = WolfImageDepthSegmenter.segment(source)
            if (shouldUseDepthLayers(candidate.separated, candidate.confidence)) {
                PreparedImage(source, candidate, candidate.confidence)
            } else {
                candidate.recycle()
                PreparedImage(source, null, 0f)
            }
        } catch (failure: Exception) {
            runCatching { onFailure(failure) }
            PreparedImage(source, null, 0f, failure.javaClass.simpleName)
        }
    }

    /**
     * Renders only uploaded-image content. Any analysis/composition error falls back
     * to the untouched source image; it does not call or mutate the procedural renderer.
     */
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
        if (width <= 0 || height <= 0 || prepared.source.isRecycled) {
            return safeFrame(canvas, width, height)
        }
        return try {
            renderImage(canvas, width, height, sceneIndex, shotIndex, progress, caption, prepared)
            if (prepared.layers != null) Result.DEPTH_LAYERS else Result.ORIGINAL_IMAGE
        } catch (_: Exception) {
            try {
                canvas.drawColor(Color.BLACK)
                drawOriginal(canvas, width, height, prepared.source, shotIndex, progress, caption)
                Result.ORIGINAL_IMAGE
            } catch (_: Exception) {
                safeFrame(canvas, width, height)
            }
        }
    }

    private fun renderImage(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float,
        caption: String,
        prepared: PreparedImage
    ) {
        val source = prepared.source
        val crop = cropForAspect(source.width, source.height, width, height)
        val shotCrop = shotCrop(crop, shotIndex, progress)
        val destination = destination(width, height, shotIndex, progress, sceneIndex, caption)
        val layers = prepared.layers
        if (layers != null && !layers.background.isRecycled &&
            !layers.midground.isRecycled && !layers.foreground.isRecycled
        ) {
            val scale = layers.sourceScale
            val layerSource = Rect(
                (shotCrop.left * scale).toInt().coerceIn(0, layers.background.width - 1),
                (shotCrop.top * scale).toInt().coerceIn(0, layers.background.height - 1),
                (shotCrop.right * scale).toInt().coerceIn(1, layers.background.width),
                (shotCrop.bottom * scale).toInt().coerceIn(1, layers.background.height)
            )
            if (layerSource.width() <= 0 || layerSource.height() <= 0) {
                drawOriginal(canvas, width, height, source, shotIndex, progress, caption)
            } else {
                WolfSceneDepthComposer.renderImageLayers(
                    canvas, width.toFloat(), height.toFloat(), sceneIndex, progress,
                    layers, layerSource, destination, cameraRotation(sceneIndex, progress, shotIndex)
                )
            }
        } else {
            canvas.drawColor(Color.BLACK)
            drawOriginal(canvas, width, height, source, shotIndex, progress, caption)
        }
    }

    private fun drawOriginal(
        canvas: Canvas,
        width: Int,
        height: Int,
        source: Bitmap,
        shotIndex: Int,
        progress: Float,
        caption: String
    ) {
        val crop = shotCrop(cropForAspect(source.width, source.height, width, height), shotIndex, progress)
        val target = destination(width, height, shotIndex, progress, 0, caption)
        val save = canvas.save()
        try {
            canvas.rotate(cameraRotation(0, progress, shotIndex), width / 2f, height / 2f)
            canvas.drawBitmap(source, crop, target, imagePaint)
        } finally {
            canvas.restoreToCount(save)
        }
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

    private fun shotCrop(base: Rect, shotIndex: Int, progress: Float): Rect {
        val t = progress.coerceIn(0f, 1f)
        val factor = when (shotIndex.coerceIn(0, 2)) { 0 -> 1f; 1 -> 0.78f; else -> 0.60f }
        val width = max(1, (base.width() * factor).toInt())
        val height = max(1, (base.height() * factor).toInt())
        val focusX = when (shotIndex.coerceIn(0, 2)) { 0 -> 0.5f; 1 -> 0.34f + 0.30f * t; else -> 0.62f - 0.24f * t }
        val focusY = when (shotIndex.coerceIn(0, 2)) { 0 -> 0.5f; 1 -> 0.45f; else -> 0.40f + 0.12f * t }
        val left = (base.left + (base.width() - width) * focusX).toInt()
            .coerceIn(base.left, base.right - width)
        val top = (base.top + (base.height() - height) * focusY).toInt()
            .coerceIn(base.top, base.bottom - height)
        return Rect(left, top, left + width, top + height)
    }

    private fun destination(
        width: Int, height: Int, shotIndex: Int, progress: Float, sceneIndex: Int, caption: String
    ): RectF {
        val t = progress.coerceIn(0f, 1f)
        val zoom = when (shotIndex.coerceIn(0, 2)) { 0 -> 1.012f; 1 -> 1.034f; else -> 1.058f }
        val phase = sceneIndex * 0.73f + t * 6.2831853f + caption.length * 0.013f
        val scale = zoom + 0.006f * kotlin.math.sin(phase.toDouble()).toFloat()
        val dw = width * scale
        val dh = height * scale
        val shiftX = kotlin.math.sin(phase.toDouble()).toFloat() * width * 0.006f
        val shiftY = kotlin.math.cos(phase.toDouble()).toFloat() * height * 0.004f
        return RectF((width - dw) / 2f + shiftX, (height - dh) / 2f + shiftY,
            (width + dw) / 2f + shiftX, (height + dh) / 2f + shiftY)
    }

    private fun cameraRotation(sceneIndex: Int, progress: Float, shotIndex: Int): Float {
        val phase = sceneIndex * 0.73f + progress.coerceIn(0f, 1f) * 6.2831853f
        val shotBias = when (shotIndex.coerceIn(0, 2)) { 0 -> 0f; 1 -> 0.12f; else -> -0.16f }
        return (kotlin.math.sin(phase.toDouble()).toFloat() * 0.22f + shotBias).coerceIn(-0.4f, 0.4f)
    }

    private fun safeFrame(canvas: Canvas, width: Int, height: Int): Result {
        if (width > 0 && height > 0) runCatching { canvas.drawColor(Color.BLACK) }
        return Result.SAFE_FRAME
    }
}
