package com.wolfcodes.offline

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Privacy-conscious local diagnostics. Never pass story text, prompts, credentials or cookies here. */
object WolfDiagnostics {
    private const val PREFS = "wolf_diagnostics"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 100

    fun record(context: Context, stage: String, component: String, type: String,
        message: String, technicalCode: String = "") {
        val enabled = context.getSharedPreferences("wolf_code_s", Context.MODE_PRIVATE)
            .getBoolean("حفظ تقارير الأخطاء", true)
        if (!enabled) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$stamp | ${safe(stage)} | ${safe(component)} | ${safe(type)} | " +
            "${redact(message).take(240)} | ${redact(technicalCode).take(160)}"
        val events = ((prefs.getString(KEY_EVENTS, "") ?: "").lines().filter { it.isNotBlank() } + line).takeLast(MAX_EVENTS)
        prefs.edit().putString(KEY_EVENTS, events.joinToString("\n")).apply()
    }

    fun report(context: Context): String {
        val events = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, "") ?: ""
        return if (events.isBlank()) "WOLF CODE S — تقرير التشخيص\nلا توجد أحداث مسجلة."
        else "WOLF CODE S — تقرير التشخيص\nآخر ${events.lines().size} حدثًا\n$events"
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
    }

    private fun safe(value: String) = redact(value).replace("|", "/").take(60)
    private fun redact(value: String): String = value
        .replace(Regex("(?i)(api[_-]?key|token|password|cookie|authorization)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace(Regex("(?i)bearer\\s+[a-z0-9._-]+"), "Bearer [REDACTED]")
}
