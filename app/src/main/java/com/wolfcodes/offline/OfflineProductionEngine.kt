package com.wolfcodes.offline

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.provider.MediaStore
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaExtractor
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/** Local, network-independent frame-to-MP4 fallback. Produces silent video. */
object OfflineProductionEngine {
    private const val VIDEO_BITRATE_BPS = 1_600_000L
    private const val CONSERVATIVE_AUDIO_BITRATE_BPS = 512_000L
    private const val EXPORT_SAFETY_MARGIN_BYTES = 128L * 1024L * 1024L

    /** Estimates peak free space while temporary MP4 and final output copies overlap. */
    internal fun estimatePeakStorageBytes(
        durationSeconds: Int,
        hasAudio: Boolean,
        usesChapteredRendering: Boolean = durationSeconds > WolfLongFilmEngine.DEFAULT_CHAPTER_SECONDS
    ): Long {
        val safeDuration = durationSeconds.coerceIn(1, 1803).toLong()
        val oneVideoCopy = safeDuration * VIDEO_BITRATE_BPS / 8L
        val videoCopies = if (usesChapteredRendering) 3L else 2L
        val audioAllowance = if (hasAudio) safeDuration * CONSERVATIVE_AUDIO_BITRATE_BPS / 8L else 0L
        return oneVideoCopy * videoCopies + audioAllowance + EXPORT_SAFETY_MARGIN_BYTES
    }

    data class Result(
        val scenes: Int,
        val frames: Int,
        val savedLocation: String,
        val mediaUri: String? = null,
        val durationSeconds: Int = 0,
        val framesPerSecond: Int = 12,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val usedReducedQualityFallback: Boolean = false
    )

    fun splitStory(story: String): List<String> {
        return OfflineStoryEngine.split(story)
    }

