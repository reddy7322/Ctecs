package com.example.navigation

import android.content.Context
import com.example.model.Chainage
import com.example.model.DirectionDetectionState
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVoiceAnnouncementStyle
import com.example.model.KmVoiceSpeed
import com.example.model.SmartDirectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class KmStoneVoiceOption(
    val name: String,
    val displayLabel: String,
    val languageTag: String
)

data class KmStoneLanguageOption(
    val languageTag: String,
    val displayLabel: String
)

data class KmStoneVoiceDispatchRecord(
    val spokenText: String,
    val isArrival: Boolean,
    val isPreview: Boolean,
    val style: KmVoiceAnnouncementStyle,
    val speed: KmVoiceSpeed,
    val volumePercent: Int,
    val repeatQueued: Boolean,
    val languageTag: String,
    val voiceName: String,
    val dispatchedToTts: Boolean,
    val timestampMs: Long
)

/**
 * Pure, deterministic spoken-script formatter for all 6 KM Stone voice announcement styles.
 * Never touches live GPS, calibration, or monitoring detector state.
 */
object KmStoneVoiceScriptFormatter {

    /**
     * Converts a chainage string such as "198+000" or "CH 198+500" into natural TTS speech
     * ("198 plus 000", "198 plus 500").
     */
    fun formatSpokenChainage(rawChainage: String): String {
        val cleaned = rawChainage
            .trim()
            .removePrefix("CH")
            .removePrefix("ch")
            .removePrefix("KM")
            .removePrefix("km")
            .trim()
        if (cleaned.isEmpty()) return "zero plus 000"
        return if (cleaned.contains('+')) {
            val parts = cleaned.split('+', limit = 2)
            val kmPart = parts[0].trim().ifEmpty { "0" }
            val mPart = parts.getOrElse(1) { "000" }.trim().ifEmpty { "000" }
            "$kmPart plus $mPart"
        } else {
            cleaned
        }
    }

    /**
     * Formats a road-alignment distance in metres into clear spoken navigation units.
     */
    fun formatSpokenDistance(metres: Long): String {
        val safe = metres.coerceAtLeast(0L)
        return when {
            safe == 1000L -> "1 kilometre"
            safe > 1000L && safe % 1000L == 0L -> "${safe / 1000L} kilometres"
            safe == 1L -> "1 metre"
            else -> "$safe metres"
        }
    }

    /**
     * Builds the final TTS announcement string for [event] using the selected [settings].
     */
    fun buildAnnouncementText(
        event: KmStoneAlertEvent,
        settings: KmStoneAlertSettings
    ): String {
        val isArrival = event.kind == KmStoneAlertKind.ARRIVAL
        val effectiveDistance = (event.announcedIntervalMetres?.toLong() ?: event.distanceMetres).coerceAtLeast(0L)
        val stoneSpoken = formatSpokenChainage(event.stoneFormattedChainage)
        val currentSpoken = formatSpokenChainage(event.currentFormattedChainage)
        val side = event.directionSide.trim().uppercase(Locale.ROOT)
            .takeIf { it == "LHS" || it == "RHS" }
            .orEmpty()
        val distSpoken = formatSpokenDistance(effectiveDistance)

        return when (settings.voiceStyle) {
            KmVoiceAnnouncementStyle.SIMPLE -> {
                when {
                    isArrival -> "Kilometre stone reached."
                    effectiveDistance > 0L -> "Next KM Stone in $distSpoken."
                    else -> "Next kilometre stone ahead."
                }
            }

            KmVoiceAnnouncementStyle.CHAINAGE -> {
                when {
                    isArrival -> "KM Stone reached, Chainage $stoneSpoken."
                    effectiveDistance > 0L -> "$distSpoken remaining to Chainage $stoneSpoken."
                    else -> "Next KM Stone, Chainage $stoneSpoken."
                }
            }

            KmVoiceAnnouncementStyle.DIRECTION -> {
                val sidePrefix = if (side.isNotEmpty()) "$side, " else ""
                when {
                    isArrival -> "${sidePrefix}kilometre stone reached."
                    effectiveDistance > 0L -> "${sidePrefix}next kilometre stone in $distSpoken."
                    else -> "${sidePrefix}next kilometre stone ahead."
                }
            }

            KmVoiceAnnouncementStyle.DETAILED_NAVIGATION -> {
                val dirSentence = if (side.isNotEmpty()) " Direction $side." else ""
                if (isArrival) {
                    "Arrived at KM Stone $stoneSpoken. Current chainage $currentSpoken.$dirSentence"
                } else {
                    "You are approaching KM Stone $stoneSpoken. Current chainage $currentSpoken. Distance remaining $distSpoken.$dirSentence"
                }
            }

            KmVoiceAnnouncementStyle.SHORT_ALERT -> {
                if (isArrival) {
                    "KM Stone reached, $stoneSpoken."
                } else {
                    "Next KM Stone, $distSpoken."
                }
            }

            KmVoiceAnnouncementStyle.CUSTOM -> {
                buildCustomAnnouncementText(
                    isArrival = isArrival,
                    stoneSpoken = stoneSpoken,
                    currentSpoken = currentSpoken,
                    side = side,
                    distSpoken = distSpoken,
                    speedKmh = event.speedKmh,
                    settings = settings
                )
            }
        }
    }

