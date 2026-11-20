package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneRenderPipelineTest {

    @Test
    fun missingUploadedImageSelectsAndRendersThroughProceduralBackend() {
        val fallbackReasons = mutableListOf<String>()
        val shot = WolfSceneRenderPipeline.prepare(
            caption = "ظهر شبح خلف الباب في الممر",
            sceneIndex = 3,
            source = null
        ) { reason ->
            fallbackReasons += reason
        }
        var proceduralRenderCalls = 0
        var imageRenderCalls = 0

        try {
            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, shot.backend)
            assertNotNull(shot.proceduralPlan)
            assertEquals("ظهر شبح خلف الباب في الممر", shot.proceduralPlan?.caption)
            assertEquals(3, shot.proceduralPlan?.sceneIndex)
            assertTrue(fallbackReasons.contains("NoUploadedImage"))

            val selectedBackend = WolfSceneRenderPipeline.renderPreparedShot(
                preparedShot = shot,
                renderProcedural = { proceduralRenderCalls++ },
                renderUploadedImage = {
                    imageRenderCalls++
                    WolfUploadedImageEngine.Result.DEPTH_LAYERS
                }
            )

            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, selectedBackend)
            assertEquals(1, proceduralRenderCalls)
            assertEquals(0, imageRenderCalls)
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
    fun safeImageFrameSwitchesToProceduralAndStaysThereForTheRestOfTheShot() {
        val shot = WolfSceneRenderPipeline.PreparedShot(
            backend = WolfSceneRenderPipeline.Backend.UPLOADED_IMAGE,
            image = null,
            proceduralPlan = null,
            caption = "انفتح الباب وظهر ظل",
            sceneIndex = 5
        )
        val fallbackReasons = mutableListOf<String>()
        var proceduralRenderCalls = 0
        var imageRenderCalls = 0

        try {
            val firstBackend = WolfSceneRenderPipeline.renderPreparedShot(
                preparedShot = shot,
                renderProcedural = { proceduralRenderCalls++ },
                renderUploadedImage = {
                    imageRenderCalls++
                    WolfUploadedImageEngine.Result.SAFE_FRAME
                },
                onFallback = { fallbackReasons += it }
            )

            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, firstBackend)
            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, shot.backend)
            assertNotNull(shot.proceduralPlan)
            assertEquals(1, proceduralRenderCalls)
            assertEquals(1, imageRenderCalls)
            assertTrue(fallbackReasons.contains("ImageRenderSafeFrame"))

            val nextFrameBackend = WolfSceneRenderPipeline.renderPreparedShot(
                preparedShot = shot,
                renderProcedural = { proceduralRenderCalls++ },
                renderUploadedImage = {
                    imageRenderCalls++
                    WolfUploadedImageEngine.Result.DEPTH_LAYERS
                }
            )

            assertEquals(WolfSceneRenderPipeline.Backend.PROCEDURAL, nextFrameBackend)
            assertEquals(2, proceduralRenderCalls)
            assertEquals(1, imageRenderCalls)
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
