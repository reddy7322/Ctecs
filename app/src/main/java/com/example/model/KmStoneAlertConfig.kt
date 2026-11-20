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

enum class KmVoiceAnnouncementStyle(val label: String, val description: String) {
    SIMPLE("Simple", "Next kilometre stone ahead / in 500 metres"),
    CHAINAGE("Chainage Announcement", "Includes upcoming KM Stone chainage"),
    DIRECTION("Direction Announcement", "Includes LHS / RHS travel direction"),
    DETAILED_NAVIGATION("Detailed Navigation", "Upcoming stone, current chainage, distance & side"),
    SHORT_ALERT("Short Voice Alert", "Compact distance alert for fast scanning"),
    CUSTOM("Custom Announcement", "Choose fields or build your own announcement format");

    companion object {
        fun fromString(raw: String?): KmVoiceAnnouncementStyle =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: CHAINAGE
    }
}

enum class KmVoiceSpeed(val label: String, val speechRate: Float) {
    SLOW("Slow", 0.82f),
    NORMAL("Normal", 1.0f),
    FAST("Fast", 1.25f);

    companion object {
        fun fromString(raw: String?): KmVoiceSpeed =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: NORMAL
    }
}

enum class KmAlertOutputMode(
    val label: String,
    val includesVoice: Boolean,
    val includesSound: Boolean,
    val includesVibration: Boolean
) {
    VOICE_ONLY("Voice only", includesVoice = true, includesSound = false, includesVibration = false),
    SOUND_ONLY("Sound only", includesVoice = false, includesSound = true, includesVibration = false),
    VOICE_AND_SOUND("Voice + sound", includesVoice = true, includesSound = true, includesVibration = false),
    VIBRATION_AND_VOICE("Vibration + voice", includesVoice = true, includesSound = false, includesVibration = true),
    VOICE_SOUND_AND_VIBRATION("Voice + sound + vibration", includesVoice = true, includesSound = true, includesVibration = true);

    companion object {
        fun fromString(raw: String?): KmAlertOutputMode =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: VOICE_SOUND_AND_VIBRATION
    }
}

