package com.wolfcodes.offline

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tanh

/**
 * Decodes narration and procedural SFX, mixes them as PCM, then encodes one AAC track.
 * The output is a real mix (not two selectable MP4 audio tracks).
 */
object WolfAudioMixer {
    private const val SAMPLE_RATE = 22050
    private const val MIME = "audio/mp4a-latm"
    private const val CHUNK_SAMPLES = 2048

    internal fun noiseGateGain(voiceEnvelope: Double): Double {
        val amount = ((voiceEnvelope - 0.0008) / 0.0042).coerceIn(0.0, 1.0)
        return amount * amount * (3.0 - 2.0 * amount)
    }

    internal fun effectsDuckingGain(voiceEnvelope: Double): Double {
        val amount = ((voiceEnvelope - 0.003) / 0.022).coerceIn(0.0, 1.0)
        val smooth = amount * amount * (3.0 - 2.0 * amount)
        return 1.0 - smooth * 0.58
    }

    internal fun mixSample(narration: Double, effects: Double,
                           narrationGain: Double = 0.92,
                           effectsGain: Double = 0.30,
                           effectsDucking: Double = 1.0): Short {
        val combined = narration * narrationGain.coerceIn(0.0, 1.5) +
            effects * effectsGain.coerceIn(0.0, 1.0) * effectsDucking.coerceIn(0.0, 1.0)
        val limited = tanh(combined * 1.12).coerceIn(-0.98, 0.98)
        return (limited * 32767.0).toInt().toShort()
    }

    fun mix(
        context: Context,
        narrationUri: Uri?,
        effectsFile: File?,
        outputFile: File,
        maxDurationUs: Long,
        narrationGain: Double = 0.92,
        effectsGain: Double = 0.30,
        narrationOffsetUs: Long = 0L,
        narrationLimitUs: Long? = null
    ): File {
        require(narrationUri != null || effectsFile != null) { "لا يوجد مصدر صوت للدمج" }
        require(narrationOffsetUs >= 0L && narrationOffsetUs < maxDurationUs.coerceAtLeast(1L)) {
            "موضع بدء الراوي خارج مدة الفيلم"
        }
        if (effectsFile != null) require(effectsFile.isFile && effectsFile.length() > 0L) {
            "ملف المؤثرات الصوتية غير موجود"
        }
        val directory = outputFile.parentFile ?: throw IllegalStateException("مجلد ملف الدمج غير محدد")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد الدمج الصوتي")
        val narrationPcm = File(directory, outputFile.nameWithoutExtension + "_voice.pcm")
        val effectsPcm = File(directory, outputFile.nameWithoutExtension + "_sfx.pcm")
        val mixedPcm = File(directory, outputFile.nameWithoutExtension + "_mix.pcm")
        var success = false
        try {
            if (narrationUri != null) {
                decodeToPcm(
                    context, narrationUri, null, narrationPcm, maxDurationUs,
                    narrationOffsetUs, narrationLimitUs ?: maxDurationUs
                )
            } else {
                narrationPcm.writeBytes(ByteArray(0))
            }
            if (effectsFile != null) {
                decodeToPcm(context, null, effectsFile, effectsPcm, maxDurationUs)
            } else {
                effectsPcm.writeBytes(ByteArray(0))
            }
            mixPcmFiles(narrationPcm, effectsPcm, mixedPcm, maxDurationUs, narrationGain, effectsGain)
            encodePcmToAac(mixedPcm, outputFile)
            success = outputFile.isFile && outputFile.length() > 0L
            if (!success) throw IllegalStateException("لم ينتج ملف الصوت المدمج")
            return outputFile
        } finally {
            narrationPcm.delete()
            effectsPcm.delete()
            mixedPcm.delete()
            if (!success) outputFile.delete()
        }
    }

