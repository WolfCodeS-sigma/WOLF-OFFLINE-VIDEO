package com.wolfcodes.offline

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.View
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
    private var voiceUri: String? = null
    private var selectedAspectRatio: String = "9:16"
    private val selectedImageUris = mutableListOf<String>()
    private var pendingImageSlot: Int? = null
    private var producing = false

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

        root.addView(label("مدة الفيلم بالثواني (5 إلى 1800)"))
        durationField = field("مثال: 300").apply { setText("60") }
        root.addView(durationField)

        introToggle = checkBox("إضافة مقدمة بصمة الذئب السينمائية", true)
        watermarkToggle = checkBox("إضافة العلامة المائية WOLF CODE S", true)
        soundEffectsToggle = checkBox("توليد المؤثرات الصوتية الإجرائية", true)
        root.addView(introToggle)
        root.addView(watermarkToggle)
        root.addView(soundEffectsToggle)

        root.addView(button("حفظ المشروع").apply { setOnClickListener { saveProject() } })
        root.addView(button("اختيار صور متعددة (اختياري)").apply {
            setOnClickListener { chooseMultipleImages() }
        })
        root.addView(button("تعيين صورة إلى لقطة محددة من 36 لقطة").apply {
            setOnClickListener { chooseImageSlot() }
        })

        root.addView(label("التعليق الصوتي"))
        root.addView(button("تسجيل / إعادة تسجيل التعليق الصوتي").apply {
            setOnClickListener { startVoiceRecording() }
        })
        root.addView(button("إيقاف التسجيل").apply {
            setOnClickListener { voiceRecorder?.stopByUser() }
        })
        root.addView(button("معاينة التسجيل الصوتي").apply {
            setOnClickListener { playVoicePreview() }
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
        require(duration in 5..1800) { "المدة يجب أن تكون بين 5 ثوانٍ و30 دقيقة" }

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
        pendingImageSlot = null
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 742)
    }

    private fun chooseImageSlot() {
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

    private fun saveProject() {
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
        WolfStoryVoiceStore.requestRepeat(this, project.id)
        voiceUri = null
        workingProject = project.copy(audioUri = null)
        WolfProjectStore.save(this, workingProject!!)
        val destination = WolfStoryVoiceStore.recordingTarget(this, project.id)
        voiceRecorder = WolfVoiceRecorder(this, object : WolfVoiceRecorder.Listener {
            override fun onProgress(elapsedMs: Long, speechDetected: Boolean) {
                runOnUiThread { status.text = "تسجيل الراوي: " + (elapsedMs / 1000) + " ثانية" }
            }

            override fun onSaved(file: File, durationMs: Long, automaticStop: Boolean) {
                runOnUiThread {
                    runCatching {
                        WolfStoryVoiceStore.saveRecording(this@MainActivity, project.id, file, durationMs)
                        val approved = WolfStoryVoiceStore.approveRecording(this@MainActivity, project.id)
                            ?: throw IllegalStateException("تعذر اعتماد التسجيل الصوتي")
                        voiceUri = Uri.fromFile(file).toString()
                        workingProject = project.copy(audioUri = voiceUri)
                        WolfProjectStore.save(this@MainActivity, workingProject!!)
                        status.text = "تم حفظ واعتماد التسجيل — " + (approved.audioDurationMs / 1000) + " ثانية"
                        refreshVoiceStatus()
                        refreshProjectList()
                    }.onFailure { showError(it) }
                }
            }

            override fun onError(message: String) {
                runOnUiThread { showError(IllegalStateException(message)); refreshVoiceStatus() }
            }
        })
        if (voiceRecorder?.start(destination) != true) {
            status.text = "تعذر بدء التسجيل الصوتي"
        } else {
            status.text = "بدأ تسجيل الراوي"
        }
    }

    private fun requestVoiceRepeat() {
        val project = workingProject ?: runCatching { currentProject() }.getOrElse {
            showError(it)
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
        status.text = "تم إلغاء اعتماد التسجيل السابق. يمكنك التسجيل من جديد."
        refreshVoiceStatus()
        refreshProjectList()
    }

    private fun playVoicePreview() {
        val project = workingProject ?: runCatching { currentProject() }.getOrElse {
            showError(it)
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
        val message = when {
            session == null -> "جلسة الراوي لم تبدأ بعد"
            !session.scriptApproved -> "نص القصة غير معتمد"
            session.voiceApproved -> "التسجيل معتمد — " + (session.audioDurationMs / 1000) + " ثانية"
            session.voiceFilePath != null -> "يوجد تسجيل يحتاج إلى المراجعة والاعتماد"
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
        if (producing) return
        stopVoicePreview()
        workingProject = item
        titleField.setText(item.title)
        storyField.setText(item.story)
        durationField.setText(item.durationSeconds.toString())
        voiceUri = item.audioUri
        selectedImageUris.clear()
        selectedImageUris.addAll(item.imageUris)
        selectedAspectRatio = item.aspectRatio.substringBefore(" ")
        introToggle.isChecked = item.includeIntro
        watermarkToggle.isChecked = item.includeWatermark
        soundEffectsToggle.isChecked = item.soundEffectsEnabled
        status.text = "تم فتح المشروع: " + item.title
        refreshVoiceStatus()
        refreshChapterStatus()
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
        if (producing) {
            status.text = "لا يمكن حذف مشروع أثناء إنتاج الفيديو"
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
                val voiceSession = WolfStoryVoiceStore.read(this, project.id)
                val approvedVoice = project.audioUri?.let { storedAudio ->
                    val storedPath = runCatching { Uri.parse(storedAudio).path }.getOrNull()
                    val approvedPath = voiceSession?.voiceFilePath
                    voiceSession?.voiceApproved == true &&
                        voiceSession.scriptApproved &&
                        voiceSession.finalStory == project.story &&
                        !storedPath.isNullOrBlank() &&
                        !approvedPath.isNullOrBlank() &&
                        runCatching {
                            File(storedPath).canonicalFile == File(approvedPath).canonicalFile &&
                                File(approvedPath).isFile
                        }.getOrDefault(false)
                } ?: false
                val effectiveDuration = if (approvedVoice) {
                    (WolfStoryVoiceStateMachine.durationSecondsCeil(voiceSession!!.audioDurationMs) +
                        if (project.includeIntro) 3 else 0).coerceIn(5, 1800)
                } else project.durationSeconds
                val shotAssignments = WolfSceneAssetStore.shotAssignments(this, project.id)
                    .mapNotNull { (slot, value) ->
                        runCatching { slot to Uri.parse(value) }.getOrNull()
                    }.toMap()
                val result = OfflineProductionEngine.render(
                    context = this,
                    story = project.story,
                    seconds = effectiveDuration,
                    imageUris = project.imageUris.mapNotNull { value ->
                        runCatching { Uri.parse(value) }.getOrNull()
                    },
                    audioUri = if (approvedVoice) Uri.parse(project.audioUri!!) else null,
                    aspectRatio = project.aspectRatio,
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
                            status.text = "إنتاج المشهد $current من $total"
                        }
                    },
                    onChapterProgress = { current, total, message ->
                        runOnUiThread {
                            progress.progress = (current * 95 / total.coerceAtLeast(1)).coerceIn(0, 95)
                            status.text = "الفصل $current من $total — $message"
                        }
                    }
                )
                runOnUiThread {
                    producing = false
                    progress.progress = 100
                    progress.visibility = View.GONE
                    status.text = "تم حفظ الفيديو: " + result.savedLocation
                    Toast.makeText(this, "تم حفظ الفيديو في Movies/WOLF CODE S", Toast.LENGTH_LONG).show()
                    refreshChapterStatus()
                }
            } catch (error: Exception) {
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
        voiceRecorder?.cancel()
        stopVoicePreview()
        worker.shutdown()
        super.onDestroy()
    }
}
