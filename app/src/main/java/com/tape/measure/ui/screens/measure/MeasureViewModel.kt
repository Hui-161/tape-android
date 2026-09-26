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
import com.tape.measure.domain.measure.FarTargetSmoother
import com.tape.measure.domain.measure.FarTargeting
import com.tape.measure.domain.measure.FrameSample
import com.tape.measure.domain.measure.HitKind
import com.tape.measure.domain.measure.MeasureGeometry
import com.tape.measure.domain.measure.Ray
import com.tape.measure.domain.measure.SurfaceHit
import com.tape.measure.domain.measure.TargetSelector
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
 * Every AR frame arrives as a [FrameSample] in [onFrame]. The point to place is where the
 * crosshair in the middle of the screen hits a detected surface or, out of ARCore's reliable
 * range, an estimate from [FarTargeting]. [addPoint] fixes the start point there, after which a
 * live line follows the crosshair until [addPoint] fixes the end point.
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

    /** A placed point, what it was placed on, and the uncertainty it had when it was placed. */
    private class PlacedPoint(val point: TrackedPoint, val kind: HitKind, val uncertainty: Float)

    private val points = mutableListOf<PlacedPoint>()

    /** Where [addPoint] places the next point, or null if there is nowhere to place it. */
    private var target: SurfaceHit? = null
    private val farSmoother = FarTargetSmoother()

    /** The camera's path since the start point was placed, for the tracking drift. */
    private var walkedMeters = 0f
    private var lastPathPoint: Vec3? = null

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
            target = null
            farSmoother.reset()
            _overlay.value = OverlayState()
            publishGuidance(sample, ray = null)
            return
        }
        if (points.any { it.point.isLost }) releaseAll()
        val ray = FarTargeting.centerRay(sample.view, sample.projection)
        trackWalk(ray.origin)
        target = resolveTarget(sample, ray)
        _overlay.value = buildOverlay(sample, ray)
        publishGuidance(sample, ray)
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
        val hit = target
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
        val uncertainty = if (points.isEmpty()) {
            walkedMeters = 0f
            hit.uncertaintyMeters
        } else {
            AccuracyEstimator.withDrift(hit.uncertaintyMeters, walkedMeters)
        }
        points += PlacedPoint(point, hit.kind, uncertainty)
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
        target = null
        farSmoother.reset()
        walkedMeters = 0f
        lastPathPoint = null
        onPointsChanged()
    }

    /** Adds up the camera's path in steps of [WALK_STEP_METERS], so hand tremor does not count. */
    private fun trackWalk(camera: Vec3) {
        val last = lastPathPoint
        if (last == null) {
            lastPathPoint = camera
            return
        }
        val step = camera.distanceTo(last)
        if (step >= WALK_STEP_METERS) {
            walkedMeters += step
            lastPathPoint = camera
        }
    }

    /** The surface ARCore hits at the crosshair if it is in range, otherwise an estimate. */
    private fun resolveTarget(sample: FrameSample, ray: Ray): SurfaceHit? {
        sample.crosshairHit?.let { hit ->
            farSmoother.reset()
            return hit
        }
        val base = points.singleOrNull()
        val far = FarTargeting.target(
            ray = ray,
            groundY = sample.groundHeight,
            base = base?.point?.currentPosition(),
            baseUncertainty = base?.uncertainty ?: 0f,
        )?.takeUnless { FarTargeting.isHidden(it, sample.crosshairTooFarMeters) }
        if (far == null) {
            farSmoother.reset()
            return null
        }
        val position = farSmoother.smooth(far)
        return SurfaceHit(
            position = position,
            kind = far.kind,
            cameraDistanceMeters = far.distanceMeters,
            uncertaintyMeters = far.uncertaintyMeters,
            createPoint = { sample.createPointAt(position) },
        )
    }

    /**
     * True if there is something at the crosshair, but out of range: a surface too far to hit
     * directly, or ground too far to estimate.
     */
    private fun isOutOfRange(sample: FrameSample, ray: Ray) =
        sample.crosshairTooFar || (ray.direction.y < 0f && FarTargeting.hasGround(ray, sample.groundHeight))

    private fun buildOverlay(sample: FrameSample, ray: Ray): OverlayState {
        val target = target
        val start = points.getOrNull(0)
        val end = points.getOrNull(1)
        val startPos = start?.point?.currentPosition()
        val endPos = end?.point?.currentPosition()

        // With only the start point placed, the line runs live to the crosshair.
        val liveTarget = if (start != null && end == null) target else null
        val segmentEnd: Vec3?
        val endKind: HitKind?
        val endUncertainty: Float
        when {
            end != null -> {
                segmentEnd = endPos
                endKind = end.kind
                endUncertainty = end.uncertainty
            }
            liveTarget != null -> {
                segmentEnd = liveTarget.position
                endKind = liveTarget.kind
                endUncertainty = AccuracyEstimator.withDrift(liveTarget.uncertaintyMeters, walkedMeters)
            }
            else -> {
                segmentEnd = null
                endKind = null
                endUncertainty = 0f
            }
        }
        val distance = if (startPos != null && segmentEnd != null) startPos.distanceTo(segmentEnd) else null
        // A height estimated above the start point already includes the start point's error.
        val startUncertainty = if (endKind == HitKind.VERTICAL_FAR) 0f else start?.uncertainty ?: 0f

        return OverlayState(
            crosshair = when {
                target == null ->
                    if (isOutOfRange(sample, ray)) CrosshairState.TOO_FAR else CrosshairState.SEARCHING
                target.kind.isFar -> CrosshairState.FAR_ESTIMATE
                else -> CrosshairState.ON_SURFACE
            },
            pointA = startPos?.let { project(sample, it) },
            pointB = endPos?.let { project(sample, it) },
            segment = if (startPos != null && segmentEnd != null) {
                MeasureGeometry.projectSegment(
                    sample.view, sample.projection, startPos, segmentEnd, sample.width, sample.height,
                )
            } else null,
            isLive = liveTarget != null,
            distanceMeters = distance,
            accuracy = if (start != null && distance != null) {
                AccuracyEstimator.estimate(distance, startUncertainty, endUncertainty)
            } else null,
        )
    }

    private fun project(sample: FrameSample, point: Vec3) =
        MeasureGeometry.projectPoint(sample.view, sample.projection, point, sample.width, sample.height)

    /** [ray] is the crosshair ray, null while not tracking. */
    private fun publishGuidance(sample: FrameSample, ray: Ray?) {
        val target = target
        val guidance = when {
            ray == null -> sample.problem.toGuidance()
            points.size >= 2 ->
                if (_overlay.value.accuracy?.level == AccuracyLevel.POOR) Guidance.MEASURED_IMPRECISE
                else Guidance.MEASURED
            target == null -> noTargetGuidance(sample, ray)
            target.kind == HitKind.GROUND_FAR -> Guidance.FAR_GROUND
            target.kind == HitKind.VERTICAL_FAR -> Guidance.FAR_VERTICAL
            target.cameraDistanceMeters > TargetSelector.PRECISE_DISTANCE_METERS -> Guidance.CLOSER_IS_MORE_ACCURATE
            points.isEmpty() -> Guidance.PLACE_START
            else -> Guidance.PLACE_END
        }
        _uiState.update {
            it.copy(guidance = guidance, isTracking = sample.isTracking, canAddPoint = target != null)
        }
    }

    private fun noTargetGuidance(sample: FrameSample, ray: Ray): Guidance = when {
        !isOutOfRange(sample, ray) ->
            if (sample.surfacesDetected) Guidance.AIM_AT_SURFACE else Guidance.FIND_SURFACE
        // Above the horizon only a height above the start point can be estimated.
        ray.direction.y >= 0f -> if (points.isEmpty()) Guidance.AIM_AT_BASE else Guidance.TOO_FAR
        !FarTargeting.hasGround(ray, sample.groundHeight) -> Guidance.NEED_GROUND
        // There is a ground point, but hidden behind a wall or an object at the crosshair.
        sample.groundHeight?.let { FarTargeting.groundTarget(ray, it) } != null -> Guidance.AIM_AT_BASE
        else -> Guidance.TOO_FAR
    }
}

/** Camera movement below this is hand tremor, not walking. */
private const val WALK_STEP_METERS = 0.1f

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
