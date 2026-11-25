package com.wolfcodes.offline

import android.graphics.Canvas

/**
 * Deterministic environment-only production route for scenes without user images.
 *
 * The production renderer draws atmospheric backgrounds and environmental effects only.
 * It deliberately does not synthesize people, ghosts, creatures, doors, furniture,
 * buildings, or props from primitive geometry. Subject-level visuals require separately
 * approved assets and a validated asset-backed renderer.
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
            visualCaption = cue,
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
            has("ممر", "طرقة", "الممر", "hallway", "corridor") -> Environment.CORRIDOR
            has("نافذة", "شباك", "زجاج", "window") -> Environment.WINDOW
            has("باب", "أبواب", "مدخل", "door", "entrance") -> Environment.DOOR
            has("غرفة", "حجرة", "الغرفة", "room", "bedroom") -> Environment.ROOM
            else -> Environment.HAUNTED_HOUSE
        }
    }

    fun shotComposition(shotIndex: Int): ShotComposition =
        when (shotIndex.coerceIn(0, 2)) {
            0 -> ShotComposition(1.0f, 0f, 0f, 0f, 0.50f, 0.50f)
            1 -> ShotComposition(1.10f, 0f, 0f, 0f, 0.46f, 0.48f)
            else -> ShotComposition(1.24f, 0f, 0f, 0f, 0.56f, 0.44f)
        }

    /**
     * Renders the selected environment plan using the same storyboard context and
     * camera intent that OfflineProductionEngine previously applied to its direct
     * no-image drawing branch. Subject and prop geometry remain prohibited here.
     */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        plan: ScenePlan,
        shotIndex: Int,
        progress: Float,
        sceneRenderContext: StoryDirectorEngine.SceneRenderContext? = null,
        shotPlan: StoryDirectorEngine.ShotPlan? = null
    ) {
        if (width <= 0 || height <= 0) return

        val safeShot = shotIndex.coerceIn(0, WolfSceneAssetStore.SHOTS_PER_SCENE - 1)
        val caption = plan.caption
        val plannedProgress = StoryDirectorEngine.motionProgress(shotPlan?.motion.orEmpty(), progress)
        val sourceSceneIndex = (sceneRenderContext?.sceneNumber?.minus(1) ?: plan.sceneIndex)
            .coerceAtLeast(0)
        val cameraMovement = StoryDirectorEngine.resolveCameraMovement(
            shotPlan?.motion.orEmpty(), caption, shotPlan?.camera.orEmpty()
        )
        val compositionFocusX = shotPlan?.let {
            StoryDirectorEngine.compositionFocusX(it.composition, it.camera)
        }
        val compositionFocusY = shotPlan?.let {
            StoryDirectorEngine.compositionFocusY(it.composition, it.camera)
        }
        val horizontalOffset = compositionFocusX?.let { (it - 0.5f) * width * 0.08f } ?: 0f
        val verticalOffset = compositionFocusY?.let { (it - 0.5f) * height * 0.06f } ?: 0f
        val sceneCamera = OfflineMotionEngine.cameraFrame(
            plannedProgress, sourceSceneIndex, caption, safeShot, cameraMovement
        )
        val sceneScale = (1f + sceneCamera.zoom).coerceIn(0.98f, 1.08f)
        val setting = sceneRenderContext?.setting?.takeIf { it.isNotBlank() }
            ?: settingFor(plan.environment)

        val saved = canvas.save()
        try {
            canvas.translate(
                width * 0.5f + sceneCamera.offsetX * width + horizontalOffset,
                height * 0.5f + sceneCamera.offsetY * height + verticalOffset
            )
            canvas.rotate(sceneCamera.rotationDegrees)
            canvas.scale(sceneScale, sceneScale)
            canvas.translate(-width * 0.5f, -height * 0.5f)
            WolfSceneArtEngine.draw(
                canvas = canvas,
                width = width,
                height = height,
                caption = caption,
                scene = sourceSceneIndex * WolfSceneAssetStore.SHOTS_PER_SCENE + safeShot,
                progress = plannedProgress,
                setting = setting,
                mood = sceneRenderContext?.mood.orEmpty(),
                continuityKey = sceneRenderContext?.continuityKey ?: 0,
                visualStyle = sceneRenderContext?.visualStyle.orEmpty(),
                horrorType = sceneRenderContext?.horrorType.orEmpty()
            )
        } finally {
            canvas.restoreToCount(saved)
        }

        // Keep the procedural renderer complete for standalone pipeline callers.
        // The MP4 encoder skips its shared image-only overlay pass for this backend.
        WolfProceduralMotionEngine.renderDepthAware(
            canvas = canvas,
            width = width,
            height = height,
            caption = caption,
            sceneIndex = sourceSceneIndex,
            shotIndex = safeShot,
            progress = plannedProgress,
            storyEvent = sceneRenderContext?.event ?: StoryDirectorEngine.classifyEvent(caption),
            continuityKey = sceneRenderContext?.continuityKey ?: 0
        )
    }

    private fun settingFor(environment: Environment): String = when (environment) {
        Environment.FOREST -> "غابة"
        Environment.CORRIDOR -> "ممر"
        Environment.WINDOW -> "نافذة"
        Environment.DOOR -> "باب"
        Environment.CEMETERY -> "مقابر"
        Environment.ROOM -> "مكان داخلي"
        Environment.HAUNTED_HOUSE -> "منزل مهجور"
    }
}
