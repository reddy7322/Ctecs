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

    val isLandscape: Boolean
        get() = this != PORTRAIT

    companion object {
        fun fromString(raw: String?): ScreensaverDisplayMode {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: PORTRAIT
        }
    }
}

enum class ScreensaverThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromString(raw: String?): ScreensaverThemeMode {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: SYSTEM
        }
    }
}

enum class HudElementSize(val label: String, val scaleFactor: Float) {
    SMALL("Small", 0.84f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.16f),
    EXTRA_LARGE("Max", 1.32f);

    companion object {
        fun fromString(raw: String?, fallback: HudElementSize = MEDIUM): HudElementSize {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: fallback
        }
    }
}

enum class HudFontStyle(val label: String) {
    MODERN("Modern"),
    CONDENSED("Condensed"),
    DIGITAL("Digital");

    companion object {
        fun fromString(raw: String?): HudFontStyle {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: MODERN
        }
    }
}

enum class HudColorPreset(val label: String, val colorLong: Long, val glowLong: Long) {
    WHITE("Pure White", 0xFFF8FAFC, 0xFF38BDF8),
    CYAN("Hud Cyan", 0xFF38BDF8, 0xFF0EA5E9),
    EMERALD("Signal Green", 0xFF34D399, 0xFF10B981),
    AMBER("Warm Amber", 0xFFFBBF24, 0xFFF59E0B);

    companion object {
        fun fromString(raw: String?, fallback: HudColorPreset = WHITE): HudColorPreset {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: fallback
        }
    }
}

enum class HudAnimationSpeed(val label: String, val durationMs: Int) {
    FAST("Fast", 110),
    NORMAL("Normal", 210),
    SMOOTH("Smooth", 360);

    companion object {
        fun fromString(raw: String?): HudAnimationSpeed {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: NORMAL
        }
    }
}

enum class HudPanelSize(val label: String, val paddingDp: Int, val weightBias: Float) {
    COMPACT("Compact", 10, 0.85f),
    STANDARD("Standard", 14, 1.0f),
    EXPANDED("Expanded", 18, 1.18f);

    companion object {
        fun fromString(raw: String?): HudPanelSize {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: STANDARD
        }
    }
}

enum class HudVerticalPosition(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom");

    companion object {
        fun fromString(raw: String?): HudVerticalPosition {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: TOP
        }
    }
}

enum class HudDirectionStyle(val label: String) {
    PILL("Glass Pill"),
    OUTLINE("Outline"),
    MINIMAL("Minimal Text");

    companion object {
        fun fromString(raw: String?): HudDirectionStyle {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: PILL
        }
    }
}

enum class HudDirectionColorMode(val label: String) {
    AUTO_SIDE("Auto Side"),
    CYAN("Cyan"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    WHITE("White");

    companion object {
        fun fromString(raw: String?): HudDirectionColorMode {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: AUTO_SIDE
        }
    }
}

enum class HudDirectionPosition(val label: String) {
    ABOVE_CHAINAGE("Above"),
    BELOW_CHAINAGE("Below"),
    INLINE("Inline");

    companion object {
        fun fromString(raw: String?): HudDirectionPosition {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: BELOW_CHAINAGE
        }
    }
}

enum class HudSpeedometerStyle(val label: String) {
    CIRCULAR_GAUGE("Circular Gauge"),
    DIGITAL("Digital Readout");

    companion object {
        fun fromString(raw: String?): HudSpeedometerStyle {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: CIRCULAR_GAUGE
        }
    }
}

enum class HudLocationPosition(val label: String) {
    BELOW_SIDE("Below Side"),
    TOP_BAR("Top Header"),
    ABOVE_MAP("Above Map");

    companion object {
        fun fromString(raw: String?): HudLocationPosition {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: BELOW_SIDE
        }
    }
}

enum class HudMapPosition(val label: String) {
    AUTO("Auto"),
    TOP("Top"),
    BOTTOM("Bottom");

