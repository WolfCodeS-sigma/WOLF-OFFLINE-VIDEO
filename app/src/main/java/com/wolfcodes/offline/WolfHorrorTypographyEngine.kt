package com.wolfcodes.offline

import kotlin.math.roundToInt

/**
 * Offline, deterministic selector for brief atmospheric horror phrases.
 *
 * Phrases are contextual embellishments, not quotations, captions, or a transcript
 * of the approved narration. Timing is assigned within scene frames; exact word-level
 * voice synchronization requires speech alignment and is intentionally not claimed here.
 */
object WolfHorrorTypographyEngine {
    enum class Placement { UPPER_LEFT, UPPER_RIGHT, MID_LEFT, MID_RIGHT }

    enum class Motion { WHISPER_FADE, GLITCH_FLICKER }

    data class Cue(
        val text: String,
        val startFrame: Int,
        val endFrameExclusive: Int,
        val placement: Placement,
        val motion: Motion
    ) {
        val durationFrames: Int get() = (endFrameExclusive - startFrame).coerceAtLeast(0)
    }

    private data class Rule(
        val keywords: List<String>,
        val phrases: List<String>,
        val placement: Placement,
        val motion: Motion
    )

    private val rules = listOf(
        Rule(
            listOf("مراية", "مرآة", "انعكاس"),
            listOf("اللي في المراية مش إنت", "الانعكاس بيتحرك لوحده"),
            Placement.UPPER_LEFT, Motion.GLITCH_FLICKER
        ),
        Rule(
            listOf("باب", "طرق", "خبط", "مقبض"),
            listOf("ما تفتحش الباب", "الصوت جاي من ورا الباب"),
            Placement.UPPER_RIGHT, Motion.WHISPER_FADE
        ),
        Rule(
            listOf("يطارد", "يلاحق", "وراه", "وراها", "خلفه", "خلفها"),
            listOf("ما تبصش وراك", "هو وراك"),
            Placement.MID_LEFT, Motion.GLITCH_FLICKER
        ),
        Rule(
            listOf("همس", "ينادي", "نادى", "أنفاس", "يتنفس", "صوت"),
            listOf("الصوت عارف اسمك", "الهمس أقرب مما تتخيل"),
            Placement.UPPER_LEFT, Motion.WHISPER_FADE
        ),
        Rule(
            listOf("جثة", "ميت", "مقبرة", "قبر", "دفن", "مشرحة"),
            listOf("اللي مات لسه هنا", "الموت مش نهاية الحكاية"),
            Placement.MID_RIGHT, Motion.GLITCH_FLICKER
        ),
        Rule(
            listOf("شبح", "ظل", "ظهر", "اختفى", "واقف"),
            listOf("فيه حد هنا", "مش لوحدك"),
            Placement.UPPER_RIGHT, Motion.WHISPER_FADE
        ),
        Rule(
            listOf("ظلام", "ضلمة", "ليل"),
            listOf("الضلمة مخبية حاجة", "مش كل اللي في الضلمة ظل"),
            Placement.MID_LEFT, Motion.WHISPER_FADE
        )
    )

    /** Selects a restrained number of short, contextual overlays with scene-local timing. */
    fun plan(
        scenes: List<String>,
        framesPerScene: List<Int>,
        fps: Int = 12,
        enabled: Boolean = true
    ): List<Cue?> {
        if (!enabled || scenes.isEmpty()) return List(scenes.size) { null }
        val safeFps = fps.coerceIn(1, 60)
        val maxCues = ((scenes.size + 2) / 3).coerceIn(1, 4)
        val selected = MutableList<Cue?>(scenes.size) { null }
        val usedPhrases = mutableSetOf<String>()
        var lastScene = -2
        var cueCount = 0

        scenes.forEachIndexed { index, source ->
            if (cueCount >= maxCues || index - lastScene < 2) return@forEachIndexed
            val normalized = source.lowercase()
            val rule = rules.firstOrNull { candidate ->
                candidate.keywords.any { normalized.contains(it) }
            } ?: return@forEachIndexed
            val phrase = rule.phrases.firstOrNull { candidate ->
                candidate !in usedPhrases && !normalized.contains(candidate.lowercase())
            } ?: return@forEachIndexed
            val totalFrames = framesPerScene.getOrNull(index)?.coerceAtLeast(0) ?: 0
            if (totalFrames <= 0) return@forEachIndexed

            val minimumFrames = (safeFps * 0.8f).roundToInt().coerceAtLeast(1)
            val maximumFrames = (safeFps * 2.2f).roundToInt().coerceAtLeast(minimumFrames)
            val proportionalFrames = (totalFrames * 0.22f).roundToInt()
            val cueFrames = minOf(
                totalFrames,
                maxOf(minimumFrames, minOf(maximumFrames, proportionalFrames))
            )
            val startFrame = ((totalFrames - cueFrames) * 0.58f).roundToInt()
                .coerceIn(0, (totalFrames - cueFrames).coerceAtLeast(0))
            selected[index] = Cue(
                text = phrase,
                startFrame = startFrame,
                endFrameExclusive = (startFrame + cueFrames).coerceAtMost(totalFrames),
                placement = rule.placement,
                motion = rule.motion
            )
            usedPhrases += phrase
            lastScene = index
            cueCount++
        }
        return selected
    }

    /** Smooth fade envelope, returning zero outside the cue's scene-frame interval. */
    fun alphaAtFrame(cue: Cue, sceneFrame: Int, fps: Int = 12): Float {
        if (sceneFrame < cue.startFrame || sceneFrame >= cue.endFrameExclusive) return 0f
        val safeFps = fps.coerceIn(1, 60)
        val fadeFrames = (safeFps * 0.22f).roundToInt().coerceAtLeast(1)
        val elapsed = sceneFrame - cue.startFrame + 1
        val remaining = cue.endFrameExclusive - sceneFrame
        val fadeIn = smoothStep((elapsed.toFloat() / fadeFrames).coerceIn(0f, 1f))
        val fadeOut = smoothStep((remaining.toFloat() / fadeFrames).coerceIn(0f, 1f))
        return minOf(fadeIn, fadeOut)
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)
}
