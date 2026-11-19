package com.wolfcodes.offline

import android.content.Context
import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Plans a long film as bounded, scene-aligned render chapters and persists safe checkpoints. */
object WolfLongFilmEngine {
    const val DEFAULT_CHAPTER_SECONDS = 300

    data class SceneSlice(
        val sceneIndex: Int,
        val startFrame: Int,
        val frameCount: Int
    )

    data class ChapterPlan(
        val number: Int,
        val slices: List<SceneSlice>,
        val bodyFrameCount: Int,
        val introFrameCount: Int
    ) {
        val totalFrameCount: Int get() = bodyFrameCount + introFrameCount
        val firstScene: Int get() = slices.firstOrNull()?.sceneIndex ?: 0
        val lastSceneExclusive: Int get() = (slices.lastOrNull()?.sceneIndex ?: -1) + 1
    }

    enum class ChapterStatus { PENDING, RUNNING, COMPLETED, RETRYABLE_FAILURE }

    data class Checkpoint(
        val plan: ChapterPlan,
        val status: ChapterStatus = ChapterStatus.PENDING,
        val attempt: Int = 0,
        val artifactPath: String? = null,
        val lastError: String? = null
    )

    data class Session(
        val projectId: String,
        val fingerprint: String,
        val framesPerSecond: Int,
        val totalFrames: Int,
        val chapters: List<Checkpoint>,
        val updatedAt: Long
    ) {
        val completedCount: Int get() = chapters.count { it.status == ChapterStatus.COMPLETED }
        val allCompleted: Boolean get() = chapters.isNotEmpty() && completedCount == chapters.size
    }

    /**
     * Splits frame budgets without dropping frames. Scene slices preserve story order and
     * allow a single unusually long scene to cross a chapter boundary safely.
     */
    fun planChapters(
        framesPerScene: List<Int>,
        framesPerSecond: Int = 12,
        introFrames: Int = 0,
        maxChapterSeconds: Int = DEFAULT_CHAPTER_SECONDS
    ): List<ChapterPlan> {
        val fps = framesPerSecond.coerceIn(1, 60)
        val chapterLimit = maxChapterSeconds.coerceAtLeast(1) * fps
        val safeIntro = introFrames.coerceIn(0, (chapterLimit - 1).coerceAtLeast(0))
        val slicesByChapter = mutableListOf<MutableList<SceneSlice>>()
        val bodyFramesByChapter = mutableListOf<Int>()
        var currentIndex = -1

        fun beginChapter() {
            slicesByChapter.add(mutableListOf())
            bodyFramesByChapter.add(0)
            currentIndex++
        }

        if (framesPerScene.none { it > 0 }) return emptyList()
        beginChapter()
        framesPerScene.forEachIndexed { sceneIndex, rawFrames ->
            var remaining = rawFrames.coerceAtLeast(0)
            var sceneOffset = 0
            while (remaining > 0) {
                val capacity = if (currentIndex == 0) chapterLimit - safeIntro else chapterLimit
                val available = capacity - bodyFramesByChapter[currentIndex]
                if (available <= 0) {
                    beginChapter()
                    continue
                }
                val count = minOf(remaining, available)
                slicesByChapter[currentIndex].add(SceneSlice(sceneIndex, sceneOffset, count))
                bodyFramesByChapter[currentIndex] += count
                sceneOffset += count
                remaining -= count
                if (remaining > 0) beginChapter()
            }
        }

        return slicesByChapter.mapIndexedNotNull { index, slices ->
            if (slices.isEmpty()) return@mapIndexedNotNull null
            ChapterPlan(
                number = index + 1,
                slices = slices.toList(),
                bodyFrameCount = bodyFramesByChapter[index],
                introFrameCount = if (index == 0) safeIntro else 0
            )
        }
    }

