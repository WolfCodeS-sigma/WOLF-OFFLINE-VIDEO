package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfScenePlannerTest {
    @Test
    fun frameAllocationPreservesExactFrameBudget() {
        val scenes = listOf("المشهد الأول", "المشهد الثاني أطول قليلًا", "المشهد الثالث")
        val allocation = WolfScenePlanner.allocateFrames(scenes, 240)
        assertEquals(240, allocation.sum())
        assertEquals(3, allocation.size)
        assertTrue(allocation.all { it > 0 })
    }

    @Test
    fun storySplittingKeepsAllTextRepresented() {
        val story = (1..20).joinToString("\n\n") { "فقرة رعب رقم " + it + " حدث غامض في المنزل المهجور." }
        val scenes = WolfScenePlanner.split(story)
        assertTrue(scenes.isNotEmpty())
        assertTrue(scenes.size <= 12)
        assertTrue(scenes.joinToString(" ").contains("فقرة رعب رقم 20"))
    }
}
