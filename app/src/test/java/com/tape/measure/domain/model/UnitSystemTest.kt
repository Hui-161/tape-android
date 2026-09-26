package com.tape.measure.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitSystemTest {

    @Test
    fun format_convertsFromMetres() {
        assertEquals("84.2 cm", UnitSystem.CM.format(0.842f, Locale.US))
        assertEquals("0.84 m", UnitSystem.M.format(0.842f, Locale.US))
        assertEquals("33.1\"", UnitSystem.IN.format(0.842f, Locale.US))
        assertEquals("2.76 ft", UnitSystem.FT.format(0.842f, Locale.US))
    }

    @Test
    fun format_usesLocaleDecimalSeparator() {
        assertEquals("1,50 m", UnitSystem.M.format(1.5f, Locale.GERMANY))
    }

    @Test
    fun uncertainty_usesCentimetresOrInches() {
        assertEquals("±1.2 cm", UnitSystem.M.formatUncertainty(0.012f, Locale.US))
        assertEquals("±1.2 cm", UnitSystem.CM.formatUncertainty(0.012f, Locale.US))
        assertEquals("±1.0\"", UnitSystem.FT.formatUncertainty(0.0254f, Locale.US))
    }

    @Test
    fun next_cyclesThroughAllUnits() {
        assertEquals(UnitSystem.M, UnitSystem.CM.next())
        assertEquals(UnitSystem.IN, UnitSystem.M.next())
        assertEquals(UnitSystem.FT, UnitSystem.IN.next())
        assertEquals(UnitSystem.CM, UnitSystem.FT.next())
    }
}
