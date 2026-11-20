package com.example.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Metadata for an installed Android Text-to-Speech engine discovered on the device.
 */
data class TtsEngineInfo(
    val packageName: String,
    val label: String,
    val isSystemDefault: Boolean,
    val isEnabled: Boolean
)

/**
 * Granular, truthful lifecycle & diagnostic state for CTECS Android TTS.
 */
enum class TtsAvailabilityStatus {
    NOT_INITIALIZED,
    INITIALIZING,
    READY,
    NO_ENGINE_INSTALLED,
    ENGINE_DISABLED,
    INIT_FAILED,
    LANGUAGE_MISSING_DATA,
    LANGUAGE_NOT_SUPPORTED,
    AUDIO_OUTPUT_UNAVAILABLE,
    ENGINE_DISCONNECTED;

    val isUsable: Boolean
        get() = this == READY

    val needsSetupAction: Boolean
        get() = this == NO_ENGINE_INSTALLED ||
            this == ENGINE_DISABLED ||
            this == INIT_FAILED ||
            this == LANGUAGE_MISSING_DATA ||
            this == LANGUAGE_NOT_SUPPORTED ||
            this == ENGINE_DISCONNECTED
}

enum class TtsLanguageAvailability {
    AVAILABLE,
    MISSING_DATA,
    NOT_SUPPORTED,
    ENGINE_NOT_READY
}

data class TtsLanguageCheckResult(
    val requestedTag: String,
    val displayLanguageName: String,
    val availability: TtsLanguageAvailability,
    val resolvedLocale: Locale?,
    val message: String
)

enum class TtsSpeechPriority {
    CRITICAL_ARRIVAL,
    USER_INTERACTIVE,
    ROUTINE
}

data class TtsSpeakRequest(
    val text: String,
    val languageTag: String = "",
    val voiceName: String = "",
    val enginePackage: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volumePercent: Int = 90,
    val priority: TtsSpeechPriority = TtsSpeechPriority.USER_INTERACTIVE,
    val repeatOnce: Boolean = false
)

data class TtsSpeakOutcome(
    val dispatched: Boolean,
    val queuedPendingInit: Boolean = false,
    val status: TtsAvailabilityStatus,
    val message: String,
    val engineLabel: String = "",
    val resolvedLanguageTag: String = "",
    val resolvedVoiceLabel: String = ""
)

data class TtsTestResult(
    val success: Boolean,
    val spokenText: String,
    val message: String,
    val status: TtsAvailabilityStatus,
    val engineLabel: String = "",
    val languageLabel: String = "",
    val voiceLabel: String = ""
)

data class TtsSetupIntentResolution(
    val intent: Intent?,
    val actionLabel: String,
    val description: String
)

data class TtsStatusSnapshot(
    val status: TtsAvailabilityStatus = TtsAvailabilityStatus.NOT_INITIALIZED,
    val installedEngines: List<TtsEngineInfo> = emptyList(),
    val disabledEngines: List<TtsEngineInfo> = emptyList(),
    val defaultEnginePackage: String = "",
    val activeEnginePackage: String = "",
    val activeEngineLabel: String = "",
    val preferredEnginePackage: String = "",
    val availableLanguages: List<KmStoneLanguageOption> = emptyList(),
    val availableVoices: List<KmStoneVoiceOption> = emptyList(),
    val languageCheck: TtsLanguageCheckResult? = null,
    val statusHeadline: String = "Voice output not initialized",
    val statusDetail: String = "Enable Voice Output to detect and initialize an Android Text-to-Speech engine.",
    val canOpenTtsSettings: Boolean = false,
    val canInstallVoiceData: Boolean = false,
    val canInstallGoogleSpeechServices: Boolean = false,
    val lastCheckedAtMs: Long = 0L
) {
    val isReady: Boolean
        get() = status == TtsAvailabilityStatus.READY

    val isAvailableOrIdle: Boolean
        get() = status == TtsAvailabilityStatus.READY ||
            status == TtsAvailabilityStatus.INITIALIZING ||
            status == TtsAvailabilityStatus.NOT_INITIALIZED
}

/**
 * Lightweight voice metadata decoupled from final Android [Voice] constructors so unit/Robolectric
 * tests can deterministically exercise voice filtering, selection, and fallback.
 */
data class TtsVoiceDescriptor(
    val name: String,
    val locale: Locale,
    val isNetworkConnectionRequired: Boolean = false,
    val quality: Int = 300
)

/**
 * Abstraction over a single Android [TextToSpeech] engine connection.
 */
interface TtsEngineAdapter {
    val requestedEnginePackage: String
    val defaultEnginePackage: String
    fun getReportedEngines(): List<TtsEngineInfo>
    fun getAvailableLanguages(): Set<Locale>
    fun getVoices(): List<TtsVoiceDescriptor>
    fun isLanguageAvailable(locale: Locale): Int
    fun setLanguage(locale: Locale): Int
    fun setVoiceByName(voiceName: String): Boolean
    fun setSpeechRate(rate: Float): Int
    fun setPitch(pitch: Float): Int
    fun setNavigationAudioAttributes()
    fun setUtteranceListener(
        onStart: (String?) -> Unit,
        onDone: (String?) -> Unit,
        onError: (String?, Int) -> Unit,
        onStop: (String?) -> Unit
    )
    fun speak(text: String, queueMode: Int, volumeGain: Float, utteranceId: String): Int
    fun playSilent(durationMs: Long, queueMode: Int, utteranceId: String): Int
    fun stop()
    fun shutdown()
}

/**
 * Abstraction over Android platform TTS discovery, engine creation, audio checks, and setup intents.
 */
interface TtsPlatformProvider {
    fun discoverInstalledEngines(context: Context): Pair<List<TtsEngineInfo>, List<TtsEngineInfo>>
    fun getSystemDefaultEnginePackage(context: Context): String
    fun createEngine(
        context: Context,
        enginePackage: String?,
        onInit: (TtsEngineAdapter, Int) -> Unit
    ): TtsEngineAdapter
    fun isAudioOutputMuted(context: Context): Boolean
    fun canResolveIntent(context: Context, intent: Intent): Boolean
}

/**
 * Production Android implementation of [TtsEngineAdapter] wrapping [android.speech.tts.TextToSpeech].
 */
