package com.tape.measure.domain.measure

import kotlin.math.tan

/** Column-major identity: camera at the world origin, looking down −Z. */
fun identityMatrix() = FloatArray(16).apply {
    this[0] = 1f
    this[5] = 1f
    this[10] = 1f
    this[15] = 1f
}

/** Column-major translation, e.g. the view matrix of a camera at (−x, −y, −z). */
fun translationMatrix(x: Float, y: Float, z: Float) = identityMatrix().apply {
    this[12] = x
    this[13] = y
    this[14] = z
}

/** Column-major OpenGL perspective projection, as ARCore returns it. */
fun perspectiveMatrix(
    fovYDegrees: Float = 90f,
    aspect: Float = 1f,
    near: Float = MeasureGeometry.NEAR_CLIP,
    far: Float = MeasureGeometry.FAR_CLIP,
) = FloatArray(16).apply {
    val f = 1f / tan(Math.toRadians(fovYDegrees / 2.0)).toFloat()
    this[0] = f / aspect
    this[5] = f
    this[10] = (far + near) / (near - far)
    this[11] = -1f
    this[14] = 2f * far * near / (near - far)
}
