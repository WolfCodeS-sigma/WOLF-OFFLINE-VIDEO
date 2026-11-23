package com.wolfcodes.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Project-scoped image assignments: three independent stills for each scene. */
object WolfSceneAssetStore {
    const val MAX_SCENES = 12
    const val SHOTS_PER_SCENE = 3
    const val MAX_IMAGE_SLOTS = MAX_SCENES * SHOTS_PER_SCENE
    const val DEPTH_SEGMENTATION_VERSION = 2

    private const val FILE_NAME = "wolf_scene_assets.json"
    private const val BACKUP_NAME = "wolf_scene_assets.json.bak"
    private const val TEMP_NAME = "wolf_scene_assets.json.tmp"
    private const val MAX_ASSETS = 600
    private val lock = Any()

    private data class Asset(
        val projectId: String,
        val sceneIndex: Int,
        val shotIndex: Int,
        val uri: String
    ) {
        val slotIndex: Int get() = sceneIndex * SHOTS_PER_SCENE + shotIndex
    }

    /** Backward-compatible view: only the primary image (shot zero) for each scene. */
    fun assignments(context: Context, projectId: String): Map<Int, String> =
        shotAssignments(context, projectId)
            .filterKeys { it % SHOTS_PER_SCENE == 0 }
            .mapKeys { (slotIndex, _) -> slotIndex / SHOTS_PER_SCENE }

    /** All images, keyed by sceneIndex * SHOTS_PER_SCENE + shotIndex. */
    fun shotAssignments(context: Context, projectId: String): Map<Int, String> = synchronized(lock) {
        if (!validProjectId(projectId)) return@synchronized emptyMap()
        read(context).filter { it.projectId == projectId }.associate { it.slotIndex to it.uri }
    }

    /** Legacy API: assign the primary image for one scene. */
    fun assign(context: Context, projectId: String, sceneIndex: Int, uri: String): Int =
        assignShot(context, projectId, sceneIndex, 0, uri)

    fun assignShot(
        context: Context, projectId: String, sceneIndex: Int, shotIndex: Int, uri: String
    ): Int = synchronized(lock) {
        require(validProjectId(projectId)) { "معرّف المشروع غير صالح لربط الصورة" }
        require(sceneIndex in 0 until MAX_SCENES) { "رقم المشهد خارج النطاق المدعوم" }
        require(shotIndex in 0 until SHOTS_PER_SCENE) { "رقم اللقطة خارج النطاق المدعوم" }
        val value = uri.trim()
        require(value.startsWith("content://") || value.startsWith("file://")) { "رابط الصورة غير صالح" }
        val current = read(context).toMutableList()
        current.removeAll {
            it.projectId == projectId && it.sceneIndex == sceneIndex && it.shotIndex == shotIndex
        }
        current.add(Asset(projectId, sceneIndex, shotIndex, value))
        write(context, current.takeLast(MAX_ASSETS))
        current.count { it.projectId == projectId }
    }

    /** Legacy API: remove the primary image for one scene. */
    fun remove(context: Context, projectId: String, sceneIndex: Int): Boolean =
        removeShot(context, projectId, sceneIndex, 0)

    fun removeShot(context: Context, projectId: String, sceneIndex: Int, shotIndex: Int): Boolean =
        synchronized(lock) {
            if (!validProjectId(projectId) || sceneIndex !in 0 until MAX_SCENES ||
                shotIndex !in 0 until SHOTS_PER_SCENE
            ) return@synchronized false
            val current = read(context)
            val remaining = current.filterNot {
                it.projectId == projectId && it.sceneIndex == sceneIndex && it.shotIndex == shotIndex
            }
            if (remaining.size == current.size) return@synchronized false
            write(context, remaining)
            true
        }

    fun clearProject(context: Context, projectId: String): Int = synchronized(lock) {
        if (!validProjectId(projectId)) return@synchronized 0
        val current = read(context)
        val remaining = current.filterNot { it.projectId == projectId }
        val removed = current.size - remaining.size
        if (removed > 0) write(context, remaining)
        removed
    }

    private fun validProjectId(value: String): Boolean =
        value.isNotBlank() && value.length <= 80 && value.matches(Regex("[A-Za-z0-9-]+"))

    private fun read(context: Context): List<Asset> {
        val candidates = listOf(File(context.filesDir, FILE_NAME), File(context.filesDir, BACKUP_NAME))
        for (file in candidates) {
            if (!file.isFile) continue
            val parsed = runCatching {
                val array = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("assets") ?: JSONArray()
                (0 until array.length()).mapNotNull { index ->
                    runCatching {
                        val item = array.getJSONObject(index)
                        val projectId = item.getString("projectId")
                        val sceneIndex = item.getInt("sceneIndex")
                        // Version-one records did not contain shotIndex: keep them as shot zero.
                        val shotIndex = item.optInt("shotIndex", 0)
                        val uri = item.getString("uri")
                        if (validProjectId(projectId) && sceneIndex in 0 until MAX_SCENES &&
                            shotIndex in 0 until SHOTS_PER_SCENE &&
                            (uri.startsWith("content://") || uri.startsWith("file://"))
                        ) Asset(projectId, sceneIndex, shotIndex, uri) else null
                    }.getOrNull()
                }.takeLast(MAX_ASSETS)
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return emptyList()
    }

    private fun write(context: Context, records: List<Asset>) {
        val root = JSONObject().put("version", 2).put(
            "assets",
            JSONArray().apply {
                records.takeLast(MAX_ASSETS).forEach { asset ->
                    put(JSONObject()
                        .put("projectId", asset.projectId)
                        .put("sceneIndex", asset.sceneIndex)
                        .put("shotIndex", asset.shotIndex)
                        .put("uri", asset.uri))
                }
            }
        )
        val primary = File(context.filesDir, FILE_NAME)
        val backup = File(context.filesDir, BACKUP_NAME)
        val temporary = File(context.filesDir, TEMP_NAME)
        FileOutputStream(temporary).use { stream ->
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (primary.exists()) {
            if (backup.exists() && !backup.delete()) throw IllegalStateException("تعذر تدوير نسخة صور المشاهد")
            if (!primary.renameTo(backup)) throw IllegalStateException("تعذر تأمين نسخة صور المشاهد")
        }
        if (!temporary.renameTo(primary)) {
            if (backup.exists()) backup.renameTo(primary)
            throw IllegalStateException("تعذر حفظ ربط صور المشاهد")
        }
        backup.delete()
    }
}
