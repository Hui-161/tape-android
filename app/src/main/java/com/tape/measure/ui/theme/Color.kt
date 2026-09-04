package com.tape.measure.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tape color tokens.
 * Locked visual direction: yellow-amber gradient on warm near-black.
 * See /Users/akshaysharma/Downloads/Work/Projects/Tape/.hermes/plans/execution.md → Visual direction.
 */

// Surfaces (dark baseline)
val InkBackground = Color(0xFF0F0E0C)      // near-black warm — main canvas
val InkSurface = Color(0xFF1F1C18)         // raised surface (cards, sheets)
val InkSurfaceHigh = Color(0xFF2B2722)     // higher-raised (modal, popover)
val InkOutline = Color(0xFF3A352E)         // dividers, outlines

// Text (warm neutrals, high contrast)
val InkTextPrimary = Color(0xFFF4ECDF)     // warm off-white
val InkTextSecondary = Color(0xFFB6A88F)   // muted warm
val InkTextDisabled = Color(0xFF6E6557)

// Amber primary (gradient endpoints)
val AmberTop = Color(0xFFFFC857)           // bright amber-yellow
val AmberBottom = Color(0xFFE89B3C)        // deep amber

// Confidence states
val ConfidenceHigh = Color(0xFF7BB87B)     // soft green
val ConfidenceMedium = Color(0xFFE89B3C)    // amber (same as primary — intentional)
val ConfidenceLow = Color(0xFFD16A6A)      // soft red