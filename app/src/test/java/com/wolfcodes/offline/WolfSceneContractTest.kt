package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneContractTest {
    private fun request(): WolfSceneRenderRequest = WolfSceneRenderRequest(
        requestId = "request-001",
        contractVersion = WolfSceneContract.VERSION,
        sceneId = "scene-01",
        shotId = "shot-01",
        masterSeed = 42L,
        timeline = WolfSceneTimeline(
            startFrameIndex = 24L,
            frameCount = 2,
            frameRateNumerator = 24L,
            frameRateDenominator = 1L
        ),
        canvas = WolfSceneCanvas(720, 1280),
        coordinateSystem = WolfSceneCoordinateSystem(
            handedness = WolfSceneHandedness.RIGHT_HANDED,
            upAxis = WolfSceneAxis.Y,
            forwardAxis = WolfSceneAxis.Z,
            forwardPositive = false,
            pixelOrigin = WolfScenePixelOrigin.TOP_LEFT
        ),
        camera = WolfSceneCameraCommand(
            position = WolfVector3(0.0, 0.0, 5.0),
            target = WolfVector3.ZERO,
            up = WolfVector3.UNIT_Y,
            verticalFieldOfViewDegrees = 45.0,
            nearClip = 0.1,
            farClip = 100.0
        ),
        layers = listOf(
            WolfSceneLayerCommand("background", 0, WolfSceneBlendMode.NORMAL, 1000),
            WolfSceneLayerCommand("foreground", 1, WolfSceneBlendMode.NORMAL, 850)
        ),
        assets = listOf(
            WolfSceneAssetReference(
                assetId = "environment-corridor",
                version = "1.0.0",
                sha256 = "a".repeat(64),
                usage = "scene-background"
            )
        ),
        resourceEnvelope = WolfSceneResourceEnvelope(
            maxWorkingSetBytes = 64L * 1024L * 1024L,
            maxTemporaryStorageBytes = 128L * 1024L * 1024L,
            maxReusableOutputBytes = 16L * 1024L * 1024L,
            maxTransientElementsPerFrame = 100_000L,
            maxRetainedFrameCount = 4L,
            maxExecutionMillis = 2_000L
        ),
        runtimeTarget = WolfSceneRuntimeTarget(
            adapterId = "wolf.adapter.godot",
            adapterVersion = "1.0.0",
            engineId = "godot",
            engineVersion = "4.7.2"
        )
    )

    private fun successfulResult(
        request: WolfSceneRenderRequest = request()
    ): WolfSceneRenderResult {
        val outputs = request.layers.filter { it.visible }.flatMap { layer ->
            (0 until request.timeline.frameCount).map { offset ->
                val frameIndex = request.timeline.startFrameIndex + offset.toLong()
                WolfSceneFrameOutput(
                    layerId = layer.layerId,
                    frameIndex = frameIndex,
                    presentationTimeUs = request.timeline.presentationTimeUs(frameIndex),
                    widthPx = request.canvas.widthPx,
                    heightPx = request.canvas.heightPx,
                    pixelFormat = WolfScenePixelFormat.RGBA_8888,
                    colorSpace = WolfSceneColorSpace.SRGB,
                    alphaMode = WolfSceneAlphaMode.STRAIGHT,
                    sha256 = "b".repeat(64),
                    outputReference = "memory:" + layer.layerId + ":" + frameIndex
                )
            }
        }
        return WolfSceneRenderResult(
            requestId = request.requestId,
            contractVersion = request.contractVersion,
            status = WolfSceneRenderStatus.SUCCESS,
            outputs = outputs,
            resourceUsage = WolfSceneResourceUsage(
                peakWorkingSetBytes = 32L * 1024L * 1024L,
                peakTemporaryStorageBytes = 8L * 1024L * 1024L,
                reusableOutputBytes = 2L * 1024L * 1024L,
                peakTransientElementsPerFrame = 20_000L,
                peakRetainedFrameCount = 2L,
                elapsedExecutionMillis = 500L
            )
        )
    }

    @Test
    fun validRequestPassesAndDeclaresAbsoluteFrameClock() {
        val request = request()
        assertTrue(WolfSceneContractValidator.validateRequest(request).isValid)
        assertEquals(1_000_000L, request.timeline.presentationTimeUs(24L))
        assertEquals(1_041_667L, request.timeline.presentationTimeUs(25L))
    }

    @Test
    fun requestRejectsUnsupportedVersionAndDuplicateLayerIds() {
        val invalid = request().copy(
            contractVersion = "9.0.0",
            layers = listOf(
                WolfSceneLayerCommand("same", 0, WolfSceneBlendMode.NORMAL, 1000),
                WolfSceneLayerCommand("same", 1, WolfSceneBlendMode.NORMAL, 1000)
            )
        )
        val result = WolfSceneContractValidator.validateRequest(invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("contractVersion") })
        assertTrue(result.errors.any { it.contains("unique") })
    }

    @Test
    fun requestRejectsMissingLayerAssetAndInvalidResourceLimits() {
        val invalid = request().copy(
            layers = listOf(WolfSceneLayerCommand("background", 0, WolfSceneBlendMode.NORMAL, 1000, assetIds = listOf("missing"))),
            resourceEnvelope = request().resourceEnvelope.copy(maxExecutionMillis = 0L)
        )
        val result = WolfSceneContractValidator.validateRequest(invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("asset absent") })
        assertTrue(result.errors.any { it.contains("resource envelope") })
    }

    @Test
    fun requestRejectsInvalidCameraAndTimeline() {
        val invalid = request().copy(
            camera = request().camera!!.copy(
                target = request().camera.position,
                verticalFieldOfViewDegrees = 180.0
            ),
            timeline = request().timeline.copy(frameRateDenominator = 0L)
        )
        val result = WolfSceneContractValidator.validateRequest(invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("camera") })
        assertTrue(result.errors.any { it.contains("timeline") })
    }

    @Test
    fun matchingResultPassesCompleteFrameAndResourceValidation() {
        val request = request()
        val result = successfulResult(request)
        assertTrue(WolfSceneContractValidator.validateResult(request, result).isValid)
    }

    @Test
    fun resultRejectsWrongPtsMissingFramesAndBadDigest() {
        val request = request()
        val valid = successfulResult(request)
        val malformedOutputs = valid.outputs.toMutableList()
        malformedOutputs.removeAt(0)
        malformedOutputs[0] = malformedOutputs[0].copy(
            presentationTimeUs = 123L,
            sha256 = "not-a-digest"
        )
        val malformed = valid.copy(outputs = malformedOutputs)
        val result = WolfSceneContractValidator.validateResult(request, malformed)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("presentationTimeUs") })
        assertTrue(result.errors.any { it.contains("SHA-256") })
        assertTrue(result.errors.any { it.contains("complete") })
    }

    @Test
    fun resultRejectsOutputsThatExceedTheRequestedCanvasOrResourceEnvelope() {
        val request = request()
        val valid = successfulResult(request)
        val oversized = valid.copy(
            outputs = valid.outputs.map { it.copy(widthPx = it.widthPx + 1) },
            resourceUsage = valid.resourceUsage!!.copy(peakWorkingSetBytes = Long.MAX_VALUE)
        )
        val result = WolfSceneContractValidator.validateResult(request, oversized)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("dimensions") })
        assertTrue(result.errors.any { it.contains("resource usage") })
    }

    @Test
    fun failedRendererMustNotReturnPartialFrames() {
        val request = request()
        val invalid = WolfSceneRenderResult(
            requestId = request.requestId,
            contractVersion = request.contractVersion,
            status = WolfSceneRenderStatus.FAILED,
            outputs = successfulResult(request).outputs,
            failureCode = WolfSceneFailureCode.RENDER_FAILED
        )
        val result = WolfSceneContractValidator.validateResult(request, invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("partial outputs") })
    }
}
