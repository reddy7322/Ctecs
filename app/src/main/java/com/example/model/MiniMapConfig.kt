package com.example.model

import org.json.JSONObject

enum class MiniMapSizePreset(val label: String, val defaultDp: Int) {
    VERY_SMALL("Very Small", 58),
    SMALL("Small", 78),
    MEDIUM("Medium", 102),
    LARGE("Large", 132),
    CUSTOM("Custom", 102);

    companion object {
        fun fromString(name: String?): MiniMapSizePreset {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true)
            } ?: SMALL
        }
    }
}

enum class MiniMapPosition(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    CUSTOM("Custom");

    companion object {
        fun fromString(name: String?): MiniMapPosition {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true)
            } ?: BOTTOM_LEFT
        }
    }
}

enum class MiniMapCornerShape(val label: String) {
    ROUNDED("Rounded"),
    SQUARE("Square");

    companion object {
        fun fromString(name: String?): MiniMapCornerShape {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true)
            } ?: ROUNDED
        }
    }
}

enum class MiniMapStyle(val label: String) {
    STANDARD("Standard"),
    SATELLITE("Satellite"),
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromString(name: String?): MiniMapStyle {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true)
            } ?: STANDARD
        }
    }
}

enum class MiniMapDirectionStyle(val label: String) {
    ARROW("Arrow"),
    CHEVRON("Chevron"),
    CONE("Direction Beam");

    companion object {
        fun fromString(name: String?): MiniMapDirectionStyle {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true)
            } ?: ARROW
        }
    }
}

enum class MiniMapOrientationMode(val label: String) {
    NORTH_UP("North Up"),
    DIRECTION_UP("Direction Up"),
    AUTO("Automatic");

    companion object {
        fun fromString(name: String?): MiniMapOrientationMode {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true)
            } ?: NORTH_UP
        }
    }
}

