package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfUploadedImageEngineTest {
    @Test
    fun depthLayersRequireSemanticValidationInAdditionToConfidence() {
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.12f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.82f))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.99f, false))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(false, 0.99f, true))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.119f, true))
        assertFalse(WolfUploadedImageEngine.shouldUseDepthLayers(false, 0f, false))
        assertTrue(WolfUploadedImageEngine.shouldUseDepthLayers(true, 0.12f, true))
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
    fun consecutiveShotsUseDifferentCameraChoreographyForSameStoryBeat() {
        val first = WolfUploadedImageEngine.planMotion("ظهر شبح في الممر", 2, 0, 0.2f)
        val second = WolfUploadedImageEngine.planMotion("ظهر شبح في الممر", 2, 1, 0.5f)
        val third = WolfUploadedImageEngine.planMotion("ظهر شبح في الممر", 2, 2, 0.8f)

        assertEquals(WolfUploadedImageEngine.Atmosphere.GHOST, first.atmosphere)
        assertEquals(first.atmosphere, second.atmosphere)
        assertEquals(first.atmosphere, third.atmosphere)
        assertTrue(first.cameraMove != second.cameraMove)
        assertTrue(second.cameraMove != third.cameraMove)
        assertTrue(first.cameraMove != third.cameraMove)
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

    @Test
    fun unverifiedImageFitKeepsTheCompleteSourceAspectInsideTheOutput() {
        val landscape = WolfUploadedImageEngine.aspectFitDestination(
            sourceWidth = 1600,
            sourceHeight = 900,
            outputWidth = 320,
            outputHeight = 568
        )
        assertEquals(320f, landscape.width, 0.001f)
        assertEquals(180f, landscape.height, 0.001f)
        assertEquals(194f, landscape.top, 0.001f)
        assertEquals(0f, landscape.left, 0.001f)
        assertTrue(landscape.top >= 0f && landscape.bottom <= 568f)

        val portrait = WolfUploadedImageEngine.aspectFitDestination(
            sourceWidth = 900,
            sourceHeight = 1600,
            outputWidth = 568,
            outputHeight = 320
        )
        assertEquals(180f, portrait.width, 0.001f)
        assertEquals(320f, portrait.height, 0.001f)
        assertEquals(194f, portrait.left, 0.001f)
        assertEquals(0f, portrait.top, 0.001f)
        assertTrue(portrait.left >= 0f && portrait.right <= 568f)
    }
}