package com.wolfcodes.offline

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Local, deterministic, scene-aware horror sound design.
 * Non-verbal SFX only: no music, narration or dialogue.
 * This is a procedural fallback, not a substitute for licensed cinematic recordings.
 */
object WolfSoundFxEngine {
    enum class Cue {
        WIND, HEARTBEAT, KNOCK, FOOTSTEPS, DOOR_CREAK, BREATH,
        WHISPER_TEXTURE, IMPACT, STATIC, SHRIEK, GROWL, WOLF_HOWL, NON_HUMAN_LAUGH
    }

    data class CueEvent(val cue: Cue, val startSeconds: Double, val durationSeconds: Double, val gain: Double)

    data class SceneSoundPlan(
        val cues: Set<Cue>,
        val intensity: Double,
        val events: List<CueEvent> = emptyList()
    )

    private const val SAMPLE_RATE = 22050
    private const val MIME = "audio/mp4a-latm"
    private const val CHUNK_SAMPLES = 1024

    /** Deterministic first-pass Arabic event tagging. */
    fun classifyScene(text: String): SceneSoundPlan {
        val cues = linkedSetOf(Cue.WIND)
        fun has(vararg words: String) = words.any { text.contains(it, ignoreCase = true) }

        if (has("خوف", "قلب", "خفقان", "يرتجف", "مطاردة", "مرعوب", "رعب")) cues += Cue.HEARTBEAT
        if (has("طرق", "يطرق", "خبط", "دق الباب", "طرقات")) cues += Cue.KNOCK
        if (has("خطوات", "يمشي", "يجري", "ركض", "أقدام", "حذاء")) cues += Cue.FOOTSTEPS
        if (has("باب", "صرير", "مفصلات", "مقبض", "فتح الباب", "يغلق الباب")) cues += Cue.DOOR_CREAK
        if (has("أنفاس", "يتنفس", "يلهث", "نَفَس", "نفسه")) cues += Cue.BREATH
        if (has("همس", "يهمس", "صوت خافت", "ينادي", "صوت غير مفهوم")) cues += Cue.WHISPER_TEXTURE
        if (has("ارتطم", "اصطدم", "سقط", "انفجر", "تحطم", "كسر", "فجأة")) cues += Cue.IMPACT
        if (has("تشويش", "لاسلكي", "راديو", "تلفاز", "إشارة مقطوعة")) cues += Cue.STATIC
        if (has("صرخ", "صرخة", "صراخ", "استغاث")) cues += Cue.SHRIEK
        if (has("زأر", "زئير", "هدير", "زمجر", "وحش", "مسخ")) cues += Cue.GROWL
        if (has("عواء", "يعوي", "عوى", "عواء الذئب")) cues += Cue.WOLF_HOWL
        if (has("ضحكة", "ضحك", "يضحك", "قهقهة", "قهقه", "قهقهات") &&
            has("شبح", "كيان", "غير بشري", "مخلوق", "روح", "مسخ")) {
            cues += Cue.NON_HUMAN_LAUGH
        }

        val intensity = when {
            Cue.SHRIEK in cues || Cue.IMPACT in cues || Cue.GROWL in cues -> 1.0
            Cue.KNOCK in cues || Cue.FOOTSTEPS in cues || Cue.DOOR_CREAK in cues -> 0.78
            Cue.WHISPER_TEXTURE in cues || Cue.BREATH in cues -> 0.58
            else -> 0.38
        }
        return SceneSoundPlan(cues, intensity)
    }

    fun classifyScenes(scenes: List<String>): List<SceneSoundPlan> = scenes.map(::classifyScene)

    /** Places individual SFX in a scene timeline rather than looping every cue for the full scene. */
    fun buildScenePlan(text: String, durationSeconds: Int): SceneSoundPlan {
        val base = classifyScene(text)
        val duration = durationSeconds.coerceIn(1, 1800).toDouble()
        val events = mutableListOf<CueEvent>()
        fun schedule(cue: Cue, startFraction: Double, lengthFraction: Double, minLength: Double, gain: Double) {
            if (cue !in base.cues) return
            val length = min(duration, max(minLength, duration * lengthFraction))
            val latestStart = (duration - length).coerceAtLeast(0.0)
            val start = (duration * startFraction).coerceIn(0.0, latestStart)
            events += CueEvent(cue, start, length, gain.coerceIn(0.0, 1.0))
        }
        schedule(Cue.HEARTBEAT, 0.22, 0.62, 1.0, 0.34)
        schedule(Cue.KNOCK, 0.46, 0.22, 1.05, 0.48)
        schedule(Cue.FOOTSTEPS, 0.12, 0.62, 1.45, 0.34)
        schedule(Cue.DOOR_CREAK, 0.58, 0.30, 1.20, 0.30)
        schedule(Cue.BREATH, 0.34, 0.52, 1.40, 0.25)
        schedule(Cue.WHISPER_TEXTURE, 0.20, 0.66, 1.50, 0.18)
        schedule(Cue.IMPACT, 0.76, 0.16, 0.62, 0.58)
        schedule(Cue.STATIC, 0.38, 0.18, 0.55, 0.22)
        schedule(Cue.SHRIEK, 0.78, 0.18, 1.05, 0.27)
        schedule(Cue.GROWL, 0.50, 0.34, 1.35, 0.34)
        schedule(Cue.WOLF_HOWL, 0.58, 0.34, 1.85, 0.42)
        schedule(Cue.NON_HUMAN_LAUGH, 0.64, 0.25, 1.25, 0.24)
        return base.copy(events = events.sortedBy { it.startSeconds })
    }

