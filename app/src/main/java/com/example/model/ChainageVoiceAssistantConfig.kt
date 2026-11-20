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
    val customLanguageTag: String = "",
    val voiceOutputEnabled: Boolean = true,
    val ttsEnginePackage: String = "",
    val ttsVoiceName: String = "",
    val speechSpeed: KmVoiceSpeed = KmVoiceSpeed.NORMAL,
    val speechPitch: Float = 1.0f,
    val volumePercent: Int = 90,
    val voiceConfirmationBeforeNav: Boolean = true,
    val autoListenOnPopupOpen: Boolean = false
) {
    fun effectiveVoiceSearchActive(): Boolean = voiceAssistantEnabled && voiceSearchEnabled

    fun effectiveVoiceOutputActive(): Boolean = voiceAssistantEnabled && voiceOutputEnabled

    /**
     * Returns the BCP-47 language tag to request from Android TTS & SpeechRecognizer.
     * Supports preset languages (English, Hindi, Telugu) as well as any dynamically discovered
     * language installed on the active TTS engine via [customLanguageTag].
     */
    fun effectiveLanguageTag(): String = customLanguageTag.trim().ifBlank { voiceLanguage.languageTag }

    fun effectiveSpeechPitch(): Float = speechPitch.coerceIn(0.6f, 1.5f)

    fun toJsonString(): String {
        val obj = JSONObject().apply {
            put("voiceAssistantEnabled", voiceAssistantEnabled)
            put("voiceSearchEnabled", voiceSearchEnabled)
            put("voiceLanguage", voiceLanguage.name)
            put("customLanguageTag", customLanguageTag)
            put("voiceOutputEnabled", voiceOutputEnabled)
            put("ttsEnginePackage", ttsEnginePackage)
            put("ttsVoiceName", ttsVoiceName)
            put("speechSpeed", speechSpeed.name)
            put("speechPitch", effectiveSpeechPitch().toDouble())
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
                    customLanguageTag = obj.optString("customLanguageTag", "").trim(),
                    voiceOutputEnabled = obj.optBoolean("voiceOutputEnabled", true),
                    ttsEnginePackage = obj.optString("ttsEnginePackage", "").trim(),
                    ttsVoiceName = obj.optString("ttsVoiceName", ""),
                    speechSpeed = KmVoiceSpeed.fromString(obj.optString("speechSpeed", "NORMAL")),
                    speechPitch = obj.optDouble("speechPitch", 1.0).toFloat().coerceIn(0.6f, 1.5f),
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
