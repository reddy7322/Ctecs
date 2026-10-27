package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
