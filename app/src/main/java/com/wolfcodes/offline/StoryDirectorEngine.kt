package com.wolfcodes.offline

import java.util.Locale

/** Deterministic first-pass director: creates a structured shot plan without network access. */
object StoryDirectorEngine {
    data class ShotPlan(val number: Int, val type: String, val camera: String, val composition: String,
        val lighting: String, val motion: String, val durationSeconds: Int, val prompt: String)
    data class ShotFrameSlice(
        val shotPlan: ShotPlan,
        val frameCount: Int,
        val startOffsetFrames: Int,
        val totalShotFrames: Int
    )

    enum class StoryEvent { CHASE, SOUND, REVEAL, SUSPENSE }

    enum class CameraMovement {
        HOLD,
        TRACK_LATERAL,
        TRACK_LEFT_TO_RIGHT,
        TRACK_RIGHT_TO_LEFT,
        FOLLOW,
        SLOW_PUSH_IN,
        SLOW_REVEAL,
        RESTRAINED_HANDHELD,
        SLOW_DRIFT
    }

    data class SceneRenderContext(
        val sceneNumber: Int,
        val setting: String,
        val mood: String,
        val continuityNotes: String,
        val continuityKey: Int,
        val event: StoryEvent,
        val visualStyle: String = "",
        val horrorType: String = ""
    )
    data class ScenePlan(val number: Int, val sourceText: String, val setting: String, val mood: String,
        val continuityNotes: String, val durationSeconds: Int, val shots: List<ShotPlan>)
    data class FilmPlan(val totalDurationSeconds: Int, val aspectRatio: String, val visualStyle: String,
        val horrorType: String, val continuityBible: String, val scenes: List<ScenePlan>)

    fun analyze(
        story: String,
        durationSeconds: Int,
        aspectRatio: String,
        visualStyle: String,
        horrorType: String,
        audioDrivenDuration: Boolean = false,
        includeIntro: Boolean = false
    ): FilmPlan {
        val requestedDuration = durationSeconds.coerceIn(1, 1800)
        val introSeconds = if (audioDrivenDuration && includeIntro) minOf(3, (requestedDuration - 1).coerceAtLeast(0)) else 0
        val sceneBudget = if (audioDrivenDuration) (requestedDuration - introSeconds).coerceAtLeast(1) else Int.MAX_VALUE
        val sceneTexts = if (audioDrivenDuration) {
            fitSceneTextsToDuration(splitIntoScenes(story), sceneBudget)
        } else splitIntoScenes(story)
        val safeDuration = if (audioDrivenDuration) requestedDuration else durationSeconds.coerceAtLeast(sceneTexts.size * 3)
        val sceneDurationBudget = if (audioDrivenDuration) sceneBudget else safeDuration
        val base = sceneDurationBudget / sceneTexts.size
        val remainder = sceneDurationBudget % sceneTexts.size
        val bible = buildContinuityBible(story)
        val scenes = sceneTexts.mapIndexed { index, text ->
            val sceneDuration = base + if (index < remainder) 1 else 0
            val setting = detectSetting(text)
            val mood = detectMood(text, horrorType)
            ScenePlan(index + 1, text, setting, mood, bible, sceneDuration,
                buildShots(index + 1, text, sceneDuration, aspectRatio, visualStyle, horrorType, setting, mood, bible))
        }
        return FilmPlan(safeDuration, aspectRatio, visualStyle, horrorType, bible, scenes)
    }

