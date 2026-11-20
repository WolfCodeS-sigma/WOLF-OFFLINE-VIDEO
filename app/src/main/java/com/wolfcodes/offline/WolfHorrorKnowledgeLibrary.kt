package com.wolfcodes.offline

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Private, device-local TXT horror reference library.
 *
 * The caller supplies an app-private root directory (normally filesDir/wolf_horror_knowledge_library).
 * Content is streamed into per-entry files; list/search never read every story body into memory.
 */
object WolfHorrorKnowledgeLibrary {
    const val MAX_TEXT_CHARS = 4_000_000
    const val MAX_TEXT_BYTES = 16L * 1024L * 1024L
    private const val MAX_INDEX_TERMS = 60_000
    private const val ENTRIES_FOLDER = "entries"
    private const val SETTINGS_FILE = "settings.json"
    private const val MAX_TITLE = 180
    private const val MAX_METADATA_FIELD = 2_000

    private val lock = Any()

    const val SOURCE_PERSONAL_ACCOUNT = "رواية شخصية"
    const val SOURCE_FICTIONAL_STORY = "قصة خيالية"
    const val SOURCE_VERIFIED_INFORMATION = "معلومة موثقة"
    const val SOURCE_INFORMATIONAL_REFERENCE = "مرجع معلوماتي"
    const val SOURCE_USER_SUBMITTED = "محتوى أضافه المستخدم"
    const val SOURCE_APP_AUTHORED = "من تأليف التطبيق"

    val DEFAULT_CATEGORIES = listOf(
        "الرعب الواقعي / الروايات الشخصية",
        "الرعب النفسي",
        "الهلوسة والإدراك",
        "الظواهر الخارقة",
        "الغموض والتحقيق",
        "الفولكلور",
        "قصص المستخدم",
        "قصص من تأليف التطبيق",
        "مراجع توضيحية"
    )

    val SOURCE_TYPES = listOf(
        SOURCE_PERSONAL_ACCOUNT,
        SOURCE_FICTIONAL_STORY,
        SOURCE_VERIFIED_INFORMATION,
        SOURCE_INFORMATIONAL_REFERENCE,
        SOURCE_USER_SUBMITTED,
        SOURCE_APP_AUTHORED
    )

    data class Draft(
        val title: String,
        val category: String = "قصص المستخدم",
        val folder: String = "",
        val tags: List<String> = emptyList(),
        val summary: String = "",
        val sourceType: String = SOURCE_USER_SUBMITTED,
        val sourceLabel: String = "",
        val sourceDate: String = "",
        val characters: List<String> = emptyList(),
        val places: List<String> = emptyList(),
        val events: List<String> = emptyList(),
        val themes: List<String> = emptyList(),
        val keywords: List<String> = emptyList(),
        val includeInStoryReferences: Boolean = true,
        val sourceFileName: String = ""
    )

    data class Entry(
        val id: String,
        val title: String,
        val category: String,
        val folder: String,
        val tags: List<String>,
        val summary: String,
        val sourceType: String,
        val sourceLabel: String,
        val sourceDate: String,
        val sourceFileName: String,
        val characters: List<String>,
        val places: List<String>,
        val events: List<String>,
        val themes: List<String>,
        val keywords: List<String>,
        val includeInStoryReferences: Boolean,
        val contentFingerprint: String,
        val contentChars: Int,
        val contentBytes: Long,
        val createdAt: Long,
        val updatedAt: Long,
        val sourceAddedAt: Long
    )

    data class SaveResult(
        val entry: Entry,
        val duplicateOfExisting: Boolean
    )

    private data class TextStats(
        val fingerprint: String,
        val chars: Int,
        val bytes: Long,
        val terms: Set<String>
    )