    private fun decodeToPcm(
        context: Context,
        sourceUri: Uri?,
        sourceFile: File?,
        output: File,
        maxDurationUs: Long,
        startOffsetUs: Long = 0L,
        sourceDurationLimitUs: Long = maxDurationUs
    ) {
        val extractor = MediaExtractor()
        var descriptor: ParcelFileDescriptor? = null
        var decoder: MediaCodec? = null
        var success = false
        try {
            if (sourceFile != null) {
                extractor.setDataSource(sourceFile.absolutePath)
            } else {
                val uri = sourceUri ?: throw IllegalArgumentException("مصدر الصوت غير محدد")
                if (uri.scheme == "file") {
                    val localFile = uri.path?.let(::File)
                        ?: throw IllegalStateException("مسار ملف السرد الصوتي غير صالح")
                    if (!localFile.isFile) throw IllegalStateException("ملف السرد الصوتي غير موجود")
                    extractor.setDataSource(localFile.absolutePath)
                } else {
                    descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException("تعذر فتح ملف السرد الصوتي")
                    extractor.setDataSource(descriptor!!.fileDescriptor)
                }
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("لم يتم العثور على مسار صوت")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("صيغة الصوت غير معروفة")
            extractor.selectTrack(track)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { fileStream ->
                BufferedOutputStream(fileStream, 64 * 1024).use { pcm ->
                val info = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                var lastInputPts = 0L
                var lastWrittenSample = -1L
                var lastMonoSample = 0.0
                var idleLoops = 0
                val maxSamples = (maxDurationUs.coerceAtLeast(1L) * SAMPLE_RATE / 1_000_000L)
                    .coerceAtLeast(1L)

                while (!outputEnded) {
                    var didWork = false
                    if (!inputEnded) {
                        val inputIndex = decoder.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val input = decoder.getInputBuffer(inputIndex)
                                ?: throw IllegalStateException("تعذر تجهيز مدخل فك ترميز الصوت")
                            input.clear()
                            val pts = extractor.sampleTime
                            val size = if (pts >= 0L && pts < sourceDurationLimitUs) extractor.readSampleData(input, 0) else -1
                            if (size < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, lastInputPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, size, pts, 0)
                                lastInputPts = pts
                                extractor.advance()
                            }
                            didWork = true
                        }
                    }

                    val outputIndex = decoder.dequeueOutputBuffer(info, 10000)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> didWork = true
                        outputIndex >= 0 -> {
                            if (info.size > 0) {
                                val decoded = decoder.getOutputBuffer(outputIndex)
                                    ?: throw IllegalStateException("تعذر قراءة عينات الصوت")
                                decoded.position(info.offset)
                                decoded.limit(info.offset + info.size)
                                val pcmEncoding = if (decoder.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                    decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                else AudioFormat.ENCODING_PCM_16BIT
                                val sampleRate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                if (sampleRate !in 8000..96000 || channels !in 1..8) {
                                    throw IllegalStateException("معدل العينة أو قنوات الصوت غير مدعومة")
                                }
                                val bytesPerSample = when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_16BIT -> 2
                                    AudioFormat.ENCODING_PCM_FLOAT -> 4
                                    else -> throw IllegalStateException("ترميز PCM غير مدعوم")
                                }
                                val frameBytes = channels * bytesPerSample
                                if (info.size % frameBytes != 0) {
                                    throw IllegalStateException("بيانات PCM تحتوي إطارًا غير مكتمل")
                                }
                                decoded.order(ByteOrder.LITTLE_ENDIAN)
                                val frames = info.size / frameBytes
                                val startUs = info.presentationTimeUs.coerceAtLeast(0L)
                                for (frame in 0 until frames) {
                                    val targetIndex = (startOffsetUs + startUs) * SAMPLE_RATE / 1_000_000L +
                                        (frame.toLong() * SAMPLE_RATE / sampleRate)
                                    if (targetIndex >= maxSamples) break
                                    var mono = 0.0
                                    repeat(channels) {
                                        mono += if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                            decoded.getFloat().toDouble()
                                        } else {
                                            decoded.getShort().toDouble() / 32768.0
                                        }
                                    }
                                    mono = (mono / channels).coerceIn(-1.0, 1.0)
                                    if (targetIndex <= lastWrittenSample) continue
                                    // Nearest-neighbour fill keeps lower-rate sources continuous when upsampling.
                                    while (lastWrittenSample + 1L < targetIndex && lastWrittenSample + 1L < maxSamples) {
                                        lastWrittenSample++
                                        writePcmSample(pcm, lastMonoSample)
                                    }
                                    if (targetIndex < maxSamples) {
                                        writePcmSample(pcm, mono)
                                        lastWrittenSample = targetIndex
                                        lastMonoSample = mono
                                    }
                                }
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                            decoder.releaseOutputBuffer(outputIndex, false)
                            didWork = true
                        }
                    }
                    idleLoops = if (didWork) 0 else idleLoops + 1
                    if (idleLoops > 60000) throw IllegalStateException("انتهت مهلة فك ترميز الصوت")
                }
                if (lastWrittenSample < 0L) throw IllegalStateException("ملف الصوت لا يحتوي عينات قابلة للقراءة")
                pcm.flush()
                fileStream.fd.sync()
            }
            success = true
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { descriptor?.close() } catch (_: Exception) {}
            if (!success) output.delete()
        }
    }

    private fun writePcmSample(pcm: OutputStream, sample: Double) {
        val value = (sample.coerceIn(-1.0, 1.0) * 32767.0).toInt()
        pcm.write(value and 0xff)
        pcm.write((value shr 8) and 0xff)
    }

    private fun mixPcmFiles(
        narration: File,
        effects: File,
        output: File,
        maxDurationUs: Long,
        narrationGain: Double,
        effectsGain: Double
    ) {
        val maxBytes = maxDurationUs.coerceAtLeast(1L) * SAMPLE_RATE * 2L / 1_000_000L
        RandomAccessFile(narration, "r").use { voice ->
            RandomAccessFile(effects, "r").use { sfx ->
                RandomAccessFile(output, "rw").use { mixed ->
                    mixed.setLength(0L)
                    val voiceBuffer = ByteArray(CHUNK_SAMPLES * 2)
                    val sfxBuffer = ByteArray(CHUNK_SAMPLES * 2)
                    var offset = 0L
                    var previousVoiceInput = 0.0
                    var previousVoiceOutput = 0.0
                    var voiceEnvelope = 0.0
                    while (offset < maxBytes) {
                        val count = minOf(voiceBuffer.size.toLong(), maxBytes - offset).toInt()
                        val aligned = count - count % 2
                        if (aligned <= 0) break
                        voiceBuffer.fill(0)
                        sfxBuffer.fill(0)
                        voice.seek(offset)
                        sfx.seek(offset)
                        val voiceRead = voice.read(voiceBuffer, 0, aligned).coerceAtLeast(0)
                        val sfxRead = sfx.read(sfxBuffer, 0, aligned).coerceAtLeast(0)
                        if (voiceRead == 0 && sfxRead == 0) break
                        val frames = aligned / 2
                        val bytes = ByteArray(aligned)
                        for (i in 0 until frames) {
                            val v = if (i * 2 + 1 < voiceRead) {
                                (((voiceBuffer[i * 2 + 1].toInt() shl 8) or
                                    (voiceBuffer[i * 2].toInt() and 0xff)).toShort()).toDouble() / 32768.0
                            } else 0.0
                            val e = if (i * 2 + 1 < sfxRead) {
                                (((sfxBuffer[i * 2 + 1].toInt() shl 8) or
                                    (sfxBuffer[i * 2].toInt() and 0xff)).toShort()).toDouble() / 32768.0
                            } else 0.0
                            // Remove DC/very-low-frequency rumble, gently gate the noise floor,
                            // and duck effects while the narration is actively speaking.
                            val highPassedVoice = v - previousVoiceInput + 0.995 * previousVoiceOutput
                            previousVoiceInput = v
                            previousVoiceOutput = highPassedVoice
                            voiceEnvelope = maxOf(kotlin.math.abs(highPassedVoice), voiceEnvelope * 0.9992)
                            val cleanedVoice = highPassedVoice * noiseGateGain(voiceEnvelope)
                            val sample = mixSample(
                                cleanedVoice, e, narrationGain, effectsGain,
                                effectsDuckingGain(voiceEnvelope)
                            ).toInt()
                            bytes[i * 2] = sample.toByte()
                            bytes[i * 2 + 1] = (sample shr 8).toByte()
                        }
                        mixed.write(bytes)
                        offset += aligned
                    }
                    if (mixed.length() <= 0L) throw IllegalStateException("تعذر إنشاء مزيج صوتي")
                    mixed.fd.sync()
                }
            }
        }
    }

    private fun encodePcmToAac(inputFile: File, outputFile: File) {
        val format = MediaFormat.createAudioFormat(MIME, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, 2)
            setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2)
        }
        val encoder = MediaCodec.createEncoderByType(MIME)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            RandomAccessFile(inputFile, "r").use { pcm ->
                val info = MediaCodec.BufferInfo()
                var samplesQueued = 0L
                val totalSamples = pcm.length() / 2L
                var inputEnded = false
                var outputEnded = false
                var audioTrack = -1
                var idleLoops = 0
                while (!outputEnded) {
                    var didWork = false
                    if (!inputEnded) {
                        val index = encoder.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val buffer = encoder.getInputBuffer(index)
                                ?: throw IllegalStateException("تعذر تجهيز مدخل تشفير AAC")
                            buffer.clear()
                            val remaining = totalSamples - samplesQueued
                            if (remaining <= 0L) {
                                encoder.queueInputBuffer(index, 0, 0,
                                    samplesQueued * 1_000_000L / SAMPLE_RATE,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                val count = minOf(buffer.remaining() / 2L, CHUNK_SAMPLES.toLong(), remaining).toInt()
                                val bytes = ByteArray(count * 2)
                                pcm.readFully(bytes)
                                buffer.put(bytes)
                                encoder.queueInputBuffer(index, 0, bytes.size,
                                    samplesQueued * 1_000_000L / SAMPLE_RATE, 0)
                                samplesQueued += count
                            }
                            didWork = true
                        }
                    }
                    val index = encoder.dequeueOutputBuffer(info, 10000)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw IllegalStateException("تغير تنسيق AAC أثناء التشفير")
                            audioTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                            didWork = true
                        }
                        index >= 0 -> {
                            val encoded = encoder.getOutputBuffer(index)
                            val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (encoded != null && info.size > 0 && muxerStarted && !config) {
                                encoded.position(info.offset)
                                encoded.limit(info.offset + info.size)
                                muxer.writeSampleData(audioTrack, encoded, info)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                            encoder.releaseOutputBuffer(index, false)
                            didWork = true
                        }
                    }
                    idleLoops = if (didWork) 0 else idleLoops + 1
                    if (idleLoops > 60000) throw IllegalStateException("انتهت مهلة تشفير المزيج الصوتي")
                }
            }
            if (!muxerStarted) throw IllegalStateException("لم ينتج مشفر AAC مسارًا")
            success = true
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            if (!success || !outputFile.isFile || outputFile.length() <= 0L) outputFile.delete()
        }
    }
}
