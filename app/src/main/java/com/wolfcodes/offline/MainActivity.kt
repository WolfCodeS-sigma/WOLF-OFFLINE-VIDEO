package com.wolfcodes.offline

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var titleField: EditText
    private lateinit var storyField: EditText
    private lateinit var durationField: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var projectListContainer: LinearLayout
    private lateinit var chapterStatusContainer: LinearLayout
    private lateinit var introToggle: CheckBox
    private lateinit var watermarkToggle: CheckBox
    private lateinit var soundEffectsToggle: CheckBox

    private var workingProject: WolfProject? = null
    private var voiceRecorder: WolfVoiceRecorder? = null
    private var voicePlayer: MediaPlayer? = null
    private var teleprompterDialog: Dialog? = null
    private var teleprompterProgress: TextView? = null
    private var voiceUri: String? = null
    private var selectedAspectRatio: String = "9:16"
    private val selectedImageUris = mutableListOf<String>()
    private var pendingImageSlot: Int? = null
    private var producing = false
    private var voiceMergePending = false
    private var voiceRecoveryPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        refreshProjectList()
        refreshChapterStatus()
        refreshVoiceStatus()
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFF07070D.toInt())
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "🐺 WOLF CODE S\nWOLF OFFLINE VIDEO"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFFD3223C.toInt())
            setPadding(0, 14, 0, 24)
        })

        root.addView(label("اسم المشروع"))
        titleField = field("اسم الفيلم")
        root.addView(titleField)
        root.addView(label("القصة — عدّل النص ثم اعتمده قبل التسجيل"))
        storyField = field("اكتب أو الصق قصة الرعب هنا").apply {
            minLines = 8
            gravity = Gravity.TOP
        }
        root.addView(storyField)
        root.addView(button("اعتماد نص القصة النهائي").apply {
            setOnClickListener { approveStory() }
        })

        root.addView(label("مقاس الفيديو"))
        val ratioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("9:16", "16:9", "1:1", "4:5").forEach { ratio ->
            val choice = button(ratio)
            ratioRow.addView(choice, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            choice.setOnClickListener {
                selectedAspectRatio = ratio
                status.text = "تم اختيار مقاس $ratio"
            }
        }
        root.addView(ratioRow)

        root.addView(label("مدة الفيلم — مدد جاهزة من 5 إلى 30 دقيقة، أو مدة مخصصة بالثواني"))
        durationField = field("المدة بالثواني — مثال: 300").apply { setText("300") }
        root.addView(durationField)
        WolfFilmProductionCoordinator.durationPresetsMinutes.chunked(3).forEach { minutesRow ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            minutesRow.forEach { minutes ->
                val choice = button("$minutes دقائق")
                row.addView(
                    choice,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                choice.setOnClickListener {
                    durationField.setText((minutes * 60).toString())
                    status.text = "تم اختيار مدة فيلم $minutes دقائق"
                }
            }
            root.addView(row)
        }

        introToggle = checkBox("إضافة مقدمة بصمة الذئب السينمائية", true)
        watermarkToggle = checkBox("إضافة العلامة المائية WOLF CODE S", true)
        soundEffectsToggle = checkBox("توليد المؤثرات الصوتية الإجرائية", true)
        root.addView(introToggle)
        root.addView(watermarkToggle)
        root.addView(soundEffectsToggle)

        root.addView(button("حفظ المشروع").apply { setOnClickListener { saveProject() } })
        root.addView(button("فتح WOLF OFFLINE STORY AI").apply {
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, WolfOfflineStoryAiActivity::class.java))
            }
        })
        root.addView(button("فتح WOLF HORROR KNOWLEDGE LIBRARY").apply {
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, WolfHorrorKnowledgeLibraryActivity::class.java))
            }
        })
        root.addView(button("إضافة القصة الحالية إلى المكتبة").apply {
            setOnClickListener { saveCurrentStoryToKnowledgeLibrary() }
        })
        root.addView(label("يمكن إنشاء المشاهد تلقائيًا من نص القصة دون صور مرجعية أو اتصال بالإنترنت؛ الصور اختيارية فقط."))
        root.addView(button("اختيار صور متعددة (اختياري)").apply {
            setOnClickListener { chooseMultipleImages() }
        })
        root.addView(button("تعيين صورة إلى لقطة محددة من 36 لقطة").apply {
            setOnClickListener { chooseImageSlot() }
        })

        root.addView(label("التعليق الصوتي"))
        root.addView(label("يمكنك تسجيل الراوي على أجزاء؛ إيقاف الجزء يحفظه، والضغط مجددًا يستكمل من الجزء التالي."))
        root.addView(button("تسجيل جزء جديد / استكمال التعليق الصوتي").apply {
            setOnClickListener { startVoiceRecording() }
        })
        root.addView(button("إيقاف التسجيل").apply {
            setOnClickListener { voiceRecorder?.stopByUser() }
        })
        root.addView(button("معاينة التسجيل الصوتي").apply {
            setOnClickListener { playVoicePreview() }
        })
        root.addView(button("اعتماد التسجيل بعد الاستماع إليه").apply {
            setOnClickListener { approveVoiceTake() }
        })
        root.addView(button("إيقاف معاينة الصوت").apply {
            setOnClickListener { stopVoicePreview() }
        })
        root.addView(button("إعادة التسجيل وإلغاء الاعتماد الحالي").apply {
            setOnClickListener { requestVoiceRepeat() }
        })

        root.addView(button("إنتاج فيديو أوفلاين / استئناف الفصل").apply {
            setOnClickListener { produce() }
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress)
        status = label("جاهز للعمل محليًا دون اتصال بالإنترنت").apply { gravity = Gravity.CENTER }
        root.addView(status)

        root.addView(label("حالة فصول الفيلم والاستئناف"))
        chapterStatusContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(chapterStatusContainer)

        root.addView(label("المشاريع المحفوظة"))
        projectListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(projectListContainer)
        setContentView(scroll)
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0xFF888888.toInt())
        setPadding(16, 12, 16, 12)
        setBackgroundColor(0xFF20202A.toInt())
    }

    private fun label(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(0xFFE0E0E8.toInt())
        setPadding(4, 14, 4, 8)
    }

    private fun button(value: String): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 16f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF850D22.toInt())
    }

    private fun checkBox(value: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        text = value
        isChecked = checked
        textSize = 15f
        setTextColor(0xFFE0E0E8.toInt())
        buttonTintList = android.content.res.ColorStateList.valueOf(0xFFD3223C.toInt())
    }

    private fun currentProject(): WolfProject {
        val title = titleField.text.toString().trim()
        val story = storyField.text.toString().trim()
        val duration = durationField.text.toString().toIntOrNull()
            ?: throw IllegalArgumentException("اكتب مدة صحيحة بالثواني")
        require(title.isNotBlank()) { "اكتب اسم الفيلم" }
        require(story.length >= 20) { "اكتب قصة أطول قليلًا حتى يمكن تقسيمها إلى مشاهد" }
        require(story.length <= 60000) { "القصة أطول من الحد المدعوم (60,000 حرف)" }
        require(duration in 5..1800) { "المدة المخصصة يجب أن تكون بين 5 ثوانٍ و30 دقيقة" }

        val existing = workingProject
        val storyChanged = existing != null && existing.story != story
        if (storyChanged) {
            // Invalidate unconditionally: project.story and session.finalStory can drift
            // independently after interrupted saves or edits made through another screen.
            WolfStoryVoiceStore.invalidateForStoryChange(this, existing!!.id, story)
            voiceUri = null
            stopVoicePreview()
        }

        val base = if (existing != null) {
            existing.copy(
                title = title,
                story = story,
                durationSeconds = duration,
                audioUri = voiceUri,
                aspectRatio = selectedAspectRatio,
                imageUris = selectedImageUris.toList(),
                includeIntro = introToggle.isChecked,
                includeWatermark = watermarkToggle.isChecked,
                soundEffectsEnabled = soundEffectsToggle.isChecked
            )
        } else {
            WolfProjectStore.newProject(
                story = story,
                sourceUrl = "",
                aspectRatio = selectedAspectRatio,
                durationSeconds = duration,
                visualStyle = "رعب سينمائي مظلم",
                horrorType = "رعب نفسي وخارق للطبيعة",
                soundEffectsEnabled = soundEffectsToggle.isChecked,
                imageUris = selectedImageUris.toList(),
                audioUri = voiceUri,
                includeIntro = introToggle.isChecked,
                includeWatermark = watermarkToggle.isChecked
            ).copy(title = title)
        }
        workingProject = base
        return base
    }

    private fun chooseMultipleImages() {
        if (producing || voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "انتظر انتهاء العملية الحالية قبل تعديل صور المشروع"
            return
        }
        pendingImageSlot = null
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 742)
    }

    private fun chooseImageSlot() {
        if (producing || voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "انتظر انتهاء العملية الحالية قبل تعديل صور المشروع"
            return
        }
        val labels = Array(WolfSceneAssetStore.MAX_IMAGE_SLOTS) { index ->
            val scene = index / WolfSceneAssetStore.SHOTS_PER_SCENE + 1
            val shot = index % WolfSceneAssetStore.SHOTS_PER_SCENE + 1
            "المشهد $scene — اللقطة $shot"
        }
        AlertDialog.Builder(this)
            .setTitle("اختر خانة الصورة")
            .setItems(labels) { _, which ->
                pendingImageSlot = which
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, 742)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    @Deprecated("The document picker result is handled for Android versions supported by this app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 742 || resultCode != RESULT_OK || data == null) return
        if (producing || voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "تم تجاهل اختيار الصور لأن المشروع مشغول بعملية أخرى"
            return
        }
        val slot = pendingImageSlot
        pendingImageSlot = null
        val picked = mutableListOf<Uri>()
        if (slot != null) {
            data.data?.let(picked::add)
        } else {
            data.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(picked::add)
            }
            data.data?.let { if (picked.isEmpty()) picked.add(it) }
        }
        picked.distinct().forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (slot != null) {
                    val project = currentProject()
                    WolfSceneAssetStore.assignShot(
                        this, project.id,
                        slot / WolfSceneAssetStore.SHOTS_PER_SCENE,
                        slot % WolfSceneAssetStore.SHOTS_PER_SCENE,
                        uri.toString()
                    )
                    WolfProjectStore.save(this, project)
                    status.text = "تم تعيين الصورة إلى الخانة " + (slot + 1) + " من 36"
                } else if (selectedImageUris.size < WolfProjectStore.MAX_IMAGES &&
                    uri.toString() !in selectedImageUris
                ) {
                    selectedImageUris.add(uri.toString())
                    workingProject = workingProject?.copy(imageUris = selectedImageUris.toList())
                    status.text = "تم اختيار " + selectedImageUris.size + " صورة إضافية"
                }
            }.onFailure { showError(it) }
        }
        refreshProjectList()
    }

    private fun saveCurrentStoryToKnowledgeLibrary() {
        val story = storyField.text.toString().trim()
        if (story.length < 20) {
            status.text = "اكتب القصة أولًا قبل إضافتها إلى المكتبة"
            return
        }
        val title = titleField.text.toString().trim().ifBlank {
            story.lineSequence().firstOrNull { it.isNotBlank() }?.take(48) ?: "قصة مستخدم"
        }
        status.text = "جارٍ حفظ نسخة من القصة في مكتبة المعرفة..."
        worker.execute {
            val result = runCatching {
                WolfHorrorKnowledgeLibrary.createFromText(
                    File(filesDir, "wolf_horror_knowledge_library"),
                    WolfHorrorKnowledgeLibrary.Draft(
                        title = title,
                        category = "قصص المستخدم",
                        folder = "قصصي المضافة",
                        tags = listOf("من محرر الفيلم"),
                        sourceType = WolfHorrorKnowledgeLibrary.SOURCE_USER_SUBMITTED,
                        sourceLabel = "أضيفت من محرر WOLF OFFLINE VIDEO",
                        includeInStoryReferences = true
                    ),
                    story
                )
            }
            runOnUiThread {
                result.onSuccess { saved ->
                    status.text = if (saved.duplicateOfExisting) {
                        "هذه القصة موجودة بالفعل في المكتبة: " + saved.entry.title
                    } else {
                        "تم حفظ نسخة مستقلة من القصة داخل WOLF HORROR KNOWLEDGE LIBRARY"
                    }
                    Toast.makeText(
                        this,
                        if (saved.duplicateOfExisting) "القصة موجودة بالفعل" else "تمت إضافة القصة إلى المكتبة",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { showError(it) }
            }
        }
    }

    private fun saveProject() {
        if (voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "أوقف التسجيل وانتظر اكتمال حفظ مقاطعه قبل حفظ تعديلات المشروع"
            return
        }
        try {
            val project = currentProject()
            WolfProjectStore.save(this, project)
            workingProject = project
            status.text = "تم حفظ المشروع على الهاتف"
            Toast.makeText(this, "تم حفظ المشروع", Toast.LENGTH_SHORT).show()
            refreshProjectList()
            refreshVoiceStatus()
            refreshChapterStatus()
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun approveStory() {
        if (voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "أوقف التسجيل وانتظر اكتمال حفظ مقاطعه قبل اعتماد نص جديد"
            return
        }
        try {
            val project = currentProject()
            WolfProjectStore.save(this, project)
            WolfStoryVoiceStore.initialize(this, project.id, project.story)
            val approved = WolfStoryVoiceStore.approveScript(this, project.id, project.story)
            voiceUri = if (approved.voiceApproved) project.audioUri else null
            workingProject = project.copy(audioUri = voiceUri)
            WolfProjectStore.save(this, workingProject!!)
            status.text = "تم اعتماد النص النهائي للقصة"
            refreshVoiceStatus()
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun startVoiceRecording() {
        if (producing || voiceMergePending || voiceRecoveryPending) {
            status.text = "انتظر انتهاء العملية الحالية قبل بدء مقطع صوتي جديد"
            return
        }
        if (voiceRecorder?.isRecording == true) {
            status.text = "التسجيل يعمل بالفعل؛ أوقف المقطع الحالي أولًا"
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 741)
            return
        }
        val project = try {
            currentProject().also { WolfProjectStore.save(this, it) }
        } catch (error: Exception) {
            showError(error)
            return
        }
        val session = WolfStoryVoiceStore.initialize(this, project.id, project.story)
        if (!session.scriptApproved || session.finalStory != project.story) {
            status.text = "اعتمد نص القصة النهائي أولًا قبل التسجيل"
            return
        }
        stopVoicePreview()
        // Copying a legacy take and validating AAC parts can be slow on low-end phones.
        // Keep that file work off the UI thread and block conflicting project actions.
        voiceRecoveryPending = true
        storyField.isEnabled = false
        status.text = "جارٍ تجهيز مساحة تسجيل الراوي..."
        worker.execute {
            val targetResult = runCatching {
                val previousSession = WolfStoryVoiceStore.read(this, project.id)
                WolfVoiceSegmentStore.importExistingTake(
                    this, project.id,
                    previousSession?.voiceFilePath?.let(::File),
                    previousSession?.audioDurationMs ?: 0L
                )
                WolfStoryVoiceStore.beginAdditionalSegment(this, project.id)
                WolfVoiceSegmentStore.nextSegmentTarget(this, project.id)
            }
            runOnUiThread {
                voiceRecoveryPending = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                storyField.isEnabled = true
                targetResult.onSuccess { destination ->
                    try {
                        voiceUri = null
                        workingProject = project.copy(audioUri = null)
                        WolfProjectStore.save(this@MainActivity, workingProject!!)
                        startVoiceRecorder(project, destination)
                    } catch (error: Exception) {
                        storyField.isEnabled = true
                        showError(error)
                    }
                }.onFailure {
                    storyField.isEnabled = true
                    showError(it)
                    refreshVoiceStatus()
                }
            }
        }
    }

    private fun startVoiceRecorder(project: WolfProject, destination: File) {
        storyField.isEnabled = false
        voiceRecorder = WolfVoiceRecorder(this, object : WolfVoiceRecorder.Listener {
            @Suppress("UNUSED_PARAMETER")
            override fun onProgress(elapsedMs: Long, speechDetected: Boolean) {
                runOnUiThread {
                    val message = "التسجيل مستمر — " + (elapsedMs / 1000) + " ثانية"
                    status.text = message
                    teleprompterProgress?.text = message
                }
            }

            override fun onSaved(file: File, durationMs: Long, automaticStop: Boolean) {
                voiceMergePending = true
                runOnUiThread {
                    dismissTeleprompter()
                    storyField.isEnabled = false
                    status.text = "تم حفظ المقطع؛ جارٍ دمجه مع المقاطع السابقة..."
                }
                worker.execute {
                    val mergedResult = runCatching {
                        val merged = WolfVoiceSegmentStore.appendAndMerge(
                            this@MainActivity, project.id, file, durationMs
                        )
                        val savedTake = WolfStoryVoiceStore.saveRecording(
                            this@MainActivity, project.id, merged.file, merged.durationMs
                        )
                        merged to savedTake
                    }
                    runOnUiThread {
                        voiceMergePending = false
                        storyField.isEnabled = true
                        mergedResult.onSuccess { (merged, savedTake) ->
                            voiceUri = Uri.fromFile(merged.file).toString()
                            val latestProject = runCatching { currentProject() }.getOrElse { project }
                            workingProject = latestProject.copy(audioUri = voiceUri)
                            WolfProjectStore.save(this@MainActivity, workingProject!!)
                            status.text = "تم حفظ الجزء ${merged.segmentCount} ودمجه مع التسجيل السابق — " +
                                (savedTake.audioDurationMs / 1000) + " ثانية إجمالًا. استمع إليه ثم اعتمده."
                            refreshVoiceStatus()
                            refreshProjectList()
                        }.onFailure {
                            showError(it)
                            status.text = "حُفظ المقطع محليًا؛ تعذر دمجه الآن. افتح المشروع لاستعادة المقاطع أو أعد المحاولة."
                        }
                    }
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    dismissTeleprompter()
                    storyField.isEnabled = true
                    showError(IllegalStateException(message))
                    refreshVoiceStatus()
                }
            }
        })
        if (voiceRecorder?.start(destination) != true) {
            storyField.isEnabled = true
            status.text = "تعذر بدء التسجيل الصوتي"
        } else {
            status.text = "بدأ تسجيل الراوي"
            showTeleprompter(project.story)
        }
    }

    /**
     * Shows the complete approved story while the microphone records.
     * Scrolling is deliberately manual: no speech recognition, auto-scroll, or
     * word highlighting is claimed by this fallback mode.
     */
    private fun showTeleprompter(story: String) {
        dismissTeleprompter()
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + 0.5f).toInt()

        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(0xFF07070D.toInt())
        }

        root.addView(TextView(this).apply {
            text = "اقرأ القصة أثناء التسجيل"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(0xFFD3223C.toInt())
            setPadding(0, dp(8), 0, dp(10))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(TextView(this).apply {
            text = "مرّر النص يدويًا أثناء القراءة. لا يوجد تمرير تلقائي أو تلوين للكلمات."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8B8C2.toInt())
            setPadding(dp(4), 0, dp(4), dp(12))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val scriptScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val scriptText = TextView(this).apply {
            text = story
            textSize = 23f
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            gravity = Gravity.START
            setTextColor(0xFFF3F3F6.toInt())
            setLineSpacing(dp(9).toFloat(), 1.18f)
            setPadding(dp(8), dp(22), dp(8), dp(30))
        }
        scriptScroll.addView(
            scriptText,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scriptScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        teleprompterProgress = TextView(this).apply {
            text = "التسجيل مستمر — 0 ثانية"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8B8C2.toInt())
            setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(
            teleprompterProgress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(Button(this).apply {
            text = "إيقاف التسجيل وحفظ الجزء"
            isAllCaps = false
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF850D22.toInt())
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                val activeRecorder = voiceRecorder
                if (activeRecorder?.isRecording == true) {
                    isEnabled = false
                    text = "جارٍ إيقاف التسجيل وحفظه..."
                    teleprompterProgress?.text = "انتهت القراءة؛ جارٍ حفظ التسجيل..."
                    activeRecorder.stopByUser()
                } else {
                    dismissTeleprompter()
                }
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (teleprompterDialog === dialog) {
                teleprompterDialog = null
                teleprompterProgress = null
            }
        }
        teleprompterDialog = dialog
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF07070D.toInt()))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun dismissTeleprompter() {
        val activeDialog = teleprompterDialog
        teleprompterDialog = null
        teleprompterProgress = null
        if (activeDialog?.isShowing == true) activeDialog.dismiss()
    }

    private fun approveVoiceTake() {
        if (voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "أوقف التسجيل وانتظر اكتمال دمج المقاطع قبل الاعتماد"
            return
        }
        val project = try {
            currentProject().also { WolfProjectStore.save(this, it) }
        } catch (error: Exception) {
            showError(error)
            return
        }
        val session = WolfStoryVoiceStore.read(this, project.id)
        if (session?.scriptApproved != true || session.finalStory != project.story) {
            status.text = "اعتمد نص القصة النهائي أولًا"
            return
        }
        val approved = WolfStoryVoiceStore.approveRecording(this, project.id)
        if (approved == null) {
            status.text = "لا يوجد تسجيل صالح للاعتماد. استمع إليه أو أعد تسجيله."
            return
        }
        val path = approved.voiceFilePath ?: run {
            status.text = "ملف التسجيل غير موجود"
            return
        }
        voiceUri = Uri.fromFile(File(path)).toString()
        workingProject = project.copy(audioUri = voiceUri)
        WolfProjectStore.save(this, workingProject!!)
        status.text = "تم اعتماد التعليق الصوتي — " + (approved.audioDurationMs / 1000) + " ثانية"
        refreshVoiceStatus()
        refreshProjectList()
    }

    private fun requestVoiceRepeat() {
        if (voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "أوقف التسجيل وانتظر اكتمال دمج المقاطع أولًا"
            return
        }
        val project = try {
            currentProject().also { WolfProjectStore.save(this, it) }
        } catch (error: Exception) {
            showError(error)
            return
        }
        stopVoicePreview()
        val updated = WolfStoryVoiceStore.requestRepeat(this, project.id)
        if (updated == null) {
            status.text = "لا يوجد تسجيل محفوظ لإعادته"
            return
        }
        voiceUri = null
        workingProject = project.copy(audioUri = null)
        WolfProjectStore.save(this, workingProject!!)
        status.text = "تم حذف مقاطع التسجيل السابقة وإلغاء اعتمادها. يمكنك البدء من جديد."
        refreshVoiceStatus()
        refreshProjectList()
    }

    private fun playVoicePreview() {
        if (voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "انتظر اكتمال حفظ أو استعادة مقاطع الراوي قبل المعاينة"
            return
        }
        val project = try {
            currentProject().also { WolfProjectStore.save(this, it) }
        } catch (error: Exception) {
            showError(error)
            return
        }
        val session = WolfStoryVoiceStore.read(this, project.id)
        val path = session?.voiceFilePath
        if (path.isNullOrBlank() || !File(path).isFile) {
            status.text = "لا يوجد تسجيل صوتي صالح للمعاينة"
            return
        }
        stopVoicePreview()
        runCatching {
            voicePlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener {
                    it.start()
                    status.text = "يتم تشغيل معاينة التعليق الصوتي"
                }
                setOnCompletionListener {
                    stopVoicePreview()
                    status.text = "انتهت معاينة التسجيل"
                }
                setOnErrorListener { _, what, extra ->
                    stopVoicePreview()
                    status.text = "تعذر تشغيل التسجيل الصوتي ($what / $extra)"
                    true
                }
                prepareAsync()
            }
        }.onFailure { showError(it) }
    }

    private fun stopVoicePreview() {
        val player = voicePlayer ?: return
        voicePlayer = null
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    private fun refreshVoiceStatus() {
        val project = workingProject ?: return
        val session = WolfStoryVoiceStore.read(this, project.id)
        val parts = WolfVoiceSegmentStore.segmentCount(this, project.id)
        val message = when {
            session == null -> if (parts > 0) "توجد $parts أجزاء محفوظة تنتظر الاستعادة" else "جلسة الراوي لم تبدأ بعد"
            !session.scriptApproved -> "نص القصة غير معتمد"
            session.voiceApproved && (session.voiceFilePath.isNullOrBlank() ||
                !File(session.voiceFilePath!!).isFile) ->
                "ملف التسجيل المعتمد غير موجود؛ أعد تسجيل الراوي قبل الإنتاج"
            session.voiceApproved -> "التسجيل معتمد — " + (session.audioDurationMs / 1000) + " ثانية"
            !session.voiceFilePath.isNullOrBlank() && !File(session.voiceFilePath!!).isFile ->
                "ملف الدمج غير متاح؛ افتح المشروع لاستعادة الأجزاء أو أعد التسجيل"
            session.voiceFilePath != null -> "يوجد تسجيل من $parts أجزاء يحتاج إلى المراجعة والاعتماد"
            parts > 0 -> "توجد $parts أجزاء محفوظة — افتح المشروع لاستعادتها"
            else -> "النص معتمد — لم يتم تسجيل الراوي بعد"
        }
        status.text = message
    }

    private fun refreshProjectList() {
        if (!::projectListContainer.isInitialized) return
        projectListContainer.removeAllViews()
        val projects = WolfProjectStore.list(this).take(30)
        if (projects.isEmpty()) {
            projectListContainer.addView(label("لا توجد مشاريع محفوظة بعد"))
            return
        }
        projects.forEach { item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(button(item.title + " — " + item.durationSeconds + " ثانية").apply {
                setOnClickListener { openProject(item) }
            })
            row.addView(button("حذف المشروع").apply {
                setOnClickListener { confirmDeleteProject(item) }
            })
            projectListContainer.addView(row)
        }
    }

    private fun openProject(item: WolfProject) {
        if (producing || voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) return
        stopVoicePreview()
        workingProject = item
        titleField.setText(item.title)
        storyField.setText(item.story)
        durationField.setText(item.durationSeconds.toString())
        voiceUri = item.audioUri
        val persistedVoice = WolfStoryVoiceStore.read(this, item.id)
        val approvedVoicePath = persistedVoice?.voiceFilePath
        if (persistedVoice?.voiceApproved == true &&
            persistedVoice.scriptApproved &&
            persistedVoice.finalStory == item.story &&
            persistedVoice.recordingStoryHash == WolfStoryVoiceStateMachine.storyFingerprint(item.story) &&
            !approvedVoicePath.isNullOrBlank() &&
            File(approvedVoicePath).isFile
        ) {
            voiceUri = Uri.fromFile(File(approvedVoicePath)).toString()
            if (item.audioUri != voiceUri) {
                workingProject = item.copy(audioUri = voiceUri)
                WolfProjectStore.save(this, workingProject!!)
            }
        }
        selectedImageUris.clear()
        selectedImageUris.addAll(item.imageUris)
        selectedAspectRatio = item.aspectRatio.substringBefore(" ")
        introToggle.isChecked = item.includeIntro
        watermarkToggle.isChecked = item.includeWatermark
        soundEffectsToggle.isChecked = item.soundEffectsEnabled
        status.text = "تم فتح المشروع: " + item.title
        refreshVoiceStatus()
        refreshChapterStatus()
        voiceRecoveryPending = true
        status.text = "جارٍ التحقق من مقاطع الراوي واستعادة التسجيل المحفوظ..."
        worker.execute {
            val recovered = runCatching { WolfVoiceSegmentStore.recover(this, item.id) }
            runOnUiThread {
                voiceRecoveryPending = false
                recovered.onSuccess { merged ->
                    if (merged != null && workingProject?.id == item.id) {
                        val existing = WolfStoryVoiceStore.read(this, item.id)
                        val approvedStillMatches =
                            existing?.voiceApproved == true &&
                                existing.scriptApproved &&
                                existing.finalStory == item.story &&
                                existing.recordingStoryHash ==
                                    WolfStoryVoiceStateMachine.storyFingerprint(item.story) &&
                                existing.voiceFilePath == merged.file.absolutePath
                        if (approvedStillMatches) {
                            // The combined file may have been rebuilt after an interruption;
                            // keep its approval but restore the project URI.
                            voiceUri = Uri.fromFile(merged.file).toString()
                            workingProject = item.copy(audioUri = voiceUri)
                            WolfProjectStore.save(this, workingProject!!)
                            refreshVoiceStatus()
                            refreshProjectList()
                        } else {
                            val restored = WolfStoryVoiceStore.restoreMergedSegments(
                                this, item.id, merged.file, merged.durationMs
                            )
                            if (restored != null) {
                                voiceUri = Uri.fromFile(merged.file).toString()
                                workingProject = item.copy(audioUri = voiceUri)
                                WolfProjectStore.save(this, workingProject!!)
                                status.text = "تم استعادة ${merged.segmentCount} مقطعًا محفوظًا للراوي؛ راجع التسجيل واعتمده."
                                refreshVoiceStatus()
                                refreshProjectList()
                            } else {
                                refreshVoiceStatus()
                            }
                        }
                    } else if (merged == null && workingProject?.id == item.id) {
                        refreshVoiceStatus()
                    }
                }.onFailure { status.text = "تعذر استعادة مقاطع الراوي: " + (it.message ?: "خطأ غير معروف") }
            }
        }
    }

    private fun confirmDeleteProject(item: WolfProject) {
        AlertDialog.Builder(this)
            .setTitle("حذف المشروع")
            .setMessage("هل تريد حذف المشروع «" + item.title + "» وملفاته المحلية المرتبطة به؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ -> deleteProject(item) }
            .show()
    }

    private fun deleteProject(item: WolfProject) {
        if (producing || voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "أوقف التسجيل أو انتظر انتهاء العملية الحالية قبل حذف المشروع"
            return
        }
        val deleted = WolfProjectStore.delete(this, item.id)
        if (!deleted) {
            status.text = "تعذر حذف المشروع أو أنه غير موجود"
            return
        }
        WolfStoryVoiceStore.clearProject(this, item.id)
        WolfSceneAssetStore.clearProject(this, item.id)
        WolfLongFilmCheckpointStore.clear(this, item.id)
        if (workingProject?.id == item.id) {
            workingProject = null
            voiceUri = null
            selectedImageUris.clear()
            titleField.setText("")
            storyField.setText("")
            durationField.setText("60")
            introToggle.isChecked = true
            watermarkToggle.isChecked = true
            soundEffectsToggle.isChecked = true
            stopVoicePreview()
            chapterStatusContainer.removeAllViews()
            chapterStatusContainer.addView(label("لا يوجد مشروع محدد"))
            status.text = "تم حذف المشروع"
        }
        refreshProjectList()
    }

    private fun refreshChapterStatus() {
        if (!::chapterStatusContainer.isInitialized) return
        chapterStatusContainer.removeAllViews()
        val project = workingProject
        if (project == null) {
            chapterStatusContainer.addView(label("افتح مشروعًا لعرض حالة فصوله"))
            return
        }
        val session = WolfLongFilmCheckpointStore.load(this, project.id)
        if (session == null) {
            chapterStatusContainer.addView(label("لا توجد فصول محفوظة للاستئناف حاليًا"))
            return
        }
        session.chapters.forEach { checkpoint ->
            val state = when (checkpoint.status) {
                WolfLongFilmEngine.ChapterStatus.PENDING -> "في الانتظار"
                WolfLongFilmEngine.ChapterStatus.RUNNING -> "توقف أثناء التنفيذ — قابل للاستئناف"
                WolfLongFilmEngine.ChapterStatus.COMPLETED -> "مكتمل"
                WolfLongFilmEngine.ChapterStatus.RETRYABLE_FAILURE -> "تعثر — يمكن إعادة المحاولة"
            }
            chapterStatusContainer.addView(label(
                "الفصل " + checkpoint.plan.number + ": " + state +
                    " — المحاولة " + checkpoint.attempt +
                    (checkpoint.lastError?.let { "\nالخطأ: $it" } ?: "")
            ))
        }
        chapterStatusContainer.addView(button("استئناف / إعادة محاولة الفصول غير المكتملة").apply {
            setOnClickListener { produce() }
            isEnabled = !producing && session.chapters.any {
                it.status != WolfLongFilmEngine.ChapterStatus.COMPLETED
            }
        })
    }

    private fun produce() {
        if (voiceMergePending || voiceRecoveryPending || voiceRecorder?.isRecording == true) {
            status.text = "أوقف تسجيل الراوي وانتظر اكتمال حفظ المقاطع قبل إنتاج الفيلم"
            return
        }
        if (producing) {
            status.text = "يوجد إنتاج قيد التشغيل بالفعل"
            return
        }
        val project = try {
            currentProject().also { WolfProjectStore.save(this, it) }
        } catch (error: Exception) {
            showError(error)
            return
        }
        producing = true
        progress.visibility = View.VISIBLE
        progress.progress = 0
        status.text = "جارٍ تجهيز محرك الإنتاج المحلي"
        refreshChapterStatus()
        worker.execute {
            try {
                WolfProjectStore.updateStatus(this, project.id, "قيد الإنتاج", lastError = null)
                    ?: throw IllegalStateException("تعذر تحديث حالة المشروع قبل بدء التصدير")
                val voiceSession = WolfStoryVoiceStore.read(this, project.id)
                val approvedVoice = project.audioUri?.let { storedAudio ->
                    val storedPath = runCatching { Uri.parse(storedAudio).path }.getOrNull()
                    val approvedPath = voiceSession?.voiceFilePath
                    voiceSession?.voiceApproved == true &&
                        voiceSession.scriptApproved &&
                        voiceSession.finalStory == project.story &&
                        voiceSession.recordingStoryHash ==
                            WolfStoryVoiceStateMachine.storyFingerprint(project.story) &&
                        !storedPath.isNullOrBlank() &&
                        !approvedPath.isNullOrBlank() &&
                        runCatching {
                            File(storedPath).canonicalFile == File(approvedPath).canonicalFile &&
                                File(approvedPath).isFile
                        }.getOrDefault(false)
                } ?: false
                // The selected project duration is the baseline; the production timeline
                // extends it only when intro + the complete approved narration needs more time.
                val shotAssignments = WolfSceneAssetStore.shotAssignments(this, project.id)
                    .mapNotNull { (slot, value) ->
                        runCatching { slot to Uri.parse(value) }.getOrNull()
                    }.toMap()
                val result = OfflineProductionEngine.render(
                    context = this,
                    story = project.story,
                    seconds = project.durationSeconds,
                    imageUris = project.imageUris.mapNotNull { value ->
                        runCatching { Uri.parse(value) }.getOrNull()
                    },
                    audioUri = if (approvedVoice) Uri.parse(project.audioUri!!) else null,
                    aspectRatio = project.aspectRatio,
                    visualStyle = project.visualStyle,
                    horrorType = project.horrorType,
                    generateSoundEffects = project.soundEffectsEnabled,
                    includeWatermark = project.includeWatermark,
                    includeIntro = project.includeIntro,
                    sceneImageAssignments = shotAssignments,
                    projectId = project.id,
                    audioDurationIsAuthoritative = approvedVoice,
                    audioDurationMs = if (approvedVoice) voiceSession?.audioDurationMs else null,
                    onSceneProgress = { current, total ->
                        runOnUiThread {
                            progress.progress = (current * 95 / total.coerceAtLeast(1)).coerceIn(0, 95)
                            status.text = "تقدم الإنتاج $current من $total"
                        }
                    },
                    onChapterProgress = { current, total, message ->
                        runOnUiThread {
                            // Shot progress is more granular and monotonic; chapter callbacks update text only.
                            status.text = "الفصل $current من $total — $message"
                        }
                    }
                )
                val projectStateSaved = runCatching {
                    WolfProjectStore.updateStatus(
                        this,
                        project.id,
                        "مكتمل",
                        videoLocation = result.mediaUri ?: result.savedLocation,
                        lastError = null
                    ) != null
                }.getOrDefault(false)
                runOnUiThread {
                    producing = false
                    progress.progress = 100
                    progress.visibility = View.GONE
                    status.text = if (projectStateSaved) {
                        "تم حفظ الفيديو: " + result.savedLocation
                    } else {
                        "تم حفظ الفيديو، لكن تعذر تحديث حالة المشروع: " + result.savedLocation
                    }
                    Toast.makeText(this, "تم حفظ الفيديو في Movies/WOLF CODE S", Toast.LENGTH_LONG).show()
                    refreshChapterStatus()
                }
            } catch (error: Exception) {
                runCatching {
                    WolfProjectStore.updateStatus(
                        this,
                        project.id,
                        "فشل الإنتاج",
                        lastError = error.message ?: error.javaClass.simpleName
                    )
                }
                runOnUiThread {
                    producing = false
                    progress.visibility = View.GONE
                    showError(error)
                    refreshChapterStatus()
                }
            }
        }
    }

    private fun showError(error: Throwable) {
        val message = error.message ?: "حدث خطأ غير معروف"
        status.text = "تعذر إكمال العملية: " + message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 741 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
        } else if (requestCode == 741) {
            status.text = "يلزم السماح بالميكروفون لتسجيل الراوي"
        }
    }

    override fun onDestroy() {
        dismissTeleprompter()
        voiceRecorder?.cancel()
        stopVoicePreview()
        worker.shutdown()
        super.onDestroy()
    }
}