private class AndroidTtsEngineAdapter(
    private val tts: TextToSpeech,
    override val requestedEnginePackage: String
) : TtsEngineAdapter {

    override val defaultEnginePackage: String
        get() = try {
            tts.defaultEngine.orEmpty()
        } catch (_: Throwable) {
            ""
        }

    override fun getReportedEngines(): List<TtsEngineInfo> {
        val defaultPkg = defaultEnginePackage
        return try {
            tts.engines.orEmpty().map { info ->
                TtsEngineInfo(
                    packageName = info.name.orEmpty(),
                    label = info.label.orEmpty().ifBlank { friendlyEngineName(info.name.orEmpty()) },
                    isSystemDefault = info.name == defaultPkg,
                    isEnabled = true
                )
            }.filter { it.packageName.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override fun getAvailableLanguages(): Set<Locale> {
        return try {
            tts.availableLanguages?.toSet().orEmpty()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    override fun getVoices(): List<TtsVoiceDescriptor> {
        return try {
            tts.voices.orEmpty().map { v ->
                TtsVoiceDescriptor(
                    name = v.name,
                    locale = v.locale,
                    isNetworkConnectionRequired = v.isNetworkConnectionRequired,
                    quality = v.quality
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override fun isLanguageAvailable(locale: Locale): Int {
        return try {
            tts.isLanguageAvailable(locale)
        } catch (_: Throwable) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun setLanguage(locale: Locale): Int {
        return try {
            tts.setLanguage(locale)
        } catch (_: Throwable) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun setVoiceByName(voiceName: String): Boolean {
        if (voiceName.isBlank()) return false
        return try {
            val matched: Voice? = tts.voices?.firstOrNull {
                it.name == voiceName && !it.isNetworkConnectionRequired
            } ?: tts.voices?.firstOrNull { it.name == voiceName }
            if (matched != null) {
                tts.voice = matched
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    override fun setSpeechRate(rate: Float): Int {
        return try {
            tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        } catch (_: Throwable) {
            TextToSpeech.ERROR
        }
    }

    override fun setPitch(pitch: Float): Int {
        return try {
            tts.setPitch(pitch.coerceIn(0.6f, 1.5f))
        } catch (_: Throwable) {
            TextToSpeech.ERROR
        }
    }

    override fun setNavigationAudioAttributes() {
        try {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (_: Throwable) {}
    }

    override fun setUtteranceListener(
        onStart: (String?) -> Unit,
        onDone: (String?) -> Unit,
        onError: (String?, Int) -> Unit,
        onStop: (String?) -> Unit
    ) {
        try {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onStart(utteranceId)
                override fun onDone(utteranceId: String?) = onDone(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

                override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId, errorCode)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = onStop(utteranceId)
            })
        } catch (_: Throwable) {}
    }

    override fun speak(text: String, queueMode: Int, volumeGain: Float, utteranceId: String): Int {
        return try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeGain.coerceIn(0f, 1f))
            }
            tts.speak(text, queueMode, params, utteranceId)
        } catch (_: Throwable) {
            TextToSpeech.ERROR
        }
    }

    override fun playSilent(durationMs: Long, queueMode: Int, utteranceId: String): Int {
        return try {
            tts.playSilentUtterance(durationMs, queueMode, utteranceId)
        } catch (_: Throwable) {
            TextToSpeech.ERROR
        }
    }

    override fun stop() {
        try {
            tts.stop()
        } catch (_: Throwable) {}
    }

    override fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Throwable) {}
    }
}

private fun friendlyEngineName(packageName: String): String {
    return when (packageName) {
        SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG -> "Google Speech Services"
        "com.samsung.SMT" -> "Samsung Text-to-Speech"
        "com.svox.pico" -> "Pico TTS"
        "com.reecedunn.espeak" -> "eSpeak TTS"
        "" -> "System Default TTS"
        else -> packageName.substringAfterLast('.').replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}

/**
 * Production Android [TtsPlatformProvider] using PackageManager, Settings.Secure, AudioManager,
 * and race-free [TextToSpeech] construction.
 */
private object AndroidTtsPlatformProvider : TtsPlatformProvider {

    @Suppress("DEPRECATION")
    override fun discoverInstalledEngines(context: Context): Pair<List<TtsEngineInfo>, List<TtsEngineInfo>> {
        val pm = context.packageManager ?: return emptyList<TtsEngineInfo>() to emptyList()
        val defaultPkg = getSystemDefaultEnginePackage(context)
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)

        val enabledList = mutableListOf<TtsEngineInfo>()
        val disabledList = mutableListOf<TtsEngineInfo>()
        val seenPackages = mutableSetOf<String>()

        try {
            val enabledFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val enabledResolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(enabledFlags.toLong()))
            } else {
                pm.queryIntentServices(intent, enabledFlags)
            }
            for (ri in enabledResolved) {
                val si = ri.serviceInfo ?: continue
                val pkg = si.packageName ?: continue
                if (!seenPackages.add(pkg)) continue
                val rawLabel = try {
                    ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Throwable) {
                    ""
                }
                val label = rawLabel.ifBlank { friendlyEngineName(pkg) }
                val isAppEnabled = try {
                    si.applicationInfo?.enabled != false && si.isEnabled
                } catch (_: Throwable) {
                    true
                }
                val info = TtsEngineInfo(
                    packageName = pkg,
                    label = label,
                    isSystemDefault = pkg == defaultPkg,
                    isEnabled = isAppEnabled
                )
                if (isAppEnabled) enabledList.add(info) else disabledList.add(info)
            }

            // Also inspect disabled TTS services so we can accurately distinguish
            // "engine disabled" from "no engine installed".
            val allFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_ALL
            } else {
                PackageManager.GET_DISABLED_COMPONENTS
            }
            val allResolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(allFlags.toLong()))
            } else {
                pm.queryIntentServices(intent, allFlags)
            }
            for (ri in allResolved) {
                val si = ri.serviceInfo ?: continue
                val pkg = si.packageName ?: continue
                if (!seenPackages.add(pkg)) continue
                val rawLabel = try {
                    ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Throwable) {
                    ""
                }
                val label = rawLabel.ifBlank { friendlyEngineName(pkg) }
                disabledList.add(
                    TtsEngineInfo(
                        packageName = pkg,
                        label = label,
                        isSystemDefault = pkg == defaultPkg,
                        isEnabled = false
                    )
                )
            }
        } catch (_: Throwable) {}

        val sortedEnabled = enabledList.sortedWith(
            compareByDescending<TtsEngineInfo> { it.isSystemDefault }
                .thenByDescending { it.packageName == SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
        return sortedEnabled to disabledList
    }

    override fun getSystemDefaultEnginePackage(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, "tts_default_synth")?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    override fun createEngine(
        context: Context,
        enginePackage: String?,
        onInit: (TtsEngineAdapter, Int) -> Unit
    ): TtsEngineAdapter {
        val appCtx = context.applicationContext ?: context
        val adapterRef = AtomicReference<AndroidTtsEngineAdapter?>(null)
        val earlyStatus = AtomicInteger(Int.MIN_VALUE)
        val listener = TextToSpeech.OnInitListener { status ->
            val created = adapterRef.get()
            if (created != null) {
                onInit(created, status)
            } else {
                // Capture synchronous callback fired before constructor returns
                earlyStatus.set(status)
            }
        }
        val tts = if (enginePackage.isNullOrBlank()) {
            TextToSpeech(appCtx, listener)
        } else {
            TextToSpeech(appCtx, listener, enginePackage)
        }
        val adapter = AndroidTtsEngineAdapter(tts, enginePackage.orEmpty())
        adapterRef.set(adapter)
        val captured = earlyStatus.getAndSet(Int.MIN_VALUE)
        if (captured != Int.MIN_VALUE) {
            onInit(adapter, captured)
        }
        return adapter
    }

    override fun isAudioOutputMuted(context: Context): Boolean {
        return try {
            val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val musicVol = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
            val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioMgr.isStreamMute(AudioManager.STREAM_MUSIC)
            } else {
                false
            }
            musicVol <= 0 || isMuted
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun canResolveIntent(context: Context, intent: Intent): Boolean {
        return try {
            val pm = context.packageManager ?: return false
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved != null
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Centralized, process-wide Android Text-to-Speech (TTS) Manager for all of CTECS:
 * - Navigate to Chainage destination confirmation & distance announcements
 * - Smart Chainage Voice Assistant responses & Test Voice Output
 * - Smart Next KM Stone announcements & Arrival alerts
 * - Direction-change navigation voice feedback
 *
 * Root-Cause Guarantees:
 * 1. Strictly ONE shared TTS engine instance across the entire application (zero duplicate instances).
 * 2. Eliminates constructor/callback race conditions so `availableLanguages` and `availableVoices`
 *    are always populated reliably upon initialization.
 * 3. Automatically discovers installed TTS engines, initializes the preferred or system default engine,
 *    and falls back cleanly to other installed compatible engines (including Google Speech Services)
 *    before ever reporting TTS unavailable.
 * 4. Distinguishes accurately between no engine installed, disabled engine, init failure, missing
 *    language voice data, unsupported language, muted audio output, and engine disconnection.
 * 5. Never silently substitutes a wrong language when the user selected Hindi, Telugu, English, etc.
 * 6. Automatically revalidates on app resume, engine switch, or default system engine change.
 * 7. Never blocks the main UI thread; queues announcements intelligently with priority and non-overlap.
 */
object SmartTtsManager {

    const val GOOGLE_SPEECH_SERVICES_PKG = "com.google.android.tts"
    const val TEST_VOICE_SAMPLE_TEXT =
        "Voice assistant is working. Your destination chainage is 201 plus 500. Remaining distance is 5 kilometres."

    private val KEY_PROBED_LOCALES = listOf(
        Locale.forLanguageTag("en-IN"),
        Locale.forLanguageTag("en-US"),
        Locale.forLanguageTag("en-GB"),
        Locale.forLanguageTag("hi-IN"),
        Locale.forLanguageTag("te-IN")
    )

    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var platformProvider: TtsPlatformProvider = AndroidTtsPlatformProvider

    @Volatile
    private var activeAdapter: TtsEngineAdapter? = null

    @Volatile
    private var activeInstanceCountInternal: Int = 0

    @Volatile
    private var activeAttemptGeneration: Int = 0

    @Volatile
    private var isInitializing: Boolean = false

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var activeEnginePackage: String = ""

    @Volatile
    private var preferredEnginePackage: String = ""

    @Volatile
    private var requestedLanguageTag: String = ""

    @Volatile
    private var activeArrivalSpeaking: Boolean = false

    @Volatile
    private var recoveryAttemptedForCurrentError: Boolean = false

    private var appContextRef: Context? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var delayedSpeakJob: Job? = null
    private var initJob: Job? = null

    private val activeUtteranceIds = mutableSetOf<String>()
    private val pendingQueue = ArrayDeque<TtsSpeakRequest>()

    private val _status = MutableStateFlow(TtsStatusSnapshot())
    val status: StateFlow<TtsStatusSnapshot> = _status.asStateFlow()

    val activeInstanceCount: Int
        get() = activeInstanceCountInternal

    /**
     * Detects installed TTS engines and initializes the best working engine asynchronously
     * (never blocking the main UI thread). Safe to call repeatedly — reuses the existing healthy
     * engine unless [preferredEngine] changed, the system default engine changed, or [forceReinitialize] is true.
     */
    fun initializeIfNeeded(
        context: Context,
        preferredEngine: String = preferredEnginePackage,
        languageTag: String = requestedLanguageTag,
        forceReinitialize: Boolean = false
    ) {
        val appCtx = context.applicationContext ?: context
        val cleanPreferred = preferredEngine.trim()
        val cleanLang = languageTag.trim()

        val shouldRunDiscovery: Boolean
        synchronized(lock) {
            appContextRef = appCtx
            preferredEnginePackage = cleanPreferred
            requestedLanguageTag = cleanLang

            val needsSwitch = cleanPreferred.isNotBlank() &&
                isInitialized &&
                activeEnginePackage.isNotBlank() &&
                !activeEnginePackage.equals(cleanPreferred, ignoreCase = true)

            val systemDefaultChanged = cleanPreferred.isBlank() &&
                isInitialized &&
                activeEnginePackage.isNotBlank() &&
                run {
                    val sysDef = platformProvider.getSystemDefaultEnginePackage(appCtx)
                    sysDef.isNotBlank() && !sysDef.equals(activeEnginePackage, ignoreCase = true)
                }

            shouldRunDiscovery = forceReinitialize ||
                needsSwitch ||
                systemDefaultChanged ||
                (!isInitialized && !isInitializing)
        }

        if (!shouldRunDiscovery) {
            if (isInitialized) {
                refreshLanguageCheck(cleanLang)
            }
            return
        }

        val generation: Int
        synchronized(lock) {
            activeAttemptGeneration += 1
            generation = activeAttemptGeneration
            isInitializing = true
            isInitialized = false
            recoveryAttemptedForCurrentError = false
            shutdownAdapterLocked()
        }

        // Publish initializing state immediately
        val currentSnap = _status.value
        _status.value = currentSnap.copy(
            status = TtsAvailabilityStatus.INITIALIZING,
            preferredEnginePackage = cleanPreferred,
            statusHeadline = "Detecting & initializing voice engine…",
            statusDetail = "Checking installed Android Text-to-Speech engines.",
            lastCheckedAtMs = System.currentTimeMillis()
        )

        initJob?.cancel()
        initJob = ttsScope.launch {
            runEngineDiscoveryAndInit(
                appCtx = appCtx,
                generation = generation,
                preferredEngine = cleanPreferred,
                languageTag = cleanLang
            )
        }
    }

    /**
     * Synchronous initialization helper used when callers (or tests) need immediate deterministic
     * detection on the calling thread without waiting for coroutine scheduling.
     */
    fun initializeBlockingForSync(
        context: Context,
        preferredEngine: String = preferredEnginePackage,
        languageTag: String = requestedLanguageTag,
        forceReinitialize: Boolean = false
    ): TtsStatusSnapshot {
        val appCtx = context.applicationContext ?: context
        val cleanPreferred = preferredEngine.trim()
        val cleanLang = languageTag.trim()

        val needsInit: Boolean
        val generation: Int
        synchronized(lock) {
            appContextRef = appCtx
            preferredEnginePackage = cleanPreferred
            requestedLanguageTag = cleanLang

            val needsSwitch = cleanPreferred.isNotBlank() &&
                isInitialized &&
                activeEnginePackage.isNotBlank() &&
                !activeEnginePackage.equals(cleanPreferred, ignoreCase = true)

            val systemDefaultChanged = cleanPreferred.isBlank() &&
                isInitialized &&
                activeEnginePackage.isNotBlank() &&
                run {
                    val sysDef = platformProvider.getSystemDefaultEnginePackage(appCtx)
                    sysDef.isNotBlank() && !sysDef.equals(activeEnginePackage, ignoreCase = true)
                }

            needsInit = forceReinitialize || needsSwitch || systemDefaultChanged || (!isInitialized && !isInitializing)
            if (needsInit) {
                activeAttemptGeneration += 1
                generation = activeAttemptGeneration
                isInitializing = true
                isInitialized = false
                recoveryAttemptedForCurrentError = false
                shutdownAdapterLocked()
            } else {
                generation = activeAttemptGeneration
            }
        }

        if (needsInit) {
            runEngineDiscoveryAndInit(
                appCtx = appCtx,
                generation = generation,
                preferredEngine = cleanPreferred,
                languageTag = cleanLang
            )
        } else if (isInitialized) {
            refreshLanguageCheck(cleanLang)
        }
        return _status.value
    }

    /**
     * Called when returning to CTECS (Activity ON_RESUME) or when the user taps "Check Again".
     * If Voice Output is OFF, stops speech and does NOT unnecessarily initialize TTS.
     */
    fun revalidateOnResume(
        context: Context,
        voiceOutputEnabled: Boolean,
        preferredEngine: String = preferredEnginePackage,
        languageTag: String = requestedLanguageTag,
        forceReinitialize: Boolean = false
    ) {
        if (!voiceOutputEnabled) {
            stopSpeaking()
            return
        }
        val shouldForce = forceReinitialize ||
            !_status.value.isReady ||
            _status.value.status == TtsAvailabilityStatus.ENGINE_DISCONNECTED
        initializeIfNeeded(
            context = context,
            preferredEngine = preferredEngine,
            languageTag = languageTag,
            forceReinitialize = shouldForce
        )
    }

    /**
     * Switches to a user-selected TTS engine (or empty string for Auto / System Default).
     */
    fun selectEngine(
        context: Context,
        enginePackage: String,
        languageTag: String = requestedLanguageTag
    ) {
        initializeIfNeeded(
            context = context,
            preferredEngine = enginePackage.trim(),
            languageTag = languageTag,
            forceReinitialize = true
        )
    }

    private fun runEngineDiscoveryAndInit(
        appCtx: Context,
        generation: Int,
        preferredEngine: String,
        languageTag: String
    ) {
        val (enabledEngines, disabledEngines) = platformProvider.discoverInstalledEngines(appCtx)
        val defaultPkg = platformProvider.getSystemDefaultEnginePackage(appCtx)
        val intentCaps = computeIntentCapabilities(appCtx, preferredEngine.ifBlank { defaultPkg })

        // Build prioritized fallback candidate list
        val candidates = buildCandidateEngineChain(
            enabledEngines = enabledEngines,
            preferredEngine = preferredEngine,
            defaultEngine = defaultPkg
        )

        if (candidates.isEmpty()) {
            // If PackageManager returned disabled engines only -> accurate ENGINE_DISABLED state
            if (disabledEngines.isNotEmpty()) {
                synchronized(lock) {
                    if (generation != activeAttemptGeneration) return
                    isInitializing = false
                    isInitialized = false
                    pendingQueue.clear()
                }
                val disabledNames = disabledEngines.joinToString(", ") { it.label }
                _status.value = TtsStatusSnapshot(
                    status = TtsAvailabilityStatus.ENGINE_DISABLED,
                    installedEngines = emptyList(),
                    disabledEngines = disabledEngines,
                    defaultEnginePackage = defaultPkg,
                    preferredEnginePackage = preferredEngine,
                    statusHeadline = "Text-to-Speech engine is disabled",
                    statusDetail = "$disabledNames is disabled on this device. Open Voice Engine Settings to enable it, then tap Check Again. Visual destination confirmation remains active.",
                    canOpenTtsSettings = intentCaps.first,
                    canInstallVoiceData = intentCaps.second,
                    canInstallGoogleSpeechServices = intentCaps.third,
                    lastCheckedAtMs = System.currentTimeMillis()
                )
                return
            }

            // Even if queryIntentServices returned empty (rare OEM package visibility quirk),
            // attempt ONE bare default system bind before concluding NO_ENGINE_INSTALLED.
            tryCandidateAtIndex(
                appCtx = appCtx,
                generation = generation,
                candidates = listOf(""),
                index = 0,
                enabledEngines = enabledEngines,
                disabledEngines = disabledEngines,
                defaultPkg = defaultPkg,
                preferredEngine = preferredEngine,
                languageTag = languageTag,
                wasEmptyDiscovery = true
            )
            return
        }

        tryCandidateAtIndex(
            appCtx = appCtx,
            generation = generation,
            candidates = candidates,
            index = 0,
            enabledEngines = enabledEngines,
            disabledEngines = disabledEngines,
            defaultPkg = defaultPkg,
            preferredEngine = preferredEngine,
            languageTag = languageTag,
            wasEmptyDiscovery = false
        )
    }

    private fun tryCandidateAtIndex(
        appCtx: Context,
        generation: Int,
        candidates: List<String>,
        index: Int,
        enabledEngines: List<TtsEngineInfo>,
        disabledEngines: List<TtsEngineInfo>,
        defaultPkg: String,
        preferredEngine: String,
        languageTag: String,
        wasEmptyDiscovery: Boolean
    ) {
        synchronized(lock) {
            if (generation != activeAttemptGeneration) return
        }

        val candidatePkg = candidates[index]
        val adapter: TtsEngineAdapter = try {
            platformProvider.createEngine(
                context = appCtx,
                enginePackage = candidatePkg.ifBlank { null }
            ) { initializedAdapter, statusCode ->
                handleCandidateInitResult(
                    appCtx = appCtx,
                    generation = generation,
                    adapter = initializedAdapter,
                    statusCode = statusCode,
                    candidates = candidates,
                    index = index,
                    enabledEngines = enabledEngines,
                    disabledEngines = disabledEngines,
                    defaultPkg = defaultPkg,
                    preferredEngine = preferredEngine,
                    languageTag = languageTag,
                    wasEmptyDiscovery = wasEmptyDiscovery
                )
            }
        } catch (_: Throwable) {
            advanceOrFailCandidate(
                appCtx = appCtx,
                generation = generation,
                candidates = candidates,
                failedIndex = index,
                enabledEngines = enabledEngines,
                disabledEngines = disabledEngines,
                defaultPkg = defaultPkg,
                preferredEngine = preferredEngine,
                languageTag = languageTag,
                wasEmptyDiscovery = wasEmptyDiscovery
            )
            return
        }

        synchronized(lock) {
            if (generation != activeAttemptGeneration) {
                adapter.shutdown()
                return
            }
            if (activeAdapter !== adapter) {
                activeAdapter?.let { prev ->
                    prev.shutdown()
                    activeInstanceCountInternal = (activeInstanceCountInternal - 1).coerceAtLeast(0)
                }
                activeAdapter = adapter
                activeInstanceCountInternal += 1
            }
        }
    }

    private fun handleCandidateInitResult(
        appCtx: Context,
        generation: Int,
        adapter: TtsEngineAdapter,
        statusCode: Int,
        candidates: List<String>,
        index: Int,
        enabledEngines: List<TtsEngineInfo>,
        disabledEngines: List<TtsEngineInfo>,
        defaultPkg: String,
        preferredEngine: String,
        languageTag: String,
        wasEmptyDiscovery: Boolean
    ) {
        val stale: Boolean
        synchronized(lock) {
            stale = (generation != activeAttemptGeneration)
        }
        if (stale) {
            adapter.shutdown()
            return
        }

        if (statusCode == TextToSpeech.SUCCESS) {
            val reportedEngines = adapter.getReportedEngines()
            val mergedEnabled = mergeEngineLists(enabledEngines, reportedEngines, defaultPkg.ifBlank { adapter.defaultEnginePackage })
            val resolvedActivePkg = candidates[index].ifBlank {
                adapter.defaultEnginePackage.ifBlank {
                    mergedEnabled.firstOrNull { it.isSystemDefault }?.packageName
                        ?: mergedEnabled.firstOrNull()?.packageName.orEmpty()
                }
            }
            val resolvedLabel = mergedEnabled.firstOrNull { it.packageName == resolvedActivePkg }?.label
                ?: friendlyEngineName(resolvedActivePkg)

            synchronized(lock) {
                if (generation != activeAttemptGeneration) {
                    adapter.shutdown()
                    return
                }
                if (activeAdapter !== adapter) {
                    activeAdapter?.let { prev ->
                        prev.shutdown()
                        activeInstanceCountInternal = (activeInstanceCountInternal - 1).coerceAtLeast(0)
                    }
                    activeAdapter = adapter
                    activeInstanceCountInternal += 1
                }
                isInitializing = false
                isInitialized = true
                activeEnginePackage = resolvedActivePkg
                recoveryAttemptedForCurrentError = false
            }

            attachAdapterListener(adapter)
            val (langs, voices) = collectInstalledLanguagesAndVoices(adapter)
            val langCheck = evaluateLanguageOnAdapter(adapter, resolvedLabel, languageTag)
            val effectiveStatus = when (langCheck.availability) {
                TtsLanguageAvailability.AVAILABLE -> TtsAvailabilityStatus.READY
                TtsLanguageAvailability.MISSING_DATA -> TtsAvailabilityStatus.LANGUAGE_MISSING_DATA
                TtsLanguageAvailability.NOT_SUPPORTED -> TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED
                TtsLanguageAvailability.ENGINE_NOT_READY -> TtsAvailabilityStatus.READY
            }
            val intentCaps = computeIntentCapabilities(appCtx, resolvedActivePkg)
            val fallbackNote = if (preferredEngine.isNotBlank() && !resolvedActivePkg.equals(preferredEngine, ignoreCase = true)) {
                "Switched automatically to $resolvedLabel because the previously selected engine could not initialize."
            } else if (effectiveStatus == TtsAvailabilityStatus.READY) {
                "Ready • $resolvedLabel (${langs.size} language${if (langs.size == 1) "" else "s"} available)"
            } else {
                langCheck.message
            }

            _status.value = TtsStatusSnapshot(
                status = effectiveStatus,
                installedEngines = mergedEnabled,
                disabledEngines = disabledEngines,
                defaultEnginePackage = defaultPkg.ifBlank { adapter.defaultEnginePackage },
                activeEnginePackage = resolvedActivePkg,
                activeEngineLabel = resolvedLabel,
                preferredEnginePackage = preferredEngine,
                availableLanguages = langs,
                availableVoices = voices,
                languageCheck = langCheck,
                statusHeadline = if (effectiveStatus == TtsAvailabilityStatus.READY) {
                    "Voice Engine Ready ($resolvedLabel)"
                } else {
                    langCheck.displayLanguageName + " voice data required"
                },
                statusDetail = fallbackNote,
                canOpenTtsSettings = intentCaps.first,
                canInstallVoiceData = intentCaps.second,
                canInstallGoogleSpeechServices = intentCaps.third,
                lastCheckedAtMs = System.currentTimeMillis()
            )

            drainPendingQueue()
        } else {
            // Tear down failed engine handle cleanly and try next compatible installed engine
            synchronized(lock) {
                if (activeAdapter === adapter) {
                    activeAdapter = null
                    activeInstanceCountInternal = (activeInstanceCountInternal - 1).coerceAtLeast(0)
                }
            }
            adapter.shutdown()
            advanceOrFailCandidate(
                appCtx = appCtx,
                generation = generation,
                candidates = candidates,
                failedIndex = index,
                enabledEngines = enabledEngines,
                disabledEngines = disabledEngines,
                defaultPkg = defaultPkg,
                preferredEngine = preferredEngine,
                languageTag = languageTag,
                wasEmptyDiscovery = wasEmptyDiscovery
            )
        }
    }

    private fun advanceOrFailCandidate(
        appCtx: Context,
        generation: Int,
        candidates: List<String>,
        failedIndex: Int,
        enabledEngines: List<TtsEngineInfo>,
        disabledEngines: List<TtsEngineInfo>,
        defaultPkg: String,
        preferredEngine: String,
        languageTag: String,
        wasEmptyDiscovery: Boolean
    ) {
        val nextIndex = failedIndex + 1
        if (nextIndex < candidates.size) {
            tryCandidateAtIndex(
                appCtx = appCtx,
                generation = generation,
                candidates = candidates,
                index = nextIndex,
                enabledEngines = enabledEngines,
                disabledEngines = disabledEngines,
                defaultPkg = defaultPkg,
                preferredEngine = preferredEngine,
                languageTag = languageTag,
                wasEmptyDiscovery = wasEmptyDiscovery
            )
            return
        }

        // All candidates exhausted
        synchronized(lock) {
            if (generation != activeAttemptGeneration) return
            isInitializing = false
            isInitialized = false
            activeEnginePackage = ""
            pendingQueue.clear()
        }

        val intentCaps = computeIntentCapabilities(appCtx, preferredEngine.ifBlank { defaultPkg })
        val finalStatus = when {
            wasEmptyDiscovery && disabledEngines.isEmpty() -> TtsAvailabilityStatus.NO_ENGINE_INSTALLED
            enabledEngines.isEmpty() && disabledEngines.isNotEmpty() -> TtsAvailabilityStatus.ENGINE_DISABLED
            enabledEngines.isEmpty() -> TtsAvailabilityStatus.NO_ENGINE_INSTALLED
            else -> TtsAvailabilityStatus.INIT_FAILED
        }
        val headline = when (finalStatus) {
            TtsAvailabilityStatus.NO_ENGINE_INSTALLED -> "No Text-to-Speech engine installed"
            TtsAvailabilityStatus.ENGINE_DISABLED -> "Text-to-Speech engine is disabled"
            else -> "Text-to-Speech engine needs configuration"
        }
        val detail = when (finalStatus) {
            TtsAvailabilityStatus.NO_ENGINE_INSTALLED ->
                "Install Google Speech Services or another compatible Android TTS engine to hear destination and KM Stone announcements. Visual destination confirmation remains active."
            TtsAvailabilityStatus.ENGINE_DISABLED ->
                "Enable an Android Text-to-Speech engine in system settings, then tap Check Again. Visual destination confirmation remains active."
            else ->
                "Installed TTS engine could not initialize. Open Voice Engine Settings to verify the default engine and voice data, then tap Check Again. Visual destination confirmation remains active."
        }

        _status.value = TtsStatusSnapshot(
            status = finalStatus,
            installedEngines = enabledEngines,
            disabledEngines = disabledEngines,
            defaultEnginePackage = defaultPkg,
            activeEnginePackage = "",
            activeEngineLabel = "",
            preferredEnginePackage = preferredEngine,
            availableLanguages = emptyList(),
            availableVoices = emptyList(),
            languageCheck = null,
            statusHeadline = headline,
            statusDetail = detail,
            canOpenTtsSettings = intentCaps.first,
            canInstallVoiceData = intentCaps.second,
            canInstallGoogleSpeechServices = intentCaps.third,
            lastCheckedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Evaluates whether [languageTag] (blank = system default) is available on the active engine
     * WITHOUT silently substituting another language.
     */
    fun checkLanguageAvailability(languageTag: String): TtsLanguageCheckResult {
        val adapter = synchronized(lock) { if (isInitialized) activeAdapter else null }
        val engineLabel = _status.value.activeEngineLabel.ifBlank { "TTS engine" }
        if (adapter == null) {
            val display = displayLanguageName(languageTag)
            return TtsLanguageCheckResult(
                requestedTag = languageTag,
                displayLanguageName = display,
                availability = TtsLanguageAvailability.ENGINE_NOT_READY,
                resolvedLocale = null,
                message = "Voice engine is not initialized yet."
            )
        }
        return evaluateLanguageOnAdapter(adapter, engineLabel, languageTag)
    }

    fun refreshLanguageCheck(languageTag: String) {
        val cleanTag = languageTag.trim()
        synchronized(lock) {
            requestedLanguageTag = cleanTag
        }
        val adapter = synchronized(lock) { if (isInitialized) activeAdapter else null } ?: return
        val cur = _status.value
        val engineLabel = cur.activeEngineLabel.ifBlank { "TTS engine" }
        val check = evaluateLanguageOnAdapter(adapter, engineLabel, cleanTag)
        val newStatus = when (check.availability) {
            TtsLanguageAvailability.AVAILABLE -> TtsAvailabilityStatus.READY
            TtsLanguageAvailability.MISSING_DATA -> TtsAvailabilityStatus.LANGUAGE_MISSING_DATA
            TtsLanguageAvailability.NOT_SUPPORTED -> TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED
            TtsLanguageAvailability.ENGINE_NOT_READY -> cur.status
        }
        _status.value = cur.copy(
            status = newStatus,
            languageCheck = check,
            statusHeadline = if (newStatus == TtsAvailabilityStatus.READY) {
                "Voice Engine Ready ($engineLabel)"
            } else {
                "${check.displayLanguageName} voice data needed"
            },
            statusDetail = if (newStatus == TtsAvailabilityStatus.READY) {
                "Ready • $engineLabel (${cur.availableLanguages.size} language${if (cur.availableLanguages.size == 1) "" else "s"} available)"
            } else {
                check.message
            },
            lastCheckedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Returns installed voices filtered for [languageTag] (blank = all installed voices).
     */
    fun getVoicesForLanguage(languageTag: String): List<KmStoneVoiceOption> {
        val all = _status.value.availableVoices
        if (languageTag.isBlank()) return all
        val prefix = languageTag.substringBefore('-').lowercase(Locale.ROOT)
        val exact = all.filter { it.languageTag.equals(languageTag, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return all.filter { it.languageTag.lowercase(Locale.ROOT).startsWith(prefix) }
    }

    /**
     * Speaks [request] via the single shared TTS engine with smart priority queueing,
     * language validation (never silently substituting a wrong language), and disconnect recovery.
     */
    fun speak(
        context: Context,
        request: TtsSpeakRequest,
        initialDelayMs: Long = 0L
    ): TtsSpeakOutcome {
        val cleanText = request.text.trim()
        if (cleanText.isEmpty()) {
            return TtsSpeakOutcome(
                dispatched = false,
                status = _status.value.status,
                message = "Nothing to speak."
            )
        }

        val appCtx = context.applicationContext ?: context
        if (request.enginePackage.isNotBlank() &&
            !request.enginePackage.equals(preferredEnginePackage, ignoreCase = true)
        ) {
            initializeIfNeeded(
                context = appCtx,
                preferredEngine = request.enginePackage,
                languageTag = request.languageTag,
                forceReinitialize = true
            )
        } else if (!isInitialized && !isInitializing) {
            initializeIfNeeded(
                context = appCtx,
                preferredEngine = request.enginePackage.ifBlank { preferredEnginePackage },
                languageTag = request.languageTag,
                forceReinitialize = false
            )
        }

        // Priority rule: do not interrupt an active critical arrival announcement with routine speech
        if (request.priority == TtsSpeechPriority.ROUTINE && activeArrivalSpeaking) {
            return TtsSpeakOutcome(
                dispatched = false,
                status = _status.value.status,
                message = "Skipped routine callout while critical arrival announcement is active."
            )
        }

        val readyNow: Boolean
        val initializingNow: Boolean
        synchronized(lock) {
            readyNow = isInitialized && activeAdapter != null
            initializingNow = isInitializing
        }

        if (!readyNow) {
            if (initializingNow) {
                enqueuePendingRequest(request)
                return TtsSpeakOutcome(
                    dispatched = true,
                    queuedPendingInit = true,
                    status = TtsAvailabilityStatus.INITIALIZING,
                    message = "Voice engine initializing; announcement queued."
                )
            }
            val snap = _status.value
            return TtsSpeakOutcome(
                dispatched = false,
                status = snap.status,
                message = snap.statusDetail
            )
        }

        if (initialDelayMs > 0L) {
            delayedSpeakJob?.cancel()
            delayedSpeakJob = ttsScope.launch {
                delay(initialDelayMs)
                speakNowInternal(appCtx, request)
            }
            return TtsSpeakOutcome(
                dispatched = true,
                queuedPendingInit = false,
                status = _status.value.status,
                message = "Scheduled voice announcement.",
                engineLabel = _status.value.activeEngineLabel
            )
        } else {
            if (request.priority != TtsSpeechPriority.ROUTINE) {
                delayedSpeakJob?.cancel()
                delayedSpeakJob = null
            }
            return speakNowInternal(appCtx, request)
        }
    }

    /**
     * Runs a comprehensive live Voice Output test checking engine readiness, language & voice data,
     * audio output volume, speech speed, pitch, and engine disconnection recovery.
     */
    fun testVoiceOutput(
        context: Context,
        languageTag: String,
        voiceName: String,
        enginePackage: String,
        speechRate: Float,
        pitch: Float,
        volumePercent: Int,
        sampleText: String = TEST_VOICE_SAMPLE_TEXT
    ): TtsTestResult {
        val appCtx = context.applicationContext ?: context
        val snap = initializeBlockingForSync(
            context = appCtx,
            preferredEngine = enginePackage,
            languageTag = languageTag,
            forceReinitialize = !_status.value.isReady ||
                (enginePackage.isNotBlank() && !enginePackage.equals(activeEnginePackage, ignoreCase = true))
        )

        if (snap.status == TtsAvailabilityStatus.NO_ENGINE_INSTALLED ||
            snap.status == TtsAvailabilityStatus.ENGINE_DISABLED ||
            snap.status == TtsAvailabilityStatus.INIT_FAILED ||
            snap.status == TtsAvailabilityStatus.ENGINE_DISCONNECTED
        ) {
            return TtsTestResult(
                success = false,
                spokenText = sampleText,
                message = snap.statusDetail,
                status = snap.status,
                engineLabel = snap.activeEngineLabel
            )
        }

        val langCheck = checkLanguageAvailability(languageTag)
        if (langCheck.availability == TtsLanguageAvailability.MISSING_DATA) {
            refreshLanguageCheck(languageTag)
            return TtsTestResult(
                success = false,
                spokenText = sampleText,
                message = langCheck.message,
                status = TtsAvailabilityStatus.LANGUAGE_MISSING_DATA,
                engineLabel = snap.activeEngineLabel,
                languageLabel = langCheck.displayLanguageName
            )
        }
        if (langCheck.availability == TtsLanguageAvailability.NOT_SUPPORTED) {
            refreshLanguageCheck(languageTag)
            return TtsTestResult(
                success = false,
                spokenText = sampleText,
                message = langCheck.message,
                status = TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED,
                engineLabel = snap.activeEngineLabel,
                languageLabel = langCheck.displayLanguageName
            )
        }

        if (volumePercent <= 0 || platformProvider.isAudioOutputMuted(appCtx)) {
            return TtsTestResult(
                success = false,
                spokenText = sampleText,
                message = "Audio output is muted or volume is 0%. Raise media volume or Announcement Volume to hear voice output.",
                status = TtsAvailabilityStatus.AUDIO_OUTPUT_UNAVAILABLE,
                engineLabel = snap.activeEngineLabel,
                languageLabel = langCheck.displayLanguageName
            )
        }

        val outcome = speak(
            context = appCtx,
            request = TtsSpeakRequest(
                text = sampleText,
                languageTag = languageTag,
                voiceName = voiceName,
                enginePackage = enginePackage,
                speechRate = speechRate,
                pitch = pitch,
                volumePercent = volumePercent,
                priority = TtsSpeechPriority.USER_INTERACTIVE,
                repeatOnce = false
            )
        )

        return if (outcome.dispatched) {
            TtsTestResult(
                success = true,
                spokenText = sampleText,
                message = "Playing test voice on ${outcome.engineLabel.ifBlank { snap.activeEngineLabel }} (${langCheck.displayLanguageName}).",
                status = TtsAvailabilityStatus.READY,
                engineLabel = outcome.engineLabel.ifBlank { snap.activeEngineLabel },
                languageLabel = langCheck.displayLanguageName,
                voiceLabel = outcome.resolvedVoiceLabel
            )
        } else {
            TtsTestResult(
                success = false,
                spokenText = sampleText,
                message = outcome.message,
                status = outcome.status,
                engineLabel = snap.activeEngineLabel,
                languageLabel = langCheck.displayLanguageName
            )
        }
    }

    /**
     * Stops any ongoing speech immediately, clears pending queue, and abandons audio focus.
     */
    fun stopSpeaking() {
        delayedSpeakJob?.cancel()
        delayedSpeakJob = null
        synchronized(lock) {
            pendingQueue.clear()
            activeUtteranceIds.clear()
            activeArrivalSpeaking = false
        }
        try {
            activeAdapter?.stop()
        } catch (_: Throwable) {}
        abandonAudioFocus()
    }

    /**
     * Releases the shared TTS engine cleanly when no longer required (for example, when Voice Output
     * is explicitly disabled and no KM Stone voice alerts are active).
     */
    fun releaseResources() {
        stopSpeaking()
        initJob?.cancel()
        initJob = null
        synchronized(lock) {
            activeAttemptGeneration += 1
            isInitializing = false
            isInitialized = false
            activeEnginePackage = ""
            shutdownAdapterLocked()
        }
        val cur = _status.value
        _status.value = cur.copy(
            status = TtsAvailabilityStatus.NOT_INITIALIZED,
            activeEnginePackage = "",
            activeEngineLabel = "",
            statusHeadline = "Voice output is OFF",
            statusDetail = "Visual destination confirmation remains active.",
            lastCheckedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Resolves the safest supported Android Intent for installing or configuring a TTS engine
     * or missing language voice data. Never returns an unresolvable Intent.
     */
    fun resolveSetupIntent(
        context: Context,
        forMissingVoiceData: Boolean = false
    ): TtsSetupIntentResolution {
        val snap = _status.value
        val activePkg = snap.activeEnginePackage.ifBlank { snap.defaultEnginePackage }
        val candidates = mutableListOf<Triple<Intent, String, String>>()

        if (forMissingVoiceData) {
            if (activePkg.isNotBlank()) {
                candidates.add(
                    Triple(
                        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                            setPackage(activePkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        "Install Voice Data",
                        "Download voice data for ${snap.activeEngineLabel.ifBlank { "your TTS engine" }}"
                    )
                )
            }
            candidates.add(
                Triple(
                    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Install Voice Data",
                    "Download missing language voice data"
                )
            )
            candidates.add(
                Triple(
                    Intent("com.android.settings.TTS_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Open TTS Settings",
                    "Configure installed languages and voices"
                )
            )
        } else if (snap.status == TtsAvailabilityStatus.NO_ENGINE_INSTALLED) {
            candidates.add(
                Triple(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_SPEECH_SERVICES_PKG")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Install Google Speech Services",
                    "Open Play Store to install Speech Recognition & Synthesis by Google"
                )
            )
            candidates.add(
                Triple(
                    Intent("com.android.settings.TTS_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Open TTS Settings",
                    "Configure Android Text-to-Speech output"
                )
            )
            candidates.add(
                Triple(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_SPEECH_SERVICES_PKG")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Install Google Speech Services",
                    "Install Speech Recognition & Synthesis by Google"
                )
            )
        } else {
            candidates.add(
                Triple(
                    Intent("com.android.settings.TTS_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Configure Voice Engine",
                    "Open Android Text-to-Speech settings to select or enable an engine"
                )
            )
            candidates.add(
                Triple(
                    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Install Voice Data",
                    "Download or repair TTS voice data"
                )
            )
            candidates.add(
                Triple(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_SPEECH_SERVICES_PKG")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Install Google Speech Services",
                    "Install Speech Recognition & Synthesis by Google"
                )
            )
            candidates.add(
                Triple(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    "Open Device Settings",
                    "Open Accessibility settings to configure Text-to-Speech"
                )
            )
        }

        for ((intent, label, desc) in candidates) {
            if (platformProvider.canResolveIntent(context, intent)) {
                return TtsSetupIntentResolution(intent = intent, actionLabel = label, description = desc)
            }
        }

        return TtsSetupIntentResolution(
            intent = null,
            actionLabel = "Manual Setup Required",
            description = "This device does not expose an Android TTS settings screen directly. Open device Settings → System / Accessibility → Text-to-Speech output to install or enable Speech Services, then return to CTECS and tap Check Again."
        )
    }

    /**
     * Launches the verified setup intent safely. Returns a user-facing status message (never throws).
     */
    fun launchSetupFlow(context: Context, forMissingVoiceData: Boolean = false): String {
        val resolution = resolveSetupIntent(context, forMissingVoiceData = forMissingVoiceData)
        val intent = resolution.intent ?: return resolution.description
        return try {
            context.startActivity(intent)
            "${resolution.description}. Return to CTECS when finished."
        } catch (_: Throwable) {
            "Unable to open ${resolution.actionLabel} automatically. Open device Settings → Text-to-Speech output, then tap Check Again."
        }
    }

    // -------------------------------------------------------------------------
    // Internal Helpers
    // -------------------------------------------------------------------------

    private fun buildCandidateEngineChain(
        enabledEngines: List<TtsEngineInfo>,
        preferredEngine: String,
        defaultEngine: String
    ): List<String> {
        val ordered = LinkedHashSet<String>()
        val enabledByPkg = enabledEngines.associateBy { it.packageName }

        if (preferredEngine.isNotBlank() && enabledByPkg.containsKey(preferredEngine)) {
            ordered.add(preferredEngine)
        }
        if (defaultEngine.isNotBlank() && enabledByPkg.containsKey(defaultEngine)) {
            ordered.add(defaultEngine)
        }
        if (enabledByPkg.containsKey(GOOGLE_SPEECH_SERVICES_PKG)) {
            ordered.add(GOOGLE_SPEECH_SERVICES_PKG)
        }
        for (eng in enabledEngines) {
            ordered.add(eng.packageName)
        }
        return ordered.toList()
    }

    private fun mergeEngineLists(
        pmEngines: List<TtsEngineInfo>,
        reportedEngines: List<TtsEngineInfo>,
        defaultPkg: String
    ): List<TtsEngineInfo> {
        val byPkg = LinkedHashMap<String, TtsEngineInfo>()
        for (e in pmEngines) {
            byPkg[e.packageName] = e.copy(isSystemDefault = e.packageName == defaultPkg)
        }
        for (e in reportedEngines) {
            if (!byPkg.containsKey(e.packageName)) {
                byPkg[e.packageName] = e.copy(isSystemDefault = e.packageName == defaultPkg)
            }
        }
        return byPkg.values.sortedWith(
            compareByDescending<TtsEngineInfo> { it.isSystemDefault }
                .thenByDescending { it.packageName == GOOGLE_SPEECH_SERVICES_PKG }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
    }

    private fun computeIntentCapabilities(context: Context, enginePkg: String): Triple<Boolean, Boolean, Boolean> {
        val canTtsSettings = platformProvider.canResolveIntent(context, Intent("com.android.settings.TTS_SETTINGS"))
        val canInstallData = platformProvider.canResolveIntent(
            context,
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                if (enginePkg.isNotBlank()) setPackage(enginePkg)
            }
        ) || platformProvider.canResolveIntent(context, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        val canGoogleStore = platformProvider.canResolveIntent(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_SPEECH_SERVICES_PKG"))
        ) || platformProvider.canResolveIntent(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_SPEECH_SERVICES_PKG"))
        )
        return Triple(canTtsSettings, canInstallData, canGoogleStore)
    }

    private fun evaluateLanguageOnAdapter(
        adapter: TtsEngineAdapter,
        engineLabel: String,
        languageTag: String
    ): TtsLanguageCheckResult {
        val cleanTag = languageTag.trim()
        val targetLocale = if (cleanTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(cleanTag)
        val displayName = displayLanguageName(cleanTag)

        val code = adapter.isLanguageAvailable(targetLocale)
        if (code >= TextToSpeech.LANG_AVAILABLE) {
            return TtsLanguageCheckResult(
                requestedTag = cleanTag,
                displayLanguageName = displayName,
                availability = TtsLanguageAvailability.AVAILABLE,
                resolvedLocale = targetLocale,
                message = "$displayName is ready on $engineLabel."
            )
        }

        // If a regional variant (e.g. en-IN) is unavailable, check same base language ONLY (e.g. en),
        // NEVER substituting a different language (e.g. never substituting English for Hindi/Telugu).
        if (cleanTag.contains('-')) {
            val baseLocale = Locale(targetLocale.language)
            val baseCode = adapter.isLanguageAvailable(baseLocale)
            if (baseCode >= TextToSpeech.LANG_AVAILABLE) {
                return TtsLanguageCheckResult(
                    requestedTag = cleanTag,
                    displayLanguageName = displayName,
                    availability = TtsLanguageAvailability.AVAILABLE,
                    resolvedLocale = baseLocale,
                    message = "$displayName is ready on $engineLabel."
                )
            }
        }

        return if (code == TextToSpeech.LANG_MISSING_DATA) {
            TtsLanguageCheckResult(
                requestedTag = cleanTag,
                displayLanguageName = displayName,
                availability = TtsLanguageAvailability.MISSING_DATA,
                resolvedLocale = null,
                message = "Voice data for $displayName is missing on $engineLabel. Tap Install Voice Data or choose another supported language."
            )
        } else {
            TtsLanguageCheckResult(
                requestedTag = cleanTag,
                displayLanguageName = displayName,
                availability = TtsLanguageAvailability.NOT_SUPPORTED,
                resolvedLocale = null,
                message = "$displayName is not supported by $engineLabel. Switch TTS engine or select an installed language."
            )
        }
    }

    private fun displayLanguageName(languageTag: String): String {
        if (languageTag.isBlank()) {
            val def = Locale.getDefault().getDisplayName(Locale.getDefault())
            return "System Default ($def)"
        }
        val loc = Locale.forLanguageTag(languageTag)
        return loc.getDisplayName(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            .ifBlank { languageTag }
    }

    private fun collectInstalledLanguagesAndVoices(
        adapter: TtsEngineAdapter
    ): Pair<List<KmStoneLanguageOption>, List<KmStoneVoiceOption>> {
        val candidateLocales = LinkedHashMap<String, Locale>()
        for (loc in adapter.getAvailableLanguages()) {
            candidateLocales[loc.toLanguageTag()] = loc
        }
        // Explicitly probe core CTECS locales & device default because some OEM engines
        // omit valid locales from `availableLanguages` despite supporting them.
        val probed = KEY_PROBED_LOCALES + Locale.getDefault()
        for (loc in probed) {
            val tag = loc.toLanguageTag()
            if (!candidateLocales.containsKey(tag)) {
                candidateLocales[tag] = loc
            }
        }

        val supportedLocales = candidateLocales.values
            .filter { loc -> adapter.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE }
            .distinctBy { it.toLanguageTag() }
            .sortedBy { it.getDisplayName(Locale.getDefault()) }
            .map { loc ->
                KmStoneLanguageOption(
                    languageTag = loc.toLanguageTag(),
                    displayLabel = loc.getDisplayName(Locale.getDefault())
                        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                )
            }

        val allVoices = adapter.getVoices()
        val offlineFirst = allVoices.sortedWith(
            compareBy<TtsVoiceDescriptor> { it.isNetworkConnectionRequired }
                .thenBy { it.locale.getDisplayName(Locale.getDefault()) }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        )
        val voiceOptions = offlineFirst.mapIndexed { idx, voice ->
            val langDisplay = voice.locale.getDisplayName(Locale.getDefault())
            val shortName = voice.name
                .substringAfterLast('#')
                .substringBefore("-local")
                .ifBlank { "Voice ${idx + 1}" }
            val netSuffix = if (voice.isNetworkConnectionRequired) " (Online)" else ""
            KmStoneVoiceOption(
                name = voice.name,
                displayLabel = "$langDisplay • $shortName$netSuffix",
                languageTag = voice.locale.toLanguageTag()
            )
        }

        return supportedLocales to voiceOptions
    }

    private fun enqueuePendingRequest(request: TtsSpeakRequest) {
        synchronized(lock) {
            when (request.priority) {
                TtsSpeechPriority.CRITICAL_ARRIVAL -> {
                    pendingQueue.clear()
                    pendingQueue.addLast(request)
                }
                TtsSpeechPriority.USER_INTERACTIVE -> {
                    pendingQueue.removeAll { it.priority != TtsSpeechPriority.CRITICAL_ARRIVAL }
                    pendingQueue.addLast(request)
                }
                TtsSpeechPriority.ROUTINE -> {
                    if (pendingQueue.none { it.priority == TtsSpeechPriority.CRITICAL_ARRIVAL }) {
                        while (pendingQueue.size >= 2) {
                            pendingQueue.removeFirst()
                        }
                        pendingQueue.addLast(request)
                    }
                }
            }
        }
    }

    private fun drainPendingQueue() {
        val ctx = appContextRef ?: return
        val items = mutableListOf<TtsSpeakRequest>()
        synchronized(lock) {
            while (pendingQueue.isNotEmpty()) {
                items.add(pendingQueue.removeFirst())
            }
        }
        items.forEach { speakNowInternal(ctx, it) }
    }

    private fun speakNowInternal(context: Context, request: TtsSpeakRequest): TtsSpeakOutcome {
        val adapter = synchronized(lock) {
            if (isInitialized) activeAdapter else null
        } ?: return TtsSpeakOutcome(
            dispatched = false,
            status = _status.value.status,
            message = _status.value.statusDetail
        )

        val engineLabel = _status.value.activeEngineLabel.ifBlank { "Android TTS" }

        // Check requested language strictly — never silently substitute a wrong language
        val langCheck = evaluateLanguageOnAdapter(adapter, engineLabel, request.languageTag)
        if (langCheck.availability == TtsLanguageAvailability.MISSING_DATA) {
            refreshLanguageCheck(request.languageTag)
            return TtsSpeakOutcome(
                dispatched = false,
                status = TtsAvailabilityStatus.LANGUAGE_MISSING_DATA,
                message = langCheck.message,
                engineLabel = engineLabel
            )
        }
        if (langCheck.availability == TtsLanguageAvailability.NOT_SUPPORTED) {
            refreshLanguageCheck(request.languageTag)
            return TtsSpeakOutcome(
                dispatched = false,
                status = TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED,
                message = langCheck.message,
                engineLabel = engineLabel
            )
        }

        val targetLocale = langCheck.resolvedLocale ?: Locale.getDefault()
        val setLangResult = adapter.setLanguage(targetLocale)
        if (setLangResult == TextToSpeech.LANG_MISSING_DATA || setLangResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            refreshLanguageCheck(request.languageTag)
            val failStatus = if (setLangResult == TextToSpeech.LANG_MISSING_DATA) {
                TtsAvailabilityStatus.LANGUAGE_MISSING_DATA
            } else {
                TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED
            }
            return TtsSpeakOutcome(
                dispatched = false,
                status = failStatus,
                message = langCheck.message,
                engineLabel = engineLabel
            )
        }

        adapter.setSpeechRate(request.speechRate.coerceIn(0.5f, 2.0f))
        adapter.setPitch(request.pitch.coerceIn(0.6f, 1.5f))
        adapter.setNavigationAudioAttributes()

        var resolvedVoiceLabel = "System Default Voice"
        if (request.voiceName.isNotBlank()) {
            val applied = adapter.setVoiceByName(request.voiceName)
            if (applied) {
                resolvedVoiceLabel = _status.value.availableVoices
                    .firstOrNull { it.name == request.voiceName }?.displayLabel
                    ?: request.voiceName.substringAfterLast('#')
            }
        }

        requestTransientDuckingAudioFocus(context)

        val queueMode = when (request.priority) {
            TtsSpeechPriority.CRITICAL_ARRIVAL -> {
                synchronized(lock) {
                    activeUtteranceIds.clear()
                    activeArrivalSpeaking = true
                }
                TextToSpeech.QUEUE_FLUSH
            }
            TtsSpeechPriority.USER_INTERACTIVE -> {
                synchronized(lock) {
                    activeUtteranceIds.clear()
                    activeArrivalSpeaking = false
                }
                TextToSpeech.QUEUE_FLUSH
            }
            TtsSpeechPriority.ROUTINE -> {
                if (activeArrivalSpeaking) {
                    return TtsSpeakOutcome(
                        dispatched = false,
                        status = TtsAvailabilityStatus.READY,
                        message = "Skipped routine announcement while arrival speech is active.",
                        engineLabel = engineLabel
                    )
                }
                TextToSpeech.QUEUE_FLUSH
            }
        }

        val volumeGain = request.volumePercent.coerceIn(0, 100) / 100f
        val firstId = "ctecs_tts_${UUID.randomUUID()}"
        synchronized(lock) {
            activeUtteranceIds.add(firstId)
        }

        val speakResult = adapter.speak(request.text, queueMode, volumeGain, firstId)
        if (speakResult == TextToSpeech.ERROR) {
            handleEngineDisconnect(context, request)
            return TtsSpeakOutcome(
                dispatched = false,
                status = TtsAvailabilityStatus.ENGINE_DISCONNECTED,
                message = "Voice engine disconnected while speaking. Re-initializing automatically…",
                engineLabel = engineLabel
            )
        }

        if (request.repeatOnce) {
            val pauseId = "ctecs_tts_pause_${UUID.randomUUID()}"
            val repeatId = "ctecs_tts_repeat_${UUID.randomUUID()}"
            synchronized(lock) {
                activeUtteranceIds.add(pauseId)
                activeUtteranceIds.add(repeatId)
            }
            adapter.playSilent(420L, TextToSpeech.QUEUE_ADD, pauseId)
            adapter.speak(request.text, TextToSpeech.QUEUE_ADD, volumeGain, repeatId)
        }

        return TtsSpeakOutcome(
            dispatched = true,
            queuedPendingInit = false,
            status = TtsAvailabilityStatus.READY,
            message = "Spoken via $engineLabel",
            engineLabel = engineLabel,
            resolvedLanguageTag = targetLocale.toLanguageTag(),
            resolvedVoiceLabel = resolvedVoiceLabel
        )
    }

    private fun handleEngineDisconnect(context: Context, failedRequest: TtsSpeakRequest?) {
        val shouldRecover: Boolean
        synchronized(lock) {
            activeUtteranceIds.clear()
            activeArrivalSpeaking = false
            shouldRecover = !recoveryAttemptedForCurrentError
            recoveryAttemptedForCurrentError = true
            isInitialized = false
            shutdownAdapterLocked()
        }
        abandonAudioFocus()

        val cur = _status.value
        _status.value = cur.copy(
            status = TtsAvailabilityStatus.ENGINE_DISCONNECTED,
            statusHeadline = "Voice engine disconnected",
            statusDetail = "The Android Text-to-Speech service disconnected. Attempting automatic recovery — tap Check Again if speech does not resume.",
            lastCheckedAtMs = System.currentTimeMillis()
        )

        if (shouldRecover) {
            if (failedRequest != null) {
                enqueuePendingRequest(failedRequest)
            }
            initializeIfNeeded(
                context = context,
                preferredEngine = preferredEnginePackage,
                languageTag = failedRequest?.languageTag ?: requestedLanguageTag,
                forceReinitialize = true
            )
        }
    }

    private fun attachAdapterListener(adapter: TtsEngineAdapter) {
        adapter.setUtteranceListener(
            onStart = {},
            onDone = { utteranceId -> handleUtteranceFinished(utteranceId) },
            onError = { utteranceId, errorCode ->
                handleUtteranceFinished(utteranceId)
                if (errorCode == TextToSpeech.ERROR_SERVICE || errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                    appContextRef?.let { ctx -> handleEngineDisconnect(ctx, null) }
                }
            },
            onStop = { utteranceId -> handleUtteranceFinished(utteranceId) }
        )
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

    private fun shutdownAdapterLocked() {
        val existing = activeAdapter
        activeAdapter = null
        if (existing != null) {
            activeInstanceCountInternal = (activeInstanceCountInternal - 1).coerceAtLeast(0)
            try {
                existing.shutdown()
            } catch (_: Throwable) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun requestTransientDuckingAudioFocus(context: Context) {
        val audioMgr = try {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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

    // -------------------------------------------------------------------------
    // Deterministic Testing Hooks
    // -------------------------------------------------------------------------

    fun setPlatformProviderForTesting(provider: TtsPlatformProvider) {
        resetForTesting()
        platformProvider = provider
    }

    fun resetForTesting() {
        delayedSpeakJob?.cancel()
        delayedSpeakJob = null
        initJob?.cancel()
        initJob = null
        synchronized(lock) {
            activeAttemptGeneration += 1
            pendingQueue.clear()
            activeUtteranceIds.clear()
            activeArrivalSpeaking = false
            isInitializing = false
            isInitialized = false
            activeEnginePackage = ""
            preferredEnginePackage = ""
            requestedLanguageTag = ""
            recoveryAttemptedForCurrentError = false
            shutdownAdapterLocked()
            activeInstanceCountInternal = 0
        }
        platformProvider = AndroidTtsPlatformProvider
        _status.value = TtsStatusSnapshot()
    }
}
