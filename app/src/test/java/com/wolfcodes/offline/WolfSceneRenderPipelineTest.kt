package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneRenderPipelineTest {

    @Test
    fun missingUploadedImageSelectsProceduralBackendAndBuildsACompletePlan() {
        val fallbackReasons = mutableListOf<String>()
        val shot = WolfSceneRenderPipeline.prepare(
            caption = "ظهر شبح خلف الباب في الممر",
            sceneIndex = 3,
            source = null
        ) { reason ->
            fallbackReasons += reason
        }

        try {
            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, shot.backend)
            assertNotNull(shot.proceduralPlan)
            assertEquals("ظهر شبح خلف الباب في الممر", shot.proceduralPlan?.caption)
            assertEquals(3, shot.proceduralPlan?.sceneIndex)
            assertTrue(fallbackReasons.contains("NoUploadedImage"))
        } finally {
            shot.recycle()
        }
    }

    @Test
    fun missingImageStillSelectsProceduralBackendWhenDiagnosticsCallbackFails() {
        val shot = WolfSceneRenderPipeline.prepare(
            caption = "تحرك الظل في الغرفة",
            sceneIndex = 4,
            source = null
        ) {
            throw IllegalStateException("diagnostics unavailable")
        }

        try {
            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, shot.backend)
            assertNotNull(shot.proceduralPlan)
            assertEquals("تحرك الظل في الغرفة", shot.proceduralPlan?.caption)
            assertEquals(4, shot.proceduralPlan?.sceneIndex)
        } finally {
            shot.recycle()
        }
    }

    @Test
    fun failedOrInsufficientImageAnalysisSelectsProceduralBackend() {
        assertEquals(
            false,
            WolfSceneRenderPipeline.shouldUseUploadedImageRoute(
                sourceAvailable = false,
                hasUsableDepthLayers = false,
                analysisFailed = false
            )
        )
        assertEquals(
            false,
            WolfSceneRenderPipeline.shouldUseUploadedImageRoute(
                sourceAvailable = true,
                hasUsableDepthLayers = false,
                analysisFailed = true
            )
        )
        assertEquals(
            false,
            WolfSceneRenderPipeline.shouldUseUploadedImageRoute(
                sourceAvailable = true,
                hasUsableDepthLayers = false,
                analysisFailed = false
            )
        )
    }

    @Test
    fun usablePartialDepthLayersKeepTheUploadedImageBackend() {
        assertEquals(
            true,
            WolfSceneRenderPipeline.shouldUseUploadedImageRoute(
                sourceAvailable = true,
                hasUsableDepthLayers = true,
                analysisFailed = false
            )
        )
    }
}
