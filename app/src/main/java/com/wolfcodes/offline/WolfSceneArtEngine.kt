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
    internal data class AtmosphereProfile(
        val topColor: Int,
        val middleColor: Int,
        val bottomColor: Int,
        val fogTint: Int,
        val fogScale: Float,
        val continuityPhase: Float
    )

    internal fun atmosphereProfile(
        setting: String,
        mood: String,
        continuityKey: Int,
        visualStyle: String = "",
        horrorType: String = ""
    ): AtmosphereProfile {
        val colors = when {
            setting.contains("مكان طبي") ->
                intArrayOf(opaqueRgb(2, 7, 14), opaqueRgb(8, 17, 27), opaqueRgb(3, 7, 13), opaqueRgb(132, 158, 180))
            setting.contains("غابة") ->
                intArrayOf(opaqueRgb(2, 7, 8), opaqueRgb(7, 15, 17), opaqueRgb(2, 7, 10), opaqueRgb(112, 146, 148))
            setting.contains("شاطئ") || setting.contains("بحر") ->
                intArrayOf(opaqueRgb(2, 7, 15), opaqueRgb(5, 17, 27), opaqueRgb(2, 8, 14), opaqueRgb(116, 151, 172))
            setting.contains("مقابر") ->
                intArrayOf(opaqueRgb(4, 7, 8), opaqueRgb(11, 15, 13), opaqueRgb(3, 6, 7), opaqueRgb(126, 143, 132))
            setting.contains("صحراء") ->
                intArrayOf(opaqueRgb(12, 8, 8), opaqueRgb(23, 14, 12), opaqueRgb(8, 7, 8), opaqueRgb(164, 137, 120))
            setting.contains("ممر") || setting.contains("داخلي") ->
                intArrayOf(opaqueRgb(4, 6, 10), opaqueRgb(12, 13, 18), opaqueRgb(4, 5, 9), opaqueRgb(142, 150, 164))
            setting.contains("طريق") || setting.contains("شارع") ->
                intArrayOf(opaqueRgb(5, 7, 11), opaqueRgb(17, 11, 14), opaqueRgb(4, 6, 9), opaqueRgb(154, 145, 148))
            setting.contains("مكان داخلي") ->
                intArrayOf(opaqueRgb(5, 5, 9), opaqueRgb(16, 10, 14), opaqueRgb(4, 4, 8), opaqueRgb(153, 145, 153))
            else ->
                intArrayOf(opaqueRgb(3, 5, 9), opaqueRgb(12, 8, 13), opaqueRgb(4, 5, 9), opaqueRgb(156, 164, 176))
        }
        val styleLower = visualStyle.lowercase(java.util.Locale.ROOT)
        val finalColors = if (
            styleLower.contains("أبيض وأسود") || styleLower.contains("أحادي") ||
            styleLower.contains("نوير") || styleLower.contains("black and white")
        ) {
            intArrayOf(
                opaqueRgb(4, 5, 7), opaqueRgb(16, 17, 19),
                opaqueRgb(3, 4, 5), opaqueRgb(142, 144, 148)
            )
        } else colors
        val eventFogScale = when {
            mood.contains("مطاردة") -> 1.12f
            mood.contains("سمعي") -> 0.92f
            mood.contains("ظهور") -> 1.06f
            else -> 1f
        }
        val horrorLower = horrorType.lowercase(java.util.Locale.ROOT)
        val horrorFogScale = when {
            horrorLower.contains("خارق") || horrorLower.contains("ماورائي") -> 1.04f
            horrorLower.contains("نفسي") -> 0.96f
            else -> 1f
        }
        val fogScale = (eventFogScale * horrorFogScale).coerceIn(0.8f, 1.25f)
        val continuityPhase = Math.floorMod(continuityKey, 1009) / 1009f * 6.2831853f
        return AtmosphereProfile(finalColors[0], finalColors[1], finalColors[2], finalColors[3], fogScale, continuityPhase)
    }

    private fun opaqueRgb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Suppress("UNUSED_PARAMETER")
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        caption: String,
        scene: Int,
        progress: Float,
        setting: String = "",
        mood: String = "",
        continuityKey: Int = 0,
        visualStyle: String = "",
        horrorType: String = ""
    ) {
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val t = progress.coerceIn(0f, 1f)
        val atmosphere = atmosphereProfile(setting, mood, continuityKey, visualStyle, horrorType)
        val phase = scene * 0.73f + t * 1.4f + atmosphere.continuityPhase

        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(atmosphere.topColor, atmosphere.middleColor, atmosphere.bottomColor),
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
            val fogAlpha = ((9 + layer * 3) * atmosphere.fogScale).toInt().coerceIn(0, 255)
            fogPaint.color = Color.argb(
                fogAlpha,
                Color.red(atmosphere.fogTint),
                Color.green(atmosphere.fogTint),
                Color.blue(atmosphere.fogTint)
            )
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
