package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfCinematicIntroRendererTest {
    @Test
    fun animationRevealsTheWolfBeforeTheBrandAndEndsWithAFullBlackTransition() {
        val opening = WolfCinematicIntroRenderer.motionAt(0.04f)
        val scratches = WolfCinematicIntroRenderer.motionAt(0.30f)
        val wolf = WolfCinematicIntroRenderer.motionAt(0.58f)
        val title = WolfCinematicIntroRenderer.motionAt(0.84f)
        val transition = WolfCinematicIntroRenderer.motionAt(1.0f)

        assertTrue(opening.wolfReveal < scratches.wolfReveal)
        assertTrue(scratches.clawReveal > 0f)
        assertTrue(wolf.wolfReveal > 0.9f)
        assertTrue(title.titleReveal > wolf.titleReveal)
        assertTrue(title.signatureReveal > 0f)
        assertEquals(1f, transition.finalFade, 0.0001f)
    }

    @Test
    fun frameProgressMovesFromOpeningToFinalTransitionDeterministically() {
        assertEquals(0f, WolfCinematicIntroRenderer.progressForFrame(0, 36), 0.0001f)
        assertEquals(18f / 35f, WolfCinematicIntroRenderer.progressForFrame(18, 36), 0.0001f)
        assertEquals(1f, WolfCinematicIntroRenderer.progressForFrame(35, 36), 0.0001f)
        assertEquals(1f, WolfCinematicIntroRenderer.progressForFrame(0, 1), 0.0001f)
    }

    @Test
    fun motionProgressIsClampedAndCameraPushRemainsBounded() {
        val before = WolfCinematicIntroRenderer.motionAt(-2f)
        val after = WolfCinematicIntroRenderer.motionAt(2f)

        assertEquals(
            WolfCinematicIntroRenderer.motionAt(0f).cameraScale,
            before.cameraScale,
            0.0001f
        )
        assertEquals(
            WolfCinematicIntroRenderer.motionAt(1f).cameraScale,
            after.cameraScale,
            0.0001f
        )
        assertTrue(before.cameraScale in 0.94f..1.04f)
        assertTrue(after.cameraScale in 0.94f..1.04f)
    }
}
