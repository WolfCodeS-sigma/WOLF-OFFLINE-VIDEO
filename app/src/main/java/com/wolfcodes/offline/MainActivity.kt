package com.wolfcodes.offline

import android.app.Activity
import android.os.Bundle
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

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var titleField: EditText
    private lateinit var storyField: EditText
    private lateinit var durationField: EditText
    private lateinit var status: TextView
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
        root.addView(label("مدة الفيلم بالثواني (5 إلى 1800)"))
        durationField = field("مثال: 300").apply { setText("60") }
        root.addView(durationField)
        val save = button("حفظ المشروع")
        root.addView(save)
        save.setOnClickListener { saveProject() }
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
            root.addView(label(item.title + " — " + item.durationSeconds + " ثانية"))
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
        return WolfProjectStore.newProject(
            story = story,
            sourceUrl = "",
            aspectRatio = "9:16 رأسي",
            durationSeconds = duration,
            visualStyle = "رعب سينمائي مظلم",
            horrorType = "رعب نفسي وخارق للطبيعة",
            soundEffectsEnabled = true,
            imageUris = emptyList(),
            audioUri = null,
            includeIntro = true,
            includeWatermark = true
        ).copy(title = title)
    }

    private fun saveProject() {
        try {
            WolfProjectStore.save(this, currentProject())
            status.text = "تم حفظ المشروع على الهاتف"
            Toast.makeText(this, "تم حفظ المشروع", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            showError(error)
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
                val result = OfflineProductionEngine.render(
                    context = this,
                    story = project.story,
                    seconds = project.durationSeconds,
                    imageUris = project.imageUris.mapNotNull { value -> runCatching { android.net.Uri.parse(value) }.getOrNull() },
                    audioUri = project.audioUri?.let { android.net.Uri.parse(it) },
                    aspectRatio = project.aspectRatio,
                    generateSoundEffects = project.soundEffectsEnabled,
                    includeWatermark = project.includeWatermark,
                    includeIntro = project.includeIntro,
                    projectId = project.id,
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
        worker.shutdown()
        super.onDestroy()
    }
}
