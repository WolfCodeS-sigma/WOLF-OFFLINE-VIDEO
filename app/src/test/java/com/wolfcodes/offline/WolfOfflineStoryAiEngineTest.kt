package com.wolfcodes.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WolfOfflineStoryAiEngineTest {
    @Test fun chapterCountCoversTargetRange() {
        assertEquals(1, WolfOfflineStoryAiEngine.chapterCount(500))
        assertEquals(4, WolfOfflineStoryAiEngine.chapterCount(3_000))
        assertEquals(63, WolfOfflineStoryAiEngine.chapterCount(50_000))
    }

    @Test fun invalidTargetsAndIncompleteBriefsAreRejected() {
        assertFalse(runCatching { WolfOfflineStoryAiEngine.chapterCount(499) }.isSuccess)
        assertFalse(runCatching { WolfOfflineStoryAiEngine.chapterCount(50_001) }.isSuccess)
        assertFalse(runCatching { WolfOfflineStoryAiEngine.validateBrief(brief(500).copy(premise = "قصيرة")) }.isSuccess)
        assertFalse(runCatching { WolfOfflineStoryAiEngine.validateBrief(brief(500).copy(premise = "فكرة".repeat(1_100))) }.isSuccess)
        assertFalse(runCatching { WolfOfflineStoryAiEngine.validateBrief(brief(500).copy(title = "ع".repeat(181))) }.isSuccess)
    }

    @Test fun parserAcceptsExactChapterCountAndFallsBackSafely() {
        val brief = brief(1_600)
        val valid = """{"logline":"خطاف","characters":"شخصية","secrets":"سر","ending":"نهاية","continuity_summary":"ملخص","chapter_titles":["البيت","المرآة"]}"""
        assertEquals(listOf("البيت", "المرآة"), WolfOfflineStoryAiEngine.parsePlan(valid, brief).chapterTitles)
        val invalid = WolfOfflineStoryAiEngine.parsePlan("""{"chapter_titles":["واحد"]}""", brief)
        assertEquals(listOf("الفصل 1", "الفصل 2"), invalid.chapterTitles)
    }

    @Test fun chapterCheckpointAdvancesContinuityAndPreventsExtraChapter() {
        val brief = brief(500)
        val plan = WolfOfflineStoryAiEngine.parsePlan("""{"chapter_titles":["النداء"],"continuity_summary":"البداية"}""", brief)
        val first = WolfOfflineStoryAiEngine.newSession(brief, plan)
        val next = WolfOfflineStoryAiEngine.withChapter(first, "هذا فصل تجريبي طويل بما يكفي للاختبار. ".repeat(10))
        assertEquals(1, next.chapters.size)
        assertTrue(next.isComplete)
        assertFalse(next.approved)
        assertTrue(next.continuitySummary.contains("هذا فصل تجريبي"))
        assertFalse(runCatching { WolfOfflineStoryAiEngine.withChapter(next, "فصل آخر ".repeat(20)) }.isSuccess)
    }

    @Test fun sessionCheckpointRoundTrips() {
        val root = Files.createTempDirectory("wolf-story-ai").toFile()
        try {
            val brief = brief(500)
            val plan = WolfOfflineStoryAiEngine.parsePlan("""{"chapter_titles":["البيت"],"continuity_summary":"ملخص البداية"}""", brief)
            val session = WolfOfflineStoryAiEngine.withChapter(
                WolfOfflineStoryAiEngine.newSession(brief, plan), "الفصل الأول يبدأ بحدث غريب. ".repeat(12)
            )
            WolfOfflineStoryAiEngine.saveSession(root, session)
            val restored = WolfOfflineStoryAiEngine.loadLatestSession(root)
            assertNotNull(restored)
            assertEquals(session.id, restored!!.id)
            assertEquals(session.chapters, restored.chapters)
            assertEquals(session.brief, restored.brief)
        } finally { root.deleteRecursively() }
    }

    @Test fun reviewedFinalTextAndApprovalSurviveSessionRestore() {
        val root = Files.createTempDirectory("wolf-story-final-review").toFile()
        try {
            val brief = brief(500)
            val plan = WolfOfflineStoryAiEngine.parsePlan(
                """{"chapter_titles":["البيت"],"continuity_summary":"بداية"}""", brief
            )
            val generated = WolfOfflineStoryAiEngine.withChapter(
                WolfOfflineStoryAiEngine.newSession(brief, plan),
                "الفصل الأصلي الذي كتبه النموذج. ".repeat(8)
            )
            val reviewedText = "النص النهائي بعد مراجعة المستخدم وتعديلاته. ".repeat(20)
            val approved = generated.copy(finalText = reviewedText, approved = true)
            WolfOfflineStoryAiEngine.saveSession(root, approved)

            val restored = WolfOfflineStoryAiEngine.loadLatestSession(root)
            assertNotNull(restored)
            assertEquals(reviewedText, restored!!.finalText)
            assertTrue(restored.approved)
            assertEquals(generated.chapters, restored.chapters)
        } finally { root.deleteRecursively() }
    }

    @Test fun unapprovedReviewedDraftSurvivesSessionRestore() {
        val root = Files.createTempDirectory("wolf-story-draft-review").toFile()
        try {
            val brief = brief(500)
            val plan = WolfOfflineStoryAiEngine.parsePlan(
                """{"chapter_titles":["البيت"],"continuity_summary":"بداية"}""", brief
            )
            val generated = WolfOfflineStoryAiEngine.withChapter(
                WolfOfflineStoryAiEngine.newSession(brief, plan),
                "الفصل الأصلي الذي كتبه النموذج. ".repeat(8)
            )
            val draftText = "نسخة المستخدم المعدلة التي لم يعتمدها بعد. ".repeat(20)
            WolfOfflineStoryAiEngine.saveSession(root, generated.copy(finalText = draftText, approved = false))
            val restored = WolfOfflineStoryAiEngine.loadLatestSession(root)
            assertNotNull(restored)
            assertEquals(draftText, restored!!.finalText)
            assertFalse(restored.approved)
        } finally { root.deleteRecursively() }
    }

    @Test fun loadRecoversCheckpointAfterInterruptedAtomicReplacement() {
        val root = Files.createTempDirectory("wolf-story-recovery").toFile()
        try {
            val brief = brief(500)
            val plan = WolfOfflineStoryAiEngine.parsePlan("""{"chapter_titles":["البيت"],"continuity_summary":"بداية"}""", brief)
            val session = WolfOfflineStoryAiEngine.newSession(brief, plan)
            WolfOfflineStoryAiEngine.saveSession(root, session)
            val target = File(root, "${session.id}.json")
            val backup = File(root, "${session.id}.json.backup")
            assertTrue(target.renameTo(backup))
            assertNotNull(WolfOfflineStoryAiEngine.loadLatestSession(root))
            assertTrue(target.isFile)
            assertFalse(backup.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun planPromptRequiresOriginalWorkAndNonCopying() {
        val prompt = WolfOfflineStoryAiEngine.buildPlanPrompt(brief(500), listOf("مرجع سياقي"))
        assertTrue(prompt.contains("أصلية"))
        assertTrue(prompt.contains("لا تنسخ"))
        assertTrue(prompt.contains("مرجع سياقي"))
    }

    private fun brief(words: Int) = WolfOfflineStoryAiEngine.Brief(
        "اختبار", "قصة عن باب يظهر في منزل مهجور كل ليلة", "رعب نفسي",
        "منزل قديم", "حارس ليلي", "مظلم", "مفتوحة", words
    )
}
