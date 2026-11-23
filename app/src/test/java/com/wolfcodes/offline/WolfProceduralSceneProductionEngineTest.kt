package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfProceduralSceneProductionEngineTest {

    @Test
    fun environmentPlannerRecognizesArabicAndEnglishSceneCues() {
        assertEquals(WolfProceduralSceneProductionEngine.Environment.FOREST,
            WolfProceduralSceneProductionEngine.classifyEnvironment("كانت الأشجار تتحرك في الغابة"))
        assertEquals(WolfProceduralSceneProductionEngine.Environment.CORRIDOR,
            WolfProceduralSceneProductionEngine.classifyEnvironment("A shadow moved down the corridor"))
        assertEquals(WolfProceduralSceneProductionEngine.Environment.WINDOW,
            WolfProceduralSceneProductionEngine.classifyEnvironment("ظهر وجه خلف النافذة"))
        assertEquals(WolfProceduralSceneProductionEngine.Environment.DOOR,
            WolfProceduralSceneProductionEngine.classifyEnvironment("الباب يفتح وحده"))
        assertEquals(WolfProceduralSceneProductionEngine.Environment.ROOM,
            WolfProceduralSceneProductionEngine.classifyEnvironment("The bedroom was empty"))
        assertEquals(WolfProceduralSceneProductionEngine.Environment.CEMETERY,
            WolfProceduralSceneProductionEngine.classifyEnvironment("The graveyard was silent"))
        assertEquals(WolfProceduralSceneProductionEngine.Environment.HAUNTED_HOUSE,
            WolfProceduralSceneProductionEngine.classifyEnvironment("سمعت خطوات غامضة في الخارج"))
    }

    @Test
    fun plannerIsDeterministicAndDoesNotCreateSubjectGeometry() {
        val caption = "ظهر شبح خلف الباب فجأة"
        val first = WolfProceduralSceneProductionEngine.plan(caption, 4)
        val second = WolfProceduralSceneProductionEngine.plan(caption, 4)
        assertEquals(first, second)
        assertEquals(WolfProceduralSceneProductionEngine.Environment.DOOR, first.environment)
        assertTrue(first.visualCaption.contains("باب"))
        assertEquals(caption, first.caption)
        assertTrue(first.features.hasReveal)
    }

    @Test
    fun threeShotsHaveDistinctBoundedCompositions() {
        val shots = (0..2).map(WolfProceduralSceneProductionEngine::shotComposition)
        assertEquals(3, shots.distinct().size)
        assertTrue(shots.all { it.scale in 1.0f..1.24f })
        assertTrue(shots.all { it.offsetX in -0.03f..0.03f })
        assertTrue(shots.all { it.offsetY in -0.02f..0.02f })
        assertTrue(shots.all { it.rotationDegrees == 0f })
        assertTrue(shots.all { it.focusX in 0.40f..0.60f })
        assertTrue(shots.all { it.focusY in 0.40f..0.60f })
        assertNotEquals(shots[0].focusX, shots[1].focusX)
        assertNotEquals(shots[1].focusX, shots[2].focusX)
    }

    @Test
    fun invalidShotIndicesUseTheNearestSupportedComposition() {
        assertEquals(WolfProceduralSceneProductionEngine.shotComposition(0),
            WolfProceduralSceneProductionEngine.shotComposition(-1))
        assertEquals(WolfProceduralSceneProductionEngine.shotComposition(2),
            WolfProceduralSceneProductionEngine.shotComposition(99))
        assertNotEquals(WolfProceduralSceneProductionEngine.shotComposition(0),
            WolfProceduralSceneProductionEngine.shotComposition(2))
    }

    @Test
    fun plannerPreservesStoryContextWithoutPretendingToRenderItsSubjects() {
        val caption = "داخل غرفة قديمة وقفت امرأة خلف النافذة ثم ظهر شبح"
        val plan = WolfProceduralSceneProductionEngine.plan(caption, 2)
        assertEquals(caption, plan.caption)
        assertEquals(WolfProceduralSceneProductionEngine.Environment.WINDOW, plan.environment)
        assertTrue(plan.features.hasReveal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun plannerRejectsBlankSceneText() {
        WolfProceduralSceneProductionEngine.plan("   ", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun plannerRejectsNegativeSceneIndex() {
        WolfProceduralSceneProductionEngine.plan("باب يفتح ببطء", -1)
    }
}
