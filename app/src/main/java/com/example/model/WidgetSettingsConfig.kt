package com.example.model

import org.json.JSONArray
import org.json.JSONObject

enum class CtecsWidgetType(val id: String, val label: String) {
    COMPACT("compact", "Compact Widget"),
    NAVIGATION("navigation", "Navigation Widget"),
    MINI_MAP("mini_map", "Mini Map Widget"),
    DASHBOARD("dashboard", "Dashboard Widget");

    companion object {
        fun fromString(raw: String?): CtecsWidgetType =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.id.equals(raw, ignoreCase = true)
            } ?: COMPACT
    }
}

enum class WidgetThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System Default");

    companion object {
        fun fromString(raw: String?): WidgetThemeMode =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: DARK
    }
}

enum class WidgetBackgroundStyle(val label: String, val alphaFraction: Float) {
    SOLID("Solid Flat", 1.0f),
    SOFT_FLAT("Muted Flat (92%)", 0.92f),
    TRANSPARENT("Transparent", 0.0f);

    companion object {
        fun fromString(raw: String?): WidgetBackgroundStyle =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: SOLID
    }
}

enum class WidgetAccentColorPreset(
    val label: String,
    val darkColorInt: Int,
    val lightColorInt: Int
) {
    CYAN("Cyan", 0xFF38BDF8.toInt(), 0xFF0284C7.toInt()),
    BLUE("Highway Blue", 0xFF60A5FA.toInt(), 0xFF2563EB.toInt()),
    EMERALD("Emerald", 0xFF34D399.toInt(), 0xFF059669.toInt()),
    AMBER("Amber", 0xFFFBBF24.toInt(), 0xFFD97706.toInt()),
    SLATE("Monochrome Slate", 0xFFE2E8F0.toInt(), 0xFF334155.toInt());

    companion object {
        fun fromString(raw: String?): WidgetAccentColorPreset =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: CYAN
    }
}

enum class WidgetSizeTier {
    COMPACT,
    MEDIUM,
    LARGE
}

/**
 * Per-widget instance configuration override so individual home-screen widget instances
 * can retain their own theme, background style, accent color, field toggles, and map orientation.
 */
data class CtecsWidgetInstanceConfig(
    val appWidgetId: Int,
    val widgetType: CtecsWidgetType = CtecsWidgetType.COMPACT,
    val useCustomOverride: Boolean = true,
    val themeMode: WidgetThemeMode = WidgetThemeMode.DARK,
    val backgroundStyle: WidgetBackgroundStyle = WidgetBackgroundStyle.SOLID,
    val accentColor: WidgetAccentColorPreset = WidgetAccentColorPreset.CYAN,
    val showChainage: Boolean = true,
    val showRoadSide: Boolean = true,
    val showSpeed: Boolean = true,
    val showNextKmMarker: Boolean = true,
    val showDistanceRemaining: Boolean = true,
    val showLocation: Boolean = true,
    val showMiniMap: Boolean = true,
    val mapOrientation: MiniMapOrientationMode = MiniMapOrientationMode.DIRECTION_UP
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("appWidgetId", appWidgetId)
            put("widgetType", widgetType.name)
            put("useCustomOverride", useCustomOverride)
            put("themeMode", themeMode.name)
            put("backgroundStyle", backgroundStyle.name)
            put("accentColor", accentColor.name)
            put("showChainage", showChainage)
            put("showRoadSide", showRoadSide)
            put("showSpeed", showSpeed)
            put("showNextKmMarker", showNextKmMarker)
            put("showDistanceRemaining", showDistanceRemaining)
            put("showLocation", showLocation)
            put("showMiniMap", showMiniMap)
            put("mapOrientation", mapOrientation.name)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): CtecsWidgetInstanceConfig {
            return CtecsWidgetInstanceConfig(
                appWidgetId = obj.optInt("appWidgetId", 0),
                widgetType = CtecsWidgetType.fromString(obj.optString("widgetType", "COMPACT")),
                useCustomOverride = obj.optBoolean("useCustomOverride", true),
                themeMode = WidgetThemeMode.fromString(obj.optString("themeMode", "DARK")),
                backgroundStyle = WidgetBackgroundStyle.fromString(obj.optString("backgroundStyle", "SOLID")),
                accentColor = WidgetAccentColorPreset.fromString(obj.optString("accentColor", "CYAN")),
                showChainage = obj.optBoolean("showChainage", true),
                showRoadSide = obj.optBoolean("showRoadSide", true),
                showSpeed = obj.optBoolean("showSpeed", true),
                showNextKmMarker = obj.optBoolean("showNextKmMarker", true),
                showDistanceRemaining = obj.optBoolean("showDistanceRemaining", true),
                showLocation = obj.optBoolean("showLocation", true),
                showMiniMap = obj.optBoolean("showMiniMap", true),
                mapOrientation = MiniMapOrientationMode.fromString(obj.optString("mapOrientation", "DIRECTION_UP"))
            )
        }
    }
}

/**
 * Global default widget settings (Settings -> Widgets) plus per-instance widget overrides.
 */
