package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Central semantic color tokens for the CTECS Borderless Flat Design System.
 * Supports Light, Dark (Deep Charcoal), and AMOLED (True Black #000000) modes across 100% of screens.
 */
@Immutable
data class CtecsColorTokens(
    val isDark: Boolean,
    val isAmoled: Boolean = false,
    val background: Color,
    val secondaryBackground: Color,
    val surface: Color,
    val secondarySurface: Color,
    val elevatedSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val scrim: Color,
    val subtleSeparator: Color
)

/** Deep Charcoal Dark Mode — preserved for legacy callers */
val CharcoalDarkCtecsColorTokens = CtecsColorTokens(
    isDark = true,
    isAmoled = false,
    background = CharcoalDarkMainBackground,
    secondaryBackground = CharcoalDarkSecondaryBackground,
    surface = CharcoalDarkPrimarySurface,
    secondarySurface = CharcoalDarkSecondarySurface,
    elevatedSurface = CharcoalDarkElevatedSurface,
    textPrimary = CharcoalDarkPrimaryText,
    textSecondary = CharcoalDarkSecondaryText,
    textMuted = CharcoalDarkMutedText,
    accent = DarkPrimaryAccent,
    accentSecondary = DarkSecondaryAccent,
    onAccent = Color.White,
    success = DarkSuccess,
    warning = DarkWarning,
    error = DarkError,
    scrim = Color(0xBA0B0E14),
    subtleSeparator = CharcoalDarkSeparator
)

/** True Black AMOLED Dark Mode (#000000) — soft charcoal surfaces, crisp white text, subtle separators */
val AmoledCtecsColorTokens = CtecsColorTokens(
    isDark = true,
    isAmoled = true,
    background = AmoledMainBackground,
    secondaryBackground = AmoledSecondaryBackground,
    surface = AmoledPrimarySurface,
    secondarySurface = AmoledSecondarySurface,
    elevatedSurface = AmoledElevatedSurface,
    textPrimary = AmoledPrimaryText,
    textSecondary = AmoledSecondaryText,
    textMuted = AmoledMutedText,
    accent = DarkPrimaryAccent,
    accentSecondary = DarkSecondaryAccent,
    onAccent = Color.White,
    success = DarkSuccess,
    warning = DarkWarning,
    error = DarkError,
    scrim = Color(0xD6000000),
    subtleSeparator = AmoledSeparator
)

// Alias preserved for any existing callers (now aligned with True Black AMOLED Dark Mode)
val DarkCtecsColorTokens = AmoledCtecsColorTokens

/** Clean White Light Mode — soft light grey surfaces & crisp dark typography */
val LightCtecsColorTokens = CtecsColorTokens(
    isDark = false,
    isAmoled = false,
    background = LightMainBackground,
    secondaryBackground = LightSecondaryBackground,
    surface = LightPrimarySurface,
    secondarySurface = LightSecondarySurface,
    elevatedSurface = LightElevatedSurface,
    textPrimary = LightPrimaryText,
    textSecondary = LightSecondaryText,
    textMuted = LightMutedText,
    accent = LightPrimaryAccent,
    accentSecondary = LightSecondaryAccent,
    onAccent = Color.White,
    success = LightSuccess,
    warning = LightWarning,
    error = LightError,
    scrim = Color(0x70101828),
    subtleSeparator = LightSeparator
)

private val CharcoalDarkColorScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = CharcoalDarkSecondarySurface,
    onPrimaryContainer = CharcoalDarkPrimaryText,
    secondary = DarkSecondaryAccent,
    onSecondary = CharcoalDarkMainBackground,
    secondaryContainer = CharcoalDarkElevatedSurface,
    onSecondaryContainer = CharcoalDarkPrimaryText,
    tertiary = DarkWarning,
    onTertiary = CharcoalDarkMainBackground,
    tertiaryContainer = CharcoalDarkElevatedSurface,
    onTertiaryContainer = CharcoalDarkPrimaryText,
    background = CharcoalDarkMainBackground,
    onBackground = CharcoalDarkPrimaryText,
    surface = CharcoalDarkPrimarySurface,
    onSurface = CharcoalDarkPrimaryText,
    surfaceVariant = CharcoalDarkSecondarySurface,
    onSurfaceVariant = CharcoalDarkSecondaryText,
    surfaceContainerLowest = CharcoalDarkMainBackground,
    surfaceContainerLow = CharcoalDarkSecondaryBackground,
    surfaceContainer = CharcoalDarkPrimarySurface,
    surfaceContainerHigh = CharcoalDarkSecondarySurface,
    surfaceContainerHighest = CharcoalDarkElevatedSurface,
    outline = CharcoalDarkElevatedSurface,
    outlineVariant = CharcoalDarkSeparator,
    error = DarkError,
    onError = Color.White,
    errorContainer = Color(0xFF33181F),
    onErrorContainer = DarkError,
    scrim = Color(0xBA0B0E14)
)