    fun categories(root: File): List<String> = synchronized(lock) {
        (DEFAULT_CATEGORIES + list(root).map { it.category })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    fun folders(root: File): List<String> = synchronized(lock) {
        list(root).map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun list(root: File): List<Entry> = synchronized(lock) {
        val directory = entriesDirectory(root)
        if (!directory.isDirectory) return@synchronized emptyList()
        directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.mapNotNull { dir -> runCatching { readMetadata(dir) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt } ?: emptyList()
    }

    fun load(root: File, id: String): Entry? = synchronized(lock) {
        if (!isSafeId(id)) return@synchronized null
        val directory = File(entriesDirectory(root), id)
        if (!directory.isDirectory) return@synchronized null
        runCatching { readMetadata(directory) }.getOrNull()
    }

    fun readContent(root: File, id: String): String? = synchronized(lock) {
        val entry = load(root, id) ?: return@synchronized null
        val file = contentFile(entryDirectory(root, id), entry.contentFingerprint)
        if (!file.isFile) return@synchronized null
        runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    /** The first imported/written text is immutable and is never replaced by later edits. */
    fun readOriginal(root: File, id: String): String? = synchronized(lock) {
        if (!isSafeId(id)) return@synchronized null
        val file = File(entryDirectory(root, id), "original.txt")
        if (!file.isFile) return@synchronized null
        runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun create(
        root: File,
        draft: Draft,
        reader: Reader,
        sourceFileName: String = ""
    ): SaveResult = synchronized(lock) {
        val safeDraft = normalizeDraft(draft)
        val entries = entriesDirectory(root)
        if (!entries.exists() && !entries.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد مكتبة الرعب المحلية")
        }

        val staging = File(entries, ".staging-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IllegalStateException("تعذر تجهيز مساحة استيراد النص")
        try {
            val temporaryContent = File(staging, "content.pending")
            val stats = reader.use { writeText(it, temporaryContent) }
            val duplicate = list(root).firstOrNull { it.contentFingerprint == stats.fingerprint }
            if (duplicate != null) {
                staging.deleteRecursively()
                return@synchronized SaveResult(duplicate, true)
            }

            val entryId = UUID.randomUUID().toString()
            val contentName = contentFileName(stats.fingerprint)
            val content = File(staging, contentName)
            moveReplace(temporaryContent, content)
            copyFile(content, File(staging, "original.txt"))

            val now = System.currentTimeMillis()
            val entry = entryFromDraft(
                id = entryId,
                draft = safeDraft.copy(sourceFileName = safeFileName(sourceFileName)),
                fingerprint = stats.fingerprint,
                chars = stats.chars,
                bytes = stats.bytes,
                createdAt = now,
                updatedAt = now,
                sourceAddedAt = now
            )
            writeIndex(staging, entry, stats.terms)
            writeMetadata(staging, entry)

            val destination = File(entries, entryId)
            if (!staging.renameTo(destination)) {
                throw IllegalStateException("تعذر تثبيت القصة داخل المكتبة")
            }
            SaveResult(entry, false)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun createFromText(
        root: File,
        draft: Draft,
        text: String,
        sourceFileName: String = ""
    ): SaveResult = create(root, draft, text.reader(), sourceFileName)

    /** Updates the working copy and metadata while preserving original.txt byte-for-byte. */
    fun update(
        root: File,
        id: String,
        draft: Draft,
        reader: Reader
    ): SaveResult = synchronized(lock) {
        require(isSafeId(id)) { "معرّف القصة غير صالح" }
        val directory = entryDirectory(root, id)
        require(directory.isDirectory) { "القصة لم تعد موجودة في المكتبة" }
        val current = readMetadata(directory)
        val safeDraft = normalizeDraft(draft)
        val pending = File(directory, "content.pending-${UUID.randomUUID()}")
        try {
            val stats = reader.use { writeText(it, pending) }
            val duplicate = list(root).firstOrNull {
                it.id != id && it.contentFingerprint == stats.fingerprint
            }
            if (duplicate != null) {
                pending.delete()
                return@synchronized SaveResult(duplicate, true)
            }

            val contentName = contentFileName(stats.fingerprint)
            val destinationContent = File(directory, contentName)
            if (stats.fingerprint == current.contentFingerprint && destinationContent.isFile) {
                pending.delete()
            } else {
                moveReplace(pending, destinationContent)
            }

            val now = System.currentTimeMillis()
            val entry = entryFromDraft(
                id = id,
                draft = safeDraft.copy(
                    sourceFileName = safeDraft.sourceFileName.ifBlank { current.sourceFileName }
                ),
                fingerprint = stats.fingerprint,
                chars = stats.chars,
                bytes = stats.bytes,
                createdAt = current.createdAt,
                updatedAt = now,
                sourceAddedAt = current.sourceAddedAt
            )
            writeIndex(directory, entry, stats.terms)
            // Metadata is the commit marker: it points to the new immutable content version.
            writeMetadata(directory, entry)
            cleanupOldContentVersions(directory, stats.fingerprint)
            SaveResult(entry, false)
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
    }

    fun updateFromText(root: File, id: String, draft: Draft, text: String): SaveResult =
        update(root, id, draft, text.reader())

    /** Updates catalog fields without loading a large TXT file into the editor. */
    fun updateMetadata(root: File, id: String, draft: Draft): Entry = synchronized(lock) {
        require(isSafeId(id)) { "معرّف القصة غير صالح" }
        val directory = entryDirectory(root, id)
        require(directory.isDirectory) { "القصة لم تعد موجودة في المكتبة" }
        val current = readMetadata(directory)
        val safeDraft = normalizeDraft(draft)
        val entry = entryFromDraft(
            id = id,
            draft = safeDraft.copy(sourceFileName = safeDraft.sourceFileName.ifBlank { current.sourceFileName }),
            fingerprint = current.contentFingerprint,
            chars = current.contentChars,
            bytes = current.contentBytes,
            createdAt = current.createdAt,
            updatedAt = System.currentTimeMillis(),
            sourceAddedAt = current.sourceAddedAt
        )
        // Content indexes are immutable per content fingerprint. Metadata is searched
        // directly from the current metadata object, so a rename cannot leave stale tokens.
        writeMetadata(directory, entry)
        entry
    }

    /** Bounded preview helpers keep very large imported stories out of EditText and dialogs. */
    fun readContentPreview(root: File, id: String, maxChars: Int = 250_000): String? =
        readPreview(root, id, "content", maxChars)

    fun readOriginalPreview(root: File, id: String, maxChars: Int = 40_000): String? =
        readPreview(root, id, "original", maxChars)

    private fun readPreview(root: File, id: String, kind: String, maxChars: Int): String? = synchronized(lock) {
        if (!isSafeId(id) || maxChars <= 0) return@synchronized null
        val entry = load(root, id) ?: return@synchronized null
        val directory = entryDirectory(root, id)
        val file = if (kind == "original") File(directory, "original.txt")
            else contentFile(directory, entry.contentFingerprint)
        if (!file.isFile) return@synchronized null
        runCatching {
            file.reader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder(minOf(maxChars, 8192))
                val buffer = CharArray(8192)
                while (output.length < maxChars) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - output.length))
                    if (count < 0) break
                    output.append(buffer, 0, count)
                }
                output.toString()
            }
        }.getOrNull()
    }

    fun delete(root: File, id: String): Boolean = synchronized(lock) {
        if (!isSafeId(id)) return@synchronized false
        val directory = entryDirectory(root, id)
        if (!directory.isDirectory) return@synchronized false
        directory.deleteRecursively()
    }

    /**
     * Searches title, source metadata, category, folder, tags and indexed story content.
     * Query words are ANDed. Only one small per-entry index is loaded at a time.
     */
    fun search(
        root: File,
        query: String = "",
        category: String? = null,
        folder: String? = null,
        referencesOnly: Boolean = false
    ): List<Entry> = synchronized(lock) {
        val queryTerms = tokenize(query)
        list(root).filter { entry ->
            (category.isNullOrBlank() || entry.category.equals(category, ignoreCase = true)) &&
                (folder.isNullOrBlank() || entry.folder.equals(folder, ignoreCase = true)) &&
                (!referencesOnly || entry.includeInStoryReferences) &&
                (queryTerms.isEmpty() || run {
                    val contentTerms = readOrRebuildIndex(root, entry)
                    val catalogTerms = metadataTerms(entry)
                    queryTerms.all { it in contentTerms || it in catalogTerms }
                })
        }
    }

    fun searchStoryReferences(
        root: File,
        query: String = "",
        category: String? = null
    ): List<Entry> = search(root, query, category, referencesOnly = true)

    fun autoSaveAppAuthoredStoriesEnabled(root: File): Boolean = synchronized(lock) {
        val file = File(root, SETTINGS_FILE)
        if (!file.isFile) return@synchronized true
        runCatching { JSONObject(file.readText(Charsets.UTF_8)).optBoolean("autoSaveAppAuthoredStories", true) }
            .getOrDefault(true)
    }

    fun setAutoSaveAppAuthoredStories(root: File, enabled: Boolean) = synchronized(lock) {
        ensureRoot(root)
        writeJsonAtomic(
            File(root, SETTINGS_FILE),
            JSONObject().put("autoSaveAppAuthoredStories", enabled)
        )
    }

    /**
     * Stable hook for the future local story-authoring engine (Phase 11).
     * It saves only final, approved app-authored text and obeys the user's auto-save setting.
     */
    fun saveFinalAppAuthoredStory(
        root: File,
        title: String,
        finalApprovedText: String,
        summary: String = "",
        tags: List<String> = emptyList()
    ): SaveResult? = synchronized(lock) {
        if (!autoSaveAppAuthoredStoriesEnabled(root)) return@synchronized null
        createFromText(
            root,
            Draft(
                title = title,
                category = "قصص من تأليف التطبيق",
                folder = "قصص التطبيق",
                tags = tags,
                summary = summary,
                sourceType = SOURCE_APP_AUTHORED,
                sourceLabel = "النص النهائي المعتمد من محرك تأليف التطبيق",
                includeInStoryReferences = true
            ),
            finalApprovedText
        )
    }

    private fun entriesDirectory(root: File): File = File(root, ENTRIES_FOLDER)

    private fun entryDirectory(root: File, id: String): File = File(entriesDirectory(root), id)

    private fun ensureRoot(root: File) {
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مساحة المكتبة المحلية")
        }
    }

    private fun normalizeDraft(draft: Draft): Draft {
        val title = draft.title.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE)
        require(title.isNotBlank()) { "اكتب عنوان القصة" }
        val category = draft.category.trim().take(100).ifBlank { "قصص المستخدم" }
        val folder = draft.folder.trim().take(100)
        val sourceType = draft.sourceType.trim().take(80).ifBlank { SOURCE_USER_SUBMITTED }
        return draft.copy(
            title = title,
            category = category,
            folder = folder,
            tags = cleanList(draft.tags, 40),
            summary = draft.summary.trim().take(MAX_METADATA_FIELD),
            sourceType = sourceType,
            sourceLabel = draft.sourceLabel.trim().take(240),
            sourceDate = draft.sourceDate.trim().take(40),
            sourceFileName = safeFileName(draft.sourceFileName),
            characters = cleanList(draft.characters, 80),
            places = cleanList(draft.places, 80),
            events = cleanList(draft.events, 80),
            themes = cleanList(draft.themes, 80),
            keywords = cleanList(draft.keywords, 120)
        )
    }

    private fun cleanList(values: List<String>, maxItems: Int): List<String> =
        values.asSequence().map { it.trim().take(100) }.filter { it.isNotBlank() }
            .distinct().take(maxItems).toList()

    private fun safeFileName(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\').trim().take(160)

    private fun entryFromDraft(
        id: String,
        draft: Draft,
        fingerprint: String,
        chars: Int,
        bytes: Long,
        createdAt: Long,
        updatedAt: Long,
        sourceAddedAt: Long
    ): Entry = Entry(
        id = id,
        title = draft.title,
        category = draft.category,
        folder = draft.folder,
        tags = draft.tags,
        summary = draft.summary,
        sourceType = draft.sourceType,
        sourceLabel = draft.sourceLabel,
        sourceDate = draft.sourceDate,
        sourceFileName = draft.sourceFileName,
        characters = draft.characters,
        places = draft.places,
        events = draft.events,
        themes = draft.themes,
        keywords = draft.keywords,
        includeInStoryReferences = draft.includeInStoryReferences,
        contentFingerprint = fingerprint,
        contentChars = chars,
        contentBytes = bytes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceAddedAt = sourceAddedAt
    )

    private fun writeText(reader: Reader, destination: File): TextStats {
        val digest = MessageDigest.getInstance("SHA-256")
        val terms = LinkedHashSet<String>()
        val tokenizer = TokenCollector(terms)
        var chars = 0
        var nonWhitespace = false
        val buffer = CharArray(8192)
        val output = FileOutputStream(destination)
        val digestOutput = DigestOutputStream(BufferedOutputStream(output), digest)
        val writer = OutputStreamWriter(digestOutput, StandardCharsets.UTF_8)
        try {
            var firstChunk = true
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                var start = 0
                if (firstChunk) {
                    firstChunk = false
                    if (count > 0 && buffer[0] == '\uFEFF') start = 1
                }
                val length = count - start
                if (length <= 0) continue
                chars += length
                require(chars <= MAX_TEXT_CHARS) {
                    "حجم النص يتجاوز الحد المحلي المدعوم (${MAX_TEXT_CHARS} حرف)"
                }
                for (index in start until count) {
                    if (!buffer[index].isWhitespace()) nonWhitespace = true
                }
                writer.write(buffer, start, length)
                tokenizer.accept(buffer, start, length)
                if (chars % 65_536 < length) writer.flush()
            }
            writer.flush()
        } finally {
            writer.close()
        }
        require(nonWhitespace) { "ملف TXT فارغ أو لا يحتوي على نص قابل للحفظ" }
        val fingerprint = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val byteCount = destination.length()
        require(byteCount <= MAX_TEXT_BYTES) {
            "حجم ملف TXT يتجاوز الحد المحلي المدعوم (16 ميجابايت)"
        }
        return TextStats(fingerprint, chars, byteCount, tokenizer.finish())
    }

    private class TokenCollector(private val tokens: MutableSet<String>) {
        private val current = StringBuilder()

        fun accept(buffer: CharArray, start: Int, length: Int) {
            val end = start + length
            for (index in start until end) append(buffer[index])
        }

        fun accept(value: String) {
            for (char in value) append(char)
            finishToken()
        }

        private fun append(char: Char) {
            val normalized = normalizeCharacter(char)
            if (normalized != null && Character.isLetterOrDigit(normalized)) {
                if (current.length < 80) current.append(normalized)
            } else {
                finishToken()
            }
        }

        private fun finishToken() {
            if (current.isNotEmpty()) {
                if (tokens.size < MAX_INDEX_TERMS) tokens.add(current.toString())
                current.setLength(0)
            }
        }

        fun finish(): Set<String> {
            finishToken()
            return tokens
        }
    }

    private fun normalizeCharacter(char: Char): Char? = when (char) {
        '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627'
        '\u0649' -> '\u064A'
        '\u0640' -> null
        else -> {
            if (char in '\u064B'..'\u065F' || char == '\u0670' ||
                char == '\u200E' || char == '\u200F'
            ) null else char.lowercaseChar()
        }
    }

    private fun tokenize(value: String): Set<String> {
        val terms = LinkedHashSet<String>()
        val tokenizer = TokenCollector(terms)
        tokenizer.accept(value)
        return tokenizer.finish()
    }

    private fun metadataTerms(entry: Entry): Set<String> {
        val values = buildString {
            append(entry.title).append(' ')
            append(entry.category).append(' ')
            append(entry.folder).append(' ')
            append(entry.summary).append(' ')
            append(entry.sourceType).append(' ')
            append(entry.sourceLabel).append(' ')
            append(entry.sourceDate).append(' ')
            append(entry.sourceFileName).append(' ')
            append(entry.tags.joinToString(" ")).append(' ')
            append(entry.characters.joinToString(" ")).append(' ')
            append(entry.places.joinToString(" ")).append(' ')
            append(entry.events.joinToString(" ")).append(' ')
            append(entry.themes.joinToString(" ")).append(' ')
            append(entry.keywords.joinToString(" "))
        }
        return tokenize(values)
    }

    private fun writeIndex(directory: File, entry: Entry, contentTerms: Set<String>) {
        // This index contains only story-body terms. Catalog fields are evaluated from
        // the current metadata so metadata edits can never leave stale searchable terms.
        val json = JSONObject()
            .put("fingerprint", entry.contentFingerprint)
            .put("terms", JSONArray(contentTerms))
        writeJsonAtomic(indexFile(directory, entry.contentFingerprint), json)
    }

    private fun readContentTerms(directory: File, fingerprint: String): Set<String> {
        val content = contentFile(directory, fingerprint)
        if (!content.isFile) return emptySet()
        val terms = LinkedHashSet<String>()
        val tokenizer = TokenCollector(terms)
        content.reader(Charsets.UTF_8).buffered().use { reader ->
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                tokenizer.accept(buffer, 0, count)
            }
        }
        return tokenizer.finish()
    }

    private fun readOrRebuildIndex(root: File, entry: Entry): Set<String> {
        val directory = entryDirectory(root, entry.id)
        val file = indexFile(directory, entry.contentFingerprint)
        if (file.isFile) {
            val cached = runCatching {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                if (json.optString("fingerprint") != entry.contentFingerprint) return@runCatching null
                val array = json.optJSONArray("terms") ?: return@runCatching null
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
            }.getOrNull()
            if (cached != null) return cached
        }

        val contentTerms = readContentTerms(directory, entry.contentFingerprint)
        writeIndex(directory, entry, contentTerms)
        return contentTerms
    }

    private fun readMetadata(directory: File): Entry {
        val json = JSONObject(File(directory, "metadata.json").readText(Charsets.UTF_8))
        val id = json.getString("id")
        require(isSafeId(id) && directory.name == id) { "معرّف مكتبة غير صالح" }
        val fingerprint = json.getString("contentFingerprint")
        require(fingerprint.matches(Regex("[a-f0-9]{64}"))) { "بصمة محتوى غير صالحة" }
        fun strings(name: String): List<String> {
            val array = json.optJSONArray(name) ?: JSONArray()
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }
        return Entry(
            id = id,
            title = json.optString("title", "قصة بدون عنوان"),
            category = json.optString("category", "قصص المستخدم"),
            folder = json.optString("folder", ""),
            tags = strings("tags"),
            summary = json.optString("summary", ""),
            sourceType = json.optString("sourceType", SOURCE_USER_SUBMITTED),
            sourceLabel = json.optString("sourceLabel", ""),
            sourceDate = json.optString("sourceDate", ""),
            sourceFileName = json.optString("sourceFileName", ""),
            characters = strings("characters"),
            places = strings("places"),
            events = strings("events"),
            themes = strings("themes"),
            keywords = strings("keywords"),
            includeInStoryReferences = json.optBoolean("includeInStoryReferences", true),
            contentFingerprint = fingerprint,
            contentChars = json.optInt("contentChars", 0).coerceAtLeast(0),
            contentBytes = json.optLong("contentBytes", 0L).coerceAtLeast(0L),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            sourceAddedAt = json.optLong("sourceAddedAt", json.optLong("createdAt", 0L))
        )
    }

    private fun writeMetadata(directory: File, entry: Entry) {
        val json = JSONObject().apply {
            put("id", entry.id)
            put("title", entry.title)
            put("category", entry.category)
            put("folder", entry.folder)
            put("tags", JSONArray(entry.tags))
            put("summary", entry.summary)
            put("sourceType", entry.sourceType)
            put("sourceLabel", entry.sourceLabel)
            put("sourceDate", entry.sourceDate)
            put("sourceFileName", entry.sourceFileName)
            put("characters", JSONArray(entry.characters))
            put("places", JSONArray(entry.places))
            put("events", JSONArray(entry.events))
            put("themes", JSONArray(entry.themes))
            put("keywords", JSONArray(entry.keywords))
            put("includeInStoryReferences", entry.includeInStoryReferences)
            put("contentFingerprint", entry.contentFingerprint)
            put("contentChars", entry.contentChars)
            put("contentBytes", entry.contentBytes)
            put("createdAt", entry.createdAt)
            put("updatedAt", entry.updatedAt)
            put("sourceAddedAt", entry.sourceAddedAt)
        }
        writeJsonAtomic(File(directory, "metadata.json"), json)
    }

    private fun cleanupOldContentVersions(directory: File, keepFingerprint: String) {
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith("content-") && file.name != contentFileName(keepFingerprint)) file.delete()
            if (file.name.startsWith("index-") && file.name != indexFileName(keepFingerprint)) file.delete()
        }
    }

