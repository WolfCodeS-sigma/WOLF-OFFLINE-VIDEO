package com.wolfcodes.offline

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Offline originality and reference review.
 * Exact normalized six-word overlap is a screening signal, never a plagiarism verdict.
 */
object WolfStoryOriginalityReviewEngine {
    const val DIRECTORY_NAME = "wolf_story_originality_review"
    const val MAX_REFERENCES = 20
    const val MAX_LEDGER_REFERENCES = 40
    const val MAX_STORY_CHARS = 4_000_000
    const val MAX_REFERENCE_CHARS = 1_000_000
    const val MAX_REVIEW_WORDS = 200_000
    const val MAX_TOTAL_REFERENCE_WORDS = 500_000
    const val SHINGLE_WORDS = 6

    private const val LEDGER_VERSION = 1
    private const val HASH_SENTINEL = Long.MIN_VALUE
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
    private val lock = Any()

    enum class Severity(val label: String, val rank: Int) {
        NONE("لا توجد تطابقات واضحة", 0),
        LOW("تشابه محدود يحتاج مراجعة", 1),
        MEDIUM("تشابه ملحوظ يحتاج تحققًا", 2),
        HIGH("تشابه مرتفع — راجع المصدر والنص", 3)
    }

    data class SourceStamp(
        val id: String,
        val title: String,
        val sourceType: String,
        val sourceLabel: String,
        val contentFingerprint: String,
        val usages: List<String> = emptyList()
    )

    data class ReferenceText(val source: SourceStamp, val text: String)
    data class UnavailableSource(val source: SourceStamp, val reason: String)

    data class SourceFinding(
        val source: SourceStamp,
        val severity: Severity,
        val coveragePercent: Int,
        val distinctMatchedPhrases: Int,
        val matchingWindowCount: Int,
        val maximumContinuousMatchWords: Int,
        val phraseSamples: List<String>
    )

    data class Report(
        val storyFingerprint: String,
        val storyWords: Int,
        val checkedSources: Int,
        val checkedSourceVersions: List<SourceStamp>,
        val overallSeverity: Severity,
        val overallOverlapPercent: Int,
        val findings: List<SourceFinding>,
        val unavailableSources: List<UnavailableSource>,
        val internalRepeatedPhraseCount: Int,
        val internalRepeatedSamples: List<String>,
        val storyTruncated: Boolean,
        val sourceTruncationCount: Int,
        val referenceWordCount: Int,
        val sourceLimitReached: Boolean,
        val warnings: List<String>
    )

    private data class Tokenized(val words: List<String>, val truncated: Boolean)
    private data class Shingles(
        val hashes: LongArray,
        val unique: Set<Long>,
        val repeated: Set<Long>,
        val repeatedSamples: List<String>,
        val sourcePositions: Map<Long, IntArray>,
        val positionLimitReached: Boolean
    )

    private data class ContinuousMatch(
        val wordCount: Int,
        val phraseSamples: List<String>
    )

    private const val MAX_POSITIONS_PER_SHINGLE = 128

    private val stopWords = setOf(
        "في", "من", "على", "الى", "عن", "ان", "كان", "كانت", "يكون", "تكون",
        "هو", "هي", "هم", "هن", "ما", "لا", "لم", "لن", "قد", "ثم", "هذا",
        "هذه", "ذلك", "تلك", "هناك", "هنا", "مع", "كل", "بعد", "قبل", "بين",
        "عند", "حتى", "او", "و", "ف", "ب", "ل", "ك", "يا", "انا", "انت",
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
        "be", "been", "to", "of", "in", "on", "for", "with", "at", "by",
        "from", "as", "it", "he", "she", "they", "we", "you", "i", "his",
        "her", "their", "this", "that", "these", "those", "then", "there"
    )

    fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
    }

    fun recordSources(
        root: File,
        sessionId: String,
        usageLabel: String,
        sources: List<SourceStamp>
    ) = synchronized(lock) {
        require(isSafeSessionId(sessionId)) { "معرّف جلسة القصة غير صالح" }
        require(usageLabel.isNotBlank() && usageLabel.length <= 100) { "وصف استخدام المرجع غير صالح" }
        require(sources.size <= 3) { "يمكن تسجيل 3 مراجع كحد أقصى لكل عملية توليد" }
        sources.forEach(::validateSourceStamp)
        if (sources.isEmpty()) return@synchronized

        val merged = linkedMapOf<String, SourceStamp>()
        loadSourcesLocked(root, sessionId).forEach { merged[sourceKey(it)] = it }
        sources.forEach { source ->
            val key = sourceKey(source)
            val previous = merged[key]
            val usages = (previous?.usages.orEmpty() + usageLabel).distinct().takeLast(80)
            if (previous == null && merged.size >= MAX_LEDGER_REFERENCES) {
                throw IllegalStateException("وصل سجل المراجع إلى الحد الآمن البالغ 40 مصدرًا")
            }
            merged[key] = source.copy(usages = usages)
        }
        saveSourcesLocked(root, sessionId, merged.values.toList())
    }

    fun loadSources(root: File, sessionId: String): List<SourceStamp> = synchronized(lock) {
        require(isSafeSessionId(sessionId)) { "معرّف جلسة القصة غير صالح" }
        loadSourcesLocked(root, sessionId)
    }

    private fun loadSourcesLocked(root: File, sessionId: String): List<SourceStamp> {
        val target = ledgerFile(root, sessionId)
        val backup = File(root, "$sessionId.json.backup")
        val pending = File(root, "$sessionId.json.pending")
        if (!target.exists() && backup.isFile) {
            if (!backup.renameTo(target)) throw IllegalStateException("تعذر استعادة سجل المراجع الاحتياطي")
        } else if (target.isFile && backup.exists()) {
            backup.delete()
        }
        if (pending.exists()) pending.delete()
        if (!target.isFile) return emptyList()

        val json = JSONObject(target.readText(Charsets.UTF_8))
        require(json.optInt("version", -1) == LEDGER_VERSION) { "إصدار سجل المراجع غير مدعوم" }
        val array = json.optJSONArray("sources") ?: JSONArray()
        require(array.length() <= MAX_LEDGER_REFERENCES) { "سجل المراجع المحفوظ يتجاوز الحد الآمن" }
        val result = ArrayList<SourceStamp>(array.length())
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val usages = item.optJSONArray("usages") ?: JSONArray()
            val source = SourceStamp(
                id = item.getString("id"),
                title = item.getString("title"),
                sourceType = item.getString("sourceType"),
                sourceLabel = item.optString("sourceLabel"),
                contentFingerprint = item.getString("contentFingerprint"),
                usages = (0 until usages.length()).mapNotNull { position ->
                    usages.optString(position).takeIf { it.isNotBlank() }
                }
            )
            validateSourceStamp(source)
            result += source
        }
        require(result.map(::sourceKey).distinct().size == result.size) { "سجل المراجع يحتوي على إدخالات مكررة" }
        return result
    }

    private fun saveSourcesLocked(root: File, sessionId: String, sources: List<SourceStamp>) {
        require(sources.size <= MAX_LEDGER_REFERENCES)
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد سجل مراجعة الأصالة")
        }
        val array = JSONArray()
        sources.forEach { source ->
            validateSourceStamp(source)
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("title", source.title)
                    .put("sourceType", source.sourceType)
                    .put("sourceLabel", source.sourceLabel)
                    .put("contentFingerprint", source.contentFingerprint)
                    .put("usages", JSONArray(source.usages.take(80)))
            )
        }
        val json = JSONObject().put("version", LEDGER_VERSION).put("sources", array)
        val target = ledgerFile(root, sessionId)
        val pending = File(root, "$sessionId.json.pending")
        val backup = File(root, "$sessionId.json.backup")
        try {
            FileOutputStream(pending).use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (backup.exists() && !backup.delete()) {
                throw IllegalStateException("تعذر تنظيف سجل المراجع الاحتياطي القديم")
            }
            if (target.exists() && !target.renameTo(backup)) {
                throw IllegalStateException("تعذر تجهيز سجل المراجع السابق للاستبدال")
            }
            if (!pending.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IllegalStateException("تعذر حفظ سجل المراجع الجديد")
            }
            if (backup.exists()) backup.delete()
        } catch (error: Exception) {
            pending.delete()
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    private fun ledgerFile(root: File, sessionId: String) = File(root, "$sessionId.json")
    private fun sourceKey(source: SourceStamp) = "${source.id}|${source.contentFingerprint}"

    private fun validateSourceStamp(source: SourceStamp) {
        require(source.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "معرّف المرجع غير صالح" }
        require(source.title.isNotBlank() && source.title.length <= 240) { "عنوان المرجع غير صالح" }
        require(source.sourceType.isNotBlank() && source.sourceType.length <= 120) { "نوع مصدر المرجع غير صالح" }
        require(source.sourceLabel.length <= 500) { "وصف مصدر المرجع طويل جدًا" }
        require(source.contentFingerprint.matches(Regex("[a-f0-9]{64}"))) { "بصمة محتوى المرجع غير صالحة" }
        require(source.usages.size <= 80 && source.usages.all { it.isNotBlank() && it.length <= 100 }) {
            "سجل استخدام المرجع غير صالح"
        }
    }

    private fun isSafeSessionId(value: String) = value.matches(Regex("[A-Za-z0-9-]{8,64}"))

    fun analyze(
        story: String,
        references: Sequence<ReferenceText>,
        unavailableSources: List<UnavailableSource> = emptyList()
    ): Report {
        require(story.isNotBlank()) { "لا يوجد نص صالح لمراجعته" }
        require(story.length <= MAX_STORY_CHARS) { "النص تجاوز حد المراجعة الآمن البالغ 4 ملايين حرف" }

        val storyTokenized = tokenize(story, MAX_REVIEW_WORDS)
        val storyShingles = buildShingles(storyTokenized.words)
        val uniqueStoryShingles = storyShingles.unique
        val allMatchedStoryShingles = HashSet<Long>()
        val findings = ArrayList<SourceFinding>()
        val checkedSourceVersions = ArrayList<SourceStamp>()
        val unavailableInputCount = unavailableSources.size
        val unavailable = unavailableSources.take(MAX_REFERENCES).toMutableList()
        val seenContentFingerprints = HashSet<String>()
        var checkedSources = 0
        var referenceWordCount = 0
        var sourceTruncationCount = 0
        var sourceLimitReached = false
        var positionLimitReached = false
        val iterator = references.iterator()

        while (iterator.hasNext()) {
            if (checkedSources >= MAX_REFERENCES) {
                sourceLimitReached = true
                break
            }
            val reference = iterator.next()
            validateSourceStamp(reference.source)
            if (reference.text.isBlank()) {
                unavailable += UnavailableSource(reference.source, "المحتوى فارغ أو غير متاح")
                continue
            }
            val actualFingerprint = fingerprint(reference.text)
            if (actualFingerprint != reference.source.contentFingerprint) {
                unavailable += UnavailableSource(
                    reference.source,
                    "تغير محتوى المرجع بعد استخدامه؛ لم تتم مقارنته بنسخة مختلفة"
                )
                continue
            }
            if (!seenContentFingerprints.add(actualFingerprint)) {
                checkedSourceVersions += reference.source
                continue
            }
            val remainingWords = MAX_TOTAL_REFERENCE_WORDS - referenceWordCount
            if (remainingWords <= 0) {
                sourceLimitReached = true
                break
            }
            checkedSourceVersions += reference.source

            val limitedText = reference.text.take(MAX_REFERENCE_CHARS)
            val sourceTokenized = tokenize(limitedText, minOf(MAX_REVIEW_WORDS, remainingWords))
            if (reference.text.length > MAX_REFERENCE_CHARS || sourceTokenized.truncated) {
                sourceTruncationCount++
            }
            referenceWordCount += sourceTokenized.words.size
            checkedSources++

            val sourceShingles = buildShingles(sourceTokenized.words, uniqueStoryShingles)
            if (sourceShingles.positionLimitReached) positionLimitReached = true
            if (uniqueStoryShingles.isEmpty() || sourceShingles.unique.isEmpty()) continue

            var matchingWindowCount = 0
            storyShingles.hashes.forEach { hash ->
                if (hash != HASH_SENTINEL && hash in sourceShingles.unique) {
                    matchingWindowCount++
                    allMatchedStoryShingles += hash
                }
            }

            val distinctMatched = uniqueStoryShingles.count { it in sourceShingles.unique }
            if (distinctMatched == 0) continue
            val continuousMatch = longestContinuousMatch(storyTokenized.words, storyShingles.hashes, sourceShingles)
            val maximumContinuousWords = continuousMatch.wordCount
            val coverage = ((distinctMatched.toDouble() / uniqueStoryShingles.size) * 100.0)
                .roundToInt().coerceIn(0, 100)
            val severity = when {
                maximumContinuousWords >= 18 || (coverage >= 15 && distinctMatched >= 10) -> Severity.HIGH
                maximumContinuousWords >= 10 || (coverage >= 5 && distinctMatched >= 4) -> Severity.MEDIUM
                else -> Severity.LOW
            }
            findings += SourceFinding(
                source = reference.source,
                severity = severity,
                coveragePercent = coverage,
                distinctMatchedPhrases = distinctMatched,
                matchingWindowCount = matchingWindowCount,
                maximumContinuousMatchWords = maximumContinuousWords,
                phraseSamples = continuousMatch.phraseSamples
            )
        }

        val overallOverlap = if (uniqueStoryShingles.isEmpty()) 0 else
            ((allMatchedStoryShingles.size.toDouble() / uniqueStoryShingles.size) * 100.0)
                .roundToInt().coerceIn(0, 100)
        val overallSeverity = findings.maxByOrNull { it.severity.rank }?.severity ?: Severity.NONE
        val warnings = buildList {
            if (checkedSources == 0) add("لم تتم مقارنة النص بأي محتوى مرجعي صالح؛ لا يمكن اعتبار ذلك إثباتًا للأصالة.")
            if (unavailable.isNotEmpty()) add("تعذر فحص ${unavailable.size} مرجعًا أو استُبعدت نسخته المتغيرة.")
            if (unavailableInputCount > MAX_REFERENCES) add("تم إخفاء ${unavailableInputCount - MAX_REFERENCES} مراجع إضافية غير متاحة للحفاظ على حجم التقرير.")
            if (storyTokenized.truncated) add("تم تحليل أول $MAX_REVIEW_WORDS كلمة من القصة فقط بسبب حد الذاكرة الآمن.")
            if (sourceTruncationCount > 0) add("تم اختصار محتوى ${sourceTruncationCount} مرجعًا وفق حد المعالجة المحلي.")
            if (sourceLimitReached) add("توقف الفحص عند الحد الآمن لعدد المراجع أو الكلمات؛ توجد مصادر أخرى لم تدخل هذه الجولة.")
            if (positionLimitReached) add("تم تقييد مواضع العبارات شديدة التكرار لتجنب استهلاك الذاكرة؛ قد لا يظهر طول بعض التطابقات المتكررة بالكامل.")
            if (findings.isNotEmpty()) add("التطابق اللفظي مؤشر للمراجعة البشرية وليس دليلًا قاطعًا على النسخ.")
            add("الفحص محلي ويشمل النصوص المرجعية المتاحة والمسموح بها فقط؛ لا يبحث في الإنترنت أو قواعد نشر خارجية.")
        }

        return Report(
            storyFingerprint = fingerprint(story),
            storyWords = storyTokenized.words.size,
            checkedSources = checkedSources,
            checkedSourceVersions = checkedSourceVersions.distinctBy(::sourceKey),
            overallSeverity = overallSeverity,
            overallOverlapPercent = overallOverlap,
            findings = findings.sortedWith(
                compareByDescending<SourceFinding> { it.severity.rank }
                    .thenByDescending { it.coveragePercent }
                    .thenByDescending { it.maximumContinuousMatchWords }
            ),
            unavailableSources = unavailable.take(MAX_REFERENCES),
            internalRepeatedPhraseCount = storyShingles.repeated.size,
            internalRepeatedSamples = storyShingles.repeatedSamples,
            storyTruncated = storyTokenized.truncated,
            sourceTruncationCount = sourceTruncationCount,
            referenceWordCount = referenceWordCount,
            sourceLimitReached = sourceLimitReached,
            warnings = warnings
        )
    }

    private fun tokenize(text: String, maximumWords: Int): Tokenized {
        val result = ArrayList<String>(minOf(maximumWords, text.length / 5 + 1))
        val current = StringBuilder()
        var offset = 0
        var truncated = false

        fun finishToken() {
            if (current.isNotEmpty()) {
                result += current.toString()
                current.setLength(0)
            }
        }

        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            when {
                Character.isLetterOrDigit(codePoint) -> current.appendCodePoint(normalizeCodePoint(codePoint))
                isCombiningMark(codePoint) || codePoint == 0x0640 -> Unit
                else -> finishToken()
            }
            if (result.size >= maximumWords && current.isNotEmpty()) {
                truncated = offset < text.length
                break
            }
            if (result.size >= maximumWords && offset < text.length) {
                truncated = true
                break
            }
        }
        if (!truncated) finishToken()
        return Tokenized(result, truncated)
    }

    private fun normalizeCodePoint(value: Int): Int {
        val lower = Character.toLowerCase(value)
        return when (lower) {
            0x0622, 0x0623, 0x0625, 0x0671 -> 0x0627
            0x0649 -> 0x064A
            else -> lower
        }
    }

    private fun isCombiningMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun buildShingles(
        words: List<String>,
        collectPositionsFor: Set<Long>? = null
    ): Shingles {
        val hashes = LongArray(words.size) { HASH_SENTINEL }
        val unique = HashSet<Long>()
        val repeated = HashSet<Long>()
        val repeatedSamples = ArrayList<String>()
        val mutablePositions = HashMap<Long, MutableList<Int>>()
        var positionLimitReached = false
        if (words.size < SHINGLE_WORDS) {
            return Shingles(hashes, unique, repeated, repeatedSamples, emptyMap(), false)
        }

        for (start in 0..(words.size - SHINGLE_WORDS)) {
            if (!isInformativeShingle(words, start)) continue
            val hash = hashShingle(words, start)
            hashes[start] = hash
            if (!unique.add(hash) && repeated.add(hash) && repeatedSamples.size < 5) {
                repeatedSamples += words.subList(start, start + SHINGLE_WORDS).joinToString(" ")
            }
            if (collectPositionsFor?.contains(hash) == true) {
                val positions = mutablePositions.getOrPut(hash) { ArrayList() }
                if (positions.size < MAX_POSITIONS_PER_SHINGLE) positions += start
                else positionLimitReached = true
            }
        }
        val sourcePositions = mutablePositions.mapValues { (_, positions) -> positions.toIntArray() }
        return Shingles(hashes, unique, repeated, repeatedSamples, sourcePositions, positionLimitReached)
    }

    private fun longestContinuousMatch(
        storyWords: List<String>,
        storyHashes: LongArray,
        sourceShingles: Shingles
    ): ContinuousMatch {
        var previousRuns = HashMap<Int, Int>()
        var bestWindows = 0
        val bestSamples = ArrayList<String>()

        storyHashes.forEachIndexed { storyStart, hash ->
            val sourceStarts = sourceShingles.sourcePositions[hash]
            val currentRuns = HashMap<Int, Int>()
            if (hash != HASH_SENTINEL && sourceStarts != null) {
                sourceStarts.forEach { sourceStart ->
                    val runWindows = (previousRuns[sourceStart - 1] ?: 0) + 1
                    currentRuns[sourceStart] = runWindows
                    when {
                        runWindows > bestWindows -> {
                            bestWindows = runWindows
                            bestSamples.clear()
                            val start = storyStart - runWindows + 1
                            val end = (storyStart + SHINGLE_WORDS).coerceAtMost(storyWords.size)
                            val sample = phraseSample(storyWords, start, end)
                            if (sample.isNotBlank()) bestSamples += sample
                        }
                        runWindows == bestWindows && bestSamples.size < 5 -> {
                            val start = storyStart - runWindows + 1
                            val end = (storyStart + SHINGLE_WORDS).coerceAtMost(storyWords.size)
                            val sample = phraseSample(storyWords, start, end)
                            if (sample.isNotBlank() && sample !in bestSamples) bestSamples += sample
                        }
                    }
                }
            }
            previousRuns = currentRuns
        }
        return ContinuousMatch(
            wordCount = if (bestWindows == 0) 0 else bestWindows + SHINGLE_WORDS - 1,
            phraseSamples = bestSamples
        )
    }

    private fun phraseSample(words: List<String>, start: Int, end: Int): String {
        val selected = words.subList(start, end)
        val pieces = ArrayList<String>(minOf(40, selected.size))
        var usedCharacters = 0
        var truncated = selected.size > 40
        for (word in selected.take(40)) {
            val allowance = 500 - usedCharacters - if (pieces.isEmpty()) 0 else 1
            if (allowance <= 0) {
                truncated = true
                break
            }
            val piece = word.take(allowance)
            pieces += piece
            usedCharacters += piece.length + if (pieces.size == 1) 0 else 1
            if (piece.length < word.length) {
                truncated = true
                break
            }
        }
        val sample = pieces.joinToString(" ")
        return if (truncated) "$sample …" else sample
    }

    private fun isInformativeShingle(words: List<String>, start: Int): Boolean {
        var meaningful = 0
        for (index in start until start + SHINGLE_WORDS) {
            val word = words[index]
            if (word !in stopWords && word.length >= 3) meaningful++
        }
        return meaningful >= 3
    }

    private fun hashShingle(words: List<String>, start: Int): Long {
        var hash = FNV_OFFSET_BASIS
        for (index in start until start + SHINGLE_WORDS) {
            for (character in words[index]) {
                hash = (hash xor character.code.toLong()) * FNV_PRIME
            }
            hash = (hash xor 255L) * FNV_PRIME
        }
        return hash
    }

    fun formatReport(report: Report): String = buildString {
        appendLine("WOLF STORY ORIGINALITY & REFERENCE REVIEW")
        appendLine("عدد كلمات النص المفحوص: ${report.storyWords}")
        appendLine("المراجع التي قورنت فعليًا: ${report.checkedSources}")
        appendLine("مستوى التشابه اللفظي: ${report.overallSeverity.label}")
        if (report.checkedSourceVersions.isNotEmpty()) {
            appendLine("المراجع التي تمت مراجعتها:")
            report.checkedSourceVersions.forEach { source ->
                appendLine("• ${source.title} — ${source.sourceType}")
                if (source.usages.isNotEmpty()) appendLine("  استُخدم في: ${source.usages.joinToString("، ")}")
            }
        }
        appendLine("تغطية العبارات المتطابقة من بصمات النص: ${report.overallOverlapPercent}%")
        appendLine("العبارات الطويلة المتكررة داخل القصة: ${report.internalRepeatedPhraseCount}")

        if (report.findings.isEmpty()) {
            appendLine("لم يعثر الفحص على تطابقات لفظية مميزة ضمن المراجع التي تمت مقارنتها.")
        } else {
            appendLine()
            appendLine("نتائج المصادر:")
            report.findings.forEach { finding ->
                appendLine("• ${finding.source.title} — ${finding.source.sourceType}")
                appendLine("  المستوى: ${finding.severity.label}")
                appendLine("  تغطية التطابق: ${finding.coveragePercent}%")
                appendLine("  نوافذ العبارة المتطابقة: ${finding.matchingWindowCount}")
                appendLine("  أطول تسلسل متصل: ${finding.maximumContinuousMatchWords} كلمة")
                if (finding.source.usages.isNotEmpty()) {
                    appendLine("  استُخدم في: ${finding.source.usages.joinToString("، ")}")
                }
                finding.phraseSamples.forEach { appendLine("  مثال للمراجعة: «${it}»") }
            }
        }
        if (report.unavailableSources.isNotEmpty()) {
            appendLine()
            appendLine("مراجع لم تُقارن:")
            report.unavailableSources.forEach { appendLine("• ${it.source.title}: ${it.reason}") }
        }
        if (report.internalRepeatedSamples.isNotEmpty()) {
            appendLine()
            appendLine("عينات تكرار داخل النص:")
            report.internalRepeatedSamples.forEach { appendLine("• «${it}»") }
        }
        if (report.warnings.isNotEmpty()) {
            appendLine()
            appendLine("حدود وملحوظات الفحص:")
            report.warnings.forEach { appendLine("• ${it}") }
        }
        appendLine()
        append("هذا التقرير أداة مساعدة للمراجعة، وليس شهادة بأن القصة أصلية أو حكمًا بوقوع الانتحال.")
    }.trim()
}
