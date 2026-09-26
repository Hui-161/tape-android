package com.tape.measure.domain.measure

/**
 * A point fixed in the real world. Backed by an ARCore anchor in the app; faked in tests so the
 * measuring state machine can run on the JVM.
 */
interface TrackedPoint {
    /** Current world position, or null while the point is temporarily not tracked. */
    fun currentPosition(): Vec3?

    /** True once the point can never be tracked again (e.g. ARCore reset the session). */
    val isLost: Boolean

    /** Frees the tracking resources. Only call while the AR session that created it is alive. */
    fun release()
}

/** The surface currently under the crosshair. */
class SurfaceHit(
    val position: Vec3,
    val kind: HitKind,
    val cameraDistanceMeters: Float,
    /** Fixes a point at this hit. Returns null if ARCore refuses to create one. */
    val createPoint: () -> TrackedPoint?,
)

/** Why the camera is not tracking. */
enum class TrackingProblem {
    NONE,
    INITIALIZING,
    INSUFFICIENT_LIGHT,
    EXCESSIVE_MOTION,
    INSUFFICIENT_FEATURES,
    CAMERA_UNAVAILABLE,
    STOPPED,
}

/** Everything the measuring logic needs from one AR frame, free of ARCore types. */
class FrameSample(
    val isTracking: Boolean,
    val problem: TrackingProblem,
    /** Column-major view matrix; only meaningful while [isTracking]. */
    val view: FloatArray,
    /** Column-major projection matrix; only meaningful while [isTracking]. */
    val projection: FloatArray,
    val width: Int,
    val height: Int,
    /** True once ARCore has found at least one tracked plane. */
    val surfacesDetected: Boolean,
    val crosshairHit: SurfaceHit?,
)

/** Reasons the AR session could not start, reduced to what the user can act on. */
enum class ArError {
    INSTALL_DECLINED,
    ARCORE_UPDATE_REQUIRED,
    APP_UPDATE_REQUIRED,
    CAMERA_UNAVAILABLE,
    CAMERA_PERMISSION,
    OTHER,
}