    fun fingerprint(
        story: String,
        framesPerScene: List<Int>,
        fps: Int,
        width: Int,
        height: Int,
        introFrames: Int,
        typographyEnabled: Boolean,
        mediaInputs: String = ""
    ): String {
        val canonical = buildString {
            append(story).append('|')
            append(framesPerScene.joinToString(",")).append('|')
            append(fps).append('|').append(width).append('x').append(height).append('|')
            append(introFrames).append('|').append(typographyEnabled).append('|')
            append(mediaInputs)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun chapterStatusAfterRetry(checkpoint: Checkpoint): Checkpoint {
        require(checkpoint.status == ChapterStatus.RETRYABLE_FAILURE ||
            checkpoint.status == ChapterStatus.RUNNING) {
            "Only a failed or interrupted chapter can be retried"
        }
        return checkpoint.copy(
            status = ChapterStatus.PENDING,
            artifactPath = null,
            lastError = null
        )
    }
}

/** Atomic per-project checkpoint storage for chapter MP4 files. */
object WolfLongFilmCheckpointStore {
    private const val FOLDER = "wolf_long_film_chapters"
    private val lock = Any()

    fun begin(
        context: Context,
        projectId: String,
        fingerprint: String,
        framesPerSecond: Int,
        totalFrames: Int,
        plans: List<WolfLongFilmEngine.ChapterPlan>
    ): WolfLongFilmEngine.Session = synchronized(lock) {
        require(validProjectId(projectId)) { "معرّف المشروع غير صالح" }
        val previous = read(context, projectId)
        val samePlan = previous != null &&
            previous.fingerprint == fingerprint &&
            previous.framesPerSecond == framesPerSecond &&
            previous.totalFrames == totalFrames &&
            previous.chapters.map { it.plan } == plans

        if (!samePlan) chapterDirectory(context, projectId).deleteRecursively()

        val chapters = plans.mapIndexed { index, plan ->
            val old = if (samePlan) previous?.chapters?.getOrNull(index) else null
            val reusable = old?.status == WolfLongFilmEngine.ChapterStatus.COMPLETED &&
                old.artifactPath?.let { safeArtifact(context, projectId, it)?.let { file -> file.isFile && file.length() > 0L } } == true
            if (reusable) {
                old!!
            } else {
                old?.artifactPath?.let { safeArtifact(context, projectId, it)?.delete() }
                WolfLongFilmEngine.Checkpoint(plan, attempt = old?.attempt ?: 0)
            }
        }
        val session = WolfLongFilmEngine.Session(
            projectId = projectId,
            fingerprint = fingerprint,
            framesPerSecond = framesPerSecond,
            totalFrames = totalFrames,
            chapters = chapters,
            updatedAt = System.currentTimeMillis()
        )
        write(context, session)
        session
    }

    fun chapterFile(
        context: Context,
        projectId: String,
        fingerprint: String,
        chapterNumber: Int
    ): File {
        require(validProjectId(projectId))
        val directory = chapterDirectory(context, projectId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد فصول الفيلم")
        }
        val safeFingerprint = fingerprint.filter { it.isLetterOrDigit() }.take(20)
        return File(directory, "chapter_${safeFingerprint}_${chapterNumber.toString().padStart(3, '0')}.mp4")
    }

    fun markRunning(context: Context, projectId: String, chapterNumber: Int) = update(context, projectId) { session ->
        val index = session.chapters.indexOfFirst { it.plan.number == chapterNumber }
        require(index >= 0) { "الفصل غير موجود" }
        val updated = session.chapters.toMutableList()
        val current = updated[index]
        require(current.status != WolfLongFilmEngine.ChapterStatus.COMPLETED) {
            "الفصل المكتمل لا يحتاج إعادة تشغيل"
        }
        updated[index] = current.copy(
            status = WolfLongFilmEngine.ChapterStatus.RUNNING,
            attempt = current.attempt + 1,
            artifactPath = null,
            lastError = null
        )
        session.copy(chapters = updated)
    }

    fun markCompleted(context: Context, projectId: String, chapterNumber: Int, artifact: File) =
        update(context, projectId) { session ->
            val safe = safeArtifact(context, projectId, artifact.absolutePath)
                ?: throw IllegalArgumentException("مسار ملف الفصل غير آمن")
            require(safe.isFile && safe.length() > 0L) { "ملف الفصل غير موجود أو فارغ" }
            val index = session.chapters.indexOfFirst { it.plan.number == chapterNumber }
            require(index >= 0) { "الفصل غير موجود" }
            val updated = session.chapters.toMutableList()
            updated[index] = updated[index].copy(
                status = WolfLongFilmEngine.ChapterStatus.COMPLETED,
                artifactPath = safe.absolutePath,
                lastError = null
            )
            session.copy(chapters = updated)
        }

    fun markFailed(context: Context, projectId: String, chapterNumber: Int, error: String) =
        update(context, projectId) { session ->
            val index = session.chapters.indexOfFirst { it.plan.number == chapterNumber }
            require(index >= 0) { "الفصل غير موجود" }
            val updated = session.chapters.toMutableList()
            val current = updated[index]
            updated[index] = current.copy(
                status = WolfLongFilmEngine.ChapterStatus.RETRYABLE_FAILURE,
                lastError = error.take(240)
            )
            session.copy(chapters = updated)
        }

    fun load(context: Context, projectId: String): WolfLongFilmEngine.Session? = synchronized(lock) {
        if (!validProjectId(projectId)) null else read(context, projectId)
    }

    fun clear(context: Context, projectId: String) = synchronized(lock) {
        if (!validProjectId(projectId)) return@synchronized
        val session = read(context, projectId)
        session?.chapters?.forEach { checkpoint ->
            checkpoint.artifactPath?.let { safeArtifact(context, projectId, it)?.delete() }
        }
        chapterDirectory(context, projectId).deleteRecursively()
        sessionFile(context, projectId).delete()
    }

    private fun update(
        context: Context,
        projectId: String,
        transform: (WolfLongFilmEngine.Session) -> WolfLongFilmEngine.Session
    ): WolfLongFilmEngine.Session = synchronized(lock) {
        require(validProjectId(projectId))
        val current = read(context, projectId) ?: throw IllegalStateException("لا توجد جلسة فصول محفوظة")
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        write(context, updated)
        updated
    }

    private fun read(context: Context, projectId: String): WolfLongFilmEngine.Session? {
        val file = sessionFile(context, projectId)
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val array = json.getJSONArray("chapters")
            val chapters = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val planJson = item.getJSONObject("plan")
                val slicesJson = planJson.getJSONArray("slices")
                val slices = (0 until slicesJson.length()).map { sliceIndex ->
                    val slice = slicesJson.getJSONObject(sliceIndex)
                    WolfLongFilmEngine.SceneSlice(
                        sceneIndex = slice.getInt("sceneIndex"),
                        startFrame = slice.getInt("startFrame"),
                        frameCount = slice.getInt("frameCount")
                    )
                }
                WolfLongFilmEngine.Checkpoint(
                    plan = WolfLongFilmEngine.ChapterPlan(
                        number = planJson.getInt("number"),
                        slices = slices,
                        bodyFrameCount = planJson.getInt("bodyFrameCount"),
                        introFrameCount = planJson.optInt("introFrameCount", 0)
                    ),
                    status = runCatching {
                        WolfLongFilmEngine.ChapterStatus.valueOf(item.optString("status", "PENDING"))
                    }.getOrDefault(WolfLongFilmEngine.ChapterStatus.PENDING),
                    attempt = item.optInt("attempt", 0),
                    artifactPath = item.optString("artifactPath").takeIf { it.isNotBlank() && it != "null" },
                    lastError = item.optString("lastError").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            WolfLongFilmEngine.Session(
                projectId = json.getString("projectId"),
                fingerprint = json.getString("fingerprint"),
                framesPerSecond = json.getInt("framesPerSecond"),
                totalFrames = json.getInt("totalFrames"),
                chapters = chapters,
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    private fun write(context: Context, session: WolfLongFilmEngine.Session) {
        val directory = File(context.filesDir, FOLDER)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد سجل فصول الفيلم")
        }
        val chapters = JSONArray()
        session.chapters.forEach { checkpoint ->
            val slices = JSONArray()
            checkpoint.plan.slices.forEach { slice ->
                slices.put(JSONObject().apply {
                    put("sceneIndex", slice.sceneIndex)
                    put("startFrame", slice.startFrame)
                    put("frameCount", slice.frameCount)
                })
            }
            val plan = JSONObject().apply {
                put("number", checkpoint.plan.number)
                put("slices", slices)
                put("bodyFrameCount", checkpoint.plan.bodyFrameCount)
                put("introFrameCount", checkpoint.plan.introFrameCount)
            }
            chapters.put(JSONObject().apply {
                put("plan", plan)
                put("status", checkpoint.status.name)
                put("attempt", checkpoint.attempt)
                put("artifactPath", checkpoint.artifactPath ?: JSONObject.NULL)
                put("lastError", checkpoint.lastError ?: JSONObject.NULL)
            })
        }
        val json = JSONObject().apply {
            put("projectId", session.projectId)
            put("fingerprint", session.fingerprint)
            put("framesPerSecond", session.framesPerSecond)
            put("totalFrames", session.totalFrames)
            put("chapters", chapters)
            put("updatedAt", session.updatedAt)
        }
        val destination = sessionFile(context, session.projectId)
        val temporary = File(directory, session.projectId + ".json.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Os.rename(temporary.absolutePath, destination.absolutePath)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("تعذر حفظ نقطة استئناف الفيلم الطويل", error)
        }
    }

    private fun safeArtifact(context: Context, projectId: String, path: String): File? {
        return runCatching {
            val root = chapterDirectory(context, projectId).canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == root) file else null
        }.getOrNull()
    }

    private fun chapterDirectory(context: Context, projectId: String) =
        File(File(context.filesDir, FOLDER), projectId)

    private fun sessionFile(context: Context, projectId: String) =
        File(File(context.filesDir, FOLDER), projectId + ".json")

    private fun validProjectId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9-]{16,64}"))
}
