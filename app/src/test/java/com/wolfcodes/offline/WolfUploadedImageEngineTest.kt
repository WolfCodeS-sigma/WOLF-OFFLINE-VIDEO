package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfUploadedImageEngineTest {
    @Test
    fun acceptsUsablePartialDepthSeparationAtConservativeConfidence() {
        assertTrue(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.12f))
        assertTrue(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.21f))
        assertTrue(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.82f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(false, 0.99f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.119f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(false, 0f))
    }

    @Test
    fun storyEventsSelectDifferentCameraAndAtmospherePlans() {
        val chase = WolfUploadedImageEngine.planMotion("بدأت المطاردة وركض خلفي", 2, 0, 0.4f)
        val ghost = WolfUploadedImageEngine.planMotion("ظهر شبح في آخر الممر", 2, 0, 0.4f)
        val rain = WolfUploadedImageEngine.planMotion("المطر يضرب النافذة", 2, 0, 0.4f)
        assertEquals(WolfUploadedImageEngine.Atmosphere.CHASE, chase.atmosphere)
        assertTrue(chase.cameraMove == WolfUploadedImageEngine.CameraMove.LEFT_TRACK ||
            chase.cameraMove == WolfUploadedImageEngine.CameraMove.RIGHT_TRACK)
        assertEquals(WolfUploadedImageEngine.Atmosphere.GHOST, ghost.atmosphere)
        assertEquals(WolfUploadedImageEngine.CameraMove.REVEAL, ghost.cameraMove)
        assertEquals(WolfUploadedImageEngine.Atmosphere.RAIN, rain.atmosphere)
    }

    @Test
    fun shotMotionPlanIsDeterministicAndBounded() {
        val first = WolfUploadedImageEngine.planMotion("باب قديم يصدر صريرا", 4, 1, 0.35f)
        val repeated = WolfUploadedImageEngine.planMotion("باب قديم يصدر صريرا", 4, 1, 0.35f)
        val otherShot = WolfUploadedImageEngine.planMotion("باب قديم يصدر صريرا", 4, 2, 0.85f)
        assertEquals(first, repeated)
        assertTrue(first.intensity in 0f..1f)
        assertTrue(first.focusX in 0f..1f)
        assertTrue(first.focusY in 0f..1f)
        assertEquals(WolfUploadedImageEngine.Atmosphere.DOOR, otherShot.atmosphere)
    }
}