    private fun buildCustomAnnouncementText(
        isArrival: Boolean,
        stoneSpoken: String,
        currentSpoken: String,
        side: String,
        distSpoken: String,
        speedKmh: Int?,
        settings: KmStoneAlertSettings
    ): String {
        val template = settings.voiceCustomTemplate.trim()
        if (template.isNotEmpty()) {
            val statusText = if (isArrival) "KM Stone reached" else "Approaching KM Stone"
            val speedText = if (speedKmh != null) "$speedKmh kilometres per hour" else "speed unavailable"
            val rendered = template
                .replace("{stone}", stoneSpoken, ignoreCase = true)
                .replace("{chainage}", currentSpoken, ignoreCase = true)
                .replace("{side}", side.ifEmpty { "LHS" }, ignoreCase = true)
                .replace("{distance}", if (isArrival) "0 metres" else distSpoken, ignoreCase = true)
                .replace("{speed}", speedText, ignoreCase = true)
                .replace("{status}", statusText, ignoreCase = true)
                .replace(Regex("\\s+"), " ")
                .trim()
            return if (rendered.endsWith(".") || rendered.endsWith("!") || rendered.endsWith("?")) {
                rendered
            } else {
                "$rendered."
            }
        }

        val parts = mutableListOf<String>()
        if (settings.voiceCustomIncludeArrivalStatus) {
            parts.add(if (isArrival) "KM Stone reached" else "Approaching KM Stone")
        }
        if (settings.voiceCustomIncludeChainage) {
            parts.add("Chainage $stoneSpoken")
        }
        if (settings.voiceCustomIncludeDistance && !isArrival) {
            parts.add("$distSpoken remaining")
        }
        if (settings.voiceCustomIncludeDirectionSide && side.isNotEmpty()) {
            parts.add("Direction $side")
        }
        if (settings.voiceCustomIncludeSpeed && speedKmh != null) {
            parts.add("Speed $speedKmh kilometres per hour")
        }

        if (parts.isEmpty()) {
            return if (isArrival) {
                "KM Stone $stoneSpoken reached."
            } else {
                "Next KM Stone $stoneSpoken."
            }
        }
        return parts.joinToString(separator = ". ", postfix = ".")
    }

    /**
     * Generates a strictly isolated sample event for Settings preview and voice testing.
     * Never writes to [CtecsNavigationTelemetryHub] or modifies detector state.
     */
    fun buildSamplePreviewEvent(
        isArrival: Boolean,
        intervalMetres: Int = 500,
        directionSide: String = "LHS"
    ): KmStoneAlertEvent {
        val stoneM = 198_000L
        val currentM = if (isArrival) stoneM else (stoneM - intervalMetres.coerceAtLeast(50)).coerceAtLeast(0L)
        return KmStoneAlertEvent(
            kind = if (isArrival) KmStoneAlertKind.ARRIVAL else KmStoneAlertKind.VOICE_DISTANCE,
            stoneChainageMetres = stoneM,
            stoneFormattedChainage = Chainage.fromMetres(stoneM).format(),
            currentChainageMetres = currentM,
            currentFormattedChainage = Chainage.fromMetres(currentM).format(),
            directionSide = directionSide,
            distanceMetres = if (isArrival) 0L else intervalMetres.toLong(),
            routeId = "voice_preview_sample",
            timestampMs = System.currentTimeMillis(),
            announcedIntervalMetres = if (isArrival) null else intervalMetres,
            speedKmh = 62
        )
    }

    /**
     * Builds natural TTS copy for a confirmed LHS/RHS direction change.
     */
    fun buildDirectionChangeText(newConfirmedSide: String): String {
        val clean = newConfirmedSide.trim().uppercase(Locale.ROOT)
        return when (clean) {
            "LHS" -> "Direction changed to LHS."
            "RHS" -> "Direction changed to RHS."
            else -> "Travel direction updated."
        }
    }

