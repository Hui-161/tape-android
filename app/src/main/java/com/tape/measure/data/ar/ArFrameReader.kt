package com.tape.measure.data.ar

import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.tape.measure.domain.measure.ArError
import com.tape.measure.domain.measure.FrameSample
import com.tape.measure.domain.measure.HitKind
import com.tape.measure.domain.measure.MeasureGeometry
import com.tape.measure.domain.measure.SurfaceHit
import com.tape.measure.domain.measure.TargetSelector
import com.tape.measure.domain.measure.TrackedPoint
import com.tape.measure.domain.measure.TrackingProblem
import com.tape.measure.domain.measure.Vec3

/**
 * Turns ARCore frames into plain [FrameSample]s so the measuring logic stays testable without
 * ARCore.
 *
 * SceneView drives its render loop from the main-thread Choreographer, so [read] runs on the
 * main thread, as do the UI callbacks that consume its results.
 */
class ArFrameReader {

    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val normal = FloatArray(3)
    private var depthEnabled = false

    /** Call once the session is configured; SceneView turns depth off if it is unsupported. */
    fun onSessionCreated(session: Session) {
        depthEnabled = session.config.depthMode != Config.DepthMode.DISABLED
    }

    /** Samples [frame], hit-testing the crosshair at the centre of the [width]×[height] view. */
    fun read(session: Session, frame: Frame, width: Int, height: Int): FrameSample {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            return FrameSample(
                isTracking = false,
                problem = camera.trackingProblem(),
                view = view.copyOf(),
                projection = projection.copyOf(),
                width = width,
                height = height,
                surfacesDetected = false,
                crosshairHit = null,
            )
        }
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(projection, 0, MeasureGeometry.NEAR_CLIP, MeasureGeometry.FAR_CLIP)
        val target = TargetSelector.select(usableHits(frame.hitTest(width / 2f, height / 2f), camera.pose))
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
        return FrameSample(
            isTracking = true,
            problem = TrackingProblem.NONE,
            view = view.copyOf(),
            projection = projection.copyOf(),
            width = width,
            height = height,
            surfacesDetected = target.hit != null || planes.isNotEmpty(),
            crosshairHit = target.hit,
            crosshairTooFarMeters = target.tooFarMeters,
            // The floor or ground is the lowest plane; a table top is higher.
            groundHeight = planes
                .filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                .minOfOrNull { it.centerPose.ty() },
            createPointAt = { p ->
                runCatching { AnchorPoint(session.createAnchor(Pose.makeTranslation(p.x, p.y, p.z))) }.getOrNull()
            },
        )
    }

    /** The hits that are good enough to measure on, nearest first like ARCore returns them. */
    private fun usableHits(hits: List<HitResult>, cameraPose: Pose): List<SurfaceHit> = hits.mapNotNull { hit ->
        val kind = when (val trackable = hit.trackable) {
            is Plane -> if (isUsablePlaneHit(trackable, hit, cameraPose)) trackable.hitKind() else null
            is DepthPoint ->
                if (depthEnabled && trackable.trackingState == TrackingState.TRACKING) HitKind.DEPTH else null
            else -> null
        } ?: return@mapNotNull null
        val pose = hit.hitPose
        SurfaceHit(
            position = Vec3(pose.tx(), pose.ty(), pose.tz()),
            kind = kind,
            cameraDistanceMeters = hit.distance,
            createPoint = { runCatching { AnchorPoint(hit.createAnchor()) }.getOrNull() },
        )
    }

    /**
     * Same checks as Google's hello_ar sample: the plane is tracked, the hit lies inside the
     * detected polygon (not on the plane's infinite extension) and the camera is on the side
     * the plane faces.
     */
    private fun isUsablePlaneHit(plane: Plane, hit: HitResult, cameraPose: Pose): Boolean {
        if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) return false
        val pose = hit.hitPose
        if (!plane.isPoseInPolygon(pose)) return false
        pose.getTransformedAxis(1, 1f, normal, 0)
        val cameraSide = (cameraPose.tx() - pose.tx()) * normal[0] +
            (cameraPose.ty() - pose.ty()) * normal[1] +
            (cameraPose.tz() - pose.tz()) * normal[2]
        return cameraSide > 0f
    }

    companion object {
        /** Session setup for measuring: floors and walls, depth where available, no light estimation. */
        fun configure(session: Session, config: Config) {
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.depthMode =
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC
                else Config.DepthMode.DISABLED
            // Only needed to light virtual objects, which Tape does not render.
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        }
    }
}

/** [TrackedPoint] backed by an ARCore anchor. */
private class AnchorPoint(private val anchor: Anchor) : TrackedPoint {
    override fun currentPosition(): Vec3? =
        if (anchor.trackingState == TrackingState.TRACKING) anchor.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
        else null

    override val isLost: Boolean
        get() = anchor.trackingState == TrackingState.STOPPED

    override fun release() = anchor.detach()
}

private fun Plane.hitKind() = if (type == Plane.Type.VERTICAL) HitKind.PLANE_VERTICAL else HitKind.PLANE_HORIZONTAL

private fun Camera.trackingProblem(): TrackingProblem =
    if (trackingState == TrackingState.STOPPED) {
        TrackingProblem.STOPPED
    } else {
        when (trackingFailureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingProblem.INSUFFICIENT_LIGHT
            TrackingFailureReason.EXCESSIVE_MOTION -> TrackingProblem.EXCESSIVE_MOTION
            TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingProblem.INSUFFICIENT_FEATURES
            TrackingFailureReason.CAMERA_UNAVAILABLE -> TrackingProblem.CAMERA_UNAVAILABLE
            TrackingFailureReason.BAD_STATE -> TrackingProblem.STOPPED
            else -> TrackingProblem.INITIALIZING
        }
    }

/** Maps a session start failure to something the user can act on. */
fun Exception.toArError(): ArError = when (this) {
    is UnavailableUserDeclinedInstallationException -> ArError.INSTALL_DECLINED
    is UnavailableArcoreNotInstalledException, is UnavailableApkTooOldException -> ArError.ARCORE_UPDATE_REQUIRED
    is UnavailableSdkTooOldException -> ArError.APP_UPDATE_REQUIRED
    is CameraNotAvailableException -> ArError.CAMERA_UNAVAILABLE
    is SecurityException -> ArError.CAMERA_PERMISSION
    else -> ArError.OTHER
}
