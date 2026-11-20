package com.wolfcodes.offline

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/** Separate UI for importing, organizing, editing and searching the offline TXT reference library. */
class WolfHorrorKnowledgeLibraryActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var titleField: EditText
    private lateinit var categoryField: AutoCompleteTextView
    private lateinit var folderField: AutoCompleteTextView
    private lateinit var tagsField: EditText
    private lateinit var sourceTypeField: AutoCompleteTextView
    private lateinit var sourceLabelField: EditText
    private lateinit var sourceDateField: EditText
    private lateinit var summaryField: EditText
    private lateinit var charactersField: EditText
    private lateinit var placesField: EditText
    private lateinit var eventsField: EditText
    private lateinit var themesField: EditText
    private lateinit var keywordsField: EditText
    private lateinit var contentField: EditText
    private lateinit var includeReferencesToggle: CheckBox
    private lateinit var autoSaveToggle: CheckBox
    private lateinit var searchField: EditText
    private lateinit var categoryFilterField: AutoCompleteTextView
    private lateinit var folderFilterField: AutoCompleteTextView
    private lateinit var entriesContainer: LinearLayout
    private lateinit var saveButton: Button

    private var activeEntryId: String? = null
    private var activeContentTooLargeToEdit = false
    private var lastQuery = ""
    private var lastCategoryFilter: String? = null
    private var lastFolderFilter: String? = null
    private var importing = false

    private val libraryRoot: File
        get() = File(filesDir, "wolf_horror_knowledge_library")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        refreshSuggestions()
        refreshEntries()
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xFF07070D.toInt())
        }
        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(label("🐺 WOLF HORROR KNOWLEDGE LIBRARY").apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFD3223C.toInt())
        })
        root.addView(label("مكتبة TXT محلية وخاصة بهذا الهاتف — لا تحتاج إلى إنترنت ولا ترفع قصصك إلى أي خادم."))

        root.addView(label("البحث في العناوين والمحتوى والتصنيفات والوسوم"))
        searchField = field("اكتب كلمة أو أكثر للبحث")
        root.addView(searchField)
        categoryFilterField = autoField("تصفية حسب التصنيف (اختياري)")
        categoryFilterField.setText("كل التصنيفات", false)
        root.addView(categoryFilterField)
        folderFilterField = autoField("تصفية حسب المجلد (اختياري)")
        root.addView(folderFilterField)
        val searchRow = horizontalRow()
        searchRow.addView(button("بحث").apply { setOnClickListener { runSearch() } }, weightedParams())
        searchRow.addView(button("عرض الكل").apply {
            setOnClickListener {
                searchField.setText("")
                categoryFilterField.setText("كل التصنيفات", false)
                folderFilterField.setText("")
                runSearch()
            }
        }, weightedParams())
        root.addView(searchRow)

        root.addView(label("بيانات القصة"))
        titleField = field("عنوان القصة")
        root.addView(titleField)
        categoryField = autoField("التصنيف — يمكنك كتابة تصنيف جديد")
        categoryField.setText("قصص المستخدم", false)
        root.addView(categoryField)
        folderField = autoField("المجلد — اتركه فارغًا أو اكتب اسم مجلد")
        root.addView(folderField)
        tagsField = field("الوسوم مفصولة بفاصلة")
        root.addView(tagsField)
        sourceTypeField = autoField("نوع المحتوى")
        sourceTypeField.setText(WolfHorrorKnowledgeLibrary.SOURCE_USER_SUBMITTED, false)
        root.addView(sourceTypeField)
        sourceLabelField = field("المصدر أو اسم الموقع/الكتاب/الشخص")
        root.addView(sourceLabelField)
        sourceDateField = field("تاريخ القصة أو المصدر — اختياري")
        root.addView(sourceDateField)
        summaryField = field("ملخص منفصل عن النص الأصلي")
        root.addView(summaryField)
        charactersField = field("الشخصيات — مفصولة بفواصل")
        placesField = field("الأماكن — مفصولة بفواصل")
        eventsField = field("الأحداث — مفصولة بفواصل")
        themesField = field("الموضوعات — مفصولة بفواصل")
        keywordsField = field("الكلمات المفتاحية — مفصولة بفواصل")
        root.addView(charactersField)
        root.addView(placesField)
        root.addView(eventsField)
        root.addView(themesField)
        root.addView(keywordsField)

        includeReferencesToggle = checkBox("السماح باستخدام هذه القصة كمصدر مرجعي للتأليف", true)
        root.addView(includeReferencesToggle)
        autoSaveToggle = checkBox("حفظ القصص النهائية التي يؤلفها التطبيق تلقائيًا", true)
        autoSaveToggle.isChecked =
            WolfHorrorKnowledgeLibrary.autoSaveAppAuthoredStoriesEnabled(libraryRoot)
        autoSaveToggle.setOnCheckedChangeListener { _, checked ->
            worker.execute {
                runCatching {
                    WolfHorrorKnowledgeLibrary.setAutoSaveAppAuthoredStories(libraryRoot, checked)
                }.onFailure { error ->
                    runOnUiThread { showError(error) }
                }
            }
        }
        root.addView(autoSaveToggle)

        root.addView(label("النص الكامل"))
        contentField = field("اكتب القصة هنا أو استورد ملف TXT").apply {
            minLines = 10
            gravity = Gravity.TOP
            setHorizontallyScrolling(false)
        }
        root.addView(contentField)

        saveButton = button("حفظ القصة في المكتبة")
        saveButton.setOnClickListener { saveEntry() }
        root.addView(saveButton)
        root.addView(button("قصة جديدة / إلغاء التعديل").apply { setOnClickListener { clearEditor() } })
        root.addView(button("استيراد ملف TXT واحد أو عدة ملفات من الهاتف").apply {
            setOnClickListener { chooseTxtFiles() }
        })
        root.addView(button("عرض النص الأصلي للقصة المحددة").apply { setOnClickListener { showOriginalText() } })

        status = label("المكتبة جاهزة")
        root.addView(status)
        root.addView(label("محتويات المكتبة"))
        entriesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(entriesContainer)
        root.addView(button("العودة إلى WOLF OFFLINE VIDEO").apply { setOnClickListener { finish() } })

        setContentView(scroll)
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0xFF888888.toInt())
        setPadding(14, 10, 14, 10)
        setBackgroundColor(0xFF20202A.toInt())
        setSingleLine(false)
    }

    private fun autoField(hintText: String): AutoCompleteTextView =
        AutoCompleteTextView(this).apply {
            hint = hintText
            threshold = 1
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xFF20202A.toInt())
        }

    private fun label(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFFE0E0E8.toInt())
        setPadding(4, 12, 4, 7)
    }

    private fun button(value: String): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF850D22.toInt())
    }

    private fun checkBox(value: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        text = value
        isChecked = checked
        textSize = 14f
        setTextColor(0xFFE0E0E8.toInt())
        buttonTintList = android.content.res.ColorStateList.valueOf(0xFFD3223C.toInt())
    }

    private fun horizontalRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun weightedParams() = LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
    )

    private fun splitValues(value: String): List<String> =
        value.split(',', '،', ';', '؛', '|').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun buildDraft(title: String = titleField.text.toString()): WolfHorrorKnowledgeLibrary.Draft =
        WolfHorrorKnowledgeLibrary.Draft(
            title = title,
            category = categoryField.text.toString(),
            folder = folderField.text.toString(),
            tags = splitValues(tagsField.text.toString()),
            summary = summaryField.text.toString(),
            sourceType = sourceTypeField.text.toString(),
            sourceLabel = sourceLabelField.text.toString(),
            sourceDate = sourceDateField.text.toString(),
            characters = splitValues(charactersField.text.toString()),
            places = splitValues(placesField.text.toString()),
            events = splitValues(eventsField.text.toString()),
            themes = splitValues(themesField.text.toString()),
            keywords = splitValues(keywordsField.text.toString()),
            includeInStoryReferences = includeReferencesToggle.isChecked
        )

    private fun refreshSuggestions() {
        val categories = WolfHorrorKnowledgeLibrary.categories(libraryRoot)
        categoryField.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))
        categoryFilterField.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf("كل التصنيفات") + categories)
        )
        folderField.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, WolfHorrorKnowledgeLibrary.folders(libraryRoot))
        )
        folderFilterField.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, WolfHorrorKnowledgeLibrary.folders(libraryRoot))
        )
        sourceTypeField.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, WolfHorrorKnowledgeLibrary.SOURCE_TYPES)
        )
    }

    private fun saveEntry() {
        val id = activeEntryId
        val draft = try { buildDraft() } catch (error: Exception) {
            showError(error)
            return
        }
        val content = contentField.text.toString()
        if (id == null && content.isBlank()) {
            status.text = "اكتب نص القصة أو استورد ملف TXT أولًا"
            return
        }
        if (id != null && !activeContentTooLargeToEdit && content.isBlank()) {
            status.text = "لا يمكن حفظ قصة فارغة"
            return
        }
        saveButton.isEnabled = false
        status.text = "جارٍ حفظ القصة وتحديث الفهرس المحلي..."
        worker.execute {
            val result = runCatching {
                if (id == null) {
                    WolfHorrorKnowledgeLibrary.createFromText(libraryRoot, draft, content)
                } else if (activeContentTooLargeToEdit) {
                    WolfHorrorKnowledgeLibrary.SaveResult(
                        WolfHorrorKnowledgeLibrary.updateMetadata(libraryRoot, id, draft),
                        false
                    )
                } else {
                    WolfHorrorKnowledgeLibrary.updateFromText(libraryRoot, id, draft, content)
                }
            }
            runOnUiThread {
                saveButton.isEnabled = true
                result.onSuccess { saved ->
                    if (saved.duplicateOfExisting) {
                        status.text = "المحتوى موجود بالفعل: ${saved.entry.title} — لم تُنشأ نسخة مكررة"
                        Toast.makeText(this, "تم اكتشاف قصة مكررة", Toast.LENGTH_LONG).show()
                    } else {
                        activeEntryId = saved.entry.id
                        status.text = "تم حفظ القصة وتحديث فهرسها المحلي"
                        Toast.makeText(this, "تم حفظ القصة في المكتبة", Toast.LENGTH_SHORT).show()
                    }
                    refreshSuggestions()
                    refreshEntries()
                    if (activeEntryId != null) saveButton.text = "تحديث القصة المحددة"
                }.onFailure { showError(it) }
            }
        }
    }

    private fun clearEditor() {
        activeEntryId = null
        activeContentTooLargeToEdit = false
        titleField.setText("")
        categoryField.setText("قصص المستخدم", false)
        folderField.setText("")
        tagsField.setText("")
        sourceTypeField.setText(WolfHorrorKnowledgeLibrary.SOURCE_USER_SUBMITTED, false)
        sourceLabelField.setText("")
        sourceDateField.setText("")
        summaryField.setText("")
        charactersField.setText("")
        placesField.setText("")
        eventsField.setText("")
        themesField.setText("")
        keywordsField.setText("")
        contentField.isEnabled = true
        contentField.setText("")
        includeReferencesToggle.isChecked = true
        saveButton.text = "حفظ القصة في المكتبة"
        status.text = "جاهز لإضافة قصة جديدة"
    }

    private fun runSearch() {
        lastQuery = searchField.text.toString().trim()
        lastCategoryFilter = categoryFilterField.text.toString().trim()
            .takeIf { it.isNotBlank() && it != "كل التصنيفات" }
        lastFolderFilter = folderFilterField.text.toString().trim().takeIf { it.isNotBlank() }
        status.text = "جارٍ البحث في الفهرس المحلي..."
        worker.execute {
            val results = runCatching {
                WolfHorrorKnowledgeLibrary.search(
                    libraryRoot, lastQuery, lastCategoryFilter, lastFolderFilter
                )
            }
            runOnUiThread {
                results.onSuccess { renderEntries(it) }.onFailure { showError(it) }
            }
        }
    }

    private fun refreshEntries() {
        worker.execute {
            val results = runCatching {
                WolfHorrorKnowledgeLibrary.search(
                    libraryRoot, lastQuery, lastCategoryFilter, lastFolderFilter
                )
            }
            runOnUiThread {
                results.onSuccess { renderEntries(it) }.onFailure { showError(it) }
            }
        }
    }

    private fun renderEntries(entries: List<WolfHorrorKnowledgeLibrary.Entry>) {
        entriesContainer.removeAllViews()
        if (entries.isEmpty()) {
            entriesContainer.addView(label("لا توجد قصص مطابقة. أضف قصة أو استورد ملف TXT."))
            return
        }
        entries.forEach { entry ->
            val details = buildString {
                append(entry.category)
                if (entry.folder.isNotBlank()) append("  •  المجلد: ").append(entry.folder)
                if (entry.tags.isNotEmpty()) append("\nالوسوم: ").append(entry.tags.joinToString("، "))
                append("\nالنوع: ").append(entry.sourceType)
                append("  •  ").append(if (entry.includeInStoryReferences) "مسموح كمصدر" else "مستبعد من المراجع")
                if (entry.summary.isNotBlank()) append("\n").append(entry.summary)
            }
            entriesContainer.addView(label("${entry.title}\n$details").apply {
                setBackgroundColor(0xFF171722.toInt())
                setPadding(12, 12, 12, 12)
            })
            val actions = horizontalRow()
            actions.addView(button("فتح / تعديل").apply {
                setOnClickListener { openEntry(entry.id) }
            }, weightedParams())
            actions.addView(button("النص الأصلي").apply {
                setOnClickListener { showOriginalText(entry.id, entry.title, entry.contentChars) }
            }, weightedParams())
            actions.addView(button("حذف").apply {
                setOnClickListener { confirmDelete(entry.id, entry.title) }
            }, weightedParams())
            entriesContainer.addView(actions)
        }
        status.text = "عدد النتائج: ${entries.size}"
    }

    private fun openEntry(id: String) {
        status.text = "جارٍ فتح بيانات القصة..."
        worker.execute {
            val loaded = runCatching {
                val entry = WolfHorrorKnowledgeLibrary.load(libraryRoot, id)
                    ?: throw IllegalStateException("القصة غير موجودة")
                val preview = WolfHorrorKnowledgeLibrary.readContentPreview(libraryRoot, id, EDITABLE_PREVIEW_CHARS)
                    ?: throw IllegalStateException("تعذر قراءة نص القصة")
                Triple(entry, preview, entry.contentChars > preview.length)
            }
            runOnUiThread {
                loaded.onSuccess { (entry, preview, tooLarge) ->
                    activeEntryId = entry.id
                    activeContentTooLargeToEdit = tooLarge
                    titleField.setText(entry.title)
                    categoryField.setText(entry.category, false)
                    folderField.setText(entry.folder, false)
                    tagsField.setText(entry.tags.joinToString("، "))
                    sourceTypeField.setText(entry.sourceType, false)
                    sourceLabelField.setText(entry.sourceLabel)
                    sourceDateField.setText(entry.sourceDate)
                    summaryField.setText(entry.summary)
                    charactersField.setText(entry.characters.joinToString("، "))
                    placesField.setText(entry.places.joinToString("، "))
                    eventsField.setText(entry.events.joinToString("، "))
                    themesField.setText(entry.themes.joinToString("، "))
                    keywordsField.setText(entry.keywords.joinToString("، "))
                    includeReferencesToggle.isChecked = entry.includeInStoryReferences
                    contentField.setText(preview)
                    contentField.isEnabled = !tooLarge
                    saveButton.text = if (tooLarge) "حفظ بيانات القصة فقط" else "تحديث القصة المحددة"
                    status.text = if (tooLarge) {
                        "تم فتح البيانات الوصفية. النص كبير (${entry.contentChars} حرف)؛ لم يُحمّل كاملًا للمحرر لحماية الذاكرة. يمكنك تعديل التصنيف والوسوم والملخص."
                    } else {
                        "تم فتح القصة. النص الأصلي محفوظ بشكل مستقل."
                    }
                }.onFailure { showError(it) }
            }
        }
    }

    private fun chooseTxtFiles() {
        if (importing) {
            status.text = "هناك عملية استيراد جارية بالفعل"
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_IMPORT_TXT)
    }

    @Deprecated("The document picker result is handled for supported Android versions.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_TXT || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
        }
        data.data?.let { if (uris.isEmpty()) uris.add(it) }
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return

        val baseDraft = buildDraft(title = "قصة مستوردة")
        importing = true
        status.text = "جارٍ استيراد ${uniqueUris.size} ملف TXT..."
        worker.execute {
            var imported = 0
            var duplicates = 0
            val failures = mutableListOf<String>()
            uniqueUris.forEach { uri ->
                runCatching {
                    val fileName = displayName(uri)
                    require(fileName.endsWith(".txt", ignoreCase = true)) {
                        "الملف ليس بامتداد TXT: $fileName"
                    }
                    val stream = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("تعذر فتح الملف: $fileName")
                    val reader = WolfHorrorKnowledgeLibrary.strictUtf8Reader(stream)
                    val draft = baseDraft.copy(
                        title = fileName.substring(0, fileName.length - 4).ifBlank { "قصة مستوردة" },
                        sourceLabel = baseDraft.sourceLabel.ifBlank { "ملف مستورد من الهاتف" }
                    )
                    WolfHorrorKnowledgeLibrary.create(libraryRoot, draft, reader, fileName)
                }.onSuccess { result ->
                    if (result.duplicateOfExisting) duplicates++ else imported++
                }.onFailure { error ->
                    failures.add(error.message ?: "خطأ أثناء استيراد ملف")
                }
            }
            runOnUiThread {
                importing = false
                val message = "تم استيراد $imported ملف، والمكرر $duplicates، والفشل ${failures.size}"
                status.text = message + if (failures.isNotEmpty()) "\n" + failures.take(3).joinToString("\n") else ""
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                refreshSuggestions()
                refreshEntries()
            }
        }
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.txt"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        } finally {
            cursor?.close()
        }
        return name
    }

    private fun showOriginalText(
        id: String = activeEntryId ?: "",
        title: String = titleField.text.toString(),
        totalChars: Int? = null
    ) {
        if (id.isBlank()) {
            status.text = "افتح قصة من القائمة أولًا"
            return
        }
        worker.execute {
            val preview = WolfHorrorKnowledgeLibrary.readOriginalPreview(libraryRoot, id, ORIGINAL_PREVIEW_CHARS)
            runOnUiThread {
                if (preview == null) {
                    showError(IllegalStateException("تعذر قراءة النص الأصلي"))
                    return@runOnUiThread
                }
                val textView = TextView(this).apply {
                    text = preview + if (totalChars != null && totalChars > preview.length) {
                        "\n\n[عرض مختصر — النص الأصلي الكامل محفوظ داخل المكتبة]"
                    } else ""
                    textSize = 15f
                    setTextColor(0xFFE0E0E8.toInt())
                    setPadding(18, 14, 18, 14)
                }
                AlertDialog.Builder(this)
                    .setTitle("النص الأصلي: $title")
                    .setView(ScrollView(this).apply { addView(textView) })
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
        }
    }

    private fun confirmDelete(id: String, title: String) {
        AlertDialog.Builder(this)
            .setTitle("حذف القصة")
            .setMessage("سيتم حذف النص وبياناته وفهرسه المحلي من المكتبة. هذا لا يحذف ملفات الهاتف الأصلية التي استوردت منها.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                worker.execute {
                    val deleted = WolfHorrorKnowledgeLibrary.delete(libraryRoot, id)
                    runOnUiThread {
                        if (activeEntryId == id) clearEditor()
                        status.text = if (deleted) "تم حذف: $title" else "تعذر حذف القصة"
                        refreshSuggestions()
                        refreshEntries()
                    }
                }
            }
            .show()
    }

    private fun showError(error: Throwable) {
        val message = error.message ?: "حدث خطأ غير معروف"
        status.text = "تعذر إكمال العملية: $message"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_IMPORT_TXT = 861
        private const val EDITABLE_PREVIEW_CHARS = 250_000
        private const val ORIGINAL_PREVIEW_CHARS = 40_000
    }
}
