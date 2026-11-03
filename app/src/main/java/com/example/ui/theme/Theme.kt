package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryText,
    onPrimary = PrimaryBackground,
    primaryContainer = ElevatedSurface,
    onPrimaryContainer = PrimaryText,
    secondary = HighlightYellow,
    onSecondary = PrimaryBackground,
    secondaryContainer = DarkYellowSurface,
    onSecondaryContainer = HighlightYellow,
    tertiary = AccentRed,
    onTertiary = PrimaryText,
    tertiaryContainer = DeepRed,
    onTertiaryContainer = PrimaryText,
    background = PrimaryBackground,
    onBackground = PrimaryText,
    surface = SurfaceDark,
    onSurface = PrimaryText,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = SecondaryText,
    outline = BorderColor,
    outlineVariant = BorderColor,
    error = AccentRed,
    onError = PrimaryText
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F172A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1F5F9),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = Color(0xFFEF4444),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF)
)

val LocalIsDarkTheme = compositionLocalOf { false }

/**
 * Returns true if the app is currently displaying dark theme, respecting
 * the user's explicit preference ("dark", "light") or following device system appearance ("system").
 */
@Composable
@ReadOnlyComposable
fun isAppDarkTheme(): Boolean = LocalIsDarkTheme.current

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

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
