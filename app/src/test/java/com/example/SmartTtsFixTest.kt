package com.example

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.ChainageVoiceLanguage
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVoiceAnnouncementStyle
import com.example.model.KmVoiceSpeed
import com.example.navigation.ChainageVoiceAssistantManager
import com.example.navigation.KmStoneAlertEvent
import com.example.navigation.KmStoneAlertKind
import com.example.navigation.KmStoneVoiceAnnouncementManager
import com.example.navigation.SmartTtsManager
import com.example.navigation.TtsAvailabilityStatus
import com.example.navigation.TtsEngineAdapter
import com.example.navigation.TtsEngineInfo
import com.example.navigation.TtsLanguageAvailability
import com.example.navigation.TtsPlatformProvider
import com.example.navigation.TtsSpeakRequest
import com.example.navigation.TtsSpeechPriority
import com.example.navigation.TtsVoiceDescriptor
import com.example.ui.settings.ChainageVoiceAssistantSettingsSectionContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive 12-scenario Robolectric verification suite for CTECS Android TTS fix:
 * automatic engine detection, fallback, disabled/missing engine setup & recovery, language data
 * validation without silent wrong-language substitution, Voice Output ON/OFF state management,
 * engine switching, persistence & revalidation, KM Stone priority, and single-instance guarantee.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartTtsFixTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KmStoneVoiceAnnouncementManager.resetTestState()
    }

    @After
    fun tearDown() {
        KmStoneVoiceAnnouncementManager.resetTestState()
    }

    // -------------------------------------------------------------------------
    // Deterministic Fake TTS Engine & Platform Provider
    // -------------------------------------------------------------------------

    private data class SpokenCall(
        val enginePkg: String,
        val text: String,
        val queueMode: Int,
        val rate: Float,
        val pitch: Float,
        val volumeGain: Float,
        val localeTag: String,
        val voiceName: String
    )

    private class FakeTtsEngineAdapter(
        override val requestedEnginePackage: String,
        override val defaultEnginePackage: String,
        private val reportedEngines: List<TtsEngineInfo>,
        private val languageStatuses: Map<String, Int>,
        private val voices: List<TtsVoiceDescriptor>,
        private val spokenSink: CopyOnWriteArrayList<SpokenCall>,
        var failNextSpeakWithDisconnect: Boolean = false
    ) : TtsEngineAdapter {

        var isShutdown: Boolean = false
        var lastRate: Float = 1.0f
        var lastPitch: Float = 1.0f
        var lastLocale: Locale = Locale.US
        var lastVoiceName: String = ""
        private var onDoneCb: ((String?) -> Unit)? = null
        private var onErrorCb: ((String?, Int) -> Unit)? = null
        private val activeUtterances = CopyOnWriteArrayList<String>()

        override fun getReportedEngines(): List<TtsEngineInfo> = reportedEngines

        override fun getAvailableLanguages(): Set<Locale> =
            languageStatuses.filterValues { it >= TextToSpeech.LANG_AVAILABLE }
                .keys
                .map { Locale.forLanguageTag(it) }
                .toSet()

        override fun getVoices(): List<TtsVoiceDescriptor> = voices

        override fun isLanguageAvailable(locale: Locale): Int {
            val exact = languageStatuses[locale.toLanguageTag()]
            if (exact != null) return exact
            val base = languageStatuses[locale.language]
            if (base != null) return base
            return TextToSpeech.LANG_NOT_SUPPORTED
        }

        override fun setLanguage(locale: Locale): Int {
            val status = isLanguageAvailable(locale)
            if (status >= TextToSpeech.LANG_AVAILABLE) {
                lastLocale = locale
            }
            return status
        }

        override fun setVoiceByName(voiceName: String): Boolean {
            if (voices.any { it.name == voiceName }) {
                lastVoiceName = voiceName
                return true
            }
            return false
        }

        override fun setSpeechRate(rate: Float): Int {
            lastRate = rate
            return TextToSpeech.SUCCESS
        }

        override fun setPitch(pitch: Float): Int {
            lastPitch = pitch
            return TextToSpeech.SUCCESS
        }

        override fun setNavigationAudioAttributes() {}

        override fun setUtteranceListener(
            onStart: (String?) -> Unit,
            onDone: (String?) -> Unit,
            onError: (String?, Int) -> Unit,
            onStop: (String?) -> Unit
        ) {
            onDoneCb = onDone
            onErrorCb = onError
        }

        override fun speak(text: String, queueMode: Int, volumeGain: Float, utteranceId: String): Int {
            if (failNextSpeakWithDisconnect) {
                failNextSpeakWithDisconnect = false
                return TextToSpeech.ERROR
            }
            activeUtterances.add(utteranceId)
            spokenSink.add(
                SpokenCall(
                    enginePkg = requestedEnginePackage.ifBlank { defaultEnginePackage },
                    text = text,
                    queueMode = queueMode,
                    rate = lastRate,
                    pitch = lastPitch,
                    volumeGain = volumeGain,
                    localeTag = lastLocale.toLanguageTag(),
                    voiceName = lastVoiceName
                )
            )
            return TextToSpeech.SUCCESS
        }

        override fun playSilent(durationMs: Long, queueMode: Int, utteranceId: String): Int =
            TextToSpeech.SUCCESS

        override fun stop() {
            activeUtterances.clear()
        }

        override fun shutdown() {
            isShutdown = true
            activeUtterances.clear()
        }

        fun finishAllUtterances() {
            val copy = activeUtterances.toList()
            activeUtterances.clear()
            copy.forEach { onDoneCb?.invoke(it) }
        }
    }

    private class FakeTtsPlatformProvider(
        var enabledEngines: List<TtsEngineInfo> = emptyList(),
        var disabledEngines: List<TtsEngineInfo> = emptyList(),
        var defaultEnginePkg: String = "",
        var failingEnginePackages: Set<String> = emptySet(),
        var languageStatusesByEngine: MutableMap<String, Map<String, Int>> = mutableMapOf(),
        var voicesByEngine: MutableMap<String, List<TtsVoiceDescriptor>> = mutableMapOf(),
        var audioMuted: Boolean = false,
        var resolvableActions: Set<String> = setOf(
            "com.android.settings.TTS_SETTINGS",
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA,
            Intent.ACTION_VIEW
        ),
        var synchronousInit: Boolean = true
    ) : TtsPlatformProvider {

        val createCount = AtomicInteger(0)
        val createdAdapters = CopyOnWriteArrayList<FakeTtsEngineAdapter>()
        val spokenCalls = CopyOnWriteArrayList<SpokenCall>()

        override fun discoverInstalledEngines(context: Context): Pair<List<TtsEngineInfo>, List<TtsEngineInfo>> =
            enabledEngines to disabledEngines

        override fun getSystemDefaultEnginePackage(context: Context): String = defaultEnginePkg

        override fun createEngine(
            context: Context,
            enginePackage: String?,
            onInit: (TtsEngineAdapter, Int) -> Unit
        ): TtsEngineAdapter {
            createCount.incrementAndGet()
            val targetPkg = enginePackage?.takeIf { it.isNotBlank() } ?: defaultEnginePkg
            val langs = languageStatusesByEngine[targetPkg]
                ?: mapOf(
                    "en-IN" to TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    "en-US" to TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    "en" to TextToSpeech.LANG_AVAILABLE,
                    "hi-IN" to TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    "te-IN" to TextToSpeech.LANG_COUNTRY_AVAILABLE
                )
            val voices = voicesByEngine[targetPkg]
                ?: listOf(
                    TtsVoiceDescriptor("en-in-x-end-local", Locale.forLanguageTag("en-IN")),
                    TtsVoiceDescriptor("hi-in-x-hia-local", Locale.forLanguageTag("hi-IN")),
                    TtsVoiceDescriptor("te-in-x-tem-local", Locale.forLanguageTag("te-IN"))
                )
            val adapter = FakeTtsEngineAdapter(
                requestedEnginePackage = targetPkg,
                defaultEnginePackage = defaultEnginePkg,
                reportedEngines = enabledEngines,
                languageStatuses = langs,
                voices = voices,
                spokenSink = spokenCalls
            )
            createdAdapters.add(adapter)
            val shouldFail = targetPkg.isBlank() && enabledEngines.isEmpty() ||
                targetPkg in failingEnginePackages
            if (synchronousInit) {
                onInit(adapter, if (shouldFail) TextToSpeech.ERROR else TextToSpeech.SUCCESS)
            }
            return adapter
        }

        override fun isAudioOutputMuted(context: Context): Boolean = audioMuted

        override fun canResolveIntent(context: Context, intent: Intent): Boolean {
            return intent.action in resolvableActions
        }
    }

    private fun googleEngineInfo(isDefault: Boolean = true, isEnabled: Boolean = true) = TtsEngineInfo(
        packageName = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG,
        label = "Google Speech Services",
        isSystemDefault = isDefault,
        isEnabled = isEnabled
    )

    private fun samsungEngineInfo(isDefault: Boolean = false, isEnabled: Boolean = true) = TtsEngineInfo(
        packageName = "com.samsung.SMT",
        label = "Samsung Text-to-Speech",
        isSystemDefault = isDefault,
        isEnabled = isEnabled
    )

    // -------------------------------------------------------------------------
    // Scenario 1: Device with Google Speech Services installed
    // -------------------------------------------------------------------------

    @Test
    fun `scenario 1 - detects Google Speech Services, initializes automatically, and passes Test Voice Output with pitch and speed`() {
        val fake = FakeTtsPlatformProvider(
            enabledEngines = listOf(googleEngineInfo(isDefault = true)),
            defaultEnginePkg = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)

        val snap = SmartTtsManager.initializeBlockingForSync(context, preferredEngine = "", languageTag = "en-IN")
        assertEquals(TtsAvailabilityStatus.READY, snap.status)
        assertEquals(SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG, snap.activeEnginePackage)
        assertEquals("Google Speech Services", snap.activeEngineLabel)
        assertTrue(snap.availableLanguages.any { it.languageTag == "en-IN" })
        assertTrue(snap.availableLanguages.any { it.languageTag == "hi-IN" })
        assertTrue(snap.availableLanguages.any { it.languageTag == "te-IN" })
        assertEquals(1, SmartTtsManager.activeInstanceCount)

        val settings = ChainageVoiceAssistantSettings(
            voiceOutputEnabled = true,
            voiceLanguage = ChainageVoiceLanguage.ENGLISH_INDIA,
            ttsVoiceName = "en-in-x-end-local",
            speechSpeed = KmVoiceSpeed.FAST,
            speechPitch = 1.15f,
            volumePercent = 85
        )
        val testRes = ChainageVoiceAssistantManager.testVoiceOutputDetailed(context, settings)
        assertTrue(testRes.success)
        assertEquals(SmartTtsManager.TEST_VOICE_SAMPLE_TEXT, testRes.spokenText)
        assertEquals(1, fake.spokenCalls.size)
        val spoken = fake.spokenCalls.single()
        assertEquals(KmVoiceSpeed.FAST.speechRate, spoken.rate, 0.01f)
        assertEquals(1.15f, spoken.pitch, 0.01f)
        assertEquals("en-in-x-end-local", spoken.voiceName)
        assertEquals(0.85f, spoken.volumeGain, 0.01f)
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Device with another compatible TTS engine & automatic fallback
    // -------------------------------------------------------------------------

    @Test
    fun `scenario 2 - falls back automatically to another compatible TTS engine when default fails`() {
        val brokenDefault = TtsEngineInfo(
            packageName = "com.vendor.broken.tts",
            label = "Broken OEM TTS",
            isSystemDefault = true,
            isEnabled = true
        )
        val samsung = samsungEngineInfo(isDefault = false)
        val fake = FakeTtsPlatformProvider(
            enabledEngines = listOf(brokenDefault, samsung),
            defaultEnginePkg = brokenDefault.packageName,
            failingEnginePackages = setOf(brokenDefault.packageName)
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)

        val snap = SmartTtsManager.initializeBlockingForSync(context)
        assertEquals(TtsAvailabilityStatus.READY, snap.status)
        assertEquals("com.samsung.SMT", snap.activeEnginePackage)
        assertEquals("Samsung Text-to-Speech", snap.activeEngineLabel)
        // Broken adapter must have been shut down cleanly; only 1 active engine remains
        assertTrue(fake.createdAdapters.first().isShutdown)
        assertEquals(1, SmartTtsManager.activeInstanceCount)
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Device without a TTS engine
    // -------------------------------------------------------------------------

    @Test
    fun `scenario 3 - device without TTS engine reports NO_ENGINE_INSTALLED with safe setup intent and keeps UI actionable`() {
        val fake = FakeTtsPlatformProvider(
            enabledEngines = emptyList(),
            disabledEngines = emptyList(),
            defaultEnginePkg = ""
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)

        val snap = SmartTtsManager.initializeBlockingForSync(context)
        assertEquals(TtsAvailabilityStatus.NO_ENGINE_INSTALLED, snap.status)
        assertFalse(snap.isReady)

        // Resolves Play Store install intent when supported
        val setup = SmartTtsManager.resolveSetupIntent(context, forMissingVoiceData = false)
        assertNotNull(setup.intent)
        assertTrue(setup.actionLabel.contains("Google Speech Services"))

        // When no Android settings/store intents exist on a stripped ROM, returns null intent + manual text (never crashes)
        fake.resolvableActions = emptySet()
        val fallbackSetup = SmartTtsManager.resolveSetupIntent(context, forMissingVoiceData = false)
        assertNull(fallbackSetup.intent)
        assertTrue(fallbackSetup.description.contains("Text-to-Speech"))

        // UI renders Install / Configure Voice Engine & Check Again buttons
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ChainageVoiceAssistantSettingsSectionContent(
                        settings = ChainageVoiceAssistantSettings(voiceOutputEnabled = true),
                        accentColor = Color(0xFF38BDF8),
                        onUpdateSettings = {},
                        onResetDefaults = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tts_status_card_setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cva_install_configure_tts_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cva_check_tts_again_btn").performScrollTo().assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Scenario 4 & 6: Engine disabled -> enabled, and Engine installed -> return to CTECS
    // -------------------------------------------------------------------------

    @Test
    fun `scenarios 4 and 6 - disabled or missing engine recovers automatically upon enable or install and return to CTECS`() {
        // Start with disabled Google Speech Services
        val fake = FakeTtsPlatformProvider(
            enabledEngines = emptyList(),
            disabledEngines = listOf(googleEngineInfo(isDefault = true, isEnabled = false)),
            defaultEnginePkg = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)

        val disabledSnap = SmartTtsManager.initializeBlockingForSync(context)
        assertEquals(TtsAvailabilityStatus.ENGINE_DISABLED, disabledSnap.status)

        // User enables Google Speech Services in Android Settings and returns to CTECS
        fake.disabledEngines = emptyList()
        fake.enabledEngines = listOf(googleEngineInfo(isDefault = true, isEnabled = true))

        val recoveredSnap = SmartTtsManager.initializeBlockingForSync(context, forceReinitialize = true)
        assertEquals(TtsAvailabilityStatus.READY, recoveredSnap.status)
        assertEquals("Google Speech Services", recoveredSnap.activeEngineLabel)

        // Simulate missing engine -> user installs engine -> returns to CTECS -> revalidateOnResume recovers
        SmartTtsManager.resetForTesting()
        val installFake = FakeTtsPlatformProvider(enabledEngines = emptyList())
        SmartTtsManager.setPlatformProviderForTesting(installFake)
        assertEquals(TtsAvailabilityStatus.NO_ENGINE_INSTALLED, SmartTtsManager.initializeBlockingForSync(context).status)

        installFake.enabledEngines = listOf(googleEngineInfo(isDefault = true))
        installFake.defaultEnginePkg = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG
        val afterInstall = SmartTtsManager.initializeBlockingForSync(context, forceReinitialize = true)
        assertEquals(TtsAvailabilityStatus.READY, afterInstall.status)
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Missing Hindi, Telugu, or English voice data (no silent wrong language)
    // -------------------------------------------------------------------------

    @Test
    fun `scenario 5 - missing Hindi or Telugu voice data reports LANGUAGE_MISSING_DATA without silently substituting English`() {
        val fake = FakeTtsPlatformProvider(
            enabledEngines = listOf(googleEngineInfo(isDefault = true)),
            defaultEnginePkg = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG,
            languageStatusesByEngine = mutableMapOf(
                SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG to mapOf(
                    "en-US" to TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    "en" to TextToSpeech.LANG_AVAILABLE,
                    "hi-IN" to TextToSpeech.LANG_MISSING_DATA,
                    "te-IN" to TextToSpeech.LANG_MISSING_DATA
                )
            )
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)
        SmartTtsManager.initializeBlockingForSync(context, languageTag = "hi-IN")

        val hiCheck = SmartTtsManager.checkLanguageAvailability("hi-IN")
        assertEquals(TtsLanguageAvailability.MISSING_DATA, hiCheck.availability)

        val teCheck = SmartTtsManager.checkLanguageAvailability("te-IN")
        assertEquals(TtsLanguageAvailability.MISSING_DATA, teCheck.availability)

        // Attempting to speak Hindi must fail clearly and MUST NOT silently speak in English
        val hiTest = ChainageVoiceAssistantManager.testVoiceOutputDetailed(
            context,
            ChainageVoiceAssistantSettings(voiceLanguage = ChainageVoiceLanguage.HINDI)
        )
        assertFalse(hiTest.success)
        assertEquals(TtsAvailabilityStatus.LANGUAGE_MISSING_DATA, hiTest.status)
        assertEquals(0, fake.spokenCalls.size)

        // Setup intent targets ACTION_INSTALL_TTS_DATA for missing voice data
        val installData = SmartTtsManager.resolveSetupIntent(context, forMissingVoiceData = true)
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, installData.intent?.action)

        // Switching to installed English succeeds immediately
        val enTest = ChainageVoiceAssistantManager.testVoiceOutputDetailed(
            context,
            ChainageVoiceAssistantSettings(voiceLanguage = ChainageVoiceLanguage.ENGLISH_US)
        )
        assertTrue(enTest.success)
        assertEquals(1, fake.spokenCalls.size)
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Switching TTS engines cleanly
    // -------------------------------------------------------------------------

    @Test
    fun `scenario 7 - switching TTS engines shuts down prior engine and activates newly selected engine without duplicates`() {
        val google = googleEngineInfo(isDefault = true)
        val samsung = samsungEngineInfo(isDefault = false)
        val fake = FakeTtsPlatformProvider(
            enabledEngines = listOf(google, samsung),
            defaultEnginePkg = google.packageName
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)

        val s1 = SmartTtsManager.initializeBlockingForSync(context, preferredEngine = google.packageName)
        assertEquals(google.packageName, s1.activeEnginePackage)
        assertEquals(1, SmartTtsManager.activeInstanceCount)

        // Switch to Samsung TTS
        val s2 = SmartTtsManager.initializeBlockingForSync(context, preferredEngine = samsung.packageName, forceReinitialize = true)
        assertEquals(samsung.packageName, s2.activeEnginePackage)
        assertEquals("Samsung Text-to-Speech", s2.activeEngineLabel)
        assertTrue("Previous engine adapter must be shut down", fake.createdAdapters[0].isShutdown)
        assertEquals(1, SmartTtsManager.activeInstanceCount)

        // Also verify automatic re-detection when system default engine changes while CTECS is running
        fake.defaultEnginePkg = google.packageName
        val s3 = SmartTtsManager.initializeBlockingForSync(context, preferredEngine = "", forceReinitialize = false)
        assertEquals(google.packageName, s3.activeEnginePackage)
        assertEquals(1, SmartTtsManager.activeInstanceCount)
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Force-stop and restart persistence & revalidation
    // -------------------------------------------------------------------------

    @Test
    fun `scenario 8 - settings persist engine, language, voice, speed, pitch and revalidate availability on restart`() = runBlocking {
        val saved = ChainageVoiceAssistantSettings(
            voiceAssistantEnabled = true,
            voiceSearchEnabled = true,
            voiceLanguage = ChainageVoiceLanguage.ENGLISH_INDIA,
            customLanguageTag = "fr-FR",
            voiceOutputEnabled = true,
            ttsEnginePackage = "com.samsung.SMT",
            ttsVoiceName = "fr-fr-x-fra-local",
            speechSpeed = KmVoiceSpeed.SLOW,
            speechPitch = 1.25f,
            volumePercent = 80,
            voiceConfirmationBeforeNav = true,
            autoListenOnPopupOpen = false
        )
        val prefs = PreferencesManager(context)
        prefs.updateChainageVoiceAssistantSettings(saved)
        val restored = prefs.chainageVoiceAssistantSettingsFlow.first()
        assertEquals(saved, restored)
        assertEquals("fr-FR", restored.effectiveLanguageTag())
        assertEquals(1.25f, restored.effectiveSpeechPitch(), 0.001f)

        // Simulate app restart where previously persisted Samsung engine was uninstalled;
        // revalidation falls back cleanly to installed Google Speech Services instead of crashing.
        val fake = FakeTtsPlatformProvider(
            enabledEngines = listOf(googleEngineInfo(isDefault = true)),
            defaultEnginePkg = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)
        val snap = SmartTtsManager.initializeBlockingForSync(
            context,
            preferredEngine = restored.ttsEnginePackage,
            languageTag = "en-IN"
        )
        assertEquals(TtsAvailabilityStatus.READY, snap.status)
        assertEquals(SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG, snap.activeEnginePackage)
    }

    // -------------------------------------------------------------------------
    // Scenarios 9, 10, 11, 12: Repeated Announcements, KM Stone Priority, OFF/ON, Single Instance, Disconnect & Muted Audio
    // -------------------------------------------------------------------------

    @Test
    fun `scenarios 9, 10, 11, 12 - single shared TTS instance, arrival priority, Voice Output OFF suppression, muted audio and disconnect recovery`() {
        val fake = FakeTtsPlatformProvider(
            enabledEngines = listOf(googleEngineInfo(isDefault = true)),
            defaultEnginePkg = SmartTtsManager.GOOGLE_SPEECH_SERVICES_PKG
        )
        SmartTtsManager.setPlatformProviderForTesting(fake)

        // Scenario 11: Voice Output OFF does NOT unnecessarily initialize TTS
        var uiSettings by mutableStateOf(ChainageVoiceAssistantSettings(voiceOutputEnabled = false))
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ChainageVoiceAssistantSettingsSectionContent(
                        settings = uiSettings,
                        accentColor = Color(0xFF38BDF8),
                        onUpdateSettings = { uiSettings = it(uiSettings) },
                        onResetDefaults = { uiSettings = ChainageVoiceAssistantSettings.defaultSettings() }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("Voice Output OFF must not initialize TTS unnecessarily", 0, fake.createCount.get())
        composeRule.onNodeWithTag("tts_status_card_off").performScrollTo().assertIsDisplayed()

        // Initialize once via SmartTtsManager
        SmartTtsManager.initializeBlockingForSync(context, languageTag = "en-IN")
        assertEquals(1, fake.createCount.get())
        assertEquals(1, SmartTtsManager.activeInstanceCount)

        // Scenario 9: Repeated destination confirmations reuse the exact same engine & flush non-overlappingly
        val assistantSettings = ChainageVoiceAssistantSettings(
            voiceOutputEnabled = true,
            voiceLanguage = ChainageVoiceLanguage.ENGLISH_INDIA
        )
        ChainageVoiceAssistantManager.speakWithAssistantSettings(context, "Destination chainage 201 plus 500.", assistantSettings)
        ChainageVoiceAssistantManager.speakWithAssistantSettings(context, "Destination chainage 202 plus 000.", assistantSettings)
        assertEquals(1, fake.createCount.get())
        assertEquals(1, SmartTtsManager.activeInstanceCount)
        assertEquals(2, fake.spokenCalls.size)
        assertTrue(fake.spokenCalls.all { it.queueMode == TextToSpeech.QUEUE_FLUSH })

        // Scenario 10: KM Stone Arrival announcement prioritizes over routine distance announcements
        val kmSettings = KmStoneAlertSettings(
            enabled = true,
            voiceAnnouncementEnabled = true,
            voiceStyle = KmVoiceAnnouncementStyle.CHAINAGE,
            voiceLanguageTag = "en-IN",
            voiceAnnounceAtArrival = true
        )
        val arrivalEvent = KmStoneAlertEvent(
            kind = KmStoneAlertKind.ARRIVAL,
            stoneChainageMetres = 198_000L,
            stoneFormattedChainage = "198+000",
            currentChainageMetres = 198_000L,
            currentFormattedChainage = "198+000",
            directionSide = "LHS",
            distanceMetres = 0L,
            routeId = "r1",
            timestampMs = 1_000L
        )
        val routineEvent = KmStoneAlertEvent(
            kind = KmStoneAlertKind.VOICE_DISTANCE,
            stoneChainageMetres = 199_000L,
            stoneFormattedChainage = "199+000",
            currentChainageMetres = 198_500L,
            currentFormattedChainage = "198+500",
            directionSide = "LHS",
            distanceMetres = 500L,
            routeId = "r1",
            timestampMs = 1_100L,
            announcedIntervalMetres = 500
        )

        val arrivalRec = KmStoneVoiceAnnouncementManager.announceEvent(context, arrivalEvent, kmSettings)
        assertNotNull(arrivalRec)
        assertTrue(arrivalRec!!.dispatchedToTts)

        // While arrival is still actively speaking, routine distance callout is skipped cleanly
        val skippedRoutine = KmStoneVoiceAnnouncementManager.announceEvent(context, routineEvent, kmSettings)
        assertNotNull(skippedRoutine)
        assertFalse(skippedRoutine!!.dispatchedToTts)

        // Once arrival finishes, routine callout works normally
        fake.createdAdapters.last().finishAllUtterances()
        val allowedRoutine = KmStoneVoiceAnnouncementManager.announceEvent(context, routineEvent, kmSettings)
        assertTrue(allowedRoutine!!.dispatchedToTts)
        assertEquals(1, SmartTtsManager.activeInstanceCount)

        // Muted audio output detection on Test Voice Output
        fake.audioMuted = true
        val mutedRes = ChainageVoiceAssistantManager.testVoiceOutputDetailed(context, assistantSettings)
        assertFalse(mutedRes.success)
        assertEquals(TtsAvailabilityStatus.AUDIO_OUTPUT_UNAVAILABLE, mutedRes.status)
        fake.audioMuted = false

        // Engine disconnect recovery when speak() returns ERROR
        fake.createdAdapters.last().failNextSpeakWithDisconnect = true
        val disconnOutcome = SmartTtsManager.speak(
            context,
            TtsSpeakRequest(
                text = "Checking disconnect recovery",
                languageTag = "en-IN",
                priority = TtsSpeechPriority.USER_INTERACTIVE
            )
        )
        assertEquals(TtsAvailabilityStatus.ENGINE_DISCONNECTED, disconnOutcome.status)

        // Releasing resources when no longer needed drops activeInstanceCount cleanly to 0
        SmartTtsManager.releaseResources()
        assertEquals(0, SmartTtsManager.activeInstanceCount)
        assertEquals(TtsAvailabilityStatus.NOT_INITIALIZED, SmartTtsManager.status.value.status)
    }
}
