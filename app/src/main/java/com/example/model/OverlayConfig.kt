package com.example.model

enum class OverlayTemplate(val title: String) {
    NONE("None / Clean"),
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
    BOTTOM_RIGHT("Bottom Right"),
    TOP_CENTER("Top Center"),
    BOTTOM_CENTER("Bottom Center"),
    CENTER("Center");

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
    val showCompass: Boolean = true,
    val showAltitude: Boolean = true,
    val showAccuracy: Boolean = true,
    val burnInTimestamp: Boolean = true,        // When false: pristine photo with metadata-only timestamp

    // Speed & Direction Visibility (OFF by default for preview and captured media)
    val showSpeedOnPreview: Boolean = false,
    val includeSpeedInCapture: Boolean = false,
    val showDirectionOnPreview: Boolean = false,
    val includeDirectionInCapture: Boolean = false,

    // Custom Logo & Manual Settings (Disabled by default, no prefill)
    val showLogo: Boolean = false,
    val logoPreset: String = "custom",           // "custom"
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
    val logoScale: Float = 1.0f,
    val logoRotationDegrees: Float = 0f,

    // Typography & Styling
    val fontSizeSp: Float = 24f,
    val fontSizePreset: String = "Medium",      // Tiny (14sp), Small (18sp), Medium (24sp), Big (28sp), Huge (34sp), Huger (42sp), Custom
    val hasTextShadow: Boolean = true,
    val textColorHex: String = "#FFFFFF",
    val textOpacity: Float = 1.0f,              // 0.10f to 1.0f
    val fontFamily: String = "Default",         // Default, Noto Sans, Source Sans Pro, Dancing Script, Monospace, Serif
    val fontStyle: String = "Bold",             // Regular, Medium, Bold, Italic
    val textStrokeWidthDp: Float = 1.5f,
    val customFontPath: String? = null,
    val customFontName: String? = null,
    val textAlignment: String = "Right",        // "Auto", "Right", "Center", "Left"
    val lineSpacingFactor: Float = 0.32f,

    // Background style & color
    val backgroundStyle: String = "Transparent", // Transparent, Solid, Translucent Glass
    val backgroundOpacity: Float = 0.0f,        // 0.0f to 1.0f
    val backgroundColorHex: String = "#000000",

    // Placement & Formatting
    val watermarkPosition: String = "Bottom Right", // Bottom Right, Bottom Center, Bottom Left, Top Right, Top Center, Top Left, Center
    val timePosition: String = "Time in above", // Time in above vs Time in below
    val timestampMarginMode: String = "One line",
    val showIndexNumber: Boolean = true,
    val resolutionOption: String = "1920 x 1080",
    val saveOriginalImage: Boolean = false,

    // Address & Location selector (from reference Screenshot 2)
    val locationAddressOption: String = "None", // None, India, Uttarakhand, Garhwal Division, Rehmadpur, National Highway 58, Sant Niwas, Full Address
    val customAddressText: String = "",
    val gpsAccuracyMarginPreset: String = "±5m",
    val customTag: String = "",

    // PDF Default Logo & Signature
    val pdfLogoUri: String? = null,
    val includePdfLogoDefault: Boolean = true,
    val pdfSignatureUri: String? = null,
    val includePdfSignatureDefault: Boolean = true,
    val pdfDefaultPreparedBy: String = "Site Engineer",

    // Dynamic Current Location Name
    val showLocationName: Boolean = true,
    val lastDetectedLocationName: String = "",

    // Manual Overlay Padding Adjustment (4-side controls in dp)
    val useSamePaddingForPhotoAndVideo: Boolean = true,
    val photoPaddingTopDp: Int = 16,
    val photoPaddingBottomDp: Int = 20,
    val photoPaddingLeftDp: Int = 16,
    val photoPaddingRightDp: Int = 16,
    val videoPaddingTopDp: Int = 16,
    val videoPaddingBottomDp: Int = 20,
    val videoPaddingLeftDp: Int = 16,
    val videoPaddingRightDp: Int = 16,

    // Independent Photo & Video Mini Map Overlay Settings
    val photoMiniMap: MiniMapStyleConfig = MiniMapStyleConfig(),
    val videoMiniMap: MiniMapStyleConfig = MiniMapStyleConfig()
) {
    fun getEffectivePadding(isVideo: Boolean): OverlayPadding {
        return if (isVideo && !useSamePaddingForPhotoAndVideo) {
            OverlayPadding(
                top = videoPaddingTopDp,
                bottom = videoPaddingBottomDp,
                left = videoPaddingLeftDp,
                right = videoPaddingRightDp
            )
        } else {
            OverlayPadding(
                top = photoPaddingTopDp,
                bottom = photoPaddingBottomDp,
                left = photoPaddingLeftDp,
                right = photoPaddingRightDp
            )
        }
    }

    fun getEffectiveMiniMapConfig(isVideo: Boolean): MiniMapStyleConfig {
        return if (isVideo) videoMiniMap else photoMiniMap
    }

    fun isMiniMapActive(isVideo: Boolean): Boolean {
        return showMapInset &&
            template != OverlayTemplate.MINIMAL_TIMESTAMP &&
            getEffectiveMiniMapConfig(isVideo).enabled
    }
}

data class OverlayPadding(
    val top: Int = 16,
    val bottom: Int = 20,
    val left: Int = 16,
    val right: Int = 16
)

/**
 * Immutable snapshot captured at the exact shutter click moment.
 */
data class CaptureSnapshot(
    val timestamp: Long,
    val location: GeoPoint? = null,
    val locationName: String = "",
    val gpsAccuracy: Float = 0f,
    val bearing: Float? = null,
    val matchResult: ChainageMatchResult = ChainageMatchResult(),
    val projectProfile: ProjectProfile = ProjectProfile(),
    val overlayConfig: OverlayConfig = OverlayConfig(),
    val activeRoute: RouteData? = null,
    val deviceRotationDegrees: Int = 0,
    val compassHeading: String = "",
    val compassAzimuth: Float = 0f,
    val speedMps: Float? = null,
    val filenameFormatPreset: String = "highway_YYYYMMDD_HHMMSS",
    val customFilenamePrefix: String = "",
    val isDarkTheme: Boolean = true,
    val lastValidLocation: GeoPoint? = null,
    val lastValidBearing: Float? = null,
    val isGpsRetained: Boolean = false,
    val isVideoCapture: Boolean = false
) {
    val displayChainage: String
        get() = matchResult.displayChainage

    val effectiveMiniMapLocation: GeoPoint?
        get() = location ?: lastValidLocation

    val effectiveMiniMapBearing: Float?
        get() = bearing ?: lastValidBearing
}
