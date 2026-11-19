package com.example.model

import org.json.JSONObject

/**
 * Dedicated preferences for CTECS Status-Bar & Notification-Shade Navigation Notifications
 * (including Android 15/16 Live Updates & Samsung Now Bar compatibility).
 */
data class NavigationNotificationSettings(
    val enabled: Boolean = false,
    val showChainage: Boolean = true,
    val showRoadSide: Boolean = true,
    val showNextKmMarker: Boolean = true,
    val showDistanceRemaining: Boolean = true,
    val showSpeed: Boolean = true,
    val showLocation: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val expandedInfoEnabled: Boolean = true,
    val liveNotificationEnabled: Boolean = true
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("enabled", enabled)
            put("showChainage", showChainage)
            put("showRoadSide", showRoadSide)
            put("showNextKmMarker", showNextKmMarker)
            put("showDistanceRemaining", showDistanceRemaining)
            put("showSpeed", showSpeed)
            put("showLocation", showLocation)
            put("showOnLockScreen", showOnLockScreen)
            put("expandedInfoEnabled", expandedInfoEnabled)
            put("liveNotificationEnabled", liveNotificationEnabled)
        }.toString()
    }

    companion object {
        fun defaultSettings(): NavigationNotificationSettings = NavigationNotificationSettings()

        fun fromJsonString(json: String?): NavigationNotificationSettings {
            if (json.isNullOrBlank()) return NavigationNotificationSettings()
            return try {
                val obj = JSONObject(json)
                NavigationNotificationSettings(
                    enabled = obj.optBoolean("enabled", false),
                    showChainage = obj.optBoolean("showChainage", true),
                    showRoadSide = obj.optBoolean("showRoadSide", true),
                    showNextKmMarker = obj.optBoolean("showNextKmMarker", true),
                    showDistanceRemaining = obj.optBoolean("showDistanceRemaining", true),
                    showSpeed = obj.optBoolean("showSpeed", true),
                    showLocation = obj.optBoolean("showLocation", true),
                    showOnLockScreen = obj.optBoolean("showOnLockScreen", true),
                    expandedInfoEnabled = obj.optBoolean("expandedInfoEnabled", true),
                    liveNotificationEnabled = obj.optBoolean("liveNotificationEnabled", true)
                )
            } catch (_: Exception) {
                NavigationNotificationSettings()
            }
        }
    }
}
