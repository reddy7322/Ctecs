package com.example

import android.content.Context
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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.KmAlertOutputMode
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVoiceAnnouncementStyle
import com.example.model.KmVoiceSpeed
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.navigation.KmStoneAlertEvent
import com.example.navigation.KmStoneAlertFeedbackManager
import com.example.navigation.KmStoneAlertKind
import com.example.navigation.KmStoneArrivalDetector
import com.example.navigation.KmStoneVoiceAnnouncementManager
import com.example.navigation.KmStoneVoiceScriptFormatter
import com.example.ui.settings.KmStoneAlertSettingsSectionContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KmStoneVoiceAnnouncementTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KmStoneVoiceAnnouncementManager.resetTestState()
        CtecsNavigationTelemetryHub.sharedKmStoneDetector.reset()
        CtecsNavigationTelemetryHub.recordKmStoneAlertEvent(null)
    }

    private fun buildHighwayRoute(irregularMarkers: Boolean = false): RouteData {
        val markers = if (irregularMarkers) {
            listOf(
                RouteMarker("m1", "KM 197+000", Chainage.fromMetres(197_000L), GeoPoint(19.070, 72.870)),
                RouteMarker("m2", "KM 197+850", Chainage.fromMetres(197_850L), GeoPoint(19.078, 72.870)),
                RouteMarker("m3", "KM 199+200", Chainage.fromMetres(199_200L), GeoPoint(19.090, 72.870))
            )
        } else {
            listOf(
                RouteMarker("m197", "KM 197+000", Chainage.fromMetres(197_000L), GeoPoint(19.070, 72.870)),
                RouteMarker("m198", "KM 198+000", Chainage.fromMetres(198_000L), GeoPoint(19.080, 72.870)),
                RouteMarker("m199", "KM 199+000", Chainage.fromMetres(199_000L), GeoPoint(19.090, 72.870))
            )
        }
        val pts = listOf(GeoPoint(19.060, 72.870), GeoPoint(19.100, 72.870))
        return RouteData(
            id = if (irregularMarkers) "route_irregular" else "route_nh48",
            name = "NH-48 Corridor",
            fileName = "nh48.kmz",
            markers = markers,
            alignments = listOf(RouteAlignment("align_1", "Main Alignment", pts)),
            bounds = GeoBounds(19.060, 19.100, 72.870, 72.870),
            startChainage = Chainage.fromMetres(196_000L),
            endChainage = Chainage.fromMetres(200_000L)
        )
    }

    private fun liveLocation(nowMs: Long, speedMps: Float = 18.0f, accuracy: Float = 6f): LocationState {
        return LocationState(
            status = LocationStatus.READY,
            point = GeoPoint(19.075, 72.870),
            accuracy = accuracy,
            speed = speedMps,
            bearing = 85f,
            lastFixTime = nowMs
        )
    }

    private fun liveMatch(chainageMetres: Long, detectedSide: String = "LHS"): ChainageMatchResult {
        return ChainageMatchResult(
            chainage = Chainage.fromMetres(chainageMetres),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 5.0f,
            statusMessage = "Calibrated KMZ Match",
            source = ChainageSource.CUSTOM_CORRECTION,
            detectedSide = detectedSide,
            offsetMetres = 2.0,
            isManual = false,
            isUncertain = false
        )
    }

    // ------------------------------------------------------------------
    // 1. All 6 Selectable Announcement Styles & Spoken Chainage Formatting
    // ------------------------------------------------------------------
    @Test
    fun allSixAnnouncementStylesFormatApproachingAndArrivalSpeechProperly() {
        val approachEvent = KmStoneVoiceScriptFormatter.buildSamplePreviewEvent(
            isArrival = false,
            intervalMetres = 500,
            directionSide = "LHS"
        )
        val arrivalEvent = KmStoneVoiceScriptFormatter.buildSamplePreviewEvent(
            isArrival = true,
            intervalMetres = 0,
            directionSide = "RHS"
        )

        assertEquals("198 plus 000", KmStoneVoiceScriptFormatter.formatSpokenChainage("198+000"))
        assertEquals("1 kilometre", KmStoneVoiceScriptFormatter.formatSpokenDistance(1000L))
        assertEquals("500 metres", KmStoneVoiceScriptFormatter.formatSpokenDistance(500L))

        // Style 1: Simple
        val s1 = KmStoneAlertSettings(voiceStyle = KmVoiceAnnouncementStyle.SIMPLE)
        assertEquals("Next KM Stone in 500 metres.", KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s1))
        assertEquals("Kilometre stone reached.", KmStoneVoiceScriptFormatter.buildAnnouncementText(arrivalEvent, s1))

        // Style 2: Chainage
        val s2 = KmStoneAlertSettings(voiceStyle = KmVoiceAnnouncementStyle.CHAINAGE)
        assertEquals(
            "500 metres remaining to Chainage 198 plus 000.",
            KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s2)
        )
        assertEquals(
            "KM Stone reached, Chainage 198 plus 000.",
            KmStoneVoiceScriptFormatter.buildAnnouncementText(arrivalEvent, s2)
        )

        // Style 3: Direction
        val s3 = KmStoneAlertSettings(voiceStyle = KmVoiceAnnouncementStyle.DIRECTION)
        assertEquals(
            "LHS, next kilometre stone in 500 metres.",
            KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s3)
        )
        assertEquals(
            "RHS, kilometre stone reached.",
            KmStoneVoiceScriptFormatter.buildAnnouncementText(arrivalEvent, s3)
        )

        // Style 4: Detailed Navigation
        val s4 = KmStoneAlertSettings(voiceStyle = KmVoiceAnnouncementStyle.DETAILED_NAVIGATION)
        val detailedApproach = KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s4)
        assertTrue(detailedApproach.contains("198 plus 000"))
        assertTrue(detailedApproach.contains("197 plus 500"))
        assertTrue(detailedApproach.contains("500 metres"))
        assertTrue(detailedApproach.contains("LHS"))

        // Style 5: Short Voice Alert
        val s5 = KmStoneAlertSettings(voiceStyle = KmVoiceAnnouncementStyle.SHORT_ALERT)
        assertEquals("Next KM Stone, 500 metres.", KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s5))
        assertEquals("KM Stone reached, 198 plus 000.", KmStoneVoiceScriptFormatter.buildAnnouncementText(arrivalEvent, s5))

        // Style 6: Custom (field chips)
        val s6Chips = KmStoneAlertSettings(
            voiceStyle = KmVoiceAnnouncementStyle.CUSTOM,
            voiceCustomIncludeChainage = true,
            voiceCustomIncludeDirectionSide = true,
            voiceCustomIncludeDistance = true,
            voiceCustomIncludeSpeed = true,
            voiceCustomIncludeArrivalStatus = true,
            voiceCustomTemplate = ""
        )
        val customChipsText = KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s6Chips)
        assertTrue(customChipsText.contains("Approaching KM Stone"))
        assertTrue(customChipsText.contains("Chainage 198 plus 000"))
        assertTrue(customChipsText.contains("500 metres remaining"))
        assertTrue(customChipsText.contains("Direction LHS"))
        assertTrue(customChipsText.contains("62 kilometres per hour"))

        // Style 6: Custom (template override)
        val s6Template = s6Chips.copy(
            voiceCustomTemplate = "{side} alert: {distance} to KM {stone} at {speed}"
        )
        assertEquals(
            "LHS alert: 500 metres to KM 198 plus 000 at 62 kilometres per hour.",
            KmStoneVoiceScriptFormatter.buildAnnouncementText(approachEvent, s6Template)
        )
    }

    // ------------------------------------------------------------------
    // 2. Dynamic Multi-Interval Crossing (LHS, RHS, Irregular, Jitter, Signal Recovery)
    // ------------------------------------------------------------------
    @Test
    fun dynamicVoiceIntervalsTriggerOncePerCrossedThresholdInLhsAndNeverRepeatOnJitterOrGpsRecovery() {
        val detector = KmStoneArrivalDetector()
        val route = buildHighwayRoute()
        val settings = KmStoneAlertSettings(
            enabled = true,
            voiceAnnouncementEnabled = true,
            alertOutputMode = KmAlertOutputMode.VOICE_SOUND_AND_VIBRATION,
            voiceInterval1000m = false,
            voiceInterval500m = true,
            voiceInterval200m = true,
            voiceInterval100m = true,
            voiceInterval50m = true,
            voiceAnnounceAtArrival = true,
            proximityThresholdMetres = 15
        )
        var t = 10_000L

        // Start 620m before KM 198+000 (197+380) -> no threshold crossed
        val e0 = detector.evaluate(liveLocation(t), liveMatch(197_380L, "LHS"), route, RoadSide.LHS, settings, t)
        assertTrue(e0.isEmpty())

        // Cross 500m threshold -> 197+505 (495m remaining) -> announces 500m
        t += 2_000L
        val e500 = detector.evaluate(liveLocation(t), liveMatch(197_505L, "LHS"), route, RoadSide.LHS, settings, t)
        assertEquals(1, e500.size)
        assertEquals(KmStoneAlertKind.VOICE_DISTANCE, e500.first().kind)
        assertEquals(500, e500.first().announcedIntervalMetres)
        assertEquals("198+000", e500.first().stoneFormattedChainage)

        // GPS jitter around 500m (498m -> 503m -> 496m) must NOT re-trigger 500m
        t += 2_000L
        assertTrue(detector.evaluate(liveLocation(t), liveMatch(197_497L, "LHS"), route, RoadSide.LHS, settings, t).isEmpty())
        t += 2_000L
        assertTrue(detector.evaluate(liveLocation(t), liveMatch(197_504L, "LHS"), route, RoadSide.LHS, settings, t).isEmpty())

        // GPS signal loss (null location) then recovery at 490m -> must NOT duplicate 500m
        t += 2_000L
        assertTrue(detector.evaluate(null, liveMatch(197_504L, "LHS"), route, RoadSide.LHS, settings, t).isEmpty())
        t += 2_000L
        assertTrue(detector.evaluate(liveLocation(t), liveMatch(197_510L, "LHS"), route, RoadSide.LHS, settings, t).isEmpty())

        // Cross 200m threshold -> 197+808 (192m remaining)
        t += 2_000L
        val e200 = detector.evaluate(liveLocation(t), liveMatch(197_808L, "LHS"), route, RoadSide.LHS, settings, t)
        assertEquals(1, e200.size)
        assertEquals(200, e200.first().announcedIntervalMetres)

        // Cross 100m threshold -> 197+905 (95m remaining)
        t += 2_000L
        val e100 = detector.evaluate(liveLocation(t), liveMatch(197_905L, "LHS"), route, RoadSide.LHS, settings, t)
        assertEquals(1, e100.size)
        assertEquals(100, e100.first().announcedIntervalMetres)

        // Cross 50m threshold -> 197+954 (46m remaining)
        t += 2_000L
        val e50 = detector.evaluate(liveLocation(t), liveMatch(197_954L, "LHS"), route, RoadSide.LHS, settings, t)
        assertEquals(1, e50.size)
        assertEquals(50, e50.first().announcedIntervalMetres)

        // Arrive at KM 198+000 -> emits ARRIVAL only (arrival priority, no duplicate VOICE_DISTANCE)
        t += 2_000L
        val eArr = detector.evaluate(liveLocation(t), liveMatch(197_996L, "LHS"), route, RoadSide.LHS, settings, t)
        assertEquals(1, eArr.size)
        assertEquals(KmStoneAlertKind.ARRIVAL, eArr.first().kind)
        assertEquals("198+000", eArr.first().stoneFormattedChainage)

        // Pass KM 198+000 to 198+050 -> KM 198+000 is now behind vehicle and MUST NEVER announce
        t += 2_000L
        val afterPass = detector.evaluate(liveLocation(t), liveMatch(198_050L, "LHS"), route, RoadSide.LHS, settings, t)
        assertTrue(afterPass.none { it.stoneFormattedChainage == "198+000" })
    }

    @Test
    fun dynamicVoiceIntervalsWorkInRhsDecreasingDirectionAndIrregularIntervals() {
        val detector = KmStoneArrivalDetector()
        val route = buildHighwayRoute(irregularMarkers = true) // stones at 197+000, 197+850, 199+200
        val settings = KmStoneAlertSettings(
            enabled = true,
            voiceAnnouncementEnabled = true,
            voiceInterval1000m = true,
            voiceInterval500m = true,
            voiceInterval200m = false,
            voiceInterval100m = true,
            voiceInterval50m = false,
            voiceAnnounceAtArrival = true
        )
        var t = 20_000L

        // Travel RHS (decreasing) from 199+000 toward irregular marker 197+850 (1150m away)
        assertTrue(detector.evaluate(liveLocation(t), liveMatch(199_000L, "RHS"), route, RoadSide.RHS, settings, t).isEmpty())

        // Cross 1000m before 197+850 -> at 198+840 (990m remaining)
        t += 2_000L
        val e1000 = detector.evaluate(liveLocation(t), liveMatch(198_840L, "RHS"), route, RoadSide.RHS, settings, t)
        assertEquals(1, e1000.size)
        assertEquals(1000, e1000.first().announcedIntervalMetres)
        assertEquals("197+850", e1000.first().stoneFormattedChainage)
        assertEquals("RHS", e1000.first().directionSide)

        // Cross 500m -> 198+340 (490m remaining)
        t += 2_000L
        val e500 = detector.evaluate(liveLocation(t), liveMatch(198_340L, "RHS"), route, RoadSide.RHS, settings, t)
        assertEquals(500, e500.first().announcedIntervalMetres)

        // Disabled interval (200m) must NOT fire when crossing 198+040 (190m remaining)
        t += 2_000L
        val e200Disabled = detector.evaluate(liveLocation(t), liveMatch(198_040L, "RHS"), route, RoadSide.RHS, settings, t)
        assertTrue("Disabled 200m interval must not announce", e200Disabled.isEmpty())

        // Enabled 100m interval fires at 197+940 (90m remaining)
        t += 2_000L
        val e100 = detector.evaluate(liveLocation(t), liveMatch(197_940L, "RHS"), route, RoadSide.RHS, settings, t)
        assertEquals(100, e100.first().announcedIntervalMetres)
    }

    // ------------------------------------------------------------------
    // 3. Output Mode Coordination, Recording Suppression & Preview Isolation
    // ------------------------------------------------------------------
    @Test
    fun alertOutputModesCoordinateVoiceSoundVibrationAndRespectRecording() {
        val arrivalEvent = KmStoneAlertEvent(
            kind = KmStoneAlertKind.ARRIVAL,
            stoneChainageMetres = 198_000L,
            stoneFormattedChainage = "198+000",
            currentChainageMetres = 198_000L,
            currentFormattedChainage = "198+000",
            directionSide = "LHS",
            distanceMetres = 2L,
            routeId = "route_nh48",
            timestampMs = System.currentTimeMillis(),
            speedKmh = 58
        )

        // Mode 1: VOICE_ONLY -> voice ON, vibration OFF, sound OFF
        val voiceOnly = KmStoneAlertSettings(
            enabled = true,
            voiceAnnouncementEnabled = true,
            vibrationEnabled = true,
            soundEnabled = true,
            alertOutputMode = KmAlertOutputMode.VOICE_ONLY,
            voiceStyle = KmVoiceAnnouncementStyle.CHAINAGE
        )
        val rVoiceOnly = KmStoneAlertFeedbackManager.dispatchAlert(context, arrivalEvent, voiceOnly)
        assertTrue(rVoiceOnly.voiceTriggered)
        assertFalse(rVoiceOnly.vibrationTriggered)
        assertFalse(rVoiceOnly.soundTriggered)
        assertEquals("KM Stone reached, Chainage 198 plus 000.", rVoiceOnly.spokenText)

        // Mode 2: SOUND_ONLY -> voice OFF, vibration OFF
        val soundOnly = voiceOnly.copy(alertOutputMode = KmAlertOutputMode.SOUND_ONLY)
        val rSoundOnly = KmStoneAlertFeedbackManager.dispatchAlert(context, arrivalEvent, soundOnly)
        assertFalse(rSoundOnly.voiceTriggered)
        assertFalse(rSoundOnly.vibrationTriggered)

        // Active Camera/Survey recording suppresses voice, sound, and vibration
        val rRecording = KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = arrivalEvent,
            settings = voiceOnly.copy(alertOutputMode = KmAlertOutputMode.VOICE_SOUND_AND_VIBRATION),
            isRecordingActive = true
        )
        assertFalse(rRecording.voiceTriggered)
        assertFalse(rRecording.soundTriggered)
        assertFalse(rRecording.vibrationTriggered)
    }

    @Test
    fun voicePreviewUsesSampleValuesAndNeverTriggersLiveArrivalMonitoringState() {
        CtecsNavigationTelemetryHub.recordKmStoneAlertEvent(null)
        val settings = KmStoneAlertSettings(
            voiceAnnouncementEnabled = true,
            voiceStyle = KmVoiceAnnouncementStyle.DETAILED_NAVIGATION,
            voiceSpeed = KmVoiceSpeed.FAST,
            voiceVolumePercent = 80
        )

        val preview = KmStoneVoiceAnnouncementManager.previewAnnouncementStyle(context, settings, isArrival = false)
        assertTrue(preview.isPreview)
        assertTrue(preview.spokenText.contains("198 plus 000"))
        assertNull(
            "Settings preview must NEVER emit a live KM Stone arrival event into telemetry hub",
            CtecsNavigationTelemetryHub.lastKmStoneEvent.value
        )

        val arrivalPreview = KmStoneVoiceAnnouncementManager.testArrivalVoiceAnnouncement(context, settings)
        assertTrue(arrivalPreview.isPreview)
        assertTrue(arrivalPreview.isArrival)
        assertNull(CtecsNavigationTelemetryHub.lastKmStoneEvent.value)
    }

    // ------------------------------------------------------------------
    // 4. Global Persistence (JSON + DataStore PreferencesManager Round-Trip)
    // ------------------------------------------------------------------
    @Test
    fun allVoiceAnnouncementSettingsPersistThroughJsonAndPreferencesManager() = runBlocking {
        val custom = KmStoneAlertSettings(
            enabled = true,
            voiceAnnouncementEnabled = true,
            voiceName = "en-in-x-enc-local",
            voiceLanguageTag = "en-IN",
            voiceStyle = KmVoiceAnnouncementStyle.CUSTOM,
            voiceVolumePercent = 72,
            voiceSpeed = KmVoiceSpeed.FAST,
            voiceRepeatEnabled = true,
            alertOutputMode = KmAlertOutputMode.VIBRATION_AND_VOICE,
            voiceInterval1000m = true,
            voiceInterval500m = true,
            voiceInterval200m = false,
            voiceInterval100m = true,
            voiceInterval50m = true,
            voiceAnnounceAtArrival = true,
            voiceCustomIncludeChainage = true,
            voiceCustomIncludeDirectionSide = false,
            voiceCustomIncludeDistance = true,
            voiceCustomIncludeSpeed = true,
            voiceCustomIncludeArrivalStatus = false,
            voiceCustomTemplate = "{distance} to KM {stone}"
        )

        val roundTripped = KmStoneAlertSettings.fromJsonString(custom.toJsonString())
        assertEquals(custom, roundTripped)

        val prefs = PreferencesManager(context)
        prefs.updateKmStoneAlertSettings(custom)
        val restored = prefs.kmStoneAlertSettingsFlow.first()
        assertEquals(custom, restored)
    }

    // ------------------------------------------------------------------
    // 5. Settings UI Verification (Voice Announcement Subsection)
    // ------------------------------------------------------------------
    @Test
    fun settingsVoiceAnnouncementUiControlsUpdateSettingsAndPreviewSafely() {
        var latestSettings = KmStoneAlertSettings(
            voiceAnnouncementEnabled = false,
            voiceStyle = KmVoiceAnnouncementStyle.CUSTOM
        )
        var lastToast = ""

        composeRule.setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(latestSettings) }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    KmStoneAlertSettingsSectionContent(
                        settings = state,
                        accentColor = Color(0xFF00E5FF),
                        isMonitoringServiceRunning = true,
                        onUpdateSettings = { transform ->
                            state = transform(state)
                            latestSettings = state
                        },
                        onResetDefaults = {
                            state = KmStoneAlertSettings.defaultSettings()
                            latestSettings = state
                        },
                        onFeedbackMessage = { lastToast = it }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("km_alert_sub_voice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("km_alert_switch_voice_enabled").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(latestSettings.voiceAnnouncementEnabled)

        // Toggle 1000m interval, Fast speed, and Repeat Announcement
        composeRule.onNodeWithTag("km_voice_interval_1000m").performScrollTo().performClick()
        composeRule.onNodeWithTag("km_voice_speed_fast").performScrollTo().performClick()
        composeRule.onNodeWithTag("km_alert_switch_voice_repeat").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(latestSettings.voiceInterval1000m)
        assertEquals(KmVoiceSpeed.FAST, latestSettings.voiceSpeed)
        assertTrue(latestSettings.voiceRepeatEnabled)

        // Custom announcement template input + Save + Preview
        composeRule.onNodeWithTag("km_voice_custom_template_input")
            .performScrollTo()
            .performTextReplacement("{side} next stone {stone}")
        composeRule.onNodeWithTag("km_voice_custom_template_apply_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("{side} next stone {stone}", latestSettings.voiceCustomTemplate)

        composeRule.onNodeWithTag("km_voice_test_style_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(lastToast.contains("LHS next stone 198 plus 000"))
        assertNotNull(KmStoneVoiceAnnouncementManager.lastVoiceRecord.value)
        assertNull(CtecsNavigationTelemetryHub.lastKmStoneEvent.value)
    }
}
