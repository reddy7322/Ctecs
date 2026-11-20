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
 * Covers 100% of screens, dialogs, bottom sheets, menus, inputs, and controls.
 */
@Immutable
data class CtecsColorTokens(
    val isDark: Boolean,
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

val DarkCtecsColorTokens = CtecsColorTokens(
    isDark = true,
    background = DarkMainBackground,
    secondaryBackground = DarkSecondaryBackground,
    surface = DarkPrimarySurface,
    secondarySurface = DarkSecondarySurface,
    elevatedSurface = DarkElevatedSurface,
    textPrimary = DarkPrimaryText,
    textSecondary = DarkSecondaryText,
    textMuted = DarkMutedText,
    accent = DarkPrimaryAccent,
    accentSecondary = DarkSecondaryAccent,
    onAccent = Color.White,
    success = DarkSuccess,
    warning = DarkWarning,
    error = DarkError,
    scrim = Color(0xCC000000),
    subtleSeparator = Color(0xFF171E2B)
)

val LightCtecsColorTokens = CtecsColorTokens(
    isDark = false,
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
    subtleSeparator = Color(0xFFEAF0F7)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = DarkSecondarySurface,
    onPrimaryContainer = DarkPrimaryText,
    secondary = DarkSecondaryAccent,
    onSecondary = DarkMainBackground,
    secondaryContainer = DarkElevatedSurface,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = DarkWarning,
    onTertiary = DarkMainBackground,
    tertiaryContainer = DarkElevatedSurface,
    onTertiaryContainer = DarkPrimaryText,
    background = DarkMainBackground,
    onBackground = DarkPrimaryText,
    surface = DarkPrimarySurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSecondarySurface,
    onSurfaceVariant = DarkSecondaryText,
    surfaceContainerLowest = DarkMainBackground,
    surfaceContainerLow = DarkSecondaryBackground,
    surfaceContainer = DarkPrimarySurface,
    surfaceContainerHigh = DarkSecondarySurface,
    surfaceContainerHighest = DarkElevatedSurface,
    outline = DarkElevatedSurface,
    outlineVariant = DarkSecondarySurface,
    error = DarkError,
    onError = Color.White,
    errorContainer = Color(0xFF2B1117),
    onErrorContainer = DarkError,
    scrim = Color(0xCC000000)
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
val LocalCtecsColors = staticCompositionLocalOf { LightCtecsColorTokens }

object CtecsTheme {
    val colors: CtecsColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalCtecsColors.current
}

/**
 * Returns true if the app is currently displaying Deep Black Dark Mode, respecting
 * the user's explicit preference ("dark", "light") or following device system appearance ("system").
 */
@Composable
@ReadOnlyComposable
fun isAppDarkTheme(): Boolean = LocalIsDarkTheme.current

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun MyApplicationTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val ctecsColors = if (darkTheme) DarkCtecsColorTokens else LightCtecsColorTokens

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
