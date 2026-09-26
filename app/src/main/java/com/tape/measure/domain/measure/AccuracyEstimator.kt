package com.tape.measure.domain.measure

import kotlin.math.sqrt

/** What a measuring point was placed on: a surface ARCore hit, or an estimate from afar. */
enum class HitKind {
    PLANE_HORIZONTAL,
    PLANE_VERTICAL,
    DEPTH,

    /** Estimated on the ground far away, see [FarTargeting.groundTarget]. */
    GROUND_FAR,

    /** Estimated straight above the start point, see [FarTargeting.verticalTarget]. */
    VERTICAL_FAR,
    ;

    /** True for points estimated by [FarTargeting] rather than hit by ARCore. */
    val isFar: Boolean get() = this == GROUND_FAR || this == VERTICAL_FAR
}

enum class AccuracyLevel { GOOD, FAIR, POOR }

/** Estimated uncertainty of a measured length (one sigma, metres) and its rating. */
data class AccuracyEstimate(val uncertaintyMeters: Float, val level: AccuracyLevel)

/**
 * Heuristic accuracy model for a two-point measurement.
 *
 * Each endpoint contributes an error proportional to how far the camera was from it when it
 * was placed: ARCore's plane fits and depth estimates degrade roughly linearly with range.
 * The two endpoint errors are treated as independent and combined as root-sum-square.
 *
 * The coefficients are conservative starting values, not calibrated figures. They have to be
 * checked against reference lengths on real devices (improvement plan, phase 2).
 */
object AccuracyEstimator {

    /** Endpoint error per metre of camera distance for points on a detected plane. */
    const val PLANE_ERROR_PER_METER = 0.015f

    /** Depth hits are noisier than fitted planes. */
    const val DEPTH_ERROR_PER_METER = 0.025f

    /** Lower bound per endpoint, however close the camera is. */
    const val MIN_POINT_ERROR = 0.003f

    /**
     * ARCore's tracking drifts while the user walks between placing two points; the end point
     * gains this much uncertainty per metre walked.
     */
    const val DRIFT_PER_METER_WALKED = 0.01f

    /** Relative uncertainty up to which a measurement counts as [AccuracyLevel.GOOD]. */
    const val GOOD_RELATIVE = 0.02f

    /** Relative uncertainty up to which a measurement counts as [AccuracyLevel.FAIR]. */
    const val FAIR_RELATIVE = 0.05f

    /** For points on a surface ARCore hit; [FarTargeting] estimates its own points' errors. */
    fun pointUncertainty(kind: HitKind, cameraDistanceMeters: Float): Float {
        val perMeter = if (kind == HitKind.DEPTH) DEPTH_ERROR_PER_METER else PLANE_ERROR_PER_METER
        return maxOf(MIN_POINT_ERROR, perMeter * cameraDistanceMeters)
    }

    /** The uncertainty of a point placed after walking [walkedMeters] since the start point. */
    fun withDrift(uncertainty: Float, walkedMeters: Float): Float {
        val drift = DRIFT_PER_METER_WALKED * walkedMeters
        return sqrt(uncertainty * uncertainty + drift * drift)
    }

    fun estimate(lengthMeters: Float, startUncertainty: Float, endUncertainty: Float): AccuracyEstimate {
        val uncertainty = sqrt(startUncertainty * startUncertainty + endUncertainty * endUncertainty)
        val relative = if (lengthMeters > 0f) uncertainty / lengthMeters else Float.POSITIVE_INFINITY
        val level = when {
            relative <= GOOD_RELATIVE -> AccuracyLevel.GOOD
            relative <= FAIR_RELATIVE -> AccuracyLevel.FAIR
            else -> AccuracyLevel.POOR
        }
        return AccuracyEstimate(uncertainty, level)
    }
}
