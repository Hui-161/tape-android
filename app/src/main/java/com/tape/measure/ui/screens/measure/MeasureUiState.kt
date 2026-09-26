package com.tape.measure.ui.screens.measure

import com.tape.measure.domain.measure.AccuracyEstimate
import com.tape.measure.domain.measure.ArError
import com.tape.measure.domain.measure.ScreenPoint
import com.tape.measure.domain.measure.ScreenSegment
import com.tape.measure.domain.model.UnitSystem

/**
 * Where the user is in the current measurement.
 *
 *  IDLE    – no point placed; the crosshair looks for the start point
 *  POINT_A – start point placed; a live line follows the crosshair
 *  BOTH    – both points placed; the result is fixed and can be saved
 */
enum class MeasurePhase { IDLE, POINT_A, BOTH }

/** The single hint shown above the controls, most urgent first. */
enum class Guidance {
    STARTING,
    TOO_DARK,
    TOO_FAST,
    LOW_TEXTURE,
    CAMERA_UNAVAILABLE,
    AR_STOPPED,
    FIND_SURFACE,
    AIM_AT_SURFACE,
    PLACE_START,
    PLACE_END,
    MEASURED,
}

enum class CrosshairState { HIDDEN, SEARCHING, ON_SURFACE }

/** Screen state that changes at most a few times per second. */
data class MeasureUiState(
    val phase: MeasurePhase = MeasurePhase.IDLE,
    val guidance: Guidance = Guidance.STARTING,
    val isTracking: Boolean = false,
    /** True while the crosshair is on a surface a point can be placed on. */
    val canAddPoint: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.M,
    val isSaving: Boolean = false,
    /** True once the current measurement is saved, until the next point is placed. */
    val isSaved: Boolean = false,
    val arError: ArError? = null,
    /** Incremented to tear down and recreate the AR session after an error. */
    val sessionAttempt: Int = 0,
)

/**
 * Drawing state, recomputed on every AR frame. Read it only in drawing code or small leaf
 * composables so the rest of the screen does not recompose at frame rate.
 */
data class OverlayState(
    val crosshair: CrosshairState = CrosshairState.HIDDEN,
    val pointA: ScreenPoint? = null,
    val pointB: ScreenPoint? = null,
    val segment: ScreenSegment? = null,
    /** True while the segment ends at the crosshair instead of a placed point. */
    val isLive: Boolean = false,
    val distanceMeters: Float? = null,
    val accuracy: AccuracyEstimate? = null,
)

/** One-off feedback the screen turns into haptics or a snackbar. */
enum class MeasureEvent { POINT_ADDED, NO_SURFACE, SAVED }
