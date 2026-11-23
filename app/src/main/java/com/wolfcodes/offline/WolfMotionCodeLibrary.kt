package com.wolfcodes.offline

import java.math.BigInteger
import java.util.Collections
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Engine-neutral deterministic motion primitives shared by WOLF and renderer adapters.
 *
 * This module does not allocate film/scene frames, choose camera intent, render pixels,
 * or depend on Android or any external engine.
 */
object WolfMotionCodeLibrary {
    const val API_VERSION = "1.2.0"
    const val MAX_CURVE_KEYFRAMES = 256

    private val MICROSECONDS_PER_SECOND = BigInteger.valueOf(1_000_000L)
    private val TWO = BigInteger.valueOf(2L)
    private val MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE)

    fun clampUnit(value: Double): Double {
        require(value.isFinite()) { "Motion progress must be finite" }
        return value.coerceIn(0.0, 1.0)
    }

    fun lerp(start: Double, end: Double, progress: Double): Double {
        require(start.isFinite() && end.isFinite() && progress.isFinite()) {
            "Lerp inputs must be finite"
        }
        val value = start + (end - start) * progress
        require(value.isFinite()) { "Lerp result must be finite" }
        return value
    }

    fun smoothStep(progress: Double): Double {
        val t = clampUnit(progress)
        return t * t * (3.0 - 2.0 * t)
    }

    /**
     * Converts an absolute frame index to microsecond PTS using rational FPS.
     * Rounding is nearest-microsecond, half-up. Avoids BigInteger.longValueExact(),
     * which is unavailable on some supported Android API levels.
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
        val rounded = numerator.add(divisor.divide(TWO)).divide(divisor)
        require(rounded.signum() >= 0 && rounded <= MAX_LONG) {
            "Frame presentation timestamp exceeds the supported Long range"
        }
        return rounded.toLong()
    }

    /**
     * Checks caller-controlled collection size before allocating a defensive copy.
     * Keep this check before ArrayList(items) so oversized input fails closed.
     */
    internal fun <T> immutableCopy(
        items: List<T>,
        maxSize: Int = Int.MAX_VALUE,
        description: String = "Collection"
    ): List<T> {
        require(maxSize >= 0) { "Maximum collection size cannot be negative" }
        require(items.size <= maxSize) { "$description exceeds the maximum size of $maxSize" }
        return Collections.unmodifiableList(ArrayList(items))
    }
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

