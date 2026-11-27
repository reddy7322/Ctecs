package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.ChainageVoiceLanguage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.KmVoiceSpeed
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.navigation.ActiveChainageNavigationTarget
import com.example.navigation.ChainageDestinationCalculation
import com.example.navigation.ChainageNavigationResolver
import com.example.navigation.ChainageTravelDirection
import com.example.navigation.ChainageVoiceAssistantCallbacks
import com.example.navigation.ChainageVoiceAssistantManager
import com.example.navigation.ChainageVoiceAssistantStage
import com.example.navigation.ChainageVoiceCommandParser
import com.example.navigation.ChainageVoiceConfirmationFormatter
import com.example.navigation.ChainageVoiceIntent
import com.example.navigation.DestinationResolutionStatus
import com.example.navigation.KmStoneVoiceAnnouncementManager
import com.example.ui.dialogs.NavigateToChainageDialog
import com.example.ui.settings.ChainageVoiceAssistantSettingsSectionContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavigateToChainageVoiceAssistantTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        KmStoneVoiceAnnouncementManager.resetTestState()
    }

    private fun buildHighwayAlignmentRoute(): RouteData {
        // Continuous 10 km corridor from 195+000 to 205+000
        val markers = (195..205).map { km ->
            val lat = 19.000 + (km - 195) * 0.009
            RouteMarker(
                id = "km_$km",
                label = "KM $km+000",
                chainage = Chainage.fromMetres(km * 1000L),
                point = GeoPoint(lat, 72.870)
            )
        }
        val alignmentPts = markers.map { it.point }
        return RouteData(
            id = "route_nh48_voice",
            name = "NH-48 Mumbai-Pune",
            fileName = "nh48_voice.kmz",
            markers = markers,
            alignments = listOf(RouteAlignment("align_main", "NH-48 Main", alignmentPts)),
            bounds = GeoBounds(19.000, 19.090, 72.870, 72.870),
            startChainage = Chainage.fromMetres(195_000L),
            endChainage = Chainage.fromMetres(205_000L)
        )
    }

    private fun buildMatchAt(metres: Long, side: String = "LHS"): ChainageMatchResult {
        val lat = 19.000 + ((metres - 195_000L) / 1000.0) * 0.009
        return ChainageMatchResult(
            chainage = Chainage.fromMetres(metres),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 4.5f,
            statusMessage = "Calibrated KMZ Match",
            detectedSide = side,
            source = ChainageSource.CUSTOM_CORRECTION,
            projectedPoint = GeoPoint(lat, 72.870),
            distanceAlongAlignmentMetres = (metres - 195_000L).toDouble().coerceAtLeast(0.0),
            isManual = false,
            isUncertain = false
        )
    }

    // ------------------------------------------------------------------
    // Req 2, 3, 4, 5, 6: Natural English, Indian English, Hindi, Telugu Recognition
    // ------------------------------------------------------------------
    @Test
    fun voiceParserRecognizesEnglishIndianEnglishHindiAndTeluguChainages() {
        // Req 3: 196+494
        assertEquals(
            "196+494",
            ChainageVoiceCommandParser.parseSpokenChainage("One ninety six plus four hundred ninety four")?.format()
        )
        assertEquals(
            "196+494",
            ChainageVoiceCommandParser.parseSpokenChainage("one nine six plus four nine four")?.format()
        )

        // Req 4: 198+000
        assertEquals(
            "198+000",
            ChainageVoiceCommandParser.parseSpokenChainage("One hundred ninety eight plus zero zero zero")?.format()
        )
        assertEquals(
            "198+000",
            ChainageVoiceCommandParser.parseSpokenChainage("198 plus triple zero")?.format()
        )

        // Req 5: 201+500
        assertEquals(
            "201+500",
            ChainageVoiceCommandParser.parseSpokenChainage("Two hundred one plus five hundred")?.format()
        )
        assertEquals(
            "201+500",
            ChainageVoiceCommandParser.parseSpokenChainage("Go to chainage two hundred one plus five hundred")?.format()
        )
        assertEquals(
            "201+500",
            ChainageVoiceCommandParser.parseSpokenChainage("Set destination to 201 plus 500")?.format()
        )

        // Req 6: Hindi recognition
        assertEquals(
            "201+500",
            ChainageVoiceCommandParser.parseSpokenChainage("दो सौ एक प्लस पांच सौ")?.format()
        )
        assertEquals(
            "196+494",
            ChainageVoiceCommandParser.parseSpokenChainage("चेनेज एक सौ छियानवे प्लस चार सौ चौरानवे")?.format()
        )
        assertEquals(
            "198+000",
            ChainageVoiceCommandParser.parseSpokenChainage("१९८ प्लस ०००")?.format()
        )

        // Req 6: Telugu recognition
        assertEquals(
            "201+500",
            ChainageVoiceCommandParser.parseSpokenChainage("రెండు వందల ఒకటి ప్లస్ ఐదు వందలు")?.format()
        )
        assertEquals(
            "198+000",
            ChainageVoiceCommandParser.parseSpokenChainage("౧౯౮ ప్లస్ ౦౦౦")?.format()
        )

        // Typing sanitizer blocks invalid characters & malformed symbols
        assertEquals("196+494", ChainageVoiceCommandParser.sanitizeTypingInput("196+494abc!!"))
        assertEquals("201+500", ChainageVoiceCommandParser.sanitizeTypingInput("201++50099"))
    }

    // ------------------------------------------------------------------
    // Req 7, 10, 11: Ambiguous Voice Confirmation, Correction, Confirmation & Cancellation
    // ------------------------------------------------------------------
    @Test
    fun voiceAssistantHandlesAmbiguityConfirmationCorrectionAndCancellationSafely() {
        val route = buildHighwayAlignmentRoute()
        val match = buildMatchAt(196_494L, "LHS")
        var currentCalc: ChainageDestinationCalculation? = null
        var startNavCount = 0
        var clearCount = 0
        var cancelCount = 0

        val manager = ChainageVoiceAssistantManager(
            context = context,
            callbacks = object : ChainageVoiceAssistantCallbacks {
                override fun onDestinationRecognized(chainage: Chainage, rawTranscript: String): ChainageDestinationCalculation {
                    val calc = ChainageNavigationResolver.calculateAndResolveDestination(
                        rawDestinationInput = chainage.format(),
                        matchResult = match,
                        activeRoute = route,
                        projectRoadSide = RoadSide.LHS
                    )
                    currentCalc = calc
                    return calc
                }

                override fun onRequestRepeatDestination(): ChainageDestinationCalculation? = currentCalc
                override fun onRequestClearDestination() {
                    clearCount++
                    currentCalc = null
                }

                override fun onRequestStartNavigation() {
                    startNavCount++
                }

                override fun onRequestCancelPopup() {
                    cancelCount++
                }
            }
        )
        manager.updateSettings(ChainageVoiceAssistantSettings(voiceConfirmationBeforeNav = true))

        // Req 7: Low confidence / competing hypotheses -> AMBIGUOUS_CONFIRMATION, NEVER starts nav!
        manager.handleRecognitionHypotheses(
            hypotheses = listOf("201 plus 500", "201 plus 505"),
            confidences = floatArrayOf(0.51f, 0.49f)
        )
        assertEquals(ChainageVoiceAssistantStage.AMBIGUOUS_CONFIRMATION, manager.state.value.stage)
        assertEquals(2, manager.state.value.ambiguousCandidates.size)
        assertEquals(0, startNavCount)

        // Clear recognition of 201+500 -> AWAITING_CONFIRMATION (still does NOT start navigation until confirmed!)
        manager.handleRecognitionHypotheses(
            hypotheses = listOf("Go to chainage two hundred one plus five hundred"),
            confidences = floatArrayOf(0.95f)
        )
        assertEquals(ChainageVoiceAssistantStage.AWAITING_CONFIRMATION, manager.state.value.stage)
        assertEquals("201+500", manager.state.value.recognizedChainage?.format())
        assertTrue(manager.state.value.confirmationPrompt.contains("201 plus 500"))
        assertTrue(manager.state.value.confirmationPrompt.contains("5 kilometres and 6 metres"))
        assertEquals(0, startNavCount)

        // Req 10: Voice correction ("No, change destination") clears and lets user speak replacement
        manager.handleRecognitionHypotheses(listOf("No, change destination"), forConfirmation = true)
        assertEquals(1, clearCount)
        assertEquals(ChainageVoiceAssistantStage.IDLE, manager.state.value.stage)
        assertEquals(0, startNavCount)

        // Replacement destination 198+000
        manager.handleRecognitionHypotheses(listOf("One hundred ninety eight plus zero zero zero"), floatArrayOf(0.94f))
        assertEquals(ChainageVoiceAssistantStage.AWAITING_CONFIRMATION, manager.state.value.stage)
        assertEquals("198+000", manager.state.value.recognizedChainage?.format())

        // Repeat destination keeps confirmation active without starting navigation
        manager.handleRecognitionHypotheses(listOf("Repeat destination"), forConfirmation = true)
        assertEquals(0, startNavCount)

        // Req 11: Explicit voice confirmation starts navigation
        manager.handleRecognitionHypotheses(listOf("Yes, start navigation"), forConfirmation = true)
        assertEquals(1, startNavCount)
        assertEquals(ChainageVoiceAssistantStage.NAVIGATION_STARTED, manager.state.value.stage)

        // Req 11: Voice cancel closes popup
        manager.handleRecognitionHypotheses(listOf("Cancel"))
        assertEquals(1, cancelCount)
    }

    // ------------------------------------------------------------------
    // Req 8, 9, 12: Increasing/Decreasing Calculations, Invalid & Out-of-Alignment Validation
    // ------------------------------------------------------------------
    @Test
    fun distanceDirectionCalculationWorksInIncreasingAndDecreasingAndRejectsOutOfAlignment() {
        val route = buildHighwayAlignmentRoute()

        // Req 12a: Increasing direction 196+494 -> 201+500 = 5.006 km
        val inc = ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "201+500",
            matchResult = buildMatchAt(196_494L, "LHS"),
            activeRoute = route,
            projectRoadSide = RoadSide.LHS
        )
        assertTrue(inc.canStartNavigation)
        assertEquals("5.006 km", inc.formattedChainageDifference)
        assertEquals(5006L, inc.chainageDifferenceMetres)
        assertEquals(ChainageTravelDirection.INCREASING, inc.travelDirection)
        assertEquals(
            "Destination chainage 201 plus 500. Distance remaining 5 kilometres and 6 metres. Do you want to start navigation?",
            ChainageVoiceConfirmationFormatter.buildConfirmationSpeech(inc, askToStartNavigation = true)
        )

        // Req 12b: Decreasing direction 201+500 -> 196+494 = 5.006 km
        val dec = ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "196+494",
            matchResult = buildMatchAt(201_500L, "RHS"),
            activeRoute = route,
            projectRoadSide = RoadSide.RHS
        )
        assertTrue(dec.canStartNavigation)
        assertEquals("5.006 km", dec.formattedChainageDifference)
        assertEquals(ChainageTravelDirection.DECREASING, dec.travelDirection)

        // Req 8: Invalid chainage
        val invalid = ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "-12+9999",
            matchResult = buildMatchAt(196_494L, "LHS"),
            activeRoute = route
        )
        assertFalse(invalid.isValidChainageInput)
        assertFalse(invalid.canStartNavigation)

        // Req 9: Destination outside active KMZ/KML alignment (250+000 outside 195+000..205+000)
        val outOfRange = ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "250+000",
            matchResult = buildMatchAt(196_494L, "LHS"),
            activeRoute = route
        )
        assertTrue(outOfRange.isValidChainageInput)
        assertFalse(outOfRange.isLocationResolved)
        assertFalse(outOfRange.canStartNavigation)
        assertEquals(DestinationResolutionStatus.UNAVAILABLE_OUT_OF_RANGE, outOfRange.resolutionStatus)
        assertTrue(outOfRange.unavailableReason.orEmpty().contains("outside the active alignment"))
    }

    // ------------------------------------------------------------------
    // Req 15: Microphone Permission Denied & Speech Service Unavailable
    // ------------------------------------------------------------------
    @Test
    fun voiceAssistantHandlesPermissionDeniedAndSpeechServiceUnavailableGracefully() {
        val manager = ChainageVoiceAssistantManager(
            context = context,
            callbacks = object : ChainageVoiceAssistantCallbacks {
                override fun onDestinationRecognized(chainage: Chainage, rawTranscript: String): ChainageDestinationCalculation? = null
                override fun onRequestRepeatDestination(): ChainageDestinationCalculation? = null
                override fun onRequestClearDestination() {}
                override fun onRequestStartNavigation() {}
                override fun onRequestCancelPopup() {}
            }
        )

        // Permission not granted -> PERMISSION_UNAVAILABLE
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        manager.startListening(ChainageVoiceAssistantSettings())
        assertEquals(ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE, manager.state.value.stage)

        // Permission granted, but no SpeechRecognizer service installed in Robolectric -> SERVICE_UNAVAILABLE
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        manager.startListening(ChainageVoiceAssistantSettings())
        assertEquals(ChainageVoiceAssistantStage.SERVICE_UNAVAILABLE, manager.state.value.stage)

        // Error mapping check
        val (title, detail) = ChainageVoiceAssistantManager.mapSpeechRecognizerError(SpeechRecognizer.ERROR_NETWORK)
        assertTrue(title.contains("offline", ignoreCase = true))
        assertTrue(detail.isNotBlank())
    }

    // ------------------------------------------------------------------
    // Req 1, 13, 14: Manual Entry UI, Light & Dark Themes, Portrait & Landscape Layouts
    // ------------------------------------------------------------------
    @Test
    fun redesignedPopupSupportsManualEntryLightAndDarkThemesAndPortraitLandscapeLayouts() {
        val route = buildHighwayAlignmentRoute()
        val match = buildMatchAt(196_494L, "LHS")
        var isDarkMode by mutableStateOf(false)
        var isLandscapeBox by mutableStateOf(false)
        var startedTarget: ActiveChainageNavigationTarget? = null

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.size(
                        width = if (isLandscapeBox) 760.dp else 390.dp,
                        height = if (isLandscapeBox) 380.dp else 820.dp
                    )
                ) {
                    NavigateToChainageDialog(
                        matchResult = match,
                        activeRoute = route,
                        currentGpsPoint = match.projectedPoint,
                        activeCalibration = null,
                        projectRoadSide = RoadSide.LHS,
                        detectedSideLabel = "LHS",
                        nextKmStoneSuggestion = null,
                        activeTarget = null,
                        preferredExternalAppId = "google_maps",
                        voiceAssistantSettings = ChainageVoiceAssistantSettings(),
                        onSavePreferredExternalAppId = {},
                        onStartInAppTarget = { startedTarget = it },
                        onClearActiveTarget = {},
                        onFeedbackMessage = {},
                        isDark = isDarkMode,
                        onDismiss = {}
                    )
                }
            }
        }

        // Light Portrait layout + Manual entry of 201+500
        composeRule.onNodeWithTag("navigate_to_chainage_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_chainage_mic_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_chainage_input").performTextReplacement("201+500")
        composeRule.onNodeWithTag("navigate_chainage_calculate_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("navigate_chainage_destination_value").assertTextContains("201+500")
        composeRule.onNodeWithTag("navigate_chainage_distance_value").assertTextContains("5.006 km")
        composeRule.onNodeWithTag("navigate_chainage_start_btn").assertIsEnabled()

        // Out-of-alignment destination 250+000 disables Start Navigation and shows clear explanation
        composeRule.onNodeWithTag("navigate_chainage_input").performTextReplacement("250+000")
        composeRule.onNodeWithTag("navigate_chainage_calculate_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("navigate_chainage_unavailable_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_chainage_start_btn").assertIsNotEnabled()

        // Switch to Dark + Landscape layout
        isDarkMode = true
        isLandscapeBox = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("navigate_chainage_input").performTextReplacement("198+000")
        composeRule.onNodeWithTag("navigate_chainage_calculate_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("navigate_chainage_start_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertNotNull(startedTarget)
        assertEquals("198+000", startedTarget?.destinationChainage?.format())
    }

    // ------------------------------------------------------------------
    // Req 16: Global Persistence & Settings UI Controls
    // ------------------------------------------------------------------
    @Test
    fun chainageVoiceAssistantSettingsPersistAcrossRestartsAndUpdateViaSettingsUi() = runBlocking {
        val custom = ChainageVoiceAssistantSettings(
            voiceAssistantEnabled = true,
            voiceSearchEnabled = true,
            voiceLanguage = ChainageVoiceLanguage.TELUGU,
            voiceOutputEnabled = true,
            ttsVoiceName = "te-in-x-tem-local",
            speechSpeed = KmVoiceSpeed.FAST,
            volumePercent = 75,
            voiceConfirmationBeforeNav = true,
            autoListenOnPopupOpen = true
        )

        assertEquals(custom, ChainageVoiceAssistantSettings.fromJsonString(custom.toJsonString()))

        val prefs = PreferencesManager(context)
        prefs.updateChainageVoiceAssistantSettings(custom)
        assertEquals(custom, prefs.chainageVoiceAssistantSettingsFlow.first())

        // Verify default autoListenOnPopupOpen is OFF
        assertFalse(ChainageVoiceAssistantSettings.defaultSettings().autoListenOnPopupOpen)

        // Verify Settings UI controls
        var latestSettings = ChainageVoiceAssistantSettings.defaultSettings()
        var lastFeedback = ""
        composeRule.setContent {
            MaterialTheme {
                var uiState by remember { mutableStateOf(latestSettings) }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ChainageVoiceAssistantSettingsSectionContent(
                        settings = uiState,
                        accentColor = Color(0xFF38BDF8),
                        onUpdateSettings = { transform ->
                            uiState = transform(uiState)
                            latestSettings = uiState
                        },
                        onResetDefaults = {
                            uiState = ChainageVoiceAssistantSettings.defaultSettings()
                            latestSettings = uiState
                        },
                        onFeedbackMessage = { lastFeedback = it }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("cva_permission_explanation_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cva_speed_fast").performScrollTo().performClick()
        composeRule.onNodeWithTag("cva_switch_auto_listen").performScrollTo().performClick()
        composeRule.onNodeWithTag("cva_test_voice_output_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(KmVoiceSpeed.FAST, latestSettings.speechSpeed)
        assertTrue(latestSettings.autoListenOnPopupOpen)
        assertTrue(lastFeedback.contains("201 plus 500"))
    }
}
