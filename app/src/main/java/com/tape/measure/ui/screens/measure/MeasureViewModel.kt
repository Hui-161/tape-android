package com.tape.measure.ui.screens.measure

import android.opengl.Matrix
import androidx.compose.ui.geometry.Offset
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.HitResult
import com.google.ar.core.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.sqrt
import androidx.lifecycle.ViewModel

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
class MeasureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    // Anchor handles — written on main thread, read on render thread.
    @Volatile private var anchorA: Anchor? = null
    @Volatile private var anchorB: Anchor? = null

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
     * Called every AR frame. Reads tracking state, projects anchor poses to
     * screen space, and pushes an updated [MeasureUiState].
     *
     * [camera] is the ARCore [Camera] obtained from the current frame.
     */
    fun onFrame(camera: Camera) {
        val confidence = camera.trackingState.toConfidence()

        if (camera.trackingState != TrackingState.TRACKING) {
            _uiState.update { it.copy(confidence = confidence) }
            return
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

    /**
     * Called when SceneView detects a tap on a detected surface.
     * [hitResult] is valid only during this callback; we call [HitResult.createAnchor]
     * immediately so ARCore retains the position.
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

    /**
     * Saves the current measurement to Room.
     * Repository injection comes in item 6; the stub is here so MeasureScreen's
     * Save button can already wire up without a second refactor.
     */
    fun saveMeasurement(label: String = "") {
        // TODO(item-6): inject MeasurementRepository and persist to Room.
        _uiState.update { it.copy(justSaved = true) }
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
