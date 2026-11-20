package com.wolfcodes.offline

import org.junit.Assert.*
import org.junit.Test

class WolfStoryLengthStructureEngineTest {
    @Test fun countsArabicAndEnglishWordsAndIgnoresDiacriticsAndTatweel() {
        val text = "كانت الإِشـارةُ غامضة، ثم ظهر الباب! The old door opened twice."
        assertEquals(11, WolfStoryLengthStructureEngine.words(text))
        assertEquals(5, WolfStoryLengthStructureEngine.words("واحد، اثنان؛ ثلاثة... four-five"))
    }

    @Test fun chapterTargetsAreDeterministicAndSumExactlyToRequestedLength() {
        val titles = (1..12).map { "الفصل $it" }
        val first = WolfStoryLengthStructureEngine.chapterTargets(12_000, titles)
        val second = WolfStoryLengthStructureEngine.chapterTargets(12_000, titles)
        assertEquals(first, second)
        assertEquals(12_000, first.sumOf { it.targetWords })
        assertTrue(first.all { it.targetWords > 0 && it.minimumWords <= it.targetWords && it.maximumWords >= it.targetWords })
        assertEquals(titles, first.map { it.title })
    }

    @Test fun longStoryPlanCoversAllEightNarrativeBeatsInOrder() {
        val targets = WolfStoryLengthStructureEngine.chapterTargets(50_000, (1..64).map { "Chapter $it" })
        val beats = targets.map { it.beat }
        assertEquals(WolfStoryLengthStructureEngine.Beat.HOOK, beats.first())
        assertEquals(WolfStoryLengthStructureEngine.Beat.RESOLUTION, beats.last())
        WolfStoryLengthStructureEngine.Beat.values().forEach { beat -> assertTrue("Missing $beat", beats.contains(beat)) }
        assertTrue(beats.zipWithNext().all { (a, b) -> a.ordinal <= b.ordinal })
    }

    @Test fun invalidLengthTitlesAndToleranceAreRejected() {
        assertFalse(runCatching { WolfStoryLengthStructureEngine.chapterTargets(499, listOf("الفصل")) }.isSuccess)
        assertFalse(runCatching { WolfStoryLengthStructureEngine.chapterTargets(500, emptyList()) }.isSuccess)
        assertFalse(runCatching { WolfStoryLengthStructureEngine.chapterTargets(500, listOf(" ")) }.isSuccess)
        assertFalse(runCatching { WolfStoryLengthStructureEngine.chapterTargets(500, listOf("الفصل"), 0.01) }.isSuccess)
    }

    @Test fun planAndChapterGuidanceIncludeTargetsAndNarrativePurpose() {
        val titles = listOf("البداية", "التحول", "النهاية")
        val plan = WolfStoryLengthStructureEngine.planGuidance(2_400, titles)
        assertTrue(plan.contains("2400 كلمة"))
        assertTrue(plan.contains("الخطاف"))
        assertTrue(plan.contains("الخاتمة"))
        val target = WolfStoryLengthStructureEngine.chapterTargets(2_400, titles)[1]
        val chapter = WolfStoryLengthStructureEngine.chapterGuidance(target)
        assertTrue(chapter.contains(target.targetWords.toString()))
        assertTrue(chapter.contains(target.beat.label))
    }

    @Test fun textReportUsesWordCountAndHonestWholeStoryChapterSemantics() {
        val text = "رعب ".repeat(850)
        val report = WolfStoryLengthStructureEngine.analyzeText(1_000, text, 2)
        assertEquals(850, report.actualWords)
        assertEquals(2, report.expectedChapters)
        assertNull(report.actualChapters)
        assertTrue(report.withinOverallTolerance)
        assertTrue(report.estimatedMinutesAt120Wpm >= report.estimatedMinutesAt160Wpm)
    }

    @Test fun chapterReportFindsIncompleteAndUnderLengthChapters() {
        val titles = listOf("الخطاف", "التصاعد", "الخاتمة")
        val chapters = listOf("كلمة ".repeat(10))
        val report = WolfStoryLengthStructureEngine.analyzeChapters(2_400, chapters, titles)
        assertEquals(1, report.actualChapters)
        assertEquals(3, report.expectedChapters)
        assertTrue(report.findings.any { it.contains("اكتمل 1 من 3") })
        assertTrue(report.findings.any { it.contains("أقصر من نطاقه") })
    }

    @Test fun fullChapterSetReportsAggregateAndPerChapterLength() {
        val titles = (1..4).map { "الفصل $it" }
        val targets = WolfStoryLengthStructureEngine.chapterTargets(3_200, titles)
        val chapters = targets.map { "كلمة ".repeat(it.targetWords) }
        val report = WolfStoryLengthStructureEngine.analyzeChapters(3_200, chapters, titles)
        assertEquals(3_200, report.actualWords)
        assertEquals(4, report.actualChapters)
        assertTrue(report.withinOverallTolerance)
        assertTrue(report.findings.any { it.contains("داخل النطاق المقبول") })
    }
}
