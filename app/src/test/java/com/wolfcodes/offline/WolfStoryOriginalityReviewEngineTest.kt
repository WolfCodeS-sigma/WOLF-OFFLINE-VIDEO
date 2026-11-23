package com.wolfcodes.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WolfStoryOriginalityReviewEngineTest {
    private fun stamp(
        text: String,
        id: String = "library-source-1",
        title: String = "مرجع اختباري",
        type: String = "قصة خيالية"
    ) = WolfStoryOriginalityReviewEngine.SourceStamp(
        id = id,
        title = title,
        sourceType = type,
        sourceLabel = "ملف محلي",
        contentFingerprint = WolfStoryOriginalityReviewEngine.fingerprint(text)
    )

    @Test fun exactLongArabicOverlapIsReportedWithSourceAndPhraseSample() {
        val reference = "في منتصف الليل اقترب الحارس من البئر القديمة وسمع صوت طفل ينادي اسمه من قاع الظلام ثم رأى ضوءا أزرق يتحرك وحده خلف الأشجار الكثيفة"
        val story = "في منتصف الليل اقترب الحارس من البئر القديمة وسمع صوت طفل ينادي اسمه من قاع الظلام ثم رأى ضوءا أزرق يتحرك وحده خلف الأشجار الكثيفة بينما كان الطريق خاليا تماما"
        val source = stamp(reference)
        val report = WolfStoryOriginalityReviewEngine.analyze(
            story,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, reference))
        )

        assertEquals(1, report.checkedSources)
        assertEquals(WolfStoryOriginalityReviewEngine.Severity.HIGH, report.overallSeverity)
        assertEquals("مرجع اختباري", report.findings.single().source.title)
        assertTrue(report.findings.single().maximumContinuousMatchWords >= 18)
        assertTrue(report.findings.single().phraseSamples.isNotEmpty())
        assertTrue(WolfStoryOriginalityReviewEngine.formatReport(report).contains("مرجع اختباري"))
    }

    @Test fun ArabicAlefFormsDiacriticsAndTatweelAreNormalizedBeforeComparison() {
        val reference = "إِشَارَةُ الحارسِ قادتْهُ إلى أُطْرُوحَةٍ غريبةٍ داخلَ الممرِّ الحجريِّ القديم"
        val story = "اشارة الحارس قادته الى اطروحة غريبة داخل الممر الحجري القديم"
        val source = stamp(reference)
        val report = WolfStoryOriginalityReviewEngine.analyze(
            story,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, reference))
        )

        assertEquals(WolfStoryOriginalityReviewEngine.Severity.MEDIUM, report.overallSeverity)
        assertTrue(report.findings.single().maximumContinuousMatchWords >= 8)
    }

    @Test fun unrelatedReferenceDoesNotCreateAnOverlapFinding() {
        val sourceText = "المطر يسقط فوق محطة القطار بينما تحمل امرأة حقيبة زرقاء وتنتظر وصول الحافلة الصباحية"
        val story = "المرآة القديمة تعكس وجها لا يعرفه الطبيب ثم تختفي الساعة من فوق الجدار الخشبي"
        val source = stamp(sourceText)
        val report = WolfStoryOriginalityReviewEngine.analyze(
            story,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, sourceText))
        )

        assertEquals(1, report.checkedSources)
        assertTrue(report.findings.isEmpty())
        assertEquals(WolfStoryOriginalityReviewEngine.Severity.NONE, report.overallSeverity)
    }

    @Test fun continuousPhraseLengthRequiresAdjacentWindowsInTheSource() {
        val story = "سقط المطر فوق الباب الخشبي القديم فجأة"
        val reference = "سقط المطر فوق الباب الخشبي القديم ثم انطفأ المصباح في الممر الطويل وبعد ذلك عاد المطر فوق الباب الخشبي القديم فجأة"
        val source = stamp(reference)
        val report = WolfStoryOriginalityReviewEngine.analyze(
            story,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, reference))
        )

        assertEquals(1, report.checkedSources)
        assertTrue(report.findings.isNotEmpty())
        assertEquals(6, report.findings.single().maximumContinuousMatchWords)
        assertEquals(WolfStoryOriginalityReviewEngine.Severity.LOW, report.findings.single().severity)
    }

    @Test fun reportRetainsCheckedSourcesEvenWhenNoPhraseWasMatched() {
        val reference = "المطر يسقط فوق محطة القطار بينما تحمل امرأة حقيبة زرقاء وتنتظر وصول الحافلة الصباحية"
        val story = "المرآة القديمة تعكس وجها لا يعرفه الطبيب ثم تختفي الساعة من فوق الجدار الخشبي"
        val source = stamp(reference, id = "no-match-source", title = "مصدر بلا تطابق").copy(
            sourceLabel = "مرجع أضافه المستخدم",
            sourceDate = "2026-11-20"
        )
        val report = WolfStoryOriginalityReviewEngine.analyze(
            story,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, reference))
        )

        assertEquals(listOf(source), report.checkedSourceVersions)
        val formatted = WolfStoryOriginalityReviewEngine.formatReport(report)
        assertTrue(formatted.contains("مصدر بلا تطابق"))
        assertTrue(formatted.contains("مرجع أضافه المستخدم"))
        assertTrue(formatted.contains("2026-11-20"))
    }

    @Test fun commonStopWordOnlySequencesAreIgnored() {
        val commonText = "في من على الى عن ثم في من على الى عن ثم"
        val source = stamp(commonText)
        val report = WolfStoryOriginalityReviewEngine.analyze(
            commonText,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, commonText))
        )

        assertTrue(report.findings.isEmpty())
        assertEquals(0, report.overallOverlapPercent)
    }

    @Test fun changedReferenceFingerprintIsExcludedAndExplained() {
        val original = "رائحة الخشب المحترق ملأت الغرفة الضيقة قبل أن تنطفئ المصابيح الثلاثة فجأة"
        val changed = "رائحة القهوة ملأت الغرفة الواسعة قبل أن تفتح النافذة الزجاجية"
        val source = stamp(original)
        val report = WolfStoryOriginalityReviewEngine.analyze(
            changed,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, changed))
        )

        assertEquals(0, report.checkedSources)
        assertEquals(1, report.unavailableSources.size)
        assertTrue(report.unavailableSources.single().reason.contains("تغير محتوى المرجع"))
        assertTrue(report.warnings.any { it.contains("لا يمكن اعتبار ذلك إثباتًا للأصالة") })
    }

    @Test fun duplicateContentAcrossLibraryEntriesIsComparedOnlyOnce() {
        val text = "توقفت السيارة أمام المنزل المهجور ووجد السائق رسالة تحمل اسمه بخط يده القديم"
        val first = stamp(text, id = "ref-one", title = "المصدر الأول")
        val second = stamp(text, id = "ref-two", title = "نسخة مكررة")
        val report = WolfStoryOriginalityReviewEngine.analyze(
            text,
            sequenceOf(
                WolfStoryOriginalityReviewEngine.ReferenceText(first, text),
                WolfStoryOriginalityReviewEngine.ReferenceText(second, text)
            )
        )

        assertEquals(1, report.checkedSources)
        assertEquals(1, report.findings.size)
        assertEquals(setOf("ref-one", "ref-two"), report.checkedSourceVersions.map { it.id }.toSet())
        assertTrue(report.checkedSourceVersions.all { it.contentFingerprint == first.contentFingerprint })
    }

    @Test fun repeatedPhrasesInsideTheStoryAreReportedSeparately() {
        val phrase = "فتح الحارس الباب الحديدي فسمع خطوات خلفه مباشرة"
        val story = "$phrase ثم عاد إلى الممر. $phrase ثم أطفأ الضوء. $phrase"
        val report = WolfStoryOriginalityReviewEngine.analyze(story, emptySequence())

        assertTrue(report.internalRepeatedPhraseCount > 0)
        assertTrue(report.internalRepeatedSamples.isNotEmpty())
        assertTrue(report.warnings.any { it.contains("لم تتم مقارنة النص") })
    }

    @Test fun storyAndSourceWordProcessingLimitsAreReported() {
        val story = "كلمة ".repeat(WolfStoryOriginalityReviewEngine.MAX_REVIEW_WORDS + 10)
        val report = WolfStoryOriginalityReviewEngine.analyze(story, emptySequence())

        assertEquals(WolfStoryOriginalityReviewEngine.MAX_REVIEW_WORDS, report.storyWords)
        assertTrue(report.storyTruncated)
        assertTrue(report.warnings.any { it.contains("حد الذاكرة الآمن") })
    }

    @Test fun exactReviewWordLimitWithOnlyTrailingWhitespaceIsNotMarkedTruncated() {
        val story = "كلمة ".repeat(WolfStoryOriginalityReviewEngine.MAX_REVIEW_WORDS).trimEnd() + "   ..."
        val report = WolfStoryOriginalityReviewEngine.analyze(story, emptySequence())

        assertEquals(WolfStoryOriginalityReviewEngine.MAX_REVIEW_WORDS, report.storyWords)
        assertFalse(report.storyTruncated)
    }

    @Test fun highlyRepeatedSourcePhrasesTriggerPositionLimitWarning() {
        val repeated = "الحارس فتح الباب الحديدي ثم عاد إلى الغرفة المظلمة "
        val reference = repeated.repeat(140)
        val story = repeated.repeat(40)
        val source = stamp(reference, id = "repeated-source", title = "مرجع متكرر")
        val report = WolfStoryOriginalityReviewEngine.analyze(
            story,
            sequenceOf(WolfStoryOriginalityReviewEngine.ReferenceText(source, reference))
        )

        assertEquals(1, report.checkedSources)
        assertTrue(report.warnings.any { it.contains("تقييد مواضع العبارات") })
    }

    @Test fun reviewFingerprintChangesWhenReviewedTextChanges() {
        val first = WolfStoryOriginalityReviewEngine.fingerprint("قصة أصلية")
        val second = WolfStoryOriginalityReviewEngine.fingerprint("قصة أصلية مختلفة")
        assertNotEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test fun ledgerMergesUsageLabelsWithoutDuplicatingSameSourceVersion() {
        val root = Files.createTempDirectory("wolf-originality-ledger").toFile()
        try {
            val text = "هذا نص مرجعي محلي لاختبار سجل المصدر"
            val source = stamp(text).copy(sourceDate = "2026-11-20")
            WolfStoryOriginalityReviewEngine.recordSources(root, "session-12345678", "المخطط", listOf(source))
            WolfStoryOriginalityReviewEngine.recordSources(root, "session-12345678", "الفصل 1", listOf(source))

            val restored = WolfStoryOriginalityReviewEngine.loadSources(root, "session-12345678")
            assertEquals(1, restored.size)
            assertEquals(listOf("المخطط", "الفصل 1"), restored.single().usages)
            assertEquals(source.contentFingerprint, restored.single().contentFingerprint)
            assertEquals("2026-11-20", restored.single().sourceDate)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun ledgerRecoversAfterInterruptedAtomicReplacement() {
        val root = Files.createTempDirectory("wolf-originality-recovery").toFile()
        val sessionId = "session-12345678"
        try {
            val sourceText = "المصدر الأصلي الذي يجب أن يستعيده السجل المحلي بعد الانقطاع"
            WolfStoryOriginalityReviewEngine.recordSources(
                root, sessionId, "المخطط", listOf(stamp(sourceText))
            )
            val target = File(root, "$sessionId.json")
            val backup = File(root, "$sessionId.json.backup")
            assertTrue(target.renameTo(backup))

            val restored = WolfStoryOriginalityReviewEngine.loadSources(root, sessionId)
            assertEquals(1, restored.size)
            assertTrue(target.isFile)
            assertFalse(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun ledgerRejectsPathTraversalAndInvalidFingerprint() {
        val root = Files.createTempDirectory("wolf-originality-validation").toFile()
        try {
            assertFalse(runCatching {
                WolfStoryOriginalityReviewEngine.loadSources(root, "../outside")
            }.isSuccess)
            val invalid = WolfStoryOriginalityReviewEngine.SourceStamp(
                id = "source-1",
                title = "مرجع",
                sourceType = "قصة",
                sourceLabel = "",
                contentFingerprint = "not-a-sha256"
            )
            assertFalse(runCatching {
                WolfStoryOriginalityReviewEngine.recordSources(
                    root, "session-12345678", "المخطط", listOf(invalid)
                )
            }.isSuccess)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun oversizedUnavailableSourceListIsCappedAndDisclosed() {
        val unavailable = (1..(WolfStoryOriginalityReviewEngine.MAX_REFERENCES + 2)).map { index ->
            val text = "محتوى مرجع غير متاح رقم $index"
            WolfStoryOriginalityReviewEngine.UnavailableSource(
                stamp(text, id = "missing-$index", title = "مرجع غير متاح $index"),
                "المصدر غير موجود"
            )
        }
        val report = WolfStoryOriginalityReviewEngine.analyze(
            "قصة قصيرة للمراجعة",
            emptySequence(),
            unavailable
        )

        assertEquals(WolfStoryOriginalityReviewEngine.MAX_REFERENCES, report.unavailableSources.size)
        assertTrue(report.warnings.any { it.contains("إخفاء 2 مراجع إضافية") })
    }

    @Test fun reportsWhenOnlyTheSafeReferenceLimitCanBeChecked() {
        val text = "الحارس وجد مفتاحا اسود تحت السجادة ثم سمع صوتا خلف الباب المغلق"
        val references = (1..(WolfStoryOriginalityReviewEngine.MAX_REFERENCES + 2)).map { index ->
            val value = text + " $index"
            WolfStoryOriginalityReviewEngine.ReferenceText(
                stamp(value, id = "source-$index", title = "مرجع $index"),
                value
            )
        }
        val report = WolfStoryOriginalityReviewEngine.analyze(text, references.asSequence())

        assertEquals(WolfStoryOriginalityReviewEngine.MAX_REFERENCES, report.checkedSources)
        assertTrue(report.sourceLimitReached)
        assertTrue(report.warnings.any { it.contains("الحد الآمن") })
    }
}
