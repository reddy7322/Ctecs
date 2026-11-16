package com.example.model

import org.json.JSONObject

/**
 * Display mode options for the Home Page Automotive Screensaver.
 * Note: [MIRROR] is only valid in Landscape layout; selecting [PORTRAIT] exits Mirror Mode.
 */
enum class ScreensaverDisplayMode(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    MIRROR("Mirror HUD");

    companion object {
        fun fromString(raw: String?): ScreensaverDisplayMode {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: PORTRAIT
        }
    }
}

/**
 * Persistent shared configuration for the Home Page Screensaver, Automotive HUD Mirror Mode,
 * and shared Home / Screensaver Mini Map behavior.
 */
data class HomeScreensaverSettings(
    val displayMode: ScreensaverDisplayMode = ScreensaverDisplayMode.PORTRAIT,
    val hudBrightness: Float = DEFAULT_HUD_BRIGHTNESS,
    val miniMapVisible: Boolean = true,
    val miniMapOrientation: MiniMapOrientationMode = MiniMapOrientationMode.NORTH_UP,
    val miniMapZoomLevel: Float = DEFAULT_ZOOM_LEVEL,
    val miniMapStyle: MiniMapStyle = MiniMapStyle.STANDARD,
    val recenterEpoch: Long = 0L
) {
    val isLandscapeLayout: Boolean
        get() = displayMode == ScreensaverDisplayMode.LANDSCAPE ||
            displayMode == ScreensaverDisplayMode.MIRROR

    val isMirrorMode: Boolean
        get() = displayMode == ScreensaverDisplayMode.MIRROR

    fun selectPortrait(): HomeScreensaverSettings =
        copy(displayMode = ScreensaverDisplayMode.PORTRAIT)

    fun selectLandscape(): HomeScreensaverSettings =
        copy(displayMode = ScreensaverDisplayMode.LANDSCAPE)

    /**
     * Enables or disables Car Dashboard Windshield Mirror Mode.
     * Turning Mirror ON automatically switches to Landscape layout as required for HUD reflection.
     * Turning Mirror OFF returns to standard Landscape Dashboard mode.
     */
    fun setMirrorEnabled(enabled: Boolean): HomeScreensaverSettings =
        copy(
            displayMode = if (enabled) {
                ScreensaverDisplayMode.MIRROR
            } else {
                ScreensaverDisplayMode.LANDSCAPE
            }
        )

    fun withHudBrightness(value: Float): HomeScreensaverSettings =
        copy(hudBrightness = value.coerceIn(MIN_HUD_BRIGHTNESS, MAX_HUD_BRIGHTNESS))

    fun withMiniMapVisible(visible: Boolean): HomeScreensaverSettings =
        copy(miniMapVisible = visible)

    fun withOrientation(mode: MiniMapOrientationMode): HomeScreensaverSettings =
        copy(miniMapOrientation = mode)

    fun withZoomLevel(zoom: Float): HomeScreensaverSettings =
        copy(miniMapZoomLevel = zoom.coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL))

    fun withZoomIn(): HomeScreensaverSettings =
        withZoomLevel(miniMapZoomLevel + ZOOM_STEP)

    fun withZoomOut(): HomeScreensaverSettings =
        withZoomLevel(miniMapZoomLevel - ZOOM_STEP)

    fun withMiniMapStyle(style: MiniMapStyle): HomeScreensaverSettings =
        copy(miniMapStyle = style)

    fun withRecenter(): HomeScreensaverSettings =
        copy(recenterEpoch = recenterEpoch + 1L)

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("displayMode", displayMode.name)
        obj.put("hudBrightness", hudBrightness.toDouble())
        obj.put("miniMapVisible", miniMapVisible)
        obj.put("miniMapOrientation", miniMapOrientation.name)
        obj.put("miniMapZoomLevel", miniMapZoomLevel.toDouble())
        obj.put("miniMapStyle", miniMapStyle.name)
        return obj.toString()
    }

    companion object {
        const val MIN_HUD_BRIGHTNESS = 0.35f
        const val MAX_HUD_BRIGHTNESS = 1.0f
        const val DEFAULT_HUD_BRIGHTNESS = 0.90f

        const val MIN_ZOOM_LEVEL = 12.0f
        const val MAX_ZOOM_LEVEL = 18.5f
        const val DEFAULT_ZOOM_LEVEL = 16.0f
        const val ZOOM_STEP = 0.75f

        fun fromJsonString(json: String?): HomeScreensaverSettings {
            if (json.isNullOrBlank()) return HomeScreensaverSettings()
            return try {
                val obj = JSONObject(json)
                HomeScreensaverSettings(
                    displayMode = ScreensaverDisplayMode.fromString(
                        obj.optString("displayMode", ScreensaverDisplayMode.PORTRAIT.name)
                    ),
                    hudBrightness = obj.optDouble("hudBrightness", DEFAULT_HUD_BRIGHTNESS.toDouble())
                        .toFloat()
                        .coerceIn(MIN_HUD_BRIGHTNESS, MAX_HUD_BRIGHTNESS),
                    miniMapVisible = obj.optBoolean("miniMapVisible", true),
                    miniMapOrientation = MiniMapOrientationMode.fromString(
                        obj.optString("miniMapOrientation", MiniMapOrientationMode.NORTH_UP.name)
                    ),
                    miniMapZoomLevel = obj.optDouble("miniMapZoomLevel", DEFAULT_ZOOM_LEVEL.toDouble())
                        .toFloat()
                        .coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL),
                    miniMapStyle = MiniMapStyle.fromString(
                        obj.optString("miniMapStyle", MiniMapStyle.STANDARD.name)
                    )
                )
            } catch (_: Exception) {
                HomeScreensaverSettings()
            }
        }
    }
}
