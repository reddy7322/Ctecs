package com.example.model

import org.json.JSONObject

enum class KmVibrationPattern(val label: String, val defaultTimingsMs: LongArray) {
    SHORT_PULSE("Short Pulse", longArrayOf(0L, 120L)),
    DOUBLE_PULSE("Double Pulse", longArrayOf(0L, 110L, 80L, 140L)),
    TRIPLE_PULSE("Triple Pulse", longArrayOf(0L, 95L, 70L, 95L, 70L, 130L)),
    LONG_PULSE("Long Pulse", longArrayOf(0L, 340L)),
    CUSTOM("Custom Pattern", longArrayOf(0L, 140L, 90L, 200L));

    companion object {
        fun fromString(raw: String?): KmVibrationPattern =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: DOUBLE_PULSE
    }
}

enum class KmVibrationIntensity(val label: String, val amplitude: Int) {
    LOW("Low", 95),
    MEDIUM("Medium", 175),
    HIGH("High", 255);

    companion object {
        fun fromString(raw: String?): KmVibrationIntensity =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: HIGH
    }
}

enum class KmAlertSoundOption(val label: String) {
    DEFAULT_BEEP("Default Beep"),
    DOUBLE_BEEP("Double Beep"),
    DIGITAL_CHIME("Digital Chime"),
    SOFT_ALERT("Soft Alert"),
    CUSTOM_SOUND("Custom Sound");

    companion object {
        fun fromString(raw: String?): KmAlertSoundOption =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: DIGITAL_CHIME
    }
}

enum class KmAdvanceWarningOption(val metres: Int, val label: String) {
    DISABLED(0, "Disabled"),
    M_500(500, "500 metres"),
    M_200(200, "200 metres"),
    M_100(100, "100 metres"),
    M_50(50, "50 metres"),
    CUSTOM(-1, "Custom distance");

    companion object {
        fun fromString(raw: String?): KmAdvanceWarningOption =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: DISABLED
    }
}

/**
 * Persistent settings for Settings -> Navigation Alerts -> KM Stone Arrival.
 * Supports independent arrival vs. advance warning feedback, vibration patterns & intensity,
 * sound selection & volume, and arrival detection thresholds.
 */
