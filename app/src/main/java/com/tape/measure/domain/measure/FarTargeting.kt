package com.tape.measure.domain.measure

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** A ray in world space; [direction] is a unit vector. */
data class Ray(val origin: Vec3, val direction: Vec3)

/** A point estimated from geometry instead of an ARCore hit. */
class FarTarget(
    val position: Vec3,
    val kind: HitKind,
    val distanceMeters: Float,
    val uncertaintyMeters: Float,
)

/**
 * Long-range targeting that does not trust far-away depth.
 *
 * Beyond a few metres ARCore's depth estimates are unreliable (the first field test measured a
 * door across a courtyard as 14.8 m). Two things stay precise, though: the camera's height above
 * the floor or ground detected nearby, and the camera's orientation, which ARCore aligns with
 * gravity. So, as [target] chooses:
 *
 * - Below the horizon, [groundTarget] intersects the crosshair ray with the ground plane: distant
 *   points on level ground, up to [MAX_DISTANCE_METERS].
 * - Above it, [verticalTarget] intersects the ray with a vertical plane through the start point:
 *   points straight above it, such as the top of a distant door, a facade or a hall's ceiling.
 *
 * Errors grow with distance. At slant distance t and camera height h, an angular error σα moves a
 * ground point by t²/h·σα and a height error σh by t/h·σh. Heights on the vertical plane are far
 * less sensitive, because an error in the start point's distance mostly moves both points together.
 */
object FarTargeting {

    const val MAX_DISTANCE_METERS = 30f

    /** Excludes a table top mistaken for the ground; standing or sitting, the phone is higher. */
    const val MIN_CAMERA_HEIGHT_METERS = 0.8f

    /** Rays closer to the horizon than this meet the ground at too grazing an angle. */
    val MIN_DEPRESSION_SIN = sin(Math.toRadians(2.0)).toFloat()

    /** Orientation plus aiming error of the crosshair ray. */
    val ANGULAR_ERROR_RAD = Math.toRadians(0.25).toFloat()

    /** Error of the camera height above the detected ground plane. */
    const val HEIGHT_ERROR_METERS = 0.02f

    /** Horizontally closer than this, the camera is right above the start point. */
    const val MIN_HORIZONTAL_DISTANCE_METERS = 0.05f

    /** A surface nearer than this fraction of a ground point's distance hides it, see [isHidden]. */
    const val HIDDEN_FRACTION = 0.7f

    /**
     * The ray through the screen centre, from column-major view and projection matrices. The view
     * matrix is [R | t] with R a rotation, so the camera sits at −Rᵀt and a view-space direction d
     * points along Rᵀd in the world. The projection may put the principal point off-centre.
     */
    fun centerRay(view: FloatArray, projection: FloatArray): Ray {
        val v = view
        val tx = v[12]
        val ty = v[13]
        val tz = v[14]
        val origin = Vec3(
            -(v[0] * tx + v[1] * ty + v[2] * tz),
            -(v[4] * tx + v[5] * ty + v[6] * tz),
            -(v[8] * tx + v[9] * ty + v[10] * tz),
        )
        val dx = projection[8] / projection[0]
        val dy = projection[9] / projection[5]
        val dz = -1f
        val direction = Vec3(
            v[0] * dx + v[1] * dy + v[2] * dz,
            v[4] * dx + v[5] * dy + v[6] * dz,
            v[8] * dx + v[9] * dy + v[10] * dz,
        )
        return Ray(origin, direction.normalized())
    }

    /**
     * The estimate at the crosshair: on the ground at [groundY] below the horizon, above the start
     * point [base] above it. Null if that is unavailable or not trustworthy.
     */
    fun target(ray: Ray, groundY: Float?, base: Vec3?, baseUncertainty: Float): FarTarget? =
        if (ray.direction.y < 0f) groundY?.let { groundTarget(ray, it) }
        else base?.let { verticalTarget(ray, it, baseUncertainty) }

