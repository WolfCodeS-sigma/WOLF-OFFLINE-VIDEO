package com.wolfcodes.offline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer

data class WolfVoiceMergeResult(
    val file: File,
    val durationMs: Long,
    val segmentCount: Int
)

/**
 * Stores narration as independently recoverable AAC takes and joins them into one local M4A.
 * The approved story fingerprint remains owned by WolfStoryVoiceStore; this manifest only
 * records the audio pieces and their measured durations.
 */
object WolfVoiceSegmentStore {
    private const val DIRECTORY = "wolf_story_voice"
    private const val MAX_DURATION_MS = 1_800_000L
    private const val MAX_SEGMENTS = 256
    private val lock = Any()

    fun nextSegmentTarget(context: Context, projectId: String): File = synchronized(lock) {
        require(validProjectId(projectId)) { "معرّف المشروع غير صالح" }
        val directory = voiceDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد مقاطع الراوي")
        }
        val stored = readEntries(directory, projectId)
        val entries = registerCommittedOrphans(directory, projectId, stored)
        if (entries.size != stored.size) writeEntries(directory, projectId, entries)
        require(entries.size < MAX_SEGMENTS) { "وصل التسجيل إلى الحد الأقصى لعدد المقاطع" }
        File(directory, segmentFileName(projectId, entries.size + 1))
    }

    /** Migrates a previously approved single-take recording into the segment ledger once. */
    fun importExistingTake(
        context: Context,
        projectId: String,
        existingFile: File?,
        durationMs: Long
    ) = synchronized(lock) {
        if (!validProjectId(projectId) || existingFile == null) return@synchronized
        val directory = voiceDirectory(context).canonicalFile
        val source = existingFile.canonicalFile
        if (!source.isFile || source.length() <= 1024L ||
            !source.path.startsWith(directory.path + File.separator)
        ) return@synchronized
        val entries = readEntries(directory, projectId)
        if (entries.isNotEmpty()) return@synchronized
        val hasRecoverableParts = directory.listFiles()?.any {
            it.name.startsWith("$projectId-segment-") &&
                it.name.endsWith(".m4a") && isValidAac(it)
        } == true
        if (hasRecoverableParts) return@synchronized
        val duration = readDurationMs(source).takeIf { it in 1_000L..MAX_DURATION_MS } ?: durationMs
        if (duration !in 1_000L..MAX_DURATION_MS) return@synchronized
        val destination = File(directory, segmentFileName(projectId, 1))
        if (source != destination) {
            // Preserve the existing approved take until the replacement merged file is fully
            // assembled. A failed recorder start or interrupted merge must never destroy it.
            val pending = File(directory, destination.name + ".importing")
            pending.delete()
            java.io.FileInputStream(source).use { input ->
                java.io.FileOutputStream(pending).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            require(pending.isFile && pending.length() == source.length()) {
                "تعذر نسخ التسجيل السابق إلى مساحة المقاطع"
            }
            if (destination.exists() && !destination.delete()) {
                pending.delete()
                throw IllegalStateException("تعذر تجهيز مقطع التسجيل السابق")
            }
            Os.rename(pending.absolutePath, destination.absolutePath)
        }
        writeEntries(directory, projectId, listOf(Entry(destination.name, duration)))
    }

    fun segmentCount(context: Context, projectId: String): Int = synchronized(lock) {
        if (!validProjectId(projectId)) 0 else readEntries(voiceDirectory(context), projectId).size
    }

    fun appendAndMerge(
        context: Context,
        projectId: String,
        segmentFile: File,
        measuredDurationMs: Long
    ): WolfVoiceMergeResult = synchronized(lock) {
        require(validProjectId(projectId)) { "معرّف المشروع غير صالح" }
        val directory = voiceDirectory(context).canonicalFile
        val segment = segmentFile.canonicalFile
        require(segment.path.startsWith(directory.path + File.separator)) {
            "يجب أن يكون مقطع الراوي داخل مساحة المشروع الخاصة"
        }
        require(segment.isFile && segment.length() > 1024L) { "مقطع الراوي غير صالح" }

        val entries = readEntries(directory, projectId).toMutableList()
        require(entries.none { it.fileName == segment.name }) { "تمت إضافة هذا المقطع من قبل" }
        require(entries.size < MAX_SEGMENTS) { "وصل التسجيل إلى الحد الأقصى لعدد المقاطع" }
        val duration = readDurationMs(segment).takeIf { it in 1_000L..MAX_DURATION_MS }
            ?: measuredDurationMs
        require(duration in 1_000L..MAX_DURATION_MS) { "مدة مقطع الراوي غير صالحة" }
        entries += Entry(segment.name, duration)
        val totalDuration = totalDurationMs(entries.map { it.durationMs })
        writeEntries(directory, projectId, entries)

        val merged = File(directory, "$projectId-voiceover.m4a")
        mergeAacSegments(entries.map { File(directory, it.fileName) }, entries, merged)
        markMerged(directory, projectId, entries, merged)
        WolfVoiceMergeResult(merged, totalDuration, entries.size)
    }

    /** Rebuilds a combined take after an interrupted merge, using only registered complete parts. */
    fun recover(context: Context, projectId: String): WolfVoiceMergeResult? = synchronized(lock) {
        if (!validProjectId(projectId)) return@synchronized null
        val directory = voiceDirectory(context).canonicalFile
        val stored = readEntries(directory, projectId)
        val entries = registerCommittedOrphans(directory, projectId, stored)
        if (entries.size != stored.size) writeEntries(directory, projectId, entries)
        if (entries.isEmpty()) return@synchronized null
        val files = entries.map { File(directory, it.fileName) }
        if (files.any { !it.isFile || it.length() <= 1024L }) return@synchronized null
        val totalDuration = totalDurationMs(entries.map { it.durationMs })
        val merged = File(directory, "$projectId-voiceover.m4a")
        if (!isMergeCurrent(directory, projectId, entries, merged)) {
            mergeAacSegments(files, entries, merged)
            markMerged(directory, projectId, entries, merged)
        }
        WolfVoiceMergeResult(merged, totalDuration, entries.size)
    }

    fun clearProject(context: Context, projectId: String) = synchronized(lock) {
        if (!validProjectId(projectId)) return@synchronized
        val directory = voiceDirectory(context)
        readEntries(directory, projectId).forEach { File(directory, it.fileName).delete() }
        directory.listFiles()?.filter {
            it.name.startsWith("$projectId-segment-") &&
                (it.name.endsWith(".m4a") || it.name.endsWith(".importing"))
        }?.forEach { it.delete() }
        listOf(
            manifestFile(directory, projectId),
            File(directory, "$projectId-segments.json.tmp"),
            File(directory, "$projectId-voiceover.m4a"),
            File(directory, "$projectId-voiceover.m4a.pending"),
            File(directory, "$projectId-voiceover.m4a.bak")
        ).forEach { it.delete() }
    }

    internal fun segmentFileName(projectId: String, index: Int): String {
        require(validProjectId(projectId) && index in 1..MAX_SEGMENTS)
        return "$projectId-segment-${index.toString().padStart(4, '0')}.m4a"
    }

    internal fun mergeStateMatches(
        expectedSegmentCount: Int,
        mergedSegmentCount: Int,
        expectedFileLength: Long,
        actualFileLength: Long,
        isValidAac: Boolean
    ): Boolean = isValidAac && expectedSegmentCount > 0 &&
        mergedSegmentCount == expectedSegmentCount &&
        expectedFileLength > 1024L && expectedFileLength == actualFileLength

    internal fun canAppendDurationMs(existing: List<Long>, candidate: Long): Boolean {
        if (candidate !in 1L..MAX_DURATION_MS) return false
        var total = 0L
        for (duration in existing) {
            if (duration !in 1L..MAX_DURATION_MS || total > MAX_DURATION_MS - duration) return false
            total += duration
        }
        return total <= MAX_DURATION_MS - candidate
    }

    internal fun totalDurationMs(durations: List<Long>): Long {
        require(durations.isNotEmpty() && durations.all { it in 1L..MAX_DURATION_MS }) {
            "مدد مقاطع الراوي غير صالحة"
        }
        val total = durations.fold(0L) { sum, value ->
            require(sum <= MAX_DURATION_MS - value) { "تجاوز التسجيل الحد الأقصى البالغ 30 دقيقة" }
            sum + value
        }
        return total
    }

    private data class Entry(val fileName: String, val durationMs: Long)

    /**
     * A recorder may finish and atomically rename its M4A just before the app process is killed,
     * before the UI worker can append it to the manifest. Register only contiguous, valid AAC
     * files with the next expected segment number; ignore incomplete temporary/orphan files.
     */
    private fun registerCommittedOrphans(
        directory: File,
        projectId: String,
        existing: List<Entry>
    ): List<Entry> {
        val result = existing.toMutableList()
        while (result.size < MAX_SEGMENTS) {
            val candidate = File(directory, segmentFileName(projectId, result.size + 1))
            if (!candidate.isFile || !isValidAac(candidate)) break
            val duration = readDurationMs(candidate)
            if (duration !in 1_000L..MAX_DURATION_MS ||
                !canAppendDurationMs(result.map(Entry::durationMs), duration)
            ) break
            result += Entry(candidate.name, duration)
        }
        return result
    }

    private fun mergeAacSegments(files: List<File>, entries: List<Entry>, output: File) {
        require(files.isNotEmpty() && files.size == entries.size)
        val pending = File(output.parentFile, output.name + ".pending")
        val backup = File(output.parentFile, output.name + ".bak")
        pending.delete()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var succeeded = false
        try {
            var referenceFormat: MediaFormat? = null
            var outputTrack = -1
            muxer = MediaMuxer(pending.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var timelineOffsetUs = 0L
            var lastPresentationTimeUs = -1L

            files.forEachIndexed { index, file ->
                require(file.isFile && file.length() > 1024L) { "أحد مقاطع الراوي مفقود أو تالف" }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    val track = (0 until extractor.trackCount).firstOrNull { trackIndex ->
                        extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME)
                            ?.startsWith("audio/") == true
                    } ?: throw IllegalStateException("أحد مقاطع الراوي لا يحتوي مسارًا صوتيًا")
                    val format = extractor.getTrackFormat(track)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    require(mime == "audio/mp4a-latm" || mime == "audio/aac") {
                        "مقاطع الراوي يجب أن تكون AAC داخل M4A"
                    }
                    val reference = referenceFormat
                    if (reference == null) {
                        referenceFormat = format
                        outputTrack = muxer.addTrack(format)
                        muxer.start()
                        muxerStarted = true
                    } else {
                        require(
                            mime == reference.getString(MediaFormat.KEY_MIME) &&
                                formatInt(format, MediaFormat.KEY_SAMPLE_RATE) ==
                                formatInt(reference, MediaFormat.KEY_SAMPLE_RATE) &&
                                formatInt(format, MediaFormat.KEY_CHANNEL_COUNT) ==
                                formatInt(reference, MediaFormat.KEY_CHANNEL_COUNT)
                        ) { "إعدادات الصوت بين المقاطع غير متطابقة" }
                    }

                    extractor.selectTrack(track)
                    val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceIn(64 * 1024, 4 * 1024 * 1024)
                    } else 1024 * 1024
                    val buffer = ByteBuffer.allocateDirect(maxInput)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val flags = extractor.sampleFlags
                        require(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                            "لا يمكن دمج مقطع صوت محمي بتشفير"
                        }
                        require(flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) {
                            "يحتوي مقطع الصوت على إطار مجزأ غير مدعوم"
                        }
                        val sourcePts = extractor.sampleTime
                        if (sourcePts >= 0L) {
                            val candidatePts = timelineOffsetUs + sourcePts
                            val pts = if (candidatePts <= lastPresentationTimeUs) {
                                lastPresentationTimeUs + 1L
                            } else candidatePts
                            info.offset = 0
                            info.size = size
                            info.presentationTimeUs = pts
                            info.flags = if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                                MediaCodec.BUFFER_FLAG_KEY_FRAME
                            } else 0
                            buffer.position(0)
                            buffer.limit(size)
                            muxer.writeSampleData(outputTrack, buffer, info)
                            lastPresentationTimeUs = pts
                        }
                        if (!extractor.advance()) break
                    }
                } finally {
                    runCatching { extractor.release() }
                }
                timelineOffsetUs += entries[index].durationMs * 1000L
            }

            require(muxerStarted) { "لم يتم العثور على مقاطع صوت صالحة" }
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null
            require(pending.isFile && pending.length() > 1024L) { "ملف الراوي المدمج فارغ" }
            if (backup.exists()) backup.delete()
            if (output.exists()) Os.rename(output.absolutePath, backup.absolutePath)
            try {
                Os.rename(pending.absolutePath, output.absolutePath)
            } catch (error: Exception) {
                if (backup.exists()) Os.rename(backup.absolutePath, output.absolutePath)
                throw error
            }
            backup.delete()
            succeeded = true
        } finally {
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            if (!succeeded) pending.delete()
        }
    }

    private fun isValidAac(file: File): Boolean {
        if (!file.isFile || file.length() <= 1024L) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        val metadataDuration = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
        if (metadataDuration > 0L) return metadataDuration

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return 0L
            val format = extractor.getTrackFormat(track)
            val formatDurationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                (format.getLong(MediaFormat.KEY_DURATION) / 1000L).coerceAtLeast(0L)
            } else 0L
            if (formatDurationMs > 0L) return formatDurationMs
            extractor.selectTrack(track)
            var lastSampleUs = -1L
            while (extractor.sampleTime >= 0L) {
                lastSampleUs = extractor.sampleTime
                if (!extractor.advance()) break
            }
            if (lastSampleUs >= 0L) lastSampleUs / 1000L + 24L else 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun readEntries(directory: File, projectId: String): List<Entry> {
        val manifest = manifestFile(directory, projectId)
        if (!manifest.isFile) return emptyList()
        return runCatching {
            val json = JSONObject(manifest.readText(Charsets.UTF_8))
            require(json.optString("projectId") == projectId)
            val array = json.optJSONArray("segments") ?: JSONArray()
            require(array.length() <= MAX_SEGMENTS)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val name = item.optString("fileName")
                    val duration = item.optLong("durationMs", 0L)
                    require(name == segmentFileName(projectId, index + 1))
                    require(duration in 1L..MAX_DURATION_MS)
                    add(Entry(name, duration))
                }
            }.also { totalDurationMs(it.map(Entry::durationMs)) }
        }.getOrElse { emptyList() }
    }

    private fun writeEntries(directory: File, projectId: String, entries: List<Entry>) {
        val json = JSONObject()
            .put("projectId", projectId)
            .put("segments", JSONArray().apply {
                entries.forEach { put(JSONObject().put("fileName", it.fileName).put("durationMs", it.durationMs)) }
            })
            .put("mergedSegmentCount", 0)
            .put("mergedFileLength", 0L)
        val target = manifestFile(directory, projectId)
        val temporary = File(directory, "$projectId-segments.json.tmp")
        java.io.FileOutputStream(temporary).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Os.rename(temporary.absolutePath, target.absolutePath)
    }

    private fun markMerged(
        directory: File,
        projectId: String,
        entries: List<Entry>,
        merged: File
    ) {
        val json = JSONObject()
            .put("projectId", projectId)
            .put("segments", JSONArray().apply {
                entries.forEach { put(JSONObject().put("fileName", it.fileName).put("durationMs", it.durationMs)) }
            })
            .put("mergedSegmentCount", entries.size)
            .put("mergedFileLength", merged.length())
        writeManifest(directory, projectId, json)
    }

    private fun isMergeCurrent(
        directory: File,
        projectId: String,
        entries: List<Entry>,
        merged: File
    ): Boolean {
        if (!isValidAac(merged)) return false
        return runCatching {
            val json = JSONObject(manifestFile(directory, projectId).readText(Charsets.UTF_8))
            mergeStateMatches(
                expectedSegmentCount = entries.size,
                mergedSegmentCount = json.optInt("mergedSegmentCount", 0),
                expectedFileLength = json.optLong("mergedFileLength", -1L),
                actualFileLength = merged.length(),
                isValidAac = true
            )
        }.getOrDefault(false)
    }

    private fun writeManifest(directory: File, projectId: String, json: JSONObject) {
        val target = manifestFile(directory, projectId)
        val temporary = File(directory, "$projectId-segments.json.tmp")
        java.io.FileOutputStream(temporary).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Os.rename(temporary.absolutePath, target.absolutePath)
    }

    private fun formatInt(format: MediaFormat, key: String): Int =
        if (format.containsKey(key)) format.getInteger(key) else -1

    private fun manifestFile(directory: File, projectId: String) =
        File(directory, "$projectId-segments.json")

    private fun voiceDirectory(context: Context) = File(context.filesDir, DIRECTORY)

    private fun validProjectId(value: String) =
        value.isNotBlank() && value.length <= 80 && value.matches(Regex("[A-Za-z0-9-]+"))
}
