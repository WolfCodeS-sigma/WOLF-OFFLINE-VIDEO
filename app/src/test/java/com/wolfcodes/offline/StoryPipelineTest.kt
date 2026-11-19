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
    fun directorCreatesThreeShotDescriptionsPerScene() {
        val story = "دخلت الغرفة. ظهر ظل خلف النافذة."
        val plan = StoryDirectorEngine.analyze(story, 20, "9:16", "سينمائي", "رعب نفسي")
        assertTrue(plan.scenes.all { it.shots.size == 3 })
    }
}
