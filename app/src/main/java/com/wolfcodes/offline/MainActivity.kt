package com.wolfcodes.offline

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.io.File

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var titleField: EditText
    private lateinit var storyField: EditText
    private lateinit var durationField: EditText
    private lateinit var status: TextView
    private var workingProject: WolfProject? = null
    private var voiceRecorder: WolfVoiceRecorder? = null
    private var voiceUri: String? = null
    private var selectedAspectRatio: String = "9:16"
    private val selectedImageUris = mutableListOf<String>()
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
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
        root.addView(label("القصة"))
        storyField = field("اكتب أو الصق قصة الرعب هنا").apply {
            minLines = 8
            gravity = Gravity.TOP
        }
        root.addView(storyField)
        root.addView(label("مقاس الفيديو"))
        val ratioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("9:16", "16:9", "1:1", "4:5").forEach { ratio ->
            val choice = button(ratio)
            ratioRow.addView(choice, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            choice.setOnClickListener {
                selectedAspectRatio = ratio
                status.text = "تم اختيار مقاس " + ratio
            }
        }
        root.addView(ratioRow)
        root.addView(label("مدة الفيلم بالثواني (5 إلى 1800)"))
        durationField = field("مثال: 300").apply { setText("60") }
        root.addView(durationField)
        val save = button("حفظ المشروع")
        root.addView(save)
        save.setOnClickListener { saveProject() }
        val chooseImages = button("اختيار صور المشاهد (اختياري)")
        root.addView(chooseImages)
        chooseImages.setOnClickListener { chooseSceneImages() }
        val voice = button("تسجيل التعليق الصوتي")
        root.addView(voice)
        voice.setOnClickListener { startVoiceRecording() }
        val stopVoice = button("إيقاف التسجيل")
        root.addView(stopVoice)
        stopVoice.setOnClickListener { voiceRecorder?.stopByUser() }
        val export = button("إنتاج فيديو أوفلاين")
        root.addView(export)
        export.setOnClickListener { produce() }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress)
        status = label("جاهز للعمل محليًا دون اتصال بالإنترنت").apply { gravity = Gravity.CENTER }
        root.addView(status)
        root.addView(label("المشاريع المحفوظة"))
        WolfProjectStore.list(this).take(15).forEach { item ->
            val projectItem = label(item.title + " — " + item.durationSeconds + " ثانية")
            projectItem.setOnClickListener {
                workingProject = item
                titleField.setText(item.title)
                storyField.setText(item.story)
                durationField.setText(item.durationSeconds.toString())
                voiceUri = item.audioUri
                selectedImageUris.clear()
                selectedImageUris.addAll(item.imageUris)
                selectedAspectRatio = item.aspectRatio.substringBefore(" ")
                status.text = "تم فتح المشروع: " + item.title
            }
            root.addView(projectItem)
        }
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

    private fun currentProject(): WolfProject {
        val title = titleField.text.toString().trim()
        val story = storyField.text.toString().trim()
        val duration = durationField.text.toString().toIntOrNull()
            ?: throw IllegalArgumentException("اكتب مدة صحيحة بالثواني")
        require(title.isNotBlank()) { "اكتب اسم الفيلم" }
        require(story.length >= 20) { "اكتب قصة أطول قليلًا حتى يمكن تقسيمها إلى مشاهد" }
        require(duration in 5..1800) { "المدة يجب أن تكون بين 5 ثوانٍ و30 دقيقة" }
        val existing = workingProject
        if (existing != null && (existing.story != story || existing.title != title)) {
            voiceUri = null
            selectedImageUris.clear()
        }
        val base = if (existing != null && existing.story == story && existing.title == title) existing.copy(durationSeconds = duration, audioUri = voiceUri, aspectRatio = selectedAspectRatio, imageUris = selectedImageUris.toList()) else WolfProjectStore.newProject(
            story = story,
            sourceUrl = "",
            aspectRatio = selectedAspectRatio,
            durationSeconds = duration,
            visualStyle = "رعب سينمائي مظلم",
            horrorType = "رعب نفسي وخارق للطبيعة",
            soundEffectsEnabled = true,
            imageUris = selectedImageUris.toList(),
            audioUri = voiceUri,
            includeIntro = true,
            includeWatermark = true
        ).copy(title = title)
        workingProject = base
        return base
    }

    private fun chooseSceneImages() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 742)
    }

    @Deprecated("The document picker result is handled for Android versions supported by this app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 742 || resultCode != RESULT_OK || data == null) return
        val picked = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(picked::add)
        }
        data.data?.let { if (picked.isEmpty()) picked.add(it) }
        picked.distinct().take(WolfProjectStore.MAX_IMAGES).forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (uri.toString() !in selectedImageUris) selectedImageUris.add(uri.toString())
            }
        }
        workingProject = workingProject?.copy(imageUris = selectedImageUris.toList())
        status.text = "تم اختيار " + selectedImageUris.size + " صورة للمشاهد واللقطات"
    }

    private fun saveProject() {
        try {
            val project = currentProject()
            WolfProjectStore.save(this, project)
            status.text = "تم حفظ المشروع على الهاتف"
            Toast.makeText(this, "تم حفظ المشروع", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun startVoiceRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 741)
            return
        }
        val project = try { currentProject() } catch (error: Exception) {
            showError(error)
            return
        }
        WolfProjectStore.save(this, project)
        WolfStoryVoiceStore.initialize(this, project.id, project.story)
        WolfStoryVoiceStore.approveScript(this, project.id, project.story)
        val destination = WolfStoryVoiceStore.recordingTarget(this, project.id)
        voiceRecorder = WolfVoiceRecorder(this, object : WolfVoiceRecorder.Listener {
            override fun onProgress(elapsedMs: Long, speechDetected: Boolean) {
                runOnUiThread {
                    status.text = "تسجيل الراوي: " + (elapsedMs / 1000) + " ثانية"
                }
            }
            override fun onSaved(file: File, durationMs: Long, automaticStop: Boolean) {
                runOnUiThread {
                    runCatching {
                        WolfStoryVoiceStore.saveRecording(this@MainActivity, project.id, file, durationMs)
                        WolfStoryVoiceStore.approveRecording(this@MainActivity, project.id)
                        voiceUri = Uri.fromFile(file).toString()
                        workingProject = project.copy(audioUri = voiceUri)
                        WolfProjectStore.save(this@MainActivity, workingProject!!)
                        status.text = "تم حفظ التعليق الصوتي واعتماده للفيلم"
                        Toast.makeText(this@MainActivity, "تم حفظ التسجيل الصوتي", Toast.LENGTH_LONG).show()
                    }.onFailure { showError(it) }
                }
            }
            override fun onError(message: String) {
                runOnUiThread { showError(IllegalStateException(message)) }
            }
        })
        if (voiceRecorder?.start(destination) != true) {
            status.text = "تعذر بدء التسجيل الصوتي"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 741 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
        } else if (requestCode == 741) {
            status.text = "يلزم السماح بالميكروفون لتسجيل الراوي"
        }
    }

    private fun produce() {
        val project = try { currentProject().also { WolfProjectStore.save(this, it) } } catch (error: Exception) {
            showError(error)
            return
        }
        progress.visibility = View.VISIBLE
        progress.progress = 0
        status.text = "جارٍ تجهيز محرك الإنتاج المحلي"
        worker.execute {
            try {
                val voiceSession = WolfStoryVoiceStore.read(this, project.id)
                val approvedVoice = voiceSession?.voiceApproved == true
                val effectiveDuration = if (approvedVoice) {
                    (WolfStoryVoiceStateMachine.durationSecondsCeil(voiceSession!!.audioDurationMs) +
                        if (project.includeIntro) 3 else 0).coerceIn(5, 1800)
                } else project.durationSeconds
                val result = OfflineProductionEngine.render(
                    context = this,
                    story = project.story,
                    seconds = effectiveDuration,
                    imageUris = project.imageUris.mapNotNull { value -> runCatching { android.net.Uri.parse(value) }.getOrNull() },
                    audioUri = project.audioUri?.let { android.net.Uri.parse(it) },
                    aspectRatio = project.aspectRatio,
                    generateSoundEffects = project.soundEffectsEnabled,
                    includeWatermark = project.includeWatermark,
                    includeIntro = project.includeIntro,
                    projectId = project.id,
                    audioDurationIsAuthoritative = approvedVoice,
                    audioDurationMs = voiceSession?.audioDurationMs,
                    onSceneProgress = { current, total ->
                        runOnUiThread {
                            progress.progress = (current * 95 / total.coerceAtLeast(1)).coerceIn(0, 95)
                            status.text = "إنتاج المشهد " + current + " من " + total
                        }
                    },
                    onChapterProgress = { current, total, message ->
                        runOnUiThread {
                            progress.progress = (current * 95 / total.coerceAtLeast(1)).coerceIn(0, 95)
                            status.text = message
                        }
                    }
                )
                runOnUiThread {
                    progress.progress = 100
                    progress.visibility = View.GONE
                    status.text = "تم حفظ الفيديو: " + result.savedLocation
                    Toast.makeText(this, "تم حفظ الفيديو في Movies/WOLF CODE S", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    showError(error)
                }
            }
        }
    }

    private fun showError(error: Throwable) {
        val message = error.message ?: "حدث خطأ غير معروف"
        status.text = "تعذر إكمال العملية: " + message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        voiceRecorder?.cancel()
        worker.shutdown()
        super.onDestroy()
    }
}