    private fun contentFile(directory: File, fingerprint: String) =
        File(directory, contentFileName(fingerprint))

    private fun contentFileName(fingerprint: String) = "content-${fingerprint}.txt"

    private fun indexFile(directory: File, fingerprint: String) =
        File(directory, indexFileName(fingerprint))

    private fun indexFileName(fingerprint: String) = "index-${fingerprint}.json"

    private fun copyFile(source: File, destination: File) {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun moveReplace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeJsonAtomic(file: File, json: JSONObject) {
        writeBytesAtomic(file, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeBytesAtomic(file: File, bytes: ByteArray) {
        val parent = file.parentFile ?: throw IllegalStateException("مسار ملف مكتبة غير صالح")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد بيانات المكتبة")
        }
        val temporary = File(parent, file.name + ".pending-${UUID.randomUUID()}")
        try {
            FileOutputStream(temporary).use { output ->
                BufferedOutputStream(output).use { buffered ->
                    buffered.write(bytes)
                    buffered.flush()
                    output.fd.sync()
                }
            }
            moveReplace(temporary, file)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun isSafeId(id: String): Boolean =
        id.matches(Regex("[a-fA-F0-9-]{36}"))

    /** Android document-picker helper; malformed UTF-8 files are rejected, not silently mangled. */
    fun strictUtf8Reader(input: java.io.InputStream): Reader =
        InputStreamReader(
            BufferedInputStream(input),
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        )
}
