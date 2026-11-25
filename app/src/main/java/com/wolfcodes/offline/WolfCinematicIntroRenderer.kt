package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.sin

/**
 * Locally rendered, deterministic opening identity.
 *
 * The mark is a brand symbol only. It is not used as a stand-in for a wolf character,
 * creature, ghost, or any story asset.
 */
internal object WolfCinematicIntroRenderer {
    data class MotionState(
        val cameraScale: Float,
        val atmosphereReveal: Float,
        val clawReveal: Float,
        val wolfReveal: Float,
        val eyePulse: Float,
        val titleReveal: Float,
        val signatureReveal: Float,
        val finalFade: Float
    )

    fun progressForFrame(frameIndex: Int, frameCount: Int): Float {
        require(frameCount > 0) { "عدد إطارات المقدمة يجب أن يكون موجبًا" }
        require(frameIndex in 0 until frameCount) { "موضع إطار المقدمة خارج النطاق" }
        return if (frameCount == 1) 1f else frameIndex.toFloat() / (frameCount - 1).toFloat()
    }

    /** Pure, deterministic animation curve, also used by JVM tests. */
    fun motionAt(progress: Float): MotionState {
        val t = progress.coerceIn(0f, 1f)
        val reveal = smoothStep(0f, 0.12f, t)
        val claw = smoothStep(0.10f, 0.43f, t)
        val wolf = smoothStep(0.27f, 0.62f, t)
        val eyes = smoothStep(0.46f, 0.68f, t)
        val title = smoothStep(0.59f, 0.82f, t)
        val signature = smoothStep(0.74f, 0.91f, t)
        val eyePulse = (0.70f + 0.30f * sin(t * (PI * 5.0).toFloat())).coerceIn(0.35f, 1f)
        return MotionState(
            cameraScale = 0.94f + 0.10f * easeOutCubic(t),
            atmosphereReveal = reveal,
            clawReveal = claw,
            wolfReveal = wolf,
            eyePulse = eyes * eyePulse,
            titleReveal = title,
            signatureReveal = signature,
            finalFade = smoothStep(0.925f, 1f, t)
        )
    }

