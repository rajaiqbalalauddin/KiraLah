package com.buyless.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Design tokens from the Buyless canvas. One source so screens never hard-code hex values. */
object BColors {
    val Violet = Color(0xFF5B3DF5)
    val VioletDark = Color(0xFF4A2EDB)
    val VioletRing = Color(0xFF6E54FF)
    val VioletSoft = Color(0xFFECE8FF)
    val VioletOnDark = Color(0xFFD9D1FF)
    val Lavender = Color(0xFFF5F3FF)
    val Border = Color(0xFFE6E1FA)
    val Divider = Color(0xFFF0EDFB)
    val Ink = Color(0xFF16132B)
    val Muted = Color(0xFF5E5A78)
    val Faint = Color(0xFF6E6A86)
    val Yellow = Color(0xFFFFC940)
    val YellowInk = Color(0xFF3D2A00)
    val Coral = Color(0xFFFF6B3D)
    val Green = Color(0xFF0B7A5F)
    val GreenSoft = Color(0xFFD8F5EA)
    val AmberSoft = Color(0xFFFFF1D6)
    val AmberInk = Color(0xFF7A4200)
    val Amber = Color(0xFF9A5200)
    val CoralSoft = Color(0xFFFFE6DC)
    val CoralInk = Color(0xFFC23F12)
    val BlueSoft = Color(0xFFDDEBFF)
    val BlueInk = Color(0xFF1F55C7)
    val Danger = Color(0xFFB3261E)
    val White = Color.White

    /** Fallback tile colours for apps whose real icon cannot be loaded. */
    val TilePalette = listOf(
        Color(0xFFFFC940) to Color(0xFF3D2A00),
        Color(0xFF0FA3A3) to Color.White,
        Color(0xFF2563EB) to Color.White,
        Color(0xFFFF6B3D) to Color.White,
        Color(0xFF5B3DF5) to Color.White,
        Color(0xFF10B981) to Color(0xFF063B2B),
    )
}

// System sans in heavy weights stands in for Bricolage Grotesque / Plus Jakarta Sans. To use the
// real fonts, drop the .ttf files in res/font and swap these two FontFamily values.
private val Display = FontFamily.SansSerif
private val Body = FontFamily.SansSerif

val BuylessTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
)

private val BuylessColorScheme = lightColorScheme(
    primary = BColors.Violet,
    onPrimary = BColors.White,
    primaryContainer = BColors.VioletSoft,
    onPrimaryContainer = BColors.VioletDark,
    secondary = BColors.Yellow,
    onSecondary = BColors.YellowInk,
    background = BColors.Lavender,
    onBackground = BColors.Ink,
    surface = BColors.White,
    onSurface = BColors.Ink,
    surfaceVariant = BColors.Lavender,
    onSurfaceVariant = BColors.Muted,
    surfaceContainer = BColors.White,
    outline = BColors.Border,
    outlineVariant = BColors.Divider,
    error = BColors.Danger,
)

private val BuylessShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/** Light only for now. Dark mode was listed as open for exploration in the design brief. */
@Composable
fun BuylessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuylessColorScheme,
        typography = BuylessTypography,
        shapes = BuylessShapes,
        content = content,
    )
}