/** Immutable normalized scalar curve. Positions are in [0,1] and strictly increasing. */
class WolfScalarCurve(
    keyframes: List<WolfScalarKeyframe>,
    val interpolation: WolfCurveInterpolation = WolfCurveInterpolation.LINEAR
) {
    val keyframes: List<WolfScalarKeyframe> = WolfMotionCodeLibrary.immutableCopy(
        keyframes, WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES, "Scalar curve keyframes"
    )

    init {
        require(this.keyframes.isNotEmpty()) { "A motion curve needs at least one keyframe" }
        require(this.keyframes.size <= WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES) {
            "A motion curve exceeds the maximum keyframe count"
        }
        require(this.keyframes.all { it.position.isFinite() && it.value.isFinite() }) {
            "Motion keyframes must contain finite values"
        }
        require(this.keyframes.all { it.position in 0.0..1.0 }) {
            "Motion keyframe positions must be normalized to 0..1"
        }
        require(this.keyframes.zipWithNext().all { (left, right) -> left.position < right.position }) {
            "Motion keyframes must be strictly ordered without duplicate positions"
        }
    }

    /** Samples a normalized curve; values outside its domain hold the nearest endpoint. */
    fun sample(progress: Double): Double {
        val t = WolfMotionCodeLibrary.clampUnit(progress)
        if (keyframes.size == 1 || t <= keyframes.first().position) return keyframes.first().value
        if (t >= keyframes.last().position) return keyframes.last().value

        var low = 1
        var high = keyframes.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (keyframes[middle].position >= t) high = middle else low = middle + 1
        }
        val right = keyframes[low]
        if (t == right.position) return right.value
        val left = keyframes[low - 1]
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

    fun length(): Double {
        val squared = dot(this)
        require(squared.isFinite() && squared >= 0.0) { "Vector length is outside the supported numeric range" }
        return sqrt(squared)
    }

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

/** Immutable normalized vector curve. */
class WolfVector3Curve(
    keyframes: List<WolfVector3Keyframe>,
    val interpolation: WolfCurveInterpolation = WolfCurveInterpolation.LINEAR
) {
    val keyframes: List<WolfVector3Keyframe> = WolfMotionCodeLibrary.immutableCopy(
        keyframes, WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES, "Vector3 curve keyframes"
    )

    init {
        require(this.keyframes.isNotEmpty()) { "A vector curve needs at least one keyframe" }
        require(this.keyframes.size <= WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES) {
            "A vector curve exceeds the maximum keyframe count"
        }
        require(this.keyframes.all { it.position.isFinite() && it.position in 0.0..1.0 }) {
            "Vector keyframe positions must be finite and normalized to 0..1"
        }
        require(this.keyframes.zipWithNext().all { (left, right) -> left.position < right.position }) {
            "Vector keyframes must be strictly ordered without duplicate positions"
        }
    }

    /**
     * Samples all three components from the same segment. This avoids constructing
     * three scalar curves and three mapped keyframe lists for every rendered frame.
     */
    fun sample(progress: Double): WolfVector3 {
        val t = WolfMotionCodeLibrary.clampUnit(progress)
        val first = keyframes[0]
        val last = keyframes[keyframes.lastIndex]
        if (keyframes.size == 1 || t <= first.position) return first.value
        if (t >= last.position) return last.value

        var low = 1
        var high = keyframes.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (keyframes[middle].position >= t) high = middle else low = middle + 1
        }

        val right = keyframes[low]
        if (t == right.position) return right.value
        val left = keyframes[low - 1]
        if (interpolation == WolfCurveInterpolation.STEP) return left.value

        val local = (t - left.position) / (right.position - left.position)
        val resolved = when (interpolation) {
            WolfCurveInterpolation.STEP -> 0.0
            WolfCurveInterpolation.LINEAR -> local
            WolfCurveInterpolation.SMOOTHSTEP -> WolfMotionCodeLibrary.smoothStep(local)
        }
        return WolfVector3(
            WolfMotionCodeLibrary.lerp(left.value.x, right.value.x, resolved),
            WolfMotionCodeLibrary.lerp(left.value.y, right.value.y, resolved),
            WolfMotionCodeLibrary.lerp(left.value.z, right.value.z, resolved)
        )
    }
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
        require(magnitude.isFinite() && magnitude > WolfVector3.EPSILON) {
            "Quaternion magnitude must be finite and non-zero"
        }
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

data class WolfQuaternionKeyframe(
    val position: Double,
    val value: WolfQuaternion
)

/** Immutable normalized quaternion curve using shortest-path spherical interpolation. */
class WolfQuaternionCurve(
    keyframes: List<WolfQuaternionKeyframe>
) {
    val keyframes: List<WolfQuaternionKeyframe> = WolfMotionCodeLibrary.immutableCopy(
        keyframes, WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES, "Quaternion curve keyframes"
    )

    init {
        require(this.keyframes.isNotEmpty()) { "A quaternion curve needs at least one keyframe" }
        require(this.keyframes.size <= WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES) {
            "A quaternion curve exceeds the maximum keyframe count"
        }
        require(this.keyframes.all { it.position.isFinite() && it.position in 0.0..1.0 }) {
            "Quaternion keyframe positions must be finite and normalized to 0..1"
        }
        require(this.keyframes.zipWithNext().all { (left, right) -> left.position < right.position }) {
            "Quaternion keyframes must be strictly ordered without duplicate positions"
        }
        this.keyframes.forEach { it.value.normalized() }
    }

    fun sample(progress: Double): WolfQuaternion {
        val t = WolfMotionCodeLibrary.clampUnit(progress)
        if (keyframes.size == 1 || t <= keyframes.first().position) return keyframes.first().value.normalized()
        if (t >= keyframes.last().position) return keyframes.last().value.normalized()

        var low = 1
        var high = keyframes.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (keyframes[middle].position >= t) high = middle else low = middle + 1
        }
        val right = keyframes[low]
        if (t == right.position) return right.value.normalized()
        val left = keyframes[low - 1]
        val local = (t - left.position) / (right.position - left.position)
        return left.value.slerp(right.value, local)
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
        rotation.normalized()
    }

    /** Applies scale, then rotation, then translation. */
    fun applyToPoint(point: WolfVector3): WolfVector3 {
        val scaled = WolfVector3(point.x * scale.x, point.y * scale.y, point.z * scale.z)
        return rotation.rotate(scaled) + translation
    }
}
