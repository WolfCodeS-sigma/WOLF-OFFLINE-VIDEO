package com.wolfcodes.offline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.sin

/**
 * Scene-local object layers for procedural artwork.
 *
 * Elements are independent render units with normalized placement, explicit depth,
 * z-order and deterministic motion. They are deliberately procedural shapes:
 * this system does not claim semantic instance segmentation or hidden-pixel recovery.
 */
object WolfSceneLayerSystem {

    enum class Depth { BACKGROUND, MIDGROUND, FOREGROUND }
    enum class Kind { CHAIR, DOOR, CURTAIN, BRANCH, HANGING_LIGHT, SHADOW }

    data class Element(
        val id: String,
        val kind: Kind,
        val depth: Depth,
        val anchorX: Float,
        val anchorY: Float,
        val size: Float,
        val zIndex: Int,
        val phase: Float,
        val motionAmplitude: Float
    )

    data class Pose(
        val translateX: Float,
        val translateY: Float,
        val rotationDegrees: Float,
        val scale: Float,
        val alpha: Int
    )

    fun plan(
        environment: WolfProceduralSceneProductionEngine.Environment,
        features: WolfProceduralMotionEngine.SceneFeatures,
        sceneIndex: Int
    ): List<Element> {
        require(sceneIndex >= 0) { "رقم المشهد غير صالح" }
        val elements = mutableListOf<Element>()
        fun add(
            id: String, kind: Kind, depth: Depth, x: Float, y: Float,
            size: Float, z: Int, amplitude: Float
        ) {
            elements += Element(
                id, kind, depth, x.coerceIn(0.05f, 0.95f),
                y.coerceIn(0.05f, 0.95f), size.coerceIn(0.08f, 0.8f),
                z, (sceneIndex * 0.71f + elements.size * 1.37f), amplitude
            )
        }

        when (environment) {
            WolfProceduralSceneProductionEngine.Environment.FOREST -> {
                add("near_branch_left", Kind.BRANCH, Depth.FOREGROUND, 0.12f, 0.28f, 0.72f, 80, 7.5f)
                add("near_branch_right", Kind.BRANCH, Depth.FOREGROUND, 0.88f, 0.34f, 0.68f, 81, 6.2f)
            }
            WolfProceduralSceneProductionEngine.Environment.CORRIDOR -> {
                add("far_door", Kind.DOOR, Depth.MIDGROUND, 0.5f, 0.54f, 0.22f, 30, 1.4f)
                add("ceiling_light", Kind.HANGING_LIGHT, Depth.MIDGROUND, 0.5f, 0.16f, 0.18f, 35, 2.8f)
            }
            WolfProceduralSceneProductionEngine.Environment.WINDOW -> {
                add("left_curtain", Kind.CURTAIN, Depth.FOREGROUND, 0.18f, 0.43f, 0.46f, 70, 5.5f)
                add("right_curtain", Kind.CURTAIN, Depth.FOREGROUND, 0.82f, 0.43f, 0.46f, 71, 5.5f)
            }
            WolfProceduralSceneProductionEngine.Environment.DOOR -> {
                add("door_leaf", Kind.DOOR, Depth.MIDGROUND, 0.52f, 0.52f, 0.48f, 40, 2.2f)
                add("foreground_shadow", Kind.SHADOW, Depth.FOREGROUND, 0.76f, 0.78f, 0.35f, 90, 2.8f)
            }
            WolfProceduralSceneProductionEngine.Environment.CEMETERY -> {
                add("graveyard_shadow", Kind.SHADOW, Depth.FOREGROUND, 0.68f, 0.82f, 0.42f, 90, 2.0f)
            }
            WolfProceduralSceneProductionEngine.Environment.ROOM -> {
                add("movable_chair", Kind.CHAIR, Depth.MIDGROUND, 0.68f, 0.72f, 0.38f, 50, 3.0f)
                add("ceiling_light", Kind.HANGING_LIGHT, Depth.BACKGROUND, 0.5f, 0.12f, 0.18f, 10, 2.4f)
            }
            WolfProceduralSceneProductionEngine.Environment.HAUNTED_HOUSE -> {
                add("house_door", Kind.DOOR, Depth.MIDGROUND, 0.5f, 0.65f, 0.34f, 45, 2.1f)
                add("foreground_branch", Kind.BRANCH, Depth.FOREGROUND, 0.9f, 0.3f, 0.62f, 85, 6.0f)
            }
        }
        if (features.hasGhost || features.hasReveal || features.hasChase) {
            add("moving_shadow", Kind.SHADOW, Depth.FOREGROUND, 0.74f, 0.76f, 0.30f, 100, 4.0f)
        }
        return elements.sortedWith(compareBy<Element> { it.depth.ordinal }.thenBy { it.zIndex })
    }

