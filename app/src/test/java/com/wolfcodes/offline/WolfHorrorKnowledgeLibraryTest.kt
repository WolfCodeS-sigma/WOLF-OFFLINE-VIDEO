package com.wolfcodes.offline

import java.io.Reader
import java.io.StringReader
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfHorrorKnowledgeLibraryTest {
    private val root = Files.createTempDirectory("wolf-horror-library-test").toFile()

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun draft(
        title: String,
        category: String = "الرعب النفسي",
        folder: String = "قصص تجريبية",
        include: Boolean = true
    ) = WolfHorrorKnowledgeLibrary.Draft(
        title = title,
        category = category,
        folder = folder,
        tags = listOf("غموض", "ليل"),
        summary = "ملخص لا يستبدل النص الأصلي",
        sourceType = WolfHorrorKnowledgeLibrary.SOURCE_PERSONAL_ACCOUNT,
        sourceLabel = "مصدر اختباري",
        sourceDate = "2026-11-20",
        characters = listOf("ليلى"),
        places = listOf("المنزل القديم"),
        events = listOf("طرق الباب"),
        themes = listOf("العزلة"),
        keywords = listOf("باب", "ظل"),
        includeInStoryReferences = include
    )

    @Test
    fun importPersistsFullTextAndSourceMetadata() {
        val text = "في منتصف الليل سمعت ليلى ثلاث طرقات على باب المنزل القديم."
        val result = WolfHorrorKnowledgeLibrary.create(
            root, draft("طرقات منتصف الليل"), StringReader(text), "midnight.txt"
        )

        assertFalse(result.duplicateOfExisting)
        assertEquals(text, WolfHorrorKnowledgeLibrary.readContent(root, result.entry.id))
        assertEquals(text, WolfHorrorKnowledgeLibrary.readOriginal(root, result.entry.id))
        assertEquals("midnight.txt", result.entry.sourceFileName)
        assertEquals("مصدر اختباري", result.entry.sourceLabel)
        assertEquals("2026-11-20", result.entry.sourceDate)
        assertEquals(listOf("ليلى"), result.entry.characters)
        assertEquals(1, WolfHorrorKnowledgeLibrary.list(root).size)
    }

    @Test
    fun editingWorkingCopyNeverOverwritesOriginalAndUpdatesSearchIndex() {
        val original = "The silent corridor was empty before the bell rang."
        val created = WolfHorrorKnowledgeLibrary.createFromText(root, draft("الممر الصامت"), original)
        val changed = "The abandoned lighthouse contains a hidden cellar and a red door."
        val update = WolfHorrorKnowledgeLibrary.updateFromText(
            root, created.entry.id, draft("المنارة المهجورة", category = "الغموض والتحقيق"), changed
        )

        assertFalse(update.duplicateOfExisting)
        assertEquals(original, WolfHorrorKnowledgeLibrary.readOriginal(root, created.entry.id))
        assertEquals(changed, WolfHorrorKnowledgeLibrary.readContent(root, created.entry.id))
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "lighthouse").any { it.id == created.entry.id })
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "cellar").any { it.id == created.entry.id })
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "corridor").none { it.id == created.entry.id })
        assertTrue(
            WolfHorrorKnowledgeLibrary.search(root, category = "الغموض والتحقيق")
                .any { it.id == created.entry.id }
        )
    }

    @Test
    fun metadataOnlyEditReindexesFieldsWithoutReadingOrChangingStory() {
        val text = "A witness saw a pale figure standing beside the station."
        val created = WolfHorrorKnowledgeLibrary.createFromText(root, draft("الشاهد"), text)
        val changed = WolfHorrorKnowledgeLibrary.updateMetadata(
            root,
            created.entry.id,
            draft("شهادة المحطة", category = "الرعب الواقعي / الروايات الشخصية")
                .copy(keywords = listOf("platform", "midnight"), folder = "شهادات")
        )

        assertEquals(text, WolfHorrorKnowledgeLibrary.readContent(root, created.entry.id))
        assertEquals(text, WolfHorrorKnowledgeLibrary.readOriginal(root, created.entry.id))
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "platform").any { it.id == changed.id })
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "station").any { it.id == changed.id })
        assertEquals("شهادات", changed.folder)
    }

    @Test
    fun duplicateContentIsDetectedWithoutCreatingAnotherEntry() {
        val text = "The window opened although every door was locked."
        val first = WolfHorrorKnowledgeLibrary.createFromText(root, draft("نافذة"), text)
        val duplicate = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("نسخة أخرى", category = "الفولكلور"), text
        )

        assertFalse(first.duplicateOfExisting)
        assertTrue(duplicate.duplicateOfExisting)
        assertEquals(first.entry.id, duplicate.entry.id)
        assertEquals(1, WolfHorrorKnowledgeLibrary.list(root).size)
    }

    @Test
    fun editingToAnotherEntryContentIsRejectedWithoutDamagingEitherEntry() {
        val firstText = "A shadow crossed the empty bedroom."
        val secondText = "A bell rang beneath the frozen lake."
        val first = WolfHorrorKnowledgeLibrary.createFromText(root, draft("غرفة النوم"), firstText)
        val second = WolfHorrorKnowledgeLibrary.createFromText(root, draft("البحيرة"), secondText)

        val result = WolfHorrorKnowledgeLibrary.updateFromText(
            root, first.entry.id, draft("تعديل مكرر"), secondText
        )

        assertTrue(result.duplicateOfExisting)
        assertEquals(second.entry.id, result.entry.id)
        assertEquals(firstText, WolfHorrorKnowledgeLibrary.readContent(root, first.entry.id))
        assertEquals(secondText, WolfHorrorKnowledgeLibrary.readContent(root, second.entry.id))
    }

    @Test
    fun referenceOptOutIsRespectedByStoryReferenceSearch() {
        val allowed = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("مرجع مسموح", include = true), "The old cemetery has a broken gate."
        )
        val excluded = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("مرجع مستبعد", include = false), "The old tunnel has a broken gate."
        )

        val references = WolfHorrorKnowledgeLibrary.searchStoryReferences(root, "broken gate")
        assertTrue(references.any { it.id == allowed.entry.id })
        assertFalse(references.any { it.id == excluded.entry.id })
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "tunnel").any { it.id == excluded.entry.id })
    }

    @Test
    fun deleteRemovesEntryAndItsSearchableIndex() {
        val created = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("المرآة", category = "الظواهر الخارقة"), "A mirror reflected a second face."
        )

        assertTrue(WolfHorrorKnowledgeLibrary.delete(root, created.entry.id))
        assertNull(WolfHorrorKnowledgeLibrary.load(root, created.entry.id))
        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "mirror").isEmpty())
        assertFalse(WolfHorrorKnowledgeLibrary.delete(root, created.entry.id))
    }

    @Test
    fun customCategoriesAndFoldersAreDiscoverable() {
        WolfHorrorKnowledgeLibrary.createFromText(
            root,
            draft("قصة مخصصة", category = "رعب بحري", folder = "سفن مهجورة"),
            "A ship drifted toward the rocks."
        )

        assertTrue(WolfHorrorKnowledgeLibrary.categories(root).contains("رعب بحري"))
        assertTrue(WolfHorrorKnowledgeLibrary.categories(root).containsAll(WolfHorrorKnowledgeLibrary.DEFAULT_CATEGORIES))
        assertTrue(WolfHorrorKnowledgeLibrary.folders(root).contains("سفن مهجورة"))
    }

    @Test
    fun appAuthoredFinalStoryAutosaveCanBeDisabled() {
        assertTrue(WolfHorrorKnowledgeLibrary.autoSaveAppAuthoredStoriesEnabled(root))
        val saved = WolfHorrorKnowledgeLibrary.saveFinalAppAuthoredStory(
            root, "قصة التطبيق", "A new fictional story begins at the abandoned house."
        )
        assertNotNull(saved)
        assertEquals(WolfHorrorKnowledgeLibrary.SOURCE_APP_AUTHORED, saved!!.entry.sourceType)
        assertEquals("قصص من تأليف التطبيق", saved.entry.category)

        WolfHorrorKnowledgeLibrary.setAutoSaveAppAuthoredStories(root, false)
        assertFalse(WolfHorrorKnowledgeLibrary.autoSaveAppAuthoredStoriesEnabled(root))
        val skipped = WolfHorrorKnowledgeLibrary.saveFinalAppAuthoredStory(
            root, "قصة لن تحفظ", "A different approved story that must not be saved."
        )
        assertNull(skipped)
        assertEquals(1, WolfHorrorKnowledgeLibrary.list(root).size)
    }

    @Test
    fun arabicSearchNormalizesHamzaFormsAndDiacritics() {
        val created = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("إشارة في الظلام"), "كانت الإِشارة تظهر فوق الباب كل ليلة."
        )

        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "الاشارة").any { it.id == created.entry.id })
    }

    @Test
    fun tokenizerKeepsWordsThatCrossReaderBufferBoundaries() {
        val text = "The mysteriousapparition appeared beside the railway."
        val created = WolfHorrorKnowledgeLibrary.create(
            root, draft("ظهور غامض"), ChunkedReader(text, 2)
        )

        assertTrue(
            WolfHorrorKnowledgeLibrary.search(root, "mysteriousapparition")
                .any { it.id == created.entry.id }
        )
    }

    @Test
    fun missingIndexIsRebuiltFromTheSavedText() {
        val created = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("فهرس مفقود"), "Whispers echoed through the abandoned hospital."
        )
        val index = File(
            File(File(root, "entries"), created.entry.id),
            "index-${created.entry.contentFingerprint}.json"
        )
        assertTrue(index.delete())

        assertTrue(WolfHorrorKnowledgeLibrary.search(root, "whispers").any { it.id == created.entry.id })
        assertTrue(index.isFile)
    }

    @Test
    fun blankContentAndFailedUpdateDoNotCorruptExistingRecords() {
        runCatching {
            WolfHorrorKnowledgeLibrary.createFromText(root, draft("فارغة"), "   \n\t ")
        }.onSuccess { throw AssertionError("Blank content must be rejected") }
        assertTrue(WolfHorrorKnowledgeLibrary.list(root).isEmpty())

        val saved = WolfHorrorKnowledgeLibrary.createFromText(
            root, draft("صالحة"), "A valid story stays stored after a rejected update."
        )
        runCatching {
            WolfHorrorKnowledgeLibrary.updateFromText(root, saved.entry.id, draft("تعديل فارغ"), "")
        }.onSuccess { throw AssertionError("Blank update must be rejected") }

        assertEquals(
            "A valid story stays stored after a rejected update.",
            WolfHorrorKnowledgeLibrary.readContent(root, saved.entry.id)
        )
        assertEquals(1, WolfHorrorKnowledgeLibrary.list(root).size)
    }

    private class ChunkedReader(
        private val value: String,
        private val maxChunk: Int
    ) : Reader() {
        private var position = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= value.length) return -1
            val count = minOf(length, maxChunk, value.length - position)
            value.toCharArray().copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() = Unit
    }
}
