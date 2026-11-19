package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Deterministic local artwork for scenes that do not have a user-supplied image.
 * This is procedural illustration, not AI-generated photography.
 */
object WolfSceneArtEngine {
    fun draw(canvas: Canvas, width: Int, height: Int, caption: String, scene: Int, progress: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = progress.coerceIn(0f, 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pulse = (wave((t * 6.2831853f) + scene * 0.83f) * 0.5f + 0.5f)
        val movement = OfflineMotionEngine.profile(caption).movement
        val cameraStyle = ((scene % 4) + 4) % 4
        val direction = if (cameraStyle % 2 == 0) 1f else -1f
        val cameraWave = wave(t * 6.2831853f + scene * 0.91f)
        val zoom = when (movement) {
            OfflineMotionEngine.Movement.CHASE -> 1.0f + 0.12f * t
            OfflineMotionEngine.Movement.REVEAL -> 1.0f + 0.10f * t
            OfflineMotionEngine.Movement.SUSPENSE -> when (cameraStyle) {
                0 -> 1.0f + 0.075f * t
                1 -> 1.075f - 0.065f * t
                2 -> 1.035f + 0.025f * cameraWave
                else -> 1.0f + 0.055f * t
            }
        }
        val panX = when (cameraStyle) {
            0 -> cameraWave * w * 0.024f
            1 -> direction * (t - 0.5f) * w * 0.055f
            2 -> wave(t * 3.1415926f + scene) * w * 0.030f
            else -> -cameraWave * w * 0.018f
        } + if (movement == OfflineMotionEngine.Movement.CHASE) {
            wave(t * 31.4159f + scene) * w * 0.006f
        } else 0f
        val panY = wave(t * 3.1415926f + scene * 0.67f) * h * 0.012f
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.translate(w * 0.5f + panX, h * 0.5f + panY)
        canvas.scale(zoom, zoom)
        canvas.translate(-w * 0.5f, -h * 0.5f)

        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.rgb(5, 4, 11), Color.rgb(22, 7, 18), Color.rgb(3, 4, 9)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // A restrained blood moon and sparse night lights establish the horror palette.
        val moonX = w * (0.76f - t * 0.035f)
        val moonY = h * 0.22f
        val moonRadius = minOf(w, h) * 0.105f
        p.color = Color.argb(24, 235, 22, 43)
        canvas.drawCircle(moonX, moonY, moonRadius * 1.9f, p)
        p.color = Color.rgb(105, 13, 29)
        canvas.drawCircle(moonX, moonY, moonRadius, p)
        p.color = Color.argb(70, 13, 4, 12)
        canvas.drawCircle(moonX + moonRadius * 0.34f, moonY - moonRadius * 0.12f, moonRadius * 0.82f, p)

        p.color = Color.argb(95, 220, 200, 190)
        for (i in 0 until 22) {
            val x = ((i * 97 + scene * 41) % 997) / 997f * w
            val y = ((i * 53 + scene * 29) % 421) / 421f * h * 0.55f
            canvas.drawCircle(x, y, if (i % 5 == 0) 2.2f else 1.1f, p)
        }

        val lower = h * 0.72f
        val forest = has(caption, "غابة", "شجرة", "أشجار", "الغابة", "forest", "trees")
        val corridor = has(caption, "ممر", "طرقة", "ممرًا", "الممر", "hallway", "corridor")
        val window = has(caption, "نافذة", "شباك", "النافذة", "زجاج", "window")
        val door = has(caption, "باب", "الباب", "أبواب", "مدخل", "door")
        val cemetery = has(caption, "قبر", "مقبرة", "قبور", "مقابر", "cemetery", "grave")
        val room = has(caption, "غرفة", "المنزل", "بيت", "منزل", "حجرة", "room", "house")

        when {
            forest -> drawForest(canvas, p, w, h, t, scene)
            corridor -> drawCorridor(canvas, p, w, h, t)
            cemetery -> drawCemetery(canvas, p, w, h, t, scene)
            window -> drawWindow(canvas, p, w, h, t)
            door -> drawDoor(canvas, p, w, h, t, pulse)
            room -> drawRoom(canvas, p, w, h, t)
            else -> drawHauntedHouse(canvas, p, w, h, t)
        }

        // Low fog drifts slowly across the foreground.
        p.style = Paint.Style.FILL
        p.color = Color.argb(22 + (pulse * 12f).toInt(), 175, 173, 184)
        val fogShift = wave(t * 6.2831853f + scene) * w * 0.025f
        canvas.drawOval(RectF(-w * 0.12f + fogShift, lower - h * 0.06f, w * 0.62f + fogShift, h * 0.88f), p)
        canvas.drawOval(RectF(w * 0.35f - fogShift, lower - h * 0.02f, w * 1.12f - fogShift, h * 0.91f), p)

        val hauntedPresence = has(caption, "ظل", "شبح", "امرأة", "شخص", "كائن", "وحش", "ظهر", "وقف", "ghost", "shadow", "creature")
        if (hauntedPresence || scene % 3 == 1) drawSilhouette(canvas, p, w, h, t, scene)

        // Dark edge treatment keeps the frame readable on small phone screens.
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(65, 0, 0, 0), Color.TRANSPARENT, Color.argb(100, 0, 0, 0)),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        canvas.restore()
        p.color = Color.argb(90, 190, 8, 28)
        canvas.drawRect(0f, 0f, w * (0.15f + t * 0.7f), maxOf(2f, h * 0.004f), p)
    }

