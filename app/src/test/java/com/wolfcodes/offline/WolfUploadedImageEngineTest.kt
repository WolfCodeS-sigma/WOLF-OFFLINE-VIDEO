package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfUploadedImageEngineTest {
    @Test
    fun unverifiedImageFitKeepsTheCompleteSourceAspectInsideTheOutput() {
        val landscape = WolfUploadedImageEngine.aspectFitDestination(160, 90, 320, 568)
        assertEquals(0f, landscape.left, 0.001f)
        assertEquals(320f, landscape.right, 0.001f)
        assertEquals(194f, landscape.top, 0.01f)
        assertEquals(374f, landscape.bottom, 0.01f)
        assertEquals(160f / 90f, landscape.width / landscape.height, 0.001f)

        val portrait = WolfUploadedImageEngine.aspectFitDestination(90, 160, 320, 568)
        assertTrue(portrait.left >= 0f && portrait.top >= 0f)
        assertTrue(portrait.right <= 320f && portrait.bottom <= 568f)
        assertEquals(90f / 160f, portrait.width / portrait.height, 0.001f)
    }

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
        val chase = WolfUploadedImageEngine.planMotion("طاردني شبح عبر الممر المهجور", 2, 0, 0.4f)
        val ghost = WolfUploadedImageEngine.planMotion("ظهر شبح في آخر الممر", 2, 0, 0.4f)
        val rain = WolfUploadedImageEngine.planMotion("المطر يضرب نافذة القبو بينما يسمع الحارس صراخًا خلفه", 2, 0, 0.4f)
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
        val first = WolfUploadedImageEngine.planMotion("باب المشرحة القديم يصدر صريرًا بينما يظهر ظل خلفه", 4, 1, 0.35f)
        val repeated = WolfUploadedImageEngine.planMotion("باب المشرحة القديم يصدر صريرًا بينما يظهر ظل خلفه", 4, 1, 0.35f)
        val otherShot = WolfUploadedImageEngine.planMotion("باب المشرحة القديم يصدر صريرًا بينما يظهر ظل خلفه", 4, 2, 0.85f)
        assertEquals(first, repeated)
        assertTrue(first.intensity in 0f..1f)
        assertTrue(first.focusX in 0f..1f)
        assertTrue(first.focusY in 0f..1f)
        assertEquals(WolfUploadedImageEngine.Atmosphere.DOOR, otherShot.atmosphere)
    }

    @Test
    fun everyVerifiedHorrorAtmosphereUsesPeakSafeIntensity() {
        val horrorScenes = listOf(
            "طارد شبح الحارس في ممر المستشفى المهجور",
            "ظهر شبح خلف الباب",
            "المطر يضرب نافذة القبو",
            "تحرك ظل مخيف بين أشجار المقبرة",
            "انطفأ الضوء قرب المرآة القديمة",
            "ظل الحارس يسمع همسًا داخل الغرفة"
        )
        horrorScenes.forEachIndexed { index, story ->
            val plan = WolfUploadedImageEngine.planMotion(story, index, 1, 0.5f)
            assertTrue("Horror intensity must remain at maximum: $story",
                plan.intensity >= 0.999f)
        }
    }
}