package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class WolfCinematicIdentityTest {
    @Test
    fun introAndWatermarkShareTheApprovedBrandName() {
        assertEquals("WOLF CODE S", WolfCinematicIdentity.BRAND_NAME)
        assertEquals("شفرة الذئب S", WolfCinematicIdentity.ARABIC_BRAND_NAME)
        assertEquals("بصمة الذئب السينمائية", WolfCinematicIdentity.INTRO_SIGNATURE)
    }
}
