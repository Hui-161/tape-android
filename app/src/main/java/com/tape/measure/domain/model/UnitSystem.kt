package com.tape.measure.domain.model

/**
 * The four units the app can display measurements in.
 *
 * [format] converts a raw meter value to a human-readable string.
 * [next] cycles to the next unit for the in-AR unit-toggle button.
 */
enum class UnitSystem {
    CM, M, IN, FT;

    /** Formats [meters] as a display string with appropriate precision. */
    fun format(meters: Float): String = when (this) {
        CM -> "%.1f cm".format(meters * 100f)
        M  -> "%.2f m".format(meters)
        IN -> "%.1f\"".format(meters * 39.3701f)
        FT -> "%.2f ft".format(meters * 3.28084f)
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
}