    companion object {
        fun fromString(raw: String?): HudMapPosition {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: AUTO
        }
    }
}

enum class HudMapMarkerStyle(val label: String) {
    NAVIGATION_ARROW("Nav Arrow"),
    PULSE_DOT("Pulse Dot"),
    CLASSIC_PIN("Route Pin");

    companion object {
        fun fromString(raw: String?): HudMapMarkerStyle {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true) ||
                    it.label.equals(raw?.trim(), ignoreCase = true)
            } ?: NAVIGATION_ARROW
        }
    }
}

/**
 * Persistent shared configuration for:
 * 1. General Home Screensaver behavior (enable/disable, timeout, touch-to-dismiss, timer restart)
 * 2. Display Modes (Portrait / Landscape / Car Dashboard Mirror HUD & brightness)
 * 3. Dashboard Element Customization (Chainage, LHS/RHS Direction, Speedometer, Location)
 * 4. Shared Mini Map behavior across Home Page and In-App Screensaver
 */
data class HomeScreensaverSettings(
    // General Screensaver Settings
    val masterEnabled: Boolean = true,
    val autoActivateEnabled: Boolean = true,
    val inactivityTimeoutSeconds: Int = DEFAULT_TIMEOUT_SEC,
    val isCustomTimeout: Boolean = false,
    val touchToDismiss: Boolean = true,
    val autoRestartTimer: Boolean = true,

    // Display Mode & Orientation Settings
    val displayMode: ScreensaverDisplayMode = ScreensaverDisplayMode.PORTRAIT,
    val autoOrientationEnabled: Boolean = true,
    val orientationLocked: Boolean = false,
    val lockedOrientationIsLandscape: Boolean? = null,
    // AMOLED Always-On Display (AOD) Settings (In-App Low-Power Screensaver Mode)
    val aodEnabled: Boolean = true,
    val aodAutoActivateOnScreenOff: Boolean = false,
    val aodShowChainage: Boolean = true,
    val aodShowDirectionSide: Boolean = true,
    val aodShowTime: Boolean = true,
    val aodShowGpsStatus: Boolean = true,
    val aodShowKmStoneStatus: Boolean = true,
    val aodShowNextKmStone: Boolean = true,
    val aodDimLevel: Float = DEFAULT_AOD_DIM_LEVEL,
    val aodBurnInProtectionEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val showScreenOffControl: Boolean = true,
    val screensaverThemeMode: ScreensaverThemeMode = ScreensaverThemeMode.SYSTEM,
    val mirrorModePreferred: Boolean = (displayMode == ScreensaverDisplayMode.MIRROR),
    val hudBrightness: Float = DEFAULT_HUD_BRIGHTNESS,

    // Dashboard Customisation - Chainage
    val showChainage: Boolean = true,
    val chainageFontSize: HudElementSize = HudElementSize.LARGE,
    val chainageFontStyle: HudFontStyle = HudFontStyle.MODERN,
    val chainageColorPreset: HudColorPreset = HudColorPreset.WHITE,
    val chainageOdometerAnimation: Boolean = true,
    val chainageAnimationSpeed: HudAnimationSpeed = HudAnimationSpeed.NORMAL,
    val chainagePanelSize: HudPanelSize = HudPanelSize.STANDARD,
    val chainagePanelPosition: HudVerticalPosition = HudVerticalPosition.TOP,

    // Dashboard Customisation - LHS/RHS Direction
    val showDirectionSide: Boolean = true,
    val directionIndicatorStyle: HudDirectionStyle = HudDirectionStyle.PILL,
    val directionArrowsAnimated: Boolean = true,
    val directionColorPreset: HudDirectionColorMode = HudDirectionColorMode.AUTO_SIDE,
    val directionSize: HudElementSize = HudElementSize.MEDIUM,
    val directionPosition: HudDirectionPosition = HudDirectionPosition.BELOW_CHAINAGE,

    // Dashboard Customisation - Speedometer
    val showSpeedometer: Boolean = true,
    val speedometerStyle: HudSpeedometerStyle = HudSpeedometerStyle.CIRCULAR_GAUGE,
    val speedometerSize: HudElementSize = HudElementSize.MEDIUM,
    val speedometerColorPreset: HudColorPreset = HudColorPreset.CYAN,
    val speedometerAnimateNeedle: Boolean = true,
    val speedometerAnimateNumbers: Boolean = true,

    // Dashboard Customisation - Location
    val showLocation: Boolean = true,
    val locationFontSize: HudElementSize = HudElementSize.MEDIUM,
    val locationPosition: HudLocationPosition = HudLocationPosition.BELOW_SIDE,
    val locationDynamicUpdates: Boolean = true,

    // Shared Mini Map Settings (Home + In-App Screensaver — Direction-Up by default)
    val miniMapVisible: Boolean = true,
    val miniMapOrientation: MiniMapOrientationMode = MiniMapOrientationMode.DIRECTION_UP,
    val miniMapZoomLevel: Float = DEFAULT_ZOOM_LEVEL,
    val miniMapZoomStep: Float = ZOOM_STEP,
    val miniMapStyle: MiniMapStyle = MiniMapStyle.STANDARD,
    val miniMapSize: HudPanelSize = HudPanelSize.STANDARD,
    val miniMapPosition: HudMapPosition = HudMapPosition.AUTO,
    val miniMapOpacity: Float = DEFAULT_MINI_MAP_OPACITY,
    val miniMapShowBorder: Boolean = true,
    val miniMapBorderColorPreset: HudColorPreset = HudColorPreset.CYAN,
    val miniMapMarkerStyle: HudMapMarkerStyle = HudMapMarkerStyle.NAVIGATION_ARROW,
    val miniMapShowRouteAlignment: Boolean = true,
    val recenterEpoch: Long = 0L
) {
    val isLandscapeLayout: Boolean
        get() = displayMode == ScreensaverDisplayMode.LANDSCAPE ||
            displayMode == ScreensaverDisplayMode.MIRROR

    val isMirrorMode: Boolean
        get() = displayMode == ScreensaverDisplayMode.MIRROR

    fun effectiveTimeoutMs(): Long =
        inactivityTimeoutSeconds.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC).toLong() * 1000L

    fun selectPortrait(): HomeScreensaverSettings =
        copy(
            displayMode = ScreensaverDisplayMode.PORTRAIT,
            mirrorModePreferred = false
        )

    fun selectLandscape(): HomeScreensaverSettings =
        copy(
            displayMode = ScreensaverDisplayMode.LANDSCAPE,
            mirrorModePreferred = false
        )

    /**
     * Enables or disables Car Dashboard Windshield Mirror Mode.
     * Turning Mirror ON records the Mirror preference and targets Landscape HUD layout.
     * Turning Mirror OFF clears Mirror preference and uses Landscape Dashboard mode.
     */
    fun setMirrorEnabled(enabled: Boolean): HomeScreensaverSettings =
        copy(
            displayMode = if (enabled) {
                ScreensaverDisplayMode.MIRROR
            } else {
                ScreensaverDisplayMode.LANDSCAPE
            },
            mirrorModePreferred = enabled
        )

    fun withAutoOrientation(enabled: Boolean): HomeScreensaverSettings =
        copy(
            autoOrientationEnabled = enabled,
            orientationLocked = if (enabled) false else orientationLocked
        )

    fun withOrientationLock(locked: Boolean, currentIsLandscape: Boolean? = null): HomeScreensaverSettings {
        val resolvedLandscape = currentIsLandscape ?: lockedOrientationIsLandscape ?: displayMode.isLandscape
        return copy(
            orientationLocked = locked,
            lockedOrientationIsLandscape = if (locked) resolvedLandscape else null,
            displayMode = when {
                !locked -> displayMode
                mirrorModePreferred && resolvedLandscape -> ScreensaverDisplayMode.MIRROR
                resolvedLandscape -> ScreensaverDisplayMode.LANDSCAPE
                else -> ScreensaverDisplayMode.PORTRAIT
            }
        )
    }

    fun withKeepScreenAwake(enabled: Boolean): HomeScreensaverSettings =
        copy(keepScreenAwake = enabled)

    fun withShowScreenOffControl(enabled: Boolean): HomeScreensaverSettings =
        copy(showScreenOffControl = enabled)

    fun withThemeMode(mode: ScreensaverThemeMode): HomeScreensaverSettings =
        copy(screensaverThemeMode = mode)

    fun withMirrorQuickToggle(
        enableMirror: Boolean,
        isCurrentlyLandscape: Boolean
    ): HomeScreensaverSettings =
        copy(
            mirrorModePreferred = enableMirror,
            displayMode = when {
                enableMirror -> ScreensaverDisplayMode.MIRROR
                isCurrentlyLandscape -> ScreensaverDisplayMode.LANDSCAPE
                else -> ScreensaverDisplayMode.PORTRAIT
            }
        )

    fun withHudBrightness(value: Float): HomeScreensaverSettings =
        copy(hudBrightness = value.coerceIn(MIN_HUD_BRIGHTNESS, MAX_HUD_BRIGHTNESS))

    fun withTimeoutSeconds(seconds: Int, custom: Boolean): HomeScreensaverSettings =
        copy(
            inactivityTimeoutSeconds = seconds.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC),
            isCustomTimeout = custom
        )

    fun withOrientation(mode: MiniMapOrientationMode): HomeScreensaverSettings =
        copy(miniMapOrientation = mode)

    fun withZoomLevel(zoom: Float): HomeScreensaverSettings =
        copy(miniMapZoomLevel = zoom.coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL))

    fun withZoomIn(): HomeScreensaverSettings =
        withZoomLevel(miniMapZoomLevel + miniMapZoomStep.coerceIn(0.25f, 1.5f))

    fun withZoomOut(): HomeScreensaverSettings =
        withZoomLevel(miniMapZoomLevel - miniMapZoomStep.coerceIn(0.25f, 1.5f))

    fun withMiniMapVisible(visible: Boolean): HomeScreensaverSettings =
        copy(miniMapVisible = visible)

    fun withMiniMapStyle(style: MiniMapStyle): HomeScreensaverSettings =
        copy(miniMapStyle = style)

    fun withMiniMapOpacity(opacity: Float): HomeScreensaverSettings =
        copy(miniMapOpacity = opacity.coerceIn(MIN_MINI_MAP_OPACITY, 1.0f))

    fun withRecenter(): HomeScreensaverSettings =
        copy(recenterEpoch = recenterEpoch + 1L)

    fun withAodEnabled(enabled: Boolean): HomeScreensaverSettings =
        copy(aodEnabled = enabled)

    fun withAodAutoActivateOnScreenOff(autoActivate: Boolean): HomeScreensaverSettings =
        copy(aodAutoActivateOnScreenOff = autoActivate)

    fun withAodDimLevel(level: Float): HomeScreensaverSettings =
        copy(aodDimLevel = level.coerceIn(MIN_AOD_DIM_LEVEL, MAX_AOD_DIM_LEVEL))

    fun withAodBurnInProtection(enabled: Boolean): HomeScreensaverSettings =
        copy(aodBurnInProtectionEnabled = enabled)

    fun toJsonString(): String {
        val obj = JSONObject()
        // General
        obj.put("masterEnabled", masterEnabled)
        obj.put("autoActivateEnabled", autoActivateEnabled)
        obj.put("inactivityTimeoutSeconds", inactivityTimeoutSeconds)
        obj.put("isCustomTimeout", isCustomTimeout)
        obj.put("touchToDismiss", touchToDismiss)
        obj.put("autoRestartTimer", autoRestartTimer)

        // Display Mode & Orientation
        obj.put("displayMode", displayMode.name)
        obj.put("autoOrientationEnabled", autoOrientationEnabled)
        obj.put("orientationLocked", orientationLocked)
        if (lockedOrientationIsLandscape != null) {
            obj.put("lockedOrientationIsLandscape", lockedOrientationIsLandscape)
        }
        obj.put("keepScreenAwake", keepScreenAwake)
        obj.put("showScreenOffControl", showScreenOffControl)
        obj.put("screensaverThemeMode", screensaverThemeMode.name)
        obj.put("mirrorModePreferred", mirrorModePreferred)
        obj.put("hudBrightness", hudBrightness.toDouble())

        // AMOLED Always-On Display
        obj.put("aodEnabled", aodEnabled)
        obj.put("aodAutoActivateOnScreenOff", aodAutoActivateOnScreenOff)
        obj.put("aodShowChainage", aodShowChainage)
        obj.put("aodShowDirectionSide", aodShowDirectionSide)
        obj.put("aodShowTime", aodShowTime)
        obj.put("aodShowGpsStatus", aodShowGpsStatus)
        obj.put("aodShowKmStoneStatus", aodShowKmStoneStatus)
        obj.put("aodShowNextKmStone", aodShowNextKmStone)
        obj.put("aodDimLevel", aodDimLevel.coerceIn(MIN_AOD_DIM_LEVEL, MAX_AOD_DIM_LEVEL).toDouble())
        obj.put("aodBurnInProtectionEnabled", aodBurnInProtectionEnabled)

        // Chainage
        obj.put("showChainage", showChainage)
        obj.put("chainageFontSize", chainageFontSize.name)
        obj.put("chainageFontStyle", chainageFontStyle.name)
        obj.put("chainageColorPreset", chainageColorPreset.name)
        obj.put("chainageOdometerAnimation", chainageOdometerAnimation)
        obj.put("chainageAnimationSpeed", chainageAnimationSpeed.name)
        obj.put("chainagePanelSize", chainagePanelSize.name)
        obj.put("chainagePanelPosition", chainagePanelPosition.name)

        // Direction
        obj.put("showDirectionSide", showDirectionSide)
        obj.put("directionIndicatorStyle", directionIndicatorStyle.name)
        obj.put("directionArrowsAnimated", directionArrowsAnimated)
        obj.put("directionColorPreset", directionColorPreset.name)
        obj.put("directionSize", directionSize.name)
        obj.put("directionPosition", directionPosition.name)

        // Speedometer
        obj.put("showSpeedometer", showSpeedometer)
        obj.put("speedometerStyle", speedometerStyle.name)
        obj.put("speedometerSize", speedometerSize.name)
        obj.put("speedometerColorPreset", speedometerColorPreset.name)
        obj.put("speedometerAnimateNeedle", speedometerAnimateNeedle)
        obj.put("speedometerAnimateNumbers", speedometerAnimateNumbers)

        // Location
        obj.put("showLocation", showLocation)
        obj.put("locationFontSize", locationFontSize.name)
        obj.put("locationPosition", locationPosition.name)
        obj.put("locationDynamicUpdates", locationDynamicUpdates)

        // Shared Mini Map
        obj.put("miniMapVisible", miniMapVisible)
        obj.put("miniMapOrientation", miniMapOrientation.name)
        obj.put("miniMapZoomLevel", miniMapZoomLevel.toDouble())
        obj.put("miniMapZoomStep", miniMapZoomStep.toDouble())
        obj.put("miniMapStyle", miniMapStyle.name)
        obj.put("miniMapSize", miniMapSize.name)
        obj.put("miniMapPosition", miniMapPosition.name)
        obj.put("miniMapOpacity", miniMapOpacity.toDouble())
        obj.put("miniMapShowBorder", miniMapShowBorder)
        obj.put("miniMapBorderColorPreset", miniMapBorderColorPreset.name)
        obj.put("miniMapMarkerStyle", miniMapMarkerStyle.name)
        obj.put("miniMapShowRouteAlignment", miniMapShowRouteAlignment)
        return obj.toString()
    }

    companion object {
        const val MIN_HUD_BRIGHTNESS = 0.35f
        const val MAX_HUD_BRIGHTNESS = 1.0f
        const val DEFAULT_HUD_BRIGHTNESS = 0.90f

        const val MIN_AOD_DIM_LEVEL = 0.18f
        const val MAX_AOD_DIM_LEVEL = 0.78f
        const val DEFAULT_AOD_DIM_LEVEL = 0.38f

        const val MIN_ZOOM_LEVEL = 2.0f
        const val MAX_ZOOM_LEVEL = 19.0f
        const val DEFAULT_ZOOM_LEVEL = 16.0f
        const val ZOOM_STEP = 0.75f
        const val MIN_MINI_MAP_OPACITY = 0.35f
        const val DEFAULT_MINI_MAP_OPACITY = 0.92f

        const val MIN_TIMEOUT_SEC = 5
        const val MAX_TIMEOUT_SEC = 600
        const val DEFAULT_TIMEOUT_SEC = 20
        val TIMEOUT_PRESETS_SEC = listOf(10, 15, 20, 30, 45, 60)

        fun defaultSettings(): HomeScreensaverSettings = HomeScreensaverSettings()

        fun fromJsonString(json: String?): HomeScreensaverSettings {
            if (json.isNullOrBlank()) return HomeScreensaverSettings()
            return try {
                val obj = JSONObject(json)
                val parsedDisplayMode = ScreensaverDisplayMode.fromString(
                    obj.optString("displayMode", ScreensaverDisplayMode.PORTRAIT.name)
                )
                HomeScreensaverSettings(
                    masterEnabled = obj.optBoolean("masterEnabled", true),
                    autoActivateEnabled = obj.optBoolean("autoActivateEnabled", true),
                    inactivityTimeoutSeconds = obj.optInt("inactivityTimeoutSeconds", DEFAULT_TIMEOUT_SEC)
                        .coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC),
                    isCustomTimeout = obj.optBoolean("isCustomTimeout", false),
                    touchToDismiss = obj.optBoolean("touchToDismiss", true),
                    autoRestartTimer = obj.optBoolean("autoRestartTimer", true),

                    displayMode = parsedDisplayMode,
                    autoOrientationEnabled = obj.optBoolean("autoOrientationEnabled", true),
                    orientationLocked = obj.optBoolean("orientationLocked", false),
                    lockedOrientationIsLandscape = if (obj.has("lockedOrientationIsLandscape")) {
                        obj.optBoolean("lockedOrientationIsLandscape")
                    } else {
                        null
                    },
                    aodEnabled = obj.optBoolean("aodEnabled", true),
                    aodAutoActivateOnScreenOff = obj.optBoolean("aodAutoActivateOnScreenOff", false),
                    aodShowChainage = obj.optBoolean("aodShowChainage", true),
                    aodShowDirectionSide = obj.optBoolean("aodShowDirectionSide", true),
                    aodShowTime = obj.optBoolean("aodShowTime", true),
                    aodShowGpsStatus = obj.optBoolean("aodShowGpsStatus", true),
                    aodShowKmStoneStatus = obj.optBoolean("aodShowKmStoneStatus", true),
                    aodShowNextKmStone = obj.optBoolean("aodShowNextKmStone", true),
                    aodDimLevel = obj.optDouble("aodDimLevel", DEFAULT_AOD_DIM_LEVEL.toDouble())
                        .toFloat()
                        .coerceIn(MIN_AOD_DIM_LEVEL, MAX_AOD_DIM_LEVEL),
                    aodBurnInProtectionEnabled = obj.optBoolean("aodBurnInProtectionEnabled", true),
                    keepScreenAwake = obj.optBoolean("keepScreenAwake", true),
                    showScreenOffControl = obj.optBoolean("showScreenOffControl", true),
                    screensaverThemeMode = ScreensaverThemeMode.fromString(
                        obj.optString("screensaverThemeMode", ScreensaverThemeMode.SYSTEM.name)
                    ),
                    mirrorModePreferred = obj.optBoolean(
                        "mirrorModePreferred",
                        parsedDisplayMode == ScreensaverDisplayMode.MIRROR
                    ),
                    hudBrightness = obj.optDouble("hudBrightness", DEFAULT_HUD_BRIGHTNESS.toDouble())
                        .toFloat()
                        .coerceIn(MIN_HUD_BRIGHTNESS, MAX_HUD_BRIGHTNESS),

                    showChainage = obj.optBoolean("showChainage", true),
                    chainageFontSize = HudElementSize.fromString(
                        obj.optString("chainageFontSize", HudElementSize.LARGE.name),
                        fallback = HudElementSize.LARGE
                    ),
                    chainageFontStyle = HudFontStyle.fromString(
                        obj.optString("chainageFontStyle", HudFontStyle.MODERN.name)
                    ),
                    chainageColorPreset = HudColorPreset.fromString(
                        obj.optString("chainageColorPreset", HudColorPreset.WHITE.name),
                        fallback = HudColorPreset.WHITE
                    ),
                    chainageOdometerAnimation = obj.optBoolean("chainageOdometerAnimation", true),
                    chainageAnimationSpeed = HudAnimationSpeed.fromString(
                        obj.optString("chainageAnimationSpeed", HudAnimationSpeed.NORMAL.name)
                    ),
                    chainagePanelSize = HudPanelSize.fromString(
                        obj.optString("chainagePanelSize", HudPanelSize.STANDARD.name)
                    ),
                    chainagePanelPosition = HudVerticalPosition.fromString(
                        obj.optString("chainagePanelPosition", HudVerticalPosition.TOP.name)
                    ),

                    showDirectionSide = obj.optBoolean("showDirectionSide", true),
                    directionIndicatorStyle = HudDirectionStyle.fromString(
                        obj.optString("directionIndicatorStyle", HudDirectionStyle.PILL.name)
                    ),
                    directionArrowsAnimated = obj.optBoolean("directionArrowsAnimated", true),
                    directionColorPreset = HudDirectionColorMode.fromString(
                        obj.optString("directionColorPreset", HudDirectionColorMode.AUTO_SIDE.name)
                    ),
                    directionSize = HudElementSize.fromString(
                        obj.optString("directionSize", HudElementSize.MEDIUM.name),
                        fallback = HudElementSize.MEDIUM
                    ),
                    directionPosition = HudDirectionPosition.fromString(
                        obj.optString("directionPosition", HudDirectionPosition.BELOW_CHAINAGE.name)
                    ),

                    showSpeedometer = obj.optBoolean("showSpeedometer", true),
                    speedometerStyle = HudSpeedometerStyle.fromString(
                        obj.optString("speedometerStyle", HudSpeedometerStyle.CIRCULAR_GAUGE.name)
                    ),
                    speedometerSize = HudElementSize.fromString(
                        obj.optString("speedometerSize", HudElementSize.MEDIUM.name),
                        fallback = HudElementSize.MEDIUM
                    ),
                    speedometerColorPreset = HudColorPreset.fromString(
                        obj.optString("speedometerColorPreset", HudColorPreset.CYAN.name),
                        fallback = HudColorPreset.CYAN
                    ),
                    speedometerAnimateNeedle = obj.optBoolean("speedometerAnimateNeedle", true),
                    speedometerAnimateNumbers = obj.optBoolean("speedometerAnimateNumbers", true),

                    showLocation = obj.optBoolean("showLocation", true),
                    locationFontSize = HudElementSize.fromString(
                        obj.optString("locationFontSize", HudElementSize.MEDIUM.name),
                        fallback = HudElementSize.MEDIUM
                    ),
                    locationPosition = HudLocationPosition.fromString(
                        obj.optString("locationPosition", HudLocationPosition.BELOW_SIDE.name)
                    ),
                    locationDynamicUpdates = obj.optBoolean("locationDynamicUpdates", true),

                    miniMapVisible = obj.optBoolean("miniMapVisible", true),
                    miniMapOrientation = MiniMapOrientationMode.fromString(
                        obj.optString("miniMapOrientation", MiniMapOrientationMode.DIRECTION_UP.name)
                    ),
                    miniMapZoomLevel = obj.optDouble("miniMapZoomLevel", DEFAULT_ZOOM_LEVEL.toDouble())
                        .toFloat()
                        .coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL),
                    miniMapZoomStep = obj.optDouble("miniMapZoomStep", ZOOM_STEP.toDouble())
                        .toFloat()
                        .coerceIn(0.25f, 1.5f),
                    miniMapStyle = MiniMapStyle.fromString(
                        obj.optString("miniMapStyle", MiniMapStyle.STANDARD.name)
                    ),
                    miniMapSize = HudPanelSize.fromString(
                        obj.optString("miniMapSize", HudPanelSize.STANDARD.name)
                    ),
                    miniMapPosition = HudMapPosition.fromString(
                        obj.optString("miniMapPosition", HudMapPosition.AUTO.name)
                    ),
                    miniMapOpacity = obj.optDouble("miniMapOpacity", DEFAULT_MINI_MAP_OPACITY.toDouble())
                        .toFloat()
                        .coerceIn(MIN_MINI_MAP_OPACITY, 1.0f),
                    miniMapShowBorder = obj.optBoolean("miniMapShowBorder", true),
                    miniMapBorderColorPreset = HudColorPreset.fromString(
                        obj.optString("miniMapBorderColorPreset", HudColorPreset.CYAN.name),
                        fallback = HudColorPreset.CYAN
                    ),
                    miniMapMarkerStyle = HudMapMarkerStyle.fromString(
                        obj.optString("miniMapMarkerStyle", HudMapMarkerStyle.NAVIGATION_ARROW.name)
                    ),
                    miniMapShowRouteAlignment = obj.optBoolean("miniMapShowRouteAlignment", true)
                )
            } catch (_: Exception) {
                HomeScreensaverSettings()
            }
        }
    }
}

