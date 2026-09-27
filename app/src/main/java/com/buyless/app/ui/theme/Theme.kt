package com.buyless.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** How the user wants the app to look. Stored by name in AppPrefs. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Whether night mode is on right now. Plain snapshot state outside composition, so it can be set
 * from MainActivity (the phone's setting) and from Settings (the user's choice) without writing
 * state in the middle of a composition.
 */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.SYSTEM)

    /** The phone's own dark mode. The activity is recreated when it changes, which refreshes this. */
    var systemDark by mutableStateOf(false)

    val isDark: Boolean get() = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && systemDark)
}

/** One full set of colour tokens. There is one for day and one for night. */
@Immutable
class Palette(
    val violet: Color,
    val violetDark: Color,
    val violetRing: Color,
    val violetSoft: Color,
    val violetOnDark: Color,
    val lavender: Color,
    val surface: Color,
    val border: Color,
    val divider: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val coral: Color,
    val green: Color,
    val greenSoft: Color,
    val amberSoft: Color,
    val amberInk: Color,
    val amber: Color,
    val coralSoft: Color,
    val coralInk: Color,
    val blueSoft: Color,
    val blueInk: Color,
    val danger: Color,
)

private val DayPalette = Palette(
    violet = Color(0xFF5B3DF5),
    violetDark = Color(0xFF4A2EDB),
    violetRing = Color(0xFF6E54FF),
    violetSoft = Color(0xFFECE8FF),
    violetOnDark = Color(0xFFD9D1FF),
    lavender = Color(0xFFF5F3FF),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFE6E1FA),
    divider = Color(0xFFF0EDFB),
    ink = Color(0xFF16132B),
    muted = Color(0xFF5E5A78),
    faint = Color(0xFF6E6A86),
    coral = Color(0xFFFF6B3D),
    green = Color(0xFF0B7A5F),
    greenSoft = Color(0xFFD8F5EA),
    amberSoft = Color(0xFFFFF1D6),
    amberInk = Color(0xFF7A4200),
    amber = Color(0xFF9A5200),
    coralSoft = Color(0xFFFFE6DC),
    coralInk = Color(0xFFC23F12),
    blueSoft = Color(0xFFDDEBFF),
    blueInk = Color(0xFF1F55C7),
    danger = Color(0xFFB3261E),
)

/**
 * Night colours. Backgrounds are deep violet-greys rather than pure black, so the brand still
 * reads. Accent and "ink" colours are lifted so text keeps enough contrast on dark cards.
 */
private val NightPalette = Palette(
    violet = Color(0xFF7C6AFF),
    violetDark = Color(0xFF5A47E0),
    violetRing = Color(0xFF8F80FF),
    violetSoft = Color(0xFF2E2757),
    violetOnDark = Color(0xFFE0DAFF),
    lavender = Color(0xFF0F0D1A),
    surface = Color(0xFF1B1830),
    border = Color(0xFF2E2A45),
    divider = Color(0xFF221F36),
    ink = Color(0xFFF1EEFF),
    muted = Color(0xFFABA6C6),
    faint = Color(0xFF8C87A8),
    coral = Color(0xFFFF7A52),
    green = Color(0xFF22A87F),
    greenSoft = Color(0xFF123A2F),
    amberSoft = Color(0xFF3A2A0E),
    amberInk = Color(0xFFFFC77A),
    amber = Color(0xFFF0A640),
    coralSoft = Color(0xFF3D1F16),
    coralInk = Color(0xFFFF9B78),
    blueSoft = Color(0xFF16284A),
    blueInk = Color(0xFF8DB4FF),
    danger = Color(0xFFE5534B),
)

/**
 * Design tokens. One source so screens never hard-code hex values. Most tokens are getters that
 * read the live palette: every composable and draw lambda that uses one is redrawn when night mode
 * flips, with no screen needing to know night mode exists.
 */
object BColors {
    private val p: Palette get() = if (ThemeState.isDark) NightPalette else DayPalette

    val Violet get() = p.violet
    /** KiraLah logo purple. Only for the brand mark and wordmark, so the logo matches the launcher icon. */
    val Brand = Color(0xFF5B2FD6)
    val VioletDark get() = p.violetDark
    val VioletRing get() = p.violetRing
    val VioletSoft get() = p.violetSoft
    val VioletOnDark get() = p.violetOnDark
    /** Page background. */
    val Lavender get() = p.lavender
    /** Cards, sheets, fields and the bottom bar. White by day, dark violet-grey by night. */
    val Surface get() = p.surface
    val Border get() = p.border
    val Divider get() = p.divider
    /** Main text. Near-black by day, near-white by night. */
    val Ink get() = p.ink
    val Muted get() = p.muted
    val Faint get() = p.faint
    val Yellow = Color(0xFFFFC940)
    val YellowInk = Color(0xFF3D2A00)
    val Coral get() = p.coral
    val Green get() = p.green
    val GreenSoft get() = p.greenSoft
    val AmberSoft get() = p.amberSoft
    val AmberInk get() = p.amberInk
    val Amber get() = p.amber
    val CoralSoft get() = p.coralSoft
    val CoralInk get() = p.coralInk
    val BlueSoft get() = p.blueSoft
    val BlueInk get() = p.blueInk
    val Danger get() = p.danger

    /** Text and icons on strong colour fills (violet hero, green tick, red delete). Always white. */
    val OnColor = Color.White

    /** Always-dark ink, for text on bright fixed fills (yellow slides) and always-dark cards (Recap). */
    val Night = Color(0xFF16132B)

    val isDark: Boolean get() = ThemeState.isDark

    /** Fallback tile colours for apps whose real icon cannot be loaded. Bright enough for both modes. */
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

private val BuylessShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/**
 * Material colours built from the live tokens, so Material parts (dialogs, text fields, sheets,
 * switches) follow night mode too, not only the screens that use BColors directly.
 */
private fun buylessColorScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = BColors.Violet, onPrimary = BColors.OnColor,
        primaryContainer = BColors.VioletSoft, onPrimaryContainer = BColors.VioletOnDark,
        secondary = BColors.Yellow, onSecondary = BColors.YellowInk,
        background = BColors.Lavender, onBackground = BColors.Ink,
        surface = BColors.Surface, onSurface = BColors.Ink,
        surfaceVariant = BColors.Surface, onSurfaceVariant = BColors.Muted,
        surfaceContainer = BColors.Surface, surfaceContainerHigh = BColors.Surface, surfaceContainerHighest = BColors.Border,
        surfaceContainerLow = BColors.Surface,
        outline = BColors.Border, outlineVariant = BColors.Divider,
        error = BColors.Danger,
    )
} else {
    lightColorScheme(
        primary = BColors.Violet, onPrimary = BColors.OnColor,
        primaryContainer = BColors.VioletSoft, onPrimaryContainer = BColors.VioletDark,
        secondary = BColors.Yellow, onSecondary = BColors.YellowInk,
        background = BColors.Lavender, onBackground = BColors.Ink,
        surface = BColors.Surface, onSurface = BColors.Ink,
        surfaceVariant = BColors.Lavender, onSurfaceVariant = BColors.Muted,
        surfaceContainer = BColors.Surface,
        outline = BColors.Border, outlineVariant = BColors.Divider,
        error = BColors.Danger,
    )
}

/** Follows ThemeState, so flipping night mode in Settings recolours the whole app at once. */
@Composable
fun BuylessTheme(content: @Composable () -> Unit) {
    val dark = ThemeState.isDark
    MaterialTheme(
        colorScheme = buylessColorScheme(dark),
        typography = BuylessTypography,
        shapes = BuylessShapes,
        content = content,
    )
}
