package com.tape.measure.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Tape theme — dark baseline, light as a setting (not the default).
 *
 * Dark scheme is the primary surface for the v1.0 build. Light scheme
 * is wired but minimal — it'll get a proper warm-amber light palette
 * once we ship the dark version and validate the wedge.
 *
 * Every role Material components read is set explicitly. Roles left out fall back to
 * Material's purple baseline (chips, sheets, dialogs and snackbars use the container roles).
 */

internal val TapeDarkColors = darkColorScheme(
    primary = AmberBottom,
    onPrimary = InkBackground,
    primaryContainer = AmberTop,
    onPrimaryContainer = InkBackground,
    inversePrimary = AmberBottom,

    secondary = AmberTop,
    onSecondary = InkBackground,
    secondaryContainer = InkSurfaceHigh,
    onSecondaryContainer = AmberTop,

    tertiary = ConfidenceHigh,
    onTertiary = InkBackground,
    tertiaryContainer = InkSurfaceHigh,
    onTertiaryContainer = ConfidenceHigh,

    background = InkBackground,
    onBackground = InkTextPrimary,

    surface = InkSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = InkTextSecondary,
    inverseSurface = InkTextPrimary,
    inverseOnSurface = InkBackground,
    surfaceDim = InkBackground,
    surfaceBright = InkSurfaceHigh,
    surfaceContainerLowest = InkBackground,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = InkSurfaceHigh,

    outline = InkOutline,
    outlineVariant = InkOutline,

    error = ConfidenceLow,
    onError = InkBackground,
    errorContainer = InkSurfaceHigh,
    onErrorContainer = ConfidenceLow,
)

// Provisional light palette — warm cream + amber. Refine after dark ships.
private val LightBackground = Color(0xFFFAF6EE)
private val LightSurface = Color(0xFFFFF9F0)
private val LightSurfaceVariant = Color(0xFFF1E9D8)
private val LightOnSurfaceVariant = Color(0xFF5A4F3F)
private val LightOutline = Color(0xFFD4CAB5)

internal val TapeLightColors = lightColorScheme(
    primary = AmberBottom,
    onPrimary = Color.White,
    primaryContainer = AmberTop,
    onPrimaryContainer = InkBackground,
    inversePrimary = AmberTop,

    secondary = AmberTop,
    onSecondary = InkBackground,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = InkBackground,

    tertiary = ConfidenceHigh,
    onTertiary = InkBackground,
    tertiaryContainer = LightSurfaceVariant,
    onTertiaryContainer = InkBackground,

    background = LightBackground,
    onBackground = InkBackground,
    surface = LightSurface,
    onSurface = InkBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    inverseSurface = InkSurface,
    inverseOnSurface = InkTextPrimary,
    surfaceDim = LightSurfaceVariant,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceVariant,

    outline = LightOutline,
    outlineVariant = LightOutline,

    errorContainer = LightSurfaceVariant,
    onErrorContainer = InkBackground,
)

@Composable
fun TapeTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val resolvedDark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = if (resolvedDark) TapeDarkColors else TapeLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !resolvedDark
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TapeTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = TapeShape.Small,
            small = TapeShape.Small,
            medium = TapeShape.Medium,
            large = TapeShape.Large,
            extraLarge = TapeShape.Large,
        ),
        content = content,
    )
}