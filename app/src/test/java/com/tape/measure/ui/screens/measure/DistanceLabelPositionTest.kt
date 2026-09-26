package com.tape.measure.ui.screens.measure

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.tape.measure.domain.measure.ScreenPoint
import com.tape.measure.domain.measure.ScreenSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceLabelPositionTest {

    // Screen 1000×2000 → crosshair at (500, 1000) with a ±60 px keep-out; end points ±9 px.
    private val screen = IntSize(1000, 2000)
    private val label = IntSize(200, 100)
    private val crosshair = IntRect(440, 940, 560, 1060)

    private fun position(from: ScreenPoint, to: ScreenPoint) = distanceLabelPosition(
        segment = ScreenSegment(from, to),
        labelSize = label,
        screenSize = screen,
        gap = 14,
        crosshairKeepOut = 60,
        pointKeepOut = 9,
    )

    private fun IntOffset.rect() = IntRect(this, label)

    private fun pointBox(p: ScreenPoint) = IntRect(p.x.toInt() - 9, p.y.toInt() - 9, p.x.toInt() + 9, p.y.toInt() + 9)

    @Test
    fun horizontalLine_labelSitsAboveItsMidpoint() {
        assertEquals(IntOffset(100, 1386), position(ScreenPoint(100f, 1500f), ScreenPoint(300f, 1500f)))
    }

    @Test
    fun verticalLine_labelSitsToTheRightInsteadOfOnTheLine() {
        assertEquals(IntOffset(514, 1350), position(ScreenPoint(500f, 1200f), ScreenPoint(500f, 1600f)))
    }

    @Test
    fun lineDownFromTheCrosshair_keepsEndPointAndCrosshairVisible() {
        // The field-test screenshot: the label used to sit on the lower end point.
        val start = ScreenPoint(500f, 1000f)
        val end = ScreenPoint(490f, 1300f)
        val rect = position(start, end).rect()

        assertTrue("label is right of the line", rect.left > 495)
        assertEquals("label is centred on the midpoint", 1150f, rect.center.y.toFloat(), 10f)
        assertFalse(rect.overlaps(crosshair))
        assertFalse(rect.overlaps(pointBox(start)))
        assertFalse(rect.overlaps(pointBox(end)))
    }

    @Test
    fun preferredSideCoveringTheCrosshair_usesTheOtherSide() {
        assertEquals(IntOffset(400, 1094), position(ScreenPoint(400f, 1080f), ScreenPoint(600f, 1080f)))
    }

    @Test
    fun anyAngle_neverCoversCrosshairOrEndPoints() {
        val segments = listOf(
            ScreenPoint(100f, 1800f) to ScreenPoint(900f, 1200f),
            ScreenPoint(200f, 300f) to ScreenPoint(800f, 700f),
            ScreenPoint(300f, 1300f) to ScreenPoint(500f, 1000f),
            ScreenPoint(700f, 400f) to ScreenPoint(650f, 1700f),
            ScreenPoint(150f, 900f) to ScreenPoint(850f, 950f),
        )
        for ((start, end) in segments) {
            val rect = position(start, end).rect()
            assertFalse("covers crosshair for $start–$end", rect.overlaps(crosshair))
            assertFalse("covers start for $start–$end", rect.overlaps(pointBox(start)))
            assertFalse("covers end for $start–$end", rect.overlaps(pointBox(end)))
        }
    }

    @Test
    fun lineOffScreen_keepsTheLabelOnScreen() {
        assertEquals(IntOffset(800, 1900), position(ScreenPoint(1200f, 2400f), ScreenPoint(1400f, 2600f)))
        assertEquals(IntOffset(0, 0), position(ScreenPoint(-400f, -300f), ScreenPoint(-100f, -50f)))
    }

    @Test
    fun zeroLengthLine_getsItsLabelAbove() {
        assertEquals(IntOffset(200, 186), position(ScreenPoint(300f, 300f), ScreenPoint(300f, 300f)))
    }

    @Test
    fun shortLineThroughTheCrosshair_fallsBackBelowIt() {
        val p = position(ScreenPoint(450f, 1000f), ScreenPoint(550f, 1000f))
        assertEquals(IntOffset(400, 1074), p)
        assertFalse(p.rect().overlaps(crosshair))
    }
}
