package com.example.model

import org.json.JSONObject

/**
 * Supported speech recognition and TTS language options for Chainage Voice Assistant.
 */
enum class ChainageVoiceLanguage(
    val label: String,
    val languageTag: String,
    val subtitle: String
) {
    SYSTEM("System Default", "", "Uses device speech & TTS language"),
    ENGLISH_INDIA("English (India)", "en-IN", "Recommended for Indian highway chainages"),
    ENGLISH_US("English", "en-US", "Standard English speech recognition"),
    HINDI("Hindi (हिन्दी)", "hi-IN", "Where supported by device speech engine"),
    TELUGU("Telugu (తెలుగు)", "te-IN", "Where supported by device speech engine");

    companion object {
        fun fromString(raw: String?): ChainageVoiceLanguage =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    it.label.equals(raw, ignoreCase = true) ||
                    (it.languageTag.isNotEmpty() && it.languageTag.equals(raw, ignoreCase = true))
            } ?: SYSTEM
    }
}

/**
 * Persistent preferences for Settings -> Navigation -> Chainage Voice Assistant.
 * Stored globally via DataStore PreferencesManager and included in SettingsBackupManager.
 */
data class ChainageVoiceAssistantSettings(
    val voiceAssistantEnabled: Boolean = true,
    val voiceSearchEnabled: Boolean = true,
    val voiceLanguage: ChainageVoiceLanguage = ChainageVoiceLanguage.SYSTEM,
    val voiceOutputEnabled: Boolean = true,
    val ttsVoiceName: String = "",
    val speechSpeed: KmVoiceSpeed = KmVoiceSpeed.NORMAL,
    val volumePercent: Int = 90,
    val voiceConfirmationBeforeNav: Boolean = true,
    val autoListenOnPopupOpen: Boolean = false
) {
    fun effectiveVoiceSearchActive(): Boolean = voiceAssistantEnabled && voiceSearchEnabled

    fun effectiveVoiceOutputActive(): Boolean = voiceAssistantEnabled && voiceOutputEnabled

    fun toJsonString(): String {
        val obj = JSONObject().apply {
            put("voiceAssistantEnabled", voiceAssistantEnabled)
            put("voiceSearchEnabled", voiceSearchEnabled)
            put("voiceLanguage", voiceLanguage.name)
            put("voiceOutputEnabled", voiceOutputEnabled)
            put("ttsVoiceName", ttsVoiceName)
            put("speechSpeed", speechSpeed.name)
            put("volumePercent", volumePercent.coerceIn(0, 100))
            put("voiceConfirmationBeforeNav", voiceConfirmationBeforeNav)
            put("autoListenOnPopupOpen", autoListenOnPopupOpen)
        }
        return obj.toString()
    }

    companion object {
        fun defaultSettings(): ChainageVoiceAssistantSettings = ChainageVoiceAssistantSettings()

        fun fromJsonString(raw: String?): ChainageVoiceAssistantSettings {
            if (raw.isNullOrBlank()) return ChainageVoiceAssistantSettings()
            return try {
                val obj = JSONObject(raw)
                ChainageVoiceAssistantSettings(
                    voiceAssistantEnabled = obj.optBoolean("voiceAssistantEnabled", true),
                    voiceSearchEnabled = obj.optBoolean("voiceSearchEnabled", true),
                    voiceLanguage = ChainageVoiceLanguage.fromString(obj.optString("voiceLanguage", "SYSTEM")),
                    voiceOutputEnabled = obj.optBoolean("voiceOutputEnabled", true),
                    ttsVoiceName = obj.optString("ttsVoiceName", ""),
                    speechSpeed = KmVoiceSpeed.fromString(obj.optString("speechSpeed", "NORMAL")),
                    volumePercent = obj.optInt("volumePercent", 90).coerceIn(0, 100),
                    voiceConfirmationBeforeNav = obj.optBoolean("voiceConfirmationBeforeNav", true),
                    autoListenOnPopupOpen = obj.optBoolean("autoListenOnPopupOpen", false)
                )
            } catch (_: Exception) {
                ChainageVoiceAssistantSettings()
            }
        }
    }
}
