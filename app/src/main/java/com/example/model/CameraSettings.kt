package com.example.model

enum class CameraGridMode(val label: String) {
    OFF("Off"),
    GRID_3X3("3×3"),
    GRID_4X4("4×4");

    companion object {
        fun fromString(str: String?): CameraGridMode {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: OFF
        }
    }
}

enum class PreviewRatio(val label: String) {
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9"),
    FIT_SCREEN("Fit Screen");

    companion object {
        fun fromString(str: String?): PreviewRatio {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: RATIO_4_3
        }
    }
}

enum class CaptureTimer(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    SEC_3(3, "3s"),
    SEC_5(5, "5s"),
    SEC_10(10, "10s");

    companion object {
        fun fromSeconds(sec: Int): CaptureTimer {
            return entries.firstOrNull { it.seconds == sec } ?: OFF
        }
    }
}

enum class CoordinateFormat(val label: String) {
    DECIMAL("Decimal (DD.DDDD°)"),
    DMS("DMS (DD°MM'SS\")"),
    UTM("UTM Grid");

    companion object {
        fun fromString(str: String?): CoordinateFormat {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: DECIMAL
        }
    }
}

enum class VideoQualityOption(val label: String) {
    FHD_1080P("1080p Full HD"),
    UHD_4K("4K Ultra HD"),
    HD_720P("720p HD");

    companion object {
        fun fromString(str: String?): VideoQualityOption {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: FHD_1080P
        }
    }
}

enum class CaptureMode(val label: String) {
    PHOTO("Photo"),
    VIDEO("Video")
}

enum class CameraMode(val label: String) {
    HIGHWAY("Highway"),
    NORMAL("Normal"),
    TIMESTAMP("Timestamp"),
    LOCATION("Location"),
    CUSTOM("Custom");

    companion object {
        fun fromString(str: String?): CameraMode {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: HIGHWAY
        }
    }
}

enum class SpeedUnit(val label: String) {
    KMH("km/h"),
    MPH("mph");

    companion object {
        fun fromString(str: String?): SpeedUnit {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: KMH
        }
    }
}

/**
 * Simple 4-format output modes for both Photos and Videos.
 * - [LANDSCAPE_FULL]: Wide horizontal output preserving natural full sensor aspect ratio.
 * - [PORTRAIT_FULL]: Full-height vertical output with maximum usable vertical framing.
 * - [SQUARE]: Smart 1:1 square output with intelligent center framing & cropping.
 * - [RECTANGULAR_CROP]: Smart rectangular (4:3 / 3:4) output adapting to device orientation.
 */
enum class CameraOutputFormat(
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val legacyAspectRatioId: String
) {
    LANDSCAPE_FULL(
        title = "Landscape",
        shortLabel = "Land",
        subtitle = "Full width horizontal framing",
        legacyAspectRatioId = "16:9"
    ),
    PORTRAIT_FULL(
        title = "Portrait",
        shortLabel = "Port",
        subtitle = "Full height vertical framing",
        legacyAspectRatioId = "Full"
    ),
    SQUARE(
        title = "Square",
        shortLabel = "1:1",
        subtitle = "Smart 1:1 square framing",
        legacyAspectRatioId = "1:1"
    ),
    RECTANGULAR_CROP(
        title = "Rectangular",
        shortLabel = "Rect",
        subtitle = "Smart 4:3 / 3:4 orientation crop",
        legacyAspectRatioId = "4:3"
    );

    companion object {
        fun fromString(raw: String?, fallback: CameraOutputFormat = PORTRAIT_FULL): CameraOutputFormat {
            if (raw.isNullOrBlank()) return fallback
            val normalized = raw.trim()
            entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.title.equals(normalized, ignoreCase = true) ||
                    it.shortLabel.equals(normalized, ignoreCase = true)
            }?.let { return it }

            return when (normalized.lowercase()) {
                "1:1", "square", "square 1:1" -> SQUARE
                "4:3", "standard", "rectangular", "rectangular crop" -> RECTANGULAR_CROP
                "16:9", "widescreen", "landscape", "landscape - full width" -> LANDSCAPE_FULL
                "full", "full screen", "portrait", "portrait - full vertical" -> PORTRAIT_FULL
                else -> fallback
            }
        }
    }
}

/**
 * Simple 4-tier output quality & file-size presets shared independently by Photos and Videos.
 */
