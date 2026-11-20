package com.wolfcodes.offline

import kotlin.math.roundToInt

/**
 * Deterministic, offline story-length and narrative-structure planner.
 *
 * This engine measures text and checks structural coverage; it does not claim to
 * understand semantic plot quality. The model remains responsible for the prose.
 */
object WolfStoryLengthStructureEngine {
    const val MIN_TARGET_WORDS = 500
    const val MAX_TARGET_WORDS = 50_000
    const val MIN_WORDS_PER_MINUTE = 120
    const val MAX_WORDS_PER_MINUTE = 160

    enum class Beat(val label: String, val guidance: String, val weight: Int) {
        HOOK("الخطاف", "افتح بصورة أو سؤال مقلق يجذب القارئ فورًا", 90),
        SETUP("التأسيس", "عرّف الشخصية والعالم الطبيعي مع إشارات اضطراب صغيرة", 100),
        INCITING_INCIDENT("الحدث المحفّز", "قدّم الحدث الذي يكسر الحياة المعتادة ويجبر الشخصية على التصرف", 105),
        RISING_ACTION("تصاعد التوتر", "صعّد الخطر تدريجيًا مع نتائج واضحة وقرارات أصعب", 125),
        MIDPOINT("منتصف التحول", "اكشف معلومة تغيّر فهم القارئ للمشكلة", 105),
        CRISIS("الأزمة", "ضيّق الخيارات وارفع الثمن النفسي أو المادي", 115),
        CLIMAX("الذروة", "واجه الشخصية بالحقيقة أو الخطر الأكبر وادفعها إلى قرار حاسم", 100),
        RESOLUTION("الخاتمة", "قدّم نتيجة مترابطة واترك الأثر الأخير دون إطالة", 70)
    }

    data class ChapterTarget(
        val index: Int,
        val title: String,
        val beat: Beat,
        val targetWords: Int,
        val minimumWords: Int,
        val maximumWords: Int
    )

    data class Report(
        val targetWords: Int,
        val actualWords: Int,
        val expectedChapters: Int,
        val actualChapters: Int,
        val completionPercent: Int,
        val estimatedMinutesAt120Wpm: Int,
        val estimatedMinutesAt160Wpm: Int,
        val findings: List<String>
    ) {
        val withinOverallTolerance: Boolean
            get() = actualWords in (targetWords * 0.85).roundToInt()..(targetWords * 1.15).roundToInt()
    }

    /** Unicode-aware word counter for Arabic and English; diacritics and tatweel do not split words. */
    fun words(text: String): Int = tokenize(text).size

