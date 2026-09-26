package com.tape.measure.ui.screens.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tape.measure.data.db.MeasurementEntity
import com.tape.measure.data.prefs.UserPreferencesRepository
import com.tape.measure.data.repository.MeasurementRepository
import com.tape.measure.domain.measure.AccuracyEstimate
import com.tape.measure.domain.measure.AccuracyEstimator
import com.tape.measure.domain.measure.AccuracyLevel
import com.tape.measure.domain.measure.ArError
import com.tape.measure.domain.measure.FrameSample
import com.tape.measure.domain.measure.MeasureGeometry
import com.tape.measure.domain.measure.SurfaceHit
import com.tape.measure.domain.measure.TrackedPoint
import com.tape.measure.domain.measure.TrackingProblem
import com.tape.measure.domain.measure.Vec3
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Live crosshair measuring.
 *
 * Every AR frame arrives as a [FrameSample] in [onFrame]; the crosshair in the middle of the
 * screen is hit-tested against detected surfaces. [addPoint] fixes the start point at the
 * crosshair, after which a live line follows the crosshair until [addPoint] fixes the end point.
 *
 * Threading: SceneView runs its render loop on the main-thread Choreographer, so frame callbacks
 * and user actions all arrive on the main thread and need no synchronisation.
 *
 * Points belong to the AR session that created them. When the scene is disposed (navigation,
 * error retry) the session is closed and the points are dropped without touching them again.
 */
