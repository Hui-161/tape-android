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
 */

private val TapeDarkColors = darkColorScheme(
    primary = AmberBottom,
    onPrimary = InkBackground,
    primaryContainer = AmberTop,
    onPrimaryContainer = InkBackground,

    secondary = AmberTop,
    onSecondary = InkBackground,

    tertiary = ConfidenceHigh,
    onTertiary = InkBackground,

    background = InkBackground,
    onBackground = InkTextPrimary,

    surface = InkSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = InkTextSecondary,

    outline = InkOutline,
    outlineVariant = InkOutline,

    error = ConfidenceLow,
    onError = InkBackground,
)

private val TapeLightColors = lightColorScheme(
    // Provisional light palette — warm cream + amber. Refine after dark ships.
    primary = AmberBottom,
    onPrimary = Color.White,
    primaryContainer = AmberTop,
    onPrimaryContainer = InkBackground,

    secondary = AmberTop,
    onSecondary = InkBackground,

    background = Color(0xFFFAF6EE),
    onBackground = InkBackground,
    surface = Color(0xFFFFF9F0),
    onSurface = InkBackground,
    surfaceVariant = Color(0xFFF1E9D8),
    onSurfaceVariant = Color(0xFF5A4F3F),

    outline = Color(0xFFD4CAB5),
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