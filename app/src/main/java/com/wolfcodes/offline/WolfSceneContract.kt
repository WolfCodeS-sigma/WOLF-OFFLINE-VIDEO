package com.wolfcodes.offline

/**
 * Versioned, engine-neutral boundary between WOLF Kotlin Core and one renderer adapter.
 *
 * The contract carries data only. It does not expose engine objects, allocate film time,
 * approve assets, or let a renderer rewrite the story/shot plan.
 */
object WolfSceneContract {
    const val VERSION = "1.0.0"
}

enum class WolfSceneAxis { X, Y, Z }
enum class WolfSceneHandedness { RIGHT_HANDED, LEFT_HANDED }
enum class WolfScenePixelOrigin { TOP_LEFT, BOTTOM_LEFT }
enum class WolfSceneBlendMode { NORMAL, ADD, MULTIPLY, SCREEN }
enum class WolfScenePixelFormat { RGBA_8888, RGBA_F16, PNG, WEBP, JPEG }
enum class WolfSceneColorSpace { SRGB, LINEAR_SRGB, DISPLAY_P3, BT709 }
enum class WolfSceneAlphaMode { OPAQUE, STRAIGHT, PREMULTIPLIED }
enum class WolfSceneRenderStatus { SUCCESS, REJECTED, FAILED, CANCELLED }

enum class WolfSceneFailureCode {
    INVALID_REQUEST,
    UNSUPPORTED_CONTRACT_VERSION,
    UNSUPPORTED_RUNTIME,
    ASSET_NOT_APPROVED,
    RESOURCE_LIMIT_EXCEEDED,
    RENDERER_UNAVAILABLE,
    RENDER_FAILED,
    CANCELLED,
    INVALID_OUTPUT
}

data class WolfSceneCoordinateSystem(
    val handedness: WolfSceneHandedness,
    val upAxis: WolfSceneAxis,
    val forwardAxis: WolfSceneAxis,
    val forwardPositive: Boolean,
    val pixelOrigin: WolfScenePixelOrigin
) {
    fun isValid(): Boolean = upAxis != forwardAxis
}

data class WolfSceneCanvas(
    val widthPx: Int,
    val heightPx: Int,
    val pixelAspectNumerator: Int = 1,
    val pixelAspectDenominator: Int = 1
) {
    fun isValid(): Boolean =
        widthPx > 0 && heightPx > 0 &&
            pixelAspectNumerator > 0 && pixelAspectDenominator > 0
}

data class WolfSceneTimeline(
    val startFrameIndex: Long,
    val frameCount: Int,
    val frameRateNumerator: Long,
    val frameRateDenominator: Long
) {
    fun isValid(): Boolean {
        if (startFrameIndex < 0L || frameCount <= 0 ||
            frameRateNumerator <= 0L || frameRateDenominator <= 0L
        ) return false
        return runCatching { Math.addExact(startFrameIndex, frameCount.toLong()) }.isSuccess
    }

    val endFrameExclusive: Long
        get() {
            require(isValid()) { "Invalid scene timeline" }
            return Math.addExact(startFrameIndex, frameCount.toLong())
        }

    fun contains(frameIndex: Long): Boolean =
        isValid() && frameIndex >= startFrameIndex && frameIndex < endFrameExclusive

    fun presentationTimeUs(frameIndex: Long): Long {
        require(contains(frameIndex)) { "Frame is outside the requested scene timeline" }
        return WolfMotionCodeLibrary.framePresentationTimeUs(
            frameIndex,
            frameRateNumerator,
            frameRateDenominator
        )
    }
}

data class WolfSceneCameraCommand(
    val position: WolfVector3,
    val target: WolfVector3,
    val up: WolfVector3,
    val verticalFieldOfViewDegrees: Double,
    val nearClip: Double,
    val farClip: Double
) {
    fun isValid(): Boolean {
        if (!verticalFieldOfViewDegrees.isFinite() ||
            verticalFieldOfViewDegrees <= 1.0 || verticalFieldOfViewDegrees >= 179.0 ||
            !nearClip.isFinite() || !farClip.isFinite() ||
            nearClip <= 0.0 || farClip <= nearClip
        ) return false
        val direction = target - position
        if (direction.length() <= WolfVector3.EPSILON || up.length() <= WolfVector3.EPSILON) return false
        return direction.cross(up).length() > WolfVector3.EPSILON
    }
}

data class WolfSceneAssetReference(
    val assetId: String,
    val version: String,
    val sha256: String,
    val usage: String
) {
    fun isWellFormed(): Boolean =
        assetId.isIdentifier() && version.isNotBlank() &&
            Regex("[0-9a-f]{64}").matches(sha256) && usage.isNotBlank()
}

