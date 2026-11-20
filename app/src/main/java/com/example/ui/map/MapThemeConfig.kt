package com.example.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.DarkCtecsColorTokens
import com.example.ui.theme.LightCtecsColorTokens
import com.example.ui.theme.isAppDarkTheme

/**
 * Supported map display types.
 */
enum class MapType(
    val id: String,
    val label: String,
    val attribution: String
) {
    STANDARD("standard", "Standard", "© OpenStreetMap contributors"),
    SATELLITE("satellite", "Satellite", "© Esri, Maxar, Earthstar Geographics");

    companion object {
        fun fromId(id: String?): MapType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STANDARD
        }
    }
}

/**
 * User preference options for map theme appearance.
 */
enum class MapThemeMode(
    val id: String,
    val label: String
) {
    SYSTEM("system", "System Default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(id: String?): MapThemeMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Centralized theme colors and styling for all map components throughout the CTECS app.
 * Synchronized with AMOLED Deep Black Dark Mode and Professional Light Mode.
 */
data class MapThemeColors(
    val isDark: Boolean,

    // Background & Canvas
    val mapBackground: Color,
    val gridLineColor: Color,
    val containerBorder: Color,
    val containerBackground: Color,

    // Route alignments
    val routeCasingColor: Color,
    val routeCenterColor: Color,
    val routeArrowColor: Color,

    // Markers & Chainage
    val markerDotColor: Color,
    val markerBorderColor: Color,
    val markerTextColor: Color,
    val markerBadgeBg: Color,

    // Live Location & Heading
    val userPinColor: Color,
    val userPinInnerColor: Color,
    val userPulseColor: Color,
    val userBearingColor: Color,

    // Controls & Overlays
    val mapControlBg: Color,
    val mapControlIconColor: Color,
    val mapControlBorder: Color,
    val compassBorderColor: Color,

    // Badges & Attribution
    val badgeBg: Color,
    val badgeTextColor: Color,
    val badgeBorderColor: Color,
    val attributionTextColor: Color,
    val attributionBg: Color
) {
    val mapBackgroundArgb: Int get() = mapBackground.toArgb()
    val routeCasingArgb: Int get() = routeCasingColor.toArgb()
    val routeCenterArgb: Int get() = routeCenterColor.toArgb()
    val markerDotArgb: Int get() = markerDotColor.toArgb()
    val markerBorderArgb: Int get() = markerBorderColor.toArgb()
    val userPinArgb: Int get() = userPinColor.toArgb()
    val userPinInnerArgb: Int get() = userPinInnerColor.toArgb()
    val userPulseArgb: Int get() = userPulseColor.toArgb()
    val userBearingArgb: Int get() = userBearingColor.toArgb()
    val badgeBgArgb: Int get() = badgeBg.toArgb()
    val badgeTextArgb: Int get() = badgeTextColor.toArgb()
    val attributionTextArgb: Int get() = attributionTextColor.toArgb()
    val attributionBgArgb: Int get() = attributionBg.toArgb()
}

object MapThemeConfig {
    val DarkColors = MapThemeColors(
        isDark = true,
        mapBackground = DarkCtecsColorTokens.secondaryBackground, // #080B12
        gridLineColor = Color.White.copy(alpha = 0.06f),
        containerBorder = Color.Transparent,
        containerBackground = DarkCtecsColorTokens.surface.copy(alpha = 0.94f),
        routeCasingColor = DarkCtecsColorTokens.accent,           // #1685FF
        routeCenterColor = DarkCtecsColorTokens.accentSecondary,  // #20B8F0
        routeArrowColor = DarkCtecsColorTokens.accentSecondary,
        markerDotColor = DarkCtecsColorTokens.warning,            // #FFB52E
        markerBorderColor = DarkCtecsColorTokens.background,
        markerTextColor = DarkCtecsColorTokens.textPrimary,
        markerBadgeBg = DarkCtecsColorTokens.surface.copy(alpha = 0.90f),
        userPinColor = DarkCtecsColorTokens.accent,               // Electric Blue live pin
        userPinInnerColor = Color.White,
        userPulseColor = DarkCtecsColorTokens.accent.copy(alpha = 0.35f),
        userBearingColor = DarkCtecsColorTokens.accentSecondary,
        mapControlBg = DarkCtecsColorTokens.surface.copy(alpha = 0.92f),
        mapControlIconColor = DarkCtecsColorTokens.textPrimary,
        mapControlBorder = Color.Transparent,
        compassBorderColor = DarkCtecsColorTokens.elevatedSurface,
        badgeBg = DarkCtecsColorTokens.surface.copy(alpha = 0.90f),
        badgeTextColor = DarkCtecsColorTokens.textPrimary,
        badgeBorderColor = Color.Transparent,
        attributionTextColor = DarkCtecsColorTokens.textMuted,
        attributionBg = DarkCtecsColorTokens.background.copy(alpha = 0.72f)
    )

    val LightColors = MapThemeColors(
        isDark = false,
        mapBackground = LightCtecsColorTokens.secondaryBackground, // #F7F9FC
        gridLineColor = LightCtecsColorTokens.textMuted.copy(alpha = 0.14f),
        containerBorder = Color.Transparent,
        containerBackground = LightCtecsColorTokens.background.copy(alpha = 0.94f),
        routeCasingColor = LightCtecsColorTokens.accent,           // #1677F2
        routeCenterColor = LightCtecsColorTokens.accentSecondary,  // #159FD5
        routeArrowColor = LightCtecsColorTokens.accent,
        markerDotColor = LightCtecsColorTokens.warning,            // #D99A20
        markerBorderColor = Color.White,
        markerTextColor = LightCtecsColorTokens.textPrimary,
        markerBadgeBg = LightCtecsColorTokens.background.copy(alpha = 0.92f),
        userPinColor = LightCtecsColorTokens.accent,
        userPinInnerColor = Color.White,
        userPulseColor = LightCtecsColorTokens.accent.copy(alpha = 0.28f),
        userBearingColor = LightCtecsColorTokens.accentSecondary,
        mapControlBg = LightCtecsColorTokens.background.copy(alpha = 0.94f),
        mapControlIconColor = LightCtecsColorTokens.textPrimary,
        mapControlBorder = Color.Transparent,
        compassBorderColor = LightCtecsColorTokens.secondarySurface,
        badgeBg = LightCtecsColorTokens.background.copy(alpha = 0.92f),
        badgeTextColor = LightCtecsColorTokens.textPrimary,
        badgeBorderColor = Color.Transparent,
        attributionTextColor = LightCtecsColorTokens.textSecondary,
        attributionBg = LightCtecsColorTokens.background.copy(alpha = 0.80f)
    )

    fun getColors(isDark: Boolean): MapThemeColors = if (isDark) DarkColors else LightColors

    fun resolveIsDark(mapThemePreference: String, appIsDark: Boolean): Boolean {
        return when (mapThemePreference.lowercase()) {
            "light" -> false
            "dark" -> true
            else -> appIsDark
        }
    }
}

@Composable
@ReadOnlyComposable
fun rememberMapThemeColors(isDark: Boolean = isAppDarkTheme()): MapThemeColors {
    return MapThemeConfig.getColors(isDark)
}

@Composable
@ReadOnlyComposable
fun rememberResolvedMapColors(mapThemePreference: String = "system"): MapThemeColors {
    val isAppDark = isAppDarkTheme()
    val isDark = MapThemeConfig.resolveIsDark(mapThemePreference, isAppDark)
    return MapThemeConfig.getColors(isDark)
}
