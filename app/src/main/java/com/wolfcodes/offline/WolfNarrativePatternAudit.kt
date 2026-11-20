package com.wolfcodes.offline

import java.util.Locale

/**
 * Lightweight offline narrative-pattern screening.
 *
 * This is deliberately a transparent lexical heuristic, not semantic AI or a substitute
 * for editorial review. Synonym groups help catch repeated event families across wording.
 */
object WolfNarrativePatternAudit {
    data class Report(
        val detectedEventCount: Int,
        val distinctEventCount: Int,
        val eventTypeCount: Int,
        val diversityPercent: Int,
        val repeatedEventTypes: List<String>,
        val eventSequence: List<String>,
        val structuralWarnings: List<String>,
        val analysisTruncated: Boolean,
        val warnings: List<String>
    )

    private const val MAX_AUDIT_CHARS = 1_000_000
    private const val MAX_AUDIT_SEGMENTS = 2_000
    private const val MAX_AUDIT_WORDS = 100_000

    private data class Segmentation(val values: List<String>, val truncated: Boolean)
    private data class Tokenization(val words: Set<String>, val wordCount: Int, val truncated: Boolean)

    private data class EventType(
        val label: String,
        val keywords: Set<String>
    )

    private val eventTypes = listOf(
        EventType("اكتشاف", setOf(
            "اكتشف", "اكتشفت", "وجد", "وجدت", "عثر", "عثرت", "لاحظ", "لاحظت",
            "رأى", "رات", "شاهد", "شاهدت", "سمع", "سمعت", "لاحظ", "discovered",
            "found", "noticed", "saw", "heard", "uncovered"
        )),
        EventType("تحقيق وبحث", setOf(
            "بحث", "بحثت", "فتش", "فتشت", "تتبع", "تتبعت", "سأل", "سالت",
            "حقق", "حققت", "قرأ", "قرات", "فحص", "فحصت", "investigated",
            "searched", "followed", "questioned", "examined", "read"
        )),
        EventType("ظهور مفاجئ", setOf(
            "ظهر", "ظهرت", "تجسد", "تجسدت", "بان", "برز",
            "شبح", "شبحا", "طيف", "ظل", "كيان", "ghost", "appeared",
            "manifested", "emerged", "entity", "specter", "shadow"
        )),
        EventType("مطاردة", setOf(
            "طارد", "طاردت", "لاحق", "لاحقت", "جرى", "جرت", "ركض",
            "ركضت", "chased", "pursued", "ran", "running", "stalked"
        )),
        EventType("تهديد أو اعتداء", setOf(
            "هاجم", "هاجمت", "ضرب", "ضربت", "خنق", "خنقت", "عض", "عضت",
            "هدد", "هددت", "سحب", "سحبت", "امسك", "أمسك", "أمسكت",
            "attacked", "struck", "choked", "bit", "threatened", "grabbed", "dragged"
        )),
        EventType("مواجهة", setOf(
            "واجه", "واجهت", "قاوم", "قاومت", "وقف", "وقفت", "اقترب", "اقتربت",
            "صاح", "صرخ", "صرخت", "صرخ", "confronted", "faced", "resisted",
            "approached", "stood", "shouted", "screamed"
        )),
        EventType("كشف الحقيقة", setOf(
            "الحقيقة", "السر", "اتضح", "تبين", "فهم", "فهمت", "عرف", "عرفت",
            "اكتشف", "اكتشفت", "انكشف", "انكشفت", "revelation", "truth",
            "secret", "realized", "understood", "revealed", "unmasked"
        )),
        EventType("هروب ونجاة", setOf(
            "هرب", "هربت", "فر", "فرت", "نجا", "نجت", "افلت", "أفلت",
            "اختبأ", "اختبات", "escape", "escaped", "fled", "survived",
            "evaded", "hid", "ranaway"
        )),
        EventType("فقد أو اختفاء", setOf(
            "اختفى", "اختفت", "اختفاء", "فقد", "فقدت", "مات", "ماتت",
            "قتل", "قتلت", "اختطف", "اختطفت", "disappeared", "vanished",
            "lost", "died", "killed", "missing", "abducted"
        )),
        EventType("عودة أو حسم", setOf(
            "عاد", "عادت", "رجع", "رجعت", "انتهى", "انتهت", "استيقظ",
            "استيقظت", "استعاد", "استعادت", "return", "returned", "ended",
            "awoke", "woke", "resolved", "settled"
        ))
    )

    private val climaxTypes = setOf("تهديد أو اعتداء", "مواجهة", "كشف الحقيقة", "مطاردة")
    private val resolutionTypes = setOf("كشف الحقيقة", "هروب ونجاة", "فقد أو اختفاء", "عودة أو حسم")