data class WolfSceneLayerCommand(
    val layerId: String,
    val zIndex: Int,
    val blendMode: WolfSceneBlendMode,
    val opacityPermille: Int,
    val visible: Boolean = true,
    val assetIds: List<String> = emptyList()
) {
    fun isWellFormed(): Boolean =
        layerId.isIdentifier() && opacityPermille in 0..1000 &&
            assetIds.all { it.isIdentifier() } && assetIds.distinct().size == assetIds.size
}

data class WolfSceneResourceEnvelope(
    val maxWorkingSetBytes: Long,
    val maxTemporaryStorageBytes: Long,
    val maxReusableOutputBytes: Long,
    val maxTransientElementsPerFrame: Long,
    val maxRetainedFrameCount: Long,
    val maxExecutionMillis: Long,
    val maxNativeGpuBytes: Long? = null
) {
    fun isWellFormed(): Boolean =
        maxWorkingSetBytes > 0L &&
            maxTemporaryStorageBytes > 0L &&
            maxReusableOutputBytes > 0L &&
            maxTransientElementsPerFrame > 0L &&
            maxRetainedFrameCount > 0L &&
            maxExecutionMillis > 0L &&
            (maxNativeGpuBytes == null || maxNativeGpuBytes > 0L)
}

data class WolfSceneRuntimeTarget(
    val adapterId: String,
    val adapterVersion: String,
    val engineId: String,
    val engineVersion: String
) {
    fun isWellFormed(): Boolean =
        adapterId.isIdentifier() && adapterVersion.isNotBlank() &&
            engineId.isIdentifier() && engineVersion.isNotBlank()
}

/**
 * masterSeed is an explicit 64-bit bit pattern; no wall-clock/device-derived seed is allowed.
 * frame indices are absolute within the film timeline, including chapter-render requests.
 */
data class WolfSceneRenderRequest(
    val requestId: String,
    val contractVersion: String,
    val sceneId: String,
    val shotId: String,
    val masterSeed: Long,
    val timeline: WolfSceneTimeline,
    val canvas: WolfSceneCanvas,
    val coordinateSystem: WolfSceneCoordinateSystem,
    val camera: WolfSceneCameraCommand?,
    val layers: List<WolfSceneLayerCommand>,
    val assets: List<WolfSceneAssetReference>,
    val resourceEnvelope: WolfSceneResourceEnvelope,
    val runtimeTarget: WolfSceneRuntimeTarget
)

data class WolfSceneFrameOutput(
    val layerId: String,
    val frameIndex: Long,
    val presentationTimeUs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val pixelFormat: WolfScenePixelFormat,
    val colorSpace: WolfSceneColorSpace,
    val alphaMode: WolfSceneAlphaMode,
    val sha256: String,
    /** Opaque in-process or app-private output handle; never an executable path or URL. */
    val outputReference: String
)

data class WolfSceneResourceUsage(
    val peakWorkingSetBytes: Long,
    val peakTemporaryStorageBytes: Long,
    val reusableOutputBytes: Long,
    val peakTransientElementsPerFrame: Long,
    val peakRetainedFrameCount: Long,
    val elapsedExecutionMillis: Long,
    val peakNativeGpuBytes: Long? = null
) {
    fun isWellFormed(): Boolean =
        peakWorkingSetBytes >= 0L &&
            peakTemporaryStorageBytes >= 0L &&
            reusableOutputBytes >= 0L &&
            peakTransientElementsPerFrame >= 0L &&
            peakRetainedFrameCount >= 0L &&
            elapsedExecutionMillis >= 0L &&
            (peakNativeGpuBytes == null || peakNativeGpuBytes >= 0L)
}

data class WolfSceneRenderResult(
    val requestId: String,
    val contractVersion: String,
    val status: WolfSceneRenderStatus,
    val outputs: List<WolfSceneFrameOutput> = emptyList(),
    val resourceUsage: WolfSceneResourceUsage? = null,
    val failureCode: WolfSceneFailureCode? = null,
    val failureMessage: String? = null
)

