package com.wolfcodes.offline

import java.math.BigInteger
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Engine-neutral deterministic motion primitives shared by WOLF and renderer adapters.
 *
 * This module deliberately does not allocate film/scene frames, choose camera intent,
 * render pixels, or depend on Android or any external engine.
 */
object WolfMotionCodeLibrary {
    const val API_VERSION = "1.0.0"

    fun clampUnit(value: Double): Double {
        require(value.isFinite()) { "Motion progress must be finite" }
        return value.coerceIn(0.0, 1.0)
    }

    fun lerp(start: Double, end: Double, progress: Double): Double {
        require(start.isFinite() && end.isFinite() && progress.isFinite()) {
            "Lerp inputs must be finite"
        }
        return start + (end - start) * progress
    }

    fun smoothStep(progress: Double): Double {
        val t = clampUnit(progress)
        return t * t * (3.0 - 2.0 * t)
    }

    /**
     * Converts an absolute frame index to microsecond PTS using rational FPS.
     * Rounding is nearest-microsecond, half-up; no floating-point clock is involved.
     */
    fun framePresentationTimeUs(
        absoluteFrameIndex: Long,
        frameRateNumerator: Long,
        frameRateDenominator: Long
    ): Long {
        require(absoluteFrameIndex >= 0L) { "Frame index cannot be negative" }
        require(frameRateNumerator > 0L && frameRateDenominator > 0L) {
            "Frame rate must be a positive rational number"
        }
        val numerator = BigInteger.valueOf(absoluteFrameIndex)
            .multiply(BigInteger.valueOf(frameRateDenominator))
            .multiply(MICROSECONDS_PER_SECOND)
        val divisor = BigInteger.valueOf(frameRateNumerator)
        return numerator.add(divisor.divide(TWO)).divide(divisor).longValueExact()
    }

    private val MICROSECONDS_PER_SECOND = BigInteger.valueOf(1_000_000L)
    private val TWO = BigInteger.valueOf(2L)
}

enum class WolfCurveInterpolation {
    STEP,
    LINEAR,
    SMOOTHSTEP
}

data class WolfScalarKeyframe(
    val position: Double,
    val value: Double
)

data class WolfScalarCurve(
    val keyframes: List<WolfScalarKeyframe>,
    val interpolation: WolfCurveInterpolation = WolfCurveInterpolation.LINEAR
) {
    init {
        require(keyframes.isNotEmpty()) { "A motion curve needs at least one keyframe" }
        require(keyframes.all { it.position.isFinite() && it.value.isFinite() }) {
            "Motion keyframes must contain finite values"
        }
        require(keyframes.all { it.position in 0.0..1.0 }) {
            "Motion keyframe positions must be normalized to 0..1"
        }
        require(keyframes.zipWithNext().all { (left, right) -> left.position < right.position }) {
            "Motion keyframes must be strictly ordered without duplicate positions"
        }
    }

    /** Samples a normalized curve; values outside its domain hold the nearest endpoint. */
    fun sample(progress: Double): Double {
        val t = WolfMotionCodeLibrary.clampUnit(progress)
        if (keyframes.size == 1 || t <= keyframes.first().position) {
            return keyframes.first().value
        }
        if (t >= keyframes.last().position) return keyframes.last().value

        val rightIndex = keyframes.indexOfFirst { it.position >= t }
        val right = keyframes[rightIndex]
        if (t == right.position) return right.value
        val left = keyframes[rightIndex - 1]
        if (interpolation == WolfCurveInterpolation.STEP) return left.value

        val local = (t - left.position) / (right.position - left.position)
        val resolved = when (interpolation) {
            WolfCurveInterpolation.STEP -> 0.0
            WolfCurveInterpolation.LINEAR -> local
            WolfCurveInterpolation.SMOOTHSTEP -> WolfMotionCodeLibrary.smoothStep(local)
        }
        return WolfMotionCodeLibrary.lerp(left.value, right.value, resolved)
    }
}

/** Immutable renderer-neutral three-dimensional vector. */
data class WolfVector3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) {
            "Vector components must be finite"
        }
    }

    operator fun plus(other: WolfVector3) = WolfVector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: WolfVector3) = WolfVector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double): WolfVector3 {
        require(scale.isFinite()) { "Vector scale must be finite" }
        return WolfVector3(x * scale, y * scale, z * scale)
    }

    fun dot(other: WolfVector3): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: WolfVector3): WolfVector3 = WolfVector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Double = sqrt(dot(this))

    fun normalized(): WolfVector3 {
        val magnitude = length()
        require(magnitude > EPSILON) { "A zero-length vector cannot be normalized" }
        return this * (1.0 / magnitude)
    }

    fun lerp(other: WolfVector3, progress: Double): WolfVector3 {
        require(progress.isFinite()) { "Vector progress must be finite" }
        return WolfVector3(
            WolfMotionCodeLibrary.lerp(x, other.x, progress),
            WolfMotionCodeLibrary.lerp(y, other.y, progress),
            WolfMotionCodeLibrary.lerp(z, other.z, progress)
        )
    }

    companion object {
        const val EPSILON = 1.0e-9
        val ZERO = WolfVector3(0.0, 0.0, 0.0)
        val UNIT_X = WolfVector3(1.0, 0.0, 0.0)
        val UNIT_Y = WolfVector3(0.0, 1.0, 0.0)
        val UNIT_Z = WolfVector3(0.0, 0.0, 1.0)
    }
}

