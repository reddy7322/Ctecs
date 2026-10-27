package com.example.model

enum class OverlayTemplate(val title: String) {
    HIGHWAY_CONSTRUCTION("Highway Construction (Reference)"),
    GPS_COORDINATES_ONLY("GPS Coordinates Only"),
    SITE_INSPECTION("Site Inspection"),
    QUALITY_CONTROL("Quality Control"),
    MINIMAL_TIMESTAMP("Minimal Timestamp")
}

enum class LogoPosition(val displayName: String) {
    TOP_RIGHT("Top Right (Reference)"),
    TOP_LEFT("Top Left"),
    ABOVE_TEXT("Above Chainage Text"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right");

    companion object {
        fun fromString(name: String?): LogoPosition {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TOP_RIGHT
        }
    }
}

enum class LogoShape(val displayName: String) {
    ORIGINAL("Original Aspect"),
    ROUNDED_CORNER("Rounded Corners"),
    CIRCLE("Circular Badge");

    companion object {
        fun fromString(name: String?): LogoShape {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ORIGINAL
        }
    }
}

data class OverlayConfig(
    val template: OverlayTemplate = OverlayTemplate.HIGHWAY_CONSTRUCTION,
    val timestampFormat: String = "dd MMMM yyyy h:mm a",
    val coordinatePrecision: Int = 4,           // 4 to 7 decimals
    val showTimestamp: Boolean = true,
    val showCoordinates: Boolean = true,
    val showChainage: Boolean = true,
    val showMapInset: Boolean = true,
    val showProjectInfo: Boolean = true,

    // Custom Logo & Manual Settings
    val showLogo: Boolean = true,
    val logoPreset: String = "cubetech",        // "cubetech", "ctecs", "nhai", "survey_seal", "custom"
    val logoUri: String? = null,
    val logoPosition: LogoPosition = LogoPosition.TOP_RIGHT,
    val logoSizeDp: Int = 58,                   // 32 to 140 dp
    val logoOpacity: Float = 0.90f,             // 0.10f to 1.0f
    val logoShape: LogoShape = LogoShape.ORIGINAL,
    val logoCornerRadiusDp: Int = 10,           // 0 to 32 dp
    val logoBorderWidthDp: Int = 0,             // 0 to 6 dp
    val logoBorderColorHex: String = "#FFFFFF",
    val logoMarginXDp: Int = 16,
    val logoMarginYDp: Int = 20,

    val fontSizeSp: Float = 24f,
    val hasTextShadow: Boolean = true,
    val textColorHex: String = "#FFFFFF",
    val saveOriginalImage: Boolean = false
)

/**
 * Immutable snapshot captured at the exact shutter click moment.
 */
data class CaptureSnapshot(
    val timestamp: Long,
    val location: GeoPoint?,
    val gpsAccuracy: Float,
    val bearing: Float?,
    val matchResult: ChainageMatchResult,
    val projectProfile: ProjectProfile,
    val overlayConfig: OverlayConfig,
    val activeRoute: RouteData?
) {
    val displayChainage: String
        get() = matchResult.displayChainage
}