    fun tokenize(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var hasBase = false
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            when {
                Character.isLetterOrDigit(codePoint) -> {
                    current.appendCodePoint(codePoint)
                    hasBase = true
                }
                isCombiningMark(codePoint) || codePoint == 0x0640 -> {
                    if (hasBase && codePoint != 0x0640) current.appendCodePoint(codePoint)
                }
                else -> {
                    if (hasBase) result += current.toString()
                    current.setLength(0)
                    hasBase = false
                }
            }
        }
        if (hasBase) result += current.toString()
        return result
    }

    private fun isCombiningMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    fun chapterTargets(
        targetWords: Int,
        chapterTitles: List<String>,
        tolerance: Double = 0.20
    ): List<ChapterTarget> {
        require(targetWords in MIN_TARGET_WORDS..MAX_TARGET_WORDS) { "الطول المستهدف يجب أن يكون بين 500 و50,000 كلمة" }
        require(chapterTitles.isNotEmpty() && chapterTitles.size <= 64) { "عدد الفصول غير صالح" }
        require(tolerance in 0.05..0.40) { "هامش الفصل يجب أن يكون بين 5% و40%" }
        require(chapterTitles.all { it.isNotBlank() && it.length <= 120 }) { "عناوين الفصول مطلوبة وبحد أقصى 120 حرفًا" }

        val beats = chapterTitles.indices.map { beatFor(it, chapterTitles.size) }
        val weights = beats.map { it.weight.toLong() }
        val totalWeight = weights.sum()
        val raw = weights.map { targetWords.toLong() * it / totalWeight }
        val allocations = raw.map { it.toInt() }.toMutableList()
        var remainder = targetWords - allocations.sum()
        var cursor = allocations.lastIndex
        while (remainder > 0) {
            allocations[cursor]++
            remainder--
            cursor--
            if (cursor < 0) cursor = allocations.lastIndex
        }
        return chapterTitles.mapIndexed { index, title ->
            val amount = allocations[index]
            ChapterTarget(
                index = index,
                title = title.trim(),
                beat = beats[index],
                targetWords = amount,
                minimumWords = (amount * (1.0 - tolerance)).roundToInt().coerceAtLeast(1),
                maximumWords = (amount * (1.0 + tolerance)).roundToInt().coerceAtLeast(1)
            )
        }
    }

    private fun beatFor(index: Int, count: Int): Beat {
        val progress = if (count <= 1) 0.0 else index.toDouble() / (count - 1)
        return when {
            progress < 0.08 -> Beat.HOOK
            progress < 0.20 -> Beat.SETUP
            progress < 0.32 -> Beat.INCITING_INCIDENT
            progress < 0.62 -> Beat.RISING_ACTION
            progress < 0.74 -> Beat.MIDPOINT
            progress < 0.87 -> Beat.CRISIS
            progress < 0.96 -> Beat.CLIMAX
            else -> Beat.RESOLUTION
        }
    }

    fun planGuidance(targetWords: Int, chapterTitles: List<String>): String {
        val targets = chapterTargets(targetWords, chapterTitles)
        return buildString {
            appendLine("WOLF STORY LENGTH & STRUCTURE PLAN")
            appendLine("إجمالي الهدف: $targetWords كلمة تقريبًا.")
            appendLine("احسب الكلمات كنص عربي/إنجليزي، ولا تعتبر عدد الحروف بديلًا عن الكلمات.")
            appendLine("وزّع الأحداث وفق الأدوار التالية، ولا تجعل كل الفصول متشابهة الإيقاع:")
            targets.forEach {
                appendLine(
                    "الفصل ${it.index + 1} — ${it.title}: ${it.beat.label}؛ " +
                        "هدف ${it.targetWords} كلمة (نطاق ${it.minimumWords}–${it.maximumWords}). " +
                        it.beat.guidance
                )
            }
            appendLine("حافظ على السببية واستمرارية الشخصيات، ومهّد للتحولات قبل كشفها.")
            appendLine("لا تملأ الطول بالحشو؛ إذا احتاج الحدث مساحة أقل فاجعل المشهد مكثفًا، وعوّض في الفصول التي تستحق.")
            appendLine("النهاية يجب أن تكون ممهدة ومترابطة، لا مجرد إيقاف مفاجئ.")
        }.trim()
    }

    fun chapterGuidance(target: ChapterTarget): String = buildString {
        appendLine("دور هذا الفصل: ${target.beat.label}.")
        appendLine(target.beat.guidance)
        appendLine("استهدف ${target.targetWords} كلمة، ضمن ${target.minimumWords}–${target.maximumWords} كلمة تقريبًا.")
        appendLine("لا تلخص الفصل السابق ولا تقفز إلى دور الفصل التالي. اختم بتغير أو سؤال يدفع القارئ للمتابعة.")
    }.trim()

    fun analyzeText(targetWords: Int, text: String, expectedChapters: Int): Report {
        require(targetWords in MIN_TARGET_WORDS..MAX_TARGET_WORDS)
        require(expectedChapters in 1..64)
        val count = words(text)
        val percent = ((count.toDouble() / targetWords) * 100).roundToInt().coerceIn(0, 10_000)
        val findings = buildList {
            if (count == 0) add("لم يُكتب نص بعد.")
            else if (count < targetWords * 0.85) add("النص أقصر من النطاق المستهدف؛ يحتاج إلى تطوير أحداث ومشاهد ذات قيمة.")
            else if (count > targetWords * 1.15) add("النص أطول من النطاق المستهدف؛ راجع التكرار والإطالة دون حذف أحداث أساسية.")
            else add("الطول الإجمالي داخل النطاق المقبول (±15%).")
            if (expectedChapters > 1) add("المخطط يتطلب $expectedChapters فصول؛ راجع توزيعها منفصلًا قبل الاعتماد.")
            add("هذا فحص كمي للبنية والطول، وليس حكمًا آليًا على جودة الحبكة أو منطق الأحداث.")
        }
        return Report(
            targetWords, count, expectedChapters, if (count == 0) 0 else 1,
            percent, (count / MIN_WORDS_PER_MINUTE.toDouble()).roundToInt(),
            (count / MAX_WORDS_PER_MINUTE.toDouble()).roundToInt(), findings
        )
    }

    fun analyzeChapters(targetWords: Int, chapters: List<String>, chapterTitles: List<String>): Report {
        require(chapterTitles.isNotEmpty() && chapterTitles.size <= 64)
        require(chapters.size <= chapterTitles.size)
        val total = chapters.sumOf { words(it) }
        val findings = buildList {
            if (chapters.size < chapterTitles.size) add("اكتمل ${chapters.size} من ${chapterTitles.size} فصل؛ لا تعتمد القصة قبل اكتمالها.")
            if (chapters.isEmpty()) add("لم يُحفظ أي فصل بعد.")
            val targets = chapterTargets(targetWords, chapterTitles)
            chapters.forEachIndexed { index, chapter ->
                val actual = words(chapter)
                val target = targets[index]
                if (actual < target.minimumWords) add("الفصل ${index + 1} (${target.title}) أقصر من نطاقه: $actual كلمة مقابل ${target.minimumWords} كحد أدنى.")
                if (actual > target.maximumWords) add("الفصل ${index + 1} (${target.title}) أطول من نطاقه: $actual كلمة مقابل ${target.maximumWords} كحد أقصى.")
            }
            if (chapters.size == chapterTitles.size && total in (targetWords * 0.85).roundToInt()..(targetWords * 1.15).roundToInt()) {
                add("الطول الإجمالي للفصول داخل النطاق المقبول (±15%).")
            }
            add("توزيع الأدوار مبني على ترتيب الفصول؛ التحقق من تحقق الأحداث فعليًا يحتاج مراجعة بشرية.")
        }
        return Report(
            targetWords, total, chapterTitles.size, chapters.size,
            ((total.toDouble() / targetWords) * 100).roundToInt().coerceIn(0, 10_000),
            (total / MIN_WORDS_PER_MINUTE.toDouble()).roundToInt(),
            (total / MAX_WORDS_PER_MINUTE.toDouble()).roundToInt(),
            findings
        )
    }

    fun formatReport(report: Report): String = buildString {
        appendLine("WOLF STORY LENGTH & STRUCTURE")
        appendLine("الكلمات الفعلية: ${report.actualWords} / ${report.targetWords}")
        appendLine("الإنجاز التقريبي: ${report.completionPercent}%")
        appendLine("الفصول: ${report.actualChapters} / ${report.expectedChapters}")
        appendLine("زمن القراءة/السرد التقديري: ${report.estimatedMinutesAt160Wpm}–${report.estimatedMinutesAt120Wpm} دقيقة (120–160 كلمة/دقيقة).")
        report.findings.forEach { appendLine("• $it") }
    }.trim()
}