    /**
     * Builds natural TTS copy describing the current authoritative travel direction status.
     */
    fun buildDirectionStatusText(directionState: SmartDirectionState): String {
        val side = directionState.confirmedBinarySide
        return when {
            directionState.detectionState == DirectionDetectionState.LHS_CONFIRMED || side == "LHS" ->
                "You are travelling on LHS."
            directionState.detectionState == DirectionDetectionState.RHS_CONFIRMED || side == "RHS" ->
                "You are travelling on RHS."
            directionState.detectionState == DirectionDetectionState.GPS_SIGNAL_WEAK ->
                "GPS signal weak. Checking road alignment."
            directionState.detectionState == DirectionDetectionState.ALIGNMENT_UNAVAILABLE ->
                "Road alignment unavailable."
            else ->
                "Travel direction uncertain. Checking road alignment."
        }
    }
}

/**
 * Process-wide coordinator for Smart Next KM Stone Dynamic Voice Announcements and
 * Navigate-to-Chainage voice prompts.
 *
 * Delegates all Android [android.speech.tts.TextToSpeech] engine detection, initialization,
 * priority queueing, audio focus, language/voice validation, and disconnect recovery to
 * CTECS's single centralized [SmartTtsManager] (strictly one shared TTS instance app-wide).
 */
object KmStoneVoiceAnnouncementManager {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    @Volatile
    private var lastAnnouncedDirectionSide: String? = null

    @Volatile
    private var lastDirectionAnnouncementMs: Long = 0L

    private const val DIRECTION_CHANGE_COOLDOWN_MS = 10_000L

    val ttsStatus: StateFlow<TtsStatusSnapshot> = SmartTtsManager.status

    val ttsReady: StateFlow<Boolean> = SmartTtsManager.status
        .map { it.isReady }
        .stateIn(managerScope, SharingStarted.Eagerly, SmartTtsManager.status.value.isReady)

    val ttsAvailable: StateFlow<Boolean> = SmartTtsManager.status
        .map { it.isAvailableOrIdle }
        .stateIn(managerScope, SharingStarted.Eagerly, SmartTtsManager.status.value.isAvailableOrIdle)

    val availableLanguages: StateFlow<List<KmStoneLanguageOption>> = SmartTtsManager.status
        .map { it.availableLanguages }
        .stateIn(managerScope, SharingStarted.Eagerly, SmartTtsManager.status.value.availableLanguages)

    val availableVoices: StateFlow<List<KmStoneVoiceOption>> = SmartTtsManager.status
        .map { it.availableVoices }
        .stateIn(managerScope, SharingStarted.Eagerly, SmartTtsManager.status.value.availableVoices)

    private val _lastVoiceRecord = MutableStateFlow<KmStoneVoiceDispatchRecord?>(null)
    val lastVoiceRecord: StateFlow<KmStoneVoiceDispatchRecord?> = _lastVoiceRecord.asStateFlow()

    /**
     * Ensures the shared Android TTS engine is detected and initialized lazily without
     * blocking the main UI thread.
     */
    fun ensureInitialized(
        context: Context,
        preferredEnginePackage: String = "",
        languageTag: String = "",
        forceReinitialize: Boolean = false
    ) {
        SmartTtsManager.initializeIfNeeded(
            context = context,
            preferredEngine = preferredEnginePackage,
            languageTag = languageTag,
            forceReinitialize = forceReinitialize
        )
    }

    /**
     * Returns installed voices filtered optionally by [languageTag] (empty = all voices).
     */
    fun getVoicesForLanguage(languageTag: String): List<KmStoneVoiceOption> {
        return SmartTtsManager.getVoicesForLanguage(languageTag)
    }

