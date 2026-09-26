package com.tape.measure.ui.screens.measure

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.tape.measure.domain.measure.ScreenPoint
import com.tape.measure.domain.measure.ScreenSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DistanceLabelPositionTest {

    // Screen 1000×2000 → crosshair at (500, 1000), keep-out ±60 px.
    private val screen = IntSize(1000, 2000)
    private val label = IntSize(200, 100)
    private val keepOut = IntRect(440, 940, 560, 1060)

    private fun position(from: ScreenPoint, to: ScreenPoint) = distanceLabelPosition(
        segment = ScreenSegment(from, to),
        labelSize = label,
        screenSize = screen,
        gap = 10,
        crosshairKeepOut = 60,
    )

    private fun IntOffset.coversCrosshair() = IntRect(this, label).overlaps(keepOut)

    @Test
    fun awayFromCrosshair_sitsCentredAboveTheMidpoint() {
        assertEquals(IntOffset(100, 1390), position(ScreenPoint(100f, 1500f), ScreenPoint(300f, 1500f)))
    }

    @Test
    fun liveLineEndingAtCrosshair_movesBelowInsteadOfCoveringIt() {
        val p = position(ScreenPoint(300f, 1150f), ScreenPoint(500f, 1000f))
        assertEquals(IntOffset(300, 1085), p)
        assertFalse(p.coversCrosshair())
    }

    @Test
    fun midpointOnCrosshair_goesBesideIt() {
        val p = position(ScreenPoint(450f, 1000f), ScreenPoint(550f, 1000f))
        assertEquals(IntOffset(570, 950), p)
        assertFalse(p.coversCrosshair())
    }

    @Test
    fun midpointOffScreen_keepsTheLabelOnScreen() {
        assertEquals(IntOffset(0, 0), position(ScreenPoint(-400f, -300f), ScreenPoint(0f, 0f)))
        assertEquals(IntOffset(800, 1900), position(ScreenPoint(1200f, 2400f), ScreenPoint(1400f, 2600f)))
    }
}
