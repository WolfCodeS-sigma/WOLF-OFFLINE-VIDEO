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
        val audioSamples: Long = 0L,
        val estimatedFrameRate: Double? = null,
        val audioStartTimeUs: Long? = null,
        val audioEndTimeUs: Long? = null
    ) {
        val summary: String
            get() = if (passed) {
                "فحص MP4 ناجح: مسار الفيديو موجود" +
                    (if (hasAudio) " ومسار الصوت موجود" else "") +
                    (if (videoSamples > 0L) " — عدد إطارات الفيديو $videoSamples" else "") +
                    (estimatedFrameRate?.let { " — FPS " + String.format(java.util.Locale.US, "%.2f", it) } ?: "") +
                    (durationSeconds?.let { " — المدة " + String.format(java.util.Locale.US, "%.1f", it) + " ثانية" } ?: "")
            } else {
                "فحص MP4 يحتاج مراجعة: " + issues.joinToString("، ")
            }
    }

    private data class TrackSamples(
        val count: Long,
        val firstPresentationTimeUs: Long?,
        val lastPresentationTimeUs: Long?
    )

    fun inspect(
        context: Context,
        mediaReference: String,
        expectedDurationSeconds: Int,
        requireAudio: Boolean,
        expectedVideoWidth: Int? = null,
        expectedVideoHeight: Int? = null,
        expectedVideoFrames: Long? = null,
        expectedFrameRate: Int? = null,
        expectedAudioStartUs: Long? = null,
        expectedAudioEndUs: Long? = null
    ): Report {
        val issues = mutableListOf<String>()
        var width: Int? = null
        var height: Int? = null
        var duration: Double? = null
        var hasVideo = false
        var hasAudio = false
        var videoTrack = -1
        var audioTrack = -1
        var videoSamples = TrackSamples(0L, null, null)
        var audioSamples = TrackSamples(0L, null, null)
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
            if (videoTrack >= 0) videoSamples = inspectSamples(extractor, videoTrack)
            if (audioTrack >= 0) audioSamples = inspectSamples(extractor, audioTrack)
        } catch (error: Exception) {
            issues += ("تعذرت قراءة ملف MP4: " + (error.message ?: error.javaClass.simpleName)).take(180)
        } finally {
            runCatching { extractor.release() }
        }

        if (!hasVideo) issues += "مسار الفيديو مفقود"
        if (videoSamples.count <= 0L) issues += "مسار الفيديو لا يحتوي إطارات قابلة للقراءة"
        if (requireAudio && !hasAudio) issues += "مسار المؤثرات الصوتية مفقود"
        if (requireAudio && audioSamples.count <= 0L) issues += "مسار الصوت لا يحتوي عينات قابلة للقراءة"
        if (width == null || height == null || width!! <= 0 || height!! <= 0) {
            issues += "أبعاد الفيديو مفقودة أو غير صالحة"
        }
        if (expectedVideoWidth != null && width != expectedVideoWidth) {
            issues += "عرض الفيديو الفعلي $width لا يطابق العرض المطلوب $expectedVideoWidth"
        }
        if (expectedVideoHeight != null && height != expectedVideoHeight) {
            issues += "ارتفاع الفيديو الفعلي $height لا يطابق الارتفاع المطلوب $expectedVideoHeight"
        }
        if (expectedVideoFrames != null && videoSamples.count != expectedVideoFrames) {
            issues += "عدد إطارات الفيديو الفعلي ${videoSamples.count} بدلًا من $expectedVideoFrames"
        }

        val videoTimestampSpanUs = if (
            videoSamples.firstPresentationTimeUs != null && videoSamples.lastPresentationTimeUs != null
        ) {
            videoSamples.lastPresentationTimeUs!! - videoSamples.firstPresentationTimeUs!!
        } else 0L
        val estimatedFrameRate = if (videoSamples.count > 1L && videoTimestampSpanUs > 0L) {
            (videoSamples.count - 1L) * 1_000_000.0 / videoTimestampSpanUs.toDouble()
        } else null
        if (expectedFrameRate != null) {
            if (estimatedFrameRate == null) {
                issues += "تعذر قياس معدل إطارات الفيديو"
            } else if (abs(estimatedFrameRate - expectedFrameRate.toDouble()) > 0.5) {
                issues += "معدل الإطارات المقاس ${String.format(java.util.Locale.US, "%.2f", estimatedFrameRate)} FPS بدلًا من $expectedFrameRate FPS"
            }
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

        if (expectedAudioStartUs != null) {
            val actualStartUs = audioSamples.firstPresentationTimeUs
            if (actualStartUs == null || abs(actualStartUs - expectedAudioStartUs) > 250_000L) {
                issues += "موضع بداية الصوت لا يطابق التوقيت المتوقع بعد المقدمة"
            }
        }
        if (expectedAudioEndUs != null) {
            val actualEndUs = audioSamples.lastPresentationTimeUs
            if (actualEndUs == null || abs(actualEndUs - expectedAudioEndUs) > 500_000L) {
                issues += "نهاية مسار الراوي لا تطابق مدة التسجيل الكاملة"
            }
        }

        return Report(
            passed = issues.isEmpty() && hasVideo && (!requireAudio || hasAudio),
            issues = issues.distinct(),
            videoWidth = width,
            videoHeight = height,
            durationSeconds = duration,
            hasAudio = hasAudio,
            videoSamples = videoSamples.count,
            audioSamples = audioSamples.count,
            estimatedFrameRate = estimatedFrameRate,
            audioStartTimeUs = audioSamples.firstPresentationTimeUs,
            audioEndTimeUs = audioSamples.lastPresentationTimeUs
        )
    }

    private fun inspectSamples(extractor: MediaExtractor, trackIndex: Int): TrackSamples {
        var count = 0L
        var firstPresentationTimeUs: Long? = null
        var lastPresentationTimeUs: Long? = null
        var selected = false
        try {
            extractor.selectTrack(trackIndex)
            selected = true
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (extractor.sampleTrackIndex >= 0) {
                if (extractor.sampleTrackIndex == trackIndex) {
                    val presentationTimeUs = extractor.sampleTime
                    if (presentationTimeUs >= 0L) {
                        if (firstPresentationTimeUs == null) firstPresentationTimeUs = presentationTimeUs
                        lastPresentationTimeUs = presentationTimeUs
                    }
                    count++
                }
                if (!extractor.advance()) break
            }
        } finally {
            if (selected) runCatching { extractor.unselectTrack(trackIndex) }
        }
        return TrackSamples(count, firstPresentationTimeUs, lastPresentationTimeUs)
    }
}
