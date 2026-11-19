package com.example.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.model.Chainage
import com.example.model.DirectionDetectionState
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVoiceAnnouncementStyle
import com.example.model.KmVoiceSpeed
import com.example.model.SmartDirectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

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
 * Process-wide Android TextToSpeech coordinator for Smart Next KM Stone Dynamic Voice Announcements.
 *
 * Design guarantees:
 * - Native Android [TextToSpeech] (no prerecorded audio files required).
 * - Lazy initialization with graceful degradation when TTS engine, language, or voice is unavailable.
 * - Transient ducking AudioFocus ([AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK]) requested before
 *   speaking and automatically abandoned via [UtteranceProgressListener] once playback drains.
 * - Priority queue prevents overlapping announcements and prioritizes Arrival announcements over
 *   routine distance callouts.
 * - Preview/test helpers use isolated sample values and NEVER trigger live arrival events.
 */
object KmStoneVoiceAnnouncementManager {

    private data class PendingUtterance(
        val text: String,
        val settings: KmStoneAlertSettings,
        val isArrival: Boolean,
        val isPreview: Boolean,
        val repeatOnce: Boolean
    )

    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var isInitializing: Boolean = false

    @Volatile
    private var ttsUnavailable: Boolean = false

    @Volatile
    private var activeArrivalSpeaking: Boolean = false

    @Volatile
    private var lastAnnouncedDirectionSide: String? = null

    @Volatile
    private var lastDirectionAnnouncementMs: Long = 0L

    private const val DIRECTION_CHANGE_COOLDOWN_MS = 10_000L

    private var appContextRef: Context? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var delayedSpeakJob: Job? = null

    private val activeUtteranceIds = mutableSetOf<String>()
    private val pendingQueue = ArrayDeque<PendingUtterance>()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _ttsAvailable = MutableStateFlow(true)
    val ttsAvailable: StateFlow<Boolean> = _ttsAvailable.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<KmStoneLanguageOption>>(emptyList())
    val availableLanguages: StateFlow<List<KmStoneLanguageOption>> = _availableLanguages.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<KmStoneVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<KmStoneVoiceOption>> = _availableVoices.asStateFlow()

    private val _lastVoiceRecord = MutableStateFlow<KmStoneVoiceDispatchRecord?>(null)
    val lastVoiceRecord: StateFlow<KmStoneVoiceDispatchRecord?> = _lastVoiceRecord.asStateFlow()

    /**
     * Ensures the Android TTS engine is initialized lazily so Settings can populate installed
     * languages and voices without blocking the UI thread.
     */
    fun ensureInitialized(context: Context) {
        val appCtx = context.applicationContext ?: context
        synchronized(lock) {
            appContextRef = appCtx
            if (isInitialized || isInitializing) return
            isInitializing = true
        }

        try {
            val engine = TextToSpeech(appCtx) { status ->
                val succeeded = status == TextToSpeech.SUCCESS
                synchronized(lock) {
                    isInitializing = false
                    isInitialized = succeeded
                    ttsUnavailable = !succeeded
                }
                _ttsReady.value = succeeded
                _ttsAvailable.value = succeeded
                if (succeeded) {
                    attachUtteranceListener()
                    refreshInstalledLanguagesAndVoices()
                    drainPendingUtterances()
                } else {
                    synchronized(lock) {
                        pendingQueue.clear()
                    }
                }
            }
            synchronized(lock) {
                tts = engine
            }
        } catch (_: Throwable) {
            synchronized(lock) {
                isInitializing = false
                isInitialized = false
                ttsUnavailable = true
                pendingQueue.clear()
            }
            _ttsReady.value = false
            _ttsAvailable.value = false
        }
    }

    /**
     * Returns installed voices filtered optionally by [languageTag] (empty = all voices).
     */
    fun getVoicesForLanguage(languageTag: String): List<KmStoneVoiceOption> {
        val all = _availableVoices.value
        if (languageTag.isBlank()) return all
        val prefix = languageTag.substringBefore('-').lowercase(Locale.ROOT)
        val exact = all.filter { it.languageTag.equals(languageTag, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return all.filter { it.languageTag.lowercase(Locale.ROOT).startsWith(prefix) }
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
            dispatchedToTts = !ttsUnavailable,
            timestampMs = event.timestampMs
        )
        _lastVoiceRecord.value = record

        val utterance = PendingUtterance(
            text = spokenText,
            settings = settings,
            isArrival = isArrival,
            isPreview = false,
            repeatOnce = settings.voiceRepeatEnabled
        )

        enqueueOrSpeak(context = context, utterance = utterance, initialDelayMs = initialDelayMs)
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
            dispatchedToTts = !ttsUnavailable,
            timestampMs = System.currentTimeMillis()
        )
        _lastVoiceRecord.value = record

        val utterance = PendingUtterance(
            text = spokenText,
            settings = settings,
            isArrival = true, // Preview flushes any prior preview immediately
            isPreview = true,
            repeatOnce = false
        )
        enqueueOrSpeak(context = context, utterance = utterance, initialDelayMs = 0L)
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
     * CTECS's single shared Android TextToSpeech engine (avoids duplicate TTS instances).
     */
    fun speakCustomAssistantText(
        context: Context,
        spokenText: String,
        settings: KmStoneAlertSettings
    ): KmStoneVoiceDispatchRecord {
        val cleanText = spokenText.trim()
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
            dispatchedToTts = !ttsUnavailable && cleanText.isNotEmpty(),
            timestampMs = System.currentTimeMillis()
        )
        _lastVoiceRecord.value = record
        if (cleanText.isNotEmpty()) {
            val utterance = PendingUtterance(
                text = cleanText,
                settings = settings,
                isArrival = true,
                isPreview = true,
                repeatOnce = false
            )
            enqueueOrSpeak(context = context, utterance = utterance, initialDelayMs = 0L)
        }
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
        delayedSpeakJob?.cancel()
        delayedSpeakJob = null
        synchronized(lock) {
            pendingQueue.clear()
            activeUtteranceIds.clear()
            activeArrivalSpeaking = false
        }
        try {
            tts?.stop()
        } catch (_: Throwable) {}
        abandonAudioFocus()
    }

