package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfProceduralSceneProductionEngineTest {

    @Test
    fun environmentPlannerRecognizesArabicAndEnglishSceneCues() {
        assertEquals(
            WolfProceduralSceneProductionEngine.Environment.FOREST,
            WolfProceduralSceneProductionEngine.classifyEnvironment("كانت الأشجار تتحرك في الغابة")
        )
        assertEquals(
            WolfProceduralSceneProductionEngine.Environment.CORRIDOR,
            WolfProceduralSceneProductionEngine.classifyEnvironment("A shadow moved down the corridor")
        )
        assertEquals(
            WolfProceduralSceneProductionEngine.Environment.WINDOW,
            WolfProceduralSceneProductionEngine.classifyEnvironment("ظهر وجه خلف النافذة")
        )
        assertEquals(
            WolfProceduralSceneProductionEngine.Environment.CEMETERY,
            WolfProceduralSceneProductionEngine.classifyEnvironment("The graveyard was silent")
        )
    }

    @Test
    fun plannerAlwaysProducesACompleteDeterministicPlanWithoutImageAssets() {
        val caption = "ظهر شبح خلف الباب فجأة"
        val first = WolfProceduralSceneProductionEngine.plan(caption, 4)
        val second = WolfProceduralSceneProductionEngine.plan(caption, 4)

        assertEquals(first, second)
        assertEquals(WolfProceduralSceneProductionEngine.Environment.DOOR, first.environment)
        assertTrue(first.visualCaption.contains("باب"))
        assertTrue(first.features.hasGhost)
        assertTrue(first.features.hasReveal)
    }

    @Test
    fun threeShotsHaveDistinctBoundedCompositions() {
        val shots = (0..2).map(WolfProceduralSceneProductionEngine::shotComposition)

        assertEquals(3, shots.distinct().size)
        assertTrue(shots.all { it.scale in 1.0f..1.15f })
        assertTrue(shots.all { it.offsetX in -0.03f..0.03f })
        assertTrue(shots.all { it.offsetY in -0.02f..0.02f })
        assertTrue(shots.all { it.rotationDegrees in -0.2f..0.2f })
    }

    @Test
    fun invalidShotIndicesUseTheNearestSupportedComposition() {
        assertEquals(
            WolfProceduralSceneProductionEngine.shotComposition(0),
            WolfProceduralSceneProductionEngine.shotComposition(-1)
        )
        assertEquals(
            WolfProceduralSceneProductionEngine.shotComposition(2),
            WolfProceduralSceneProductionEngine.shotComposition(99)
        )
        assertNotEquals(
            WolfProceduralSceneProductionEngine.shotComposition(0),
            WolfProceduralSceneProductionEngine.shotComposition(2)
        )
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
