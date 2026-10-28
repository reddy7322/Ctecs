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
    val speedUnit: SpeedUnit = SpeedUnit.KMH
)