    /**
     * Speaks a live KM Stone arrival or dynamic distance announcement.
     * Respects silent/DND, active recording, output mode, priority, and non-overlap rules.
     */
    fun announceEvent(
        context: Context,
        event: KmStoneAlertEvent,
        settings: KmStoneAlertSettings,
        isRecordingActive: Boolean = false,
        initialDelayMs: Long = 0L
    ): KmStoneVoiceDispatchRecord? {
        if (!settings.enabled || !settings.voiceAnnouncementEnabled) return null
        if (!settings.alertOutputMode.includesVoice) return null
        if (isRecordingActive) return null
        if (!KmStoneAlertFeedbackManager.canPlayAudibleAlert(context)) return null

        val isArrival = event.kind == KmStoneAlertKind.ARRIVAL
        if (isArrival && !settings.voiceAnnounceAtArrival) return null

        val spokenText = KmStoneVoiceScriptFormatter.buildAnnouncementText(event, settings)
        val outcome = SmartTtsManager.speak(
            context = context,
            request = TtsSpeakRequest(
                text = spokenText,
                languageTag = settings.voiceLanguageTag,
                voiceName = settings.voiceName,
                enginePackage = settings.voiceEnginePackage,
                speechRate = settings.voiceSpeed.speechRate,
                pitch = settings.voicePitch,
                volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
                priority = if (isArrival) TtsSpeechPriority.CRITICAL_ARRIVAL else TtsSpeechPriority.ROUTINE,
                repeatOnce = settings.voiceRepeatEnabled
            ),
            initialDelayMs = initialDelayMs
        )
        val record = KmStoneVoiceDispatchRecord(
            spokenText = spokenText,
            isArrival = isArrival,
            isPreview = false,
            style = settings.voiceStyle,
            speed = settings.voiceSpeed,
            volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
            repeatQueued = settings.voiceRepeatEnabled,
            languageTag = settings.voiceLanguageTag,
            voiceName = settings.voiceName,
            dispatchedToTts = outcome.dispatched,
            timestampMs = event.timestampMs
        )
        _lastVoiceRecord.value = record
        return record
    }

    /**
     * Plays an isolated sample preview for Settings (Style, Language, Voice, Speed, Volume).
     * Strictly uses sample data and NEVER triggers actual arrival events or modifies detector state.
     */
    fun previewAnnouncementStyle(
        context: Context,
        settings: KmStoneAlertSettings,
        isArrival: Boolean = false,
        sampleIntervalMetres: Int = 500
    ): KmStoneVoiceDispatchRecord {
        val sampleEvent = KmStoneVoiceScriptFormatter.buildSamplePreviewEvent(
            isArrival = isArrival,
            intervalMetres = sampleIntervalMetres
        )
        val spokenText = KmStoneVoiceScriptFormatter.buildAnnouncementText(sampleEvent, settings)
        val outcome = SmartTtsManager.speak(
            context = context,
            request = TtsSpeakRequest(
                text = spokenText,
                languageTag = settings.voiceLanguageTag,
                voiceName = settings.voiceName,
                enginePackage = settings.voiceEnginePackage,
                speechRate = settings.voiceSpeed.speechRate,
                pitch = settings.voicePitch,
                volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
                priority = TtsSpeechPriority.USER_INTERACTIVE,
                repeatOnce = false
            )
        )
        val record = KmStoneVoiceDispatchRecord(
            spokenText = spokenText,
            isArrival = isArrival,
            isPreview = true,
            style = settings.voiceStyle,
            speed = settings.voiceSpeed,
            volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
            repeatQueued = false,
            languageTag = settings.voiceLanguageTag,
            voiceName = settings.voiceName,
            dispatchedToTts = outcome.dispatched,
            timestampMs = System.currentTimeMillis()
        )
        _lastVoiceRecord.value = record
        return record
    }

    /**
     * Plays a sample preview of the user's Custom Announcement (Style 6) configuration before saving.
     */
    fun previewCustomAnnouncement(
        context: Context,
        settings: KmStoneAlertSettings,
        isArrival: Boolean = false
    ): KmStoneVoiceDispatchRecord {
        return previewAnnouncementStyle(
            context = context,
            settings = settings.copy(voiceStyle = KmVoiceAnnouncementStyle.CUSTOM),
            isArrival = isArrival,
            sampleIntervalMetres = 500
        )
    }

