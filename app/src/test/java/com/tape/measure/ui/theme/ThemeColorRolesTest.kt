package com.tape.measure.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Material components read container, inverse and surface-container roles that a partial
 * scheme leaves on Material's purple baseline (selected chips, bottom sheets, dialogs,
 * snackbars). Every one of them must come from the Tape palette.
 */
class ThemeColorRolesTest {

    private val componentRoles: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "tertiary" to { it.tertiary },
        "onTertiary" to { it.onTertiary },
        "secondaryContainer" to { it.secondaryContainer },
        "onSecondaryContainer" to { it.onSecondaryContainer },
        "tertiaryContainer" to { it.tertiaryContainer },
        "onTertiaryContainer" to { it.onTertiaryContainer },
        "errorContainer" to { it.errorContainer },
        "onErrorContainer" to { it.onErrorContainer },
        "inverseSurface" to { it.inverseSurface },
        "inverseOnSurface" to { it.inverseOnSurface },
        "inversePrimary" to { it.inversePrimary },
        "outlineVariant" to { it.outlineVariant },
        "surfaceDim" to { it.surfaceDim },
        "surfaceBright" to { it.surfaceBright },
        "surfaceContainerLowest" to { it.surfaceContainerLowest },
        "surfaceContainerLow" to { it.surfaceContainerLow },
        "surfaceContainer" to { it.surfaceContainer },
        "surfaceContainerHigh" to { it.surfaceContainerHigh },
        "surfaceContainerHighest" to { it.surfaceContainerHighest },
    )

    @Test
    fun darkScheme_setsEveryComponentRole() = assertNoBaselineRoles(TapeDarkColors, darkColorScheme())

    @Test
    fun lightScheme_setsEveryComponentRole() = assertNoBaselineRoles(TapeLightColors, lightColorScheme())

    private fun assertNoBaselineRoles(scheme: ColorScheme, baseline: ColorScheme) {
        val leftovers = componentRoles.filter { (_, role) -> role(scheme) == role(baseline) }.map { it.first }
        assertTrue("Roles still on Material's baseline palette: $leftovers", leftovers.isEmpty())
    }
}