enum class OutputQualityPreset(
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val photoJpegQuality: Int,
    val photoMaxLongEdgePx: Int, // 0 = full native sensor resolution
    val videoBitrateMultiplier: Float,
    val photoBytesPerPixel: Double
) {
    MAXIMUM(
        title = "Maximum Quality",
        shortLabel = "Max",
        subtitle = "Highest resolution & sharpest detail",
        photoJpegQuality = 97,
        photoMaxLongEdgePx = 0,
        videoBitrateMultiplier = 1.0f,
        photoBytesPerPixel = 0.42
    ),
    HIGH(
        title = "High Quality",
        shortLabel = "High",
        subtitle = "Excellent clarity with optimized size",
        photoJpegQuality = 92,
        photoMaxLongEdgePx = 4096,
        videoBitrateMultiplier = 0.78f,
        photoBytesPerPixel = 0.28
    ),
    BALANCED(
        title = "Balanced",
        shortLabel = "Balanced",
        subtitle = "Balanced detail and storage usage",
        photoJpegQuality = 86,
        photoMaxLongEdgePx = 2560,
        videoBitrateMultiplier = 0.56f,
        photoBytesPerPixel = 0.18
    ),
    SMALLER_SIZE(
        title = "Smaller Size",
        shortLabel = "Small",
        subtitle = "Compact file size with clear image",
        photoJpegQuality = 78,
        photoMaxLongEdgePx = 1920,
        videoBitrateMultiplier = 0.36f,
        photoBytesPerPixel = 0.11
    );

    companion object {
        fun fromString(raw: String?, fallback: OutputQualityPreset = HIGH): OutputQualityPreset {
            if (raw.isNullOrBlank()) return fallback
            val trimmed = raw.trim()
            return entries.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) ||
                    it.title.equals(trimmed, ignoreCase = true) ||
                    it.shortLabel.equals(trimmed, ignoreCase = true)
            } ?: fallback
        }
    }
}

/**
 * Simple hardware-backed Video Stabilization controls (OIS + EIS).
 */
enum class VideoStabilizationMode(val label: String, val subtitle: String) {
    AUTO("Auto", "Best supported OIS / EIS automatically"),
    ON("ON", "Active optical & electronic stabilization"),
    OFF("OFF", "Stabilization disabled");

    companion object {
        fun fromString(raw: String?): VideoStabilizationMode {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: AUTO
        }
    }
}

/**
 * Simple hardware-backed Autofocus controls.
 */
enum class CameraAutofocusMode(val label: String, val subtitle: String) {
    CONTINUOUS("Continuous", "Smooth tracking with tap-to-focus"),
    TAP_LOCK("Tap & Lock", "Locks focus on tapped subject");

    companion object {
        fun fromString(raw: String?): CameraAutofocusMode {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: CONTINUOUS
        }
    }
}

data class CameraSettings(
    val captureTimer: CaptureTimer = CaptureTimer.OFF,
    val gridMode: CameraGridMode = CameraGridMode.OFF,
    val showHorizonLevel: Boolean = true,
    val previewRatio: PreviewRatio = PreviewRatio.RATIO_4_3,
    val captureSound: Boolean = true,
    val volumeButtonShutter: Boolean = true,
    val frontCameraMirror: Boolean = false,

    // Image quality & storage
    val jpegQuality: Int = 92,
    val saveOriginalAndStamped: Boolean = false,
    val preserveExif: Boolean = true,

    // Video recording
    val videoQuality: VideoQualityOption = VideoQualityOption.FHD_1080P,
    val videoFps: Int = 30,
    val recordAudio: Boolean = true,

    // GPS & Chainage
    val gpsUpdateIntervalSec: Int = 1,
    val gpsAccuracyThresholdMeters: Float = 15f,
    val routeMatchingRadiusMeters: Double = 100.0,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val is24HourFormat: Boolean = false,

    // Lens & Exposure
    val defaultLensZoom: Float = 1.0f,
    val exposureCompensationStep: Int = 0,

    // Camera Mode & Speed Units
    val cameraMode: CameraMode = CameraMode.HIGHWAY,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,

    // Additional controls (from user reference settings)
    val touchToCapture: Boolean = false,
    val alwaysDisplayZoomSlider: Boolean = true,
    val batterySaveMode: Boolean = false,
    val batterySaveBrightness: Float = 0.5f,
    val easyToggleCameras: Boolean = true,
    val whiteBalance: String = "auto",
    val videoSegmentation: String = "None",
    val customFilenamePrefix: String = "",
    val filenameFormatPreset: String = "TimePhoto_YYYYMMDD_HHMMSS",
    val customStoragePath: String = "/storage/emulated/0/DCIM/Camera",
    val autoRotateUI: Boolean = true,
    val photoResolution: String = "1920 x 1080",
    val videoResolution: String = "1920 x 1080",
    val photoAspectRatio: String = "4:3",
    val videoAspectRatio: String = "16:9",

    // Simple Output Formats, Quality Presets, Stabilization & Autofocus
    val photoOutputFormat: CameraOutputFormat = CameraOutputFormat.PORTRAIT_FULL,
    val videoOutputFormat: CameraOutputFormat = CameraOutputFormat.LANDSCAPE_FULL,
    val photoQualityPreset: OutputQualityPreset = OutputQualityPreset.HIGH,
    val videoQualityPreset: OutputQualityPreset = OutputQualityPreset.HIGH,
    val videoStabilizationMode: VideoStabilizationMode = VideoStabilizationMode.AUTO,
    val autofocusMode: CameraAutofocusMode = CameraAutofocusMode.CONTINUOUS,

    // Photo & Video Output Size Optimization and Noise Reduction / Enhancement
    val photoOutputSizeOptimization: Boolean = true,
    val photoEnhancementEnabled: Boolean = true,
    val videoOutputSizeOptimization: Boolean = false,
    val videoNoiseReductionEnabled: Boolean = true,

    // Smooth Continuous Photo & Video Capture Exposure (0.0f = min dim, 0.5f = balanced 0.0 EV, 1.0f = max bright)
    val autoBrightnessEnabled: Boolean = true,
    val manualPreviewBrightness: Float = 0.5f
)
