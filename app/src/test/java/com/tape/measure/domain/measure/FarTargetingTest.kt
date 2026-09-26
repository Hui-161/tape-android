package com.tape.measure.domain.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.math.tan

class FarTargetingTest {

    /** Phone held at 1.5 m above the ground at y = 0. */
    private val eye = Vec3(0f, 1.5f, 0f)
    private val projection = perspectiveMatrix()

    private fun rayTowards(target: Vec3, from: Vec3 = eye) =
        FarTargeting.centerRay(lookAtMatrix(from, target), projection)

    /** Where a ray from [eye] that is [degrees] below the horizon passes 10 m ahead. */
    private fun belowHorizon(degrees: Double) =
        Vec3(0f, 1.5f - 10f * tan(Math.toRadians(degrees)).toFloat(), -10f)

    private fun assertVec(expected: Vec3, actual: Vec3, tolerance: Float = 1e-3f) {
        assertEquals("x", expected.x, actual.x, tolerance)
        assertEquals("y", expected.y, actual.y, tolerance)
        assertEquals("z", expected.z, actual.z, tolerance)
    }

    // ── centerRay ────────────────────────────────────────────────────────────

    @Test
    fun centerRay_ofTheIdentityViewLooksDownNegativeZ() {
        val ray = FarTargeting.centerRay(identityMatrix(), projection)

        assertVec(Vec3(0f, 0f, 0f), ray.origin)
        assertVec(Vec3(0f, 0f, -1f), ray.direction)
    }

    @Test
    fun centerRay_followsTheCameraPose() {
        val from = Vec3(1f, 1.5f, 2f)
        val target = Vec3(4f, 0f, -2f)

        val ray = rayTowards(target, from)

        assertVec(from, ray.origin)
        assertVec((target - from).normalized(), ray.direction)
    }

    @Test
    fun centerRay_passesThroughTheScreenCentreWithAnOffCentrePrincipalPoint() {
        val offCentre = perspectiveMatrix().apply {
            this[8] = 0.2f
            this[9] = -0.1f
        }
        val view = lookAtMatrix(Vec3(1f, 1.5f, 2f), Vec3(4f, 0f, -2f))

        val ray = FarTargeting.centerRay(view, offCentre)
        val projected = MeasureGeometry.projectPoint(view, offCentre, ray.origin + ray.direction * 5f, 1000, 1000)

        assertNotNull(projected)
        assertEquals(500f, projected!!.x, 0.5f)
        assertEquals(500f, projected.y, 0.5f)
    }

    // ── groundTarget ─────────────────────────────────────────────────────────

    @Test
    fun groundTarget_findsADistantPointOnTheGround() {
        val target = FarTargeting.groundTarget(rayTowards(Vec3(0f, 0f, -10f)), groundY = 0f)

        assertNotNull(target)
        assertVec(Vec3(0f, 0f, -10f), target!!.position)
        assertEquals(HitKind.GROUND_FAR, target.kind)
        assertEquals(sqrt(100f + 1.5f * 1.5f), target.distanceMeters, 1e-3f)
        // 0.25° at 10 m from 1.5 m up: ±30 cm; 2 cm of camera height: ±13 cm.
        assertEquals(0.33f, target.uncertaintyMeters, 0.01f)
    }

    @Test
    fun groundTarget_isRelativeToTheGroundHeight() {
        val target = FarTargeting.groundTarget(
            rayTowards(Vec3(0f, -2f, -10f), from = Vec3(0f, -0.5f, 0f)),
            groundY = -2f,
        )

        assertVec(Vec3(0f, -2f, -10f), target!!.position)
    }

    @Test
    fun groundTarget_growsLessPreciseWithDistance() {
        val near = FarTargeting.groundTarget(rayTowards(Vec3(0f, 0f, -5f)), 0f)!!
        val far = FarTargeting.groundTarget(rayTowards(Vec3(0f, 0f, -20f)), 0f)!!

        // Relative error: about 2 % at 5 m, 6 % at 20 m.
        assertTrue(far.uncertaintyMeters / far.distanceMeters > 2 * near.uncertaintyMeters / near.distanceMeters)
    }

    @Test
    fun groundTarget_refusesRaysAboveOrCloseToTheHorizon() {
        assertNull(FarTargeting.groundTarget(rayTowards(Vec3(0f, 2f, -10f)), 0f))
        // 1° down would meet the ground 86 m away, at too grazing an angle.
        assertNull(FarTargeting.groundTarget(rayTowards(belowHorizon(1.0)), 0f))
    }

    @Test
    fun groundTarget_endsAtTheMaximumDistance() {
        assertNotNull(FarTargeting.groundTarget(rayTowards(Vec3(0f, 0f, -29f)), 0f))
        // 2.5° down meets the ground 34 m away.
        assertNull(FarTargeting.groundTarget(rayTowards(belowHorizon(2.5)), 0f))
    }

    @Test
    fun groundTarget_refusesAPlaneTooCloseBelowTheCamera() {
        // A table top 0.75 m below the phone is not the ground.
        assertNull(FarTargeting.groundTarget(rayTowards(Vec3(0f, 0.75f, -5f)), groundY = 0.75f))
    }

    // ── verticalTarget ───────────────────────────────────────────────────────

