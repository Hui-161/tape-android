package com.tape.measure.ui.screens.measure

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.tape.measure.domain.measure.ScreenPoint
import com.tape.measure.domain.measure.ScreenSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceLabelPositionTest {

    // Screen 1000×2000; the HUD ends at y = 150 and the controls start at y = 1650.
    // Crosshair at (500, 1000) with a ±60 px keep-out; end points ±9 px.
    private val freeArea = IntRect(0, 150, 1000, 1650)
    private val label = IntSize(200, 100)
    private val crosshair = IntRect(440, 940, 560, 1060)

    private fun position(from: ScreenPoint, to: ScreenPoint) = distanceLabelPosition(
        segment = ScreenSegment(from, to),
        labelSize = label,
        freeArea = freeArea,
        crosshair = IntOffset(500, 1000),
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
    fun lineRunningBelowTheScreen_isLabelledBesideItsVisiblePart() {
        // Field video, t = 7 s: start point on the floor below the screen, live end at the
        // crosshair. The label was pushed into the controls and covered the Save button.
        val rect = position(ScreenPoint(500f, 1000f), ScreenPoint(505f, 2600f))!!.rect()

        assertTrue("stays above the controls", rect.bottom <= freeArea.bottom)
        assertTrue("right of the line", rect.left > 500)
        assertEquals("centred on the visible part", 1325f, rect.center.y.toFloat(), 10f)
        assertFalse(rect.overlaps(crosshair))
    }

    @Test
    fun lineCompletelyOffScreen_hasNoLabel() {
        // Field video, t = 18 s: the finished door measurement was behind the user, yet its
        // label stuck to the top edge over the status bar.
        assertNull(position(ScreenPoint(1200f, -800f), ScreenPoint(1300f, -300f)))
    }

    @Test
    fun lineInsideTheControls_hasNoLabel() {
        assertNull(position(ScreenPoint(100f, 1700f), ScreenPoint(300f, 1700f)))
    }

    @Test
    fun lineUnderTheHud_flipsBelowInsteadOfSlidingUnderTheHud() {
        assertEquals(IntOffset(100, 214), position(ScreenPoint(100f, 200f), ScreenPoint(300f, 200f)))
    }

    @Test
    fun preferredSideCoveringTheCrosshair_usesTheOtherSide() {
        assertEquals(IntOffset(400, 1094), position(ScreenPoint(400f, 1080f), ScreenPoint(600f, 1080f)))
    }

    @Test
    fun anyAngle_neverCoversCrosshairEndPointsHudOrControls() {
        val segments = listOf(
            ScreenPoint(100f, 1600f) to ScreenPoint(900f, 1200f),
            ScreenPoint(200f, 300f) to ScreenPoint(800f, 700f),
            ScreenPoint(300f, 1300f) to ScreenPoint(500f, 1000f),
            ScreenPoint(700f, 400f) to ScreenPoint(650f, 1600f),
            ScreenPoint(150f, 900f) to ScreenPoint(850f, 950f),
        )
        for ((start, end) in segments) {
            val p = position(start, end)
            assertNotNull("label for $start–$end", p)
            val rect = p!!.rect()
            assertTrue("inside the free area for $start–$end", rect.top >= freeArea.top && rect.bottom <= freeArea.bottom)
            assertFalse("covers crosshair for $start–$end", rect.overlaps(crosshair))
            assertFalse("covers start for $start–$end", rect.overlaps(pointBox(start)))
            assertFalse("covers end for $start–$end", rect.overlaps(pointBox(end)))
        }
    }

    @Test
    fun zeroLengthLine_getsItsLabelAbove() {
        assertEquals(IntOffset(200, 186), position(ScreenPoint(300f, 300f), ScreenPoint(300f, 300f)))
    }

    @Test
    fun shortLineThroughTheCrosshair_fallsBackBelowIt() {
        val p = position(ScreenPoint(450f, 1000f), ScreenPoint(550f, 1000f))!!
        assertEquals(IntOffset(400, 1074), p)
        assertFalse(p.rect().overlaps(crosshair))
    }
}

class ClipSegmentTest {

    private val bounds = IntRect(0, 150, 1000, 1650)

    private fun clip(x0: Float, y0: Float, x1: Float, y1: Float) =
        clipSegment(ScreenSegment(ScreenPoint(x0, y0), ScreenPoint(x1, y1)), bounds)

    @Test
    fun segmentInside_isUnchanged() {
        assertEquals(ScreenSegment(ScreenPoint(100f, 200f), ScreenPoint(900f, 1600f)), clip(100f, 200f, 900f, 1600f))
    }

    @Test
    fun segmentLeavingThroughTheBottom_isCutAtTheEdge() {
        assertEquals(ScreenSegment(ScreenPoint(500f, 1000f), ScreenPoint(500f, 1650f)), clip(500f, 1000f, 500f, 2500f))
    }

    @Test
    fun segmentCrossingBothSides_keepsTheMiddle() {
        assertEquals(ScreenSegment(ScreenPoint(0f, 500f), ScreenPoint(1000f, 500f)), clip(-100f, 500f, 1100f, 500f))
    }

    @Test
    fun segmentOutside_isNull() {
        assertNull(clip(1200f, 200f, 1300f, 700f))
        assertNull(clip(100f, 20f, 900f, 100f))
    }
}
