package com.tape.measure.domain.measure

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class AccuracyEstimatorTest {

    @Test
    fun planePointUncertainty_growsWithCameraDistance() {
        assertEquals(0.015f, AccuracyEstimator.pointUncertainty(HitKind.PLANE_HORIZONTAL, 1f), 1e-6f)
        assertEquals(0.03f, AccuracyEstimator.pointUncertainty(HitKind.PLANE_VERTICAL, 2f), 1e-6f)
    }

    @Test
    fun depthPoints_areNoisierThanPlanes() {
        assertEquals(0.05f, AccuracyEstimator.pointUncertainty(HitKind.DEPTH, 2f), 1e-6f)
    }

    @Test
    fun closePoints_neverClaimLessThanTheFloor() {
        assertEquals(
            AccuracyEstimator.MIN_POINT_ERROR,
            AccuracyEstimator.pointUncertainty(HitKind.PLANE_HORIZONTAL, 0.05f),
            1e-6f,
        )
    }

    @Test
    fun endpointErrors_combineAsRootSumSquare() {
        assertEquals(0.05f, AccuracyEstimator.estimate(1f, 0.03f, 0.04f).uncertaintyMeters, 1e-6f)
    }

    @Test
    fun level_followsRelativeUncertainty() {
        assertEquals(AccuracyLevel.GOOD, AccuracyEstimator.estimate(1f, 0.01f, 0.01f).level) // 1.4 %
        assertEquals(AccuracyLevel.FAIR, AccuracyEstimator.estimate(1f, 0.02f, 0.02f).level) // 2.8 %
        assertEquals(AccuracyLevel.POOR, AccuracyEstimator.estimate(0.1f, 0.01f, 0.01f).level) // 14 %
    }

    @Test
    fun walking_addsOnePercentOfTheDistanceWalked() {
        assertEquals(0.02f, AccuracyEstimator.withDrift(0.02f, walkedMeters = 0f), 1e-6f)
        // ±2 cm at the end point, ±10 cm from walking 10 m.
        assertEquals(sqrt(0.02f * 0.02f + 0.1f * 0.1f), AccuracyEstimator.withDrift(0.02f, walkedMeters = 10f), 1e-6f)
    }

    @Test
    fun zeroLength_isPoor() {
        assertEquals(AccuracyLevel.POOR, AccuracyEstimator.estimate(0f, 0.003f, 0.003f).level)
    }
}
