package com.wolfcodes.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WolfProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val story: String,
    val durationSeconds: Int,
    val aspectRatio: String = "9:16",
    val updatedAt: Long = System.currentTimeMillis()
)

class WolfProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("wolf_offline_projects", Context.MODE_PRIVATE)

    @Synchronized
    fun save(project: WolfProject): WolfProject {
        require(project.title.isNotBlank() && project.story.isNotBlank())
        require(project.durationSeconds in 5..1800)
        val saved = project.copy(updatedAt = System.currentTimeMillis())
        val all = loadAll().filterNot { it.id == saved.id }.toMutableList()
        all.add(saved)
        val json = JSONArray()
        all.sortedByDescending { it.updatedAt }.forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("story", item.story)
                put("durationSeconds", item.durationSeconds)
                put("aspectRatio", item.aspectRatio)
                put("updatedAt", item.updatedAt)
            })
        }
        check(prefs.edit().putString("projects", json.toString()).commit()) { "تعذر حفظ المشروع" }
        return saved
    }

    @Synchronized
    fun loadAll(): List<WolfProject> {
        val array = JSONArray(prefs.getString("projects", "[]") ?: "[]")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(WolfProject(
                    item.getString("id"), item.getString("title"), item.getString("story"),
                    item.getInt("durationSeconds"), item.optString("aspectRatio", "9:16"),
                    item.optLong("updatedAt")
                ))
            }
        }
    }

    @Synchronized
    fun delete(id: String) {
        val remaining = loadAll().filterNot { it.id == id }
        val json = JSONArray()
        remaining.forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("story", item.story)
                put("durationSeconds", item.durationSeconds); put("aspectRatio", item.aspectRatio)
                put("updatedAt", item.updatedAt)
            })
        }
        check(prefs.edit().putString("projects", json.toString()).commit()) { "تعذر حذف المشروع" }
    }
}
