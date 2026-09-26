package com.tape.measure.domain.measure

/** What the crosshair currently measures on. */
class CrosshairTarget(
    /** The surface to place a point on, or null if there is none within range. */
    val hit: SurfaceHit?,
    /** True if there is a surface under the crosshair, but it is beyond [TargetSelector.MAX_DISTANCE_METERS]. */
    val tooFar: Boolean,
)

/**
 * Chooses the surface under the crosshair from ARCore's usable hits (sorted nearest first).
 *
 * Far away, ARCore's plane and depth estimates are unreliable: points still land on the right
 * spot on screen, but at the wrong depth, and the measured distance is then mostly depth error
 * (a door measured from about 15 m came out at 14.8 m). Hits beyond [MAX_DISTANCE_METERS] are
 * therefore refused, so the user is asked to move closer instead.
 */
object TargetSelector {

    /** Google names 0.5–5 m as the range in which ARCore depth is most accurate. */
    const val MAX_DISTANCE_METERS = 5f

    /**
     * Beyond this, a point on a plane is uncertain by more than about 4.5 cm, so the user is
     * told that measuring from closer is more accurate. Placing a point is still allowed.
     */
    const val PRECISE_DISTANCE_METERS = 3f

    /**
     * Fitted planes are more precise than single depth samples. A plane hit this close behind
     * the nearest depth hit is the same surface, and is used instead. A depth hit clearly in
     * front of the plane is an object standing on it, and is kept.
     */
    const val PLANE_PREFERENCE_METERS = 0.1f

    fun select(hits: List<SurfaceHit>): CrosshairTarget {
        val nearest = hits.firstOrNull() ?: return CrosshairTarget(hit = null, tooFar = false)
        if (nearest.cameraDistanceMeters > MAX_DISTANCE_METERS) return CrosshairTarget(hit = null, tooFar = true)

        val inRange = hits.filter { it.cameraDistanceMeters <= MAX_DISTANCE_METERS }
        val plane = inRange.firstOrNull { it.kind != HitKind.DEPTH }
        val chosen = if (
            plane != null &&
            plane.cameraDistanceMeters - nearest.cameraDistanceMeters <= PLANE_PREFERENCE_METERS
        ) plane else nearest
        return CrosshairTarget(hit = chosen, tooFar = false)
    }
}
