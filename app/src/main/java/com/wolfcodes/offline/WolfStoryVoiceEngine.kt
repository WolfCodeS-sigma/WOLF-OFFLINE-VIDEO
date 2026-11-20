package com.wolfcodes.offline

import android.content.Context
import android.media.MediaRecorder
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.system.Os
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class WolfStoryVoiceSession(
    val projectId: String,
    val sourceStory: String,
    val draftStory: String,
    val finalStory: String,
    val scriptApproved: Boolean,
    val voiceFilePath: String?,
    val audioDurationMs: Long,
    val recordingStoryHash: String?,
    val voiceApproved: Boolean,
    val takeNumber: Int,
    val updatedAt: Long
)

object WolfStoryVoiceStateMachine {
    fun storyFingerprint(story: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(story.trim().take(60000).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun edit(session: WolfStoryVoiceSession, draftStory: String, finalStory: String, now: Long): WolfStoryVoiceSession {
        val normalized = finalStory.trim().take(60000)
        val changed = normalized != session.finalStory
        val recordingMatches = session.recordingStoryHash != null &&
            session.recordingStoryHash == storyFingerprint(normalized)
        val invalidateTake = changed || (session.voiceFilePath != null && !recordingMatches)
        return session.copy(
            draftStory = draftStory.take(60000),
            finalStory = normalized,
            scriptApproved = if (changed) false else session.scriptApproved,
            voiceFilePath = if (invalidateTake) null else session.voiceFilePath,
            audioDurationMs = if (invalidateTake) 0L else session.audioDurationMs,
            recordingStoryHash = if (invalidateTake) null else session.recordingStoryHash,
            voiceApproved = if (invalidateTake) false else session.voiceApproved,
            updatedAt = now
        )
    }

    fun approveScript(session: WolfStoryVoiceSession, finalStory: String, now: Long): WolfStoryVoiceSession {
        val normalized = finalStory.trim()
        require(normalized.isNotBlank()) { "لا يمكن اعتماد قصة فارغة" }
        val edited = edit(session, session.draftStory, normalized, now)
        return edited.copy(scriptApproved = true, updatedAt = now)
    }

    fun saveRecording(session: WolfStoryVoiceSession, path: String, durationMs: Long, now: Long): WolfStoryVoiceSession {
        require(session.scriptApproved) { "يجب اعتماد نص القصة قبل التسجيل" }
        require(path.isNotBlank()) { "مسار التسجيل غير محدد" }
        require(durationMs in 1000L..1_800_000L) { "مدة التسجيل يجب أن تكون بين ثانية و30 دقيقة" }
        return session.copy(
            voiceFilePath = path, audioDurationMs = durationMs,
            recordingStoryHash = storyFingerprint(session.finalStory),
            voiceApproved = false, takeNumber = session.takeNumber + 1, updatedAt = now
        )
    }

    fun approveRecording(session: WolfStoryVoiceSession, fileExists: Boolean, fileLength: Long, now: Long): WolfStoryVoiceSession {
        require(session.scriptApproved) { "اعتمد القصة أولًا" }
        require(!session.voiceFilePath.isNullOrBlank() && fileExists && fileLength > 1024L) {
            "لا يوجد تسجيل صالح للمراجعة"
        }
        require(session.audioDurationMs in 1000L..1_800_000L) { "مدة التسجيل غير صالحة" }
        require(session.recordingStoryHash == storyFingerprint(session.finalStory)) {
            "هذا التسجيل لا يطابق نسخة القصة الحالية؛ أعد التسجيل"
        }
        return session.copy(voiceApproved = true, updatedAt = now)
    }

    /**
     * Invalidates every approval and take when the owning project's story changes.
     * This is intentionally unconditional: project text and session.finalStory can drift
     * independently after interrupted saves or edits made in another screen.
     */
    fun invalidateForStoryChange(
        session: WolfStoryVoiceSession,
        newStory: String,
        now: Long
    ): WolfStoryVoiceSession {
        val normalized = newStory.trim().take(60000)
        require(normalized.isNotBlank()) { "لا يمكن ربط جلسة الراوي بقصة فارغة" }
        return session.copy(
            draftStory = normalized,
            finalStory = normalized,
            scriptApproved = false,
            voiceFilePath = null,
            audioDurationMs = 0L,
            recordingStoryHash = null,
            voiceApproved = false,
            updatedAt = now
        )
    }

    fun beginAdditionalSegment(session: WolfStoryVoiceSession, now: Long): WolfStoryVoiceSession {
        require(session.scriptApproved) { "اعتمد القصة أولًا" }
        return session.copy(voiceApproved = false, updatedAt = now)
    }

    fun requestRepeat(session: WolfStoryVoiceSession, now: Long): WolfStoryVoiceSession {
        require(session.scriptApproved) { "اعتمد القصة أولًا" }
        return session.copy(
            voiceFilePath = null, audioDurationMs = 0L, recordingStoryHash = null,
            voiceApproved = false, updatedAt = now
        )
    }

    fun durationSecondsCeil(durationMs: Long): Int =
        ((durationMs.coerceAtLeast(0L) + 999L) / 1000L).toInt().coerceIn(0, 1800)
}

object WolfStoryVoiceStore {
    private const val DIRECTORY = "wolf_story_voice"
    private const val MAX_TEXT_CHARS = 60000
    private val lock = Any()

    fun initialize(context: Context, projectId: String, sourceStory: String): WolfStoryVoiceSession =
        synchronized(lock) {
            require(validProjectId(projectId)) { "معرّف المشروع غير صالح" }
            readUnlocked(context, projectId) ?: WolfStoryVoiceSession(
                projectId, sourceStory.take(MAX_TEXT_CHARS), sourceStory.take(MAX_TEXT_CHARS),
                sourceStory.trim().take(MAX_TEXT_CHARS), false, null, 0L, null, false, 0,
                System.currentTimeMillis()
            ).also { writeUnlocked(context, it) }
        }

    fun read(context: Context, projectId: String): WolfStoryVoiceSession? = synchronized(lock) {
        if (!validProjectId(projectId)) null else readUnlocked(context, projectId)
    }

    fun saveEditing(context: Context, projectId: String, draftStory: String, finalStory: String): WolfStoryVoiceSession =
        synchronized(lock) {
            val current = readUnlocked(context, projectId) ?: initialize(context, projectId, draftStory)
            val updated = WolfStoryVoiceStateMachine.edit(current, draftStory, finalStory, System.currentTimeMillis())
            writeUnlocked(context, updated)
            if (updated.voiceFilePath == null && current.voiceFilePath != null) File(current.voiceFilePath).delete()
            if (updated.finalStory != current.finalStory) WolfVoiceSegmentStore.clearProject(context, projectId)
            updated
        }

    fun approveScript(context: Context, projectId: String, finalStory: String): WolfStoryVoiceSession =
        synchronized(lock) {
            val current = readUnlocked(context, projectId) ?: throw IllegalStateException("مسودة القصة غير موجودة")
            val updated = WolfStoryVoiceStateMachine.approveScript(current, finalStory, System.currentTimeMillis())
            writeUnlocked(context, updated)
            if (updated.voiceFilePath == null && current.voiceFilePath != null) File(current.voiceFilePath).delete()
            if (updated.finalStory != current.finalStory) WolfVoiceSegmentStore.clearProject(context, projectId)
            updated
        }

    fun recordingTarget(context: Context, projectId: String): File = synchronized(lock) {
        require(validProjectId(projectId)) { "معرّف المشروع غير صالح" }
        val directory = voiceDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التسجيلات")
        File(directory, projectId + "-voiceover.m4a")
    }

    fun saveRecording(context: Context, projectId: String, file: File, durationMs: Long): WolfStoryVoiceSession =
        synchronized(lock) {
            val current = readUnlocked(context, projectId) ?: throw IllegalStateException("جلسة القصة غير موجودة")
            val expectedDirectory = voiceDirectory(context).canonicalFile
            val actualFile = file.canonicalFile
            require(actualFile.path.startsWith(expectedDirectory.path + File.separator)) {
                "يجب حفظ التسجيل داخل مجلد WOLF الخاص بالمشروع"
            }
            require(file.isFile && file.length() > 1024L) { "ملف التسجيل غير صالح أو فارغ" }
            val updated = WolfStoryVoiceStateMachine.saveRecording(current, actualFile.absolutePath, durationMs, System.currentTimeMillis())
            writeUnlocked(context, updated)
            updated
        }

    fun approveRecording(context: Context, projectId: String): WolfStoryVoiceSession? = synchronized(lock) {
        val current = readUnlocked(context, projectId) ?: return@synchronized null
        val file = current.voiceFilePath?.let(::File)
        val updated = runCatching {
            WolfStoryVoiceStateMachine.approveRecording(
                current, file?.isFile == true, file?.length() ?: 0L, System.currentTimeMillis()
            )
        }.getOrNull() ?: return@synchronized null
        writeUnlocked(context, updated)
        updated
    }

    fun invalidateForStoryChange(
        context: Context,
        projectId: String,
        newStory: String
    ): WolfStoryVoiceSession = synchronized(lock) {
        require(validProjectId(projectId)) { "معرّف المشروع غير صالح" }
        val current = readUnlocked(context, projectId)
            ?: WolfStoryVoiceSession(
                projectId, newStory.take(MAX_TEXT_CHARS), newStory.take(MAX_TEXT_CHARS),
                newStory.trim().take(MAX_TEXT_CHARS), false, null, 0L, null, false, 0,
                System.currentTimeMillis()
            )
        val updated = WolfStoryVoiceStateMachine.invalidateForStoryChange(
            current, newStory, System.currentTimeMillis()
        )
        writeUnlocked(context, updated)
        current.voiceFilePath?.let { File(it).delete() }
        WolfVoiceSegmentStore.clearProject(context, projectId)
        updated
    }

    fun beginAdditionalSegment(context: Context, projectId: String): WolfStoryVoiceSession =
        synchronized(lock) {
            val current = readUnlocked(context, projectId)
                ?: throw IllegalStateException("جلسة القصة غير موجودة")
            val updated = WolfStoryVoiceStateMachine.beginAdditionalSegment(
                current, System.currentTimeMillis()
            )
            writeUnlocked(context, updated)
            updated
        }

    fun restoreMergedSegments(
        context: Context,
        projectId: String,
        mergedFile: File,
        durationMs: Long
    ): WolfStoryVoiceSession? = synchronized(lock) {
        val current = readUnlocked(context, projectId) ?: return@synchronized null
        if (!current.scriptApproved || !mergedFile.isFile || durationMs !in 1000L..1_800_000L) {
            return@synchronized null
        }
        val directory = voiceDirectory(context).canonicalFile
        val actual = mergedFile.canonicalFile
        if (!actual.path.startsWith(directory.path + File.separator)) return@synchronized null
        val updated = current.copy(
            voiceFilePath = actual.absolutePath,
            audioDurationMs = durationMs,
            recordingStoryHash = WolfStoryVoiceStateMachine.storyFingerprint(current.finalStory),
            voiceApproved = false,
            updatedAt = System.currentTimeMillis()
        )
        writeUnlocked(context, updated)
        updated
    }

    fun requestRepeat(context: Context, projectId: String): WolfStoryVoiceSession? = synchronized(lock) {
        val current = readUnlocked(context, projectId) ?: return@synchronized null
        val updated = runCatching {
            WolfStoryVoiceStateMachine.requestRepeat(current, System.currentTimeMillis())
        }.getOrNull() ?: return@synchronized null
        writeUnlocked(context, updated)
        current.voiceFilePath?.let { File(it).delete() }
        WolfVoiceSegmentStore.clearProject(context, projectId)
        updated
    }

    fun clearProject(context: Context, projectId: String) = synchronized(lock) {
        if (!validProjectId(projectId)) return@synchronized
        readUnlocked(context, projectId)?.voiceFilePath?.let { File(it).delete() }
        WolfVoiceSegmentStore.clearProject(context, projectId)
        listOf(
            File(voiceDirectory(context), projectId + ".json"),
            File(voiceDirectory(context), projectId + ".json.bak"),
            File(voiceDirectory(context), projectId + ".json.tmp"),
            File(voiceDirectory(context), projectId + "-voiceover.m4a")
        ).forEach { if (it.exists()) it.delete() }
    }

    private fun readUnlocked(context: Context, projectId: String): WolfStoryVoiceSession? {
        if (!validProjectId(projectId)) return null
        val directory = voiceDirectory(context)
        val base = directory.canonicalFile
        val primary = File(directory, projectId + ".json")
        val backup = File(directory, projectId + ".json.bak")
        return sequenceOf(primary, backup).mapNotNull { file ->
            runCatching {
                if (!file.isFile) return@runCatching null
                val session = parse(file.readText(Charsets.UTF_8))
                val path = session.voiceFilePath ?: return@runCatching session
                val audio = File(path).canonicalFile
                if (audio.path.startsWith(base.path + File.separator)) session else null
            }.getOrNull()
        }.firstOrNull()
    }

    private fun writeUnlocked(context: Context, session: WolfStoryVoiceSession) {
        val directory = voiceDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد القصة والصوت")
        val primary = File(directory, session.projectId + ".json")
        val backup = File(directory, session.projectId + ".json.bak")
        val temporary = File(directory, session.projectId + ".json.tmp")
        val json = JSONObject()
            .put("projectId", session.projectId)
            .put("sourceStory", session.sourceStory.take(MAX_TEXT_CHARS))
            .put("draftStory", session.draftStory.take(MAX_TEXT_CHARS))
            .put("finalStory", session.finalStory.take(MAX_TEXT_CHARS))
            .put("scriptApproved", session.scriptApproved)
            .put("voiceFilePath", session.voiceFilePath ?: JSONObject.NULL)
            .put("audioDurationMs", session.audioDurationMs)
            .put("recordingStoryHash", session.recordingStoryHash ?: JSONObject.NULL)
            .put("voiceApproved", session.voiceApproved)
            .put("takeNumber", session.takeNumber)
            .put("updatedAt", session.updatedAt)
        FileOutputStream(temporary).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (primary.exists()) {
            if (backup.exists() && !backup.delete()) {
                temporary.delete()
                throw IllegalStateException("تعذر تدوير نسخة جلسة القصة والصوت")
            }
            if (!primary.renameTo(backup)) {
                temporary.delete()
                throw IllegalStateException("تعذر حفظ النسخة السابقة من جلسة القصة والصوت")
            }
        }
        if (!temporary.renameTo(primary)) {
            if (backup.exists()) backup.renameTo(primary)
            temporary.delete()
            throw IllegalStateException("تعذر حفظ جلسة القصة والصوت")
        }
        backup.delete()
    }

    private fun parse(raw: String): WolfStoryVoiceSession {
        val json = JSONObject(raw)
        val projectId = json.getString("projectId")
        require(validProjectId(projectId))
        val path = if (json.isNull("voiceFilePath")) null else json.optString("voiceFilePath").takeIf { it.isNotBlank() }
        return WolfStoryVoiceSession(
            projectId, json.optString("sourceStory", "").take(MAX_TEXT_CHARS),
            json.optString("draftStory", "").take(MAX_TEXT_CHARS),
            json.optString("finalStory", "").take(MAX_TEXT_CHARS),
            json.optBoolean("scriptApproved", false), path,
            json.optLong("audioDurationMs", 0L).coerceAtLeast(0L),
            if (json.isNull("recordingStoryHash")) null else json.optString("recordingStoryHash").takeIf { it.matches(Regex("[a-f0-9]{64}")) },
            json.optBoolean("voiceApproved", false) && path != null &&
                json.optString("recordingStoryHash") == WolfStoryVoiceStateMachine.storyFingerprint(
                    json.optString("finalStory", "")
                ),
            json.optInt("takeNumber", 0).coerceAtLeast(0),
            json.optLong("updatedAt", 0L)
        )
    }

    private fun voiceDirectory(context: Context) = File(context.filesDir, DIRECTORY)
    private fun validProjectId(value: String) =
        value.isNotBlank() && value.length <= 80 && value.matches(Regex("[A-Za-z0-9-]+"))
}

internal class WolfSilenceDetector(
    private val amplitudeThreshold: Int = 650,
    private val quietWindowMs: Long = 5000L
) {
    private var speechDetected = false
    private var lastVoiceAtMs = 0L

    fun sample(amplitude: Int, elapsedMs: Long): Boolean {
        if (amplitude >= amplitudeThreshold) {
            speechDetected = true
            lastVoiceAtMs = elapsedMs
        }
        return speechDetected && elapsedMs - lastVoiceAtMs >= quietWindowMs
    }

    fun hasDetectedSpeech(): Boolean = speechDetected
}

class WolfVoiceRecorder(context: Context, private val listener: Listener) {
    interface Listener {
        fun onProgress(elapsedMs: Long, speechDetected: Boolean)
        fun onSaved(file: File, durationMs: Long, automaticStop: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var targetFile: File? = null
    private var temporaryFile: File? = null
    private var startedAtMs = 0L
    private var stopping = false
    private var detector = WolfSilenceDetector()

    val isRecording: Boolean get() = recorder != null && !stopping

    private val poller = object : Runnable {
        override fun run() {
            val active = recorder ?: return
            if (stopping) return
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            val amplitude = runCatching { active.maxAmplitude }.getOrDefault(0)
            val quietTailReached = detector.sample(amplitude, elapsed)
            listener.onProgress(elapsed, detector.hasDetectedSpeech())
            when {
                quietTailReached -> finish(true)
                elapsed >= MAX_RECORDING_MS -> finish(true)
                else -> handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun start(destination: File): Boolean {
        if (isRecording) return false
        return runCatching {
            val parent = destination.parentFile ?: throw IllegalArgumentException("مجلد التسجيل غير محدد")
            if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التسجيل")
            val temporary = File(parent, destination.nameWithoutExtension + ".recording.tmp.m4a")
            temporary.delete()
            temporaryFile = temporary
            targetFile = destination

            @Suppress("DEPRECATION")
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioSamplingRate(44100)
            created.setAudioEncodingBitRate(128000)
            created.setOutputFile(temporary.absolutePath)
            created.prepare()
            created.start()

            recorder = created
            startedAtMs = SystemClock.elapsedRealtime()
            stopping = false
            detector = WolfSilenceDetector()
            handler.postDelayed(poller, POLL_INTERVAL_MS)
            listener.onProgress(0L, false)
            true
        }.getOrElse {
            releaseRecorder()
            temporaryFile?.delete()
            temporaryFile = null
            targetFile = null
            listener.onError(it.message ?: "تعذر بدء التسجيل الصوتي")
            false
        }
    }

    fun stopByUser() = finish(false)

    fun cancel() {
        handler.removeCallbacks(poller)
        stopping = true
        releaseRecorder()
        temporaryFile?.delete()
        targetFile = null
        temporaryFile = null
        stopping = false
    }

    private fun finish(automaticStop: Boolean) {
        if (stopping || recorder == null) return
        stopping = true
        handler.removeCallbacks(poller)
        val duration = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        val active = recorder
        recorder = null
        var stopError: Exception? = null
        try {
            active?.stop()
        } catch (error: Exception) {
            stopError = error
        } finally {
            try { active?.reset() } catch (_: Exception) { }
            try { active?.release() } catch (_: Exception) { }
        }

        val target = targetFile
        val temporary = temporaryFile
        targetFile = null
        temporaryFile = null
        stopping = false

        if (stopError != null || duration < MIN_RECORDING_MS ||
            temporary == null || !temporary.isFile || temporary.length() <= MIN_FILE_BYTES || target == null) {
            temporary?.delete()
            listener.onError(stopError?.message ?: "التسجيل قصير جدًا أو لم ينتج ملفًا صوتيًا صالحًا. أعد المحاولة.")
            return
        }

        runCatching {
            Os.rename(temporary.absolutePath, target.absolutePath)
            if (!target.isFile || target.length() <= MIN_FILE_BYTES) {
                throw IllegalStateException("ملف التسجيل النهائي غير صالح")
            }
            val measuredDuration = readAudioDurationMs(target).takeIf {
                it in MIN_RECORDING_MS..MAX_RECORDING_MS
            } ?: duration
            listener.onSaved(target, measuredDuration, automaticStop)
        }.onFailure { error ->
            temporary.delete()
            listener.onError(error.message ?: "تعذر حفظ التسجيل الصوتي")
        }
    }

    private fun readAudioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun releaseRecorder() {
        val active = recorder
        recorder = null
        try { active?.reset() } catch (_: Exception) { }
        try { active?.release() } catch (_: Exception) { }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 400L
        private const val MIN_RECORDING_MS = 1000L
        private const val MIN_FILE_BYTES = 1024L
        private const val MAX_RECORDING_MS = 1_800_000L
    }
}
