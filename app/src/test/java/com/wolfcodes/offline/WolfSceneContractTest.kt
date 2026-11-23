package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfSceneContractTest {
    private fun request(
        layers: List<WolfSceneLayerCommand> = listOf(
            WolfSceneLayerCommand("background", 0, WolfSceneBlendMode.NORMAL, 1000,
                assetIds = listOf("environment-corridor")),
            WolfSceneLayerCommand("foreground", 1, WolfSceneBlendMode.NORMAL, 850)
        )
    ): WolfSceneRenderRequest = WolfSceneRenderRequest(
        requestId = "request-001",
        contractVersion = WolfSceneContract.VERSION,
        sceneId = "scene-01",
        shotId = "shot-01",
        masterSeed = 42L,
        timeline = WolfSceneTimeline(24L, 2, 24L, 1L),
        canvas = WolfSceneCanvas(720, 1280),
        coordinateSystem = WolfSceneCoordinateSystem(
            WolfSceneHandedness.RIGHT_HANDED,
            WolfSceneAxis.Y,
            WolfSceneAxis.Z,
            false,
            WolfScenePixelOrigin.TOP_LEFT
        ),
        camera = WolfSceneCameraCommand(
            WolfVector3(0.0, 0.0, 5.0),
            WolfVector3.ZERO,
            WolfVector3.UNIT_Y,
            45.0,
            0.1,
            100.0
        ),
        layers = layers,
        assets = listOf(
            WolfSceneAssetReference("environment-corridor", "1.0.0", "a".repeat(64), "scene-background")
        ),
        resourceEnvelope = WolfSceneResourceEnvelope(
            64L * 1024L * 1024L,
            128L * 1024L * 1024L,
            16L * 1024L * 1024L,
            100_000L,
            4L,
            2_000L
        ),
        runtimeTarget = WolfSceneRuntimeTarget(
            "wolf.adapter.godot", "1.0.0", "godot", "4.7.2"
        )
    )

    private fun artifactVerifier(
        metadataByReference: Map<String, WolfSceneOutputArtifactMetadata>
    ) = WolfSceneOutputArtifactVerifier { reference -> metadataByReference[reference] }

    private fun successfulResult(request: WolfSceneRenderRequest): WolfSceneRenderResult {
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
                    imageEncoding = WolfSceneImageEncoding.PNG,
                    colorSpace = WolfSceneColorSpace.SRGB,
                    alphaMode = WolfSceneAlphaMode.STRAIGHT,
                    byteCount = 100L,
                    sha256 = "b".repeat(64),
                    outputReference = "artifact:" + layer.layerId + ":" + frameIndex
                )
            }
        }
        val metadata = outputs.associate { output ->
            output.outputReference to WolfSceneOutputArtifactMetadata(
                output.byteCount, output.sha256, output.widthPx, output.heightPx,
                output.pixelFormat, output.imageEncoding, output.colorSpace, output.alphaMode
            )
        }
        return WolfSceneRenderResult(
            requestId = request.requestId,
            contractVersion = request.contractVersion,
            status = WolfSceneRenderStatus.SUCCESS,
            outputs = outputs,
            resourceUsage = WolfSceneResourceUsage(32L * 1024L * 1024L, 8L * 1024L * 1024L,
                0L, 20_000L, 2L, 500L),
            runtimeTarget = request.runtimeTarget
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
        val invalid = WolfSceneRenderRequest(
            "request-001", "9.0.0", "scene-01", "shot-01", 42L,
            request().timeline, request().canvas, request().coordinateSystem, request().camera,
            listOf(
                WolfSceneLayerCommand("same", 0, WolfSceneBlendMode.NORMAL, 1000),
                WolfSceneLayerCommand("same", 1, WolfSceneBlendMode.NORMAL, 1000)
            ),
            emptyList(), request().resourceEnvelope, request().runtimeTarget
        )
        val result = WolfSceneContractValidator.validateRequest(invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("contractVersion") })
        assertTrue(result.errors.any { it.contains("unique") })
    }

    @Test
    fun requestRejectsMissingLayerAssetAndInvalidResourceLimits() {
        val base = request()
        val invalid = WolfSceneRenderRequest(
            base.requestId, base.contractVersion, base.sceneId, base.shotId, base.masterSeed,
            base.timeline, base.canvas, base.coordinateSystem, base.camera,
            listOf(WolfSceneLayerCommand("background", 0, WolfSceneBlendMode.NORMAL, 1000,
                assetIds = listOf("missing"))),
            base.assets,
            base.resourceEnvelope.copy(maxExecutionMillis = 0L),
            base.runtimeTarget
        )
        val result = WolfSceneContractValidator.validateRequest(invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("asset absent") })
        assertTrue(result.errors.any { it.contains("resource envelope") })
    }

    @Test
    fun requestRejectsInvalidCameraAndTimelinePtsOverflow() {
        val base = request()
        val invalid = WolfSceneRenderRequest(
            base.requestId, base.contractVersion, base.sceneId, base.shotId, base.masterSeed,
            base.timeline.copy(startFrameIndex = Long.MAX_VALUE - 1L, frameCount = 2),
            base.canvas, base.coordinateSystem,
            base.camera!!.copy(target = base.camera.position, verticalFieldOfViewDegrees = 180.0),
            base.layers, base.assets, base.resourceEnvelope, base.runtimeTarget
        )
        val result = WolfSceneContractValidator.validateRequest(invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("camera") })
        assertTrue(result.errors.any { it.contains("timeline") })
    }

    @Test
    fun layerAndCameraMotionTracksArePartOfTheRequestContract() {
        val scalar = WolfScalarCurve(listOf(WolfScalarKeyframe(0.0, 0.0), WolfScalarKeyframe(1.0, 1.0)))
        val vector = WolfVector3Curve(listOf(
            WolfVector3Keyframe(0.0, WolfVector3.ZERO),
            WolfVector3Keyframe(1.0, WolfVector3(1.0, 2.0, 3.0))
        ))
        val rotation = WolfQuaternionCurve(listOf(
            WolfQuaternionKeyframe(0.0, WolfQuaternion.IDENTITY),
            WolfQuaternionKeyframe(1.0, WolfQuaternion.IDENTITY)
        ))
        val unitScale = WolfVector3Curve(listOf(
            WolfVector3Keyframe(0.0, WolfVector3(1.0, 1.0, 1.0)),
            WolfVector3Keyframe(1.0, WolfVector3(1.2, 1.2, 1.2))
        ))
        val motion = WolfSceneMotionTrack("background", vector, rotation, unitScale, scalar)
        val cameraMotion = WolfSceneCameraMotionTrack(
            position = WolfVector3Curve(listOf(
                WolfVector3Keyframe(0.0, WolfVector3(0.0, 0.0, 5.0)),
                WolfVector3Keyframe(1.0, WolfVector3(0.0, 0.0, 4.0))
            )),
            target = WolfVector3Curve(listOf(
                WolfVector3Keyframe(0.0, WolfVector3.ZERO),
                WolfVector3Keyframe(1.0, WolfVector3.ZERO)
            )),
            up = WolfVector3Curve(listOf(
                WolfVector3Keyframe(0.0, WolfVector3.UNIT_Y),
                WolfVector3Keyframe(1.0, WolfVector3.UNIT_Y)
            )),
            verticalFieldOfViewDegrees = WolfScalarCurve(listOf(
                WolfScalarKeyframe(0.0, 45.0), WolfScalarKeyframe(1.0, 50.0)
            )),
            nearClip = 0.1,
            farClip = 100.0
        )
        val base = request()
        val movingLayers = listOf(
            WolfSceneLayerCommand("background", 0, WolfSceneBlendMode.NORMAL, 1000,
                assetIds = listOf("environment-corridor"), motion = motion),
            base.layers[1]
        )
        val moving = WolfSceneRenderRequest(
            base.requestId, base.contractVersion, base.sceneId, base.shotId, base.masterSeed,
            base.timeline, base.canvas, base.coordinateSystem, base.camera,
            movingLayers, base.assets, base.resourceEnvelope, base.runtimeTarget, cameraMotion
        )
        assertTrue(WolfSceneContractValidator.validateRequest(moving).isValid)
    }

    @Test
    fun requestRejectsUnsupportedCanvasAndTooManyLayers() {
        val base = request()
        val invalidCanvas = WolfSceneRenderRequest(
            base.requestId, base.contractVersion, base.sceneId, base.shotId, base.masterSeed,
            base.timeline, WolfSceneCanvas(8192, 8192), base.coordinateSystem, base.camera,
            base.layers, base.assets, base.resourceEnvelope, base.runtimeTarget
        )
        assertFalse(WolfSceneContractValidator.validateRequest(invalidCanvas).isValid)

        val manyLayers = (0..WolfSceneContractLimits.MAX_LAYER_COUNT).map {
            WolfSceneLayerCommand("layer-" + it, it, WolfSceneBlendMode.NORMAL, 1000)
        }
        val invalidLayers = WolfSceneRenderRequest(
            base.requestId, base.contractVersion, base.sceneId, base.shotId, base.masterSeed,
            base.timeline, base.canvas, base.coordinateSystem, base.camera,
            manyLayers, emptyList(), base.resourceEnvelope, base.runtimeTarget
        )
        assertFalse(WolfSceneContractValidator.validateRequest(invalidLayers).isValid)
    }

    @Test
    fun requestCollectionsAreImmutableAfterDefensiveCopy() {
        val base = request()
        try {
            (base.layers as MutableList).clear()
            throw AssertionError("Expected immutable layer collection")
        } catch (_: UnsupportedOperationException) {
            assertEquals(2, base.layers.size)
        }
        try {
            (base.layers.first().assetIds as MutableList).clear()
            throw AssertionError("Expected immutable asset collection")
        } catch (_: UnsupportedOperationException) {
            assertEquals(listOf("environment-corridor"), base.layers.first().assetIds)
        }
    }

    @Test
    fun matchingResultPassesOnlyAfterArtifactBytesAreIndependentlyVerified() {
        val request = request()
        val result = successfulResult(request)
        val verifier = artifactVerifier(result.outputs.associate { output ->
            output.outputReference to WolfSceneOutputArtifactMetadata(
                output.byteCount, output.sha256, output.widthPx, output.heightPx,
                output.pixelFormat, output.imageEncoding, output.colorSpace, output.alphaMode
            )
        })
        assertTrue(WolfSceneContractValidator.validateResult(request, result, verifier).isValid)
    }

    @Test
    fun resultRejectsWrongPtsMissingFramesAndBadDigest() {
        val request = request()
        val valid = successfulResult(request)
        val malformed = valid.outputs.toMutableList().also { outputs ->
            outputs.removeAt(0)
            outputs[0] = outputs[0].copy(presentationTimeUs = 123L, sha256 = "not-a-digest")
        }
        val result = WolfSceneRenderResult(
            valid.requestId, valid.contractVersion, valid.status, malformed,
            valid.resourceUsage, valid.failureCode, valid.failureMessage, valid.runtimeTarget
        )
        val verifier = artifactVerifier(emptyMap())
        val checked = WolfSceneContractValidator.validateResult(request, result, verifier)
        assertFalse(checked.isValid)
        assertTrue(checked.errors.any { it.contains("presentationTimeUs") })
        assertTrue(checked.errors.any { it.contains("SHA-256") })
        assertTrue(checked.errors.any { it.contains("complete") })
        assertTrue(checked.errors.any { it.contains("independently verified") })
    }

    @Test
    fun resultRejectsMetadataHashMismatchAndOutputBytesOverLimit() {
        val request = request()
        val valid = successfulResult(request)
        val altered = valid.outputs.map { it.copy(byteCount = 40_000_000L) }
        val result = WolfSceneRenderResult(
            valid.requestId, valid.contractVersion, valid.status, altered,
            valid.resourceUsage, valid.failureCode, valid.failureMessage, valid.runtimeTarget
        )
        val metadata = altered.associate { output ->
            output.outputReference to WolfSceneOutputArtifactMetadata(
                output.byteCount, "c".repeat(64), output.widthPx, output.heightPx,
                output.pixelFormat, output.imageEncoding, output.colorSpace, output.alphaMode
            )
        }
        val checked = WolfSceneContractValidator.validateResult(request, result, artifactVerifier(metadata))
        assertFalse(checked.isValid)
        assertTrue(checked.errors.any { it.contains("SHA-256 does not match") })
        assertTrue(checked.errors.any { it.contains("temporary storage") })
    }

    @Test
    fun resultRejectsRuntimeProvenanceMismatch() {
        val request = request()
        val valid = successfulResult(request)
        val wrongRuntime = WolfSceneRuntimeTarget("wolf.adapter.filament", "1.0.0", "filament", "1.75.0")
        val result = WolfSceneRenderResult(
            valid.requestId, valid.contractVersion, valid.status, valid.outputs,
            valid.resourceUsage, valid.failureCode, valid.failureMessage, wrongRuntime
        )
        val metadata = valid.outputs.associate { output ->
            output.outputReference to WolfSceneOutputArtifactMetadata(
                output.byteCount, output.sha256, output.widthPx, output.heightPx,
                output.pixelFormat, output.imageEncoding, output.colorSpace, output.alphaMode
            )
        }
        val checked = WolfSceneContractValidator.validateResult(request, result, artifactVerifier(metadata))
        assertFalse(checked.isValid)
        assertTrue(checked.errors.any { it.contains("runtime/adapter identity") })
    }

    @Test
    fun resultOutputCollectionIsImmutable() {
        val base = request()
        val result = successfulResult(base)
        try {
            (result.outputs as MutableList).clear()
            throw AssertionError("Expected immutable output collection")
        } catch (_: UnsupportedOperationException) {
            assertEquals(4, result.outputs.size)
        }
    }

    @Test
    fun failedRendererMustNotReturnPartialFrames() {
        val request = request()
        val invalid = WolfSceneRenderResult(
            request.requestId,
            request.contractVersion,
            WolfSceneRenderStatus.FAILED,
            outputs = successfulResult(request).outputs,
            failureCode = WolfSceneFailureCode.RENDER_FAILED
        )
        val result = WolfSceneContractValidator.validateResult(
            request, invalid, artifactVerifier(emptyMap())
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("partial outputs") })
    }

    @Test
    fun requestCollectionsAreDefensivelyCopied() {
        val base = request()
        val mutableLayers = base.layers.toMutableList()
        val mutableAssets = base.assets.toMutableList()
        val copy = WolfSceneRenderRequest(
            base.requestId, base.contractVersion, base.sceneId, base.shotId, base.masterSeed,
            base.timeline, base.canvas, base.coordinateSystem, base.camera,
            mutableLayers, mutableAssets, base.resourceEnvelope, base.runtimeTarget
        )
        mutableLayers.clear()
        mutableAssets.clear()
        assertEquals(2, copy.layers.size)
        assertEquals(1, copy.assets.size)
    }
}