data class KmStoneAlertSettings(
    // General & Monitoring Service
    val enabled: Boolean = true,
    val backgroundMonitoringEnabled: Boolean = true,
    val persistentNotificationEnabled: Boolean = true,
    val lockScreenNotificationsEnabled: Boolean = true,
    val arrivalAlertsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    // Vibration Customisation
    val vibrationPattern: KmVibrationPattern = KmVibrationPattern.DOUBLE_PULSE,
    val vibrationIntensity: KmVibrationIntensity = KmVibrationIntensity.HIGH,
    val customVibrationPatternCsv: String = "0,140,90,200",
    // Sound Customisation
    val soundOption: KmAlertSoundOption = KmAlertSoundOption.DIGITAL_CHIME,
    val customSoundUri: String = "",
    val customSoundName: String = "",
    val alertVolumePercent: Int = 85,
    // Arrival Detection
    val proximityThresholdMetres: Int = 15,
    val maxGpsAccuracyMetres: Int = 25,
    val repeatAlertPreventionEnabled: Boolean = true,
    val repeatAlertDistanceMetres: Int = 60,
    // Smart Advance Warning (independent of actual arrival alert)
    val advanceWarningOption: KmAdvanceWarningOption = KmAdvanceWarningOption.DISABLED,
    val customAdvanceWarningMetres: Int = 150,
    val advanceWarningVibrationEnabled: Boolean = true,
    val advanceWarningSoundEnabled: Boolean = true,
    val advanceWarningVibrationPattern: KmVibrationPattern = KmVibrationPattern.SHORT_PULSE,
    val advanceWarningSoundOption: KmAlertSoundOption = KmAlertSoundOption.SOFT_ALERT
) {
    /**
     * Determines whether the dedicated KM Stone Monitoring Foreground Service should stay active.
     * Android requires an ongoing foreground notification for a location foreground service, so
     * disabling [enabled], [backgroundMonitoringEnabled], or [persistentNotificationEnabled] stops
     * the background foreground service cleanly without keeping unnecessary services alive.
     */
    fun shouldRunBackgroundForegroundService(hasActiveRoute: Boolean): Boolean {
        return enabled &&
            backgroundMonitoringEnabled &&
            persistentNotificationEnabled &&
            hasActiveRoute
    }

    fun resolveAdvanceWarningDistanceMetres(): Int {
        return when (advanceWarningOption) {
            KmAdvanceWarningOption.DISABLED -> 0
            KmAdvanceWarningOption.CUSTOM -> customAdvanceWarningMetres.coerceIn(25, 2000)
            else -> advanceWarningOption.metres
        }
    }

    fun resolveVibrationTimings(pattern: KmVibrationPattern = vibrationPattern): LongArray {
        if (pattern != KmVibrationPattern.CUSTOM) {
            return pattern.defaultTimingsMs.clone()
        }
        return parseSafeCustomPattern(customVibrationPatternCsv)
    }

    fun toJsonString(): String {
        val obj = JSONObject().apply {
            put("enabled", enabled)
            put("backgroundMonitoringEnabled", backgroundMonitoringEnabled)
            put("persistentNotificationEnabled", persistentNotificationEnabled)
            put("lockScreenNotificationsEnabled", lockScreenNotificationsEnabled)
            put("arrivalAlertsEnabled", arrivalAlertsEnabled)
            put("hapticFeedbackEnabled", hapticFeedbackEnabled)
            put("vibrationEnabled", vibrationEnabled)
            put("soundEnabled", soundEnabled)
            put("vibrationPattern", vibrationPattern.name)
            put("vibrationIntensity", vibrationIntensity.name)
            put("customVibrationPatternCsv", customVibrationPatternCsv)
            put("soundOption", soundOption.name)
            put("customSoundUri", customSoundUri)
            put("customSoundName", customSoundName)
            put("alertVolumePercent", alertVolumePercent.coerceIn(0, 100))
            put("proximityThresholdMetres", proximityThresholdMetres.coerceIn(5, 100))
            put("maxGpsAccuracyMetres", maxGpsAccuracyMetres.coerceIn(5, 100))
            put("repeatAlertPreventionEnabled", repeatAlertPreventionEnabled)
            put("repeatAlertDistanceMetres", repeatAlertDistanceMetres.coerceIn(25, 300))
            put("advanceWarningOption", advanceWarningOption.name)
            put("customAdvanceWarningMetres", customAdvanceWarningMetres.coerceIn(25, 2000))
            put("advanceWarningVibrationEnabled", advanceWarningVibrationEnabled)
            put("advanceWarningSoundEnabled", advanceWarningSoundEnabled)
            put("advanceWarningVibrationPattern", advanceWarningVibrationPattern.name)
            put("advanceWarningSoundOption", advanceWarningSoundOption.name)
        }
        return obj.toString()
    }

    companion object {
        val PROXIMITY_THRESHOLD_OPTIONS = listOf(10, 15, 20, 25, 35, 50)
        val GPS_ACCURACY_OPTIONS = listOf(15, 20, 25, 35, 50)
        val REPEAT_PREVENTION_DISTANCE_OPTIONS = listOf(40, 60, 80, 100, 150)

        fun defaultSettings(): KmStoneAlertSettings = KmStoneAlertSettings()

        /**
         * Parses a user-supplied comma-separated vibration pattern into safe non-repeating timings
         * (capped at 8 segments and <= 600ms per pulse so it never causes continuous/excessive vibration).
         */
        fun parseSafeCustomPattern(csv: String?): LongArray {
            if (csv.isNullOrBlank()) return KmVibrationPattern.CUSTOM.defaultTimingsMs.clone()
            val parts = csv.split(',', ';', ' ')
                .mapNotNull { it.trim().toLongOrNull() }
                .take(8)
            if (parts.isEmpty()) return KmVibrationPattern.CUSTOM.defaultTimingsMs.clone()
            val normalized = mutableListOf<Long>()
            if (parts.first() > 0L) {
                normalized.add(0L)
            }
            parts.forEachIndexed { idx, raw ->
                val isDelay = (normalized.size % 2 == 0)
                val clamped = if (isDelay) {
                    raw.coerceIn(0L, 400L)
                } else {
                    raw.coerceIn(30L, 600L)
                }
                normalized.add(clamped)
            }
            if (normalized.size < 2) {
                normalized.add(140L)
            }
            return normalized.toLongArray()
        }

        fun fromJsonString(raw: String?): KmStoneAlertSettings {
            if (raw.isNullOrBlank()) return KmStoneAlertSettings()
            return try {
                val obj = JSONObject(raw)
                KmStoneAlertSettings(
                    enabled = obj.optBoolean("enabled", true),
                    backgroundMonitoringEnabled = obj.optBoolean("backgroundMonitoringEnabled", true),
                    persistentNotificationEnabled = obj.optBoolean("persistentNotificationEnabled", true),
                    lockScreenNotificationsEnabled = obj.optBoolean("lockScreenNotificationsEnabled", true),
                    arrivalAlertsEnabled = obj.optBoolean("arrivalAlertsEnabled", true),
                    hapticFeedbackEnabled = obj.optBoolean("hapticFeedbackEnabled", true),
                    vibrationEnabled = obj.optBoolean("vibrationEnabled", true),
                    soundEnabled = obj.optBoolean("soundEnabled", true),
                    vibrationPattern = KmVibrationPattern.fromString(obj.optString("vibrationPattern", "DOUBLE_PULSE")),
                    vibrationIntensity = KmVibrationIntensity.fromString(obj.optString("vibrationIntensity", "HIGH")),
                    customVibrationPatternCsv = obj.optString("customVibrationPatternCsv", "0,140,90,200"),
                    soundOption = KmAlertSoundOption.fromString(obj.optString("soundOption", "DIGITAL_CHIME")),
                    customSoundUri = obj.optString("customSoundUri", ""),
                    customSoundName = obj.optString("customSoundName", ""),
                    alertVolumePercent = obj.optInt("alertVolumePercent", 85).coerceIn(0, 100),
                    proximityThresholdMetres = obj.optInt("proximityThresholdMetres", 15).coerceIn(5, 100),
                    maxGpsAccuracyMetres = obj.optInt("maxGpsAccuracyMetres", 25).coerceIn(5, 100),
                    repeatAlertPreventionEnabled = obj.optBoolean("repeatAlertPreventionEnabled", true),
                    repeatAlertDistanceMetres = obj.optInt("repeatAlertDistanceMetres", 60).coerceIn(25, 300),
                    advanceWarningOption = KmAdvanceWarningOption.fromString(obj.optString("advanceWarningOption", "DISABLED")),
                    customAdvanceWarningMetres = obj.optInt("customAdvanceWarningMetres", 150).coerceIn(25, 2000),
                    advanceWarningVibrationEnabled = obj.optBoolean("advanceWarningVibrationEnabled", true),
                    advanceWarningSoundEnabled = obj.optBoolean("advanceWarningSoundEnabled", true),
                    advanceWarningVibrationPattern = KmVibrationPattern.fromString(
                        obj.optString("advanceWarningVibrationPattern", "SHORT_PULSE")
                    ),
                    advanceWarningSoundOption = KmAlertSoundOption.fromString(
                        obj.optString("advanceWarningSoundOption", "SOFT_ALERT")
                    )
                )
            } catch (_: Exception) {
                KmStoneAlertSettings()
            }
        }
    }
}