private val AmoledColorScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = AmoledSecondarySurface,
    onPrimaryContainer = AmoledPrimaryText,
    secondary = DarkSecondaryAccent,
    onSecondary = AmoledMainBackground,
    secondaryContainer = AmoledElevatedSurface,
    onSecondaryContainer = AmoledPrimaryText,
    tertiary = DarkWarning,
    onTertiary = AmoledMainBackground,
    tertiaryContainer = AmoledElevatedSurface,
    onTertiaryContainer = AmoledPrimaryText,
    background = AmoledMainBackground,
    onBackground = AmoledPrimaryText,
    surface = AmoledPrimarySurface,
    onSurface = AmoledPrimaryText,
    surfaceVariant = AmoledSecondarySurface,
    onSurfaceVariant = AmoledSecondaryText,
    surfaceContainerLowest = AmoledMainBackground,
    surfaceContainerLow = AmoledSecondaryBackground,
    surfaceContainer = AmoledPrimarySurface,
    surfaceContainerHigh = AmoledSecondarySurface,
    surfaceContainerHighest = AmoledElevatedSurface,
    outline = AmoledElevatedSurface,
    outlineVariant = AmoledSeparator,
    error = DarkError,
    onError = Color.White,
    errorContainer = Color(0xFF2B1117),
    onErrorContainer = DarkError,
    scrim = Color(0xD6000000)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = LightSecondarySurface,
    onPrimaryContainer = LightPrimaryText,
    secondary = LightSecondaryAccent,
    onSecondary = Color.White,
    secondaryContainer = LightSecondarySurface,
    onSecondaryContainer = LightPrimaryText,
    tertiary = LightWarning,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF6E0),
    onTertiaryContainer = LightPrimaryText,
    background = LightMainBackground,
    onBackground = LightPrimaryText,
    surface = LightPrimarySurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSecondarySurface,
    onSurfaceVariant = LightSecondaryText,
    surfaceContainerLowest = LightMainBackground,
    surfaceContainerLow = LightSecondaryBackground,
    surfaceContainer = LightPrimarySurface,
    surfaceContainerHigh = LightSecondarySurface,
    surfaceContainerHighest = LightElevatedSurface,
    outline = LightSecondarySurface,
    outlineVariant = LightPrimarySurface,
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = LightError,
    scrim = Color(0x70101828)
)

val CtecsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val LocalIsDarkTheme = compositionLocalOf { false }
val LocalIsAmoledTheme = compositionLocalOf { false }
val LocalCtecsColors = staticCompositionLocalOf { LightCtecsColorTokens }

object CtecsTheme {
    val colors: CtecsColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalCtecsColors.current
}

@Composable
@ReadOnlyComposable
fun isAppDarkTheme(): Boolean = LocalIsDarkTheme.current

@Composable
@ReadOnlyComposable
fun isAppAmoledTheme(): Boolean = LocalIsAmoledTheme.current

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Supports instant theme switching across:
 * - "dark" / "amoled": True black (#000000) AMOLED-friendly Dark Mode with soft charcoal secondary surfaces
 * - "light": Clean white Light Mode with soft light grey secondary surfaces
 * - "system": Follows OS dark/light preference (uses True Black Dark Mode when OS is dark)
 */
@Composable
fun MyApplicationTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val normalized = themeMode.trim().lowercase()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (normalized) {
        "amoled", "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val isAmoled = darkTheme

    val colorScheme = if (darkTheme) AmoledColorScheme else LightColorScheme
    val ctecsColors = if (darkTheme) AmoledCtecsColorTokens else LightCtecsColorTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                try {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                } catch (_: Throwable) {}
            }
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalIsAmoledTheme provides isAmoled,
        LocalCtecsColors provides ctecsColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = CtecsShapes,
            content = content
        )
    }
}
