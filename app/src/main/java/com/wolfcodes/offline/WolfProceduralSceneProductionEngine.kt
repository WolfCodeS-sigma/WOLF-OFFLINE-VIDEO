package com.wolfcodes.offline

import android.graphics.Canvas

/**
 * Deterministic environment-only production route for scenes without user images.
 *
 * The render contract deliberately excludes procedural human, ghost, creature, door,
 * furniture, and prop geometry. It draws atmospheric background artwork and environmental
 * effects only. A future asset-backed adapter may add approved subjects after separate
 * semantic, visual-quality, licensing, and performance gates.
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
        val rotationDegrees: Float,
        val focusX: Float,
        val focusY: Float
    )

    data class ScenePlan(
        val sceneIndex: Int,
        val caption: String,
        val environment: Environment,
        val visualCaption: String,
        val features: WolfProceduralMotionEngine.SceneFeatures,
        /** Always empty until an approved asset-backed subject/prop renderer exists. */
        val objectLayers: List<WolfSceneLayerSystem.Element>
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
            visualCaption = cue,
            features = WolfProceduralMotionEngine.featuresFor(caption),
            objectLayers = emptyList()
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
            0 -> ShotComposition(1.0f, 0f, 0f, 0f, 0.50f, 0.50f)
            1 -> ShotComposition(1.10f, 0f, 0f, 0f, 0.46f, 0.48f)
            else -> ShotComposition(1.24f, 0f, 0f, 0f, 0.56f, 0.44f)
        }
    }

    /** Renders atmospheric artwork and environmental motion only. */
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
            canvas.translate(-width * shot.focusX, -height * shot.focusY)

            WolfSceneArtEngine.draw(
                canvas = canvas,
                width = width,
                height = height,
                caption = plan.caption,
                scene = plan.sceneIndex * 3 + safeShot,
                progress = t
            )

            WolfProceduralMotionEngine.renderDepthAware(
                canvas = canvas,
                width = width,
                height = height,
                caption = plan.caption,
                sceneIndex = plan.sceneIndex,
                shotIndex = safeShot,
                progress = t
            )
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
