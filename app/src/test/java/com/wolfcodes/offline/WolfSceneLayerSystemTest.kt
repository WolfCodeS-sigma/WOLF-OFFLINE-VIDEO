package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneLayerSystemTest {

    private val quietFeatures = WolfProceduralMotionEngine.SceneFeatures(
        hasGhost = false,
        hasForest = false,
        hasRain = false,
        hasGlitch = false,
        hasChase = false,
        hasReveal = false
    )

    @Test
    fun roomPlanContainsIndependentlyMovableChairAndLight() {
        val layers = WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.ROOM,
            quietFeatures,
            4
        )

        assertTrue(layers.any { it.id == "movable_chair" && it.kind == WolfSceneLayerSystem.Kind.CHAIR })
        assertTrue(layers.any { it.id == "ceiling_light" && it.kind == WolfSceneLayerSystem.Kind.HANGING_LIGHT })
        assertEquals(layers.sortedWith(compareBy<WolfSceneLayerSystem.Element> { it.depth.ordinal }.thenBy { it.zIndex }), layers)
    }

    @Test
    fun windowAndForestUseDifferentDepthDistributedElements() {
        val window = WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.WINDOW,
            quietFeatures,
            1
        )
        val forest = WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.FOREST,
            quietFeatures,
            1
        )

        assertTrue(window.any { it.kind == WolfSceneLayerSystem.Kind.CURTAIN && it.depth == WolfSceneLayerSystem.Depth.FOREGROUND })
        assertTrue(forest.any { it.kind == WolfSceneLayerSystem.Kind.BRANCH && it.depth == WolfSceneLayerSystem.Depth.FOREGROUND })
        assertNotEquals(window.map { it.kind }, forest.map { it.kind })
    }

    @Test
    fun storyPresenceAddsAnIndependentForegroundShadow() {
        val features = quietFeatures.copy(hasGhost = true)
        val layers = WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.CORRIDOR,
            features,
            2
        )

        assertTrue(layers.any { it.id == "moving_shadow" && it.depth == WolfSceneLayerSystem.Depth.FOREGROUND })
    }

    @Test
    fun objectMotionIsDeterministicAndProgressBounded() {
        val element = WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.ROOM,
            quietFeatures,
            3
        ).first { it.kind == WolfSceneLayerSystem.Kind.CHAIR }

        val first = WolfSceneLayerSystem.pose(element, 0.35f)
        val second = WolfSceneLayerSystem.pose(element, 0.35f)
        val end = WolfSceneLayerSystem.pose(element, 1.4f)

        assertEquals(first, second)
        assertEquals(WolfSceneLayerSystem.pose(element, 1f), end)
        assertTrue(first.rotationDegrees in -2f..2f)
        assertTrue(first.alpha in 0..255)
    }

    @Test
    fun distinctObjectsReceiveIndependentMotionPhases() {
        val layers = WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.WINDOW,
            quietFeatures,
            7
        )
        val left = layers.first { it.id == "left_curtain" }
        val right = layers.first { it.id == "right_curtain" }

        assertNotEquals(
            WolfSceneLayerSystem.pose(left, 0.42f),
            WolfSceneLayerSystem.pose(right, 0.42f)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun plannerRejectsNegativeSceneIndex() {
        WolfSceneLayerSystem.plan(
            WolfProceduralSceneProductionEngine.Environment.ROOM,
            quietFeatures,
            -1
        )
    }
}