data class CtecsWidgetSettings(
    val themeMode: WidgetThemeMode = WidgetThemeMode.DARK,
    val backgroundStyle: WidgetBackgroundStyle = WidgetBackgroundStyle.SOLID,
    val accentColor: WidgetAccentColorPreset = WidgetAccentColorPreset.CYAN,
    val showChainage: Boolean = true,
    val showRoadSide: Boolean = true,
    val showSpeed: Boolean = true,
    val showNextKmMarker: Boolean = true,
    val showDistanceRemaining: Boolean = true,
    val showLocation: Boolean = true,
    val showMiniMap: Boolean = true,
    val mapOrientation: MiniMapOrientationMode = MiniMapOrientationMode.DIRECTION_UP,
    val instanceOverrides: Map<Int, CtecsWidgetInstanceConfig> = emptyMap()
) {
    fun resolveForInstance(
        appWidgetId: Int,
        widgetType: CtecsWidgetType = CtecsWidgetType.COMPACT
    ): CtecsWidgetInstanceConfig {
        val custom = instanceOverrides[appWidgetId]
        if (custom != null && custom.useCustomOverride) {
            return custom.copy(widgetType = widgetType)
        }
        return CtecsWidgetInstanceConfig(
            appWidgetId = appWidgetId,
            widgetType = widgetType,
            useCustomOverride = false,
            themeMode = themeMode,
            backgroundStyle = backgroundStyle,
            accentColor = accentColor,
            showChainage = showChainage,
            showRoadSide = showRoadSide,
            showSpeed = showSpeed,
            showNextKmMarker = showNextKmMarker,
            showDistanceRemaining = showDistanceRemaining,
            showLocation = showLocation,
            showMiniMap = showMiniMap,
            mapOrientation = mapOrientation
        )
    }

    fun withInstanceConfig(config: CtecsWidgetInstanceConfig): CtecsWidgetSettings {
        val nextMap = instanceOverrides.toMutableMap()
        nextMap[config.appWidgetId] = config.copy(useCustomOverride = true)
        return copy(instanceOverrides = nextMap)
    }

    fun withoutInstanceConfig(appWidgetId: Int): CtecsWidgetSettings {
        if (!instanceOverrides.containsKey(appWidgetId)) return this
        val nextMap = instanceOverrides.toMutableMap()
        nextMap.remove(appWidgetId)
        return copy(instanceOverrides = nextMap)
    }

    fun toJsonString(): String {
        val root = JSONObject().apply {
            put("themeMode", themeMode.name)
            put("backgroundStyle", backgroundStyle.name)
            put("accentColor", accentColor.name)
            put("showChainage", showChainage)
            put("showRoadSide", showRoadSide)
            put("showSpeed", showSpeed)
            put("showNextKmMarker", showNextKmMarker)
            put("showDistanceRemaining", showDistanceRemaining)
            put("showLocation", showLocation)
            put("showMiniMap", showMiniMap)
            put("mapOrientation", mapOrientation.name)
            val arr = JSONArray()
            instanceOverrides.values.sortedBy { it.appWidgetId }.forEach { inst ->
                arr.put(inst.toJson())
            }
            put("instanceOverrides", arr)
        }
        return root.toString()
    }

    companion object {
        fun defaultSettings(): CtecsWidgetSettings = CtecsWidgetSettings()

        fun fromJsonString(raw: String?): CtecsWidgetSettings {
            if (raw.isNullOrBlank()) return CtecsWidgetSettings()
            return try {
                val obj = JSONObject(raw)
                val overridesMap = linkedMapOf<Int, CtecsWidgetInstanceConfig>()
                val arr = obj.optJSONArray("instanceOverrides")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val parsed = CtecsWidgetInstanceConfig.fromJson(item)
                        if (parsed.appWidgetId != 0) {
                            overridesMap[parsed.appWidgetId] = parsed
                        }
                    }
                }
                CtecsWidgetSettings(
                    themeMode = WidgetThemeMode.fromString(obj.optString("themeMode", "DARK")),
                    backgroundStyle = WidgetBackgroundStyle.fromString(obj.optString("backgroundStyle", "SOLID")),
                    accentColor = WidgetAccentColorPreset.fromString(obj.optString("accentColor", "CYAN")),
                    showChainage = obj.optBoolean("showChainage", true),
                    showRoadSide = obj.optBoolean("showRoadSide", true),
                    showSpeed = obj.optBoolean("showSpeed", true),
                    showNextKmMarker = obj.optBoolean("showNextKmMarker", true),
                    showDistanceRemaining = obj.optBoolean("showDistanceRemaining", true),
                    showLocation = obj.optBoolean("showLocation", true),
                    showMiniMap = obj.optBoolean("showMiniMap", true),
                    mapOrientation = MiniMapOrientationMode.fromString(obj.optString("mapOrientation", "DIRECTION_UP")),
                    instanceOverrides = overridesMap
                )
            } catch (_: Exception) {
                CtecsWidgetSettings()
            }
        }
    }
}
