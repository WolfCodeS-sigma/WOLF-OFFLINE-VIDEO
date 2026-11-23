package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPipelineTest {
    @Test
    fun offlineAndDirectorUseIdenticalSceneBoundaries() {
        val story = "وصلت إلى البيت. سمعت طرقًا خلف الباب.\n\nاختفى الضوء فجأة."
        val offline = OfflineStoryEngine.split(story)
        val director = StoryDirectorEngine.analyze(story, 30, "9:16", "سينمائي", "رعب نفسي")
        assertEquals(offline, director.scenes.map { it.sourceText })
    }

    @Test
    fun longStoryKeepsAllParagraphsWithinTwelveScenes() {
        val story = (1..20).joinToString("\n\n") { "الفقرة رقم $it تصف حدثًا جديدًا في القصة." }
        val offline = OfflineStoryEngine.split(story)
        val director = StoryDirectorEngine.analyze(story, 60, "9:16", "سينمائي", "رعب نفسي")
        assertEquals(12, offline.size)
        assertEquals(offline, director.scenes.map { it.sourceText })
        for (number in 1..20) {
            assertTrue("Missing paragraph $number", offline.any { it.contains("الفقرة رقم $number") })
        }
    }

    @Test
    fun directorExtendsDurationToGiveEverySceneAtLeastThreeSeconds() {
        val story = (1..8).joinToString("\n\n") { "مشهد رقم $it." }
        val plan = StoryDirectorEngine.analyze(story, 5, "9:16", "سينمائي", "رعب نفسي")
        assertTrue(plan.totalDurationSeconds >= plan.scenes.size * 3)
        assertEquals(plan.totalDurationSeconds, plan.scenes.sumOf { it.durationSeconds })
    }

    @Test
    fun approvedNarrationKeepsItsDurationInsteadOfEnforcingThreeSecondsPerScene() {
        val story = (1..12).joinToString("\n\n") { "مشهد رقم " + it + "." }
        val plan = StoryDirectorEngine.analyze(
            story, 25, "9:16", "سينمائي", "رعب نفسي",
            audioDrivenDuration = true,
            includeIntro = true
        )
        assertEquals(25, plan.totalDurationSeconds)
        assertEquals(22, plan.scenes.sumOf { it.durationSeconds })
        assertTrue(plan.scenes.size <= 22)
    }

    @Test
    fun narrationSceneBudgetMergesSectionsWithoutDroppingStoryText() {
        val scenes = (1..8).map { "فقرة رقم " + it }
        val fitted = StoryDirectorEngine.fitSceneTextsToDuration(scenes, 3)
        assertEquals(3, fitted.size)
        for (number in 1..8) assertTrue(fitted.any { it.contains("فقرة رقم " + number) })
    }

    
    @Test
    fun approvedNarrationDurationDefinesActualFilmRuntime() {
        val shorterNarration = OfflineProductionEngine.buildAudioTimeline(
            requestedFilmSeconds = 30,
            narrationTimingEnabled = true,
            audioDurationMs = 7_000L
        )
        assertEquals(7_000_000L, shorterNarration.filmDurationUs)
        assertEquals(7_000_000L, shorterNarration.narrationLimitUs)

        val longerNarration = OfflineProductionEngine.buildAudioTimeline(
            requestedFilmSeconds = 5,
            narrationTimingEnabled = true,
            audioDurationMs = 45_000L
        )
        assertEquals(45_000_000L, longerNarration.filmDurationUs)
        assertEquals(45_000_000L, longerNarration.narrationLimitUs)

        val withoutNarrationTiming = OfflineProductionEngine.buildAudioTimeline(
            requestedFilmSeconds = 30,
            narrationTimingEnabled = false,
            audioDurationMs = 7_000L
        )
        assertEquals(30_000_000L, withoutNarrationTiming.filmDurationUs)
        assertEquals(30_000_000L, withoutNarrationTiming.narrationLimitUs)

        val missingNarrationDuration = OfflineProductionEngine.buildAudioTimeline(
            requestedFilmSeconds = 30,
            narrationTimingEnabled = true,
            audioDurationMs = null
        )
        assertEquals(30_000_000L, missingNarrationDuration.filmDurationUs)
        assertEquals(30_000_000L, missingNarrationDuration.narrationLimitUs)
    }

    @Test
    fun renderRevisionInvalidatesLegacyStoryboardCheckpointFingerprint() {
        assertEquals("storyboard-planner-v3", OfflineProductionEngine.STORYBOARD_RENDER_REVISION)
        assertEquals(2, WolfSceneAssetStore.DEPTH_SEGMENTATION_VERSION)

        val legacy = WolfLongFilmEngine.fingerprint(
            story = "قصة اختبار",
            framesPerScene = listOf(120, 120),
            fps = 12,
            width = 720,
            height = 1280,
            introFrames = 36,
            typographyEnabled = true,
            mediaInputs = "depth-segmentation-v1#storyboard-planner-v2"
        )
        val current = WolfLongFilmEngine.fingerprint(
            story = "قصة اختبار",
            framesPerScene = listOf(120, 120),
            fps = 12,
            width = 720,
            height = 1280,
            introFrames = 36,
            typographyEnabled = true,
            mediaInputs = "depth-segmentation-v${WolfSceneAssetStore.DEPTH_SEGMENTATION_VERSION}#${OfflineProductionEngine.STORYBOARD_RENDER_REVISION}"
        )
        assertTrue(legacy != current)
    }

    @Test
    fun introIsIncludedInsideRequestedFilmDuration() {
        val requestedFilmSeconds = 30
        val introSeconds = 3
        val bodySeconds = requestedFilmSeconds - introSeconds
        val bodyFrames = bodySeconds * 12
        val sceneTexts = StoryDirectorEngine.fitSceneTextsToDuration(
            (1..11).map { "مشهد رقم $it" }, bodySeconds
        )
        val frames = OfflineMotionEngine.allocateFrames(sceneTexts, bodyFrames, 1)

        assertEquals(324, frames.sum())
        assertEquals(360, frames.sum() + introSeconds * 12)
    }

    @Test
    fun introNeverPushesShortFilmsPastTheirRequestedDuration() {
        assertEquals(0, OfflineProductionEngine.introDurationSeconds(1, true))
        assertEquals(1, OfflineProductionEngine.introDurationSeconds(2, true))
        assertEquals(3, OfflineProductionEngine.introDurationSeconds(5, true))
        assertEquals(0, OfflineProductionEngine.introDurationSeconds(30, false))
        assertEquals(3, OfflineProductionEngine.introDurationSeconds(30, true))
    }

    @Test
    fun storyboardShotFrameBudgetsPreserveExactSceneFrames() {
        val plan = StoryDirectorEngine.analyze(
            "سمعت خطوات في الممر. هربت إلى الغرفة ثم ظهر ظل خلف الباب.",
            30, "9:16", "رعب سينمائي مظلم", "رعب نفسي", audioDrivenDuration = true
        )
        val sceneFrames = OfflineMotionEngine.allocateFrames(
            plan.scenes.map { it.sourceText }, 360, minimumFramesPerScene = 1
        )
        val shotFrames = plan.scenes.mapIndexed { index, scene ->
            StoryDirectorEngine.allocateShotFrames(
                sceneFrames[index], scene.shots.map { it.durationSeconds }
            )
        }
        assertEquals(360, shotFrames.sumOf { it.sum() })
        assertTrue(shotFrames.all { it.isNotEmpty() && it.all { frames -> frames > 0 } })
        assertEquals(sceneFrames, shotFrames.map { it.sum() })
    }

    @Test
    fun storyboardChapterSlicesPreserveShotBoundariesAndFrames() {
        val shots = listOf(2, 5, 3)
        val slice = StoryDirectorEngine.sliceShotFrames(shots, startFrame = 4, frameCount = 5)
        assertEquals(listOf(3, 2), slice)
        assertEquals(5, slice.sum())
        assertEquals(2, StoryDirectorEngine.sliceShotPlans(
            listOf(
                StoryDirectorEngine.ShotPlan(1, "A", "24mm", "", "", "", 2, ""),
                StoryDirectorEngine.ShotPlan(2, "B", "50mm", "", "", "", 5, ""),
                StoryDirectorEngine.ShotPlan(3, "C", "85mm", "", "", "", 3, "")
            ),
            shots, startFrame = 4, frameCount = 5
        ).size)
    }

    @Test
    fun chapterStartingAtSecondOrThirdShotKeepsOriginalImageSlots() {
        val sourceImages = listOf("image-1", "image-2", "image-3")
        val originalPlans = listOf(
            StoryDirectorEngine.ShotPlan(1, "A", "24mm", "تكوين واسع", "إضاءة منخفضة", "تثبيت", 2, ""),
            StoryDirectorEngine.ShotPlan(2, "B", "50mm", "المصدر الصوتي", "إضاءة باردة", "اقتراب بطيء", 5, ""),
            StoryDirectorEngine.ShotPlan(3, "C", "85mm", "حجب جزئي", "إضاءة ليلية", "كشف بطيء", 3, "")
        )
        val originalFrameBudgets = listOf(2, 5, 3)

        val chapterStartingInsideShotTwo = StoryDirectorEngine.sliceShotPlans(
            originalPlans, originalFrameBudgets, startFrame = 4, frameCount = 6
        )
        assertEquals(listOf(2, 3), chapterStartingInsideShotTwo.map { it.number })
        val secondChapterImages = chapterStartingInsideShotTwo.map { shot ->
            sourceImages[StoryDirectorEngine.sourceShotIndex(shot.number)]
        }
        assertEquals(listOf("image-2", "image-3"), secondChapterImages)

        val chapterStartingAtShotThree = StoryDirectorEngine.sliceShotPlans(
            originalPlans, originalFrameBudgets, startFrame = 7, frameCount = 3
        )
        assertEquals(listOf(3), chapterStartingAtShotThree.map { it.number })
        assertEquals(
            listOf("image-3"),
            chapterStartingAtShotThree.map { shot ->
                sourceImages[StoryDirectorEngine.sourceShotIndex(shot.number)]
            }
        )
    }

    @Test
    fun chapterBoundaryPreservesProgressWithinAnInterruptedShot() {
        val shots = listOf(
            StoryDirectorEngine.ShotPlan(1, "A", "24mm", "", "", "تثبيت", 2, ""),
            StoryDirectorEngine.ShotPlan(2, "B", "50mm", "", "", "اقتراب بطيء", 5, ""),
            StoryDirectorEngine.ShotPlan(3, "C", "85mm", "", "", "كشف بطيء", 3, "")
        )
        val segments = StoryDirectorEngine.sliceShotSegments(
            shots,
            listOf(2, 5, 3),
            startFrame = 4,
            frameCount = 6
        )

        assertEquals(listOf(2, 3), segments.map { it.shotPlan.number })
        assertEquals(2, segments[0].startOffsetFrames)
        assertEquals(3, segments[0].frameCount)
        assertEquals(5, segments[0].totalShotFrames)
        assertEquals(0, segments[1].startOffsetFrames)
        assertEquals(3, segments[1].frameCount)
        assertEquals(3, segments[1].totalShotFrames)

        assertEquals(
            0.5f,
            StoryDirectorEngine.progressForShotFrame(segments[0].startOffsetFrames, 0, segments[0].totalShotFrames),
            0.0001f
        )
        assertEquals(
            1f,
            StoryDirectorEngine.progressForShotFrame(segments[0].startOffsetFrames, 2, segments[0].totalShotFrames),
            0.0001f
        )
    }

    @Test
    fun storyboardMotionCompositionAndLightingReachDeterministicRenderIntents() {
        assertEquals(0.5f, StoryDirectorEngine.motionProgress("تثبيت الكاميرا", 0.9f), 0.0001f)
        assertEquals(0.5f, StoryDirectorEngine.motionProgress("كشف بطيء ومحدود", 0.5f), 0.0001f)
        assertEquals(0.64f, StoryDirectorEngine.compositionFocusX("اترك مساحة بصرية للمصدر الصوتي المذكور") ?: -1f, 0.0001f)
        assertEquals(0.66f, StoryDirectorEngine.compositionFocusX("تكوين اللقطة", "لقطة واسعة مع حجب جزئي للكادر") ?: -1f, 0.0001f)
        assertEquals(0.38f, StoryDirectorEngine.compositionFocusX("حافظ على الاستمرارية", "لقطة متوسطة من خلف الشخصية") ?: -1f, 0.0001f)
        assertEquals(null, StoryDirectorEngine.compositionFocusX("تكوين عام بلا توجيه محدد"))
        assertEquals("COOL", StoryDirectorEngine.lightingTreatment("إضاءة عملية باردة"))
        assertEquals("NIGHT", StoryDirectorEngine.lightingTreatment("إضاءة ليلية محيطية منخفضة"))
        assertEquals("SIDE", StoryDirectorEngine.lightingTreatment("إضاءة جانبية درامية"))
        assertEquals("NEUTRAL", StoryDirectorEngine.lightingTreatment("إضاءة محيطية طبيعية"))
    }

    @Test
    fun directorCreatesThreeShotDescriptionsPerScene() {
        val story = "دخلت الغرفة. ظهر ظل خلف النافذة."
        val plan = StoryDirectorEngine.analyze(story, 20, "9:16", "سينمائي", "رعب نفسي")
        assertTrue(plan.scenes.all { it.shots.size == 3 })
    }
}
