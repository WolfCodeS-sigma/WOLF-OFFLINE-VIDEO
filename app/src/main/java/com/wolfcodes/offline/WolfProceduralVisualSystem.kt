package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WolfProceduralVisualSystem {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun render(c: Canvas, w: Int, h: Int, text: String, scene: Int, progress: Float, time: Float) {
        val width = w.toFloat()
        val height = h.toFloat()
        val t = progress.coerceIn(0f, 1f)
        p.shader = android.graphics.LinearGradient(0f, 0f, width, height,
            intArrayOf(Color.rgb(5, 8, 17), Color.rgb(35, 7, 19), Color.rgb(2, 3, 8)),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width, height, p)
        p.shader = null
        drawFog(c, width, height, time)
        drawMoon(c, width, height, t)
        drawBuildings(c, width, height, scene)
        drawTrees(c, width, height, scene, time)
        drawDoor(c, width, height, time)
        drawGhost(c, width, height, text, t, time)
        drawRainAndDust(c, width, height, scene, time)
        drawCaption(c, width, height, text, t)
        p.shader = RadialGradient(width / 2f, height / 2f, maxOf(width, height) * .72f,
            intArrayOf(Color.TRANSPARENT, Color.argb(220, 0, 0, 0)), floatArrayOf(.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width, height, p)
        p.shader = null
    }

    private fun drawFog(c: Canvas, w: Float, h: Float, time: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(24, 155, 160, 175)
        for (i in 0..6) {
            val y = h * (.27f + i * .105f) + sin(time * .22f + i) * h * .015f
            val drift = sin(time * .12f + i * .8f) * w * .08f
            c.drawOval(drift - w * .2f, y, drift + w * 1.1f, y + h * .055f, p)
        }
    }

    private fun drawMoon(c: Canvas, w: Float, h: Float, t: Float) {
        val x = w * (.75f - .025f * t); val y = h * .18f
        p.color = Color.argb(38, 170, 8, 32); c.drawCircle(x, y, w * .16f, p)
        p.color = Color.rgb(115, 14, 31); c.drawCircle(x, y, w * .105f, p)
        p.color = Color.rgb(22, 6, 16); c.drawCircle(x + w * .035f, y - h * .012f, w * .09f, p)
    }

    private fun drawBuildings(c: Canvas, w: Float, h: Float, scene: Int) {
        for (i in 0..5) {
            val x = w * (i / 5.5f); val top = h * (.52f - ((i + scene) % 3) * .035f)
            p.color = Color.rgb(8, 9, 16); c.drawRect(x, top, x + w * .14f, h * .84f, p)
            p.color = Color.argb(100, 120, 12, 28)
            for (j in 0..2) c.drawRect(x + w * (.025f + j * .04f), top + h * .045f,
                x + w * (.04f + j * .04f), top + h * .075f, p)
        }
    }

    private fun drawTrees(c: Canvas, w: Float, h: Float, scene: Int, time: Float) {
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(2, 5, 8)
        for (i in 0..8) {
            val x = w * (i / 8f); val sway = sin(time * .8f + i + scene) * w * .018f
            val top = h * (.28f + (i % 3) * .035f)
            p.strokeWidth = w * (.009f + (i % 3) * .003f)
            path.reset(); path.moveTo(x, h * .9f)
            path.cubicTo(x + sway, h * .65f, x - sway, h * .45f, x + sway * .3f, top)
            c.drawPath(path, p)
            for (b in 0..2) {
                val by = top + h * (.1f + b * .07f)
                path.reset(); path.moveTo(x, by)
                path.quadTo(x + (if (b % 2 == 0) -w else w) * .07f, by - h * .06f, x + sway, by - h * .16f)
                c.drawPath(path, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun drawDoor(c: Canvas, w: Float, h: Float, time: Float) {
        val x = w * .42f + sin(time * .35f) * w * .005f
        p.color = Color.rgb(5, 5, 10)
        c.drawRoundRect(RectF(x, h * .48f, x + w * .19f, h * .88f), w * .008f, w * .008f, p)
        p.color = Color.argb(90, 110, 9, 24)
        c.drawRect(x + w * .025f, h * .51f, x + w * .165f, h * .85f, p)
        p.color = Color.rgb(1, 2, 5); c.drawRect(0f, h * .88f, w, h, p)
    }

    private fun drawGhost(c: Canvas, w: Float, h: Float, text: String, t: Float, time: Float) {
        val lower = text.lowercase()
        val cue = listOf("شبح", "ظل", "امرأة", "ظهر", "ghost", "shadow").any(lower::contains)
        if (!cue && t < .72f) return
        val reveal = if (cue) abs(sin(t * PI).toFloat()) else ((t - .72f) / .28f).coerceIn(0f, 1f)
        val alpha = (reveal * (105 + 35 * abs(sin(time)))).toInt().coerceIn(0, 145)
        val x = w * (.54f + sin(time * .25f) * .012f)
        val y = h * (.45f + sin(time * .7f) * .008f)
        p.color = Color.argb(alpha, 2, 3, 8)
        c.drawOval(x - w * .06f, y - h * .12f, x + w * .06f, y + h * .22f, p)
        c.drawOval(x - w * .04f, y - h * .23f, x + w * .04f, y - h * .09f, p)
        p.color = Color.argb(alpha / 2, 140, 12, 30)
        c.drawCircle(x - w * .016f, y - h * .17f, w * .005f, p)
        c.drawCircle(x + w * .016f, y - h * .17f, w * .005f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = w * .016f; p.color = Color.argb(alpha / 2, 0, 0, 0)
        val swing = sin(time * 1.2f) * w * .025f
        c.drawLine(x - w * .035f, y, x - w * .12f + swing, y + h * .13f, p)
        c.drawLine(x + w * .035f, y, x + w * .12f - swing, y + h * .13f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawRainAndDust(c: Canvas, w: Float, h: Float, scene: Int, time: Float) {
        for (i in 0..85) {
            val seed = i * 127.1 + scene * 311.7
            val x = ((sin(seed) * .5 + .5) * w + time * (5 + i % 11)) % w
            val y = ((cos(seed * .7) * .5 + .5) * h + time * (10 + i % 7)) % h
            p.color = Color.argb(25 + (25 * abs(sin(time + i))).toInt(), 160, 165, 180)
            p.strokeWidth = if (i % 4 == 0) 1.6f else .7f
            c.drawLine(x.toFloat(), y.toFloat(), x.toFloat() - w * .004f, y.toFloat() + h * .014f, p)
        }
    }

    private fun drawCaption(c: Canvas, w: Float, h: Float, text: String, t: Float) {
        val value = text.lowercase()
        val cue = when {
            listOf("خلفك", "وراءك", "اقترب").any(value::contains) -> "لا تلتفت"
            listOf("اختفى", "اختفت").any(value::contains) -> "لم يكن هنا"
            listOf("شبح", "ظهر", "ghost").any(value::contains) -> "كان يراقبك"
            else -> return
        }
        p.color = Color.argb((220 * abs(sin(t * PI))).toInt(), 210, 25, 45)
        p.textAlign = Paint.Align.CENTER; p.textSize = w * .055f
        p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        c.drawText(cue, w / 2f, h * .77f, p)
        p.textAlign = Paint.Align.LEFT
    }
}
