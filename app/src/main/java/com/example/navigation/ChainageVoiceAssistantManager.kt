package com.example.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.model.Chainage
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.ChainageVoiceLanguage
import com.example.model.KmStoneAlertSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * UI states for the Navigate to Chainage Smart Voice Assistant.
 */
enum class ChainageVoiceAssistantStage {
    IDLE,
    LISTENING,
    PROCESSING,
    DESTINATION_RECOGNIZED,
    AWAITING_CONFIRMATION,
    AMBIGUOUS_CONFIRMATION,
    NAVIGATION_STARTED,
    RECOGNITION_FAILED,
    PERMISSION_UNAVAILABLE,
    SERVICE_UNAVAILABLE
}

data class ChainageVoiceAssistantState(
    val stage: ChainageVoiceAssistantStage = ChainageVoiceAssistantStage.IDLE,
    val statusText: String = "Tap microphone to speak chainage",
    val recognizedTranscript: String = "",
    val recognizedChainage: Chainage? = null,
    val ambiguousCandidates: List<Chainage> = emptyList(),
    val confirmationPrompt: String = "",
    val errorDetail: String? = null,
    val rmsDb: Float = 0f,
    val isListeningForConfirmation: Boolean = false
)

/**
 * Callbacks linking [ChainageVoiceAssistantManager] cleanly to the active Navigate to Chainage popup
 * without duplicating GPS, chainage calculation, or external navigation engines.
 */
interface ChainageVoiceAssistantCallbacks {
    fun onDestinationRecognized(chainage: Chainage, rawTranscript: String): ChainageDestinationCalculation?
    fun onRequestRepeatDestination(): ChainageDestinationCalculation?
    fun onRequestClearDestination()
    fun onRequestStartNavigation()
    fun onRequestCancelPopup()
}

/**
 * Pure spoken-confirmation formatter for Navigate to Chainage voice responses.
 */
object ChainageVoiceConfirmationFormatter {

    fun formatSpokenRemainingDistance(diffMetres: Long): String {
        val absM = kotlin.math.abs(diffMetres)
        val km = absM / 1000L
        val remM = absM % 1000L
        return when {
            absM == 0L -> "0 metres"
            km == 0L -> if (remM == 1L) "1 metre" else "$remM metres"
            remM == 0L -> if (km == 1L) "1 kilometre" else "$km kilometres"
            else -> {
                val kmPart = if (km == 1L) "1 kilometre" else "$km kilometres"
                val mPart = if (remM == 1L) "1 metre" else "$remM metres"
                "$kmPart and $mPart"
            }
        }
    }

    fun buildConfirmationSpeech(
        calculation: ChainageDestinationCalculation,
        askToStartNavigation: Boolean
    ): String {
        val spokenDest = KmStoneVoiceScriptFormatter.formatSpokenChainage(calculation.formattedDestinationChainage)
        if (!calculation.isValidChainageInput) {
            return calculation.inputValidationError ?: "Invalid chainage value. Please try again."
        }
        if (!calculation.isLocationResolved) {
            return calculation.unavailableReason
                ?: "Destination chainage $spokenDest is not available on the active road alignment."
        }

        val effectiveDistM = calculation.alignmentTravelDistanceMetres ?: calculation.chainageDifferenceMetres
        val distSentence = when {
            effectiveDistM == null -> "Waiting for live GPS chainage."
            effectiveDistM == 0L -> "You are currently at destination chainage $spokenDest."
            else -> "Distance remaining ${formatSpokenRemainingDistance(effectiveDistM)}."
        }

        val question = if (askToStartNavigation) {
            " Do you want to start navigation?"
        } else {
            ""
        }

        return "Destination chainage $spokenDest. $distSentence$question"
    }
}