    fun buildScenePlans(scenes: List<String>, durationsSeconds: List<Int>): List<SceneSoundPlan> {
        require(scenes.size == durationsSeconds.size) { "عدد مدد المشاهد لا يطابق عدد المشاهد" }
        return scenes.indices.map { buildScenePlan(scenes[it], durationsSeconds[it]) }
    }

    internal fun applyLimiter(sample: Double, gain: Double = 1.0): Double =
        kotlin.math.tanh(sample * gain.coerceIn(0.5, 1.5) * 1.45).coerceIn(-0.96, 0.96)

    fun renderAac(
        scenes: List<String>,
        durationSeconds: Int,
        output: File,
        introSeconds: Int = 0,
        sceneFrameCounts: List<Int>? = null,
        framesPerSecond: Int = 12,
        masterGain: Double = 1.0
    ) {
        require(scenes.isNotEmpty()) { "لا توجد مشاهد لتصميم الصوت" }
        val duration = durationSeconds.coerceIn(1, 1803)
        val totalSamples = duration.toLong() * SAMPLE_RATE
        val introSamples = introSeconds.coerceIn(0, duration) * SAMPLE_RATE.toLong()
        val storySamples = (totalSamples - introSamples).coerceAtLeast(1L)
        output.parentFile?.mkdirs()
        val safeMasterGain = masterGain.coerceIn(0.5, 1.5)
        val format = MediaFormat.createAudioFormat(MIME, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, 2)
            setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var audioTrack = -1
        var inputSamples = 0L
        var encodingSucceeded = false
        var seed = 0x13579BDF
        var wind = 0.0
        var whisperNoise = 0.0
        var previousNoise = 0.0
        require(framesPerSecond in 1..60) { "معدل الإطارات غير صالح لمزامنة المؤثرات" }
        val frameCounts = sceneFrameCounts?.takeIf { counts ->
            counts.size == scenes.size && counts.all { it > 0 }
        } ?: List(scenes.size) { 1 }
        val totalSceneFrames = frameCounts.sum().toLong().coerceAtLeast(1L)
        var cumulativeFrames = 0L
        val sceneEndSamples = LongArray(scenes.size) { index ->
            cumulativeFrames += frameCounts[index]
            if (index == scenes.lastIndex) storySamples
            else storySamples * cumulativeFrames / totalSceneFrames
        }
        var previousSceneEnd = 0L
        val profiles = scenes.indices.map { index ->
            val sceneLength = (sceneEndSamples[index] - previousSceneEnd).coerceAtLeast(1L)
            previousSceneEnd = sceneEndSamples[index]
            val sceneSeconds = (sceneLength.toDouble() / SAMPLE_RATE).toInt().coerceAtLeast(1)
            buildScenePlan(scenes[index], sceneSeconds)
        }
        var previousDcInput = 0.0
        var previousDcOutput = 0.0
        var activeSceneIndex = 0
        var activeSceneStartSample = 0L

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("تعذر تجهيز مخزن الصوت")
                        buffer.clear()
                        if (inputSamples >= totalSamples) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0,
                                inputSamples * 1_000_000L / SAMPLE_RATE,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val count = minOf(CHUNK_SAMPLES.toLong(), totalSamples - inputSamples).toInt()
                            for (i in 0 until count) {
                                val absoluteSample = inputSamples + i
                                val inIntro = absoluteSample < introSamples
                                val storySample = (absoluteSample - introSamples).coerceAtLeast(0L)
                                if (!inIntro) {
                                    while (activeSceneIndex < profiles.lastIndex &&
                                        storySample >= sceneEndSamples[activeSceneIndex]
                                    ) {
                                        activeSceneStartSample = sceneEndSamples[activeSceneIndex]
                                        activeSceneIndex++
                                    }
                                }
                                val localSample = if (inIntro) absoluteSample else storySample - activeSceneStartSample
                                val localTime = localSample.toDouble() / SAMPLE_RATE
                                val profile = profiles[activeSceneIndex]

                                seed = seed xor (seed shl 13)
                                seed = seed xor (seed ushr 17)
                                seed = seed xor (seed shl 5)
                                val noise = ((seed and 0x7fffffff).toDouble() / 1073741823.5) - 1.0
                                wind = wind * 0.988 + noise * 0.012
                                whisperNoise = whisperNoise * 0.84 + noise * 0.16
                                val highNoise = noise - previousNoise
                                previousNoise = noise

                                val gust = 0.62 + 0.38 * sin(2.0 * PI * localTime / 7.4)
                                var sample = wind * if (inIntro) 0.12 else 0.26 * gust
                                sample += sin(2.0 * PI * 39.0 * absoluteSample / SAMPLE_RATE) * 0.025

                                if (inIntro) {
                                    sample += wolfHowl(localTime) * 0.48
                                } else {
                                    profile.events.forEach { event ->
                                        val age = localTime - event.startSeconds
                                        if (age >= 0.0 && age <= event.durationSeconds) {
                                            val envelope = eventEnvelope(age, event.durationSeconds)
                                            sample += renderCue(event.cue, age, event.durationSeconds, noise, whisperNoise, highNoise) *
                                                event.gain * envelope
                                        }
                                    }
                                }

                                val sceneLengthSamples = sceneEndSamples[activeSceneIndex] - activeSceneStartSample
                                val boundaryFadeSamples = (SAMPLE_RATE * 0.14).toLong().coerceAtLeast(1L)
                                val fadeIn = (localSample.toDouble() / boundaryFadeSamples).coerceIn(0.0, 1.0)
                                val fadeOut = ((sceneLengthSamples - localSample).toDouble() / boundaryFadeSamples).coerceIn(0.0, 1.0)
                                val sceneEnvelope = min(fadeIn, fadeOut)
                                val globalFadeIn = (absoluteSample.toDouble() / (SAMPLE_RATE * 0.12)).coerceIn(0.0, 1.0)
                                val globalFadeOut = ((totalSamples - absoluteSample).toDouble() / (SAMPLE_RATE * 0.28)).coerceIn(0.0, 1.0)
                                val masterEnvelope = min(globalFadeIn, globalFadeOut)
                                val intensity = if (inIntro) 0.78 else profile.intensity.coerceAtLeast(0.35)
                                val stagedSample = sample * intensity * sceneEnvelope * masterEnvelope
                                val dcBlocked = stagedSample - previousDcInput + 0.995 * previousDcOutput
                                previousDcInput = stagedSample
                                previousDcOutput = dcBlocked
                                val normalized = applyLimiter(dcBlocked, safeMasterGain)
                                buffer.putShort((normalized * 32767.0).toInt().toShort())
                            }
                            codec.queueInputBuffer(
                                inputIndex, 0, count * 2,
                                inputSamples * 1_000_000L / SAMPLE_RATE, 0
                            )
                            inputSamples += count
                        }
                    }
                }

                var drained = false
                while (!drained) {
                    val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> drained = true
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw IllegalStateException("تغير تنسيق AAC أثناء التشفير")
                            audioTrack = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputIndex >= 0 -> {
                            val encoded = codec.getOutputBuffer(outputIndex)
                            if (encoded != null && info.size > 0 && muxerStarted) {
                                encoded.position(info.offset)
                                encoded.limit(info.offset + info.size)
                                muxer.writeSampleData(audioTrack, encoded, info)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (outputDone) drained = true
                        }
                    }
                }
            }
            if (!muxerStarted) throw IllegalStateException("لم ينتج مشفر AAC مسارًا صوتيًا")
            encodingSucceeded = true
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            muxer?.release()
            if (!encodingSucceeded || !output.exists() || output.length() == 0L) output.delete()
        }
    }

    private fun renderCue(cue: Cue, age: Double, duration: Double, noise: Double, filteredNoise: Double, highNoise: Double): Double =
        when (cue) {
            Cue.WIND -> noise * 0.08
            Cue.HEARTBEAT -> heartbeat(age)
            Cue.KNOCK -> knocks(age)
            Cue.FOOTSTEPS -> footsteps(age)
            Cue.DOOR_CREAK -> doorCreak(age, duration)
            Cue.BREATH -> breath(age, duration, noise, filteredNoise)
            Cue.WHISPER_TEXTURE -> filteredNoise * (0.58 + 0.42 * sin(2.0 * PI * 6.2 * age))
            Cue.IMPACT -> impact(age)
            Cue.STATIC -> highNoise * (expPulse(age, 0.10, 0.06) + expPulse(age, 0.36, 0.07) * 0.58)
            Cue.SHRIEK -> shriek(age, duration)
            Cue.GROWL -> growl(age)
            Cue.WOLF_HOWL -> wolfHowl(age + 0.32)
            Cue.NON_HUMAN_LAUGH -> nonHumanLaugh(age)
        }

    private fun eventEnvelope(age: Double, duration: Double): Double {
        if (age < 0.0 || age > duration) return 0.0
        val attack = (duration * 0.10).coerceIn(0.025, 0.16)
        val release = (duration * 0.18).coerceIn(0.08, 0.32)
        return min((age / attack).coerceIn(0.0, 1.0), ((duration - age) / release).coerceIn(0.0, 1.0))
    }

    private fun wolfHowl(t: Double): Double {
        val age = t - 0.32
        if (age < 0.0 || age > 2.1) return 0.0
        val progress = age / 2.1
        val envelope = sin(PI * progress).coerceAtLeast(0.0)
        val phase = 2.0 * PI * (235.0 * age + 82.0 * age * age + 18.0 * age * age * age)
        val wavering = 1.0 + 0.035 * sin(2.0 * PI * 5.2 * age)
        return envelope * envelope * wavering *
            (sin(phase) + 0.34 * sin(phase * 1.98) + 0.12 * sin(phase * 3.03))
    }

    private fun heartbeat(t: Double): Double {
        val phase = t % 1.28
        val first = expPulse(phase, 0.075, 0.05)
        val second = expPulse(phase, 0.26, 0.045) * 0.72
        return (first + second) * (sin(2.0 * PI * 48.0 * t) + 0.22 * sin(2.0 * PI * 73.0 * t))
    }

    private fun knocks(t: Double): Double {
        val hit = expPulse(t, 0.10, 0.030) +
            expPulse(t, 0.46, 0.034) * 0.92 +
            expPulse(t, 0.84, 0.040) * 0.78
        val body = sin(2.0 * PI * 92.0 * t) + 0.25 * sin(2.0 * PI * 184.0 * t)
        return hit * body
    }

    private fun footsteps(t: Double): Double {
        val phase = t % 1.12
        val left = expPulse(phase, 0.12, 0.055)
        val right = expPulse(phase, 0.66, 0.06) * 0.86
        val body = sin(2.0 * PI * 54.0 * t) + 0.28 * sin(2.0 * PI * 108.0 * t)
        return (left + right) * body
    }

    private fun doorCreak(t: Double, duration: Double): Double {
        val u = (t / duration.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
        val envelope = sin(PI * u).coerceAtLeast(0.0)
        val sweep = 145.0 + 90.0 * sin(PI * u) + 32.0 * u
        val tone = sin(2.0 * PI * sweep * t) +
            0.36 * sin(2.0 * PI * (sweep * 2.03) * t)
        return tone * envelope
    }

    private fun breath(t: Double, duration: Double, noise: Double, filteredNoise: Double): Double {
        val inhale = expPulse(t, duration * 0.30, duration * 0.12) * 0.82
        val exhale = expPulse(t, duration * 0.72, duration * 0.16)
        return (inhale + exhale) * (noise * 0.52 + filteredNoise * 0.48)
    }

    private fun impact(t: Double): Double {
        val hit = expPulse(t, 0.10, 0.050) + expPulse(t, 0.38, 0.075) * 0.34
        val sweep = 68.0 - 22.0 * (t.coerceAtMost(0.65) / 0.65)
        return hit * (sin(2.0 * PI * sweep * t) + 0.22 * sin(2.0 * PI * sweep * 2.1 * t))
    }

    private fun shriek(t: Double, duration: Double): Double {
        val u = (t / duration.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
        val sweep = 280.0 * t + 430.0 * t * t
        return (sin(2.0 * PI * sweep) + 0.24 * sin(2.0 * PI * sweep * 2.7)) * sin(PI * u).coerceAtLeast(0.0)
    }

    private fun growl(t: Double): Double {
        val modulation = 1.0 + 0.17 * sin(2.0 * PI * 3.2 * t)
        val base = sin(2.0 * PI * 47.0 * t) + 0.48 * sin(2.0 * PI * 94.0 * t)
        val pulse = 0.65 + 0.35 * sin(2.0 * PI * 0.7 * t)
        return base * modulation * pulse
    }

    /** Abstract non-verbal rhythmic texture, not a recorded human laugh. */
    private fun nonHumanLaugh(t: Double): Double {
        val phase = t % 4.6
        val pulses = expPulse(phase, 0.38, 0.09) +
            expPulse(phase, 0.72, 0.08) * 0.78 +
            expPulse(phase, 1.06, 0.095) * 0.68
        val pitch = 245.0 + 55.0 * sin(2.0 * PI * 2.4 * t)
        return pulses * (sin(2.0 * PI * pitch * t) + 0.22 * sin(2.0 * PI * pitch * 2.0 * t))
    }

    private fun expPulse(value: Double, center: Double, width: Double): Double {
        val distance = (value - center) / width
        return if (abs(distance) > 8.0) 0.0 else exp(-0.5 * distance * distance)
    }
}
