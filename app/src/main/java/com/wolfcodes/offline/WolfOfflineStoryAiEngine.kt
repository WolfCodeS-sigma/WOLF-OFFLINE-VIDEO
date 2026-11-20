package com.wolfcodes.offline

import android.content.Context
import com.google.ai.edge.litertlm.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Local story-authoring pipeline. All model calls must be serialized on one worker thread. */
object WolfOfflineStoryAiEngine {
    const val MIN_TARGET_WORDS = 500
    const val MAX_TARGET_WORDS = 50_000
    const val MAX_CHAPTERS = 64
    const val MODEL_EXTENSION = ".litertlm"
    const val MODEL_DIRECTORY = "wolf_story_ai"
    const val SESSION_DIRECTORY = "wolf_story_ai/sessions"

    enum class Genre(val label: String) {
        PSYCHOLOGICAL("رعب نفسي"), SUPERNATURAL("رعب خارق للطبيعة"),
        MYSTERY("غموض وتحقيق"), HALLUCINATION("هلوسة واضطراب إدراك"),
        REALISTIC("رعب واقعي / رواية شخصية"), FOLKLORE("فولكلور وأساطير")
    }

    data class Brief(
        val title: String, val premise: String, val genre: String, val setting: String,
        val protagonist: String, val tone: String, val ending: String, val targetWords: Int,
        val useKnowledgeLibrary: Boolean = true
    )

    data class Plan(
        val logline: String, val characters: String, val secrets: String, val ending: String,
        val continuitySummary: String, val chapterTitles: List<String>, val raw: String
    )

    data class Session(
        val id: String, val brief: Brief, val plan: Plan, val chapters: List<String>,
        val continuitySummary: String, val approved: Boolean, val createdAt: Long, val updatedAt: Long,
        val finalText: String = ""
    ) {
        val nextChapterIndex: Int get() = chapters.size
        val isComplete: Boolean get() = chapters.size == plan.chapterTitles.size
    }

    class LocalModel {
        private var engine: Engine? = null

        fun load(modelFile: File) {
            check(engine == null) { "المحرك محمّل بالفعل" }
            require(modelFile.isFile && modelFile.length() > 1_000_000L) {
                "ملف النموذج المحلي غير موجود أو غير صالح"
            }
            val candidate = Engine(EngineConfig(
                modelPath = modelFile.absolutePath,
                cacheDir = File(modelFile.parentFile, "runtime_cache").absolutePath
            ))
            try {
                candidate.initialize()
                engine = candidate
            } catch (error: Throwable) {
                if (candidate.isInitialized()) runCatching { candidate.close() }
                throw error
            }
        }

        fun generate(prompt: String): String {
            val active = engine ?: error("حمّل نموذجًا محليًا أولًا")
            require(prompt.isNotBlank())
            // A fresh conversation per request prevents the context from growing with every chapter.
            val chat = active.createConversation(ConversationConfig(
                systemInstruction = Contents.of(
                    "أنت WOLF OFFLINE STORY AI، كاتب رعب عربي محترف. اكتب نصوصًا أصلية من الصفر. " +
                    "استخدم المراجع كسياق فقط ولا تنسخ منها عبارات أو أحداثًا مميزة. ميّز بين الوقائع الموثقة " +
                    "والروايات الشخصية والخيال، ولا تعرض الخيال كواقعة حقيقية. التزم بالمخطط واستمرارية الشخصيات."
                ),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.92, temperature = 0.82),
                maxOutputToken = 1536
            ))
            return try {
                chat.sendMessage(prompt).toString().trim().also {
                    require(it.isNotBlank()) { "النموذج لم يُنتج نصًا" }
                }
            } finally {
                runCatching { chat.close() }
            }
        }

        fun isLoaded(): Boolean = engine?.isInitialized() == true

