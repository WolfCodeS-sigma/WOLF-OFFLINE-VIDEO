package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lightweight deterministic character and prop animation for uploaded-image shots.
 * The existing procedural renderer remains the fallback and keeps its own motion path.
 */
object WolfCinematicMotionEngine {
    data class Features(
        val figure: Boolean,
        val ghost: Boolean,
        val door: Boolean,
        val curtain: Boolean,
        val branches: Boolean,
        val shadow: Boolean
    )

    data class Pose(
        val figureSway: Float,
        val figureBob: Float,
        val armSwing: Float,
        val doorAngle: Float,
        val curtainWave: Float,
        val branchAngle: Float,
        val shadowAlpha: Float
    )

    private val figurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val propPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val propStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun featuresFor(caption: String): Features {
        val text = caption.lowercase(java.util.Locale.ROOT)
        fun has(vararg words: String) = words.any(text::contains)
        val ghost = has("شبح", "أشباح", "طيف", "روح", "كيان", "ghost", "specter", "phantom")
        val figure = ghost || has("شخص", "رجل", "امرأة", "فتاة", "طفل", "الشخصية",
            "man", "woman", "person", "figure", "child")
        return Features(
            figure = figure,
            ghost = ghost,
            door = has("باب", "أبواب", "مقبض", "صرير", "door", "hinge"),
            curtain = has("ستارة", "ستائر", "نافذة", "شباك", "curtain", "window"),
            branches = has("غابة", "شجرة", "أشجار", "غصن", "أغصان", "forest", "tree", "branch"),
            shadow = has("ظل", "ظلال", "ظلّ", "shadow", "shadows")
        )
    }

    /** Pure deterministic motion sample. Every channel has a deliberately bounded range. */
    fun samplePose(progress: Float, sceneIndex: Int, shotIndex: Int): Pose {
        require(sceneIndex >= 0 && shotIndex >= 0)
        val t = progress.coerceIn(0f, 1f)
        val phase = sceneIndex * 0.79f + shotIndex * 1.13f
        val wave = sin((t * 2.0 * PI + phase).toDouble()).toFloat()
        val slow = sin((t * PI + phase * 0.37).toDouble()).toFloat()
        val reveal = smoothstep(((t - 0.12f) / 0.72f).coerceIn(0f, 1f))
        return Pose(
            figureSway = wave * 2.8f,
            figureBob = slow * 0.012f,
            armSwing = wave * 7.5f,
            doorAngle = -18f * smoothstep(((t - 0.18f) / 0.72f).coerceIn(0f, 1f)),
            curtainWave = wave * 0.075f,
            branchAngle = wave * 3.4f,
            shadowAlpha = (0.22f + 0.24f * (0.5f + 0.5f * slow)) * reveal
        )
    }

