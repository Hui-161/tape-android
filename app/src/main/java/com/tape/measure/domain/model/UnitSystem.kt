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
     * Formats a measurement uncertainty. Metric units show centimetres and imperial units show
     * inches, since "±0.01 m" or "±0.03 ft" are hard to read.
     */
    fun formatUncertainty(meters: Float, locale: Locale = Locale.getDefault()): String = when (this) {
        CM, M -> "±%.1f cm".format(locale, meters * 100f)
        IN, FT -> "±%.1f\"".format(locale, meters * INCHES_PER_METER)
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