    @Test
    fun verticalTarget_findsTheTopOfADistantDoor() {
        val base = Vec3(0f, 0f, -15f)

        val target = FarTargeting.verticalTarget(rayTowards(Vec3(0f, 2f, -15f)), base, baseUncertainty = 0.69f)

        assertNotNull(target)
        assertVec(Vec3(0f, 2f, -15f), target!!.position)
        assertEquals(HitKind.VERTICAL_FAR, target.kind)
        // Far less than the base point's ±69 cm: the height hardly depends on the base distance.
        assertEquals(0.072f, target.uncertaintyMeters, 0.002f)
    }

    @Test
    fun verticalTarget_worksFromTheSide() {
        val base = Vec3(0f, 0f, -10f)
        val from = Vec3(10f, 1.5f, -10f)

        val target = FarTargeting.verticalTarget(rayTowards(Vec3(0f, 5f, -10f), from), base, 0.1f)

        assertVec(Vec3(0f, 5f, -10f), target!!.position)
    }

    @Test
    fun verticalTarget_needsTheStartPointAheadAndInRange() {
        val base = Vec3(0f, 0f, -10f)
        // Looking away from the start point.
        assertNull(FarTargeting.verticalTarget(rayTowards(Vec3(0f, 3f, 10f)), base, 0.1f))
        // Standing right above it.
        assertNull(FarTargeting.verticalTarget(rayTowards(Vec3(1f, 5f, -10f), from = Vec3(0f, 1.5f, -10f)), base, 0.1f))
        // Too far away.
        assertNull(FarTargeting.verticalTarget(rayTowards(Vec3(0f, 25f, -40f)), Vec3(0f, 0f, -40f), 0.1f))
    }

    // ── target ───────────────────────────────────────────────────────────────

    @Test
    fun target_estimatesOnTheGroundBelowAndAboveTheStartPointAboveTheHorizon() {
        val base = Vec3(0f, 0f, -15f)
        val down = rayTowards(Vec3(0f, 0f, -10f))
        val up = rayTowards(Vec3(0f, 2f, -15f))

        assertEquals(HitKind.GROUND_FAR, FarTargeting.target(down, groundY = 0f, base = base, baseUncertainty = 0.1f)?.kind)
        assertEquals(HitKind.VERTICAL_FAR, FarTargeting.target(up, groundY = 0f, base = base, baseUncertainty = 0.1f)?.kind)
        assertNull(FarTargeting.target(up, groundY = 0f, base = null, baseUncertainty = 0f))
        assertNull(FarTargeting.target(down, groundY = null, base = base, baseUncertainty = 0.1f))
    }

    @Test
    fun groundPoint_isHiddenByASurfaceClearlyInFrontOfIt() {
        val ground = FarTargeting.groundTarget(rayTowards(Vec3(0f, 0f, -15f)), 0f)!!

        assertTrue("a wall at 10 m", FarTargeting.isHidden(ground, surfaceMeters = 10f))
        assertFalse("the ground itself, roughly", FarTargeting.isHidden(ground, surfaceMeters = 13f))
        assertFalse("nothing seen", FarTargeting.isHidden(ground, surfaceMeters = null))
        val height = FarTarget(Vec3(0f, 2f, -15f), HitKind.VERTICAL_FAR, 15f, 0.07f)
        assertFalse("heights are not checked", FarTargeting.isHidden(height, surfaceMeters = 5f))
    }

    @Test
    fun hasGround_needsAKnownPlaneWellBelowTheCamera() {
        val ray = rayTowards(Vec3(0f, 0f, -10f))

        assertTrue(FarTargeting.hasGround(ray, 0f))
        assertFalse(FarTargeting.hasGround(ray, 1f))
        assertFalse(FarTargeting.hasGround(ray, null))
    }

    // ── FarTargetSmoother ────────────────────────────────────────────────────

    private fun far(position: Vec3, kind: HitKind = HitKind.GROUND_FAR) = FarTarget(position, kind, 10f, 0.3f)

    @Test
    fun smoother_dampsTremor() {
        val smoother = FarTargetSmoother()

        assertVec(Vec3(0f, 0f, -10f), smoother.smooth(far(Vec3(0f, 0f, -10f))))
        // 40 cm at 10 m is tremor: a quarter of the way per frame.
        assertVec(Vec3(0.1f, 0f, -10f), smoother.smooth(far(Vec3(0.4f, 0f, -10f))))
    }

    @Test
    fun smoother_followsAimingAtSomethingElseAtOnce() {
        val smoother = FarTargetSmoother()
        smoother.smooth(far(Vec3(0f, 0f, -10f)))

        assertVec(Vec3(3f, 0f, -10f), smoother.smooth(far(Vec3(3f, 0f, -10f))))
    }

    @Test
    fun smoother_startsOverForAnotherKindAndAfterReset() {
        val smoother = FarTargetSmoother()
        smoother.smooth(far(Vec3(0f, 0f, -10f)))

        assertVec(Vec3(0.4f, 0f, -10f), smoother.smooth(far(Vec3(0.4f, 0f, -10f), HitKind.VERTICAL_FAR)))
        smoother.reset()
        assertVec(Vec3(0.8f, 0f, -10f), smoother.smooth(far(Vec3(0.8f, 0f, -10f), HitKind.VERTICAL_FAR)))
    }
}
