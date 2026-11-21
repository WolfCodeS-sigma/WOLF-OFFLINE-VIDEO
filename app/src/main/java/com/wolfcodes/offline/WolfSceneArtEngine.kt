package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin

/**
 * Atmospheric-only fallback for scenes without an approved cinematic asset or
 * a user-supplied image. It intentionally does not draw buildings, props,
 * characters, ghosts, or geometric placeholders that could look cartoonish.
 */
object WolfSceneArtEngine {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Suppress("UNUSED_PARAMETER")
    fun draw(canvas: Canvas, width: Int, height: Int, caption: String, scene: Int, progress: Float) {
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val t = progress.coerceIn(0f, 1f)
        val phase = scene * 0.73f + t * 1.4f

        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.rgb(3, 5, 9), Color.rgb(12, 8, 13), Color.rgb(4, 5, 9)),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)
        backgroundPaint.shader = null

        // Smoke-like curves provide atmosphere only; they are not scene objects.
        for (layer in 0..4) {
            val layerPhase = phase + layer * 1.17f
            val baseY = h * (0.56f + layer * 0.095f)
            val amplitude = h * (0.018f + layer * 0.004f)
            val drift = sin(layerPhase.toDouble()).toFloat() * w * 0.025f
            val path = Path().apply {
                moveTo(-w * 0.08f + drift, baseY)
                cubicTo(
                    w * 0.18f,
                    baseY - amplitude + sin((layerPhase + 0.8f).toDouble()).toFloat() * amplitude,
                    w * 0.62f,
                    baseY + amplitude + sin((layerPhase + 1.9f).toDouble()).toFloat() * amplitude,
                    w * 1.08f - drift,
                    baseY + sin((layerPhase + 2.8f).toDouble()).toFloat() * amplitude * 0.45f
                )
            }
            fogPaint.color = Color.argb(9 + layer * 3, 156, 164, 176)
            fogPaint.strokeWidth = h * (0.025f + layer * 0.006f)
            canvas.drawPath(path, fogPaint)
        }

        vignettePaint.shader = RadialGradient(
            w * 0.5f, h * 0.48f, maxOf(w, h) * 0.76f,
            intArrayOf(Color.TRANSPARENT, Color.argb(164, 0, 0, 0)),
            floatArrayOf(0.42f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
        vignettePaint.shader = null
    }
}