data class WolfVector3Keyframe(
    val position: Double,
    val value: WolfVector3
)

data class WolfVector3Curve(
    val keyframes: List<WolfVector3Keyframe>,
    val interpolation: WolfCurveInterpolation = WolfCurveInterpolation.LINEAR
) {
    init {
        require(keyframes.isNotEmpty()) { "A vector curve needs at least one keyframe" }
        require(keyframes.all { it.position.isFinite() && it.position in 0.0..1.0 }) {
            "Vector keyframe positions must be finite and normalized to 0..1"
        }
        require(keyframes.zipWithNext().all { (left, right) -> left.position < right.position }) {
            "Vector keyframes must be strictly ordered without duplicate positions"
        }
    }

    fun sample(progress: Double): WolfVector3 = WolfVector3(
        WolfScalarCurve(keyframes.map { WolfScalarKeyframe(it.position, it.value.x) }, interpolation).sample(progress),
        WolfScalarCurve(keyframes.map { WolfScalarKeyframe(it.position, it.value.y) }, interpolation).sample(progress),
        WolfScalarCurve(keyframes.map { WolfScalarKeyframe(it.position, it.value.z) }, interpolation).sample(progress)
    )
}

/** Quaternion uses x/y/z vector components and w scalar component. */
data class WolfQuaternion(
    val x: Double,
    val y: Double,
    val z: Double,
    val w: Double
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()) {
            "Quaternion components must be finite"
        }
    }

    fun normalized(): WolfQuaternion {
        val magnitude = sqrt(x * x + y * y + z * z + w * w)
        require(magnitude > WolfVector3.EPSILON) { "A zero quaternion cannot be normalized" }
        return WolfQuaternion(x / magnitude, y / magnitude, z / magnitude, w / magnitude)
    }

    fun rotate(vector: WolfVector3): WolfVector3 {
        val q = normalized()
        val imaginary = WolfVector3(q.x, q.y, q.z)
        val twiceCross = imaginary.cross(vector) * 2.0
        return vector + twiceCross * q.w + imaginary.cross(twiceCross)
    }

    fun slerp(other: WolfQuaternion, progress: Double): WolfQuaternion {
        require(progress.isFinite()) { "Quaternion progress must be finite" }
        val amount = WolfMotionCodeLibrary.clampUnit(progress)
        val left = normalized()
        var right = other.normalized()
        var cosine = left.x * right.x + left.y * right.y + left.z * right.z + left.w * right.w

        if (cosine < 0.0) {
            right = WolfQuaternion(-right.x, -right.y, -right.z, -right.w)
            cosine = -cosine
        }

        if (cosine > 0.9995) {
            return WolfQuaternion(
                WolfMotionCodeLibrary.lerp(left.x, right.x, amount),
                WolfMotionCodeLibrary.lerp(left.y, right.y, amount),
                WolfMotionCodeLibrary.lerp(left.z, right.z, amount),
                WolfMotionCodeLibrary.lerp(left.w, right.w, amount)
            ).normalized()
        }

        val angle = acos(cosine.coerceIn(-1.0, 1.0))
        val sine = sin(angle)
        val leftWeight = sin((1.0 - amount) * angle) / sine
        val rightWeight = sin(amount * angle) / sine
        return WolfQuaternion(
            left.x * leftWeight + right.x * rightWeight,
            left.y * leftWeight + right.y * rightWeight,
            left.z * leftWeight + right.z * rightWeight,
            left.w * leftWeight + right.w * rightWeight
        ).normalized()
    }

    companion object {
        val IDENTITY = WolfQuaternion(0.0, 0.0, 0.0, 1.0)
    }
}

data class WolfTransform3(
    val translation: WolfVector3 = WolfVector3.ZERO,
    val rotation: WolfQuaternion = WolfQuaternion.IDENTITY,
    val scale: WolfVector3 = WolfVector3(1.0, 1.0, 1.0)
) {
    init {
        require(
            kotlin.math.abs(scale.x) > WolfVector3.EPSILON &&
                kotlin.math.abs(scale.y) > WolfVector3.EPSILON &&
                kotlin.math.abs(scale.z) > WolfVector3.EPSILON
        ) { "Transform scale components cannot be zero" }
    }

    fun applyToPoint(point: WolfVector3): WolfVector3 {
        val scaled = WolfVector3(point.x * scale.x, point.y * scale.y, point.z * scale.z)
        return rotation.rotate(scaled) + translation
    }
}
