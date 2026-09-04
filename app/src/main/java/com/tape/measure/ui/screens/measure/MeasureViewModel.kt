package com.tape.measure.ui.screens.measure

import android.opengl.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.tape.measure.data.db.MeasurementEntity
import com.tape.measure.data.prefs.UserPreferencesRepository
import com.tape.measure.data.repository.MeasurementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * ViewModel for the AR measurement screen.
 *
 * Threading model
 * ───────────────
 * • [onFrame] is called from SceneView's GL render thread (60 fps).
 *   We do the 3-D→2-D projection there and write to [_uiState] directly —
 *   StateFlow.update() is internally thread-safe via CAS, so no dispatch needed.
 *   We extract only primitive FloatArrays from ARCore objects (no ARCore handles
 *   escape the render thread beyond what ARCore's own threading model allows).
 *
 * • [onTap], [cycleUnit], [reset] are called from the main thread (gesture callbacks).
 *   Anchor references are @Volatile so the render thread always sees the latest value
 *   without needing a full lock.
 *
 * Save flow
 * ─────────
 * Room repository will be injected here in a future step (item 6). The [saveMeasurement]
 * stub already has the right signature so MeasureScreen doesn't need to change later.
 */
@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val repository: MeasurementRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    init {
        prefs.unitFlow
            .onEach { unit -> _uiState.update { it.copy(unitSystem = unit) } }
            .launchIn(viewModelScope)
    }

    // Anchor handles — written on main thread, read on render thread.
    @Volatile private var anchorA: Anchor? = null
    @Volatile private var anchorB: Anchor? = null

    // Pending tap screen coordinates — set from main thread, consumed on render thread.
    @Volatile private var pendingTapX: Float = -1f
    @Volatile private var pendingTapY: Float = -1f

    // Viewport dimensions set once from MeasureScreen's onGloballyPositioned.
    @Volatile private var viewportWidth: Int = 1
    @Volatile private var viewportHeight: Int = 1

    // Reusable float arrays to avoid per-frame allocation on the render thread.
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val worldPoint = FloatArray(4)
    private val viewPoint  = FloatArray(4)
    private val clipPoint  = FloatArray(4)

    // ── Lifecycle hooks (called from MeasureScreen) ──────────────────────────

    /** Called once after ARScene is laid out; saves pixel dimensions for projection. */
    fun setViewport(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            viewportWidth = width
            viewportHeight = height
        }
    }

    // ── Per-frame update (render thread) ─────────────────────────────────────

    /**
     * Called every AR frame from the GL render thread. Reads tracking state,
     * processes any queued tap via [Frame.hitTest], projects anchor poses to
     * screen space, and pushes an updated [MeasureUiState].
     */
    fun onFrame(camera: Camera, frame: Frame) {
        val confidence = camera.trackingState.toConfidence()

        if (camera.trackingState != TrackingState.TRACKING) {
            _uiState.update { it.copy(confidence = confidence) }
            return
        }

        // Consume pending tap — hit-test against detected planes.
        val tapX = pendingTapX
        val tapY = pendingTapY
        if (tapX >= 0f) {
            pendingTapX = -1f
            pendingTapY = -1f
            frame.hitTest(tapX, tapY)
                .firstOrNull { it.trackable is Plane && it.trackable.trackingState == TrackingState.TRACKING }
                ?.let { onTap(it) }
        }

        // Grab matrices — safe to call on render thread after Session.update().
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

        val poseA = anchorA?.takeIf { it.trackingState == TrackingState.TRACKING }?.pose
        val poseB = anchorB?.takeIf { it.trackingState == TrackingState.TRACKING }?.pose

        _uiState.update { state ->
            state.copy(
                confidence     = confidence,
                screenPointA   = poseA?.let { project(it.tx(), it.ty(), it.tz()) },
                screenPointB   = poseB?.let { project(it.tx(), it.ty(), it.tz()) },
                distanceMeters = if (poseA != null && poseB != null) {
                    distance(
                        poseA.tx(), poseA.ty(), poseA.tz(),
                        poseB.tx(), poseB.ty(), poseB.tz(),
                    )
                } else null,
            )
        }
    }

    // ── User-interaction handlers (main thread) ───────────────────────────────

    /** Queues screen-space tap coordinates to be processed in the next [onFrame] call. */
    fun enqueueTap(x: Float, y: Float) {
        pendingTapX = x
        pendingTapY = y
    }

    /**
     * Places an anchor at the given hit result. Called from [onFrame] on the render thread
     * after a successful plane hit test, or directly from tests.
     */
    fun onTap(hitResult: HitResult) {
        when (_uiState.value.phase) {
            MeasurePhase.IDLE -> {
                anchorA = hitResult.createAnchor()
                _uiState.update { it.copy(phase = MeasurePhase.POINT_A) }
            }
            MeasurePhase.POINT_A -> {
                anchorB = hitResult.createAnchor()
                _uiState.update { it.copy(phase = MeasurePhase.BOTH) }
            }
            MeasurePhase.BOTH -> reset()   // third tap = start over
        }
    }

    /** Cycles the active unit display: CM → M → IN → FT → CM. */
    fun cycleUnit() {
        _uiState.update { it.copy(unitSystem = it.unitSystem.next()) }
    }

    /** Detaches both anchors and returns the state machine to IDLE. */
    fun reset() {
        anchorA?.detach().also { anchorA = null }
        anchorB?.detach().also { anchorB = null }
        _uiState.update {
            it.copy(
                phase          = MeasurePhase.IDLE,
                screenPointA   = null,
                screenPointB   = null,
                distanceMeters = null,
                justSaved      = false,
            )
        }
    }

    fun saveMeasurement(label: String = "") {
        val dist = _uiState.value.distanceMeters ?: return
        val unit = _uiState.value.unitSystem
        val conf = when (_uiState.value.confidence) {
            TrackingConfidence.HIGH         -> 0.9f
            TrackingConfidence.LOW          -> 0.5f
            TrackingConfidence.NOT_TRACKING -> 0.1f
        }
        viewModelScope.launch {
            repository.save(
                MeasurementEntity(
                    id            = UUID.randomUUID().toString(),
                    distanceMeters = dist,
                    unit          = unit.name,
                    label         = label.ifBlank { null },
                    confidence    = conf,
                    createdAt     = System.currentTimeMillis(),
                )
            )
            _uiState.update { it.copy(justSaved = true) }
        }
    }

    override fun onCleared() {
        anchorA?.detach()
        anchorB?.detach()
        super.onCleared()
    }

    // ── Private math ─────────────────────────────────────────────────────────

    /**
     * Projects a world-space point (wx, wy, wz) to 2-D screen pixels.
     * Returns null when the point is behind the camera (view-space Z ≥ 0).
     *
     * NDC → Screen mapping:
     *   screenX = (ndcX + 1) / 2 * width
     *   screenY = (1 − ndcY) / 2 * height   ← Y is flipped (NDC Y=1 = top of screen)
     */
    private fun project(wx: Float, wy: Float, wz: Float): Offset? {
        worldPoint[0] = wx; worldPoint[1] = wy; worldPoint[2] = wz; worldPoint[3] = 1f

        Matrix.multiplyMV(viewPoint, 0, viewMatrix, 0, worldPoint, 0)
        if (viewPoint[2] >= 0f) return null  // behind camera

        Matrix.multiplyMV(clipPoint, 0, projMatrix, 0, viewPoint, 0)
        val w = clipPoint[3]
        if (w == 0f) return null

        return Offset(
            x = (clipPoint[0] / w + 1f) / 2f * viewportWidth,
            y = (1f - clipPoint[1] / w) / 2f * viewportHeight,
        )
    }

    private fun distance(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
    ): Float {
        val dx = ax - bx; val dy = ay - by; val dz = az - bz
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun TrackingState.toConfidence(): TrackingConfidence = when (this) {
        TrackingState.TRACKING -> TrackingConfidence.HIGH
        TrackingState.PAUSED   -> TrackingConfidence.LOW
        TrackingState.STOPPED  -> TrackingConfidence.NOT_TRACKING
    }
}
