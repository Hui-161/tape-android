package com.tape.measure.domain.measure

import kotlin.math.sqrt

/** Immutable 3-D vector in ARCore world space (metres). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun distanceTo(o: Vec3): Float = (this - o).length()

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun normalized(): Vec3 = times(1f / length())
}
