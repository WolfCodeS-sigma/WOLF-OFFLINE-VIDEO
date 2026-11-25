package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
                intArrayOf(opaqueRgb(8, 17, 20), opaqueRgb(22, 34, 39), opaqueRgb(1, 4, 6), opaqueRgb(112, 146, 148))
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

        // Draw location-specific landscape planes before the common horror haze.
        // No people, ghosts, creatures, furniture, buildings or unlicensed assets are generated.
        drawEnvironment(canvas, w, h, setting, scene, t, atmosphere.continuityPhase, visualStyle)

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

    private val environmentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val environmentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawEnvironment(
        canvas: Canvas,
        width: Float,
        height: Float,
        setting: String,
        scene: Int,
        progress: Float,
        continuityPhase: Float,
        visualStyle: String
    ) {
        val normalized = setting.lowercase(java.util.Locale.ROOT)
        val style = visualStyle.lowercase(java.util.Locale.ROOT)
        val monochrome = style.contains("أبيض وأسود") || style.contains("أحادي") ||
            style.contains("نوير") || style.contains("black and white") || style.contains("monochrome")
        val colorFilter = if (monochrome) {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        } else {
            null
        }
        // These Paint instances are shared across scene renders. A previous
        // environment may have left a translucent color (for example the desert
        // foreground dune at alpha 72). Reset the complete fill/line state before
        // applying this scene's style so gradients are not unintentionally faded.
        environmentFillPaint.shader = null
        environmentFillPaint.color = Color.WHITE
        environmentFillPaint.alpha = 255
        environmentFillPaint.style = Paint.Style.FILL
        environmentFillPaint.colorFilter = colorFilter

        environmentLinePaint.shader = null
        environmentLinePaint.color = Color.WHITE
        environmentLinePaint.alpha = 255
        environmentLinePaint.style = Paint.Style.STROKE
        environmentLinePaint.strokeCap = Paint.Cap.ROUND
        environmentLinePaint.strokeJoin = Paint.Join.ROUND
        environmentLinePaint.colorFilter = colorFilter
        try {
            when {
                normalized.contains("شاطئ") || normalized.contains("ساحل") ||
                    normalized.contains("beach") || normalized.contains("shore") ||
                    normalized.contains("coast") -> drawBeach(canvas, width, height, scene, progress, continuityPhase)
                normalized.contains("بحر") || normalized.contains("محيط") ||
                    normalized.contains("sea") || normalized.contains("ocean") ||
                    normalized.contains("water") -> drawOpenSea(canvas, width, height, scene, progress, continuityPhase)
                normalized.contains("صحراء") || normalized.contains("رمال") ||
                    normalized.contains("كثبان") || normalized.contains("desert") ||
                    normalized.contains("dunes") -> drawDesert(canvas, width, height, scene, progress, continuityPhase)
                normalized.contains("غابة") || normalized.contains("forest") ||
                    normalized.contains("woods") -> drawForest(canvas, width, height, scene, progress, continuityPhase)
                normalized.contains("طريق") || normalized.contains("شارع") ||
                    normalized.contains("road") || normalized.contains("street") -> drawRoad(canvas, width, height, scene, progress)
                normalized.contains("ريف") || normalized.contains("مزرعة") ||
                    normalized.contains("حقل") || normalized.contains("rural") ||
                    normalized.contains("farm") || normalized.contains("field") -> drawRural(canvas, width, height, scene, progress)
            }
        } finally {
            // Restore neutral state even when an environment renderer throws.
            // The next shot must never inherit another environment's alpha, shader,
            // color filter, or paint style.
            environmentFillPaint.shader = null
            environmentFillPaint.color = Color.WHITE
            environmentFillPaint.alpha = 255
            environmentFillPaint.style = Paint.Style.FILL
            environmentFillPaint.colorFilter = null

            environmentLinePaint.shader = null
            environmentLinePaint.color = Color.WHITE
            environmentLinePaint.alpha = 255
            environmentLinePaint.style = Paint.Style.STROKE
            environmentLinePaint.strokeCap = Paint.Cap.ROUND
            environmentLinePaint.strokeJoin = Paint.Join.ROUND
            environmentLinePaint.strokeWidth = 1f
            environmentLinePaint.colorFilter = null
        }
    }

    private fun drawBeach(
        canvas: Canvas, width: Float, height: Float, scene: Int, progress: Float, continuityPhase: Float
    ) {
        val phase = progress * 6.2831853f + scene * 0.61f + continuityPhase
        val horizon = height * 0.405f
        val shoreline = height * (0.785f + kotlin.math.sin(phase.toDouble()).toFloat() * 0.007f)
        val shoreWave = height * 0.012f

        val sea = Path().apply {
            moveTo(0f, horizon)
            lineTo(width, horizon)
            lineTo(width, shoreline + shoreWave)
            cubicTo(width * 0.72f, shoreline - shoreWave, width * 0.30f, shoreline + shoreWave * 0.8f, 0f, shoreline)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, horizon, 0f, shoreline + shoreWave,
            intArrayOf(opaqueRgb(8, 30, 42), opaqueRgb(5, 21, 31), opaqueRgb(3, 13, 22)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(sea, environmentFillPaint)
        environmentFillPaint.shader = null

        val sand = Path().apply {
            moveTo(0f, shoreline)
            cubicTo(width * 0.30f, shoreline + shoreWave * 0.8f, width * 0.72f, shoreline - shoreWave, width, shoreline + shoreWave)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, shoreline, 0f, height,
            intArrayOf(opaqueRgb(36, 31, 29), opaqueRgb(22, 21, 22), opaqueRgb(12, 14, 17)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(sand, environmentFillPaint)
        environmentFillPaint.shader = null

        // Wet sand forms a curved transition band below the moving tide.
        val wetSand = Path().apply {
            moveTo(0f, shoreline + height * 0.014f)
            cubicTo(width * 0.30f, shoreline + height * 0.028f, width * 0.72f, shoreline + height * 0.004f, width, shoreline + height * 0.020f)
            lineTo(width, shoreline + height * 0.066f)
            cubicTo(width * 0.72f, shoreline + height * 0.049f, width * 0.30f, shoreline + height * 0.076f, 0f, shoreline + height * 0.060f)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, shoreline, 0f, shoreline + height * 0.07f,
            intArrayOf(opaqueRgb(43, 42, 39), opaqueRgb(29, 29, 28)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(wetSand, environmentFillPaint)
        environmentFillPaint.shader = null

        drawWaterRipples(canvas, width, height, horizon, shoreline - height * 0.018f, scene, progress, continuityPhase, 15)
        val foam = Path().apply {
            moveTo(0f, shoreline)
            cubicTo(width * 0.30f, shoreline + shoreWave * 0.8f, width * 0.72f, shoreline - shoreWave, width, shoreline + shoreWave)
        }
        environmentLinePaint.color = Color.argb(86, 197, 207, 204)
        environmentLinePaint.strokeWidth = maxOf(1.2f, height * 0.003f)
        canvas.drawPath(foam, environmentLinePaint)

        val retreat = Path().apply {
            moveTo(0f, shoreline + height * 0.038f)
            cubicTo(width * 0.32f, shoreline + height * 0.050f, width * 0.72f, shoreline + height * 0.030f, width, shoreline + height * 0.044f)
        }
        environmentLinePaint.color = Color.argb(28, 183, 194, 190)
        environmentLinePaint.strokeWidth = maxOf(1f, height * 0.0017f)
        canvas.drawPath(retreat, environmentLinePaint)
        drawHorizonMist(canvas, width, height, horizon, scene, progress)
    }

    private fun drawOpenSea(
        canvas: Canvas, width: Float, height: Float, scene: Int, progress: Float, continuityPhase: Float
    ) {
        val horizon = height * 0.415f
        val water = Path().apply {
            moveTo(0f, horizon)
            lineTo(width, horizon)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, horizon, 0f, height,
            intArrayOf(opaqueRgb(9, 34, 48), opaqueRgb(4, 20, 31), opaqueRgb(2, 10, 19)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(water, environmentFillPaint)
        environmentFillPaint.shader = null
        environmentLinePaint.color = Color.argb(70, 166, 190, 197)
        environmentLinePaint.strokeWidth = maxOf(1f, height * 0.002f)
        canvas.drawLine(0f, horizon, width, horizon, environmentLinePaint)
        drawWaterRipples(canvas, width, height, horizon, height * 0.985f, scene, progress, continuityPhase, 22)
        drawHorizonMist(canvas, width, height, horizon, scene, progress)
    }

    private fun drawWaterRipples(
        canvas: Canvas, width: Float, height: Float, top: Float, bottom: Float,
        scene: Int, progress: Float, continuityPhase: Float, rows: Int
    ) {
        val phase = progress * 6.2831853f + scene * 0.47f + continuityPhase
        environmentLinePaint.strokeWidth = maxOf(1f, height * 0.0015f)
        val path = Path()
        for (row in 0 until rows) {
            val fraction = (row + 1f) / (rows + 1f)
            val y = top + (bottom - top) * fraction
            val drift = kotlin.math.sin((phase + row * 0.73f).toDouble()).toFloat()
            val center = width * (0.50f + drift * 0.035f)
            val span = width * (0.09f + (row % 4) * 0.035f)
            val wave = height * (0.0018f + (row % 3) * 0.0009f)
            path.reset()
            path.moveTo(center - span, y)
            path.cubicTo(center - span * 0.42f, y - wave, center + span * 0.30f, y + wave, center + span, y - wave * 0.12f)
            environmentLinePaint.color = Color.argb(22 + (row % 5) * 5, 151, 177, 185)
            canvas.drawPath(path, environmentLinePaint)
        }
    }

    private fun drawHorizonMist(
        canvas: Canvas, width: Float, height: Float, horizon: Float, scene: Int, progress: Float
    ) {
        val drift = kotlin.math.sin((progress * 2.0 * Math.PI + scene * 0.3).toDouble()).toFloat() * width * 0.025f
        environmentFillPaint.shader = RadialGradient(
            width * 0.51f + drift, horizon, width * 0.62f,
            intArrayOf(Color.argb(42, 148, 171, 178), Color.argb(14, 107, 132, 144), Color.TRANSPARENT),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, horizon - height * 0.13f, width, horizon + height * 0.17f, environmentFillPaint)
        environmentFillPaint.shader = null
        environmentLinePaint.color = Color.argb(24, 184, 199, 199)
        environmentLinePaint.strokeWidth = maxOf(1f, height * 0.0015f)
        canvas.drawLine(0f, horizon + height * 0.004f, width, horizon + height * 0.004f, environmentLinePaint)
    }

    private fun drawDesert(
        canvas: Canvas, width: Float, height: Float, scene: Int, progress: Float, continuityPhase: Float
    ) {
        val phase = scene * 0.67f + progress * 0.32f + continuityPhase
        val horizon = height * 0.62f
        val ground = Path().apply {
            moveTo(0f, horizon)
            cubicTo(width * 0.28f, horizon - height * 0.10f, width * 0.68f, horizon + height * 0.05f, width, horizon - height * 0.04f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, horizon - height * 0.1f, 0f, height,
            intArrayOf(opaqueRgb(48, 37, 35), opaqueRgb(31, 25, 25), opaqueRgb(17, 16, 18)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(ground, environmentFillPaint)
        environmentFillPaint.shader = null
        val foregroundDune = Path().apply {
            moveTo(0f, height * 0.82f)
            cubicTo(width * 0.22f, height * (0.70f + kotlin.math.sin(phase.toDouble()).toFloat() * 0.008f),
                width * 0.70f, height * (0.86f + kotlin.math.cos(phase.toDouble()).toFloat() * 0.006f),
                width, height * 0.78f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        environmentFillPaint.color = Color.argb(72, 99, 71, 55)
        canvas.drawPath(foregroundDune, environmentFillPaint)
        environmentLinePaint.color = Color.argb(35, 187, 151, 125)
        environmentLinePaint.strokeWidth = maxOf(1f, height * 0.002f)
        val contour = Path().apply {
            moveTo(0f, height * 0.82f)
            cubicTo(width * 0.22f, height * 0.70f, width * 0.70f, height * 0.86f, width, height * 0.78f)
        }
        canvas.drawPath(contour, environmentLinePaint)
    }

    private fun drawForest(
        canvas: Canvas, width: Float, height: Float, scene: Int, progress: Float, continuityPhase: Float
    ) {
        val groundY = height * 0.78f

        // A soft, backlit mist layer separates the distant treeline from the near-black sky.
        // It is environmental depth only and contains no structures or story subjects.
        environmentFillPaint.shader = RadialGradient(
            width * 0.50f,
            height * 0.52f,
            width * 0.86f,
            intArrayOf(
                Color.argb(118, 64, 78, 82),
                Color.argb(52, 36, 49, 53),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, groundY, environmentFillPaint)
        environmentFillPaint.shader = null

        val ground = Path().apply {
            moveTo(0f, groundY)
            cubicTo(width * 0.32f, groundY - height * 0.055f, width * 0.66f, groundY + height * 0.025f, width, groundY - height * 0.035f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, groundY, 0f, height,
            intArrayOf(opaqueRgb(7, 13, 12), opaqueRgb(0, 1, 2)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(ground, environmentFillPaint)
        environmentFillPaint.shader = null

        val sway = kotlin.math.sin((progress * 2.0 * Math.PI + scene * 0.6 + continuityPhase).toDouble()).toFloat()
        val trunk = Path()
        for (index in 0..12) {
            val seed = scene * 19 + index * 13
            val x = width * (index / 12f)
            val top = height * (0.34f + ((seed % 5) * 0.027f))
            val lean = sway * width * (0.004f + (index % 3) * 0.001f)
            trunk.reset()
            trunk.moveTo(x - width * 0.012f, height * 0.86f)
            trunk.cubicTo(x + lean, height * 0.68f, x - lean * 0.55f, top + height * 0.17f, x + lean * 0.30f, top)
            trunk.cubicTo(x + width * 0.014f, top + height * 0.19f, x + lean, height * 0.68f, x + width * 0.013f, height * 0.86f)
            trunk.close()
            environmentFillPaint.color = Color.argb(224, 1, 3, 5)
            canvas.drawPath(trunk, environmentFillPaint)
        }
    }

    private fun drawRoad(canvas: Canvas, width: Float, height: Float, scene: Int, progress: Float) {
        val horizon = height * 0.54f
        val centerX = width * (0.50f + kotlin.math.sin((progress * 2.0 * Math.PI + scene).toDouble()).toFloat() * 0.012f)
        val road = Path().apply {
            moveTo(centerX - width * 0.025f, horizon)
            lineTo(centerX + width * 0.025f, horizon)
            lineTo(width * 0.90f, height)
            lineTo(width * 0.10f, height)
            close()
        }
        environmentFillPaint.shader = LinearGradient(
            0f, horizon, 0f, height,
            intArrayOf(opaqueRgb(17, 18, 22), opaqueRgb(5, 7, 11)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(road, environmentFillPaint)
        environmentFillPaint.shader = null
        environmentLinePaint.color = Color.argb(65, 122, 126, 133)
        environmentLinePaint.strokeWidth = maxOf(1f, width * 0.002f)
        canvas.drawLine(centerX - width * 0.025f, horizon, width * 0.10f, height, environmentLinePaint)
        canvas.drawLine(centerX + width * 0.025f, horizon, width * 0.90f, height, environmentLinePaint)
        environmentLinePaint.color = Color.argb(56, 174, 160, 132)
        environmentLinePaint.strokeWidth = maxOf(1f, width * 0.003f)
        for (mark in 0..5) {
            val fraction = (mark + 1f) / 7f
            val y = horizon + (height - horizon) * fraction
            val half = width * (0.003f + fraction * 0.010f)
            canvas.drawLine(centerX - half, y, centerX + half, y, environmentLinePaint)
        }
    }

    private fun drawRural(canvas: Canvas, width: Float, height: Float, scene: Int, progress: Float) {
        val horizon = height * 0.67f
        val distantHill = Path().apply {
            moveTo(0f, horizon)
            cubicTo(width * 0.22f, horizon - height * 0.09f, width * 0.48f, horizon + height * 0.015f,
                width * 0.72f, horizon - height * 0.065f)
            cubicTo(width * 0.85f, horizon - height * 0.10f, width * 0.94f, horizon - height * 0.025f, width, horizon - height * 0.055f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        environmentFillPaint.color = Color.rgb(9, 16, 17)
        canvas.drawPath(distantHill, environmentFillPaint)
        val fieldPhase = progress * 0.22f + scene * 0.4f
        environmentLinePaint.color = Color.argb(26, 137, 151, 132)
        environmentLinePaint.strokeWidth = maxOf(1f, height * 0.0016f)
        val fieldLine = Path()
        for (row in 0..7) {
            val y = height * (0.78f + row * 0.027f)
            val drift = kotlin.math.sin((fieldPhase + row).toDouble()).toFloat() * width * 0.015f
            fieldLine.reset()
            fieldLine.moveTo(-width * 0.06f, y + drift)
            fieldLine.cubicTo(width * 0.26f, y - height * 0.01f, width * 0.66f, y + height * 0.012f, width * 1.06f, y - drift)
            canvas.drawPath(fieldLine, environmentLinePaint)
        }
    }

}
