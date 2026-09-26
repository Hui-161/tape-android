package com.tape.measure.domain.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeasureGeometryTest {

    private val view = identityMatrix()
    private val projection = perspectiveMatrix(fovYDegrees = 90f, aspect = 1f)
    private val size = 1000

    private fun project(p: Vec3, view: FloatArray = this.view) =
        MeasureGeometry.projectPoint(view, projection, p, size, size)

    @Test
    fun pointOnOpticalAxis_projectsToScreenCentre() {
        val p = project(Vec3(0f, 0f, -2f))!!
        assertEquals(500f, p.x, 0.01f)
        assertEquals(500f, p.y, 0.01f)
    }

    @Test
    fun pointAtEdgeOfFieldOfView_projectsToScreenEdge() {
        // 90° field of view: at 1 m distance the visible half-width is 1 m.
        val right = project(Vec3(1f, 0f, -1f))!!
        assertEquals(1000f, right.x, 0.01f)
        assertEquals(500f, right.y, 0.01f)
    }

    @Test
    fun worldUp_isTopOfScreen() {
        val top = project(Vec3(0f, 1f, -1f))!!
        assertEquals(0f, top.y, 0.01f)
    }

    @Test
    fun pointBehindCameraOrInsideNearPlane_isNotProjected() {
        assertNull(project(Vec3(0f, 0f, 1f)))
        assertNull(project(Vec3(0f, 0f, -0.05f)))
    }

    @Test
    fun viewMatrix_isApplied() {
        // Camera moved to z = +1: its view matrix translates the world by −1 on z.
        val movedCamera = translationMatrix(0f, 0f, -1f)
        assertNotNull(project(Vec3(0f, 0f, 0.5f), movedCamera))
        assertNull(project(Vec3(0f, 0f, 1.5f), movedCamera))
    }

    @Test
    fun segmentWithOneEndBehindCamera_isClippedAtNearPlane() {
        // From the screen centre towards a point behind the camera on the right: the line must
        // still leave the screen to the right instead of disappearing.
        val segment = MeasureGeometry.projectSegment(
            view, projection, Vec3(0f, 0f, -1f), Vec3(1f, 0f, 1f), size, size,
        )!!
        assertEquals(500f, segment.start.x, 0.01f)
        assertEquals(500f, segment.start.y, 0.01f)
        // Clipped at z = −0.1 where x = 0.45 → NDC x = 4.5 → far right of the screen.
        assertEquals(2750f, segment.end.x, 0.5f)
        assertEquals(500f, segment.end.y, 0.01f)
    }

    @Test
    fun segmentEntirelyBehindCamera_isNotProjected() {
        assertNull(
            MeasureGeometry.projectSegment(view, projection, Vec3(0f, 0f, 1f), Vec3(1f, 0f, 2f), size, size),
        )
    }

    @Test
    fun segmentInFront_keepsBothEnds() {
        val segment = MeasureGeometry.projectSegment(
            view, projection, Vec3(-1f, 0f, -2f), Vec3(1f, 0f, -2f), size, size,
        )!!
        assertEquals(250f, segment.start.x, 0.01f)
        assertEquals(750f, segment.end.x, 0.01f)
    }
}
