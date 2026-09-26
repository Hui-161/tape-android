package com.tape.measure.domain.measure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSelectorTest {

    private fun hit(distance: Float, kind: HitKind = HitKind.PLANE_HORIZONTAL) =
        SurfaceHit(Vec3(0f, 0f, -distance), kind, distance) { null }

    @Test
    fun noHits_isNoTarget() {
        val target = TargetSelector.select(emptyList())
        assertNull(target.hit)
        assertFalse(target.tooFar)
    }

    @Test
    fun nearestSurfaceInRange_isChosen() {
        val near = hit(1.2f)
        assertSame(near, TargetSelector.select(listOf(near, hit(3f))).hit)
    }

    @Test
    fun surfaceBeyondRange_isRefusedAsTooFar() {
        // The field test: a facade about 15 m away gave a 14.8 m "door".
        val target = TargetSelector.select(listOf(hit(14f, HitKind.DEPTH), hit(15f, HitKind.PLANE_VERTICAL)))
        assertNull(target.hit)
        assertTrue(target.tooFar)
    }

    @Test
    fun rangeLimit_isInclusive() {
        val atLimit = hit(TargetSelector.MAX_DISTANCE_METERS)
        assertSame(atLimit, TargetSelector.select(listOf(atLimit)).hit)
    }

    @Test
    fun planeJustBehindDepthHit_isPreferred() {
        val plane = hit(1.56f, HitKind.PLANE_VERTICAL)
        assertSame(plane, TargetSelector.select(listOf(hit(1.5f, HitKind.DEPTH), plane)).hit)
    }

    @Test
    fun objectInFrontOfPlane_keepsTheDepthHit() {
        val box = hit(1.2f, HitKind.DEPTH)
        assertSame(box, TargetSelector.select(listOf(box, hit(1.6f))).hit)
    }

    @Test
    fun depthOnly_isUsed() {
        val depth = hit(2f, HitKind.DEPTH)
        assertSame(depth, TargetSelector.select(listOf(depth)).hit)
    }
}
