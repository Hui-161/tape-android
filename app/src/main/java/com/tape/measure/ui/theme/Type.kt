package com.tape.measure.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.tape.measure.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs,
)

private val PlexSans = FontFamily(
    Font(GoogleFont("IBM Plex Sans"), provider, FontWeight.Normal),
    Font(GoogleFont("IBM Plex Sans"), provider, FontWeight.Medium),
    Font(GoogleFont("IBM Plex Sans"), provider, FontWeight.SemiBold),
)

private val PlexMono = FontFamily(
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.Normal),
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.Medium),
)

val TapeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily   = PlexSans,
        fontWeight   = FontWeight.Medium,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily    = PlexSans,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * Measurement-specific text styles using IBM Plex Mono — renders digits like
 * a real tape measure: fixed-width, industrial.
 */
object TapeMeasurement {
    val Large: TextStyle = TextStyle(
        fontFamily    = PlexMono,
        fontWeight    = FontWeight.Medium,
        fontSize      = 56.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-1).sp,
    )
    val Medium: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize   = 32.sp,
        lineHeight = 38.sp,
    )
    val Small: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 18.sp,
    )
}