        fun close() {
            val active = engine
            engine = null
            if (active != null && active.isInitialized()) runCatching { active.close() }
        }
    }

    fun modelFile(context: Context) = File(context.filesDir, "$MODEL_DIRECTORY/story-model$MODEL_EXTENSION")
    fun sessionRoot(context: Context) = File(context.filesDir, SESSION_DIRECTORY)

    fun chapterCount(words: Int): Int {
        require(words in MIN_TARGET_WORDS..MAX_TARGET_WORDS)
        return ((words + 799) / 800).coerceIn(1, MAX_CHAPTERS)
    }

    fun validateBrief(v: Brief) {
        require(v.title.isNotBlank()) { "اكتب عنوان القصة" }
        require(v.premise.trim().length >= 20) { "اكتب فكرة أوضح للقصة (20 حرفًا على الأقل)" }
        require(v.targetWords in MIN_TARGET_WORDS..MAX_TARGET_WORDS) { "عدد الكلمات يجب أن يكون بين 500 و50,000" }
        require(v.genre.isNotBlank() && v.tone.isNotBlank()) { "حدد نوع الرعب والنبرة" }
    }

    fun buildPlanPrompt(v: Brief, refs: List<String>): String {
        validateBrief(v)
        return """
            اكتب مخطط رواية رعب عربية أصلية قابلة للتنفيذ على فصول.
            العنوان: ${v.title}
            الفكرة: ${v.premise}
            النوع: ${v.genre}
            المكان والزمن: ${v.setting}
            الشخصية المحورية: ${v.protagonist}
            النبرة: ${v.tone}
            شكل النهاية: ${v.ending}
            الطول المستهدف: ${v.targetWords} كلمة تقريبًا.
            عدد الفصول: ${chapterCount(v.targetWords)}.
            ابدأ بخطاف قوي وتصاعد تدريجي وأحداث منطقية وأسرار تكشف تدريجيًا ونهاية مؤثرة.
            لا تكتب الرواية الآن. أخرج JSON صالحًا فقط بالمفاتيح:
            logline, characters, secrets, ending, continuity_summary, chapter_titles (array).
            يجب أن يحتوي chapter_titles على ${chapterCount(v.targetWords)} عنوانًا بالضبط.
            اكتب عملًا أصليًا ولا تنسخ أي مرجع.
            المراجع السياقية:
            ${refs.joinToString("\n---\n").ifBlank { "لا توجد مراجع مختارة." }}
        """.trimIndent()
    }

    fun parsePlan(raw: String, brief: Brief): Plan {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        val json = if (start >= 0 && end > start) runCatching {
            JSONObject(raw.substring(start, end + 1))
        }.getOrNull() else null
        val expected = chapterCount(brief.targetWords)
        val titles = json?.optJSONArray("chapter_titles")?.toStringList()
            ?.map { it.trim().take(120) }?.filter { it.isNotBlank() }.orEmpty()
        val safeTitles = if (titles.size == expected) titles else (1..expected).map { "الفصل $it" }
        return Plan(
            json?.optString("logline").orEmpty().ifBlank { raw.take(2_000) },
            json?.optString("characters").orEmpty(), json?.optString("secrets").orEmpty(),
            json?.optString("ending").orEmpty().ifBlank { brief.ending },
            json?.optString("continuity_summary").orEmpty().ifBlank { raw.take(2_000) },
            safeTitles, raw.take(30_000)
        )
    }

    fun buildChapterPrompt(s: Session, refs: List<String>): String {
        val i = s.nextChapterIndex
        require(i in s.plan.chapterTitles.indices) { "اكتملت فصول الرواية" }
        val target = (s.brief.targetWords / s.plan.chapterTitles.size).coerceAtLeast(500)
        return """
            اكتب الفصل ${i + 1} من ${s.plan.chapterTitles.size} بعنوان:
            ${s.plan.chapterTitles[i]}
            اكتب بالعربية الفصحى السلسة وحوار طبيعي مميز لكل شخصية.
            الفكرة: ${s.brief.premise}
            النوع والنبرة: ${s.brief.genre} — ${s.brief.tone}
            المكان: ${s.brief.setting}
            الشخصيات: ${s.plan.characters}
            الأسرار وقواعد العالم: ${s.plan.secrets}
            مسار النهاية: ${s.plan.ending}
            ملخص الاستمرارية: ${s.continuitySummary.take(3_000)}
            آخر النص السابق (للاتساق فقط، لا تعِد كتابته):
            ${s.chapters.lastOrNull()?.takeLast(1_500).orEmpty()}
            مراجع سياقية ممنوع النسخ منها:
            ${refs.joinToString("\n---\n").ifBlank { "لا توجد مراجع." }}
            اكتب هذا الفصل فقط بمشاهد حية وتفاصيل حسية وتشويق متصاعد.
            استهدف نحو ${target} كلمة ولا تلخص الأحداث ولا تكتب ملاحظات خارج الرواية.
        """.trimIndent()
    }

    fun updateContinuity(previous: String, chapter: String): String =
        listOf(previous.trim().take(1_200), chapter.trim().takeLast(1_800))
            .filter { it.isNotBlank() }.joinToString("\n[آخر تطورات الفصل]\n").takeLast(3_000)

    fun newSession(brief: Brief, plan: Plan): Session {
        val now = System.currentTimeMillis()
        return Session(UUID.randomUUID().toString(), brief, plan, emptyList(), plan.continuitySummary, false, now, now)
    }

    fun withChapter(s: Session, chapter: String): Session {
        require(!s.isComplete) { "اكتملت فصول الرواية" }
        val clean = chapter.trim()
        require(clean.length >= 80) { "الفصل الناتج قصير جدًا؛ أعد المحاولة قبل حفظه" }
        require(clean.length <= 100_000) { "الفصل تجاوز الحد الآمن للحفظ" }
        return s.copy(
            chapters = s.chapters + clean,
            continuitySummary = updateContinuity(s.continuitySummary, clean),
            approved = false,
            updatedAt = System.currentTimeMillis(),
            finalText = ""
        )
    }

    fun saveSession(root: File, s: Session) {
        require(s.id.matches(Regex("[A-Za-z0-9-]{8,64}")))
        require(s.chapters.size <= s.plan.chapterTitles.size && s.chapters.all { it.length <= 100_000 })
        if (!root.exists() && !root.mkdirs()) error("تعذر إنشاء مجلد جلسات التأليف")
        val target = File(root, "${s.id}.json")
        val pending = File(root, "${s.id}.json.pending")
        val backup = File(root, "${s.id}.json.backup")
        val json = JSONObject().put("id", s.id).put("brief", briefJson(s.brief))
            .put("plan", planJson(s.plan)).put("chapters", JSONArray(s.chapters))
            .put("continuitySummary", s.continuitySummary).put("approved", s.approved)
            .put("createdAt", s.createdAt).put("updatedAt", s.updatedAt)
            .put("finalText", s.finalText)
        try {
            FileOutputStream(pending).use { out ->
                out.write(json.toString().toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (backup.exists()) backup.delete()
            if (target.exists() && !target.renameTo(backup)) error("تعذر تجهيز نقطة الاستعادة السابقة")
            if (!pending.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("تعذر حفظ نقطة استعادة القصة")
            }
            backup.delete()
        } catch (error: Exception) {
            pending.delete()
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    fun loadLatestSession(root: File): Session? {
        if (!root.isDirectory) return null
        // Recover a checkpoint if the process stopped after moving the previous file to backup.
        root.listFiles { f -> f.isFile && f.name.endsWith(".json.backup") }?.forEach { backup ->
            val target = File(root, backup.name.removeSuffix(".backup"))
            if (!target.exists()) backup.renameTo(target) else backup.delete()
        }
        return root.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }?.firstOrNull()
            ?.let { runCatching { readSession(JSONObject(it.readText(Charsets.UTF_8))) }.getOrNull() }
    }

    private fun briefJson(v: Brief) = JSONObject().put("title", v.title).put("premise", v.premise)
        .put("genre", v.genre).put("setting", v.setting).put("protagonist", v.protagonist)
        .put("tone", v.tone).put("ending", v.ending).put("targetWords", v.targetWords)
        .put("useKnowledgeLibrary", v.useKnowledgeLibrary)

    private fun planJson(v: Plan) = JSONObject().put("logline", v.logline).put("characters", v.characters)
        .put("secrets", v.secrets).put("ending", v.ending).put("continuitySummary", v.continuitySummary)
        .put("chapterTitles", JSONArray(v.chapterTitles)).put("raw", v.raw)

    private fun readSession(j: JSONObject): Session {
        val b = j.getJSONObject("brief")
        val p = j.getJSONObject("plan")
        val a = j.optJSONArray("chapters") ?: JSONArray()
        val brief = Brief(
            b.getString("title"), b.getString("premise"), b.getString("genre"),
            b.optString("setting"), b.optString("protagonist"), b.getString("tone"),
            b.optString("ending"), b.getInt("targetWords"), b.optBoolean("useKnowledgeLibrary", true)
        )
        validateBrief(brief)
        val titles = (0 until (p.optJSONArray("chapterTitles")?.length() ?: 0))
            .map { p.getJSONArray("chapterTitles").getString(it) }
        require(titles.isNotEmpty() && titles.size <= MAX_CHAPTERS)
        val chapters = (0 until a.length()).map { a.getString(it) }
        require(chapters.size <= titles.size && chapters.all { it.length <= 100_000 })
        val plan = Plan(p.optString("logline"), p.optString("characters"), p.optString("secrets"),
            p.optString("ending"), p.optString("continuitySummary"), titles, p.optString("raw"))
        return Session(j.getString("id"), brief, plan, chapters, j.optString("continuitySummary"),
            j.optBoolean("approved"), j.optLong("createdAt"), j.optLong("updatedAt"),
            j.optString("finalText"))
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
}
