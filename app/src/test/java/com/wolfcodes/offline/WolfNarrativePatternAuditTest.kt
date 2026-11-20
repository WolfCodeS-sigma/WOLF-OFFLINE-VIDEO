package com.wolfcodes.offline

import org.junit.Assert.*
import org.junit.Test

class WolfNarrativePatternAuditTest {
    @Test fun equivalentEscapeActionsAreGroupedAsOneRepeatedEventFamily() {
        val story = listOf(
            "هرب الرجل من الغرفة بعد سماع الخطوات.",
            "فر مرة أخرى عندما ظهر الظل خلفه.",
            "نجا بصعوبة من الممر قبل إغلاق الباب.",
            "عاد إلى المنزل في الصباح."
        ).joinToString(" ")
        val report = WolfNarrativePatternAudit.analyze(story)

        assertTrue(report.detectedEventCount >= 3)
        assertTrue(report.repeatedEventTypes.contains("هروب ونجاة"))
        assertTrue(report.structuralWarnings.any { it.contains("تكررت عائلات الأحداث") })
    }

    @Test fun distinctEventFamiliesIncreaseNarrativeDiversity() {
        val story = listOf(
            "اكتشف الحارس رسالة قديمة فوق الطاولة.",
            "فتش الغرفة بحثا عن صاحب الرسالة.",
            "ظهر شبح عند النافذة فجأة.",
            "طارد الكيان الرجل عبر الممر.",
            "واجه الرجل الظل عند الباب.",
            "عرف أخيرا سر المنزل."
        ).joinToString(" ")
        val report = WolfNarrativePatternAudit.analyze(story)

        assertTrue(report.distinctEventCount >= 5)
        assertTrue(report.diversityPercent >= 50)
        assertTrue(report.eventSequence.isNotEmpty())
    }

    @Test fun narrativeAuditIsBoundedAndReturnsAdvisoryWarnings() {
        val report = WolfNarrativePatternAudit.analyze("قصة قصيرة بلا أفعال واضحة.")
        assertEquals(10, report.eventTypeCount)
        assertTrue(report.structuralWarnings.isNotEmpty())
        assertTrue(report.warnings.any { it.contains("لا يفهم السياق") })
    }

    @Test fun englishEventSynonymsAreRecognized() {
        val story = listOf(
            "He discovered a locked room.",
            "He investigated the strange sound.",
            "A ghost appeared behind him.",
            "The creature chased him.",
            "He escaped into the street.",
            "He returned before sunrise."
        ).joinToString(" ")
        val report = WolfNarrativePatternAudit.analyze(story)

        assertTrue(report.distinctEventCount >= 5)
    }
}
