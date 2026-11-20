package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lightweight offline 2.5D compositor for analyzed still images.
 *
 * It animates the three existing depth planes independently. It does not claim
 * semantic object segmentation, true 3D geometry, or reconstruction of hidden pixels.
 */
object Wolf25DCinematicImageEngine {
    data class FramePose(
        val zoom: Float,
        val panX: Float,
        val panY: Float,
        val rotationDegrees: Float,
        val depthParallax: Float,
        val breathing: Float
    )

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gradePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Pure deterministic camera sample, safe to exercise in local JVM tests. */
    fun samplePose(
        move: WolfUploadedImageEngine.CameraMove,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float,
        depth: Float,
        intensity: Float
    ): FramePose {
        require(sceneIndex >= 0 && shotIndex >= 0)
        val t = smooth(progress)
        val d = depth.coerceIn(0f, 1f)
        val power = intensity.coerceIn(0f, 1f)
        val phase = sceneIndex * 0.71f + shotIndex * 0.43f
        val wave = sin((phase + t * 2.0 * PI).toDouble()).toFloat()
        val secondary = cos((phase * 0.61f + t * 2.0 * PI * 0.72).toDouble()).toFloat()
        val zoomPath = when (move) {
            WolfUploadedImageEngine.CameraMove.SLOW_PUSH -> 1.018f + 0.105f * t
            WolfUploadedImageEngine.CameraMove.SLOW_PULL -> 1.112f - 0.092f * t
            WolfUploadedImageEngine.CameraMove.LEFT_TRACK,
            WolfUploadedImageEngine.CameraMove.RIGHT_TRACK -> 1.045f + 0.025f * sin((t * PI).toDouble()).toFloat()
            WolfUploadedImageEngine.CameraMove.REVEAL -> 1.025f + 0.082f * t
            WolfUploadedImageEngine.CameraMove.HANDHELD -> 1.045f + 0.012f * wave
        }
        val direction = when (move) {
            WolfUploadedImageEngine.CameraMove.LEFT_TRACK -> 1f
            WolfUploadedImageEngine.CameraMove.RIGHT_TRACK -> -1f
            WolfUploadedImageEngine.CameraMove.REVEAL -> if (shotIndex % 2 == 0) -1f else 1f
            else -> 0f
        }
        val track = when (move) {
            WolfUploadedImageEngine.CameraMove.LEFT_TRACK,
            WolfUploadedImageEngine.CameraMove.RIGHT_TRACK -> direction * 0.038f * (t - 0.5f)
            WolfUploadedImageEngine.CameraMove.REVEAL -> direction * 0.026f * (t - 0.5f)
            WolfUploadedImageEngine.CameraMove.HANDHELD -> wave * 0.006f
            else -> wave * 0.0025f
        }
        val vertical = when (move) {
            WolfUploadedImageEngine.CameraMove.REVEAL -> -0.014f * (t - 0.5f)
            WolfUploadedImageEngine.CameraMove.HANDHELD -> secondary * 0.005f
            else -> secondary * 0.0025f
        }
        val rotation = when (move) {
            WolfUploadedImageEngine.CameraMove.HANDHELD -> wave * 0.22f
            WolfUploadedImageEngine.CameraMove.REVEAL -> wave * 0.09f
            else -> wave * 0.035f
        }
        val parallax = (0.28f + d * 0.92f) * power
        val breathing = 1f + sin((t * 2.0 * PI + phase * 0.3).toDouble()).toFloat() * 0.0035f * power
        return FramePose(
            zoom = 1f + (zoomPath - 1f) * (0.72f + d * 0.28f),
            panX = track * parallax,
            panY = vertical * parallax,
            rotationDegrees = rotation * (0.45f + d * 0.55f),
            depthParallax = parallax,
            breathing = breathing
        )
    }

    /** Draws background, midground, then foreground with depth-scaled camera movement. */
    @Synchronized
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        sceneIndex: Int,
        shotIndex: Int,
        progress: Float,
        plan: WolfUploadedImageEngine.MotionPlan,
        layers: WolfImageDepthSegmenter.Layers,
        source: Rect
    ) {
        if (width <= 0 || height <= 0) return
        val bitmaps = listOf(layers.background, layers.midground, layers.foreground)
        if (bitmaps.any { it.isRecycled }) return
        val depths = floatArrayOf(0.12f, 0.55f, 1.0f)
        val overscan = floatArrayOf(1.012f, 1.032f, 1.058f)
        val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawColor(Color.rgb(7, 8, 13))
        bitmaps.forEachIndexed { index, bitmap ->
            val depth = depths[index]
            val pose = samplePose(plan.cameraMove, sceneIndex, shotIndex, progress, depth, plan.intensity)
            val saved = canvas.save()
            try {
                val px = width * (0.5f + pose.panX * 1.35f)
                val py = height * (0.5f + pose.panY * 1.35f)
                canvas.translate(px, py)
                canvas.scale(pose.zoom * pose.breathing * overscan[index], pose.zoom * pose.breathing * overscan[index])
                canvas.rotate(pose.rotationDegrees, 0f, 0f)
                canvas.translate(-width * 0.5f, -height * 0.5f)
                canvas.drawBitmap(bitmap, source, destination, bitmapPaint)
            } finally {
                canvas.restoreToCount(saved)
            }
        }
        drawCinematicGrade(canvas, width, height, progress, plan)
        return true
    }

    private fun drawCinematicGrade(
        canvas: Canvas,
        width: Int,
        height: Int,
        progress: Float,
        plan: WolfUploadedImageEngine.MotionPlan
    ) {
        val strength = plan.intensity.coerceIn(0f, 1f)
        gradePaint.shader = null
        gradePaint.style = Paint.Style.FILL
        gradePaint.color = Color.argb((8f + strength * 13f).toInt(), 8, 13, 25)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradePaint)

        if (plan.atmosphere == WolfUploadedImageEngine.Atmosphere.GHOST ||
            plan.atmosphere == WolfUploadedImageEngine.Atmosphere.DOOR ||
            plan.atmosphere == WolfUploadedImageEngine.Atmosphere.MIRROR
        ) {
            val pulse = 0.5f + 0.5f * sin((progress.coerceIn(0f, 1f) * 2.0 * PI).toDouble()).toFloat()
            val alpha = (strength * pulse * 12f).toInt().coerceIn(0, 12)
            gradePaint.color = Color.argb(alpha, 105, 5, 20)
            canvas.drawCircle(width * plan.focusX, height * plan.focusY, max(width, height) * 0.20f, gradePaint)
        }

        val vignette = RadialGradient(
            width * 0.5f, height * 0.48f, max(width, height) * 0.76f,
            intArrayOf(Color.TRANSPARENT, Color.argb((26f + strength * 34f).toInt(), 0, 0, 0)),
            floatArrayOf(0.48f, 1f), Shader.TileMode.CLAMP
        )
        vignettePaint.shader = vignette
        vignettePaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        vignettePaint.shader = null
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
