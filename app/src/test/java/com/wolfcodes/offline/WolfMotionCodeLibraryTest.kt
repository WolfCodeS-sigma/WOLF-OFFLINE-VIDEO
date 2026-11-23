package com.wolfcodes.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WolfMotionCodeLibraryTest {
    @Test
    fun scalarCurveUsesLinearInterpolationAndClampsOutsideItsDomain() {
        val curve = WolfScalarCurve(
            listOf(WolfScalarKeyframe(0.2, 10.0), WolfScalarKeyframe(0.8, 40.0))
        )
        assertEquals(10.0, curve.sample(-5.0), 0.000001)
        assertEquals(25.0, curve.sample(0.5), 0.000001)
        assertEquals(40.0, curve.sample(1.0), 0.000001)
    }

    @Test
    fun smoothStepAndStepCurvesHaveStableBoundarySemantics() {
        val step = WolfScalarCurve(
            listOf(WolfScalarKeyframe(0.0, 2.0), WolfScalarKeyframe(0.5, 9.0)),
            WolfCurveInterpolation.STEP
        )
        val smooth = WolfScalarCurve(
            listOf(WolfScalarKeyframe(0.0, 0.0), WolfScalarKeyframe(1.0, 1.0)),
            WolfCurveInterpolation.SMOOTHSTEP
        )
        assertEquals(2.0, step.sample(0.499), 0.000001)
        assertEquals(9.0, step.sample(0.5), 0.000001)
        assertEquals(0.5, smooth.sample(0.5), 0.000001)
        assertEquals(0.0, WolfMotionCodeLibrary.smoothStep(-1.0), 0.0)
        assertEquals(1.0, WolfMotionCodeLibrary.smoothStep(2.0), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scalarCurveRejectsDuplicateKeyframePositions() {
        WolfScalarCurve(listOf(WolfScalarKeyframe(0.5, 1.0), WolfScalarKeyframe(0.5, 2.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scalarCurveRejectsTooManyKeyframes() {
        WolfScalarCurve((0..WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES).map {
            WolfScalarKeyframe(it.toDouble() / WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES, it.toDouble())
        })
    }

    @Test
    fun vectorCurveAndQuaternionInterpolationAreDeterministic() {
        val curve = WolfVector3Curve(
            listOf(
                WolfVector3Keyframe(0.0, WolfVector3.ZERO),
                WolfVector3Keyframe(1.0, WolfVector3(10.0, 4.0, -2.0))
            )
        )
        assertEquals(WolfVector3(5.0, 2.0, -1.0), curve.sample(0.5))

        val quarterTurn = WolfQuaternion(0.0, 0.0, kotlin.math.sin(Math.PI / 4.0), kotlin.math.cos(Math.PI / 4.0))
        val rotated = quarterTurn.rotate(WolfVector3.UNIT_X)
        assertEquals(0.0, rotated.x, 0.000001)
        assertEquals(1.0, rotated.y, 0.000001)

        val quaternionCurve = WolfQuaternionCurve(
            listOf(
                WolfQuaternionKeyframe(0.0, WolfQuaternion.IDENTITY),
                WolfQuaternionKeyframe(1.0, quarterTurn)
            )
        )
        assertEquals(
            WolfQuaternion.IDENTITY.slerp(quarterTurn, 0.5),
            quaternionCurve.sample(0.5)
        )
    }

    @Test
    fun quaternionCurveUsesBinarySearchAcrossMultipleSegments() {
        val quarterTurn = WolfQuaternion(0.0, 0.0, kotlin.math.sin(Math.PI / 4.0), kotlin.math.cos(Math.PI / 4.0))
        val halfTurn = WolfQuaternion(0.0, 0.0, 1.0, 0.0)
        val curve = WolfQuaternionCurve(
            listOf(
                WolfQuaternionKeyframe(0.0, WolfQuaternion.IDENTITY),
                WolfQuaternionKeyframe(0.5, quarterTurn),
                WolfQuaternionKeyframe(1.0, halfTurn)
            )
        )
        assertEquals(quarterTurn.normalized(), curve.sample(0.5))
        assertEquals(quarterTurn.slerp(halfTurn, 0.5), curve.sample(0.75))
    }

    @Test
    fun transformSupportsNegativeScaleForMirroring() {
        val mirrored = WolfTransform3(scale = WolfVector3(-1.0, 1.0, -1.0))
        assertEquals(WolfVector3(-2.0, 3.0, -4.0), mirrored.applyToPoint(WolfVector3(2.0, 3.0, 4.0)))
    }

    @Test
    fun transformAppliesScaleThenRotationThenTranslation() {
        val transform = WolfTransform3(
            translation = WolfVector3(3.0, 0.0, 0.0),
            rotation = WolfQuaternion.IDENTITY,
            scale = WolfVector3(2.0, 2.0, 2.0)
        )
        assertEquals(WolfVector3(5.0, 2.0, 0.0), transform.applyToPoint(WolfVector3(1.0, 1.0, 0.0)))
    }

    @Test
    fun rationalFrameClockProducesStableMicrosecondPts() {
        assertEquals(0L, WolfMotionCodeLibrary.framePresentationTimeUs(0L, 24L, 1L))
        assertEquals(1_000_000L, WolfMotionCodeLibrary.framePresentationTimeUs(24L, 24L, 1L))
        assertEquals(41_667L, WolfMotionCodeLibrary.framePresentationTimeUs(1L, 24L, 1L))
        assertEquals(
            5_772_433L,
            WolfMotionCodeLibrary.framePresentationTimeUs(173L, 30_000L, 1_001L)
        )
    }

    @Test
    fun rationalClockRejectsTimestampOverflowWithoutApiLevelSpecificExactMethod() {
        try {
            WolfMotionCodeLibrary.framePresentationTimeUs(Long.MAX_VALUE, 1L, Long.MAX_VALUE)
            throw AssertionError("Expected timestamp overflow rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Long range"))
        }
    }

    @Test
    fun vectorCurveSamplesAllComponentsWithSharedInterpolationRules() {
        val smooth = WolfVector3Curve(
            listOf(
                WolfVector3Keyframe(0.0, WolfVector3.ZERO),
                WolfVector3Keyframe(1.0, WolfVector3(10.0, 20.0, 30.0))
            ),
            WolfCurveInterpolation.SMOOTHSTEP
        )
        assertEquals(WolfVector3(5.0, 10.0, 15.0), smooth.sample(0.5))
        assertEquals(WolfVector3.ZERO, smooth.sample(-1.0))
        assertEquals(WolfVector3(10.0, 20.0, 30.0), smooth.sample(2.0))
    }

    @Test
    fun vectorCurveRejectsOversizedInputBeforeReadingOrCopyingIt() {
        val oversized = object : AbstractList<WolfVector3Keyframe>() {
            override val size: Int = WolfMotionCodeLibrary.MAX_CURVE_KEYFRAMES + 1
            override fun get(index: Int): WolfVector3Keyframe =
                throw AssertionError("Oversized keyframes must be rejected before iteration")
        }
        try {
            WolfVector3Curve(oversized)
            throw AssertionError("Expected oversized curve rejection")
        } catch (_: IllegalArgumentException) {
            // Expected: size is checked before ArrayList copies or iterates the source.
        }
    }

    @Test
    fun vectorCurveCanSampleIntoReusableScratchStorage() {
        val curve = WolfVector3Curve(
            listOf(
                WolfVector3Keyframe(0.0, WolfVector3(1.0, 2.0, 3.0)),
                WolfVector3Keyframe(1.0, WolfVector3(9.0, 6.0, -1.0))
            ),
            WolfCurveInterpolation.SMOOTHSTEP
        )
        val scratch = DoubleArray(3)
        curve.sampleInto(0.5, scratch)
        assertEquals(5.0, scratch[0], 0.000001)
        assertEquals(4.0, scratch[1], 0.000001)
        assertEquals(1.0, scratch[2], 0.000001)
        curve.sampleInto(1.0, scratch)
        assertEquals(9.0, scratch[0], 0.000001)
        assertEquals(6.0, scratch[1], 0.000001)
        assertEquals(-1.0, scratch[2], 0.000001)
    }

    @Test
    fun scalarCurveUsesBinarySearchAcrossMultipleUnevenSegments() {
        val keyframes = listOf(
            WolfScalarKeyframe(0.0, 0.0),
            WolfScalarKeyframe(0.25, 10.0),
            WolfScalarKeyframe(0.75, 30.0),
            WolfScalarKeyframe(1.0, 40.0)
        )

        val linear = WolfScalarCurve(keyframes, WolfCurveInterpolation.LINEAR)
        assertEquals(5.0, linear.sample(0.125), 0.000001)
        assertEquals(10.0, linear.sample(0.25), 0.000001)
        assertEquals(20.0, linear.sample(0.5), 0.000001)
        assertEquals(35.0, linear.sample(0.875), 0.000001)

        val step = WolfScalarCurve(keyframes, WolfCurveInterpolation.STEP)
        assertEquals(0.0, step.sample(0.125), 0.000001)
        assertEquals(10.0, step.sample(0.25), 0.000001)
        assertEquals(10.0, step.sample(0.5), 0.000001)
        assertEquals(30.0, step.sample(0.875), 0.000001)

        val smooth = WolfScalarCurve(keyframes, WolfCurveInterpolation.SMOOTHSTEP)
        assertEquals(5.0, smooth.sample(0.125), 0.000001)
        assertEquals(13.125, smooth.sample(0.375), 0.000001)
        assertEquals(35.0, smooth.sample(0.875), 0.000001)
        assertEquals(0.0, smooth.sample(-1.0), 0.000001)
        assertEquals(40.0, smooth.sample(2.0), 0.000001)
    }

    @Test
    fun vectorCurveScratchSamplingMatchesMultiSegmentSamplingForEveryInterpolation() {
        val keyframes = listOf(
            WolfVector3Keyframe(0.0, WolfVector3(0.0, 10.0, -10.0)),
            WolfVector3Keyframe(0.25, WolfVector3(8.0, 2.0, -2.0)),
            WolfVector3Keyframe(0.75, WolfVector3(16.0, 14.0, 6.0)),
            WolfVector3Keyframe(1.0, WolfVector3(20.0, 18.0, 10.0))
        )
        val samplePositions = listOf(-0.25, 0.0, 0.125, 0.25, 0.375, 0.5, 0.75, 0.875, 1.0, 1.25)

        WolfCurveInterpolation.values().forEach { interpolation ->
            val curve = WolfVector3Curve(keyframes, interpolation)
            val scratch = DoubleArray(4) { -123.0 }

            samplePositions.forEach { progress ->
                val expected = curve.sample(progress)
                curve.sampleInto(progress, scratch)
                assertEquals("$interpolation x at $progress", expected.x, scratch[0], 0.000001)
                assertEquals("$interpolation y at $progress", expected.y, scratch[1], 0.000001)
                assertEquals("$interpolation z at $progress", expected.z, scratch[2], 0.000001)
                assertEquals("scratch capacity beyond Vector3 is untouched", -123.0, scratch[3], 0.0)
            }
        }
    }

    @Test
    fun vectorOperationsPreserveExpectedGeometry() {
        val first = WolfVector3(1.0, 0.0, 0.0)
        val second = WolfVector3(0.0, 1.0, 0.0)
        assertEquals(WolfVector3.UNIT_Z, first.cross(second))
        assertEquals(0.0, first.dot(second), 0.0)
        assertEquals(1.0, first.normalized().length(), 0.000001)
        assertNotEquals(first, second)
        assertTrue(first.lerp(second, 0.5).length() > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun motionClockRejectsInvalidFrameRate() {
        WolfMotionCodeLibrary.framePresentationTimeUs(1L, 0L, 1L)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun curveKeyframesAreDefensivelyCopiedAndUnmodifiable() {
        val source = mutableListOf(WolfScalarKeyframe(0.0, 1.0), WolfScalarKeyframe(1.0, 2.0))
        val curve = WolfScalarCurve(source)
        source.clear()
        assertEquals(2, curve.keyframes.size)
        (curve.keyframes as MutableList).clear()
    }
}