    fun render(
        context: Context,
        story: String,
        seconds: Int,
        imageUris: List<Uri> = emptyList(),
        audioUri: Uri? = null,
        aspectRatio: String = "9:16",
        generateSoundEffects: Boolean = false,
        includeWatermark: Boolean = false,
        includeIntro: Boolean = false,
        onSceneProgress: ((Int, Int) -> Unit)? = null,
        sceneImageAssignments: Map<Int, Uri> = emptyMap(),
        soundEffectGain: Double = 1.0,
        audioDurationIsAuthoritative: Boolean = false,
        audioDurationMs: Long? = null,
        enableHorrorTypography: Boolean = true,
        projectId: String? = null,
        onChapterProgress: ((Int, Int, String) -> Unit)? = null,
        chapterDurationLimitSeconds: Int = WolfLongFilmEngine.DEFAULT_CHAPTER_SECONDS
    ): Result {
        val dimensions = outputDimensions(aspectRatio)
        val w = dimensions.first
        val h = dimensions.second
        val fps = 12
        val requestedTotal = seconds.coerceIn(1, 1800)
        val useNarrationTiming = audioDurationIsAuthoritative && audioUri != null &&
            audioDurationMs != null && audioDurationMs in 1000L..1_800_000L
        val introSeconds = if (includeIntro) {
            if (useNarrationTiming) minOf(3, (requestedTotal - 1).coerceAtLeast(0)) else 3
        } else 0
        val introFrames = introSeconds * fps
        val bodySeconds = if (useNarrationTiming) (requestedTotal - introSeconds).coerceAtLeast(1) else requestedTotal
        val rawScenes = splitStory(story)
        val scenes = if (useNarrationTiming) {
            StoryDirectorEngine.fitSceneTextsToDuration(rawScenes, bodySeconds)
        } else rawScenes
        val total = if (useNarrationTiming) bodySeconds else max(requestedTotal, scenes.size * 3).coerceAtMost(1800)
        val totalVideoSeconds = if (useNarrationTiming) requestedTotal else total + introSeconds
        val totalFrames = total * fps
        val minimumFrames = if (useNarrationTiming) fps else fps * 3
        val framesPerScene = OfflineMotionEngine.allocateFrames(scenes, totalFrames, minimumFrames)
        val typographyCues = WolfHorrorTypographyEngine.plan(
            scenes, framesPerScene, fps, enabled = enableHorrorTypography
        )
        val safeChapterDuration = chapterDurationLimitSeconds.coerceIn(
            1, WolfLongFilmEngine.DEFAULT_CHAPTER_SECONDS
        )
        val chapterPlans = if (totalVideoSeconds > safeChapterDuration) {
            WolfLongFilmEngine.planChapters(
                framesPerScene, fps, introFrames,
                maxChapterSeconds = safeChapterDuration
            )
        } else emptyList()
        val narrationLimitUs = if (useNarrationTiming) {
            (audioDurationMs ?: 1000L).coerceIn(1000L, 1_800_000L) * 1000L
        } else totalVideoSeconds.toLong() * 1_000_000L
        val name = "wolf_offline_${System.currentTimeMillis()}.mp4"
        val tempDir = File(context.cacheDir, "WOLF_OFFLINE").apply { mkdirs() }
        // Reserve for overlapping temporary/final MP4 copies, optional AAC, and a safety margin.
        val estimatedOutputBytes = estimatePeakStorageBytes(
            totalVideoSeconds,
            audioUri != null || generateSoundEffects,
            usesChapteredRendering = chapterPlans.size > 1
        ) + if (audioUri != null && generateSoundEffects) 256L * 1024L * 1024L else 0L
        WolfResourceGuard.ensureSafeToStart(context, tempDir, estimatedOutputBytes)
        val silentFile = File(tempDir, "silent_$name")
        var tempFile: File = silentFile
        // User images are optional. Reuse available images across scenes and render
        // deterministic local artwork for any scene without a usable image.
        val chosenUris = imageUris.distinct().take(WolfProjectStore.MAX_IMAGES)
        val sceneShotUris: List<List<Uri?>> = scenes.indices.map { sceneIndex ->
            (0 until WolfSceneAssetStore.SHOTS_PER_SCENE).map { shotIndex ->
                val slotIndex = sceneIndex * WolfSceneAssetStore.SHOTS_PER_SCENE + shotIndex
                sceneImageAssignments[slotIndex]
                    ?: chosenUris.getOrNull(slotIndex)
                    ?: chosenUris.getOrNull(if (chosenUris.isEmpty()) -1 else sceneIndex % chosenUris.size)
            }
        }
        val chapterFingerprint = if (chapterPlans.isNotEmpty()) {
            WolfLongFilmEngine.fingerprint(
                story, framesPerScene, fps, w, h, introFrames, enableHorrorTypography,
                listOf(
                    includeWatermark.toString(),
                    "depth-segmentation-v${WolfSceneAssetStore.DEPTH_SEGMENTATION_VERSION}",
                    audioUri?.toString().orEmpty(),
                    sceneShotUris.flatten().joinToString("|"),
                    sceneImageAssignments.entries.sortedBy { it.key }
                        .joinToString("|") { "${it.key}=${it.value}" }
                ).joinToString("#")
            )
        } else ""
        val checkpointProjectId = projectId?.takeIf { it.matches(Regex("[A-Za-z0-9-]{16,64}")) }
            ?: if (chapterPlans.isNotEmpty()) "wolf-${chapterFingerprint.take(32)}" else ""
        var outputWidth = w
        var outputHeight = h
        var usedReducedQualityFallback = false
        try {
            if (chapterPlans.size > 1) {
                encodeChaptered(
                    context, silentFile, checkpointProjectId, chapterFingerprint, chapterPlans,
                    scenes, w, h, fps, framesPerScene, sceneShotUris, includeWatermark,
                    typographyCues, introSeconds, onSceneProgress, onChapterProgress, 1_600_000
                )
            } else {
                encode(context, silentFile, scenes, w, h, fps, framesPerScene, sceneShotUris,
                    includeWatermark, typographyCues, introSeconds, onSceneProgress, 1_600_000)
            }
        } catch (primaryError: Exception) {
            silentFile.delete()
            // Keep successfully rendered chapters for a targeted retry instead of restarting the film.
            if (chapterPlans.size > 1) {
                throw IllegalStateException(
                    "توقف تصدير الفيلم عند أحد الفصول؛ حُفظت الفصول المكتملة ويمكن استئناف الفصل المتعثر. ${primaryError.message ?: "خطأ غير معروف"}",
                    primaryError
                )
            }
            // A lower-quality retry is not allowed after a memory or thermal stop.
            WolfResourceGuard.ensureSafeToContinue(context)
            val fallbackDimensions = fallbackOutputDimensions(aspectRatio)
            if (fallbackDimensions.first >= w && fallbackDimensions.second >= h) {
                throw primaryError
            }
            WolfDiagnostics.record(
                context, "استعادة التصدير", "WOLF RECOVERY ENGINE", "WARNING",
                "تعذر التصدير بالدقة الأصلية؛ تتم إعادة المحاولة بدقة أقل",
                primaryError.javaClass.simpleName
            )
            try {
                encode(context, silentFile, scenes, fallbackDimensions.first, fallbackDimensions.second,
                    fps, framesPerScene, sceneShotUris, includeWatermark, typographyCues, introSeconds, onSceneProgress, 720_000)
                outputWidth = fallbackDimensions.first
                outputHeight = fallbackDimensions.second
                usedReducedQualityFallback = true
            } catch (fallbackError: Exception) {
                silentFile.delete()
                fallbackError.addSuppressed(primaryError)
                throw IllegalStateException(
                    "فشل التصدير بالدقة الأصلية والاحتياطية: ${fallbackError.message ?: fallbackError.javaClass.simpleName}",
                    fallbackError
                )
            }
        }
        val generatedAudio = if (generateSoundEffects) {
            File(tempDir, "sfx_$name.m4a").also { soundFile ->
                try {
                    WolfSoundFxEngine.renderAac(
                        scenes, totalVideoSeconds, soundFile, introSeconds, framesPerScene, fps,
                        masterGain = soundEffectGain
                    )
                } catch (e: Exception) {
                    soundFile.delete()
                    silentFile.delete()
                    throw IllegalStateException("تعذر إنشاء المؤثرات الصوتية: ${e.message ?: "خطأ صوت غير معروف"}", e)
                }
            }
        } else null
        if(audioUri!=null || generatedAudio!=null) {
            val mixedFile=File(tempDir,name)
            var convertedAudioFile: File? = null
            var layeredAudioFile: File? = null
            try {
                if (audioUri != null && generatedAudio != null) {
                    layeredAudioFile = File(tempDir, "layered_$name.m4a")
                    WolfAudioMixer.mix(
                        context, audioUri, generatedAudio, layeredAudioFile,
                        narrationLimitUs, narrationGain = 0.92, effectsGain = 0.30
                    )
                    muxAudio(context, silentFile, null, layeredAudioFile, mixedFile, narrationLimitUs, fps)
                } else {
                    convertedAudioFile = audioUri?.let { source ->
                        WolfAudioTranscoder.transcodeToAacIfNeeded(
                            context, source, File(tempDir, "converted_$name.m4a"),
                            narrationLimitUs
                        )
                    }
                    val muxAudioUri = if (convertedAudioFile == null) audioUri else null
                    val muxAudioFile = convertedAudioFile ?: generatedAudio
                    muxAudio(context,silentFile,muxAudioUri,muxAudioFile,mixedFile,narrationLimitUs, fps)
                }
                silentFile.delete()
                tempFile=mixedFile
            } catch(e:Exception) {
                mixedFile.delete()
                silentFile.delete()
                throw IllegalStateException("تعذر تجهيز أو دمج ملف الصوت: ${e.message ?: "صيغة غير مدعومة"}",e)
            } finally {
                generatedAudio?.delete()
                convertedAudioFile?.delete()
                layeredAudioFile?.delete()
            }
        } else {
            // Surface-backed Canvas frames can inherit wall-clock timestamps from slow devices.
            // Rebase every encoded sample to the intended constant frame cadence before export.
            val normalizedFile=File(tempDir,"normalized_$name")
            try {
                normalizeVideoTimestamps(silentFile,normalizedFile,fps)
                silentFile.delete()
                tempFile=normalizedFile
            } catch(e:Exception) {
                normalizedFile.delete()
                silentFile.delete()
                throw IllegalStateException("تعذر ضبط توقيت إطارات الفيديو: ${e.message ?: "خطأ غير معروف"}",e)
            }
        }

        var mediaReference: String? = null
        val savedLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WOLF CODE S")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("تعذر إنشاء ملف في مجلد الفيديوهات")
            try {
                val output = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف الفيديو للحفظ")
                output.use { stream -> tempFile.inputStream().use { input -> input.copyTo(stream) } }
                val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, ready, null, null)
                mediaReference = uri.toString()
                "Movies/WOLF CODE S/$name"
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            } finally {
                tempFile.delete()
            }
        } else {
            val publicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "WOLF CODE S").apply { mkdirs() }
            val destination = File(publicDir, name)
            if (!tempFile.renameTo(destination)) tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
            mediaReference = destination.absolutePath
            destination.absolutePath
        }
        val finalReference = mediaReference
            ?: throw IllegalStateException("تم حفظ الفيديو لكن تعذر الحصول على مرجع لملف التسليم")
        val requiresAudioTrack = audioUri != null || generatedAudio != null
        val qualityReport = WolfFilmQualityInspector.inspect(
            context, finalReference, totalVideoSeconds, requiresAudioTrack
        )
        if (!qualityReport.passed) {
            if (finalReference.startsWith("content://")) {
                runCatching { context.contentResolver.delete(Uri.parse(finalReference), null, null) }
            } else {
                runCatching { File(finalReference).delete() }
            }
            throw IllegalStateException("رفض WOLF FILM QA تسليم ملف غير سليم: ${qualityReport.summary}")
        }
        if (chapterPlans.size > 1) {
            WolfLongFilmCheckpointStore.clear(context, checkpointProjectId)
        }
        return Result(
            scenes = scenes.size,
            frames = framesPerScene.sum() + introFrames,
            savedLocation = savedLocation,
            mediaUri = finalReference,
            durationSeconds = if (useNarrationTiming) requestedTotal else totalVideoSeconds,
            framesPerSecond = fps,
            videoWidth = outputWidth,
            videoHeight = outputHeight,
            usedReducedQualityFallback = usedReducedQualityFallback
        )
    }


    private fun encodeChaptered(
        context: Context,
        outputFile: File,
        projectId: String,
        fingerprint: String,
        chapters: List<WolfLongFilmEngine.ChapterPlan>,
        scenes: List<String>,
        width: Int,
        height: Int,
        fps: Int,
        framesPerScene: List<Int>,
        sceneImageUris: List<List<Uri?>>,
        includeWatermark: Boolean,
        typographyCues: List<WolfHorrorTypographyEngine.Cue?>,
        introSeconds: Int,
        onSceneProgress: ((Int, Int) -> Unit)?,
        onChapterProgress: ((Int, Int, String) -> Unit)?,
        bitRate: Int
    ) {
        val session = WolfLongFilmCheckpointStore.begin(
            context, projectId, fingerprint, fps,
            framesPerScene.sum() + introSeconds * fps, chapters
        )
        val totalSlices = chapters.sumOf { it.slices.size }
        var completedSlices = 0
        val chapterFiles = mutableListOf<File>()

        chapters.forEachIndexed { chapterIndex, chapter ->
            val existing = session.chapters.getOrNull(chapterIndex)
            val reusableFile = if (existing?.status == WolfLongFilmEngine.ChapterStatus.COMPLETED) {
                existing.artifactPath?.let { File(it).takeIf { file -> file.isFile && file.length() > 0L } }
            } else null
            if (reusableFile != null) {
                chapterFiles.add(reusableFile)
                completedSlices += chapter.slices.size
                onSceneProgress?.invoke(completedSlices, totalSlices)
                onChapterProgress?.invoke(chapterIndex + 1, chapters.size, "تم استعادة الفصل المحفوظ")
                return@forEachIndexed
            }

            WolfLongFilmCheckpointStore.markRunning(context, projectId, chapter.number)
            val chapterFile = WolfLongFilmCheckpointStore.chapterFile(
                context, projectId, fingerprint, chapter.number
            )
            chapterFile.delete()
            try {
                val chapterScenes = chapter.slices.map { slice ->
                    scenes.getOrElse(slice.sceneIndex) { throw IllegalStateException("مرجع مشهد غير صالح في خطة الفصل") }
                }
                val chapterFrames = chapter.slices.map { it.frameCount }
                val chapterImages = chapter.slices.map { slice ->
                    sceneImageUris.getOrElse(slice.sceneIndex) { emptyList() }
                }
                val chapterCues = chapter.slices.map { slice ->
                    val source = typographyCues.getOrNull(slice.sceneIndex) ?: return@map null
                    val start = (source.startFrame - slice.startFrame).coerceAtLeast(0)
                    val end = (source.endFrameExclusive - slice.startFrame).coerceAtMost(slice.frameCount)
                    if (end <= start) null else source.copy(startFrame = start, endFrameExclusive = end)
                }
                val alreadyCompleted = completedSlices
                encode(
                    context, chapterFile, chapterScenes, width, height, fps, chapterFrames,
                    chapterImages, includeWatermark, chapterCues,
                    if (chapter.introFrameCount > 0) introSeconds else 0,
                    { localCompleted, _ ->
                        onSceneProgress?.invoke(
                            (alreadyCompleted + localCompleted).coerceAtMost(totalSlices),
                            totalSlices
                        )
                    },
                    bitRate
                )
                WolfLongFilmCheckpointStore.markCompleted(
                    context, projectId, chapter.number, chapterFile
                )
                chapterFiles.add(chapterFile)
                completedSlices += chapter.slices.size
                onSceneProgress?.invoke(completedSlices, totalSlices)
                onChapterProgress?.invoke(chapterIndex + 1, chapters.size, "اكتمل الفصل")
            } catch (error: Exception) {
                runCatching {
                    WolfLongFilmCheckpointStore.markFailed(
                        context, projectId, chapter.number,
                        error.message ?: error.javaClass.simpleName
                    )
                }
                chapterFile.delete()
                throw error
            }
        }
        if (chapterFiles.size != chapters.size) {
            throw IllegalStateException("بعض فصول الفيلم غير متوفرة للتجميع")
        }
        mergeChapterVideos(chapterFiles, outputFile, fps)
    }

    /** Repackages chapter H.264 streams into one continuous video timeline without re-encoding. */
    private fun mergeChapterVideos(chapters: List<File>, outputFile: File, fps: Int) {
        require(chapters.isNotEmpty()) { "لا توجد فصول للتجميع" }
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val firstExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var succeeded = false
        try {
            firstExtractor.setDataSource(chapters.first().absolutePath)
            val firstTrack = (0 until firstExtractor.trackCount).firstOrNull { index ->
                firstExtractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalStateException("الفصل الأول لا يحتوي مسار فيديو")
            val format = firstExtractor.getTrackFormat(firstTrack)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true
            firstExtractor.release()

            var globalFrameIndex = 0L
            chapters.forEach { chapterFile ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(chapterFile.absolutePath)
                    val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                    } ?: throw IllegalStateException("أحد الفصول لا يحتوي مسار فيديو")
                    val sourceFormat = extractor.getTrackFormat(videoTrack)
                    if (sourceFormat.getString(MediaFormat.KEY_MIME) != format.getString(MediaFormat.KEY_MIME) ||
                        sourceFormat.getInteger(MediaFormat.KEY_WIDTH) != format.getInteger(MediaFormat.KEY_WIDTH) ||
                        sourceFormat.getInteger(MediaFormat.KEY_HEIGHT) != format.getInteger(MediaFormat.KEY_HEIGHT)
                    ) {
                        throw IllegalStateException("تختلف أبعاد أو صيغة الفيديو بين الفصول؛ أوقفنا الدمج لحماية الملف")
                    }
                    extractor.selectTrack(videoTrack)
                    val capacity = if (sourceFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        sourceFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceIn(256 * 1024, 8 * 1024 * 1024)
                    } else 2 * 1024 * 1024
                    val buffer = ByteBuffer.allocateDirect(capacity)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val flags = extractor.sampleFlags
                        if (flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0 ||
                            flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0
                        ) {
                            throw IllegalStateException("تعذر دمج عينة فيديو محمية أو مجزأة")
                        }
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = globalFrameIndex * 1_000_000L / fps.coerceAtLeast(1)
                        info.flags = if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else 0
                        buffer.position(0)
                        buffer.limit(size)
                        muxer.writeSampleData(outputTrack, buffer, info)
                        globalFrameIndex++
                        if (!extractor.advance()) break
                    }
                } finally {
                    runCatching { extractor.release() }
                }
            }
            if (globalFrameIndex <= 0L) throw IllegalStateException("لا توجد إطارات فيديو لدمجها")
            muxer.stop()
            muxerStarted = false
            succeeded = true
        } finally {
            runCatching { firstExtractor.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            if (!succeeded || !outputFile.isFile || outputFile.length() == 0L) outputFile.delete()
        }
    }

    private fun outputDimensions(aspectRatio: String): Pair<Int, Int> = when {
        aspectRatio.startsWith("16:9") -> 1280 to 720
        aspectRatio.startsWith("1:1") -> 720 to 720
        aspectRatio.startsWith("4:5") -> 720 to 900
        else -> 720 to 1280
    }

    private fun fallbackOutputDimensions(aspectRatio: String): Pair<Int, Int> = when {
        aspectRatio.startsWith("16:9") -> 854 to 480
        aspectRatio.startsWith("1:1") -> 480 to 480
        aspectRatio.startsWith("4:5") -> 480 to 600
        else -> 480 to 854
    }

    private fun encode(context: Context, file: File, scenes: List<String>, w: Int, h: Int, fps: Int,
        framesPerScene: List<Int>, sceneImageUris: List<List<Uri?>>, includeWatermark: Boolean,
        typographyCues: List<WolfHorrorTypographyEngine.Cue?>,
        introSeconds: Int, onSceneProgress: ((Int, Int) -> Unit)?, bitRate: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, 0x7F000789)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate.coerceIn(400_000, 2_500_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var started = false
        var track = -1
        var inputSurface: Surface? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            if(introSeconds > 0) {
                val introCount=fps*introSeconds
                for(frame in 0 until introCount) {
                    if(frame % (fps*2) == 0) WolfResourceGuard.ensureSafeToContinue(context)
                    drawIntro(
                        inputSurface ?: throw IllegalStateException("تعذر إنشاء سطح مقدمة الذئب"),
                        w,h,frame.toFloat()/max(1,introCount-1)
                    )
                    val drained=drain(codec,muxer,info,started,track,false)
                    track=drained.first
                    started=drained.second
                }
            }
            for (s in scenes.indices) {
                WolfResourceGuard.ensureSafeToContinue(context)
                val sceneFrames = framesPerScene[s]
                val baseShotFrames = sceneFrames / WolfSceneAssetStore.SHOTS_PER_SCENE
                val extraShotFrames = sceneFrames % WolfSceneAssetStore.SHOTS_PER_SCENE
                var sceneFrameOffset = 0
                for (shotIndex in 0 until WolfSceneAssetStore.SHOTS_PER_SCENE) {
                    val shotFrames = baseShotFrames + if (shotIndex < extraShotFrames) 1 else 0
                    val shotUri = sceneImageUris.getOrNull(s)?.getOrNull(shotIndex)
                        ?: sceneImageUris.getOrNull(s)?.firstOrNull { it != null }
                    val image = shotUri?.let { uri -> decodeImage(context, uri) }
                    val preparedShot = WolfSceneRenderPipeline.prepare(
                        caption = scenes[s],
                        sceneIndex = s,
                        source = image
                    ) { reason ->
                        WolfDiagnostics.record(
                            context,
                            "استعادة مسار المشهد",
                            "WOLF SCENE RENDER PIPELINE",
                            "WARNING",
                            "تعذر استخدام الصورة المرفوعة؛ تم تحويل اللقطة تلقائياً إلى المحرك الإجرائي",
                            reason
                        )
                    }
                    try {
                        for (f in 0 until shotFrames) {
                            if (f % (fps * 5) == 0) WolfResourceGuard.ensureSafeToContinue(context)
                            val shotProgress = f.toFloat() / max(1, shotFrames - 1)
                            draw(
                                context,
                                inputSurface ?: throw IllegalStateException("تعذر إنشاء سطح إدخال الفيديو"),
                                w, h, scenes[s], s, shotIndex, shotProgress, preparedShot, includeWatermark,
                                typographyCues.getOrNull(s), sceneFrameOffset + f, fps
                            )
                            val drained = drain(codec, muxer, info, started, track, false)
                            track = drained.first
                            started = drained.second
                        }
                    } finally {
                        preparedShot.recycle()
                        if (image != null && !image.isRecycled) image.recycle()
                    }
                    sceneFrameOffset += shotFrames
                }
                onSceneProgress?.invoke(s + 1, scenes.size)
            }
            codec.signalEndOfInputStream()
            var done = false
            var tries = 0
            while (!done && tries < 1000) {
                val result = drain(codec, muxer, info, started, track, true)
                track = result.first
                started = result.second
                done = result.third
                tries++
            }
            if (!started || !done) throw IllegalStateException("تعذر إنهاء ملف الفيديو")
        } finally {
            try { inputSurface?.release() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            muxer?.release()
        }
    }

    private fun drain(c: MediaCodec, m: MediaMuxer, info: MediaCodec.BufferInfo,
                      wasStarted: Boolean, oldTrack: Int, wait: Boolean): Triple<Int, Boolean, Boolean> {
        var started = wasStarted
        var track = oldTrack
        var eos = false
        var count = 0
        while (count < if (wait) 20 else 4) {
            val index = c.dequeueOutputBuffer(info, if (wait) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (wait) count++ else break }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (started) throw IllegalStateException("تغير تنسيق الفيديو")
                    track = m.addTrack(c.outputFormat)
                    m.start()
                    started = true
                }
                index >= 0 -> {
                    val data = c.getOutputBuffer(index)
                    if (data != null && info.size > 0 && started) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        m.writeSampleData(track, data, info)
                    }
                    eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    c.releaseOutputBuffer(index, false)
                    if (eos) break
                }
            }
        }
        return Triple(track, started, eos)
    }

    private fun draw(
        context: Context, surface: Surface, w: Int, h: Int, caption: String, scene: Int,
        shotIndex: Int, t: Float, preparedShot: WolfSceneRenderPipeline.PreparedShot,
        includeWatermark: Boolean, typographyCue: WolfHorrorTypographyEngine.Cue?,
        sceneFrameIndex: Int, fps: Int
    ) {
        val c: Canvas = surface.lockCanvas(null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val safeShotIndex = shotIndex.coerceIn(0, WolfSceneAssetStore.SHOTS_PER_SCENE - 1)
        try {
            WolfSceneRenderPipeline.render(
                canvas = c,
                width = w,
                height = h,
                shotIndex = safeShotIndex,
                progress = t,
                caption = caption,
                sceneIndex = scene,
                preparedShot = preparedShot
            ) { reason ->
                WolfDiagnostics.record(
                    context,
                    "استعادة مسار المشهد",
                    "WOLF SCENE RENDER PIPELINE",
                    "WARNING",
                    "تعذر رسم الصورة المرفوعة؛ تم الرجوع إلى المحرك الإجرائي",
                    reason
                )
            }
            drawHorrorTypography(c, w, h, typographyCue, sceneFrameIndex, fps)
            if (includeWatermark) {
                p.color=android.graphics.Color.WHITE
                p.textAlign=Paint.Align.CENTER
                p.textSize=(w * 0.037f).coerceIn(16f,24f)
                p.setShadowLayer(5f,0f,1f,android.graphics.Color.BLACK)
                c.drawText("WOLF CODE S",w/2f,h*.965f,p)
                p.clearShadowLayer()
                val centerX = w / 2f - p.measureText("WOLF CODE S") / 2f - 24f
                val centerY = h * .952f
                val wolf = Path().apply {
                    moveTo(centerX - 12f, centerY + 8f)
                    lineTo(centerX - 10f, centerY - 11f)
                    lineTo(centerX - 4f, centerY - 5f)
                    lineTo(centerX, centerY - 15f)
                    lineTo(centerX + 4f, centerY - 5f)
                    lineTo(centerX + 10f, centerY - 11f)
                    lineTo(centerX + 12f, centerY + 8f)
                    close()
                }
                p.color=android.graphics.Color.BLACK
                c.drawPath(wolf,p)
                p.color=android.graphics.Color.RED
                c.drawCircle(centerX - 4f, centerY, 1.8f, p)
                c.drawCircle(centerX + 4f, centerY, 1.8f, p)
            }
            p.color=android.graphics.Color.RED
            c.drawRect(0f,0f,w*((safeShotIndex + t) / WolfSceneAssetStore.SHOTS_PER_SCENE),5f,p)
        } finally {
            surface.unlockCanvasAndPost(c)
        }
    }


    /** Composite a brief atmospheric phrase into the rendered scene, never as UI subtitles. */
    private fun drawHorrorTypography(
        canvas: Canvas,
        w: Int,
        h: Int,
        cue: WolfHorrorTypographyEngine.Cue?,
        sceneFrame: Int,
        fps: Int
    ) {
        if (cue == null) return
        val baseAlpha = WolfHorrorTypographyEngine.alphaAtFrame(cue, sceneFrame, fps)
        if (baseAlpha <= 0f) return
        val phase = (sceneFrame / (fps / 5).coerceAtLeast(1)) % 13
        val flicker = if (cue.motion == WolfHorrorTypographyEngine.Motion.GLITCH_FLICKER && phase == 4) 0.62f else 1f
        val alpha = (baseAlpha * flicker * 232f).toInt().coerceIn(0, 232)
        if (alpha <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
            textSize = (w * 0.046f).coerceIn(20f, 43f)
            textAlign = when (cue.placement) {
                WolfHorrorTypographyEngine.Placement.UPPER_LEFT,
                WolfHorrorTypographyEngine.Placement.MID_LEFT -> Paint.Align.LEFT
                else -> Paint.Align.RIGHT
            }
            color = android.graphics.Color.argb(alpha, 235, 32, 48)
            setShadowLayer((w * 0.018f).coerceIn(7f, 16f), 0f, 0f,
                android.graphics.Color.argb((alpha * 0.72f).toInt(), 155, 0, 18))
        }
        val maxTextWidth = w * 0.72f
        while (paint.textSize > 16f && paint.measureText(cue.text) > maxTextWidth) {
            paint.textSize *= 0.94f
        }
        val leftAligned = paint.textAlign == Paint.Align.LEFT
        val x = if (leftAligned) w * 0.12f else w * 0.88f
        val y = when (cue.placement) {
            WolfHorrorTypographyEngine.Placement.UPPER_LEFT,
            WolfHorrorTypographyEngine.Placement.UPPER_RIGHT -> h * 0.25f
            WolfHorrorTypographyEngine.Placement.MID_LEFT,
            WolfHorrorTypographyEngine.Placement.MID_RIGHT -> h * 0.64f
        }
        val jitter = if (cue.motion == WolfHorrorTypographyEngine.Motion.GLITCH_FLICKER && phase % 5 == 0) {
            if (phase % 2 == 0) 2.2f else -1.6f
        } else 0f
        val save = canvas.save()
        canvas.rotate(if (phase % 7 == 0) -0.45f else 0.18f, x, y)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (w * 0.0018f).coerceIn(0.8f, 1.8f)
        paint.color = android.graphics.Color.argb((alpha * 0.42f).toInt(), 95, 0, 10)
        canvas.drawText(cue.text, x + jitter, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(alpha, 235, 32, 48)
        canvas.drawText(cue.text, x + jitter, y, paint)
        canvas.restoreToCount(save)
    }

    /** Three-second branded wolf signature, rendered locally with no external artwork. */
    private fun drawIntro(surface: Surface, w: Int, h: Int, progress: Float) {
        val canvas=surface.lockCanvas(null)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        try {
            canvas.drawColor(android.graphics.Color.rgb(3,2,5))
            paint.shader=android.graphics.LinearGradient(
                0f,0f,w.toFloat(),h.toFloat(),
                android.graphics.Color.rgb(43,4,14),
                android.graphics.Color.rgb(2,2,5),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint)
            paint.shader=null

            val moonX=w*0.76f
            val moonY=h*0.25f
            val moonRadius=minOf(w,h)*0.14f
            paint.shader=android.graphics.RadialGradient(
                moonX,moonY,moonRadius*1.55f,
                intArrayOf(
                    android.graphics.Color.argb(90,190,8,28),
                    android.graphics.Color.argb(45,110,3,18),
                    android.graphics.Color.TRANSPARENT
                ),
                floatArrayOf(0f,0.66f,1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(moonX,moonY,moonRadius*1.55f,paint)
            paint.shader=null
            paint.color=android.graphics.Color.rgb(112,5,22)
            canvas.drawCircle(moonX,moonY,moonRadius,paint)
            paint.color=android.graphics.Color.argb(110,20,2,9)
            canvas.drawCircle(moonX+moonRadius*0.22f,moonY-moonRadius*0.16f,moonRadius*0.78f,paint)

            val scale=minOf(w,h)*0.42f
            val cx=w*0.5f
            val cy=h*0.43f
            val wolf=Path().apply {
                moveTo(cx-scale*0.47f,cy+scale*0.44f)
                lineTo(cx-scale*0.53f,cy-scale*0.34f)
                lineTo(cx-scale*0.22f,cy-scale*0.18f)
                lineTo(cx,cy-scale*0.55f)
                lineTo(cx+scale*0.22f,cy-scale*0.18f)
                lineTo(cx+scale*0.53f,cy-scale*0.34f)
                lineTo(cx+scale*0.47f,cy+scale*0.44f)
                lineTo(cx+scale*0.18f,cy+scale*0.66f)
                lineTo(cx,cy+scale*0.78f)
                lineTo(cx-scale*0.18f,cy+scale*0.66f)
                close()
            }
            paint.color=android.graphics.Color.rgb(3,2,4)
            paint.setShadowLayer(scale*0.08f,0f,0f,android.graphics.Color.RED)
            canvas.drawPath(wolf,paint)
            paint.clearShadowLayer()
            paint.color=android.graphics.Color.rgb(255,25,43)
            paint.setShadowLayer(scale*0.045f,0f,0f,android.graphics.Color.RED)
            canvas.drawOval(
                RectF(cx-scale*0.23f,cy+scale*0.08f,cx-scale*0.10f,cy+scale*0.145f),paint
            )
            canvas.drawOval(
                RectF(cx+scale*0.10f,cy+scale*0.08f,cx+scale*0.23f,cy+scale*0.145f),paint
            )
            paint.clearShadowLayer()

            paint.textAlign=Paint.Align.CENTER
            paint.typeface=android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD)
            paint.textSize=(h*0.062f).coerceIn(30f,72f)
            paint.color=android.graphics.Color.WHITE
            paint.setShadowLayer(12f,0f,0f,android.graphics.Color.RED)
            canvas.drawText("WOLF CODE S",cx,h*0.82f,paint)
            paint.clearShadowLayer()
            paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize=(h*0.027f).coerceIn(16f,30f)
            paint.color=android.graphics.Color.rgb(235,42,57)
            canvas.drawText("شفرة الذئب S",cx,h*0.875f,paint)
            paint.typeface=android.graphics.Typeface.DEFAULT
            paint.textSize=(h*0.019f).coerceIn(12f,21f)
            paint.color=android.graphics.Color.LTGRAY
            canvas.drawText("رعب الظل المفقود",cx,h*0.92f,paint)

            val fade=when {
                progress<0.10f -> (progress/0.10f*255f).toInt()
                progress>0.92f -> ((1f-progress)/0.08f*255f).toInt()
                else -> 255
            }.coerceIn(0,255)
            paint.color=android.graphics.Color.argb(255-fade,0,0,0)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint)
            paint.color=android.graphics.Color.rgb(205,12,34)
            canvas.drawRect(0f,h-5f,w*progress,h.toFloat(),paint)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun muxAudio(context: Context, videoFile: File, audioUri: Uri?, generatedAudioFile: File?, outputFile: File, maxDurationUs: Long, fps: Int) {
        val videoExtractor=MediaExtractor()
        val audioExtractor=MediaExtractor()
        var muxer: MediaMuxer?=null
        var audioDescriptor: ParcelFileDescriptor?=null
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            if (generatedAudioFile != null) {
                audioExtractor.setDataSource(generatedAudioFile.absolutePath)
            } else {
                val sourceUri = audioUri ?: throw IllegalStateException("لم يتم تحديد مصدر الصوت")
                audioDescriptor=context.contentResolver.openFileDescriptor(sourceUri,"r")
                    ?: throw IllegalStateException("تعذر فتح ملف الصوت")
                audioExtractor.setDataSource(audioDescriptor!!.fileDescriptor)
            }
            val videoTrack=(0 until videoExtractor.trackCount).firstOrNull {
                videoExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true
            } ?: throw IllegalStateException("مسار الفيديو غير موجود")
            val audioTrack=(0 until audioExtractor.trackCount).firstOrNull {
                audioExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            } ?: throw IllegalStateException("لم يتم العثور على مسار صوت صالح")
            val videoFormat=videoExtractor.getTrackFormat(videoTrack)
            val audioFormat=audioExtractor.getTrackFormat(audioTrack)
            val audioMime=audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if(audioMime!="audio/mp4a-latm" && audioMime!="audio/aac") {
                throw IllegalStateException("اختر ملف AAC أو M4A؛ MP3 يحتاج تحويلًا إلى AAC أولًا")
            }
            muxer=MediaMuxer(outputFile.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputVideo=muxer.addTrack(videoFormat)
            val outputAudio=muxer.addTrack(audioFormat)
            muxer.start()
            copyTrack(videoExtractor,videoTrack,muxer,outputVideo,maxDurationUs,fps)
            copyTrack(audioExtractor,audioTrack,muxer,outputAudio,maxDurationUs)
            muxer.stop()
        } finally {
            try { videoExtractor.release() } catch (_:Exception) {}
            try { audioExtractor.release() } catch (_:Exception) {}
            try { audioDescriptor?.close() } catch (_:Exception) {}
            try { muxer?.release() } catch (_:Exception) {}
        }
    }

    private fun copyTrack(extractor: MediaExtractor, sourceTrack: Int, muxer: MediaMuxer,
                          outputTrack: Int, maxDurationUs: Long, frameRate: Int? = null) {
        extractor.selectTrack(sourceTrack)
        val format=extractor.getTrackFormat(sourceTrack)
        val capacity=if(format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256*1024) else 1024*1024
        val buffer=ByteBuffer.allocateDirect(capacity)
        val info=MediaCodec.BufferInfo()
        var frameIndex=0L
        while(true) {
            buffer.clear()
            val size=extractor.readSampleData(buffer,0)
            if(size<0) break
            val sourcePts=extractor.sampleTime
            val pts=if(frameRate!=null) frameIndex * 1_000_000L / frameRate.coerceAtLeast(1) else sourcePts
            if(pts<0 || pts>maxDurationUs) break
            val sampleFlags = extractor.sampleFlags
            if (sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                throw IllegalStateException("لا يمكن دمج ملف صوت أو فيديو محمي بتشفير DRM")
            }
            if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                throw IllegalStateException("الملف يحتوي عينات وسائط مجزأة لا يمكن دمجها مباشرة")
            }
            info.offset=0
            info.size=size
            info.presentationTimeUs=pts
            info.flags = if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            buffer.position(0)
            buffer.limit(size)
            muxer.writeSampleData(outputTrack,buffer,info)
            frameIndex++
            if(!extractor.advance()) break
        }
        extractor.unselectTrack(sourceTrack)
    }

    /** Copies the encoded video while assigning deterministic presentation timestamps. */
    private fun normalizeVideoTimestamps(inputFile: File, outputFile: File, fps: Int) {
        val extractor=MediaExtractor()
        var muxer: MediaMuxer?=null
        var muxerStarted=false
        var succeeded=false
        try {
            extractor.setDataSource(inputFile.absolutePath)
            val videoTrack=(0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true
            } ?: throw IllegalStateException("مسار الفيديو غير موجود")
            muxer=MediaMuxer(outputFile.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack=muxer.addTrack(extractor.getTrackFormat(videoTrack))
            muxer.start()
            muxerStarted=true
            copyTrack(extractor,videoTrack,muxer,outputTrack,Long.MAX_VALUE,fps)
            succeeded=true
        } finally {
            try { extractor.release() } catch (_:Exception) {}
            try { if(muxerStarted) muxer?.stop() } catch (_:Exception) {}
            try { muxer?.release() } catch (_:Exception) {}
            if(!succeeded || !outputFile.exists() || outputFile.length()==0L) outputFile.delete()
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
            if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
            var sample=1
            while(max(bounds.outWidth/sample,bounds.outHeight/sample)>2048) sample*=2
            val options=BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.RGB_565 }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
        } catch (_: Exception) { null }
    }
}