/**
 * Dedicated Smart Voice Assistant Manager for the Navigate to Chainage workflow.
 *
 * Reliability & Resource Guarantees:
 * - Uses native Android [SpeechRecognizer] (zero external cloud speech services).
 * - Maintains at most ONE active SpeechRecognizer instance at a time; never listens in background.
 * - Reuses CTECS's single shared [KmStoneVoiceAnnouncementManager] Android TTS engine (zero duplicate TTS).
 * - Never starts navigation from ambiguous, invalid, out-of-alignment, or unconfirmed speech.
 * - Releases SpeechRecognizer and stops TTS cleanly when cancelled or when the popup closes.
 */
class ChainageVoiceAssistantManager(
    context: Context,
    private val callbacks: ChainageVoiceAssistantCallbacks
) {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentSettings: ChainageVoiceAssistantSettings = ChainageVoiceAssistantSettings()
    private var isClosed: Boolean = false

    private val _state = MutableStateFlow(ChainageVoiceAssistantState())
    val state: StateFlow<ChainageVoiceAssistantState> = _state.asStateFlow()

    fun updateSettings(settings: ChainageVoiceAssistantSettings) {
        currentSettings = settings
        if (!settings.effectiveVoiceSearchActive() && _state.value.stage == ChainageVoiceAssistantStage.LISTENING) {
            stopListening()
        }
        if (!settings.effectiveVoiceOutputActive()) {
            KmStoneVoiceAnnouncementManager.stopSpeaking()
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return hasRecordAudioPermission(appContext)
    }

    fun isSpeechServiceAvailable(): Boolean {
        return isSpeechServiceAvailable(appContext)
    }

    /**
     * Step 1 – Activate Voice Search (for destination entry or voice confirmation).
     */
    fun startListening(
        settings: ChainageVoiceAssistantSettings,
        forConfirmation: Boolean = false
    ) {
        currentSettings = settings
        if (isClosed) return

        if (!settings.effectiveVoiceSearchActive()) {
            _state.value = ChainageVoiceAssistantState(
                stage = ChainageVoiceAssistantStage.IDLE,
                statusText = "Voice search is disabled in Settings"
            )
            return
        }

        if (!hasRecordAudioPermission(appContext)) {
            _state.value = ChainageVoiceAssistantState(
                stage = ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE,
                statusText = "Microphone permission required",
                errorDetail = "Allow microphone permission to speak destination chainages."
            )
            return
        }

        if (!isSpeechServiceAvailable(appContext)) {
            _state.value = ChainageVoiceAssistantState(
                stage = ChainageVoiceAssistantStage.SERVICE_UNAVAILABLE,
                statusText = "Speech recognition service unavailable",
                errorDetail = "No Android speech recognition service is installed or enabled on this device. You can still type chainage manually."
            )
            return
        }

        // Stop any active TTS before opening the microphone so the mic never hears its own prompt
        KmStoneVoiceAnnouncementManager.stopSpeaking()

        mainHandler.post {
            if (isClosed) return@post
            try {
                destroyRecognizerInternal()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(createRecognitionListener(forConfirmation))

                val intent = buildRecognizerIntent(settings)
                _state.value = _state.value.copy(
                    stage = ChainageVoiceAssistantStage.LISTENING,
                    statusText = if (forConfirmation) {
                        "Listening… Say \"Yes, start navigation\" or \"No, change destination\""
                    } else {
                        "Listening for destination chainage"
                    },
                    errorDetail = null,
                    rmsDb = 0f,
                    isListeningForConfirmation = forConfirmation
                )
                recognizer.startListening(intent)
            } catch (_: Throwable) {
                destroyRecognizerInternal()
                _state.value = ChainageVoiceAssistantState(
                    stage = ChainageVoiceAssistantStage.SERVICE_UNAVAILABLE,
                    statusText = "Unable to start speech recognition",
                    errorDetail = "Speech recognizer could not be initialized on this device."
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            destroyRecognizerInternal()
            val cur = _state.value
            if (cur.stage == ChainageVoiceAssistantStage.LISTENING || cur.stage == ChainageVoiceAssistantStage.PROCESSING) {
                _state.value = cur.copy(
                    stage = if (cur.recognizedChainage != null) {
                        ChainageVoiceAssistantStage.AWAITING_CONFIRMATION
                    } else {
                        ChainageVoiceAssistantStage.IDLE
                    },
                    statusText = if (cur.recognizedChainage != null) {
                        "Confirm destination ${cur.recognizedChainage.format()}"
                    } else {
                        "Tap microphone to speak chainage"
                    },
                    rmsDb = 0f,
                    isListeningForConfirmation = false
                )
            }
        }
    }

    /**
     * Marks microphone permission denied cleanly after runtime request rejection.
     */
    fun onPermissionDenied() {
        _state.value = ChainageVoiceAssistantState(
            stage = ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE,
            statusText = "Microphone permission denied",
            errorDetail = "Microphone access is required only while listening for spoken chainages. You can enter chainage manually anytime."
        )
    }

    /**
     * Called when user taps one of the disambiguation candidate chips or edits manually.
     */
    fun selectDisambiguatedCandidate(chainage: Chainage, settings: ChainageVoiceAssistantSettings) {
        currentSettings = settings
        handleResolvedDestination(
            chainage = chainage,
            rawTranscript = chainage.format(),
            settings = settings
        )
    }

    fun markNavigationStarted(destinationLabel: String) {
        stopListening()
        _state.value = _state.value.copy(
            stage = ChainageVoiceAssistantStage.NAVIGATION_STARTED,
            statusText = "Navigation started to KM $destinationLabel",
            errorDetail = null,
            isListeningForConfirmation = false
        )
    }

    fun resetToIdle() {
        stopListening()
        KmStoneVoiceAnnouncementManager.stopSpeaking()
        _state.value = ChainageVoiceAssistantState()
    }

    /**
     * Processes returned ASR hypotheses (also directly callable from unit/Robolectric tests).
     */
    fun handleRecognitionHypotheses(
        hypotheses: List<String>,
        confidences: FloatArray? = null,
        forConfirmation: Boolean = _state.value.isListeningForConfirmation
    ) {
        val awaitingConfirm = forConfirmation ||
            _state.value.stage == ChainageVoiceAssistantStage.AWAITING_CONFIRMATION ||
            _state.value.stage == ChainageVoiceAssistantStage.DESTINATION_RECOGNIZED

        val intent = ChainageVoiceCommandParser.parseVoiceHypotheses(
            hypotheses = hypotheses,
            confidences = confidences,
            awaitingConfirmation = awaitingConfirm
        )

        when (intent) {
            is ChainageVoiceIntent.SetDestination -> {
                handleResolvedDestination(
                    chainage = intent.chainage,
                    rawTranscript = intent.rawTranscript,
                    settings = currentSettings
                )
            }

            is ChainageVoiceIntent.AmbiguousDestination -> {
                _state.value = ChainageVoiceAssistantState(
                    stage = ChainageVoiceAssistantStage.AMBIGUOUS_CONFIRMATION,
                    statusText = "Please confirm or repeat destination",
                    recognizedTranscript = intent.rawTranscript,
                    ambiguousCandidates = intent.candidates,
                    errorDetail = intent.explanation,
                    isListeningForConfirmation = false
                )
                speakIfEnabled(intent.explanation)
            }

            is ChainageVoiceIntent.ConfirmDestination -> {
                val calc = callbacks.onRequestRepeatDestination()
                if (calc != null && calc.canStartNavigation) {
                    val prompt = ChainageVoiceConfirmationFormatter.buildConfirmationSpeech(
                        calculation = calc,
                        askToStartNavigation = true
                    )
                    _state.value = _state.value.copy(
                        stage = ChainageVoiceAssistantStage.AWAITING_CONFIRMATION,
                        statusText = "Destination ${calc.formattedDestinationChainage} confirmed. Say \"Start navigation\" or tap Start.",
                        recognizedChainage = calc.destinationChainage,
                        confirmationPrompt = prompt,
                        errorDetail = null
                    )
                    speakIfEnabled(prompt)
                } else {
                    _state.value = _state.value.copy(
                        stage = ChainageVoiceAssistantStage.RECOGNITION_FAILED,
                        statusText = "No valid destination to confirm",
                        errorDetail = calc?.unavailableReason ?: "Please speak or enter a valid destination chainage first."
                    )
                }
            }

            is ChainageVoiceIntent.StartNavigation -> {
                val calc = callbacks.onRequestRepeatDestination()
                if (calc != null && calc.canStartNavigation) {
                    markNavigationStarted(calc.formattedDestinationChainage)
                    speakIfEnabled("Starting navigation to chainage ${KmStoneVoiceScriptFormatter.formatSpokenChainage(calc.formattedDestinationChainage)}.")
                    callbacks.onRequestStartNavigation()
                } else {
                    val reason = calc?.unavailableReason
                        ?: "Destination is not validated on the active alignment yet."
                    _state.value = _state.value.copy(
                        stage = ChainageVoiceAssistantStage.RECOGNITION_FAILED,
                        statusText = "Cannot start navigation yet",
                        errorDetail = reason
                    )
                    speakIfEnabled(reason)
                }
            }

            is ChainageVoiceIntent.RepeatDestination -> {
                val calc = callbacks.onRequestRepeatDestination()
                if (calc != null && calc.isValidChainageInput) {
                    val prompt = ChainageVoiceConfirmationFormatter.buildConfirmationSpeech(
                        calculation = calc,
                        askToStartNavigation = calc.canStartNavigation && currentSettings.voiceConfirmationBeforeNav
                    )
                    _state.value = _state.value.copy(
                        stage = if (calc.canStartNavigation) ChainageVoiceAssistantStage.AWAITING_CONFIRMATION else ChainageVoiceAssistantStage.RECOGNITION_FAILED,
                        statusText = if (calc.canStartNavigation) "Destination ${calc.formattedDestinationChainage}" else "Destination unavailable",
                        confirmationPrompt = prompt,
                        errorDetail = calc.unavailableReason
                    )
                    speakIfEnabled(prompt)
                } else {
                    val msg = "No destination chainage is set yet."
                    _state.value = _state.value.copy(
                        stage = ChainageVoiceAssistantStage.IDLE,
                        statusText = msg
                    )
                    speakIfEnabled(msg)
                }
            }

            is ChainageVoiceIntent.ChangeDestination -> {
                callbacks.onRequestClearDestination()
                _state.value = ChainageVoiceAssistantState(
                    stage = ChainageVoiceAssistantStage.IDLE,
                    statusText = "Destination cleared. Speak or enter a new chainage.",
                    recognizedTranscript = hypotheses.firstOrNull().orEmpty()
                )
                speakIfEnabled("Okay, please say the new destination chainage.")
            }

            is ChainageVoiceIntent.ClearDestination -> {
                callbacks.onRequestClearDestination()
                _state.value = ChainageVoiceAssistantState(
                    stage = ChainageVoiceAssistantStage.IDLE,
                    statusText = "Destination cleared",
                    recognizedTranscript = hypotheses.firstOrNull().orEmpty()
                )
                speakIfEnabled("Destination cleared.")
            }

            is ChainageVoiceIntent.Cancel -> {
                stopListening()
                KmStoneVoiceAnnouncementManager.stopSpeaking()
                callbacks.onRequestCancelPopup()
            }

            is ChainageVoiceIntent.Unrecognized -> {
                _state.value = _state.value.copy(
                    stage = ChainageVoiceAssistantStage.RECOGNITION_FAILED,
                    statusText = "Could not understand chainage",
                    recognizedTranscript = intent.rawTranscript,
                    errorDetail = intent.reason,
                    isListeningForConfirmation = false
                )
                speakIfEnabled("I did not catch a valid chainage. Please repeat or type the destination.")
            }
        }
    }

    private fun handleResolvedDestination(
        chainage: Chainage,
        rawTranscript: String,
        settings: ChainageVoiceAssistantSettings
    ) {
        val calc = callbacks.onDestinationRecognized(chainage, rawTranscript)
        if (calc == null || !calc.isValidChainageInput) {
            val err = calc?.inputValidationError ?: "Invalid destination chainage."
            _state.value = ChainageVoiceAssistantState(
                stage = ChainageVoiceAssistantStage.RECOGNITION_FAILED,
                statusText = "Invalid chainage",
                recognizedTranscript = rawTranscript,
                recognizedChainage = chainage,
                errorDetail = err
            )
            speakIfEnabled(err)
            return
        }

        if (!calc.isLocationResolved) {
            val reason = calc.unavailableReason
                ?: "Chainage ${calc.formattedDestinationChainage} is outside the active road alignment."
            _state.value = ChainageVoiceAssistantState(
                stage = ChainageVoiceAssistantStage.RECOGNITION_FAILED,
                statusText = "Outside active alignment",
                recognizedTranscript = rawTranscript,
                recognizedChainage = chainage,
                errorDetail = reason
            )
            speakIfEnabled(reason)
            return
        }

        val spokenPrompt = ChainageVoiceConfirmationFormatter.buildConfirmationSpeech(
            calculation = calc,
            askToStartNavigation = settings.voiceConfirmationBeforeNav
        )

        val nextStage = if (settings.voiceConfirmationBeforeNav) {
            ChainageVoiceAssistantStage.AWAITING_CONFIRMATION
        } else {
            ChainageVoiceAssistantStage.DESTINATION_RECOGNIZED
        }

        _state.value = ChainageVoiceAssistantState(
            stage = nextStage,
            statusText = if (settings.voiceConfirmationBeforeNav) {
                "Confirm KM ${calc.formattedDestinationChainage} • ${calc.formattedChainageDifference}"
            } else {
                "Recognized KM ${calc.formattedDestinationChainage}"
            },
            recognizedTranscript = rawTranscript,
            recognizedChainage = chainage,
            confirmationPrompt = spokenPrompt,
            errorDetail = null,
            isListeningForConfirmation = false
        )

        speakIfEnabled(spokenPrompt)
    }

    private fun speakIfEnabled(text: String) {
        if (!currentSettings.effectiveVoiceOutputActive() || text.isBlank()) return
        speakWithAssistantSettings(appContext, text, currentSettings)
    }

    private fun buildRecognizerIntent(settings: ChainageVoiceAssistantSettings): Intent {
        val langTag = settings.effectiveLanguageTag().ifBlank { Locale.getDefault().toLanguageTag() }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
    }

    private fun createRecognitionListener(forConfirmation: Boolean): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(
                    stage = ChainageVoiceAssistantStage.LISTENING,
                    errorDetail = null
                )
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(
                    stage = ChainageVoiceAssistantStage.PROCESSING,
                    statusText = "Processing speech…",
                    rmsDb = 0f
                )
            }

            override fun onError(error: Int) {
                destroyRecognizerInternal()
                val (status, detail) = mapSpeechRecognizerError(error)
                val targetStage = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE
                    SpeechRecognizer.ERROR_CLIENT -> ChainageVoiceAssistantStage.SERVICE_UNAVAILABLE
                    else -> ChainageVoiceAssistantStage.RECOGNITION_FAILED
                }
                _state.value = _state.value.copy(
                    stage = targetStage,
                    statusText = status,
                    errorDetail = detail,
                    rmsDb = 0f,
                    isListeningForConfirmation = false
                )
            }

            override fun onResults(results: Bundle?) {
                destroyRecognizerInternal()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                handleRecognitionHypotheses(
                    hypotheses = matches,
                    confidences = scores,
                    forConfirmation = forConfirmation
                )
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!partial.isNullOrEmpty()) {
                    _state.value = _state.value.copy(recognizedTranscript = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Throwable) {}
        speechRecognizer = null
    }

    fun release() {
        isClosed = true
        mainHandler.post {
            destroyRecognizerInternal()
        }
        KmStoneVoiceAnnouncementManager.stopSpeaking()
    }

    companion object {
        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun isSpeechServiceAvailable(context: Context): Boolean {
            return try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (_: Throwable) {
                false
            }
        }

        fun mapSpeechRecognizerError(errorCode: Int): Pair<String, String> = when (errorCode) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission unavailable" to "Grant microphone permission to use voice search."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech service offline" to "Network speech service unavailable. Install an offline language pack or enter chainage manually."
            SpeechRecognizer.ERROR_NO_MATCH ->
                "No chainage recognized" to "Speak clearly, for example: \"201 plus 500\"."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "No speech heard" to "Tap the microphone and speak the destination chainage."
            SpeechRecognizer.ERROR_AUDIO ->
                "Microphone audio error" to "Could not capture audio from the microphone."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech recognizer busy" to "Please wait a moment and try again."
            else ->
                "Voice recognition failed" to "Please try again or enter the destination chainage manually."
        }

        /**
         * Speaks a confirmation or test prompt via CTECS's single shared [SmartTtsManager]
         * TTS instance using the Chainage Voice Assistant's engine, language, voice, speed, pitch, and volume settings.
         */
        fun speakWithAssistantSettings(
            context: Context,
            text: String,
            settings: ChainageVoiceAssistantSettings
        ): KmStoneVoiceDispatchRecord {
            val bridgeSettings = KmStoneAlertSettings(
                voiceAnnouncementEnabled = true,
                voiceEnginePackage = settings.ttsEnginePackage,
                voiceName = settings.ttsVoiceName,
                voiceLanguageTag = settings.effectiveLanguageTag(),
                voiceSpeed = settings.speechSpeed,
                voicePitch = settings.effectiveSpeechPitch(),
                voiceVolumePercent = settings.volumePercent
            )
            return KmStoneVoiceAnnouncementManager.speakCustomAssistantText(
                context = context,
                spokenText = text,
                settings = bridgeSettings
            )
        }

        /**
         * Runs a full Voice Output diagnostic & audio test verifying selected engine, language,
         * voice, speech speed, pitch, volume/audio output, and recovery.
         */
        fun testVoiceOutputDetailed(
            context: Context,
            settings: ChainageVoiceAssistantSettings
        ): TtsTestResult {
            return SmartTtsManager.testVoiceOutput(
                context = context,
                languageTag = settings.effectiveLanguageTag(),
                voiceName = settings.ttsVoiceName,
                enginePackage = settings.ttsEnginePackage,
                speechRate = settings.speechSpeed.speechRate,
                pitch = settings.effectiveSpeechPitch(),
                volumePercent = settings.volumePercent,
                sampleText = SmartTtsManager.TEST_VOICE_SAMPLE_TEXT
            )
        }

        /**
         * Plays a sample confirmation test from Settings -> Navigation -> Chainage Voice Assistant.
         */
        fun testVoiceOutput(
            context: Context,
            settings: ChainageVoiceAssistantSettings
        ): KmStoneVoiceDispatchRecord {
            val result = testVoiceOutputDetailed(context, settings)
            return KmStoneVoiceDispatchRecord(
                spokenText = result.spokenText,
                isArrival = false,
                isPreview = true,
                style = com.example.model.KmVoiceAnnouncementStyle.CHAINAGE,
                speed = settings.speechSpeed,
                volumePercent = settings.volumePercent.coerceIn(0, 100),
                repeatQueued = false,
                languageTag = settings.effectiveLanguageTag(),
                voiceName = settings.ttsVoiceName,
                dispatchedToTts = result.success,
                timestampMs = System.currentTimeMillis()
            )
        }
    }
}