/**
 * Resolved live presentation state for the active Screensaver.
 * Combines saved settings, auto-orientation sensor state, orientation lock, and Mirror Mode landscape coupling.
 */
data class EffectiveScreensaverPresentation(
    val activeMode: ScreensaverDisplayMode,
    val isLandscape: Boolean,
    val isMirror: Boolean,
    val mirrorPreferenceEnabled: Boolean,
    val isOrientationLocked: Boolean = false
)

fun resolveEffectiveScreensaverPresentation(
    settings: HomeScreensaverSettings,
    sensorIsLandscape: Boolean?,
    windowIsLandscape: Boolean,
    temporaryLandscapeOverride: Boolean = false
): EffectiveScreensaverPresentation {
    val mirrorPref = settings.mirrorModePreferred || settings.displayMode == ScreensaverDisplayMode.MIRROR
    if (settings.orientationLocked) {
        val lockedLandscape = settings.lockedOrientationIsLandscape ?: settings.displayMode.isLandscape
        val lockedMode = when {
            !lockedLandscape -> ScreensaverDisplayMode.PORTRAIT
            mirrorPref -> ScreensaverDisplayMode.MIRROR
            else -> ScreensaverDisplayMode.LANDSCAPE
        }
        return EffectiveScreensaverPresentation(
            activeMode = lockedMode,
            isLandscape = lockedLandscape,
            isMirror = lockedMode == ScreensaverDisplayMode.MIRROR,
            mirrorPreferenceEnabled = mirrorPref,
            isOrientationLocked = true
        )
    }

    if (!settings.autoOrientationEnabled) {
        val manualMode = settings.displayMode
        return EffectiveScreensaverPresentation(
            activeMode = manualMode,
            isLandscape = manualMode.isLandscape,
            isMirror = manualMode == ScreensaverDisplayMode.MIRROR,
            mirrorPreferenceEnabled = mirrorPref,
            isOrientationLocked = false
        )
    }

    val isLandscape = when {
        temporaryLandscapeOverride -> true
        sensorIsLandscape != null -> sensorIsLandscape
        settings.displayMode.isLandscape -> true
        else -> windowIsLandscape
    }

    val activeMode = when {
        !isLandscape -> ScreensaverDisplayMode.PORTRAIT
        mirrorPref -> ScreensaverDisplayMode.MIRROR
        else -> ScreensaverDisplayMode.LANDSCAPE
    }

    return EffectiveScreensaverPresentation(
        activeMode = activeMode,
        isLandscape = isLandscape,
        isMirror = activeMode == ScreensaverDisplayMode.MIRROR,
        mirrorPreferenceEnabled = mirrorPref,
        isOrientationLocked = false
    )
}
