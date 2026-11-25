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
        HAUNTED_HOUSE,
        BEACH,
        SEA,
        DESERT,
        ROAD,
        RURAL,
        MEDICAL_FACILITY,
        EDUCATIONAL
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
            Environment.BEACH -> "شاطئ"
            Environment.SEA -> "بحر مفتوح"
            Environment.DESERT -> "صحراء"
            Environment.ROAD -> "طريق"
            Environment.RURAL -> "منطقة ريفية"
            Environment.MEDICAL_FACILITY -> "مكان طبي"
            Environment.EDUCATIONAL -> "مبنى تعليمي"
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
            // Resolve coast before sand so beach sand is not classified as a desert.
            has("شاطئ", "الشاطئ", "ساحل", "الساحل", "beach", "shore", "coast", "seashore") ->
                Environment.BEACH
            has("بحر", "البحر", "محيط", "المحيط", "أمواج", "موج", "sea", "ocean", "waves", "open water") ->
                Environment.SEA
            has("صحراء", "الصحراء", "كثبان", "كثيب", "رمال", "desert", "dunes", "sand") ->
                Environment.DESERT
            has("غابة", "غابه", "أشجار", "شجرة", "غصن", "أغصان", "forest", "woods", "trees") ->
                Environment.FOREST
            has("مستشفى", "عيادة", "مشرحة", "hospital", "clinic", "morgue") ->
                Environment.MEDICAL_FACILITY
            has("مدرسة", "فصل", "جامعة", "school", "classroom", "university") ->
                Environment.EDUCATIONAL
            has("طريق", "شارع", "سيارة", "road", "street", "highway") ->
                Environment.ROAD
            has("مزرعة", "حقل", "قرية", "farm", "field", "village", "rural") ->
                Environment.RURAL
            has("ممر", "طرقة", "الممر", "hallway", "corridor") -> Environment.CORRIDOR
            else -> classifyInteriorEnvironment(text)
        }
    }

    /**
     * Scores the interior location separately from its focal door/window.
     * Incidental props do not override an explicit room, but a prop that anchors
     * the event (for example, a figure behind a window) becomes the scene focus.
     */
    private fun classifyInteriorEnvironment(text: String): Environment {
        fun has(vararg terms: String) = terms.any(text::contains)

        val roomMentioned = has("غرفة", "حجرة", "الغرفة", "room", "bedroom")
        val windowTerms = listOf("نافذة", "شباك", "زجاج", "window")
        val doorTerms = listOf("باب", "أبواب", "مدخل", "door", "entrance")
        val windowFocusTerms = listOf(
            "خلف النافذة", "وراء النافذة", "عند النافذة", "من النافذة", "عبر النافذة",
            "behind the window", "behind a window", "through the window",
            "at the window", "outside the window", "from the window"
        )
        val doorFocusTerms = listOf(
            "خلف الباب", "وراء الباب", "عند الباب", "الباب يفتح", "الباب انفتح",
            "انفتح الباب", "يفتح الباب", "فتح الباب",
            "behind the door", "behind a door", "at the door", "through the door",
            "the door opens", "the door opened", "door opens", "door opened"
        )

        val windowMentioned = windowTerms.any(text::contains)
        val doorMentioned = doorTerms.any(text::contains)
        val windowFocused = windowFocusTerms.any(text::contains)
        val doorFocused = doorFocusTerms.any(text::contains)

        // A named room outweighs a passing prop mention (4 vs 2).
        // A clear focal relation/action outweighs the room (2 + 5 vs 4).
        val roomScore = if (roomMentioned) 4 else 0
        val windowScore = (if (windowMentioned) 2 else 0) + (if (windowFocused) 5 else 0)
        val doorScore = (if (doorMentioned) 2 else 0) + (if (doorFocused) 5 else 0)

        val windowLastMention = (windowTerms + windowFocusTerms)
            .maxOfOrNull { text.lastIndexOf(it) } ?: -1
        val doorLastMention = (doorTerms + doorFocusTerms)
            .maxOfOrNull { text.lastIndexOf(it) } ?: -1

        return when {
            windowScore > roomScore && windowScore > doorScore -> Environment.WINDOW
            doorScore > roomScore && doorScore > windowScore -> Environment.DOOR
            windowScore == doorScore && windowScore > roomScore &&
                windowMentioned && doorMentioned -> {
                if (windowLastMention > doorLastMention) Environment.WINDOW else Environment.DOOR
            }
            roomScore > 0 -> Environment.ROOM
            windowMentioned -> Environment.WINDOW
            doorMentioned -> Environment.DOOR
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
            plannedProgress, sourceSceneIndex, caption, safeShot, cameraMovement,
            OfflineMotionEngine.MAX_HORROR_INTENSITY
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
        Environment.BEACH -> "شاطئ أو ساحل"
        Environment.SEA -> "بحر مفتوح"
        Environment.DESERT -> "صحراء"
        Environment.ROAD -> "طريق أو شارع"
        Environment.RURAL -> "منطقة ريفية"
        Environment.MEDICAL_FACILITY -> "مكان طبي"
        Environment.EDUCATIONAL -> "مبنى تعليمي"
    }
}