    /**
     * Speaks a Navigate-to-Chainage Smart Voice Assistant confirmation or feedback prompt using
     * CTECS's single shared [SmartTtsManager] instance.
     */
    fun speakCustomAssistantText(
        context: Context,
        spokenText: String,
        settings: KmStoneAlertSettings
    ): KmStoneVoiceDispatchRecord {
        val cleanText = spokenText.trim()
        val outcome = if (cleanText.isNotEmpty()) {
            SmartTtsManager.speak(
                context = context,
                request = TtsSpeakRequest(
                    text = cleanText,
                    languageTag = settings.voiceLanguageTag,
                    voiceName = settings.voiceName,
                    enginePackage = settings.voiceEnginePackage,
                    speechRate = settings.voiceSpeed.speechRate,
                    pitch = settings.voicePitch,
                    volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
                    priority = TtsSpeechPriority.USER_INTERACTIVE,
                    repeatOnce = false
                )
            )
        } else {
            TtsSpeakOutcome(
                dispatched = false,
                status = SmartTtsManager.status.value.status,
                message = "Nothing to speak."
            )
        }
        val record = KmStoneVoiceDispatchRecord(
            spokenText = cleanText,
            isArrival = false,
            isPreview = true,
            style = settings.voiceStyle,
            speed = settings.voiceSpeed,
            volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
            repeatQueued = false,
            languageTag = settings.voiceLanguageTag,
            voiceName = settings.voiceName,
            dispatchedToTts = outcome.dispatched,
            timestampMs = System.currentTimeMillis()
        )
        _lastVoiceRecord.value = record
        return record
    }

    /**
     * Tests the KM Stone arrival voice announcement using sample values without affecting live monitoring.
     */
    fun testArrivalVoiceAnnouncement(
        context: Context,
        settings: KmStoneAlertSettings
    ): KmStoneVoiceDispatchRecord {
        return previewAnnouncementStyle(
            context = context,
            settings = settings,
            isArrival = true,
            sampleIntervalMetres = 0
        )
    }

    fun stopSpeaking() {
        SmartTtsManager.stopSpeaking()
    }

    /**
     * Speaks a confirmed LHS/RHS direction change using the single shared Android TTS engine.
     */
    fun announceDirectionChange(
        context: Context,
        directionState: SmartDirectionState,
        settings: KmStoneAlertSettings,
        voiceDirectionEnabled: Boolean = true,
        isRecordingActive: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): KmStoneVoiceDispatchRecord? {
        if (!voiceDirectionEnabled) return null
        if (!settings.enabled || !settings.voiceAnnouncementEnabled) return null
        if (!settings.alertOutputMode.includesVoice) return null
        if (isRecordingActive) return null
        if (!directionState.detectionState.isConfirmed) return null
        val newSide = directionState.confirmedBinarySide ?: return null
        if (directionState.directionChangedFrom == null) return null
        if (directionState.isManualOverride) return null
        if (!KmStoneAlertFeedbackManager.canPlayAudibleAlert(context)) return null

        synchronized(lock) {
            if (lastAnnouncedDirectionSide == newSide &&
                (nowMs - lastDirectionAnnouncementMs) < DIRECTION_CHANGE_COOLDOWN_MS
            ) {
                return null
            }
            lastAnnouncedDirectionSide = newSide
            lastDirectionAnnouncementMs = nowMs
        }

        val spokenText = KmStoneVoiceScriptFormatter.buildDirectionChangeText(newSide)
        val outcome = SmartTtsManager.speak(
            context = context,
            request = TtsSpeakRequest(
                text = spokenText,
                languageTag = settings.voiceLanguageTag,
                voiceName = settings.voiceName,
                enginePackage = settings.voiceEnginePackage,
                speechRate = settings.voiceSpeed.speechRate,
                pitch = settings.voicePitch,
                volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
                priority = TtsSpeechPriority.ROUTINE,
                repeatOnce = false
            )
        )
        val record = KmStoneVoiceDispatchRecord(
            spokenText = spokenText,
            isArrival = false,
            isPreview = false,
            style = settings.voiceStyle,
            speed = settings.voiceSpeed,
            volumePercent = settings.voiceVolumePercent.coerceIn(0, 100),
            repeatQueued = false,
            languageTag = settings.voiceLanguageTag,
            voiceName = settings.voiceName,
            dispatchedToTts = outcome.dispatched,
            timestampMs = nowMs
        )
        _lastVoiceRecord.value = record
        return record
    }

    /**
     * Speaks the current authoritative travel direction status on explicit user/assistant query.
     */
    fun announceDirectionStatus(
        context: Context,
        directionState: SmartDirectionState,
        settings: KmStoneAlertSettings,
        voiceEnabled: Boolean = true
    ): KmStoneVoiceDispatchRecord? {
        if (!voiceEnabled) return null
        val spokenText = KmStoneVoiceScriptFormatter.buildDirectionStatusText(directionState)
        return speakCustomAssistantText(
            context = context,
            spokenText = spokenText,
            settings = settings
        )
    }

    fun resetTestState() {
        SmartTtsManager.resetForTesting()
        synchronized(lock) {
            lastAnnouncedDirectionSide = null
            lastDirectionAnnouncementMs = 0L
        }
        _lastVoiceRecord.value = null
    }
}
