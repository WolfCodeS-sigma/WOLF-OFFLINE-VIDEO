package com.wolfcodes.offline

import java.math.BigInteger
import kotlin.math.roundToInt

/**
 * Versioned, engine-neutral boundary between WOLF Kotlin Core and one renderer adapter.
 *
 * The contract carries data only. It does not expose engine objects, allocate film time,
 * approve assets, or let a renderer rewrite the story/shot plan.
 */
object WolfSceneContract {
    const val VERSION = "1.2.0"
}

enum class WolfSceneAxis { X, Y, Z }
enum class WolfSceneHandedness { RIGHT_HANDED, LEFT_HANDED }
enum class WolfScenePixelOrigin { TOP_LEFT, BOTTOM_LEFT }
enum class WolfSceneBlendMode { NORMAL, ADD, MULTIPLY, SCREEN }
enum class WolfScenePixelFormat { RGBA_8888, RGBA_F16 }
enum class WolfSceneImageEncoding { RAW, PNG, WEBP, JPEG }
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

/**
 * Global protocol ceilings are anti-abuse limits, not measured device budgets.
 * Runtime dispatch must still apply WolfResourceGuard and the approved renderer profile.
 */
object WolfSceneContractLimits {
    const val MAX_CANVAS_DIMENSION_PX = 4096
    const val MAX_CANVAS_PIXELS = 16_777_216L
    const val MAX_FRAME_COUNT = 21_600
    const val MAX_LAYER_COUNT = 16
    const val MAX_ASSET_COUNT = 128
    const val MAX_OUTPUT_COUNT = 65_536L
    const val MAX_KEYFRAME_COUNT = WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES
    const val MAX_PIXEL_ASPECT_COMPONENT = 1000
    const val MAX_IDENTIFIER_LENGTH = 128
    const val MAX_ARTIFACT_REFERENCE_LENGTH = 137
}

private object WolfSceneContractPatterns {
    val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
    val sha256 = Regex("[0-9a-f]{64}")
}

private const val SCENE_ARTIFACT_PREFIX = "artifact:"

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
        widthPx in 1..WolfSceneContractLimits.MAX_CANVAS_DIMENSION_PX &&
            heightPx in 1..WolfSceneContractLimits.MAX_CANVAS_DIMENSION_PX &&
            widthPx.toLong() * heightPx.toLong() <= WolfSceneContractLimits.MAX_CANVAS_PIXELS &&
            pixelAspectNumerator in 1..WolfSceneContractLimits.MAX_PIXEL_ASPECT_COMPONENT &&
            pixelAspectDenominator in 1..WolfSceneContractLimits.MAX_PIXEL_ASPECT_COMPONENT
}

