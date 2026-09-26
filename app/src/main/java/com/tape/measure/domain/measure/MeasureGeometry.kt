package com.tape.measure.domain.measure

/** A position on screen in pixels (origin top-left, y pointing down). */
data class ScreenPoint(val x: Float, val y: Float)

/** A projected line segment, already clipped to the part in front of the camera. */
data class ScreenSegment(val start: ScreenPoint, val end: ScreenPoint)

/**
 * Projection math for the measurement overlay.
 *
 * Matrices are column-major 4×4 arrays as returned by ARCore's `Camera.getViewMatrix` and
 * `Camera.getProjectionMatrix` (OpenGL convention: the camera looks down −Z in view space).
 * Kept free of Android and ARCore types so it can be unit-tested on the JVM.
 */
object MeasureGeometry {

    /** Near clip distance in metres; also passed to `Camera.getProjectionMatrix`. */
    const val NEAR_CLIP = 0.1f

    /** Far clip distance in metres. */
    const val FAR_CLIP = 100f

    /** Projects a world-space point, or returns null if it is not in front of the near plane. */
    fun projectPoint(
        view: FloatArray,
        projection: FloatArray,
        point: Vec3,
        width: Int,
        height: Int,
    ): ScreenPoint? {
        val v = transform(view, point)
        if (v.z > -NEAR_CLIP) return null
        return viewToScreen(projection, v, width, height)
    }

    /**
     * Projects the segment [a]–[b]. The segment is clipped against the near plane first, so a
     * measurement whose start point is behind the user is still drawn up to the screen edge.
     * Returns null if the whole segment is behind the camera.
     */
    fun projectSegment(
        view: FloatArray,
        projection: FloatArray,
        a: Vec3,
        b: Vec3,
        width: Int,
        height: Int,
    ): ScreenSegment? {
        var va = transform(view, a)
        var vb = transform(view, b)
        val limit = -NEAR_CLIP
        val aInFront = va.z <= limit
        val bInFront = vb.z <= limit
        if (!aInFront && !bInFront) return null
        if (!aInFront) va = clipToZ(outside = va, inside = vb, z = limit)
        if (!bInFront) vb = clipToZ(outside = vb, inside = va, z = limit)
        val start = viewToScreen(projection, va, width, height) ?: return null
        val end = viewToScreen(projection, vb, width, height) ?: return null
        return ScreenSegment(start, end)
    }

    /** Applies an affine column-major 4×4 matrix to (x, y, z, 1). */
    private fun transform(m: FloatArray, p: Vec3) = Vec3(
        m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
        m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
        m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
    )

    /**
     * View space → screen pixels via clip space and NDC.
     *   screenX = (ndcX + 1) / 2 · width
     *   screenY = (1 − ndcY) / 2 · height   (NDC y = +1 is the top of the screen)
     */
    private fun viewToScreen(projection: FloatArray, v: Vec3, width: Int, height: Int): ScreenPoint? {
        val m = projection
        val clipX = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12]
        val clipY = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13]
        val clipW = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15]
        if (clipW <= 0f) return null
        return ScreenPoint(
            x = (clipX / clipW + 1f) / 2f * width,
            y = (1f - clipY / clipW) / 2f * height,
        )
    }

    /** Moves [outside] along the segment towards [inside] until it reaches depth [z]. */
    private fun clipToZ(outside: Vec3, inside: Vec3, z: Float): Vec3 {
        val t = (z - outside.z) / (inside.z - outside.z)
        return outside + (inside - outside) * t
    }
}