/**
 * Persistent settings for Settings -> Navigation Alerts -> KM Stone Arrival.
 * Supports independent arrival vs. advance warning feedback, vibration patterns & intensity,
 * sound selection & volume, arrival detection thresholds, and dynamic TTS voice announcements.
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
    val advanceWarningSoundOption: KmAlertSoundOption = KmAlertSoundOption.SOFT_ALERT,
    // Smart Next KM Stone Dynamic Voice Announcements (Android TTS)
    val voiceAnnouncementEnabled: Boolean = false,
    val voiceName: String = "",
    val voiceLanguageTag: String = "",
    val voiceStyle: KmVoiceAnnouncementStyle = KmVoiceAnnouncementStyle.CHAINAGE,
    val voiceVolumePercent: Int = 90,
    val voiceSpeed: KmVoiceSpeed = KmVoiceSpeed.NORMAL,
    val voiceRepeatEnabled: Boolean = false,
    val alertOutputMode: KmAlertOutputMode = KmAlertOutputMode.VOICE_SOUND_AND_VIBRATION,
    // Configurable Dynamic Distance Intervals (multi-select)
    val voiceInterval1000m: Boolean = false,
    val voiceInterval500m: Boolean = true,
    val voiceInterval200m: Boolean = true,
    val voiceInterval100m: Boolean = true,
    val voiceInterval50m: Boolean = false,
    val voiceAnnounceAtArrival: Boolean = true,
    // Style 6 – Custom Announcement Fields & Optional Template
    val voiceCustomIncludeChainage: Boolean = true,
    val voiceCustomIncludeDirectionSide: Boolean = true,
    val voiceCustomIncludeDistance: Boolean = true,
    val voiceCustomIncludeSpeed: Boolean = false,
    val voiceCustomIncludeArrivalStatus: Boolean = true,
    val voiceCustomTemplate: String = "",
    val voiceEnginePackage: String = "",
    val voicePitch: Float = 1.0f
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

    fun isVoiceActiveForLiveAlerts(): Boolean {
        return enabled && voiceAnnouncementEnabled && alertOutputMode.includesVoice
    }

    fun resolveEffectiveVibrationEnabled(): Boolean {
        return if (voiceAnnouncementEnabled || alertOutputMode != KmAlertOutputMode.VOICE_SOUND_AND_VIBRATION) {
            vibrationEnabled && alertOutputMode.includesVibration
        } else {
            vibrationEnabled
        }
    }

    fun resolveEffectiveSoundEnabled(): Boolean {
        return if (voiceAnnouncementEnabled || alertOutputMode != KmAlertOutputMode.VOICE_SOUND_AND_VIBRATION) {
            soundEnabled && alertOutputMode.includesSound
        } else {
            soundEnabled
        }
    }

    fun enabledVoiceIntervalsDescending(): List<Int> {
        val list = mutableListOf<Int>()
        if (voiceInterval1000m) list.add(1000)
        if (voiceInterval500m) list.add(500)
        if (voiceInterval200m) list.add(200)
        if (voiceInterval100m) list.add(100)
        if (voiceInterval50m) list.add(50)
        return list
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
            // Voice announcement settings
            put("voiceAnnouncementEnabled", voiceAnnouncementEnabled)
            put("voiceName", voiceName)
            put("voiceLanguageTag", voiceLanguageTag)
            put("voiceStyle", voiceStyle.name)
            put("voiceVolumePercent", voiceVolumePercent.coerceIn(0, 100))
            put("voiceSpeed", voiceSpeed.name)
            put("voiceRepeatEnabled", voiceRepeatEnabled)
            put("alertOutputMode", alertOutputMode.name)
            put("voiceInterval1000m", voiceInterval1000m)
            put("voiceInterval500m", voiceInterval500m)
            put("voiceInterval200m", voiceInterval200m)
            put("voiceInterval100m", voiceInterval100m)
            put("voiceInterval50m", voiceInterval50m)
            put("voiceAnnounceAtArrival", voiceAnnounceAtArrival)
            put("voiceCustomIncludeChainage", voiceCustomIncludeChainage)
            put("voiceCustomIncludeDirectionSide", voiceCustomIncludeDirectionSide)
            put("voiceCustomIncludeDistance", voiceCustomIncludeDistance)
            put("voiceCustomIncludeSpeed", voiceCustomIncludeSpeed)
            put("voiceCustomIncludeArrivalStatus", voiceCustomIncludeArrivalStatus)
            put("voiceCustomTemplate", voiceCustomTemplate)
            put("voiceEnginePackage", voiceEnginePackage)
            put("voicePitch", voicePitch.coerceIn(0.6f, 1.5f).toDouble())
        }
        return obj.toString()
    }

    companion object {
        val PROXIMITY_THRESHOLD_OPTIONS = listOf(10, 15, 20, 25, 35, 50)
        val GPS_ACCURACY_OPTIONS = listOf(15, 20, 25, 35, 50)
        val REPEAT_PREVENTION_DISTANCE_OPTIONS = listOf(40, 60, 80, 100, 150)
        val VOICE_DISTANCE_INTERVAL_OPTIONS = listOf(1000, 500, 200, 100, 50)

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
            parts.forEachIndexed { _, raw ->
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
                    ),
                    voiceAnnouncementEnabled = obj.optBoolean("voiceAnnouncementEnabled", false),
                    voiceName = obj.optString("voiceName", ""),
                    voiceLanguageTag = obj.optString("voiceLanguageTag", ""),
                    voiceStyle = KmVoiceAnnouncementStyle.fromString(obj.optString("voiceStyle", "CHAINAGE")),
                    voiceVolumePercent = obj.optInt("voiceVolumePercent", 90).coerceIn(0, 100),
                    voiceSpeed = KmVoiceSpeed.fromString(obj.optString("voiceSpeed", "NORMAL")),
                    voiceRepeatEnabled = obj.optBoolean("voiceRepeatEnabled", false),
                    alertOutputMode = KmAlertOutputMode.fromString(
                        obj.optString("alertOutputMode", "VOICE_SOUND_AND_VIBRATION")
                    ),
                    voiceInterval1000m = obj.optBoolean("voiceInterval1000m", false),
                    voiceInterval500m = obj.optBoolean("voiceInterval500m", true),
                    voiceInterval200m = obj.optBoolean("voiceInterval200m", true),
                    voiceInterval100m = obj.optBoolean("voiceInterval100m", true),
                    voiceInterval50m = obj.optBoolean("voiceInterval50m", false),
                    voiceAnnounceAtArrival = obj.optBoolean("voiceAnnounceAtArrival", true),
                    voiceCustomIncludeChainage = obj.optBoolean("voiceCustomIncludeChainage", true),
                    voiceCustomIncludeDirectionSide = obj.optBoolean("voiceCustomIncludeDirectionSide", true),
                    voiceCustomIncludeDistance = obj.optBoolean("voiceCustomIncludeDistance", true),
                    voiceCustomIncludeSpeed = obj.optBoolean("voiceCustomIncludeSpeed", false),
                    voiceCustomIncludeArrivalStatus = obj.optBoolean("voiceCustomIncludeArrivalStatus", true),
                    voiceCustomTemplate = obj.optString("voiceCustomTemplate", ""),
                    voiceEnginePackage = obj.optString("voiceEnginePackage", "").trim(),
                    voicePitch = obj.optDouble("voicePitch", 1.0).toFloat().coerceIn(0.6f, 1.5f)
                )
            } catch (_: Exception) {
                KmStoneAlertSettings()
            }
        }
    }
}

