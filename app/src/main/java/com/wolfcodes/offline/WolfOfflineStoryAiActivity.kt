package com.wolfcodes.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** Separate on-device story studio. Model loading, copying and generation never run on the UI thread. */
class WolfOfflineStoryAiActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val model = WolfOfflineStoryAiEngine.LocalModel()
    private lateinit var status: TextView
    private lateinit var planView: TextView
    private lateinit var titleField: EditText
    private lateinit var premiseField: EditText
    private lateinit var genreField: EditText
    private lateinit var settingField: EditText
    private lateinit var protagonistField: EditText
    private lateinit var toneField: EditText
    private lateinit var endingField: EditText
    private lateinit var wordsField: EditText
    private lateinit var storyField: EditText
    private lateinit var libraryToggle: CheckBox
    private lateinit var referenceChoicesContainer: LinearLayout
    private var session: WolfOfflineStoryAiEngine.Session? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        refreshReferenceChoices()
        updateModelStatus()
        setBusy(true, "جارٍ استعادة آخر جلسة تأليف محفوظة...")
        worker.execute {
            val restored = WolfOfflineStoryAiEngine.loadLatestSession(WolfOfflineStoryAiEngine.sessionRoot(this))
            runOnUiThread {
                session = restored
                if (restored == null) setBusy(false, "ابدأ باستيراد نموذج محلي لإنشاء قصتك الأولى.")
                else {
                    renderSession()
                    setBusy(false, "تمت استعادة الجلسة: ${restored.chapters.size} / ${restored.plan.chapterTitles.size} فصل.")
                }
            }
        }
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xFF07070D.toInt())
        }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(label("🐺 WOLF OFFLINE STORY AI").apply {
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFFD3223C.toInt())
        })
        root.addView(label("تأليف قصص رعب محليًا على الهاتف — دون إنترنت أو اشتراك أو API"))
        root.addView(button("استيراد نموذج ذكاء اصطناعي محلي (.litertlm)").apply {
            setOnClickListener { chooseModel() }
        })
        root.addView(label("عنوان القصة"))
        titleField = field("عنوان مبدئي")
        root.addView(titleField)
        root.addView(label("الفكرة الأساسية"))
        premiseField = field("اكتب فكرة القصة في سطرين أو أكثر").apply { minLines = 4; gravity = Gravity.TOP }
        root.addView(premiseField)
        root.addView(label("نوع الرعب"))
        genreField = field("رعب نفسي / خارق / غموض / هلوسة / واقعي / فولكلور")
        genreField.setText(WolfOfflineStoryAiEngine.Genre.PSYCHOLOGICAL.label)
        root.addView(genreField)
        root.addView(label("المكان والزمن"))
        settingField = field("مثال: قرية مصرية معزولة في شتاء 1998")
        root.addView(settingField)
        root.addView(label("الشخصية الرئيسية"))
        protagonistField = field("الاسم والخلفية والدافع")
        root.addView(protagonistField)
        root.addView(label("النبرة"))
        toneField = field("سينمائية مظلمة، توتر نفسي وتصاعد تدريجي")
        toneField.setText("سينمائية مظلمة، توتر نفسي وتصاعد تدريجي")
        root.addView(toneField)
        root.addView(label("شكل النهاية"))
        endingField = field("صادمة / مفتوحة / مأساوية / ملتوية")
        endingField.setText("صادمة ومتماسكة مع تمهيد سابق")
        root.addView(endingField)
        root.addView(label("الطول المستهدف بالكلمات (500 إلى 50,000)"))
        wordsField = field("مثال: 3000").apply { setText("3000") }
        root.addView(wordsField)
        libraryToggle = CheckBox(this).apply {
            text = "استخدام المراجع المسموح بها من WOLF HORROR KNOWLEDGE LIBRARY"
            isChecked = true
            setTextColor(0xFFE0E0E8.toInt())
        }
        root.addView(libraryToggle)
        root.addView(label("اختر حتى 3 مراجع بعينها (اختياري). إذا لم تختر، سيبحث المحرك تلقائيًا في المراجع المسموح بها."))
        referenceChoicesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(referenceChoicesContainer)
        root.addView(button("تحديث قائمة مراجع المكتبة").apply { setOnClickListener { refreshReferenceChoices() } })
        root.addView(button("إنشاء مخطط القصة والشخصيات والأسرار والنهاية").apply {
            setOnClickListener { createPlan() }
        })
        root.addView(button("توليد الفصل التالي / استكمال الكتابة").apply {
            setOnClickListener { generateNextChapter() }
        })
        root.addView(button("استعادة آخر جلسة محفوظة").apply {
            setOnClickListener { restoreSession() }
        })
        root.addView(label("المخطط"))
        planView = label("لم يتم إنشاء مخطط بعد")
        root.addView(planView)
        root.addView(label("النص النهائي — يمكنك مراجعته وتعديله قبل الاعتماد"))
        storyField = field("ستظهر الفصول هنا بالتتابع").apply {
            minLines = 12
            gravity = Gravity.TOP
            isEnabled = false
        }
        root.addView(storyField)
        root.addView(button("اعتماد القصة النهائية وحفظها في مكتبة المعرفة").apply {
            setOnClickListener { approveAndSave() }
        })
        status = label("لم يتم تحميل نموذج بعد").apply { gravity = Gravity.CENTER }
        root.addView(status)
        setContentView(scroll)
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0xFF888888.toInt())
        setPadding(14, 10, 14, 10)
        setBackgroundColor(0xFF20202A.toInt())
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(0xFFE0E0E8.toInt())
        setPadding(4, 12, 4, 6)
    }

    private fun button(value: String) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF850D22.toInt())
    }

    private fun chooseModel() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-binary"))
        }
        startActivityForResult(intent, REQUEST_MODEL)
    }

    @Deprecated("Document picker result is handled for supported Android versions.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL || resultCode != RESULT_OK || data?.data == null) return
        importModel(data.data!!)
    }

    private fun importModel(uri: Uri) {
        setBusy(true, "جارٍ نسخ النموذج إلى مساحة التطبيق الخاصة...")
        worker.execute {
            val target = WolfOfflineStoryAiEngine.modelFile(this)
            val parent = target.parentFile!!
            val pending = File(parent, "model.pending")
            val backup = File(parent, "model.previous")
            val result = runCatching {
                if (!parent.exists() && !parent.mkdirs()) error("تعذر إنشاء مجلد النموذج")
                val name = queryDisplayName(uri)
                require(name.endsWith(WolfOfflineStoryAiEngine.MODEL_EXTENSION, true)) {
                    "اختر ملفًا بصيغة .litertlm متوافقًا مع LiteRT-LM"
                }
                if (!target.exists() && backup.exists()) backup.renameTo(target)
                else if (target.exists() && backup.exists()) backup.delete()
                model.close()
                // The LiteRT runtime cache belongs to the previous model; never reuse it after replacement.
                File(parent, "runtime_cache").deleteRecursively()
                pending.delete()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(pending).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_MODEL_BYTES) { "حجم النموذج أكبر من الحد الآمن (4 جيجابايت)" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                } ?: error("تعذر فتح ملف النموذج")
                require(pending.length() > MIN_MODEL_BYTES) { "ملف النموذج صغير جدًا أو تالف" }
                if (backup.exists()) backup.delete()
                if (target.exists() && !target.renameTo(backup)) error("تعذر تجهيز النموذج الحالي")
                if (!pending.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target)
                    error("تعذر تثبيت ملف النموذج")
                }
                backup.delete()
                target.length()
            }
            pending.delete()
            runOnUiThread {
                setBusy(false, result.fold(
                    { "تم استيراد النموذج (${it / (1024 * 1024)} MB). اضغط إنشاء المخطط للبدء." },
                    { "فشل استيراد النموذج: ${it.message}" }
                ))
                updateModelStatus()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index).orEmpty()
                }
            }
        return uri.lastPathSegment.orEmpty()
    }

    private fun briefFromFields() = WolfOfflineStoryAiEngine.Brief(
        titleField.text.toString().trim(), premiseField.text.toString().trim(),
        genreField.text.toString().trim(), settingField.text.toString().trim(),
        protagonistField.text.toString().trim(), toneField.text.toString().trim(),
        endingField.text.toString().trim(), wordsField.text.toString().toIntOrNull() ?: -1,
        libraryToggle.isChecked
    )

    private fun createPlan() {
        if (busy) return
        val brief = runCatching { briefFromFields().also(WolfOfflineStoryAiEngine::validateBrief) }
            .getOrElse { status.text = it.message; return }
        if (!WolfOfflineStoryAiEngine.modelFile(this).isFile) {
            status.text = "استورد نموذج .litertlm محليًا أولًا. التطبيق لا ينزّل نماذج ولا يتصل بالإنترنت."
            return
        }
        val selectedIds = selectedReferenceIds()
        setBusy(true, "جارٍ تحميل النموذج المحلي وإعداد مخطط القصة...")
        worker.execute {
            val result = runCatching {
                if (!model.isLoaded()) model.load(WolfOfflineStoryAiEngine.modelFile(this))
                val refs = loadReferences(brief.premise, brief.useKnowledgeLibrary, selectedIds)
                val raw = model.generate(WolfOfflineStoryAiEngine.buildPlanPrompt(brief, refs))
                val plan = WolfOfflineStoryAiEngine.parsePlan(raw, brief)
                WolfOfflineStoryAiEngine.newSession(brief, plan).also {
                    WolfOfflineStoryAiEngine.saveSession(WolfOfflineStoryAiEngine.sessionRoot(this), it)
                }
            }
            runOnUiThread {
                result.onSuccess {
                    session = it
                    planView.text = formatPlan(it)
                    storyField.setText("")
                    storyField.isEnabled = false
                    setBusy(false, "تم إنشاء المخطط. راجعه ثم ابدأ توليد الفصل الأول.")
                }.onFailure { setBusy(false, "تعذر إنشاء المخطط: ${it.message}") }
            }
        }
    }

    private fun generateNextChapter() {
        if (busy) return
        val current = session
        if (current == null) {
            status.text = "أنشئ مخطط القصة أولًا أو استعد جلسة محفوظة."
            return
        }
        if (current.isComplete) {
            status.text = "اكتملت كل الفصول. راجع النص النهائي ثم اعتمده."
            return
        }
        val selectedIds = selectedReferenceIds()
        setBusy(true, "جارٍ توليد الفصل ${current.nextChapterIndex + 1} وحفظ نقطة الاستعادة...")
        worker.execute {
            val result = runCatching {
                if (!model.isLoaded()) model.load(WolfOfflineStoryAiEngine.modelFile(this))
                val refs = loadReferences(current.brief.premise, current.brief.useKnowledgeLibrary, selectedIds)
                val chapter = model.generate(WolfOfflineStoryAiEngine.buildChapterPrompt(current, refs))
                WolfOfflineStoryAiEngine.withChapter(current, chapter).also {
                    WolfOfflineStoryAiEngine.saveSession(WolfOfflineStoryAiEngine.sessionRoot(this), it)
                }
            }
            runOnUiThread {
                result.onSuccess {
                    session = it
                    storyField.setText(formatChapters(it))
                    storyField.isEnabled = it.isComplete
                    setBusy(false, if (it.isComplete) "اكتملت الفصول. راجع القصة واعتمدها عند رضاك."
                    else "تم حفظ الفصل ${it.chapters.size} من ${it.plan.chapterTitles.size}. يمكنك المتابعة لاحقًا.")
                }.onFailure { setBusy(false, "تعذر توليد الفصل: ${it.message}") }
            }
        }
    }

    private fun selectedReferenceIds(): Set<String> =
        (0 until referenceChoicesContainer.childCount)
            .mapNotNull { referenceChoicesContainer.getChildAt(it) as? CheckBox }
            .filter { it.isChecked }
            .mapNotNull { it.tag as? String }
            .toSet()

    private fun refreshReferenceChoices() {
        if (!::referenceChoicesContainer.isInitialized) return
        worker.execute {
            val root = File(filesDir, "wolf_horror_knowledge_library")
            val allAllowed = WolfHorrorKnowledgeLibrary.selectedStoryReferences(root)
            val entries = allAllowed.take(40)
            runOnUiThread {
                referenceChoicesContainer.removeAllViews()
                if (entries.isEmpty()) {
                    referenceChoicesContainer.addView(label("لا توجد مراجع مسموح بها بعد. أضف قصصًا إلى المكتبة أو فعّل استخدامها من إعداداتها."))
                } else {
                    entries.forEach { entry ->
                        referenceChoicesContainer.addView(CheckBox(this).apply {
                            text = "${entry.title} — ${entry.sourceType}"
                            tag = entry.id
                            setTextColor(0xFFE0E0E8.toInt())
                            setOnCheckedChangeListener { view, checked ->
                                if (checked && selectedReferenceIds().size > 3) {
                                    view.isChecked = false
                                    Toast.makeText(this@WolfOfflineStoryAiActivity, "يمكن اختيار 3 مراجع كحد أقصى", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                    }
                    if (allAllowed.size > 40) {
                        referenceChoicesContainer.addView(label("تُعرض أول 40 نتيجة فقط في هذه القائمة."))
                    }
                }
            }
        }
    }

    private fun loadReferences(query: String, enabled: Boolean, selectedIds: Set<String>): List<String> {
        if (!enabled) return emptyList()
        val root = File(filesDir, "wolf_horror_knowledge_library")
        val matches = if (selectedIds.isNotEmpty()) {
            WolfHorrorKnowledgeLibrary.selectedStoryReferences(root, selectedIds)
        } else {
            val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val focusedQuery = terms.take(2).joinToString(" ")
            WolfHorrorKnowledgeLibrary.searchStoryReferences(root, focusedQuery)
                .ifEmpty { WolfHorrorKnowledgeLibrary.searchStoryReferences(root, terms.firstOrNull().orEmpty()) }
        }
        return matches.take(3).mapNotNull { entry ->
            val text = WolfHorrorKnowledgeLibrary.readContent(root, entry.id) ?: return@mapNotNull null
            "عنوان المرجع: ${entry.title}\nنوع المصدر: ${entry.sourceType}\nملخص: ${entry.summary}\nمقتطف: ${text.take(1_200)}"
        }
    }
    private fun formatPlan(value: WolfOfflineStoryAiEngine.Session) = buildString {
        appendLine("المخطط — ${value.brief.title}")
        appendLine(value.plan.logline)
        appendLine("\nالشخصيات:\n${value.plan.characters}")
        appendLine("\nالأسرار وقواعد العالم:\n${value.plan.secrets}")
        appendLine("\nالنهاية المخططة:\n${value.plan.ending}")
        value.plan.chapterTitles.forEachIndexed { i, title -> appendLine("\n${i + 1}. $title") }
    }

    private fun formatChapters(value: WolfOfflineStoryAiEngine.Session) = buildString {
        value.chapters.forEachIndexed { i, chapter ->
            appendLine("الفصل ${i + 1} — ${value.plan.chapterTitles[i]}\n")
            appendLine(chapter)
            appendLine()
        }
    }

    private fun renderSession() {
        val value = session ?: return
        titleField.setText(value.brief.title)
        premiseField.setText(value.brief.premise)
        genreField.setText(value.brief.genre)
        settingField.setText(value.brief.setting)
        protagonistField.setText(value.brief.protagonist)
        toneField.setText(value.brief.tone)
        endingField.setText(value.brief.ending)
        wordsField.setText(value.brief.targetWords.toString())
        libraryToggle.isChecked = value.brief.useKnowledgeLibrary
        planView.text = formatPlan(value)
        storyField.setText(value.finalText.ifBlank { formatChapters(value) })
        storyField.isEnabled = value.isComplete
        status.text = "تم استعادة جلسة محفوظة: ${value.chapters.size} / ${value.plan.chapterTitles.size} فصل"
    }

    private fun restoreSession() {
        if (busy) return
        worker.execute {
            val loaded = WolfOfflineStoryAiEngine.loadLatestSession(WolfOfflineStoryAiEngine.sessionRoot(this))
            runOnUiThread {
                session = loaded
                if (loaded == null) status.text = "لا توجد جلسة محفوظة بعد" else renderSession()
            }
        }
    }

    private fun approveAndSave() {
        if (busy) return
        val value = session
        if (value == null || !value.isComplete) {
            status.text = "لا يمكن اعتماد القصة قبل اكتمال جميع الفصول."
            return
        }
        val finalText = storyField.text.toString().trim()
        if (finalText.length < 500) {
            status.text = "النص النهائي قصير جدًا؛ راجع الفصول وأكملها أولًا."
            return
        }
        if (finalText.length > WolfOfflineStoryAiEngine.MAX_STORY_CHARS) {
            status.text = "النص النهائي تجاوز الحد المحلي الآمن البالغ 4 ملايين حرف."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("اعتماد القصة النهائية")
            .setMessage("سيتم حفظ النص الذي راجعته الآن كقصة من تأليف التطبيق داخل مكتبة المعرفة. هل توافق؟")
            .setPositiveButton("اعتماد وحفظ") { _, _ ->
                setBusy(true, "جارٍ حفظ النسخة النهائية المعتمدة...")
                worker.execute {
                    val result = runCatching {
                        val root = WolfOfflineStoryAiEngine.sessionRoot(this)
                        // Persist the user's reviewed text before attempting the optional library save.
                        val reviewed = value.copy(
                            finalText = finalText,
                            approved = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        WolfOfflineStoryAiEngine.saveSession(root, reviewed)
                        val saved = WolfHorrorKnowledgeLibrary.saveFinalAppAuthoredStory(
                            File(filesDir, "wolf_horror_knowledge_library"),
                            value.brief.title, finalText, isFinalApproved = true,
                            summary = value.plan.logline,
                            tags = listOf(value.brief.genre, "WOLF OFFLINE STORY AI")
                        )
                        val approved = reviewed.copy(approved = true, updatedAt = System.currentTimeMillis())
                        WolfOfflineStoryAiEngine.saveSession(root, approved)
                        approved to saved
                    }
                    runOnUiThread {
                        result.onSuccess { (approved, saved) ->
                            session = approved
                            storyField.setText(approved.finalText)
                            setBusy(false, when {
                                saved == null -> "تم اعتماد النص وحفظه داخل جلسة التأليف. الحفظ التلقائي في المكتبة معطّل من إعداداتها."
                                saved.duplicateOfExisting -> "تم الاعتماد؛ المحتوى موجود بالفعل في المكتبة."
                                else -> "تم اعتماد القصة وحفظها في WOLF HORROR KNOWLEDGE LIBRARY."
                            })
                        }.onFailure {
                            session = value.copy(finalText = finalText, approved = false)
                            setBusy(false, "تم الاحتفاظ بالنص المعدّل كمسودة إن أمكن؛ تعذر إكمال الاعتماد: ${it.message}")
                        }
                    }
                }
            }
            .setNegativeButton("مراجعة لاحقًا", null)
            .show()
    }

    override fun onPause() {
        // Preserve completed-story edits even if the user leaves before pressing Approve.
        val current = session
        val reviewedText = if (::storyField.isInitialized) storyField.text.toString().trim() else ""
        if (!busy && current != null && current.isComplete &&
            reviewedText.length in 500..WolfOfflineStoryAiEngine.MAX_STORY_CHARS
        ) {
            val unchangedApprovedText = current.approved && reviewedText == current.finalText
            val draft = current.copy(
                finalText = reviewedText,
                approved = unchangedApprovedText,
                updatedAt = System.currentTimeMillis()
            )
            runCatching {
                worker.execute {
                    runCatching {
                        WolfOfflineStoryAiEngine.saveSession(WolfOfflineStoryAiEngine.sessionRoot(this), draft)
                    }
                }
            }
        }
        super.onPause()
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        status.text = message
    }

    private fun updateModelStatus() {
        val file = WolfOfflineStoryAiEngine.modelFile(this)
        if (file.isFile) status.text = "نموذج محلي مستورد: ${file.length() / (1024 * 1024)} MB — يعمل دون إنترنت."
    }

    override fun onDestroy() {
        worker.execute { model.close() }
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_MODEL = 1181
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val MAX_MODEL_BYTES = 4L * 1024L * 1024L * 1024L
    }
}