    fun draw(canvas: Canvas, width: Int, height: Int, progress: Float) {
        require(width > 0 && height > 0) { "أبعاد إطار المقدمة غير صالحة" }
        val t = progress.coerceIn(0f, 1f)
        val motion = motionAt(t)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w * 0.5f
        val centerY = h * 0.43f
        val minSide = minOf(w, h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(Color.rgb(2, 2, 5))
        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.rgb(43, 4, 14), Color.rgb(9, 2, 7), Color.rgb(2, 2, 5)),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val save = canvas.save()
        try {
            canvas.scale(motion.cameraScale, motion.cameraScale, centerX, centerY)

            // Distant blood-red moon and a restrained atmospheric halo.
            val moonX = w * 0.76f + sin(t * (PI * 2.0).toFloat()) * w * 0.018f
            val moonY = h * 0.245f - t * h * 0.012f
            val moonRadius = minSide * 0.105f
            paint.shader = RadialGradient(
                moonX,
                moonY,
                moonRadius * 1.85f,
                intArrayOf(
                    Color.argb((145 * motion.atmosphereReveal).toInt(), 205, 12, 32),
                    Color.argb((70 * motion.atmosphereReveal).toInt(), 112, 4, 19),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(moonX, moonY, moonRadius * 1.85f, paint)
            paint.shader = null
            paint.color = Color.argb((210 * motion.atmosphereReveal).toInt(), 91, 5, 20)
            canvas.drawCircle(moonX, moonY, moonRadius, paint)
            paint.color = Color.argb((125 * motion.atmosphereReveal).toInt(), 18, 2, 8)
            canvas.drawCircle(
                moonX + moonRadius * 0.23f,
                moonY - moonRadius * 0.16f,
                moonRadius * 0.76f,
                paint
            )

            // A moving light sweep crosses the mark once during the reveal.
            val sweepCenter = w * (-0.32f + 1.66f * t)
            val sweepWidth = minSide * 0.20f
            val sweepAlpha = (82f * sin(PI.toFloat() * t).coerceAtLeast(0f) *
                motion.atmosphereReveal).toInt().coerceIn(0, 82)
            paint.shader = LinearGradient(
                sweepCenter - sweepWidth,
                0f,
                sweepCenter + sweepWidth,
                0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(sweepAlpha, 255, 32, 52), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            // Slow drifting ash flecks make the frame move even between logo keyframes.
            for (index in 0 until 18) {
                val seed = index.toFloat()
                val baseX = ((index * 47) % 101) / 100f
                val driftX = sin(t * (PI * 2.0).toFloat() + seed * 0.73f) * 0.018f
                val x = ((baseX + driftX + 1f) % 1f) * w
                val yFraction = (0.12f + ((index * 19) % 77) / 100f + t * (0.08f + seed * 0.002f)) % 0.94f
                val y = yFraction * h
                val pulse = (0.5f + 0.5f * sin(t * 7f + seed)).coerceIn(0f, 1f)
                val alpha = (18f + 42f * pulse) * motion.atmosphereReveal
                paint.color = Color.argb(alpha.toInt().coerceIn(0, 60), 206, 34, 48)
                canvas.drawCircle(x, y, 1.1f + (index % 3) * 0.55f, paint)
            }

            val logoScale = minSide * 0.365f * (0.82f + 0.18f * easeOutCubic(motion.wolfReveal))
            val cx = centerX
            val cy = centerY

            // Three restrained claw-light streaks reveal the silhouette.
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = (minSide * 0.006f).coerceIn(1.5f, 4.5f)
            for (index in 0..2) {
                val start = 0.10f + index * 0.075f
                val localReveal = smoothStep(start, start + 0.20f, t)
                val alpha = (116f * localReveal * motion.atmosphereReveal).toInt().coerceIn(0, 116)
                if (alpha > 0) {
                    val path = Path().apply {
                        val offset = (index - 1) * logoScale * 0.23f
                        moveTo(cx - logoScale * 0.68f + offset, cy - logoScale * 0.48f)
                        cubicTo(
                            cx - logoScale * 0.31f + offset,
                            cy - logoScale * 0.29f,
                            cx + logoScale * 0.06f + offset,
                            cy + logoScale * 0.08f,
                            cx + logoScale * 0.55f + offset,
                            cy + logoScale * 0.42f
                        )
                    }
                    paint.color = Color.argb(alpha, 220, 22, 44)
                    paint.setShadowLayer(logoScale * 0.035f, 0f, 0f, Color.RED)
                    canvas.drawPath(path, paint)
                    paint.clearShadowLayer()
                }
            }

            val wolf = Path().apply {
                moveTo(cx - logoScale * 0.47f, cy + logoScale * 0.44f)
                lineTo(cx - logoScale * 0.53f, cy - logoScale * 0.34f)
                lineTo(cx - logoScale * 0.22f, cy - logoScale * 0.18f)
                lineTo(cx, cy - logoScale * 0.55f)
                lineTo(cx + logoScale * 0.22f, cy - logoScale * 0.18f)
                lineTo(cx + logoScale * 0.53f, cy - logoScale * 0.34f)
                lineTo(cx + logoScale * 0.47f, cy + logoScale * 0.44f)
                lineTo(cx + logoScale * 0.18f, cy + logoScale * 0.66f)
                lineTo(cx, cy + logoScale * 0.78f)
                lineTo(cx - logoScale * 0.18f, cy + logoScale * 0.66f)
                close()
            }
            val wolfAlpha = (255f * motion.wolfReveal).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(wolfAlpha, 3, 2, 5)
            paint.setShadowLayer(logoScale * 0.12f, 0f, 0f, Color.argb(200, 205, 8, 28))
            canvas.drawPath(wolf, paint)
            paint.clearShadowLayer()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (logoScale * 0.022f).coerceAtLeast(1.5f)
            paint.color = Color.argb((205f * motion.wolfReveal).toInt().coerceIn(0, 205), 168, 12, 34)
            canvas.drawPath(wolf, paint)
            paint.style = Paint.Style.FILL

            val eyeAlpha = (255f * motion.eyePulse).toInt().coerceIn(0, 255)
            paint.color = Color.argb(eyeAlpha, 255, 28, 49)
            paint.setShadowLayer(logoScale * 0.075f, 0f, 0f, Color.RED)
            val eyeHeight = logoScale * 0.045f
            canvas.drawOval(
                RectF(cx - logoScale * 0.23f, cy + logoScale * 0.08f,
                    cx - logoScale * 0.10f, cy + logoScale * 0.08f + eyeHeight),
                paint
            )
            canvas.drawOval(
                RectF(cx + logoScale * 0.10f, cy + logoScale * 0.08f,
                    cx + logoScale * 0.23f, cy + logoScale * 0.08f + eyeHeight),
                paint
            )
            paint.clearShadowLayer()

            // The official names and signature arrive in sequence, never as an unrelated slogan.
            val titleAlpha = (255f * motion.titleReveal).toInt().coerceIn(0, 255)
            val titleRise = (1f - motion.titleReveal) * h * 0.022f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("serif", Typeface.BOLD)
            paint.textSize = (h * 0.062f).coerceIn(30f, 72f)
            paint.color = Color.argb(titleAlpha, 255, 255, 255)
            paint.setShadowLayer(12f, 0f, 0f, Color.RED)
            canvas.drawText(WolfCinematicIdentity.BRAND_NAME, cx, h * 0.82f + titleRise, paint)
            paint.clearShadowLayer()

            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = (h * 0.027f).coerceIn(16f, 30f)
            paint.color = Color.argb(titleAlpha, 235, 42, 57)
            canvas.drawText(WolfCinematicIdentity.ARABIC_BRAND_NAME, cx, h * 0.875f + titleRise, paint)

            val signatureAlpha = (205f * motion.signatureReveal).toInt().coerceIn(0, 205)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = (h * 0.019f).coerceIn(12f, 21f)
            paint.color = Color.argb(signatureAlpha, 218, 203, 205)
            canvas.drawText(WolfCinematicIdentity.INTRO_SIGNATURE, cx, h * 0.925f + titleRise, paint)
        } finally {
            canvas.restoreToCount(save)
        }

        // A full black final frame creates a clean, intentional transition into scene one.
        val openingBlack = (255f * (1f - motion.atmosphereReveal)).toInt().coerceIn(0, 255)
        if (openingBlack > 0) {
            paint.color = Color.argb(openingBlack, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        val closingBlack = (255f * motion.finalFade).toInt().coerceIn(0, 255)
        if (closingBlack > 0) {
            paint.color = Color.argb(closingBlack, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }

    private fun smoothStep(start: Float, end: Float, value: Float): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        val amount = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return amount * amount * (3f - 2f * amount)
    }
}
