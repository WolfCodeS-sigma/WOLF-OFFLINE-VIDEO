package com.wolfcodes.offline

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Converts external audio to AAC using Android codecs on the phone.
 * Existing AAC streams are passed through unchanged.
 */
object WolfAudioTranscoder {
    private const val AAC_MIME = "audio/mp4a-latm"
    private const val AAC_ALIAS_MIME = "audio/aac"

    internal fun requiresTranscoding(mime: String): Boolean =
        mime != AAC_MIME && mime != AAC_ALIAS_MIME

    fun transcodeToAacIfNeeded(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        maxDurationUs: Long
    ): File? {
        val descriptor = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalStateException("تعذر فتح ملف الصوت المختار")
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false
        try {
            extractor.setDataSource(descriptor.fileDescriptor)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IllegalStateException("لم يتم العثور على مسار صوت داخل الملف")
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val sourceMime = sourceFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!requiresTranscoding(sourceMime)) return null

            val sampleRate = if (sourceFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channels = if (sourceFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            if (sampleRate !in 8000..48000 || channels !in 1..2) {
                throw IllegalStateException("معدل العينة أو عدد قنوات الصوت غير مدعوم في هذا الملف")
            }

            extractor.selectTrack(trackIndex)
            decoder = MediaCodec.createDecoderByType(sourceMime)
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()

            val encoderFormat = MediaFormat.createAudioFormat(AAC_MIME, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, 2)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(AAC_MIME)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val decodeInfo = MediaCodec.BufferInfo()
            val encodeInfo = MediaCodec.BufferInfo()
            val frameBytes = channels * 2
            val durationLimitUs = maxDurationUs.coerceAtLeast(1L)
            var extractorEnded = false
            var decoderEnded = false
            var encoderInputEnded = false
            var encoderOutputEnded = false
            var audioTrack = -1
            var pendingPcm: ByteArray? = null
            var pendingOffset = 0
            var pendingPtsUs = 0L
            var lastPtsUs = 0L
            var idleLoops = 0

            while (!encoderOutputEnded) {
                var didWork = false

                if (!extractorEnded) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = decoder.getInputBuffer(index)
                            ?: throw IllegalStateException("تعذر تجهيز مدخل فك ترميز الصوت")
                        input.clear()
                        val sampleTime = extractor.sampleTime
                        val size = if (sampleTime >= 0L && sampleTime < durationLimitUs) {
                            extractor.readSampleData(input, 0)
                        } else -1
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, lastPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, sampleTime, 0)
                            lastPtsUs = sampleTime
                            extractor.advance()
                        }
                        didWork = true
                    }
                }

                if (pendingPcm == null && !decoderEnded) {
                    val index = decoder.dequeueOutputBuffer(decodeInfo, 10_000)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val decodedFormat = decoder.outputFormat
                            val rate = if (decodedFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            } else sampleRate
                            val count = if (decodedFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            } else channels
                            val encoding = if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else AudioFormat.ENCODING_PCM_16BIT
                            if (rate != sampleRate || count != channels || encoding != AudioFormat.ENCODING_PCM_16BIT) {
                                throw IllegalStateException("صيغة الصوت المفكوكة غير متوافقة مع محول AAC")
                            }
                            didWork = true
                        }
                        index >= 0 -> {
                            if (decodeInfo.size > 0) {
                                val decoded = decoder.getOutputBuffer(index)
                                    ?: throw IllegalStateException("تعذر قراءة عينات الصوت")
                                decoded.position(decodeInfo.offset)
                                decoded.limit(decodeInfo.offset + decodeInfo.size)
                                val bytes = ByteArray(decodeInfo.size)
                                decoded.get(bytes)
                                if (bytes.size % frameBytes != 0) {
                                    throw IllegalStateException("ملف الصوت يحتوي عينة غير مكتملة")
                                }
                                pendingPcm = bytes
                                pendingOffset = 0
                                pendingPtsUs = decodeInfo.presentationTimeUs.coerceAtLeast(0L)
                            }
                            if (decodeInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decoderEnded = true
                            }
                            decoder.releaseOutputBuffer(index, false)
                            didWork = true
                        }
                    }
                }

                if (pendingPcm != null && !encoderInputEnded) {
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = encoder.getInputBuffer(index)
                            ?: throw IllegalStateException("تعذر تجهيز مدخل AAC")
                        input.clear()
                        val pending = pendingPcm ?: ByteArray(0)
                        val available = pending.size - pendingOffset
                        val bytesToWrite = min(input.remaining(), available)
                        val alignedBytes = bytesToWrite - bytesToWrite % frameBytes
                        if (alignedBytes <= 0) {
                            throw IllegalStateException("مساحة مدخل AAC أصغر من عينة صوت واحدة")
                        }
                        input.put(pending, pendingOffset, alignedBytes)
                        val samplesBeforeChunk = pendingOffset / frameBytes
                        val ptsUs = pendingPtsUs + samplesBeforeChunk.toLong() * 1_000_000L / sampleRate
                        encoder.queueInputBuffer(index, 0, alignedBytes, ptsUs, 0)
                        pendingOffset += alignedBytes
                        if (pendingOffset >= pending.size) {
                            pendingPcm = null
                            pendingOffset = 0
                        }
                        didWork = true
                    }
                } else if (decoderEnded && pendingPcm == null && !encoderInputEnded) {
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        encoder.queueInputBuffer(index, 0, 0, lastPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        encoderInputEnded = true
                        didWork = true
                    }
                }

                val encodedIndex = encoder.dequeueOutputBuffer(encodeInfo, 10_000)
                when {
                    encodedIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) throw IllegalStateException("تغير تنسيق AAC أثناء التحويل")
                        audioTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        didWork = true
                    }
                    encodedIndex >= 0 -> {
                        val encoded = encoder.getOutputBuffer(encodedIndex)
                        val codecConfig = encodeInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (encoded != null && encodeInfo.size > 0 && muxerStarted && !codecConfig) {
                            encoded.position(encodeInfo.offset)
                            encoded.limit(encodeInfo.offset + encodeInfo.size)
                            muxer.writeSampleData(audioTrack, encoded, encodeInfo)
                        }
                        if (encodeInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderOutputEnded = true
                        }
                        encoder.releaseOutputBuffer(encodedIndex, false)
                        didWork = true
                    }
                }

                idleLoops = if (didWork) 0 else idleLoops + 1
                if (idleLoops > 60000) throw IllegalStateException("انتهت مهلة تحويل الصوت إلى AAC")
            }

            if (!muxerStarted || !outputFile.isFile || outputFile.length() <= 0L) {
                throw IllegalStateException("لم ينتج محول الصوت ملف AAC صالحًا")
            }
            success = true
            return outputFile
        } catch (error: Exception) {
            throw IllegalStateException(
                "تعذر تحويل ملف الصوت إلى AAC على هذا الهاتف: ${error.message ?: error.javaClass.simpleName}",
                error
            )
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { descriptor.close() } catch (_: Exception) {}
            if (!success) outputFile.delete()
        }
    }
}
