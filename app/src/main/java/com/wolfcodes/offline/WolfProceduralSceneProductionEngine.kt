package com.wolfcodes.offline

import android.graphics.Canvas

/**
 * Primary self-contained visual production path for scenes without supplied images.
 *
 * It plans a local horror environment from the scene text, creates three distinct
 * procedural camera compositions, draws the base artwork, then composites the
 * deterministic depth-aware motion layers. No network, model download, reference
 * image, or remote rendering service is required.
 *
 * The artwork is deliberately procedural illustration; it is not represented as
 * photorealistic AI-generated imagery.
 */
object WolfProceduralSceneProductionEngine {

    enum class Environment {
        FOREST,
        CORRIDOR,
        WINDOW,
        DOOR,
        CEMETERY,
        ROOM,
        HAUNTED_HOUSE
    }

    data class ShotComposition(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val rotationDegrees: Float
    )

    data class ScenePlan(
        val sceneIndex: Int,
        val caption: String,
        val environment: Environment,
        val visualCaption: String,
        val features: WolfProceduralMotionEngine.SceneFeatures
    )

    fun plan(caption: String, sceneIndex: Int): ScenePlan {
        require(caption.isNotBlank()) { "لا يمكن تخطيط مشهد فارغ" }
        require(sceneIndex >= 0) { "رقم المشهد غير صالح" }

        val environment = classifyEnvironment(caption)
        val cue = when (environment) {
            Environment.FOREST -> "غابة"
            Environment.CORRIDOR -> "ممر"
            Environment.WINDOW -> "نافذة"
            Environment.DOOR -> "باب"
            Environment.CEMETERY -> "مقبرة"
            Environment.ROOM -> "غرفة"
            Environment.HAUNTED_HOUSE -> "منزل مهجور"
        }

        return ScenePlan(
            sceneIndex = sceneIndex,
            caption = caption,
            environment = environment,
            visualCaption = "$cue — $caption",
            features = WolfProceduralMotionEngine.featuresFor(caption)
        )
    }

    fun classifyEnvironment(caption: String): Environment {
        val text = caption.lowercase(java.util.Locale.ROOT)
        fun has(vararg terms: String) = terms.any(text::contains)

        return when {
            has("مقبرة", "مقابر", "قبر", "قبور", "cemetery", "graveyard", "grave") ->
                Environment.CEMETERY
            has("غابة", "غابه", "أشجار", "شجرة", "غصن", "أغصان", "forest", "woods", "trees") ->
                Environment.FOREST
            has("ممر", "طرقة", "الممر", "hallway", "corridor") ->
                Environment.CORRIDOR
            has("نافذة", "شباك", "زجاج", "window") ->
                Environment.WINDOW
            has("باب", "أبواب", "مدخل", "door", "entrance") ->
                Environment.DOOR
            has("غرفة", "حجرة", "الغرفة", "room", "bedroom") ->
                Environment.ROOM
            else -> Environment.HAUNTED_HOUSE
        }
    }

    fun shotComposition(shotIndex: Int): ShotComposition {
        return when (shotIndex.coerceIn(0, 2)) {
            0 -> ShotComposition(
                scale = 1.0f,
                offsetX = 0f,
                offsetY = 0f,
                rotationDegrees = 0f
            )
            1 -> ShotComposition(
                scale = 1.075f,
                offsetX = -0.018f,
                offsetY = 0.008f,
                rotationDegrees = -0.12f
            )
            else -> ShotComposition(
                scale = 1.15f,
                offsetX = 0.022f,
                offsetY = -0.012f,
                rotationDegrees = 0.16f
            )
        }
    }

    /** Renders one complete local procedural visual frame, excluding final overlays. */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        plan: ScenePlan,
        shotIndex: Int,
        progress: Float
    ) {
        if (width <= 0 || height <= 0) return
        val safeShot = shotIndex.coerceIn(0, 2)
        val t = progress.coerceIn(0f, 1f)
        val shot = shotComposition(safeShot)
        val saved = canvas.save()
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.translate(
                width * (0.5f + shot.offsetX),
                height * (0.5f + shot.offsetY)
            )
            canvas.rotate(shot.rotationDegrees)
            canvas.scale(shot.scale, shot.scale)
            canvas.translate(-width * 0.5f, -height * 0.5f)

            // The scene index and shot index seed deterministic but distinct artwork.
            WolfSceneArtEngine.draw(
                canvas = canvas,
                width = width,
                height = height,
                caption = plan.visualCaption,
                scene = plan.sceneIndex * 3 + safeShot,
                progress = t
            )
        } finally {
            canvas.restoreToCount(saved)
        }

        WolfProceduralMotionEngine.renderDepthAware(
            canvas = canvas,
            width = width,
            height = height,
            caption = plan.caption,
            sceneIndex = plan.sceneIndex,
            shotIndex = safeShot,
            progress = t
        )
    }
}
