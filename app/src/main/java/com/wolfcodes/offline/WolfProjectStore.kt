package com.wolfcodes.offline

import android.content.Context
import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Durable local project records. Keeps the source story, edit choices and asset references together. */
data class WolfProject(
    val id: String,
    val title: String,
    val story: String,
    val sourceUrl: String,
    val aspectRatio: String,
    val durationSeconds: Int,
    val visualStyle: String,
    val horrorType: String,
    val realism: String,
    val ending: String,
    val includeIntro: Boolean,
    val includeWatermark: Boolean,
    val soundEffectsEnabled: Boolean,
    val imageUris: List<String>,
    val audioUri: String?,
    val status: String,
    val videoLocation: String?,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)

object WolfProjectStore {
    const val MAX_IMAGES = WolfSceneAssetStore.MAX_IMAGE_SLOTS
    private const val FOLDER = "wolf_projects"
    private val lock = Any()

    fun newProject(
        story: String,
        sourceUrl: String,
        aspectRatio: String,
        durationSeconds: Int,
        visualStyle: String,
        horrorType: String,
        soundEffectsEnabled: Boolean,
        imageUris: List<String>,
        audioUri: String?,
        realism: String = "واقعي",
        ending: String = "صادمة",
        includeIntro: Boolean = true,
        includeWatermark: Boolean = true
    ): WolfProject {
        val now = System.currentTimeMillis()
        val title = story.lineSequence().firstOrNull { it.isNotBlank() }
            ?.replace(Regex("\\s+"), " ")?.take(48) ?: "فيلم رعب جديد"
        return WolfProject(
            id = UUID.randomUUID().toString(),
            title = title,
            story = story,
            sourceUrl = sourceUrl,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds.coerceIn(1, 1800),
            visualStyle = visualStyle,
            horrorType = horrorType,
            realism = realism,
            ending = ending,
            includeIntro = includeIntro,
            includeWatermark = includeWatermark,
            soundEffectsEnabled = soundEffectsEnabled,
            imageUris = imageUris.distinct().take(MAX_IMAGES),
            audioUri = audioUri,
            status = "مسودة",
            videoLocation = null,
            lastError = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun save(context: Context, project: WolfProject) = synchronized(lock) {
        val directory = directory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد المشاريع المحلي")
        }
        val json = JSONObject().apply {
            put("id", project.id)
            put("title", project.title)
            put("story", project.story)
            put("sourceUrl", project.sourceUrl)
            put("aspectRatio", project.aspectRatio)
            put("durationSeconds", project.durationSeconds)
            put("visualStyle", project.visualStyle)
            put("horrorType", project.horrorType)
            put("realism", project.realism)
            put("ending", project.ending)
            put("includeIntro", project.includeIntro)
            put("includeWatermark", project.includeWatermark)
            put("soundEffectsEnabled", project.soundEffectsEnabled)
            put("imageUris", JSONArray(project.imageUris))
            put("audioUri", project.audioUri ?: JSONObject.NULL)
            put("status", project.status)
            put("videoLocation", project.videoLocation ?: JSONObject.NULL)
            put("lastError", project.lastError ?: JSONObject.NULL)
            put("createdAt", project.createdAt)
            put("updatedAt", System.currentTimeMillis())
        }
        val destination = fileFor(context, project.id)
        val temporary = File(directory, project.id + ".json.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // POSIX rename replaces the old file atomically because both paths share a directory.
            Os.rename(temporary.absolutePath, destination.absolutePath)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("تعذر حفظ المشروع بأمان", error)
        }
    }

    fun list(context: Context): List<WolfProject> = synchronized(lock) {
        val directory = directory(context)
        if (!directory.isDirectory) return@synchronized emptyList()
        directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file -> runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt } ?: emptyList()
    }

    fun load(context: Context, id: String): WolfProject? = synchronized(lock) {
        if (!id.matches(Regex("[A-Za-z0-9-]{16,64}"))) return@synchronized null
        val file = fileFor(context, id)
        if (file.isFile) runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull() else null
    }

    fun updateStory(context: Context, id: String, story: String): WolfProject? = synchronized(lock) {
        val current = load(context, id) ?: return@synchronized null
        val updated = current.copy(story = story.take(60000), updatedAt = System.currentTimeMillis())
        save(context, updated)
        updated
    }

    fun updateStatus(
        context: Context,
        id: String,
        status: String,
        videoLocation: String? = null,
        lastError: String? = null
    ): WolfProject? = synchronized(lock) {
        val current = load(context, id) ?: return@synchronized null
        val updated = current.copy(
            status = status.take(48),
            videoLocation = videoLocation ?: current.videoLocation,
            lastError = lastError?.take(240),
            updatedAt = System.currentTimeMillis()
        )
        save(context, updated)
        updated
    }

    fun delete(context: Context, id: String): Boolean = synchronized(lock) {
        if (!id.matches(Regex("[A-Za-z0-9-]{16,64}"))) return@synchronized false
        if (load(context, id) == null) return@synchronized false
        // URI grants may be shared by several projects; retain them until a reference-counted
        // asset manager can safely release the final reference.
        fileFor(context, id).delete()
    }

    private fun directory(context: Context) = File(context.filesDir, FOLDER)

    private fun fileFor(context: Context, id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{16,64}"))) { "معرّف مشروع غير صالح" }
        return File(directory(context), id + ".json")
    }

    private fun parse(raw: String): WolfProject {
        val json = JSONObject(raw)
        val images = json.optJSONArray("imageUris") ?: JSONArray()
        val uriList = (0 until images.length()).mapNotNull { index ->
            images.optString(index).takeIf { it.isNotBlank() }
        }
        fun nullable(name: String): String? =
            if (json.isNull(name)) null else json.optString(name).takeIf { it.isNotBlank() }

        return WolfProject(
            id = json.getString("id"),
            title = json.optString("title", "فيلم رعب"),
            story = json.optString("story", ""),
            sourceUrl = json.optString("sourceUrl", ""),
            aspectRatio = json.optString("aspectRatio", "9:16 رأسي"),
            durationSeconds = json.optInt("durationSeconds", 60).coerceIn(1, 1800),
            visualStyle = json.optString("visualStyle", "سينمائي واقعي"),
            horrorType = json.optString("horrorType", "رعب نفسي"),
            realism = json.optString("realism", "واقعي"),
            ending = json.optString("ending", "صادمة"),
            includeIntro = json.optBoolean("includeIntro", true),
            includeWatermark = json.optBoolean("includeWatermark", true),
            soundEffectsEnabled = json.optBoolean("soundEffectsEnabled", true),
            imageUris = uriList.distinct().take(MAX_IMAGES),
            audioUri = nullable("audioUri"),
            status = json.optString("status", "مسودة"),
            videoLocation = nullable("videoLocation"),
            lastError = nullable("lastError"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