    fun pose(element: Element, progress: Float): Pose {
        val t = progress.coerceIn(0f, 1f)
        val wave = sin((t * 2.0 * PI + element.phase).toDouble()).toFloat()
        val slowWave = sin((t * PI + element.phase * 0.63).toDouble()).toFloat()
        return when (element.kind) {
            Kind.CHAIR -> Pose(wave * 0.018f * element.motionAmplitude / 3f, 0f,
                wave * 1.8f, 1f + slowWave * 0.004f, 255)
            Kind.DOOR -> Pose(0f, 0f, (0.5f + 0.5f * wave) * element.motionAmplitude,
                1f, 255)
            Kind.CURTAIN -> Pose(wave * 0.012f * element.motionAmplitude, 0f,
                wave * element.motionAmplitude, 1f, 238)
            Kind.BRANCH -> Pose(wave * 0.014f * element.motionAmplitude, 0f,
                wave * element.motionAmplitude * 0.72f, 1f, 255)
            Kind.HANGING_LIGHT -> Pose(0f, slowWave * 0.008f, wave * element.motionAmplitude * 0.28f,
                1f, (210 + 45 * (0.5f + 0.5f * wave)).toInt())
            Kind.SHADOW -> Pose(wave * 0.025f, slowWave * 0.012f,
                wave * 2.0f, 1f + 0.025f * slowWave,
                (75 + 45 * (0.5f + 0.5f * wave)).toInt())
        }
    }

    /** Draw each object independently, preserving the caller's canvas state. */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        elements: List<Element>,
        progress: Float
    ) {
        if (width <= 0 || height <= 0 || elements.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        elements.forEach { element ->
            val pose = pose(element, progress)
            val saved = canvas.save()
            try {
                val depthScale = when (element.depth) {
                    Depth.BACKGROUND -> 0.78f
                    Depth.MIDGROUND -> 1.0f
                    Depth.FOREGROUND -> 1.18f
                }
                val depthDrift = when (element.depth) {
                    Depth.BACKGROUND -> 0.003f
                    Depth.MIDGROUND -> 0.010f
                    Depth.FOREGROUND -> 0.018f
                }
                val parallax = sin((progress.coerceIn(0f, 1f) * 2.0 * PI + element.phase).toDouble()).toFloat()
                canvas.translate(
                    element.anchorX * width + parallax * width * depthDrift,
                    element.anchorY * height
                )
                canvas.rotate(pose.rotationDegrees)
                val unit = minOf(width, height) * element.size * depthScale
                canvas.scale(unit / 100f * pose.scale, unit / 100f * pose.scale)
                drawElement(canvas, paint, element.kind, pose.alpha)
            } finally {
                canvas.restoreToCount(saved)
            }
        }
    }

    private fun drawElement(canvas: Canvas, paint: Paint, kind: Kind, alpha: Int) {
        fun color(r: Int, g: Int, b: Int, a: Int = alpha) {
            paint.color = Color.argb(a.coerceIn(0, 255), r, g, b)
        }
        when (kind) {
            Kind.CHAIR -> {
                color(17, 13, 17); canvas.drawRoundRect(RectF(-28f, -42f, 28f, 8f), 7f, 7f, paint)
                color(28, 20, 23); canvas.drawRect(-25f, 5f, 25f, 15f, paint)
                color(8, 7, 10); canvas.drawRect(-21f, 14f, -15f, 49f, paint)
                canvas.drawRect(15f, 14f, 21f, 49f, paint)
            }
            Kind.DOOR -> {
                color(24, 12, 17); canvas.drawRoundRect(RectF(-34f, -55f, 34f, 55f), 3f, 3f, paint)
                color(65, 20, 29, alpha / 2); canvas.drawRect(-27f, -48f, 27f, 48f, paint)
                color(185, 115, 66); canvas.drawCircle(20f, 2f, 3f, paint)
            }
            Kind.CURTAIN -> {
                color(31, 12, 23); val path = Path().apply {
                    moveTo(-18f, -58f); cubicTo(10f, -20f, -24f, 20f, -5f, 58f)
                    lineTo(22f, 58f); cubicTo(3f, 10f, 30f, -20f, 18f, -58f); close()
                }; canvas.drawPath(path, paint)
            }
            Kind.BRANCH -> {
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 9f; color(13, 12, 17)
                val path = Path().apply {
                    moveTo(-55f, 55f); cubicTo(-15f, 18f, 5f, -8f, 54f, -55f)
                    moveTo(-8f, 18f); lineTo(-40f, -16f)
                    moveTo(16f, -5f); lineTo(48f, -25f)
                }; canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
            }
            Kind.HANGING_LIGHT -> {
                color(12, 10, 14); canvas.drawRect(-2f, -55f, 2f, -12f, paint)
                color(92, 22, 31, alpha); canvas.drawOval(RectF(-24f, -18f, 24f, 8f), paint)
                color(210, 65, 48, alpha / 3); canvas.drawCircle(0f, 8f, 17f, paint)
            }
            Kind.SHADOW -> {
                color(2, 2, 5, alpha); canvas.drawOval(RectF(-42f, -20f, 42f, 20f), paint)
                color(5, 3, 8, alpha / 2); canvas.drawOval(RectF(-24f, -58f, 24f, 12f), paint)
            }
        }
    }
}