    fun analyze(story: String): Report {
        val segmentation = splitSegments(story)
        val segmentEvents = ArrayList<List<String>>(segmentation.values.size)
        var remainingWords = MAX_AUDIT_WORDS
        var tokenLimitReached = false
        for (segment in segmentation.values) {
            if (remainingWords <= 0) {
                tokenLimitReached = true
                break
            }
            val tokenized = normalizeTokens(segment, remainingWords)
            remainingWords -= tokenized.wordCount
            if (tokenized.truncated) tokenLimitReached = true
            segmentEvents += eventTypes.filter { type ->
                type.keywords.any { normalizeToken(it) in tokenized.words }
            }.map { it.label }
            if (tokenized.truncated) break
        }
        val analysisTruncated = segmentation.truncated || tokenLimitReached
        val frequencies = linkedMapOf<String, Int>()
        segmentEvents.forEach { labels ->
            labels.forEach { label -> frequencies[label] = (frequencies[label] ?: 0) + 1 }
        }
        val detectedSegments = segmentEvents.count { it.isNotEmpty() }
        val sequence = segmentEvents.flatMap { it }.take(24)
        val distinctCount = frequencies.size
        val diversity = if (eventTypes.isEmpty()) 0
        else ((distinctCount.toDouble() / eventTypes.size) * 100.0).toInt().coerceIn(0, 100)
        val repeated = frequencies.filterValues { it >= 3 }.keys.toList()

        val warnings = ArrayList<String>()
        val structural = ArrayList<String>()
        if (segmentEvents.size < 4) {
            structural += "تعذر تقسيم النص إلى أربعة مقاطع سردية واضحة على الأقل؛ قد تكون علامات الترقيم أو الفقرات غير كافية."
        }
        if (detectedSegments < 3) {
            structural += "لم تُرصد أحداث كافية من قاموس القرائن؛ راجع وضوح الأفعال وتطور ما يحدث في القصة."
        }
        if (distinctCount < 3 && detectedSegments >= 3) {
            structural += "تنوع الأحداث المرصود منخفض؛ قد تكون القصة تدور حول نوع واحد من الأفعال دون انتقالات كافية."
        }
        if (repeated.isNotEmpty()) {
            structural += "تكررت عائلات الأحداث التالية ثلاث مرات أو أكثر: ${repeated.joinToString("، ")}؛ تحقق من أن كل تكرار يضيف تطورًا جديدًا."
        }

        val analyzedSegmentCount = segmentEvents.size
        val middleStart = (analyzedSegmentCount * 0.25).toInt().coerceAtMost((analyzedSegmentCount - 1).coerceAtLeast(0))
        val middleEnd = (analyzedSegmentCount * 0.80).toInt().coerceAtLeast(middleStart + 1)
            .coerceAtMost(analyzedSegmentCount)
        val middleHasEscalation = segmentEvents.subList(middleStart, middleEnd)
            .flatten().any { it in climaxTypes }
        if (analyzedSegmentCount >= 5 && !middleHasEscalation) {
            structural += "لم تظهر قرائن تصاعد أو تهديد واضحة في منتصف القصة؛ تحقق من بناء التوتر تدريجيًا."
        }

        val finalStart = (analyzedSegmentCount * 0.70).toInt().coerceAtMost((analyzedSegmentCount - 1).coerceAtLeast(0))
        val endingEvents = segmentEvents.drop(finalStart).flatten().toSet()
        if (analyzedSegmentCount >= 5 && endingEvents.none { it in climaxTypes }) {
            structural += "لم تُرصد مواجهة أو ذروة أو كشف واضح في الجزء الأخير؛ راجع وجود ذروة سردية."
        }
        if (analyzedSegmentCount >= 5 && endingEvents.none { it in resolutionTypes }) {
            structural += "لم تُرصد قرينة حسم أو نتيجة أو أثر نهائي في الخاتمة؛ تحقق من إغلاق القوس السردي أو تعمد تركه مفتوحًا."
        }
        if (structural.isEmpty()) {
            warnings += "لم يرصد الفحص البنيوي مشكلة واضحة ضمن قرائن الكلمات المحدودة؛ هذا لا يثبت سلامة الحبكة."
        } else {
            warnings += "ملاحظات البنية السردية قرائن تحذيرية؛ قد يفوت القاموس مرادفات أو يسيء تصنيف بعض الجمل."
        }
        if (analysisTruncated) warnings += "تم تقييد فحص الأحداث عند حد المليون حرف أو 2,000 مقطع أو 100,000 كلمة؛ لم يشمل التحليل بقية النص."
        warnings += "فحص الأحداث لا يفهم السياق أو النفي أو الدوافع أو العلاقات السببية فهمًا كاملًا، ولا يستطيع إثبات تكرار حبكة بالمعنى الدلالي."

        return Report(
            detectedEventCount = detectedSegments,
            distinctEventCount = distinctCount,
            eventTypeCount = eventTypes.size,
            diversityPercent = diversity,
            repeatedEventTypes = repeated,
            eventSequence = sequence,
            structuralWarnings = structural,
            analysisTruncated = analysisTruncated,
            warnings = warnings
        )
    }

    private fun splitSegments(text: String): Segmentation {
        val limitedText = text.take(MAX_AUDIT_CHARS)
        val segments = ArrayList<String>()
        var start = 0
        var offset = 0
        while (offset < limitedText.length && segments.size < MAX_AUDIT_SEGMENTS) {
            val character = limitedText[offset]
            if (character == '.' || character == '!' || character == '?' ||
                character == '؟' || character == '؛' || character == ';' || character == '\n'
            ) {
                if (offset > start) {
                    val segment = limitedText.substring(start, offset).trim()
                    if (segment.isNotBlank()) segments += segment
                }
                start = offset + 1
            }
            offset++
        }
        val hitSegmentLimit = segments.size >= MAX_AUDIT_SEGMENTS && offset < limitedText.length
        if (!hitSegmentLimit && start < limitedText.length) {
            val segment = limitedText.substring(start).trim()
            if (segment.isNotBlank()) segments += segment
        }
        if (segments.isEmpty()) segments += limitedText
        return Segmentation(
            values = segments,
            truncated = text.length > MAX_AUDIT_CHARS || hitSegmentLimit
        )
    }

    private fun normalizeTokens(text: String, maximumWords: Int): Tokenization {
        val words = HashSet<String>()
        var truncated = false
        var wordCount = 0
        val iterator = Regex("[\\p{L}\\p{N}]+").findAll(text).iterator()
        while (iterator.hasNext()) {
            if (wordCount >= maximumWords) {
                truncated = true
                break
            }
            words += normalizeToken(iterator.next().value)
            wordCount++
        }
        return Tokenization(words, wordCount, truncated)
    }

    private fun normalizeToken(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("\\p{M}+"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
}
