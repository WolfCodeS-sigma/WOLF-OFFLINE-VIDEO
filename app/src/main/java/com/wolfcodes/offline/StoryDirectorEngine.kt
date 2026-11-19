package com.wolfcodes.offline

import java.util.Locale

/** Deterministic first-pass director: creates a structured shot plan without network access. */
object StoryDirectorEngine {
    data class ShotPlan(val number: Int, val type: String, val camera: String, val composition: String,
        val lighting: String, val motion: String, val durationSeconds: Int, val prompt: String)
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
        val placeWords = listOf("منزل", "بيت", "غرفة", "ممر", "مستشفى", "مدرسة", "غابة", "شارع", "قصر", "مبنى", "قبو")
        val appearance = sentences.filter { s -> appearanceWords.any { s.contains(it) } }.take(4).joinToString("؛ ")
        val places = sentences.filter { s -> placeWords.any { s.contains(it) } }.take(4).joinToString("؛ ")
        val notes = mutableListOf<String>()
        if (appearance.isNotBlank()) notes.add("تفاصيل الشخصيات المذكورة: $appearance")
        if (places.isNotBlank()) notes.add("تفاصيل المواقع المذكورة: $places")
        notes.add("ثبّت ملامح الشخصيات وملابسها وتصميم الموقع بين اللقطات. لا تخترع تفاصيل غير مذكورة؛ اتركها محايدة وثابتة.")
        return notes.joinToString("\n")
    }

    private fun detectSetting(text: String): String {
        val options = listOf(
            listOf("منزل", "بيت", "شقة", "غرفة") to "مكان داخلي سكني",
            listOf("مستشفى", "عيادة", "مشرحة") to "مكان طبي",
            listOf("غابة", "أشجار") to "غابة كثيفة",
            listOf("شارع", "طريق", "سيارة") to "طريق أو شارع",
            listOf("مدرسة", "فصل", "جامعة") to "مبنى تعليمي",
            listOf("قبر", "مقابر", "مقبرة") to "مقابر"
        )
        return options.firstOrNull { entry -> entry.first.any { text.contains(it) } }?.second
            ?: "الموقع الذي تصفه أحداث القصة، دون إضافة موقع جديد"
    }

    private fun detectMood(text: String, horrorType: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            listOf("يطارد", "يجري", "هرب", "مطاردة", "يلحق").any { lower.contains(it) } -> "توتر ومطاردة متصاعدة"
            listOf("همس", "صوت", "أنفاس", "ينادي", "طرق").any { lower.contains(it) } -> "رعب سمعي وترقب"
            listOf("ظهر", "شبح", "ظل", "اختفى", "وجه").any { lower.contains(it) } -> "ظهور خارق وغموض"
            else -> "رعب $horrorType بإيقاع مقلق"
        }
    }

    private fun buildShots(sceneNumber: Int, text: String, sceneDuration: Int, ratio: String,
        style: String, horror: String, setting: String, mood: String, continuity: String): List<ShotPlan> {
        val templates = listOf(
            Triple("لقطة تأسيسية", "لقطة واسعة بعدسة 24mm من مستوى العين", "أظهر المكان والعزلة والمساحة السلبية حول الحدث"),
            Triple("لقطة اقتراب", "لقطة متوسطة بعدسة 50mm وعمق مجال سينمائي", "قرّب الانتباه إلى الشخصية أو الحدث الأساسي"),
            Triple("لقطة تفصيل أو كشف", "لقطة قريبة بعدسة 85mm أو زاوية منخفضة عند ملاءمة الحدث", "اكشف تفصيلًا مقلقًا موجودًا في النص دون اختراع حدث")
        ).take(sceneDuration.coerceIn(1, 3))
        val oneThird = sceneDuration / templates.size
        val durations = List(templates.size) { index ->
            if (index == templates.lastIndex) sceneDuration - oneThird * index else oneThird
        }
        return templates.mapIndexed { i, shotTemplate ->
            val lighting = when (i) {
                0 -> "إضاءة محيطية منخفضة ومصدر ضوء منطقي داخل الموقع"
                1 -> "إضاءة جانبية درامية وظلال عميقة مع وضوح الحدث"
                else -> "ضوء محدود وموجّه يبرز التفصيل المرعب"
            }
            val motion = when (i) {
                0 -> "حركة كاميرا بطيئة للداخل أو انزلاق جانبي هادئ"
                1 -> "اقتراب سينمائي بطيء مع اهتزاز طفيف جدًا عند التوتر"
                else -> "تثبيت قصير ثم حركة دقيقة تكشف التفصيل"
            }
            val prompt = "صورة ثابتة فوتوغرافية واقعية للغاية لفيلم رعب $horror. " +
                "المشهد: $text. الموقع: $setting. الجو: $mood. " +
                "${shotTemplate.second}. ${shotTemplate.third}. الإضاءة: $lighting. " +
                "الأسلوب: $style. نسبة الإطار: $ratio. استمرارية بصرية ملزمة:\n$continuity\n" +
                "تكوين سينمائي احترافي وخامات واقعية وتفاصيل دقيقة، بلا نصوص أو شعارات أو علامة مائية. " +
                "لا تضف شخصيات أو أحداثًا غير مذكورة."
            ShotPlan((sceneNumber - 1) * 3 + i + 1, shotTemplate.first, shotTemplate.second, shotTemplate.third,
                lighting, motion, durations[i].coerceAtLeast(1), prompt)
        }
    }
}