data class WolfSceneTimeline(
    val startFrameIndex: Long,
    val frameCount: Int,
    val frameRateNumerator: Long,
    val frameRateDenominator: Long
) {
    fun isValid(): Boolean {
        if (startFrameIndex < 0L ||
            frameCount !in 1..WolfSceneContractLimits.MAX_FRAME_COUNT ||
            frameRateNumerator <= 0L ||
            frameRateDenominator <= 0L
        ) return false

        val endExclusive = runCatching { Math.addExact(startFrameIndex, frameCount.toLong()) }.getOrNull()
            ?: return false
        return runCatching {
            val firstPresentationTimeUs = WolfMotionCodeLibrary.framePresentationTimeUs(
                startFrameIndex,
                frameRateNumerator,
                frameRateDenominator
            )
            val lastPresentationTimeUs = WolfMotionCodeLibrary.framePresentationTimeUs(
                endExclusive - 1L,
                frameRateNumerator,
                frameRateDenominator
            )

            // PTS values are rounded to whole microseconds. When an exact frame
            // interval is at least one microsecond, this monotonic rounded sequence
            // is strictly increasing. Below one microsecond, each adjacent rounded
            // step can only be zero or one; checking the total span against the
            // number of frame edges therefore proves every step is distinct.
            val intervalNumerator = BigInteger.valueOf(frameRateDenominator)
                .multiply(BigInteger.valueOf(1_000_000L))
            val frameIntervalAtLeastOneMicrosecond =
                BigInteger.valueOf(frameRateNumerator) <= intervalNumerator

            frameIntervalAtLeastOneMicrosecond ||
                Math.subtractExact(lastPresentationTimeUs, firstPresentationTimeUs) ==
                frameCount.toLong() - 1L
        }.getOrDefault(false)
    }

    val endFrameExclusive: Long
        get() {
            require(isValid()) { "Invalid scene timeline" }
            return Math.addExact(startFrameIndex, frameCount.toLong())
        }

    /**
     * Fast membership check for callers that have already validated this timeline.
     * Frame indices and counts are non-negative and bounded, so subtraction is safe
     * once frameIndex >= startFrameIndex.
     */
    internal fun containsValidatedFrameIndex(frameIndex: Long): Boolean =
        startFrameIndex >= 0L &&
            frameCount in 1..WolfSceneContractLimits.MAX_FRAME_COUNT &&
            frameIndex >= startFrameIndex &&
            frameIndex - startFrameIndex < frameCount.toLong()

    internal fun presentationTimeUsForValidatedFrame(frameIndex: Long): Long {
        require(containsValidatedFrameIndex(frameIndex)) {
            "Frame is outside the validated scene timeline"
        }
        return WolfMotionCodeLibrary.framePresentationTimeUs(
            frameIndex,
            frameRateNumerator,
            frameRateDenominator
        )
    }

    fun contains(frameIndex: Long): Boolean =
        isValid() && containsValidatedFrameIndex(frameIndex)

    fun presentationTimeUs(frameIndex: Long): Long {
        require(contains(frameIndex)) { "Frame is outside the requested scene timeline" }
        return presentationTimeUsForValidatedFrame(frameIndex)
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
    fun isValid(): Boolean = isValidCameraValues(
        position.x, position.y, position.z,
        target.x, target.y, target.z,
        up.x, up.y, up.z,
        verticalFieldOfViewDegrees, nearClip, farClip
    )
}

/** Numeric camera-basis check shared by static and animated camera commands. */
private fun isValidCameraValues(
    positionX: Double, positionY: Double, positionZ: Double,
    targetX: Double, targetY: Double, targetZ: Double,
    upX: Double, upY: Double, upZ: Double,
    verticalFieldOfViewDegrees: Double,
    nearClip: Double,
    farClip: Double
): Boolean {
    if (!verticalFieldOfViewDegrees.isFinite() ||
        verticalFieldOfViewDegrees <= 1.0 || verticalFieldOfViewDegrees >= 179.0 ||
        !nearClip.isFinite() || !farClip.isFinite() ||
        nearClip <= 0.0 || farClip <= nearClip
    ) return false

    val directionX = targetX - positionX
    val directionY = targetY - positionY
    val directionZ = targetZ - positionZ
    if (!directionX.isFinite() || !directionY.isFinite() || !directionZ.isFinite()) return false

    val directionLengthSquared =
        directionX * directionX + directionY * directionY + directionZ * directionZ
    val upLengthSquared = upX * upX + upY * upY + upZ * upZ
    val minimumLengthSquared = WolfVector3.EPSILON * WolfVector3.EPSILON
    if (!directionLengthSquared.isFinite() || directionLengthSquared <= minimumLengthSquared ||
        !upLengthSquared.isFinite() || upLengthSquared <= minimumLengthSquared
    ) return false

    val crossX = directionY * upZ - directionZ * upY
    val crossY = directionZ * upX - directionX * upZ
    val crossZ = directionX * upY - directionY * upX
    if (!crossX.isFinite() || !crossY.isFinite() || !crossZ.isFinite()) return false
    val crossLengthSquared = crossX * crossX + crossY * crossY + crossZ * crossZ
    return crossLengthSquared.isFinite() && crossLengthSquared > minimumLengthSquared
}

data class WolfSceneCameraMotionTrack(
    val position: WolfVector3Curve,
    val target: WolfVector3Curve,
    val up: WolfVector3Curve,
    val verticalFieldOfViewDegrees: WolfScalarCurve,
    val nearClip: Double,
    val farClip: Double
) {
    fun isWellFormed(frameCount: Int): Boolean {
        if (frameCount !in 1..WolfSceneContractLimits.MAX_FRAME_COUNT ||
            !nearClip.isFinite() || !farClip.isFinite() || nearClip <= 0.0 || farClip <= nearClip
        ) return false
        val positionKeys = position.keyframes
        val targetKeys = target.keyframes
        val upKeys = up.keyframes
        val fieldOfViewKeys = verticalFieldOfViewDegrees.keyframes
        if (targetKeys.size != positionKeys.size || upKeys.size != positionKeys.size ||
            fieldOfViewKeys.size != positionKeys.size
        ) return false
        for (index in positionKeys.indices) {
            val keyPosition = positionKeys[index].position
            if (targetKeys[index].position != keyPosition ||
                upKeys[index].position != keyPosition ||
                fieldOfViewKeys[index].position != keyPosition
            ) return false
        }
        // Reuse scratch buffers across frames; camera validation is allocation-free per frame.
        val positionComponents = DoubleArray(3)
        val targetComponents = DoubleArray(3)
        val upComponents = DoubleArray(3)
        return try {
            for (frame in 0 until frameCount) {
                val progress = if (frameCount == 1) 0.0 else frame.toDouble() / (frameCount - 1).toDouble()
                position.sampleInto(progress, positionComponents)
                target.sampleInto(progress, targetComponents)
                up.sampleInto(progress, upComponents)
                val valid = isValidCameraValues(
                    positionComponents[0], positionComponents[1], positionComponents[2],
                    targetComponents[0], targetComponents[1], targetComponents[2],
                    upComponents[0], upComponents[1], upComponents[2],
                    verticalFieldOfViewDegrees.sample(progress),
                    nearClip,
                    farClip
                )
                if (!valid) return false
            }
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

data class WolfSceneAssetReference(
    val assetId: String,
    val version: String,
    val sha256: String,
    val usage: String
) {
    fun isWellFormed(): Boolean =
        assetId.isSceneIdentifier() &&
            version.isNotBlank() && version.length <= 64 &&
            sha256.isSha256Digest() &&
            usage.isSceneIdentifier()
}

/**
 * Layer-motion semantics:
 * - opacity is a normalized multiplier applied to the layer's base opacityPermille.
 * - negative scale is supported for mirroring; each animated axis must keep its sign
 *   so interpolation never crosses a singular zero scale.
 */
data class WolfSceneMotionTrack(
    val targetLayerId: String,
    val translation: WolfVector3Curve? = null,
    val rotation: WolfQuaternionCurve? = null,
    val scale: WolfVector3Curve? = null,
    val opacity: WolfScalarCurve? = null
) {
    fun effectiveOpacityPermille(baseOpacityPermille: Int, progress: Double): Int {
        require(baseOpacityPermille in 0..1000) { "Base opacity must be in 0..1000 permille" }
        val multiplier = opacity?.sample(progress) ?: 1.0
        require(multiplier in 0.0..1.0) { "Animated opacity multiplier must be in 0..1" }
        return (baseOpacityPermille * multiplier).roundToInt()
    }

    private fun hasStableNonZeroScaleAxis(axis: Int): Boolean {
        val curve = scale ?: return true
        var expectedSign = 0
        for (keyframe in curve.keyframes) {
            val value = when (axis) {
                0 -> keyframe.value.x
                1 -> keyframe.value.y
                else -> keyframe.value.z
            }
            if (kotlin.math.abs(value) <= WolfVector3.EPSILON) return false
            val sign = if (value < 0.0) -1 else 1
            if (expectedSign != 0 && sign != expectedSign) return false
            expectedSign = sign
        }
        return true
    }

    fun isWellFormed(): Boolean {
        if (!targetLayerId.isSceneIdentifier()) return false
        if (translation == null && rotation == null && scale == null && opacity == null) return false
        if (opacity != null && opacity.keyframes.any { it.value !in 0.0..1.0 }) return false
        if (scale != null && !(
            hasStableNonZeroScaleAxis(0) &&
                hasStableNonZeroScaleAxis(1) &&
                hasStableNonZeroScaleAxis(2)
            )
        ) return false
        return true
    }
}

class WolfSceneLayerCommand(
    val layerId: String,
    val zIndex: Int,
    val blendMode: WolfSceneBlendMode,
    val opacityPermille: Int,
    val visible: Boolean = true,
    assetIds: List<String> = emptyList(),
    val motion: WolfSceneMotionTrack? = null
) {
    val assetIds: List<String> = WolfMotionCodeLibrary.immutableCopy(
        assetIds, WolfSceneContractLimits.MAX_ASSET_COUNT, "Layer asset identifiers"
    )

    fun isWellFormed(): Boolean =
        layerId.isSceneIdentifier() &&
            opacityPermille in 0..1000 &&
            assetIds.size <= WolfSceneContractLimits.MAX_ASSET_COUNT &&
            assetIds.all { it.isSceneIdentifier() } &&
            assetIds.distinct().size == assetIds.size &&
            (motion == null || (motion.targetLayerId == layerId && motion.isWellFormed()))
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
        maxWorkingSetBytes in 1L..(2L * 1024L * 1024L * 1024L) &&
            maxTemporaryStorageBytes in 1L..(8L * 1024L * 1024L * 1024L) &&
            maxReusableOutputBytes in 1L..(512L * 1024L * 1024L) &&
            maxTransientElementsPerFrame in 1L..1_000_000L &&
            maxRetainedFrameCount in 1L..512L &&
            maxExecutionMillis in 1L..1_800_000L &&
            (maxNativeGpuBytes == null || maxNativeGpuBytes in 1L..(2L * 1024L * 1024L * 1024L))
}

data class WolfSceneRuntimeTarget(
    val adapterId: String,
    val adapterVersion: String,
    val engineId: String,
    val engineVersion: String
) {
    fun isWellFormed(): Boolean =
        adapterId.isSceneIdentifier() && adapterVersion.isNotBlank() && adapterVersion.length <= 64 &&
            engineId.isSceneIdentifier() && engineVersion.isNotBlank() && engineVersion.length <= 64
}

/**
 * masterSeed is an explicit 64-bit bit pattern; no wall-clock/device-derived seed is allowed.
 * frame indices are absolute within the film timeline, including chapter-render requests.
 */
class WolfSceneRenderRequest(
    val requestId: String,
    val contractVersion: String,
    val sceneId: String,
    val shotId: String,
    val masterSeed: Long,
    val timeline: WolfSceneTimeline,
    val canvas: WolfSceneCanvas,
    val coordinateSystem: WolfSceneCoordinateSystem,
    val camera: WolfSceneCameraCommand?,
    layers: List<WolfSceneLayerCommand>,
    assets: List<WolfSceneAssetReference>,
    val resourceEnvelope: WolfSceneResourceEnvelope,
    val runtimeTarget: WolfSceneRuntimeTarget,
    val cameraMotion: WolfSceneCameraMotionTrack? = null
) {
    val layers: List<WolfSceneLayerCommand> = WolfMotionCodeLibrary.immutableCopy(
        layers, WolfSceneContractLimits.MAX_LAYER_COUNT, "Scene layers"
    )
    val assets: List<WolfSceneAssetReference> = WolfMotionCodeLibrary.immutableCopy(
        assets, WolfSceneContractLimits.MAX_ASSET_COUNT, "Scene assets"
    )
}

data class WolfSceneFrameOutput(
    val layerId: String,
    val frameIndex: Long,
    val presentationTimeUs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val pixelFormat: WolfScenePixelFormat,
    val imageEncoding: WolfSceneImageEncoding,
    val colorSpace: WolfSceneColorSpace,
    val alphaMode: WolfSceneAlphaMode,
    val byteCount: Long,
    val sha256: String,
    /** Opaque app-private artifact identifier; never a filesystem path or URL. */
    val outputReference: String,
    val retainedForReuse: Boolean = false
)

data class WolfSceneOutputArtifactMetadata(
    val byteCount: Long,
    val sha256: String,
    val widthPx: Int,
    val heightPx: Int,
    val pixelFormat: WolfScenePixelFormat,
    val imageEncoding: WolfSceneImageEncoding,
    val colorSpace: WolfSceneColorSpace,
    val alphaMode: WolfSceneAlphaMode
)

/**
 * The implementation must resolve the opaque identifier inside app-private storage,
 * stream/hash the actual bytes, and return metadata computed from those bytes.
 * Returning caller-supplied metadata without reading the referenced bytes is invalid.
 */
fun interface WolfSceneOutputArtifactVerifier {
    fun verifyAndInspect(outputReference: String): WolfSceneOutputArtifactMetadata?
}

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

class WolfSceneRenderResult(
    val requestId: String,
    val contractVersion: String,
    val status: WolfSceneRenderStatus,
    outputs: List<WolfSceneFrameOutput> = emptyList(),
    val resourceUsage: WolfSceneResourceUsage? = null,
    val failureCode: WolfSceneFailureCode? = null,
    val failureMessage: String? = null,
    val runtimeTarget: WolfSceneRuntimeTarget? = null
) {
    val outputs: List<WolfSceneFrameOutput> = WolfMotionCodeLibrary.immutableCopy(
        outputs, WolfSceneContractLimits.MAX_OUTPUT_COUNT.toInt(), "Scene frame outputs"
    )
}

class WolfSceneContractValidation(errors: List<String>) {
    val errors: List<String> = WolfMotionCodeLibrary.immutableCopy(errors)
    val isValid: Boolean get() = errors.isEmpty()
}

object WolfSceneContractValidator {
    fun validateRequest(request: WolfSceneRenderRequest): WolfSceneContractValidation {
        val errors = mutableListOf<String>()
        if (!request.requestId.isSceneIdentifier()) errors += "requestId is invalid"
        if (request.contractVersion != WolfSceneContract.VERSION) errors += "unsupported contractVersion"
        if (!request.sceneId.isSceneIdentifier()) errors += "sceneId is invalid"
        if (!request.shotId.isSceneIdentifier()) errors += "shotId is invalid"
        val timelineValid = request.timeline.isValid()
        if (!timelineValid) errors += "timeline is invalid or its PTS exceeds Long range"
        if (!request.canvas.isValid()) errors += "canvas dimensions or pixel aspect ratio exceed contract limits"
        if (!request.coordinateSystem.isValid()) errors += "coordinateSystem is invalid"
        if ((request.camera == null) == (request.cameraMotion == null)) {
            errors += "request must define exactly one camera source: static camera or camera motion track"
        }
        if (request.layers.size > WolfSceneContractLimits.MAX_LAYER_COUNT) {
            errors += "layer count exceeds contract limits"
        }
        if (request.assets.size > WolfSceneContractLimits.MAX_ASSET_COUNT) {
            errors += "asset count exceeds contract limits"
        }
        // Return before copying, mapping, sorting, or grouping attacker-controlled oversized collections.
        if (errors.any { it.contains("count exceeds") }) {
            return WolfSceneContractValidation(errors.distinct())
        }
        if (request.camera != null && !request.camera.isValid()) errors += "camera command is invalid"
        if (request.cameraMotion != null &&
            (!timelineValid || !request.cameraMotion.isWellFormed(request.timeline.frameCount))
        ) {
            errors += "camera motion track is invalid for the requested frame timeline"
        }
        if (request.layers.isEmpty()) errors += "at least one scene layer is required"
        if (request.layers.none { it.visible }) errors += "at least one visible scene layer is required"
        if (request.layers.any { !it.isWellFormed() }) errors += "one or more layer commands or motion tracks are invalid"
        if (request.layers.map { it.layerId }.distinct().size != request.layers.size) {
            errors += "layerId values must be unique"
        }
        if (request.layers.map { it.zIndex }.distinct().size != request.layers.size) {
            errors += "zIndex values must be unique for deterministic layer ordering"
        }
        if (request.assets.any { !it.isWellFormed() }) errors += "one or more asset references are invalid"
        if (request.assets.map { it.assetId }.distinct().size != request.assets.size) {
            errors += "assetId values must be unique within a request"
        }
        val knownAssets = request.assets.map { it.assetId }.toSet()
        request.layers.forEach { layer ->
            if (layer.assetIds.any { it !in knownAssets }) {
                errors += "a layer references an asset absent from the request"
            }
        }
        if (request.assets.any { asset -> request.layers.none { asset.assetId in it.assetIds } }) {
            errors += "request contains an unused asset reference"
        }
        if (!request.resourceEnvelope.isWellFormed()) errors += "resource envelope is outside contract ceilings"
        if (!request.runtimeTarget.isWellFormed()) errors += "runtime target is invalid"

        val visibleLayers = request.layers.count { it.visible }
        if (request.timeline.frameCount > 0 &&
            visibleLayers > 0 &&
            request.timeline.frameCount.toLong() * visibleLayers.toLong() > WolfSceneContractLimits.MAX_OUTPUT_COUNT
        ) {
            errors += "requested frame output count exceeds contract limits"
        }

        return WolfSceneContractValidation(errors.distinct())
    }

    fun validateResult(
        request: WolfSceneRenderRequest,
        result: WolfSceneRenderResult,
        artifactVerifier: WolfSceneOutputArtifactVerifier
    ): WolfSceneContractValidation {
        val errors = mutableListOf<String>()
        val requestValidation = validateRequest(request)
        if (!requestValidation.isValid) {
            errors += "result cannot be validated against an invalid request"
            errors += requestValidation.errors
            return WolfSceneContractValidation(errors.distinct())
        }
        if (result.requestId != request.requestId) errors += "result requestId does not match"
        if (result.contractVersion != request.contractVersion) errors += "result contractVersion does not match"
        if (result.runtimeTarget != request.runtimeTarget) errors += "result runtime/adapter identity does not match the request"

        if (result.status != WolfSceneRenderStatus.SUCCESS) {
            if (result.outputs.isNotEmpty()) errors += "failed or cancelled results must not expose partial outputs"
            if (result.failureCode == null) errors += "failed or cancelled results require a failureCode"
            if (result.resourceUsage != null) errors += "failed or cancelled results must not publish successful resource usage"
            return WolfSceneContractValidation(errors.distinct())
        }

        if (result.failureCode != null) errors += "successful results cannot contain failureCode"
        if (result.failureMessage != null) errors += "successful results cannot contain failureMessage"

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
            return WolfSceneContractValidation(errors.distinct())
        }

        val requestedLayers = request.layers.filter { it.visible }.map { it.layerId }.toSet()
        val expectedOutputCount = request.timeline.frameCount.toLong() * requestedLayers.size.toLong()
        if (expectedOutputCount > WolfSceneContractLimits.MAX_OUTPUT_COUNT ||
            result.outputs.size.toLong() > WolfSceneContractLimits.MAX_OUTPUT_COUNT
        ) {
            errors += "result output count exceeds contract limits"
            return WolfSceneContractValidation(errors.distinct())
        }
        if (result.outputs.size.toLong() != expectedOutputCount) {
            errors += "result output count does not match the bounded request"
        }

        // One PTS computation per requested frame, rather than one BigInteger
        // calculation for every layer/frame output pair.
        val expectedPtsByFrameOffset = LongArray(request.timeline.frameCount) { offset ->
            val frameIndex = Math.addExact(request.timeline.startFrameIndex, offset.toLong())
            request.timeline.presentationTimeUsForValidatedFrame(frameIndex)
        }
        val outputsByLayer = request.layers.filter { it.visible }.associate { it.layerId to ArrayList<WolfSceneFrameOutput>() }
        val seenFrames = HashSet<Pair<String, Long>>()
        val seenReferences = HashSet<String>()
        var totalOutputBytes = 0L
        var totalReusableBytes = 0L

        result.outputs.forEach { output ->
            if (!output.layerId.isSceneIdentifier() || output.layerId !in requestedLayers) {
                errors += "output layerId is not requested"
                return@forEach
            }
            if (!request.timeline.containsValidatedFrameIndex(output.frameIndex)) {
                errors += "output frameIndex is outside the request"
                return@forEach
            }
            if (!seenFrames.add(output.layerId to output.frameIndex)) {
                errors += "result contains duplicate layer/frame outputs"
            }
            val frameOffset = (output.frameIndex - request.timeline.startFrameIndex).toInt()
            if (output.presentationTimeUs != expectedPtsByFrameOffset[frameOffset]) {
                errors += "output presentationTimeUs does not match the film timeline"
            }
            if (output.widthPx != request.canvas.widthPx || output.heightPx != request.canvas.heightPx) {
                errors += "output dimensions do not match the requested canvas"
            }
            if (output.byteCount <= 0L) errors += "output byteCount must be positive"
            if (!output.sha256.isSha256Digest()) errors += "output SHA-256 is invalid"
            val referenceIsValid = output.outputReference.isSceneArtifactReference()
            if (!referenceIsValid) {
                errors += "outputReference must be an opaque artifact identifier, not a path or URL"
            }
            if (referenceIsValid && !seenReferences.add(output.outputReference)) {
                errors += "outputReference values must be unique"
            }
            if (output.imageEncoding == WolfSceneImageEncoding.JPEG && output.alphaMode != WolfSceneAlphaMode.OPAQUE) {
                errors += "JPEG output must use OPAQUE alpha mode"
            }

            // Never pass an untrusted path/URL to the app-private artifact resolver.
            val metadata = if (referenceIsValid) {
                runCatching { artifactVerifier.verifyAndInspect(output.outputReference) }.getOrNull()
            } else {
                null
            }
            if (metadata == null) {
                errors += "output artifact could not be independently verified"
            } else {
                if (metadata.byteCount != output.byteCount ||
                    metadata.sha256 != output.sha256 ||
                    metadata.widthPx != output.widthPx ||
                    metadata.heightPx != output.heightPx ||
                    metadata.pixelFormat != output.pixelFormat ||
                    metadata.imageEncoding != output.imageEncoding ||
                    metadata.colorSpace != output.colorSpace ||
                    metadata.alphaMode != output.alphaMode
                ) errors += "output artifact metadata or SHA-256 does not match the verified bytes"
            }

            totalOutputBytes = safeAdd(totalOutputBytes, output.byteCount, errors)
            if (output.retainedForReuse) {
                totalReusableBytes = safeAdd(totalReusableBytes, output.byteCount, errors)
            }
            outputsByLayer[output.layerId]?.add(output)
        }

        outputsByLayer.forEach { (layerId, layerOutputs) ->
            if (layerOutputs.size != request.timeline.frameCount) {
                errors += "layer $layerId does not contain the complete requested frame range"
            } else {
                val sorted = layerOutputs.map { it.frameIndex }.sorted()
                sorted.forEachIndexed { offset, frameIndex ->
                    val expectedFrame = request.timeline.startFrameIndex + offset.toLong()
                    if (frameIndex != expectedFrame) {
                        errors += "layer $layerId has a discontinuous frame range"
                        return@forEachIndexed
                    }
                }
            }
        }

        if (totalOutputBytes > request.resourceEnvelope.maxTemporaryStorageBytes) {
            errors += "total output bytes exceed temporary storage envelope"
        }
        if (totalReusableBytes > request.resourceEnvelope.maxReusableOutputBytes) {
            errors += "reusable output bytes exceed reusable output envelope"
        }
        if (usage != null && usage.reusableOutputBytes != totalReusableBytes) {
            errors += "reusableOutputBytes does not equal the verified retained output bytes"
        }
        return WolfSceneContractValidation(errors.distinct())
    }

    private fun safeAdd(current: Long, addition: Long, errors: MutableList<String>): Long =
        try {
            Math.addExact(current, addition)
        } catch (_: ArithmeticException) {
            errors += "output byte total overflowed"
            Long.MAX_VALUE
        }
}

private fun String.isSceneIdentifier(): Boolean =
    length in 1..WolfSceneContractLimits.MAX_IDENTIFIER_LENGTH &&
        WolfSceneContractPatterns.identifier.matches(this)

private fun String.isSha256Digest(): Boolean =
    length == 64 && WolfSceneContractPatterns.sha256.matches(this)

private fun String.isSceneArtifactReference(): Boolean {
    if (length !in (SCENE_ARTIFACT_PREFIX.length + 1)..WolfSceneContractLimits.MAX_ARTIFACT_REFERENCE_LENGTH) {
        return false
    }
    if (!startsWith(SCENE_ARTIFACT_PREFIX)) return false
    return substring(SCENE_ARTIFACT_PREFIX.length).isSceneIdentifier()
}