    /**
     * True if ARCore sees a surface clearly in front of the ground point [target], [surfaceMeters]
     * away: the crosshair is on a wall or an object, and the ground point lies behind it. ARCore's
     * distance to such a surface is too imprecise to measure with, but good enough for this.
     */
    fun isHidden(target: FarTarget, surfaceMeters: Float?): Boolean =
        target.kind == HitKind.GROUND_FAR &&
            surfaceMeters != null &&
            surfaceMeters < HIDDEN_FRACTION * target.distanceMeters

    /** True if [groundY] is known and far enough below the camera to be the ground. */
    fun hasGround(ray: Ray, groundY: Float?): Boolean =
        groundY != null && ray.origin.y - groundY >= MIN_CAMERA_HEIGHT_METERS

    /** Where [ray] meets level ground at height [groundY], or null if that is not trustworthy. */
    fun groundTarget(ray: Ray, groundY: Float): FarTarget? {
        if (!hasGround(ray, groundY)) return null
        val height = ray.origin.y - groundY
        val down = -ray.direction.y
        if (down < MIN_DEPRESSION_SIN) return null
        val distance = height / down
        if (distance > MAX_DISTANCE_METERS) return null
        val fromAngle = distance * distance / height * ANGULAR_ERROR_RAD
        val fromHeight = distance / height * HEIGHT_ERROR_METERS
        return FarTarget(
            position = ray.origin + ray.direction * distance,
            kind = HitKind.GROUND_FAR,
            distanceMeters = distance,
            uncertaintyMeters = sqrt(fromAngle * fromAngle + fromHeight * fromHeight),
        )
    }

    /**
     * Where [ray] meets the vertical plane through [base] that faces the camera. The uncertainty is
     * that of the distance to [base] and already includes [baseUncertainty], the start point's.
     */
    fun verticalTarget(ray: Ray, base: Vec3, baseUncertainty: Float): FarTarget? {
        val dx = ray.origin.x - base.x
        val dz = ray.origin.z - base.z
        val horizontal = sqrt(dx * dx + dz * dz)
        if (horizontal < MIN_HORIZONTAL_DISTANCE_METERS) return null
        val facing = Vec3(dx / horizontal, 0f, dz / horizontal)
        val towardsPlane = -ray.direction.dot(facing)
        if (towardsPlane <= 0f) return null // looking away from the start point
        val distance = horizontal / towardsPlane
        if (distance > MAX_DISTANCE_METERS) return null

        // An angular error moves the point on the plane by D·σα/cos²β (β = ray elevation); an
        // error in the start point's distance shifts the plane and the height by tan β·σbase.
        // The start point's own height is about as uncertain as the camera height.
        val cosSquared = (1f - ray.direction.y * ray.direction.y).coerceAtLeast(1e-4f)
        val tanElevation = abs(ray.direction.y) / sqrt(cosSquared)
        val onPlane = horizontal * ANGULAR_ERROR_RAD / cosSquared
        val fromBase = tanElevation * baseUncertainty
        return FarTarget(
            position = ray.origin + ray.direction * distance,
            kind = HitKind.VERTICAL_FAR,
            distanceMeters = distance,
            uncertaintyMeters = sqrt(
                onPlane * onPlane + fromBase * fromBase + HEIGHT_ERROR_METERS * HEIGHT_ERROR_METERS,
            ),
        )
    }
}

/**
 * Steadies far estimates: at 20 m, a hand tremor of 0.1° moves a ground point by half a metre.
 * A jump by more than [resetFraction] of the distance (aiming somewhere else) is taken at once.
 */
class FarTargetSmoother(private val weight: Float = 0.25f, private val resetFraction: Float = 0.15f) {
    private var kind: HitKind? = null
    private var smoothed: Vec3? = null

    fun smooth(target: FarTarget): Vec3 {
        val last = smoothed
        val next = if (
            last == null ||
            kind != target.kind ||
            last.distanceTo(target.position) > resetFraction * target.distanceMeters
        ) {
            target.position
        } else {
            last + (target.position - last) * weight
        }
        smoothed = next
        kind = target.kind
        return next
    }

    fun reset() {
        smoothed = null
        kind = null
    }
}
