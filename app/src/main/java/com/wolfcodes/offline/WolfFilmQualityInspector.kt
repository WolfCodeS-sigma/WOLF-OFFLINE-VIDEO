package com.wolfcodes.offline

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.abs

/** Read-only structural QA for exported MP4 files. It reports faults and never claims to repair them. */
object WolfFilmQualityInspector {
    data class Report(
        val passed: Boolean,
        val issues: List<String>,
        val videoWidth: Int?,
        val videoHeight: Int?,
        val durationSeconds: Double?,
        val hasAudio: Boolean,
        val videoSamples: Long = 0L,
        val audioSamples: Long = 0L
    ) {
        val summary: String
            get() = if (passed) {
                "فحص MP4 ناجح: مسار الفيديو موجود" +
                    (if (hasAudio) " ومسار الصوت موجود" else "") +
                    (if (videoSamples > 0L) " — عدد إطارات الفيديو $videoSamples" else "") +
                    (durationSeconds?.let { " — المدة " + String.format(java.util.Locale.US, "%.1f", it) + " ثانية" } ?: "")
            } else {
                "فحص MP4 يحتاج مراجعة: " + issues.joinToString("، ")
            }
    }

    fun inspect(
        context: Context,
        mediaReference: String,
        expectedDurationSeconds: Int,
        requireAudio: Boolean
    ): Report {
        val issues = mutableListOf<String>()
        var width: Int? = null
        var height: Int? = null
        var duration: Double? = null
        var hasVideo = false
        var hasAudio = false
        var videoTrack = -1
        var audioTrack = -1
        var videoSamples = 0L
        var audioSamples = 0L
        val extractor = MediaExtractor()
        try {
            val uri = Uri.parse(mediaReference)
            when (uri.scheme) {
                "content" -> extractor.setDataSource(context, uri, null)
                "file" -> extractor.setDataSource(uri.path ?: mediaReference)
                else -> extractor.setDataSource(mediaReference)
            }
            if (extractor.trackCount == 0) issues += "لا توجد مسارات وسائط داخل الملف"
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    videoTrack = index
                    width = runCatching { format.getInteger(MediaFormat.KEY_WIDTH) }.getOrNull()
                    height = runCatching { format.getInteger(MediaFormat.KEY_HEIGHT) }.getOrNull()
                    val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrNull()
                    if (durationUs != null && durationUs > 0L) duration = durationUs / 1_000_000.0
                }
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    audioTrack = index
                }
            }
            if (videoTrack >= 0) videoSamples = countSamples(extractor, videoTrack)
            if (audioTrack >= 0) audioSamples = countSamples(extractor, audioTrack)
        } catch (error: Exception) {
            issues += ("تعذرت قراءة ملف MP4: " + (error.message ?: error.javaClass.simpleName)).take(180)
        } finally {
            runCatching { extractor.release() }
        }

        if (!hasVideo) issues += "مسار الفيديو مفقود"
        if (videoSamples <= 0L) issues += "مسار الفيديو لا يحتوي إطارات قابلة للقراءة"
        if (requireAudio && !hasAudio) issues += "مسار المؤثرات الصوتية مفقود"
        if (requireAudio && audioSamples <= 0L) issues += "مسار الصوت لا يحتوي عينات قابلة للقراءة"
        if (width == null || height == null || width!! <= 0 || height!! <= 0) {
            issues += "أبعاد الفيديو مفقودة أو غير صالحة"
        }
        val expected = expectedDurationSeconds.coerceAtLeast(1)
        if (duration == null) {
            issues += "تعذر قراءة مدة الفيديو من مسار MP4"
        } else {
            // Allow small container/encoder rounding, but catch missing or stretched timelines.
            val tolerance = maxOf(1.0, expected * 0.08)
            if (abs(duration!! - expected) > tolerance) {
                issues += "مدة الفيديو الفعلية ${String.format(java.util.Locale.US, "%.2f", duration)} ثانية بدلًا من ${expected} ثانية"
            }
        }

        return Report(
            passed = issues.isEmpty() && hasVideo && (!requireAudio || hasAudio),
            issues = issues.distinct(),
            videoWidth = width,
            videoHeight = height,
            durationSeconds = duration,
            hasAudio = hasAudio,
            videoSamples = videoSamples,
            audioSamples = audioSamples
        )
    }

    private fun countSamples(extractor: MediaExtractor, trackIndex: Int): Long {
        var count = 0L
        var selected = false
        try {
            extractor.selectTrack(trackIndex)
            selected = true
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (extractor.sampleTrackIndex >= 0) {
                if (extractor.sampleTrackIndex == trackIndex) count++
                if (!extractor.advance()) break
            }
        } finally {
            if (selected) runCatching { extractor.unselectTrack(trackIndex) }
        }
        return count
    }
}
