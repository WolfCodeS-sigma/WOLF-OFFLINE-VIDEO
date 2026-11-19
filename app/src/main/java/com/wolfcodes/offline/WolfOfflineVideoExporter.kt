package com.wolfcodes.offline

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

data class WolfExportResult(val uri: Uri, val displayName: String, val durationSeconds: Int, val width: Int, val height: Int)

class WolfOfflineVideoExporter(private val context: Context) {
    private val visuals = WolfProceduralVisualSystem()

    fun export(project: WolfProject, onProgress: (Int, String) -> Unit): WolfExportResult {
        require(project.story.isNotBlank())
        require(project.durationSeconds in 5..1800)
        val scenes = WolfScenePlanner.split(project.story)
        val dimensions = if (project.aspectRatio == "16:9") 1280 to 720 else 720 to 1280
        val fps = 24
        val frames = WolfScenePlanner.allocateFrames(scenes, project.durationSeconds * fps)
        val name = "WOLF_" + System.currentTimeMillis() + ".mp4"
        val temporary = File(context.cacheDir, name)
        try {
            encode(temporary, scenes, frames, dimensions.first, dimensions.second, fps, onProgress)
            val uri = saveToMediaStore(temporary, name)
            return WolfExportResult(uri, name, project.durationSeconds, dimensions.first, dimensions.second)
        } finally {
            temporary.delete()
        }
    }

    private fun encode(file: File, scenes: List<String>, frameCounts: List<Int>, width: Int, height: Int,
                       fps: Int, onProgress: (Int, String) -> Unit) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, 0x7F000789)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val track = intArrayOf(-1)
        val info = MediaCodec.BufferInfo()
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            val activeMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer
            val totalFrames = frameCounts.sum()
            var frameNumber = 0
            var nextFrameNanos = System.nanoTime()
            val frameIntervalNanos = 1_000_000_000L / fps
            scenes.forEachIndexed { sceneIndex, scene ->
                val count = frameCounts[sceneIndex]
                for (localFrame in 0 until count) {
                    val canvas = surface.lockCanvas(null)
                    try {
                        canvas.drawColor(Color.BLACK)
                        val progress = if (count <= 1) 1f else localFrame.toFloat() / (count - 1)
                        visuals.render(canvas, width, height, scene, sceneIndex, progress, frameNumber.toFloat() / fps)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    drain(encoder, activeMuxer, info, track) { outputFormat ->
                        if (!muxerStarted) {
                            track[0] = activeMuxer.addTrack(outputFormat)
                            activeMuxer.start()
                            muxerStarted = true
                        }
                    }
                    frameNumber++
                    if (frameNumber % fps == 0 || frameNumber == totalFrames) {
                        onProgress((frameNumber * 100 / totalFrames).coerceIn(0, 99),
                            "إنتاج المشهد " + (sceneIndex + 1) + " من " + scenes.size)
                    }
                    nextFrameNanos += frameIntervalNanos
                    val remaining = nextFrameNanos - System.nanoTime()
                    if (remaining > 0) {
                        val millis = remaining / 1_000_000L
                        val nanos = (remaining % 1_000_000L).toInt()
                        Thread.sleep(millis, nanos)
                    }
                }
            }
            encoder.signalEndOfInputStream()
            drain(encoder, activeMuxer, info, track) { outputFormat ->
                if (!muxerStarted) {
                    track[0] = activeMuxer.addTrack(outputFormat)
                    activeMuxer.start()
                    muxerStarted = true
                }
            }
            check(muxerStarted && track[0] >= 0) { "تعذر إنشاء مسار الفيديو داخل ملف MP4" }
            onProgress(100, "اكتمل تصدير الفيديو")
        } finally {
            runCatching { surface?.release() }
            runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }

    private fun drain(encoder: MediaCodec, muxer: MediaMuxer, info: MediaCodec.BufferInfo,
                      track: IntArray, onFormat: (MediaFormat) -> Unit) {
        var idle = 0
        while (true) {
            when (val index = encoder.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (++idle >= 3) return
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(encoder.outputFormat)
                else -> if (index >= 0) {
                    val buffer: ByteBuffer = encoder.getOutputBuffer(index)
                        ?: throw IllegalStateException("تعذر قراءة بيانات الفيديو المشفرة")
                    if (info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        check(track[0] >= 0) { "لم يتم تهيئة مسار الفيديو" }
                        muxer.writeSampleData(track[0], buffer, info)
                    }
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (end) return
                }
            }
        }
    }

    private fun saveToMediaStore(source: File, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WOLF CODE S")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("تعذر إنشاء ملف الفيديو في المعرض")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("تعذر فتح ملف الفيديو للحفظ")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