@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val repository: MeasurementRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    private val _overlay = MutableStateFlow(OverlayState())
    val overlay: StateFlow<OverlayState> = _overlay.asStateFlow()

    private val _events = Channel<MeasureEvent>(Channel.BUFFERED)
    val events: Flow<MeasureEvent> = _events.receiveAsFlow()

    /** A placed point and the uncertainty it had when it was placed. */
    private class PlacedPoint(val point: TrackedPoint, val uncertainty: Float)

    private val points = mutableListOf<PlacedPoint>()
    private var crosshairHit: SurfaceHit? = null

    init {
        prefs.unitFlow
            .onEach { unit -> _uiState.update { it.copy(unitSystem = unit) } }
            .launchIn(viewModelScope)
    }

    // ── AR callbacks ─────────────────────────────────────────────────────────

    fun onFrame(sample: FrameSample) {
        if (!sample.isTracking) {
            // Placed points keep their last screen position otherwise, which would be wrong
            // as soon as the phone moves.
            crosshairHit = null
            _overlay.value = OverlayState()
            publishGuidance(sample)
            return
        }
        crosshairHit = sample.crosshairHit
        if (points.any { it.point.isLost }) releaseAll()
        _overlay.value = buildOverlay(sample)
        publishGuidance(sample)
    }

    /** A new session never tracks the points of a previous one. */
    fun onSessionCreated() = forgetPoints()

    /** The session is closed together with the scene, which also frees its anchors. */
    fun onArSceneDisposed() {
        forgetPoints()
        _overlay.value = OverlayState()
    }

    fun onArError(error: ArError) = _uiState.update { it.copy(arError = error) }

    fun retrySession() = _uiState.update { it.copy(arError = null, sessionAttempt = it.sessionAttempt + 1) }

    // ── User actions ─────────────────────────────────────────────────────────

    /** Places a point at the crosshair. A third point starts a new measurement. */
    fun addPoint() {
        val hit = crosshairHit
        if (hit == null) {
            _events.trySend(MeasureEvent.NO_SURFACE)
            return
        }
        if (points.size >= 2) releaseAll()
        val point = hit.createPoint()
        if (point == null) {
            _events.trySend(MeasureEvent.NO_SURFACE)
            return
        }
        points += PlacedPoint(point, AccuracyEstimator.pointUncertainty(hit.kind, hit.cameraDistanceMeters))
        onPointsChanged()
        _events.trySend(MeasureEvent.POINT_ADDED)
    }

    /** Removes the most recently placed point. */
    fun undo() {
        val last = points.removeLastOrNull() ?: return
        last.point.release()
        onPointsChanged()
    }

    /** Cycles cm → m → in → ft and stores the choice as the app-wide unit. */
    fun cycleUnit() {
        val next = _uiState.value.unitSystem.next()
        _uiState.update { it.copy(unitSystem = next) }
        viewModelScope.launch { prefs.setUnit(next) }
    }

    fun saveMeasurement() {
        val state = _uiState.value
        val overlay = _overlay.value
        val distance = overlay.distanceMeters
        if (state.phase != MeasurePhase.BOTH || state.isSaving || state.isSaved || distance == null) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.save(
                MeasurementEntity(
                    id = UUID.randomUUID().toString(),
                    distanceMeters = distance,
                    unit = state.unitSystem.name,
                    label = null,
                    confidence = overlay.accuracy.toScore(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
            _events.send(MeasureEvent.SAVED)
        }
    }

    override fun onCleared() {
        // The scene, and with it the session, is disposed before the ViewModel is cleared.
        points.clear()
        super.onCleared()
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun onPointsChanged() {
        _uiState.update { it.copy(phase = phaseOf(points.size), isSaved = false) }
    }

    private fun releaseAll() {
        points.forEach { it.point.release() }
        points.clear()
        onPointsChanged()
    }

    private fun forgetPoints() {
        points.clear()
        crosshairHit = null
        onPointsChanged()
    }

    private fun buildOverlay(sample: FrameSample): OverlayState {
        val hit = sample.crosshairHit
        val start = points.getOrNull(0)
        val end = points.getOrNull(1)
        val startPos = start?.point?.currentPosition()
        val endPos = end?.point?.currentPosition()

        // With only the start point placed, the line runs live to the crosshair.
        val liveHit = if (start != null && end == null) hit else null
        val segmentEnd: Vec3?
        val segmentEndUncertainty: Float
        when {
            end != null -> {
                segmentEnd = endPos
                segmentEndUncertainty = end.uncertainty
            }
            liveHit != null -> {
                segmentEnd = liveHit.position
                segmentEndUncertainty =
                    AccuracyEstimator.pointUncertainty(liveHit.kind, liveHit.cameraDistanceMeters)
            }
            else -> {
                segmentEnd = null
                segmentEndUncertainty = 0f
            }
        }
        val distance = if (startPos != null && segmentEnd != null) startPos.distanceTo(segmentEnd) else null

        return OverlayState(
            crosshair = when {
                hit != null -> CrosshairState.ON_SURFACE
                sample.crosshairTooFar -> CrosshairState.TOO_FAR
                else -> CrosshairState.SEARCHING
            },
            pointA = startPos?.let { project(sample, it) },
            pointB = endPos?.let { project(sample, it) },
            segment = if (startPos != null && segmentEnd != null) {
                MeasureGeometry.projectSegment(
                    sample.view, sample.projection, startPos, segmentEnd, sample.width, sample.height,
                )
            } else null,
            isLive = liveHit != null,
            distanceMeters = distance,
            accuracy = if (start != null && distance != null) {
                AccuracyEstimator.estimate(distance, start.uncertainty, segmentEndUncertainty)
            } else null,
        )
    }

    private fun project(sample: FrameSample, point: Vec3) =
        MeasureGeometry.projectPoint(sample.view, sample.projection, point, sample.width, sample.height)

    private fun publishGuidance(sample: FrameSample) {
        val hit = sample.crosshairHit.takeIf { sample.isTracking }
        val guidance = when {
            !sample.isTracking -> sample.problem.toGuidance()
            points.size >= 2 -> Guidance.MEASURED
            hit == null && sample.crosshairTooFar -> Guidance.TOO_FAR
            hit == null && !sample.surfacesDetected -> Guidance.FIND_SURFACE
            hit == null -> Guidance.AIM_AT_SURFACE
            points.isEmpty() -> Guidance.PLACE_START
            else -> Guidance.PLACE_END
        }
        _uiState.update {
            it.copy(guidance = guidance, isTracking = sample.isTracking, canAddPoint = hit != null)
        }
    }
}

private fun phaseOf(pointCount: Int) = when (pointCount) {
    0 -> MeasurePhase.IDLE
    1 -> MeasurePhase.POINT_A
    else -> MeasurePhase.BOTH
}

private fun TrackingProblem.toGuidance() = when (this) {
    TrackingProblem.NONE, TrackingProblem.INITIALIZING -> Guidance.STARTING
    TrackingProblem.INSUFFICIENT_LIGHT -> Guidance.TOO_DARK
    TrackingProblem.EXCESSIVE_MOTION -> Guidance.TOO_FAST
    TrackingProblem.INSUFFICIENT_FEATURES -> Guidance.LOW_TEXTURE
    TrackingProblem.CAMERA_UNAVAILABLE -> Guidance.CAMERA_UNAVAILABLE
    TrackingProblem.STOPPED -> Guidance.AR_STOPPED
}

/** Coarse score for the existing `confidence` column until the schema stores the uncertainty. */
private fun AccuracyEstimate?.toScore(): Float = when (this?.level) {
    AccuracyLevel.GOOD -> 0.9f
    AccuracyLevel.FAIR -> 0.6f
    AccuracyLevel.POOR -> 0.3f
    null -> 0f
}
