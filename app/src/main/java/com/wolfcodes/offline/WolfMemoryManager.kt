package com.wolfcodes.offline

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/** Conservative cache manager. It never touches gallery media, project records, or user-selected assets. */
object WolfMemoryManager {
    data class CleanupResult(
        val removedFiles: Int,
        val releasedBytes: Long,
        val remainingCacheBytes: Long
    )

    private const val OFFLINE_FOLDER = "WOLF_OFFLINE"
    private val staleAgeMs = TimeUnit.HOURS.toMillis(72)

    fun cacheUsageBytes(context: Context): Long = directorySize(context.cacheDir)

    fun cleanupStaleOfflineTemps(
        context: Context,
        now: Long = System.currentTimeMillis(),
        maxAgeMs: Long = staleAgeMs
    ): CleanupResult {
        val offlineDirectory = File(context.cacheDir, OFFLINE_FOLDER)
        if (!offlineDirectory.isDirectory) {
            return CleanupResult(0, 0L, cacheUsageBytes(context))
        }

        var removed = 0
        var released = 0L
        offlineDirectory.listFiles()?.forEach { file ->
            if (!file.isFile || !isOwnedTemporaryFile(file.name)) return@forEach
            val age = now - file.lastModified()
            if (age < maxAgeMs) return@forEach
            val size = file.length()
            if (file.delete()) {
                removed++
                released += size
            }
        }
        return CleanupResult(removed, released, cacheUsageBytes(context))
    }

    private fun isOwnedTemporaryFile(name: String): Boolean =
        name.startsWith("silent_wolf_offline_") ||
            name.startsWith("sfx_wolf_offline_") ||
            name.startsWith("normalized_wolf_offline_") ||
            name.startsWith("wolf_offline_")

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::directorySize) ?: 0L
    }
}
