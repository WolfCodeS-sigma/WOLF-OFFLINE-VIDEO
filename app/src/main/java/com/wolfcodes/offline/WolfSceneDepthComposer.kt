package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

/**
 * Composes independently rendered scene planes into a deterministic 2.5D frame.
 *
 * Each plane is rasterized separately, then projected with its own parallax,
 * scale and subtle perspective drift. The camera never substitutes for object
 * motion: scene elements remain independently drawable on their depth plane.
 */
object WolfSceneDepthComposer {

    enum class Plane {
        BACKGROUND,
        MIDGROUND,
        FOREGROUND
    }

    private val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float,
        drawBackground: (Canvas) -> Unit,
        drawMidground: (Canvas) -> Unit,
        drawForeground: (Canvas) -> Unit
    ) {
        if (width <= 0 || height <= 0) return

        val safeProgress = progress.coerceIn(0f, 1f)
        val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val midground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val foreground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        try {
            val backgroundCanvas = Canvas(background)
            val midgroundCanvas = Canvas(midground)
            val foregroundCanvas = Canvas(foreground)

            backgroundCanvas.drawColor(Color.TRANSPARENT)
            midgroundCanvas.drawColor(Color.TRANSPARENT)
            foregroundCanvas.drawColor(Color.TRANSPARENT)

            drawBackground(backgroundCanvas)
            drawMidground(midgroundCanvas)
            drawForeground(foregroundCanvas)

            canvas.save()
            canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
            compositePlane(canvas, background, width, height, sceneIndex, safeProgress, Plane.BACKGROUND)
            compositePlane(canvas, midground, width, height, sceneIndex, safeProgress, Plane.MIDGROUND)
            compositePlane(canvas, foreground, width, height, sceneIndex, safeProgress, Plane.FOREGROUND)
            canvas.restore()
        } finally {
            background.recycle()
            midground.recycle()
            foreground.recycle()
        }
    }

    private fun compositePlane(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        sceneIndex: Int,
        progress: Float,
        plane: Plane
    ) {
        val offset = depthOffset(width, height, progress, sceneIndex, plane)
        val scale = depthScale(progress, plane)
        val saved = canvas.save()
        canvas.translate(width * 0.5f + offset.first, height * 0.5f + offset.second)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, -width * 0.5f, -height * 0.5f, compositePaint)
        canvas.restoreToCount(saved)
    }

    internal fun parallaxFactor(plane: Plane): Float = when (plane) {
        Plane.BACKGROUND -> 0.16f
        Plane.MIDGROUND -> 0.54f
        Plane.FOREGROUND -> 1.0f
    }

    internal fun depthOffset(
        width: Int,
        height: Int,
        progress: Float,
        sceneIndex: Int,
        plane: Plane
    ): Pair<Float, Float> {
        val t = progress.coerceIn(0f, 1f)
        val phase = sceneIndex * 0.73f + t * 6.2831853f
        val factor = parallaxFactor(plane)
        val horizontal = sin(phase.toDouble()).toFloat() * width * 0.018f * factor
        val vertical = sin((phase * 0.61f + 1.2f).toDouble()).toFloat() * height * 0.009f * factor
        return horizontal to vertical
    }

    internal fun depthScale(progress: Float, plane: Plane): Float {
        val t = progress.coerceIn(0f, 1f)
        val base = when (plane) {
            Plane.BACKGROUND -> 1.018f
            Plane.MIDGROUND -> 1.038f
            Plane.FOREGROUND -> 1.058f
        }
        val travel = when (plane) {
            Plane.BACKGROUND -> 0.006f
            Plane.MIDGROUND -> 0.012f
            Plane.FOREGROUND -> 0.019f
        }
        return base + travel * t
    }
}