    /**
     * Speaks a confirmed LHS/RHS direction change using the single shared Android TTS engine.
     * Enforces:
     * - Announces ONLY confirmed direction changes (never uncertain or weak-GPS states).
     * - Prevents repetitive announcements via cooldown and side deduplication.
     * - Gives priority to critical KM Stone arrival announcements (`activeArrivalSpeaking`).
     * - Respects voice settings, silent/DND mode, and active recording.
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
        if (activeArrivalSpeaking) return null
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
            dispatchedToTts = !ttsUnavailable,
            timestampMs = nowMs
        )
        _lastVoiceRecord.value = record
        val utterance = PendingUtterance(
            text = spokenText,
            settings = settings,
            isArrival = false,
            isPreview = false,
            repeatOnce = false
        )
        enqueueOrSpeak(context = context, utterance = utterance, initialDelayMs = 0L)
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
        stopSpeaking()
        synchronized(lock) {
            lastAnnouncedDirectionSide = null
            lastDirectionAnnouncementMs = 0L
        }
        _lastVoiceRecord.value = null
    }

    private fun enqueueOrSpeak(
        context: Context,
        utterance: PendingUtterance,
        initialDelayMs: Long
    ) {
        ensureInitialized(context)
        if (ttsUnavailable) return

        // If an arrival announcement is currently speaking, drop lower-priority routine distance alerts
        if (!utterance.isArrival && !utterance.isPreview && activeArrivalSpeaking) {
            return
        }

        if (initialDelayMs > 0L) {
            delayedSpeakJob?.cancel()
            delayedSpeakJob = voiceScope.launch {
                delay(initialDelayMs)
                dispatchOrQueueInternal(utterance)
            }
        } else {
            if (utterance.isArrival || utterance.isPreview) {
                delayedSpeakJob?.cancel()
                delayedSpeakJob = null
            }
            dispatchOrQueueInternal(utterance)
        }
    }

    private fun dispatchOrQueueInternal(utterance: PendingUtterance) {
        val ready: Boolean
        synchronized(lock) {
            ready = isInitialized && tts != null && !ttsUnavailable
            if (!ready) {
                if (utterance.isArrival || utterance.isPreview) {
                    pendingQueue.clear()
                } else if (pendingQueue.size >= 2) {
                    pendingQueue.removeFirst()
                }
                pendingQueue.addLast(utterance)
                return
            }
        }
        speakNowInternal(utterance)
    }

    private fun drainPendingUtterances() {
        val items = mutableListOf<PendingUtterance>()
        synchronized(lock) {
            while (pendingQueue.isNotEmpty()) {
                items.add(pendingQueue.removeFirst())
            }
        }
        items.forEach { speakNowInternal(it) }
    }

    private fun speakNowInternal(utterance: PendingUtterance) {
        val engine = tts ?: return
        try {
            configureEngineForSettings(engine, utterance.settings)
            requestTransientDuckingAudioFocus()

            val volumeGain = (utterance.settings.voiceVolumePercent.coerceIn(0, 100) / 100f)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeGain)
            }

            val queueMode = if (utterance.isArrival || utterance.isPreview) {
                synchronized(lock) {
                    activeUtteranceIds.clear()
                    activeArrivalSpeaking = utterance.isArrival
                }
                TextToSpeech.QUEUE_FLUSH
            } else {
                // Routine distance announcements replace stale queued distance callouts cleanly
                // without interrupting an active arrival announcement.
                if (activeArrivalSpeaking) {
                    return
                }
                TextToSpeech.QUEUE_FLUSH
            }

            val firstId = "ctecs_km_voice_${UUID.randomUUID()}"
            synchronized(lock) {
                activeUtteranceIds.add(firstId)
            }
            engine.speak(utterance.text, queueMode, params, firstId)

            if (utterance.repeatOnce) {
                val pauseId = "ctecs_km_pause_${UUID.randomUUID()}"
                val repeatId = "ctecs_km_repeat_${UUID.randomUUID()}"
                synchronized(lock) {
                    activeUtteranceIds.add(pauseId)
                    activeUtteranceIds.add(repeatId)
                }
                engine.playSilentUtterance(420L, TextToSpeech.QUEUE_ADD, pauseId)
                engine.speak(utterance.text, TextToSpeech.QUEUE_ADD, params, repeatId)
            }
        } catch (_: Throwable) {
            synchronized(lock) {
                activeUtteranceIds.clear()
                activeArrivalSpeaking = false
            }
            abandonAudioFocus()
        }
    }

    private fun configureEngineForSettings(engine: TextToSpeech, settings: KmStoneAlertSettings) {
        try {
            engine.setSpeechRate(settings.voiceSpeed.speechRate)
            engine.setPitch(1.0f)

            // Apply AudioAttributes so Android routes speech cleanly as navigation guidance
            try {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            } catch (_: Throwable) {}

            // Apply preferred language (with safe fallback to system default)
            val targetLocale = if (settings.voiceLanguageTag.isNotBlank()) {
                Locale.forLanguageTag(settings.voiceLanguageTag)
            } else {
                Locale.getDefault()
            }
            val langResult = try {
                engine.setLanguage(targetLocale)
            } catch (_: Throwable) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                try {
                    engine.setLanguage(Locale.getDefault())
                } catch (_: Throwable) {}
            }

            // Apply preferred voice if installed and available
            if (settings.voiceName.isNotBlank()) {
                val matchedVoice: Voice? = try {
                    engine.voices?.firstOrNull { voice ->
                        voice.name == settings.voiceName && !voice.isNetworkConnectionRequired
                    } ?: engine.voices?.firstOrNull { it.name == settings.voiceName }
                } catch (_: Throwable) {
                    null
                }
                if (matchedVoice != null) {
                    try {
                        engine.voice = matchedVoice
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    private fun attachUtteranceListener() {
        try {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    handleUtteranceFinished(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleUtteranceFinished(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleUtteranceFinished(utteranceId)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    handleUtteranceFinished(utteranceId)
                }
            })
        } catch (_: Throwable) {}
    }

    private fun handleUtteranceFinished(utteranceId: String?) {
        val shouldAbandon: Boolean
        synchronized(lock) {
            if (utteranceId != null) {
                activeUtteranceIds.remove(utteranceId)
            }
            shouldAbandon = activeUtteranceIds.isEmpty()
            if (shouldAbandon) {
                activeArrivalSpeaking = false
            }
        }
        if (shouldAbandon) {
            abandonAudioFocus()
        }
    }

    private fun refreshInstalledLanguagesAndVoices() {
        val engine = tts ?: return
        try {
            val locales = engine.availableLanguages
                ?.filter { locale ->
                    val status = try {
                        engine.isLanguageAvailable(locale)
                    } catch (_: Throwable) {
                        TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    status >= TextToSpeech.LANG_AVAILABLE
                }
                ?.distinctBy { it.toLanguageTag() }
                ?.sortedBy { it.getDisplayName(Locale.getDefault()) }
                ?.map { locale ->
                    KmStoneLanguageOption(
                        languageTag = locale.toLanguageTag(),
                        displayLabel = locale.getDisplayName(Locale.getDefault())
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    )
                }
                .orEmpty()

            val voices = engine.voices
                ?.filter { !it.isNetworkConnectionRequired }
                ?.sortedWith(compareBy<Voice> { it.locale.getDisplayName(Locale.getDefault()) }.thenBy { it.name })
                ?.mapIndexed { idx, voice ->
                    val langDisplay = voice.locale.getDisplayName(Locale.getDefault())
                    val shortName = voice.name
                        .substringAfterLast('#')
                        .substringBefore("-local")
                        .ifBlank { "Voice ${idx + 1}" }
                    KmStoneVoiceOption(
                        name = voice.name,
                        displayLabel = "$langDisplay • $shortName",
                        languageTag = voice.locale.toLanguageTag()
                    )
                }
                .orEmpty()

            _availableLanguages.value = locales
            _availableVoices.value = voices
        } catch (_: Throwable) {
            _availableLanguages.value = emptyList()
            _availableVoices.value = emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestTransientDuckingAudioFocus() {
        val ctx = appContextRef ?: return
        val audioMgr = try {
            ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                activeFocusRequest = req
                audioMgr.requestAudioFocus(req)
            } else {
                val listener = AudioManager.OnAudioFocusChangeListener { }
                legacyFocusListener = listener
                audioMgr.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Throwable) {}
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        val ctx = appContextRef ?: return
        val audioMgr = try {
            ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { audioMgr.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                legacyFocusListener?.let { audioMgr.abandonAudioFocus(it) }
                legacyFocusListener = null
            }
        } catch (_: Throwable) {}
    }
}
