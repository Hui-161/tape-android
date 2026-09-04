package com.tape.measure.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shape tokens — restrained radii.
 * Locked: 8-12px cards, 4-6px small elements. No oversized pill buttons.
 */
object TapeShape {
    val Small = RoundedCornerShape(6.dp)        // chips, small buttons, inputs
    val Medium = RoundedCornerShape(10.dp)      // cards, sheets
    val Large = RoundedCornerShape(12.dp)       // modals, large cards
    val Full = RoundedCornerShape(50)           // only for circular indicators (e.g. point dots)
}