    /** Classify the dominant story event once so planning and rendering use the same intent. */
    fun classifyEvent(text: String): StoryEvent {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            listOf("يطارد", "يجري", "هرب", "يهرب", "مطاردة", "يلحق", "ركض", "فرّ", "chase", "running", "escape")
                .any { lower.contains(it) } -> StoryEvent.CHASE
            listOf("همس", "صوت", "أنفاس", "ينادي", "طرق", "خطوات", "صرير", "whisper", "footsteps", "knock")
                .any { lower.contains(it) } -> StoryEvent.SOUND
            listOf("ظهر", "ظهرت", "انكشف", "اكتشف", "وجد", "رأى", "اختفى", "ظل", "وجه", "شبح", "appeared", "revealed", "shadow")
                .any { lower.contains(it) } -> StoryEvent.REVEAL
            else -> StoryEvent.SUSPENSE
        }
    }

    /** Carry scene-level narrative continuity into the actual render path without drawing story text. */
    fun sceneRenderContext(
        scene: ScenePlan,
        visualStyle: String = "",
        horrorType: String = ""
    ): SceneRenderContext {
        val stableContinuity = scene.continuityNotes.trim().replace(Regex("\\s+"), " ")
        val continuityKey = (
            scene.setting.trim().lowercase(Locale.ROOT) + "|" + stableContinuity + "|" +
                visualStyle.trim() + "|" + horrorType.trim()
            ).hashCode()
        return SceneRenderContext(
            sceneNumber = scene.number,
            setting = scene.setting,
            mood = scene.mood,
            continuityNotes = scene.continuityNotes,
            continuityKey = continuityKey,
            event = classifyEvent(scene.sourceText),
            visualStyle = visualStyle,
            horrorType = horrorType
        )
    }

    /** Translate the director's words into a deterministic camera command, not a generic preset. */
    fun resolveCameraMovement(
        motion: String,
        storyText: String = "",
        cameraText: String = ""
    ): CameraMovement {
        val motionValue = motion.lowercase(Locale.ROOT)
        val cameraValue = cameraText.lowercase(Locale.ROOT)
        val value = "$motionValue $cameraValue"
        val narrativeDirection = storyText.lowercase(Locale.ROOT)
        return when {
            cameraValue.contains("اهتزاز محدود") || cameraValue.contains("اهتزاز طفيف") ->
                CameraMovement.RESTRAINED_HANDHELD
            motionValue.contains("تثبيت") && motionValue.contains("ثم حركة") -> CameraMovement.SLOW_REVEAL
            motionValue.contains("تثبيت") -> CameraMovement.HOLD
            value.contains("كشف بطيء") -> CameraMovement.SLOW_REVEAL
            value.contains("للداخل") || value.contains("اقتراب") -> CameraMovement.SLOW_PUSH_IN
            value.contains("تتبّع") || value.contains("تتبع") || value.contains("انزلاق جانبي") ->
                when {
                    narrativeDirection.contains("إلى اليمين") || narrativeDirection.contains("نحو اليمين") ->
                        CameraMovement.TRACK_LEFT_TO_RIGHT
                    narrativeDirection.contains("إلى اليسار") || narrativeDirection.contains("نحو اليسار") ->
                        CameraMovement.TRACK_RIGHT_TO_LEFT
                    else -> CameraMovement.TRACK_LATERAL
                }
            value.contains("متابعة") -> CameraMovement.FOLLOW
            value.contains("اهتزاز") -> CameraMovement.RESTRAINED_HANDHELD
            else -> CameraMovement.SLOW_DRIFT
        }
    }

    /** Vertical composition anchor; null means the director supplied no safe instruction. */
    internal fun compositionFocusY(composition: String, camera: String = ""): Float? {
        val intent = "$composition $camera"
        return when {
            intent.contains("زاوية منخفضة") -> 0.58f
            intent.contains("زاوية مرتفعة") -> 0.42f
            intent.contains("مساحة فوق") || intent.contains("مساحة سلبية أعلى") -> 0.40f
            else -> null
        }
    }

    /** Merge adjacent story sections if an approved narration cannot fit one second per scene. */
    fun fitSceneTextsToDuration(sceneTexts: List<String>, durationSeconds: Int): List<String> {
        if (sceneTexts.isEmpty()) return listOf("بداية الحكاية")
        val limit = minOf(sceneTexts.size, durationSeconds.coerceAtLeast(1))
        if (sceneTexts.size <= limit) return sceneTexts
        val buckets = List(limit) { mutableListOf<String>() }
        sceneTexts.forEachIndexed { index, text ->
            val bucket = (index * limit / sceneTexts.size).coerceIn(0, limit - 1)
            buckets[bucket].add(text)
        }
        return buckets.map { it.joinToString("\n") }.filter { it.isNotBlank() }
    }

    /** Recover the source slot for a shot even when a chapter starts mid-scene. */
    internal fun sourceShotIndex(shotNumber: Int, shotsPerScene: Int = 3): Int {
        require(shotNumber > 0) { "رقم اللقطة يجب أن يكون موجبًا" }
        require(shotsPerScene > 0) { "عدد لقطات المشهد يجب أن يكون موجبًا" }
        return (shotNumber - 1) % shotsPerScene
    }

    /** Return the normalized progress of a frame within its original, unsliced shot. */
    internal fun progressForShotFrame(
        startOffsetFrames: Int,
        frameIndex: Int,
        totalShotFrames: Int
    ): Float {
        require(startOffsetFrames >= 0 && frameIndex >= 0)
        require(totalShotFrames > 0 && startOffsetFrames + frameIndex < totalShotFrames) {
            "إطار اللقطة خارج نطاقها الأصلي"
        }
        return (startOffsetFrames + frameIndex).toFloat() / maxOf(1, totalShotFrames - 1)
    }

    /** Convert the planned motion description into a restrained normalized timeline. */
    internal fun motionProgress(motion: String, progress: Float): Float {
        val value = progress.coerceIn(0f, 1f)
        return when {
            motion.contains("تثبيت") && !motion.contains("ثم حركة") -> 0.5f
            motion.contains("كشف بطيء") || motion.contains("اقتراب بطيء") || motion.contains("ثم حركة") ->
                value * value * (3f - 2f * value)
            else -> value
        }
    }

    /** Choose a conservative horizontal focus from the shot's actual composition notes. */
    internal fun compositionFocusX(composition: String, camera: String = ""): Float? {
        val intent = "$composition $camera"
        return when {
            intent.contains("حجب جزئي") -> 0.66f
            intent.contains("مصدر الصوت") || intent.contains("المصدر الصوتي") ||
                intent.contains("اتجاه الصوت") || intent.contains("مساحة بصرية") -> 0.64f
            intent.contains("خلف الشخصية") -> 0.38f
            intent.contains("اتجاه الحركة") -> 0.58f
            else -> null
        }
    }

    /** Normalize lighting intent for a subtle, deterministic local render treatment. */
    internal fun lightingTreatment(lighting: String): String = when {
        lighting.contains("باردة") -> "COOL"
        lighting.contains("ليلية") -> "NIGHT"
        lighting.contains("جانبية") -> "SIDE"
        lighting.contains("محدود") -> "LOW_KEY"
        else -> "NEUTRAL"
    }

    /** Allocate an exact frame budget across planned shots using their duration weights. */
    fun allocateShotFrames(totalFrames: Int, shotDurationsSeconds: List<Int>): List<Int> {
        require(totalFrames > 0) { "عدد إطارات اللقطة يجب أن يكون موجبًا" }
        require(shotDurationsSeconds.isNotEmpty()) { "خطة اللقطات فارغة" }
        require(totalFrames >= shotDurationsSeconds.size) {
            "عدد الإطارات لا يكفي لتمثيل كل اللقطات المخططة"
        }
        val weights = shotDurationsSeconds.map { it.coerceAtLeast(1).toDouble() }
        val remaining = totalFrames - weights.size
        val weightSum = weights.sum()
        val exact = weights.map { remaining * it / weightSum }
        val result = exact.map { 1 + it.toInt() }.toMutableList()
        var remainder = totalFrames - result.sum()
        exact.indices.sortedByDescending { exact[it] - exact[it].toInt() }.forEach { index ->
            if (remainder > 0) {
                result[index] += 1
                remainder -= 1
            }
        }
        check(result.sum() == totalFrames && result.all { it > 0 })
        return result
    }

    /**
     * Intersect a chapter slice with the original shot ranges while preserving each
     * shot's offset and full frame count. The offset prevents motion from restarting
     * when a chapter boundary falls in the middle of a shot.
     */
    fun sliceShotSegments(
        shots: List<ShotPlan>,
        shotFrames: List<Int>,
        startFrame: Int,
        frameCount: Int
    ): List<ShotFrameSlice> {
        require(shots.isNotEmpty() && shots.size == shotFrames.size)
        require(startFrame >= 0 && frameCount > 0)
        require(shotFrames.all { it > 0 })
        val totalFrames = shotFrames.sum()
        require(startFrame <= totalFrames && frameCount <= totalFrames - startFrame) {
            "شريحة الفصل تتجاوز نطاق إطارات المشهد"
        }
        val endFrame = startFrame + frameCount
        val result = mutableListOf<ShotFrameSlice>()
        var cursor = 0
        shots.forEachIndexed { index, shot ->
            val shotStart = cursor
            val shotEnd = shotStart + shotFrames[index]
            val overlapStart = maxOf(startFrame, shotStart)
            val overlapEnd = minOf(endFrame, shotEnd)
            if (overlapEnd > overlapStart) {
                result.add(
                    ShotFrameSlice(
                        shotPlan = shot,
                        frameCount = overlapEnd - overlapStart,
                        startOffsetFrames = overlapStart - shotStart,
                        totalShotFrames = shotFrames[index]
                    )
                )
            }
            cursor = shotEnd
        }
        require(result.sumOf { it.frameCount } == frameCount) {
            "تعذر مطابقة كل إطارات شريحة الفصل مع اللقطات الأصلية"
        }
        return result
    }

    /** Intersect a scene's shot-frame ranges with one chapter slice without losing frames. */
    fun sliceShotFrames(shotFrames: List<Int>, startFrame: Int, frameCount: Int): List<Int> {
        val placeholderShots = shotFrames.mapIndexed { index, _ ->
            ShotPlan(index + 1, "", "", "", "", "", 1, "")
        }
        return sliceShotSegments(placeholderShots, shotFrames, startFrame, frameCount)
            .map { it.frameCount }
    }

    /** Return the shot descriptions intersecting a chapter slice, preserving their order. */
    fun sliceShotPlans(
        shots: List<ShotPlan>,
        shotFrames: List<Int>,
        startFrame: Int,
        frameCount: Int
    ): List<ShotPlan> = sliceShotSegments(shots, shotFrames, startFrame, frameCount)
        .map { it.shotPlan }

    fun promptsForSharing(plan: FilmPlan): String = plan.scenes.flatMap { it.shots }.joinToString("\n\n") { shot ->
        "اللقطة ${shot.number} — ${shot.type}\n${shot.prompt}"
    }

    private fun splitIntoScenes(story: String): List<String> {
        val normalized = story.trim().replace("\r\n", "\n")
        if (normalized.isBlank()) return listOf("بداية الحكاية")
        val paragraphs = normalized.split(Regex("\\n\\s*\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val parts = if (paragraphs.size > 1) paragraphs else normalized
            .split(Regex("(?<=[.!؟?؛])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = parts.flatMap { if (it.length > 520) it.chunked(420) else listOf(it) }
        if (chunks.size <= 12) return chunks.ifEmpty { listOf(normalized) }
        // Keep every part of the user's story: merge overflow into the twelfth scene.
        return chunks.take(11) + chunks.drop(11).joinToString("\n")
    }

    private fun buildContinuityBible(story: String): String {
        val sentences = story.split(Regex("(?<=[.!؟?؛])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val appearanceWords = listOf("شعر", "عين", "عيون", "يرتدي", "ترتدي", "ملابس", "وجه", "طويل", "قصير", "معطف", "فستان")
        val placeWords = listOf(
            "منزل", "بيت", "غرفة", "ممر", "مستشفى", "مدرسة", "غابة", "شارع", "قصر", "مبنى", "قبو",
            "شاطئ", "بحر", "محيط", "ساحل", "صحراء", "رمال", "سرداب", "نافذة", "مزرعة", "حقل"
        )
        val appearance = sentences.filter { s -> appearanceWords.any { s.contains(it) } }.take(4).joinToString("؛ ")
        val places = sentences.filter { s -> placeWords.any { s.contains(it) } }.take(4).joinToString("؛ ")
        val notes = mutableListOf<String>()
        if (appearance.isNotBlank()) notes.add("تفاصيل الشخصيات المذكورة: $appearance")
        if (places.isNotBlank()) notes.add("تفاصيل المواقع المذكورة: $places")
        notes.add("ثبّت ملامح الشخصيات وملابسها وتصميم الموقع بين اللقطات. لا تخترع تفاصيل غير مذكورة؛ اتركها محايدة وثابتة.")
        return notes.joinToString("\n")
    }

    private fun detectSetting(text: String): String {
        val normalized = text.lowercase(Locale.ROOT)
        // Resolve specific outdoor locations before generic buildings.
        val options = listOf(
            listOf("مقابر", "مقبرة", "قبر", "قبور", "cemetery", "graveyard") to "مقابر",
            listOf("شاطئ", "الشاطئ", "ساحل", "الساحل", "beach", "shore", "coast", "seashore") to "شاطئ أو ساحل",
            listOf("بحر", "البحر", "محيط", "المحيط", "أمواج", "موج", "sea", "ocean", "waves", "open water") to "بحر مفتوح",
            listOf("صحراء", "الصحراء", "كثبان", "كثيب", "رمال", "desert", "dunes", "sand") to "صحراء",
            listOf("غابة", "غابه", "أشجار", "شجرة", "forest", "woods", "trees") to "غابة كثيفة",
            listOf("شارع", "طريق", "سيارة", "road", "street", "highway") to "طريق أو شارع",
            listOf("مزرعة", "حقل", "قرية", "farm", "field", "village", "rural") to "منطقة ريفية",
            listOf("مستشفى", "عيادة", "مشرحة", "hospital", "clinic", "morgue") to "مكان طبي",
            listOf("مدرسة", "فصل", "جامعة", "school", "classroom", "university") to "مبنى تعليمي",
            listOf("ممر", "ردهة", "سرداب", "قبو", "hallway", "corridor", "basement") to "ممر أو قبو داخلي",
            listOf("نافذة", "شرفة", "window", "balcony") to "بجانب نافذة أو شرفة",
            listOf("منزل", "بيت", "شقة", "غرفة", "house", "home", "room", "bedroom") to "مكان داخلي سكني"
        )
        return options.firstOrNull { entry -> entry.first.any(normalized::contains) }?.second
            ?: "الموقع الذي تصفه أحداث القصة، دون إضافة موقع جديد"
    }

    private fun detectMood(text: String, horrorType: String): String = when (classifyEvent(text)) {
        StoryEvent.CHASE -> "توتر ومطاردة متصاعدة"
        StoryEvent.SOUND -> "رعب سمعي وترقب"
        StoryEvent.REVEAL -> "ظهور خارق وغموض"
        StoryEvent.SUSPENSE -> "رعب $horrorType بإيقاع مقلق"
    }

    private fun buildShots(sceneNumber: Int, text: String, sceneDuration: Int, ratio: String,
        style: String, horror: String, setting: String, mood: String, continuity: String): List<ShotPlan> {
        val event = classifyEvent(text)
        val templates = when (event) {
            StoryEvent.CHASE -> listOf(
                Triple("لقطة تتبّع واسعة", "لقطة واسعة بعدسة 24mm مع تتبّع جانبي مضبوط", "أظهر اتجاه الحركة والمسافة والعوائق المذكورة في النص"),
                Triple("لقطة حركة متوسطة", "لقطة متوسطة بعدسة 50mm من زاوية موازية للحركة", "حافظ على اتجاه الحركة واستمرارية موقع الشخصية"),
                Triple("لقطة تفصيل أثناء المطاردة", "لقطة قريبة بعدسة 85mm مع اهتزاز محدود", "أبرز تفصيل الخطر المذكور دون إضافة مطارد أو حدث جديد")
            )
            StoryEvent.SOUND -> listOf(
                Triple("لقطة تأسيسية صامتة", "لقطة واسعة بعدسة 24mm وكاميرا ثابتة", "اترك مساحة بصرية للمصدر الصوتي المذكور"),
                Triple("لقطة ترقّب", "لقطة متوسطة بعدسة 50mm من خلف الشخصية عند وجودها بالنص", "وجّه الانتباه إلى اتجاه الصوت دون إظهار مصدر غير مؤكد"),
                Triple("لقطة مصدر الصوت", "لقطة قريبة بعدسة 85mm", "اكشف المصدر فقط إذا كان محددًا في القصة وإلا أبقه خارج الكادر")
            )
            StoryEvent.REVEAL -> listOf(
                Triple("لقطة تأسيسية غامضة", "لقطة واسعة بعدسة 24mm مع حجب جزئي للكادر", "مهّد للمكان والعنصر المذكور دون كشف مبكر"),
                Triple("لقطة اقتراب بطيء", "لقطة متوسطة بعدسة 50mm مع اقتراب بصري محدود", "وجّه النظر إلى موضع الظهور أو الاكتشاف المذكور"),
                Triple("لقطة الكشف", "لقطة قريبة بعدسة 85mm بزاوية منخفضة عند ملاءمة النص", "أظهر التفصيل المذكور فقط مع الحفاظ على الغموض")
            )
            StoryEvent.SUSPENSE -> listOf(
                Triple("لقطة تأسيسية", "لقطة واسعة بعدسة 24mm من مستوى العين", "أظهر المكان والعزلة والمساحة السلبية حول الحدث"),
                Triple("لقطة اقتراب", "لقطة متوسطة بعدسة 50mm وعمق مجال سينمائي", "قرّب الانتباه إلى الشخصية أو الحدث الأساسي المذكور"),
                Triple("لقطة تفصيل", "لقطة قريبة بعدسة 85mm", "اكشف تفصيلًا مقلقًا موجودًا في النص دون اختراع حدث")
            )
        }.take(sceneDuration.coerceIn(1, 3))
        val baseDuration = sceneDuration / templates.size
        val durations = List(templates.size) { index ->
            if (index == templates.lastIndex) sceneDuration - baseDuration * index else baseDuration
        }
        return templates.mapIndexed { i, shotTemplate ->
            val lighting = when {
                setting == "مكان طبي" -> "إضاءة عملية باردة من مصادر طبية واقعية مع ظلال عميقة"
                setting == "غابة كثيفة" || setting == "مقابر" -> "إضاءة ليلية محيطية منخفضة مع ضوء خلفي طبيعي محدود"
                i == 0 -> "إضاءة محيطية منخفضة ومصدر ضوء منطقي داخل الموقع"
                i == 1 -> "إضاءة جانبية درامية وظلال عميقة مع وضوح الحدث"
                else -> "ضوء محدود وموجّه يبرز التفصيل المرعب"
            }
            val motion = when {
                event == StoryEvent.CHASE && i == 0 -> "تتبّع جانبي بطيء يحافظ على اتجاه الحركة"
                event == StoryEvent.CHASE && i == 1 -> "حركة متابعة متوسطة وثابتة دون اهتزاز مبالغ"
                event == StoryEvent.SOUND && i == 0 -> "تثبيت الكاميرا وترك التوتر يتصاعد داخل الكادر"
                event == StoryEvent.SOUND && i == 2 -> "اقتراب دقيق من مصدر الصوت إن كان ظاهرًا في النص"
                event == StoryEvent.REVEAL && i == 0 -> "تثبيت مع حجب جزئي للمعلومة البصرية"
                event == StoryEvent.REVEAL && i == templates.lastIndex -> "كشف بطيء ومحدود للتفصيل المذكور"
                i == 0 -> "حركة كاميرا بطيئة للداخل أو انزلاق جانبي هادئ"
                i == 1 -> "اقتراب سينمائي بطيء مع اهتزاز طفيف جدًا عند التوتر"
                else -> "تثبيت قصير ثم حركة دقيقة تكشف التفصيل"
            }
            val prompt = "صورة ثابتة فوتوغرافية واقعية للغاية لفيلم رعب $horror. " +
                "المشهد: $text. الموقع: $setting. الجو: $mood. " +
                "${shotTemplate.second}. ${shotTemplate.third}. الإضاءة: $lighting. " +
                "الأسلوب: $style. نسبة الإطار: $ratio. استمرارية بصرية ملزمة:\n$continuity\n" +
                "تكوين سينمائي احترافي وخامات واقعية وتفاصيل دقيقة، بلا نصوص أو شعارات أو علامة مائية. " +
                "لا تضف شخصيات أو أحداثًا أو مواقع أو مصادر صوت غير مذكورة."
            ShotPlan((sceneNumber - 1) * 3 + i + 1, shotTemplate.first, shotTemplate.second, shotTemplate.third,
                lighting, motion, durations[i].coerceAtLeast(1), prompt)
        }
    }
}
