package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneRenderPipelineTest {
    @Test
    fun failedImageRenderNeverCallsProceduralFallback() {
        val shot = WolfSceneRenderPipeline.PreparedShot(
            backend = WolfSceneRenderPipeline.Backend.UPLOADED_IMAGE,
            image = null,
            proceduralPlan = null,
            caption = "باب قديم",
            sceneIndex = 0
        )
        var proceduralCalls = 0
        val error = runCatching {
            WolfSceneRenderPipeline.renderPreparedShot(
                preparedShot = shot,
                renderProcedural = { proceduralCalls++ },
                renderUploadedImage = { WolfUploadedImageEngine.Result.SAFE_FRAME }
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(0, proceduralCalls)
        assertEquals(WolfSceneRenderPipeline.Backend.UPLOADED_IMAGE, shot.backend)
    }



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
    fun failedUserImageFrameStopsExportWithoutSwitchingToProcedural() {
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
            val failure = runCatching {
                WolfSceneRenderPipeline.renderPreparedShot(
                    preparedShot = shot,
                    renderProcedural = { proceduralRenderCalls++ },
                    renderUploadedImage = {
                        imageRenderCalls++
                        WolfUploadedImageEngine.Result.SAFE_FRAME
                    },
                    onFallback = { fallbackReasons += it }
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals(WolfSceneRenderPipeline.Backend.UPLOADED_IMAGE, shot.backend)
            assertEquals(null, shot.proceduralPlan)
            assertEquals(0, proceduralRenderCalls)
            assertEquals(1, imageRenderCalls)
            assertTrue(fallbackReasons.contains("ImageRenderSafeFrame"))
        } finally {
            shot.recycle()
        }
    }

    @Test
    fun aDecodedUserImageRemainsSelectedWhenDepthAnalysisFailsOrProducesNoLayers() {
        assertEquals(
            false,
            WolfSceneRenderPipeline.shouldKeepUploadedImageSource(sourceAvailable = false)
        )
        assertEquals(
            true,
            WolfSceneRenderPipeline.shouldKeepUploadedImageSource(sourceAvailable = true)
        )
    }
}