    /**
     * Draws independent rig/prop layers. Returns false only for invalid canvas bounds;
     * callers can safely continue with the already-rendered base frame.
     */
    @Synchronized
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        caption: String,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        return try {
            val features = featuresFor(caption)
            if (!(features.figure || features.door || features.curtain || features.branches || features.shadow)) {
                return true
            }
            val pose = samplePose(progress, sceneIndex, shotIndex)
            if (features.shadow) drawShadow(canvas, width, height, pose)
            if (features.branches) drawBranches(canvas, width, height, pose)
            if (features.curtain) drawCurtains(canvas, width, height, pose)
            if (features.door) drawDoor(canvas, width, height, pose)
            if (features.figure) drawFigure(canvas, width, height, pose, features.ghost)
            true
        } catch (_: OutOfMemoryError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun drawFigure(canvas: Canvas, w: Int, h: Int, pose: Pose, ghost: Boolean) {
        val saved = canvas.save()
        try {
            val unit = h * 0.34f
            val cx = w * 0.72f + pose.figureSway * w / 900f
            val cy = h * (0.60f + pose.figureBob)
            canvas.translate(cx, cy)
            canvas.scale(unit / 220f, unit / 220f)
            figurePaint.color = if (ghost) Color.argb(94, 190, 202, 218) else Color.argb(210, 10, 9, 13)
            figurePaint.shader = null
            canvas.drawOval(RectF(-37f, -112f, 37f, -34f), figurePaint)
            val body = Path().apply {
                moveTo(-31f, -38f)
                cubicTo(-54f, 8f, -51f, 80f, -67f, 164f)
                cubicTo(-36f, 178f, -17f, 160f, 0f, 174f)
                cubicTo(22f, 158f, 43f, 180f, 67f, 164f)
                cubicTo(48f, 82f, 53f, 4f, 31f, -38f)
                close()
            }
            canvas.drawPath(body, figurePaint)
            limbPaint.color = if (ghost) Color.argb(88, 198, 207, 220) else Color.argb(220, 7, 6, 10)
            limbPaint.strokeWidth = 17f
            canvas.save()
            canvas.rotate(pose.armSwing, -30f, -25f)
            canvas.drawLine(-30f, -25f, -72f, 68f, limbPaint)
            canvas.restore()
            canvas.save()
            canvas.rotate(-pose.armSwing, 30f, -25f)
            canvas.drawLine(30f, -25f, 72f, 68f, limbPaint)
            canvas.restore()
            if (ghost) {
                figurePaint.color = Color.argb(70, 170, 15, 32)
                canvas.drawOval(RectF(-24f, -84f, -10f, -66f), figurePaint)
                canvas.drawOval(RectF(10f, -84f, 24f, -66f), figurePaint)
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun drawDoor(canvas: Canvas, w: Int, h: Int, pose: Pose) {
        val x = w * 0.08f
        val top = h * 0.18f
        val doorW = w * 0.28f
        val doorH = h * 0.62f
        propStroke.color = Color.argb(110, 190, 184, 176)
        propStroke.strokeWidth = max(2f, w * 0.004f)
        canvas.drawRect(x, top, x + doorW, top + doorH, propStroke)
        val saved = canvas.save()
        try {
            canvas.rotate(pose.doorAngle, x, top + doorH)
            propPaint.color = Color.argb(205, 13, 12, 17)
            canvas.drawRect(x, top, x + doorW, top + doorH, propPaint)
            propStroke.color = Color.argb(150, 92, 74, 69)
            propStroke.strokeWidth = max(1.5f, w * 0.0025f)
            canvas.drawRect(x + doorW * 0.10f, top + doorH * 0.08f,
                x + doorW * 0.90f, top + doorH * 0.92f, propStroke)
            propPaint.color = Color.argb(210, 180, 126, 52)
            canvas.drawCircle(x + doorW * 0.82f, top + doorH * 0.52f, max(2f, w * 0.008f), propPaint)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun drawCurtains(canvas: Canvas, w: Int, h: Int, pose: Pose) {
        propStroke.color = Color.argb(120, 28, 24, 30)
        propStroke.strokeWidth = max(3f, w * 0.012f)
        for (side in 0..1) {
            val x = if (side == 0) w * 0.16f else w * 0.84f
            val direction = if (side == 0) 1f else -1f
            val sway = pose.curtainWave * w * direction
            val path = Path().apply {
                moveTo(x, h * 0.12f)
                cubicTo(x + sway, h * 0.30f, x - sway * 0.7f, h * 0.57f, x + sway * 0.35f, h * 0.84f)
            }
            canvas.drawPath(path, propStroke)
            propStroke.strokeWidth = max(1.5f, w * 0.004f)
            canvas.drawLine(x + sway * 0.15f, h * 0.15f, x + sway * 0.35f, h * 0.82f, propStroke)
            propStroke.strokeWidth = max(3f, w * 0.012f)
        }
    }

    private fun drawBranches(canvas: Canvas, w: Int, h: Int, pose: Pose) {
        val saved = canvas.save()
        try {
            canvas.translate(w * 0.96f, h * 0.78f)
            canvas.rotate(pose.branchAngle)
            propStroke.color = Color.argb(150, 7, 8, 12)
            propStroke.strokeWidth = max(2f, w * 0.006f)
            val path = Path().apply {
                moveTo(0f, 0f)
                cubicTo(-w * 0.05f, -h * 0.18f, -w * 0.10f, -h * 0.31f, -w * 0.19f, -h * 0.48f)
                moveTo(-w * 0.08f, -h * 0.23f)
                cubicTo(-w * 0.18f, -h * 0.27f, -w * 0.22f, -h * 0.34f, -w * 0.25f, -h * 0.39f)
                moveTo(-w * 0.12f, -h * 0.34f)
                cubicTo(-w * 0.05f, -h * 0.39f, -w * 0.04f, -h * 0.44f, -w * 0.03f, -h * 0.49f)
            }
            canvas.drawPath(path, propStroke)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun drawShadow(canvas: Canvas, w: Int, h: Int, pose: Pose) {
        val alpha = (pose.shadowAlpha * 125f).toInt().coerceIn(0, 64)
        shadowPaint.color = Color.argb(alpha, 0, 0, 0)
        canvas.drawOval(RectF(w * 0.48f, h * 0.79f, w * 0.90f, h * 0.91f), shadowPaint)
    }

    private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)
}
