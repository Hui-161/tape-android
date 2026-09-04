package com.tape.measure.ui.screens.measure

import androidx.compose.ui.geometry.Offset
import com.tape.measure.domain.model.UnitSystem

/**
 * What stage the user is in for the current measurement.
 *
 *  IDLE        – no anchors placed; showing "tap to place first point" hint
 *  POINT_A     – first anchor placed; waiting for second tap
 *  BOTH        – both anchors placed; distance shown; tap resets
 */
enum class MeasurePhase { IDLE, POINT_A, BOTH }

/**
 * Reflects the ARCore camera tracking quality.
 *
 *  NOT_TRACKING – session paused or stopped; can't place points
 *  LOW          – tracking is shaky / recovering; accuracy worse
 *  HIGH         – full TRACKING state; best accuracy
 */
enum class TrackingConfidence {
    NOT_TRACKING,
    LOW,
    HIGH;

    /** Short label shown in the confidence badge. */
    fun label(): String = when (this) {
        NOT_TRACKING -> "No tracking"
        LOW          -> "Low accuracy"
        HIGH         -> "Good tracking"
    }

    /**
     * Approximate accuracy range to display next to the badge.
     * Values are informed by ARCore's typical plane-detection accuracy
     * at normal distances (0.5–3 m from a flat surface).
     */
    fun accuracyHint(): String = when (this) {
        NOT_TRACKING -> "–"
        LOW          -> "±3 cm"
        HIGH         -> "±1 cm"
    }
}

/**
 * Complete UI snapshot for MeasureScreen — owned by [MeasureViewModel].
 *
 * [screenPointA] / [screenPointB] are in raw pixels (aligned with the
 * Compose Canvas coordinate system) and are null when the anchor is not
 * in the camera's view or isn't tracking.
 */
data class MeasureUiState(
    val phase: MeasurePhase = MeasurePhase.IDLE,
    val confidence: TrackingConfidence = TrackingConfidence.NOT_TRACKING,
    /** 2-D projection of anchor A, in screen pixels. Null = out of view. */
    val screenPointA: Offset? = null,
    /** 2-D projection of anchor B, in screen pixels. Null = out of view. */
    val screenPointB: Offset? = null,
    /** Raw distance in metres between the two anchors. */
    val distanceMeters: Float? = null,
    /** Which unit to display the distance in. */
    val unitSystem: UnitSystem = UnitSystem.CM,
    /** True while save operation is in-flight (spinner on Save button). */
    val isSaving: Boolean = false,
    /** Momentarily true after a successful save (for snackbar / animation). */
    val justSaved: Boolean = false,
)