    private fun drawForest(c: Canvas, p: Paint, w: Float, h: Float, t: Float, scene: Int) {
        p.color = Color.rgb(5, 12, 13)
        c.drawRect(0f, h * 0.62f, w, h, p)
        for (i in -2..8) {
            val x = (i * w / 7f) + (wave(t * 6.28f + i) * w * 0.009f)
            val top = h * (0.22f + ((i * 13 + scene * 7).absoluteMod(4)) * 0.045f)
            p.color = Color.rgb(7, 15, 15)
            c.drawRect(x, top, x + w * 0.035f, h * 0.82f, p)
            val branches = Path().apply {
                moveTo(x - w * 0.12f, top + h * 0.24f)
                lineTo(x + w * 0.017f, top - h * 0.08f)
                lineTo(x + w * 0.16f, top + h * 0.24f)
                close()
                moveTo(x - w * 0.10f, top + h * 0.39f)
                lineTo(x + w * 0.017f, top + h * 0.08f)
                lineTo(x + w * 0.14f, top + h * 0.39f)
                close()
            }
            p.color = Color.rgb(6, 18, 17)
            c.drawPath(branches, p)
        }
    }

    private fun drawCorridor(c: Canvas, p: Paint, w: Float, h: Float, t: Float) {
        val vanishingX = w * (0.5f + wave(t * 6.28f) * 0.012f)
        val vanishingY = h * 0.34f
        p.color = Color.rgb(20, 12, 17)
        c.drawRect(0f, 0f, w, h, p)
        val walls = Path().apply {
            moveTo(0f, h * 0.08f); lineTo(vanishingX, vanishingY); lineTo(vanishingX, h * 0.78f)
            lineTo(0f, h); close()
            moveTo(w, h * 0.08f); lineTo(vanishingX, vanishingY); lineTo(vanishingX, h * 0.78f)
            lineTo(w, h); close()
        }
        p.color = Color.rgb(35, 17, 24)
        c.drawPath(walls, p)
        p.color = Color.rgb(8, 7, 12)
        val floor = Path().apply {
            moveTo(0f, h); lineTo(w, h); lineTo(vanishingX, h * 0.68f); lineTo(vanishingX, h * 0.68f); close()
        }
        c.drawPath(floor, p)
        p.color = Color.argb(115, 151, 18, 34)
        p.strokeWidth = maxOf(2f, w * 0.003f)
        p.style = Paint.Style.STROKE
        c.drawLine(0f, h * 0.04f, vanishingX, vanishingY, p)
        c.drawLine(w, h * 0.04f, vanishingX, vanishingY, p)
        c.drawLine(0f, h * 0.92f, vanishingX, h * 0.68f, p)
        c.drawLine(w, h * 0.92f, vanishingX, h * 0.68f, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(80, 8, 22)
        c.drawRect(vanishingX - w * 0.07f, h * 0.23f, vanishingX + w * 0.07f, h * 0.69f, p)
    }

    private fun drawWindow(c: Canvas, p: Paint, w: Float, h: Float, t: Float) {
        p.color = Color.rgb(25, 12, 19)
        c.drawRect(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.79f, p)
        p.color = Color.rgb(4, 7, 14)
        c.drawRect(w * 0.29f, h * 0.13f, w * 0.72f, h * 0.65f, p)
        p.color = Color.rgb(105, 11, 28)
        c.drawCircle(w * (0.64f - t * 0.025f), h * 0.29f, h * 0.09f, p)
        p.color = Color.rgb(9, 8, 15)
        p.strokeWidth = maxOf(5f, w * 0.014f)
        p.style = Paint.Style.STROKE
        c.drawRect(w * 0.29f, h * 0.13f, w * 0.72f, h * 0.65f, p)
        c.drawLine(w * 0.505f, h * 0.13f, w * 0.505f, h * 0.65f, p)
        c.drawLine(w * 0.29f, h * 0.39f, w * 0.72f, h * 0.39f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawDoor(c: Canvas, p: Paint, w: Float, h: Float, t: Float, pulse: Float) {
        p.color = Color.rgb(24, 13, 18)
        c.drawRect(w * 0.12f, h * 0.08f, w * 0.88f, h * 0.82f, p)
        p.color = Color.rgb(5, 5, 9)
        c.drawRect(w * 0.34f, h * 0.16f, w * 0.69f, h * 0.82f, p)
        p.color = Color.rgb(69, 18, 27)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(4f, w * 0.009f)
        c.drawRect(w * 0.34f, h * 0.16f, w * 0.69f, h * 0.82f, p)
        p.style = Paint.Style.FILL
        p.color = Color.argb((75 + pulse * 70f).toInt(), 160, 8, 25)
        c.drawRect(w * 0.665f, h * 0.19f, w * 0.69f, h * 0.79f, p)
        p.color = Color.rgb(190, 135, 82)
        c.drawCircle(w * 0.63f, h * 0.49f, maxOf(3f, w * 0.009f), p)
    }

    private fun drawRoom(c: Canvas, p: Paint, w: Float, h: Float, t: Float) {
        p.color = Color.rgb(26, 15, 21)
        c.drawRect(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.78f, p)
        p.color = Color.rgb(7, 7, 12)
        c.drawRect(w * 0.17f, h * 0.15f, w * 0.42f, h * 0.52f, p)
        p.color = Color.rgb(100, 12, 28)
        c.drawRect(w * 0.2f, h * 0.18f, w * 0.39f, h * 0.49f, p)
        p.color = Color.rgb(9, 7, 12)
        c.drawRect(w * 0.12f, h * 0.72f, w * 0.88f, h * 0.86f, p)
        p.color = Color.argb(90, 190, 20, 38)
        c.drawRect(w * (0.2f + t * 0.04f), h * 0.56f, w * (0.8f - t * 0.04f), h * 0.59f, p)
    }

    private fun drawCemetery(c: Canvas, p: Paint, w: Float, h: Float, t: Float, scene: Int) {
        p.color = Color.rgb(5, 9, 13)
        c.drawRect(0f, h * 0.62f, w, h, p)
        for (i in 0..6) {
            val x = w * (i + 0.5f) / 7f
            val top = h * (0.53f + (i % 3) * 0.045f)
            val stone = RectF(x - w * 0.045f, top, x + w * 0.045f, h * 0.83f)
            p.color = Color.rgb(38, 39, 45)
            c.drawRoundRect(stone, w * 0.018f, w * 0.018f, p)
            p.color = Color.rgb(10, 12, 16)
            c.drawRect(x - w * 0.006f, top + h * 0.07f, x + w * 0.006f, top + h * 0.18f, p)
        }
    }

    private fun drawHauntedHouse(c: Canvas, p: Paint, w: Float, h: Float, t: Float) {
        p.color = Color.rgb(5, 8, 13)
        val house = Path().apply {
            moveTo(w * 0.13f, h * 0.82f); lineTo(w * 0.13f, h * 0.43f)
            lineTo(w * 0.31f, h * 0.28f); lineTo(w * 0.43f, h * 0.43f)
            lineTo(w * 0.56f, h * 0.24f); lineTo(w * 0.83f, h * 0.43f)
            lineTo(w * 0.83f, h * 0.82f); close()
        }
        c.drawPath(house, p)
        p.color = Color.rgb(95, 12, 27)
        c.drawRect(w * 0.22f, h * 0.53f, w * 0.31f, h * 0.65f, p)
        c.drawRect(w * 0.65f, h * 0.53f, w * 0.75f, h * 0.65f, p)
        p.color = Color.rgb(2, 3, 7)
        c.drawRect(w * 0.45f, h * 0.61f, w * 0.56f, h * 0.82f, p)
    }

    private fun drawSilhouette(c: Canvas, p: Paint, w: Float, h: Float, t: Float, scene: Int) {
        val x = w * (0.52f + wave(t * 6.2831853f + scene * 0.7f) * 0.025f)
        val ground = h * 0.83f
        p.color = Color.argb(215, 1, 2, 5)
        c.drawOval(RectF(x - w * 0.045f, ground - h * 0.47f, x + w * 0.045f, ground - h * 0.35f), p)
        val cloak = Path().apply {
            moveTo(x - w * 0.045f, ground - h * 0.36f)
            lineTo(x - w * 0.095f, ground - h * 0.08f)
            lineTo(x - w * 0.12f, ground)
            lineTo(x + w * 0.12f, ground)
            lineTo(x + w * 0.095f, ground - h * 0.08f)
            lineTo(x + w * 0.045f, ground - h * 0.36f)
            close()
        }
        c.drawPath(cloak, p)
        p.color = Color.argb(225, 220, 12, 35)
        c.drawCircle(x - w * 0.018f, ground - h * 0.405f, maxOf(2f, w * 0.004f), p)
        c.drawCircle(x + w * 0.018f, ground - h * 0.405f, maxOf(2f, w * 0.004f), p)
    }

    private fun wave(angle: Float): Float = kotlin.math.sin(angle.toDouble()).toFloat()

    private fun has(text: String, vararg terms: String): Boolean =
        terms.any { term -> text.contains(term, ignoreCase = true) }

    private fun Int.absoluteMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