data class MiniMapStyleConfig(
    // A. Independent Enable/Disable
    val enabled: Boolean = true,

    // B. Size
    val sizePreset: MiniMapSizePreset = MiniMapSizePreset.SMALL,
    val customWidthDp: Int = 98,
    val customHeightDp: Int = 98,

    // C. Opacity (0% to 100%)
    val opacity: Float = 0.92f,

    // D. Position & Custom Fine Placement (normalized 0f..1f inside safe area)
    val position: MiniMapPosition = MiniMapPosition.BOTTOM_LEFT,
    val customPosXFraction: Float = 0.04f,
    val customPosYFraction: Float = 0.80f,

    // E. Map Appearance
    val cornerShape: MiniMapCornerShape = MiniMapCornerShape.ROUNDED,
    val cornerRadiusDp: Int = 12,
    val borderEnabled: Boolean = true,
    val borderColorHex: String = "#94A3B8",
    val borderThicknessDp: Float = 1.2f,
    val backgroundAlpha: Float = 0.85f,
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val mapStyle: MiniMapStyle = MiniMapStyle.STANDARD,
    val routeColorHex: String = "#38BDF8",
    val gpsMarkerColorHex: String = "#EF4444",
    val directionMarkerStyle: MiniMapDirectionStyle = MiniMapDirectionStyle.ARROW,
    val shadowEnabled: Boolean = true,

    // F. Map Zoom & Orientation
    val autoZoom: Boolean = true,
    val zoomLevel: Float = 15.5f,
    val orientationMode: MiniMapOrientationMode = MiniMapOrientationMode.NORTH_UP,

    // G. Map Information Toggles
    val showCurrentPositionMarker: Boolean = true,
    val showRoadAlignment: Boolean = true,
    val showCurrentChainageLabel: Boolean = true,
    val showSideIndicator: Boolean = true,
    val showDirectionArrow: Boolean = true,
    val showNearbyChainageMarkers: Boolean = true
) {
    fun resolveWidthDp(): Int {
        return when (sizePreset) {
            MiniMapSizePreset.CUSTOM -> customWidthDp.coerceIn(44, 220)
            else -> sizePreset.defaultDp
        }
    }

    fun resolveHeightDp(): Int {
        return when (sizePreset) {
            MiniMapSizePreset.CUSTOM -> customHeightDp.coerceIn(44, 220)
            else -> sizePreset.defaultDp
        }
    }

    fun resolveCornerRadiusDp(): Int {
        return if (cornerShape == MiniMapCornerShape.SQUARE) 0 else cornerRadiusDp.coerceIn(0, 32)
    }

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("enabled", enabled)
        obj.put("sizePreset", sizePreset.name)
        obj.put("customWidthDp", customWidthDp)
        obj.put("customHeightDp", customHeightDp)
        obj.put("opacity", opacity.toDouble())
        obj.put("position", position.name)
        obj.put("customPosXFraction", customPosXFraction.toDouble())
        obj.put("customPosYFraction", customPosYFraction.toDouble())
        obj.put("cornerShape", cornerShape.name)
        obj.put("cornerRadiusDp", cornerRadiusDp)
        obj.put("borderEnabled", borderEnabled)
        obj.put("borderColorHex", borderColorHex)
        obj.put("borderThicknessDp", borderThicknessDp.toDouble())
        obj.put("backgroundAlpha", backgroundAlpha.toDouble())
        obj.put("brightness", brightness.toDouble())
        obj.put("contrast", contrast.toDouble())
        obj.put("mapStyle", mapStyle.name)
        obj.put("routeColorHex", routeColorHex)
        obj.put("gpsMarkerColorHex", gpsMarkerColorHex)
        obj.put("directionMarkerStyle", directionMarkerStyle.name)
        obj.put("shadowEnabled", shadowEnabled)
        obj.put("autoZoom", autoZoom)
        obj.put("zoomLevel", zoomLevel.toDouble())
        obj.put("orientationMode", orientationMode.name)
        obj.put("showCurrentPositionMarker", showCurrentPositionMarker)
        obj.put("showRoadAlignment", showRoadAlignment)
        obj.put("showCurrentChainageLabel", showCurrentChainageLabel)
        obj.put("showSideIndicator", showSideIndicator)
        obj.put("showDirectionArrow", showDirectionArrow)
        obj.put("showNearbyChainageMarkers", showNearbyChainageMarkers)
        return obj.toString()
    }

    companion object {
        fun fromJsonString(json: String?): MiniMapStyleConfig {
            if (json.isNullOrBlank()) return MiniMapStyleConfig()
            return try {
                val obj = JSONObject(json)
                MiniMapStyleConfig(
                    enabled = obj.optBoolean("enabled", true),
                    sizePreset = MiniMapSizePreset.fromString(obj.optString("sizePreset", "SMALL")),
                    customWidthDp = obj.optInt("customWidthDp", 98).coerceIn(44, 220),
                    customHeightDp = obj.optInt("customHeightDp", 98).coerceIn(44, 220),
                    opacity = obj.optDouble("opacity", 0.92).toFloat().coerceIn(0f, 1f),
                    position = MiniMapPosition.fromString(obj.optString("position", "BOTTOM_LEFT")),
                    customPosXFraction = obj.optDouble("customPosXFraction", 0.04).toFloat().coerceIn(0f, 1f),
                    customPosYFraction = obj.optDouble("customPosYFraction", 0.80).toFloat().coerceIn(0f, 1f),
                    cornerShape = MiniMapCornerShape.fromString(obj.optString("cornerShape", "ROUNDED")),
                    cornerRadiusDp = obj.optInt("cornerRadiusDp", 12).coerceIn(0, 32),
                    borderEnabled = obj.optBoolean("borderEnabled", true),
                    borderColorHex = obj.optString("borderColorHex", "#94A3B8").ifBlank { "#94A3B8" },
                    borderThicknessDp = obj.optDouble("borderThicknessDp", 1.2).toFloat().coerceIn(0.5f, 5f),
                    backgroundAlpha = obj.optDouble("backgroundAlpha", 0.85).toFloat().coerceIn(0f, 1f),
                    brightness = obj.optDouble("brightness", 1.0).toFloat().coerceIn(0.4f, 1.6f),
                    contrast = obj.optDouble("contrast", 1.0).toFloat().coerceIn(0.5f, 1.8f),
                    mapStyle = MiniMapStyle.fromString(obj.optString("mapStyle", "STANDARD")),
                    routeColorHex = obj.optString("routeColorHex", "#38BDF8").ifBlank { "#38BDF8" },
                    gpsMarkerColorHex = obj.optString("gpsMarkerColorHex", "#EF4444").ifBlank { "#EF4444" },
                    directionMarkerStyle = MiniMapDirectionStyle.fromString(obj.optString("directionMarkerStyle", "ARROW")),
                    shadowEnabled = obj.optBoolean("shadowEnabled", true),
                    autoZoom = obj.optBoolean("autoZoom", true),
                    zoomLevel = obj.optDouble("zoomLevel", 15.5).toFloat().coerceIn(11f, 18.5f),
                    orientationMode = MiniMapOrientationMode.fromString(obj.optString("orientationMode", "NORTH_UP")),
                    showCurrentPositionMarker = obj.optBoolean("showCurrentPositionMarker", true),
                    showRoadAlignment = obj.optBoolean("showRoadAlignment", true),
                    showCurrentChainageLabel = obj.optBoolean("showCurrentChainageLabel", true),
                    showSideIndicator = obj.optBoolean("showSideIndicator", true),
                    showDirectionArrow = obj.optBoolean("showDirectionArrow", true),
                    showNearbyChainageMarkers = obj.optBoolean("showNearbyChainageMarkers", true)
                )
            } catch (_: Exception) {
                MiniMapStyleConfig()
            }
        }
    }
}
