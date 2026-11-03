package com.example.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * Provides synchronized styling across Home, Camera, Routes, Gallery and any screen containing a map.
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
    // Android Graphics integer color helpers for Canvas / Paint
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
        mapBackground = Color(0xFF0F172A),
        gridLineColor = Color.White.copy(alpha = 0.08f),
        containerBorder = Color(0xFF38BDF8).copy(alpha = 0.4f),
        containerBackground = Color(0xE60F172A),
        routeCasingColor = Color(0xFF0284C7),
        routeCenterColor = Color(0xFF38BDF8),
        routeArrowColor = Color(0xFF7DD3FC),
        markerDotColor = Color(0xFFFBBF24),
        markerBorderColor = Color(0xFF0F172A),
        markerTextColor = Color.White,
        markerBadgeBg = Color(0xD00F172A),
        userPinColor = Color(0xFFEF4444),
        userPinInnerColor = Color.White,
        userPulseColor = Color(0xFFFF0000).copy(alpha = 0.45f),
        userBearingColor = Color(0xFF38BDF8),
        mapControlBg = Color(0xD0121212),
        mapControlIconColor = Color.White,
        mapControlBorder = Color.White.copy(alpha = 0.2f),
        compassBorderColor = Color(0xFFFBBF24).copy(alpha = 0.8f),
        badgeBg = Color(0xD9000000),
        badgeTextColor = Color.White,
        badgeBorderColor = Color.White.copy(alpha = 0.2f),
        attributionTextColor = Color(0xFF94A3B8),
        attributionBg = Color(0xD00F172A)
    )

    val LightColors = MapThemeColors(
        isDark = false,
        mapBackground = Color(0xFFF1F5F9),
        gridLineColor = Color(0xFF64748B).copy(alpha = 0.18f),
        containerBorder = Color(0xFF94A3B8).copy(alpha = 0.6f),
        containerBackground = Color(0xF0FFFFFF),
        routeCasingColor = Color(0xFF0369A1), // Deep contrasting navy blue casing
        routeCenterColor = Color(0xFF0284C7), // High-visibility royal blue centerline
        routeArrowColor = Color(0xFF0284C7),
        markerDotColor = Color(0xFFD97706), // Deep warm amber for maximum contrast on light roads
        markerBorderColor = Color.White,
        markerTextColor = Color(0xFF0F172A),
        markerBadgeBg = Color(0xF0FFFFFF),
        userPinColor = Color(0xFFDC2626), // Vivid red location pin
        userPinInnerColor = Color.White,
        userPulseColor = Color(0xFFDC2626).copy(alpha = 0.35f),
        userBearingColor = Color(0xFF0284C7),
        mapControlBg = Color(0xF0FFFFFF),
        mapControlIconColor = Color(0xFF0F172A),
        mapControlBorder = Color(0xFFCBD5E1),
        compassBorderColor = Color(0xFFD97706),
        badgeBg = Color(0xEEFFFFFF),
        badgeTextColor = Color(0xFF0F172A),
        badgeBorderColor = Color(0xFFCBD5E1),
        attributionTextColor = Color(0xFF475569),
        attributionBg = Color(0xEEFFFFFF)
    )

    fun getColors(isDark: Boolean): MapThemeColors = if (isDark) DarkColors else LightColors

    /**
     * Resolves whether the map should display dark styling based on
     * the user's manual map theme preference ("system", "light", "dark")
     * and the current app/system theme.
     */
    fun resolveIsDark(mapThemePreference: String, appIsDark: Boolean): Boolean {
        return when (mapThemePreference.lowercase()) {
            "light" -> false
            "dark" -> true
            else -> appIsDark // "system" -> follow app theme / device appearance
        }
    }
}

/**
 * Composable helper returning the current theme's MapThemeColors.
 * Automatically recomposes when app theme or system appearance changes.
 */
@Composable
@ReadOnlyComposable
fun rememberMapThemeColors(isDark: Boolean = isAppDarkTheme()): MapThemeColors {
    return MapThemeConfig.getColors(isDark)
}

/**
 * Resolves map theme colors considering both user preference and current app theme.
 */
@Composable
@ReadOnlyComposable
fun rememberResolvedMapColors(mapThemePreference: String = "system"): MapThemeColors {
    val isAppDark = isAppDarkTheme()
    val isDark = MapThemeConfig.resolveIsDark(mapThemePreference, isAppDark)
    return MapThemeConfig.getColors(isDark)
}
