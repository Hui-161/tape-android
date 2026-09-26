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

/** Column-major view matrix of a camera at [eye] looking at [target], like gluLookAt. */
fun lookAtMatrix(eye: Vec3, target: Vec3, up: Vec3 = Vec3(0f, 1f, 0f)): FloatArray {
    val forward = (target - eye).normalized()
    val side = cross(forward, up).normalized()
    val cameraUp = cross(side, forward)
    return FloatArray(16).apply {
        this[0] = side.x
        this[4] = side.y
        this[8] = side.z
        this[12] = -side.dot(eye)
        this[1] = cameraUp.x
        this[5] = cameraUp.y
        this[9] = cameraUp.z
        this[13] = -cameraUp.dot(eye)
        this[2] = -forward.x
        this[6] = -forward.y
        this[10] = -forward.z
        this[14] = forward.dot(eye)
        this[15] = 1f
    }
}

private fun cross(a: Vec3, b: Vec3) = Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
