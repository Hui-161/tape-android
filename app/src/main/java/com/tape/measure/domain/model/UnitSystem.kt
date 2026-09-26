package com.tape.measure.domain.model

import java.util.Locale

/**
 * The four units the app can display measurements in.
 *
 * [format] converts a raw meter value to a human-readable string.
 * [next] cycles to the next unit for the in-AR unit-toggle button.
 */
enum class UnitSystem {
    CM, M, IN, FT;

    /** Formats [meters] as a display string with appropriate precision. */
    fun format(meters: Float, locale: Locale = Locale.getDefault()): String = when (this) {
        CM -> "%.1f cm".format(locale, meters * 100f)
        M  -> "%.2f m".format(locale, meters)
        IN -> "%.1f\"".format(locale, meters * INCHES_PER_METER)
        FT -> "%.2f ft".format(locale, meters * FEET_PER_METER)
    }

    /**
     * Formats a measurement uncertainty with no more digits than it deserves. Small errors are
     * shown in centimetres or inches, since "±0.01 m" or "±0.03 ft" are hard to read; the large
     * errors of far estimates in metres or feet.
     */
    fun formatUncertainty(meters: Float, locale: Locale = Locale.getDefault()): String = when (this) {
        CM, M -> when {
            meters < 0.1f -> "±%.1f cm".format(locale, meters * 100f)
            meters < 1f -> "±%.0f cm".format(locale, meters * 100f)
            else -> "±%.1f m".format(locale, meters)
        }
        IN, FT -> {
            val inches = meters * INCHES_PER_METER
            if (inches < 12f) "±%.1f\"".format(locale, inches)
            else "±%.1f ft".format(locale, meters * FEET_PER_METER)
        }
    }

    /** Short label shown on the unit-toggle chip. */
    fun label(): String = when (this) {
        CM -> "cm"
        M  -> "m"
        IN -> "in"
        FT -> "ft"
    }

    /** Returns the next unit in the cycle CM → M → IN → FT → CM. */
    fun next(): UnitSystem = entries[(ordinal + 1) % entries.size]

    private companion object {
        const val INCHES_PER_METER = 39.3701f
        const val FEET_PER_METER = 3.28084f
    }
}