data class WolfSceneContractValidation(
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object WolfSceneContractValidator {
    fun validateRequest(request: WolfSceneRenderRequest): WolfSceneContractValidation {
        val errors = mutableListOf<String>()
        if (!request.requestId.isIdentifier()) errors += "requestId is invalid"
        if (request.contractVersion != WolfSceneContract.VERSION) errors += "unsupported contractVersion"
        if (!request.sceneId.isIdentifier()) errors += "sceneId is invalid"
        if (!request.shotId.isIdentifier()) errors += "shotId is invalid"
        if (!request.timeline.isValid()) errors += "timeline is invalid"
        if (!request.canvas.isValid()) errors += "canvas is invalid"
        if (!request.coordinateSystem.isValid()) errors += "coordinateSystem is invalid"
        if (request.camera != null && !request.camera.isValid()) errors += "camera command is invalid"
        if (request.layers.isEmpty()) errors += "at least one scene layer is required"
        if (request.layers.any { !it.isWellFormed() }) errors += "one or more layer commands are invalid"
        if (request.layers.map { it.layerId }.distinct().size != request.layers.size) {
            errors += "layerId values must be unique"
        }
        if (request.assets.any { !it.isWellFormed() }) errors += "one or more asset references are invalid"
        if (request.assets.map { it.assetId }.distinct().size != request.assets.size) {
            errors += "assetId values must be unique within a request"
        }
        val knownAssets = request.assets.map { it.assetId }.toSet()
        if (request.layers.flatMap { it.assetIds }.any { it !in knownAssets }) {
            errors += "a layer references an asset absent from the request"
        }
        if (!request.resourceEnvelope.isWellFormed()) errors += "resource envelope must contain finite positive hard limits"
        if (!request.runtimeTarget.isWellFormed()) errors += "runtime target is invalid"
        return WolfSceneContractValidation(errors)
    }

    fun validateResult(
        request: WolfSceneRenderRequest,
        result: WolfSceneRenderResult
    ): WolfSceneContractValidation {
        val errors = mutableListOf<String>()
        val requestValidation = validateRequest(request)
        if (!requestValidation.isValid) errors += "result cannot be validated against an invalid request"
        if (result.requestId != request.requestId) errors += "result requestId does not match"
        if (result.contractVersion != request.contractVersion) errors += "result contractVersion does not match"

        if (result.status != WolfSceneRenderStatus.SUCCESS) {
            if (result.outputs.isNotEmpty()) errors += "failed or cancelled results must not expose partial outputs"
            if (result.failureCode == null) errors += "failed or cancelled results require a failureCode"
            return WolfSceneContractValidation(errors)
        }

        if (result.failureCode != null) errors += "successful results cannot contain failureCode"
        val usage = result.resourceUsage
        if (usage == null || !usage.isWellFormed()) {
            errors += "successful results require valid resource measurements"
        } else {
            val limits = request.resourceEnvelope
            if (usage.peakWorkingSetBytes > limits.maxWorkingSetBytes ||
                usage.peakTemporaryStorageBytes > limits.maxTemporaryStorageBytes ||
                usage.reusableOutputBytes > limits.maxReusableOutputBytes ||
                usage.peakTransientElementsPerFrame > limits.maxTransientElementsPerFrame ||
                usage.peakRetainedFrameCount > limits.maxRetainedFrameCount ||
                usage.elapsedExecutionMillis > limits.maxExecutionMillis
            ) errors += "measured resource usage exceeded the request envelope"
            if (limits.maxNativeGpuBytes != null &&
                (usage.peakNativeGpuBytes == null || usage.peakNativeGpuBytes > limits.maxNativeGpuBytes)
            ) errors += "native GPU usage is missing or exceeded the request envelope"
        }

        if (result.outputs.isEmpty()) {
            errors += "successful results must contain rendered frame outputs"
            return WolfSceneContractValidation(errors)
        }

        val requestedLayers = request.layers.filter { it.visible }.map { it.layerId }.toSet()
        val unexpectedLayers = result.outputs.map { it.layerId }.toSet() - requestedLayers
        if (unexpectedLayers.isNotEmpty()) errors += "result contains an unrequested layer"

        val duplicateKeys = result.outputs.groupBy { it.layerId to it.frameIndex }.filterValues { it.size > 1 }
        if (duplicateKeys.isNotEmpty()) errors += "result contains duplicate layer/frame outputs"

        result.outputs.forEach { output ->
            if (output.layerId !in requestedLayers) errors += "output layerId is not requested"
            if (!request.timeline.contains(output.frameIndex)) errors += "output frameIndex is outside the request"
            else if (output.presentationTimeUs != request.timeline.presentationTimeUs(output.frameIndex)) {
                errors += "output presentationTimeUs does not match the film timeline"
            }
            if (output.widthPx != request.canvas.widthPx || output.heightPx != request.canvas.heightPx) {
                errors += "output dimensions do not match the requested canvas"
            }
            if (!Regex("[0-9a-f]{64}").matches(output.sha256)) errors += "output SHA-256 is invalid"
            if (output.outputReference.isBlank()) errors += "outputReference is missing"
            if (output.alphaMode == WolfSceneAlphaMode.OPAQUE &&
                output.pixelFormat in setOf(WolfScenePixelFormat.JPEG)
            ) {
                // JPEG is compatible with an opaque output; this explicit branch documents that pairing.
            }
        }

        requestedLayers.forEach { layerId ->
            val layerOutputs = result.outputs.filter { it.layerId == layerId }
            if (layerOutputs.size != request.timeline.frameCount) {
                errors += "layer $layerId does not contain the complete requested frame range"
            } else {
                val actual = layerOutputs.map { it.frameIndex }.sorted()
                val expected = (0 until request.timeline.frameCount)
                    .map { request.timeline.startFrameIndex + it.toLong() }
                if (actual != expected) errors += "layer $layerId has a discontinuous frame range"
            }
        }
        return WolfSceneContractValidation(errors.distinct())
    }
}

private fun String.isIdentifier(): Boolean =
    length in 1..128 && matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]*"))
