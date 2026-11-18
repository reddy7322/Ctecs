package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CtecsWidgetInstanceConfig
import com.example.model.CtecsWidgetSettings
import com.example.model.CtecsWidgetType
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.KmAdvanceWarningOption
import com.example.model.KmAlertSoundOption
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVibrationIntensity
import com.example.model.KmVibrationPattern
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.WidgetAccentColorPreset
import com.example.model.WidgetBackgroundStyle
import com.example.model.WidgetSizeTier
import com.example.model.WidgetThemeMode
import com.example.navigation.KmStoneAlertEvent
import com.example.navigation.KmStoneAlertFeedbackManager
import com.example.navigation.KmStoneAlertKind
import com.example.navigation.KmStoneArrivalDetector
import com.example.navigation.LockScreenNavUiState
import com.example.ui.settings.KmStoneAlertSettingsSectionContent
import com.example.ui.settings.WidgetSettingsSectionContent
import com.example.widget.CtecsCompactWidgetProvider
import com.example.widget.CtecsDashboardWidgetProvider
import com.example.widget.CtecsMiniMapWidgetProvider
import com.example.widget.CtecsNavigationWidgetProvider
import com.example.widget.CtecsWidgetRenderer
import com.example.widget.CtecsWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetsAndKmStoneAlertsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun buildSampleRouteWithKmStones(): RouteData {
        val points = listOf(
            GeoPoint(17.4000, 78.4000),
            GeoPoint(17.4100, 78.4100),
            GeoPoint(17.4200, 78.4200),
            GeoPoint(17.4300, 78.4300)
        )
        val markers = listOf(
            RouteMarker("m1", "KM 168+000", Chainage.fromMetres(168_000L), GeoPoint(17.4000, 78.4000)),
            RouteMarker("m2", "KM 168+850", Chainage.fromMetres(168_850L), GeoPoint(17.4085, 78.4085)),
            RouteMarker("m3", "KM 170+000", Chainage.fromMetres(170_000L), GeoPoint(17.4200, 78.4200)),
            RouteMarker("m4", "KM 171+000", Chainage.fromMetres(171_000L), GeoPoint(17.4300, 78.4300))
        )
        return RouteData(
            id = "route_nh65",
            name = "NH-65 Stretch",
            fileName = "nh65.kmz",
            markers = markers,
            alignments = listOf(RouteAlignment("align_1", "Main Carriageway", points)),
            bounds = GeoBounds(17.4000, 17.4300, 78.4000, 78.4300),
            startChainage = Chainage.fromMetres(168_000L),
            endChainage = Chainage.fromMetres(171_000L)
        )
    }

    private fun buildValidLocation(speedMps: Float = 0f, accuracy: Float = 4.5f): LocationState {
        return LocationState(
            status = LocationStatus.READY,
            point = GeoPoint(17.4100, 78.4100),
            accuracy = accuracy,
            speed = speedMps,
            bearing = 85f,
            lastFixTime = System.currentTimeMillis()
        )
    }

    private fun buildMatch(chainageMetres: Long, side: String = "LHS", isManual: Boolean = false): ChainageMatchResult {
        val ch = Chainage.fromMetres(chainageMetres)
        return ChainageMatchResult(
            chainage = ch,
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 4.2f,
            statusMessage = "Calibrated KMZ Match",
            isManual = isManual,
            detectedSide = side,
            source = if (isManual) ChainageSource.MANUAL else ChainageSource.CUSTOM_CORRECTION
        )
    }

    @Test
    fun widgetRenderer_rendersAllFourWidgetTypesAcrossSizeTiersWithoutClipping() {
        val route = buildSampleRouteWithKmStones()
        val navState = LockScreenNavUiState(
            chainageText = "196+494",
            reliableSide = "RHS",
            directionArrow = "↓",
            isIncreasingTrend = false,
            directionInstruction = "Decreasing Chainage (RHS)",
            roadOrStretchName = "NH-65 Corridor",
            nextMarkerLabel = "Next KM 196+000",
            distanceToNextText = "494 m",
            distanceToNextMetres = 494L,
            speedKmh = 0,
            speedText = "0 km/h",
            locationName = "Badrabad-Rajputna Road",
            isLiveGpsValid = true,
            hasRouteData = true
        )

        val sizeDims = listOf(
            WidgetSizeTier.COMPACT to (160 to 88),
            WidgetSizeTier.MEDIUM to (260 to 125),
            WidgetSizeTier.LARGE to (340 to 200)
        )

        for (type in CtecsWidgetType.entries) {
            for ((tier, dims) in sizeDims) {
                val cfg = CtecsWidgetInstanceConfig(
                    appWidgetId = 101,
                    widgetType = type,
                    themeMode = WidgetThemeMode.DARK,
                    backgroundStyle = WidgetBackgroundStyle.SOLID,
                    accentColor = WidgetAccentColorPreset.CYAN
                )
                val renderResult = CtecsWidgetRenderer.renderWidgetBitmap(
                    context = context,
                    navState = navState,
                    instanceConfig = cfg,
                    widthDp = dims.first,
                    heightDp = dims.second,
                    location = GeoPoint(17.4100, 78.4100),
                    bearing = 90f,
                    routeData = route,
                    isTrackingActive = true
                )
                val bmp = renderResult.bitmap
                assertNotNull("Bitmap for $type ($tier) must not be null", bmp)
                assertTrue("Bitmap width > 0", bmp.width > 0)
                assertTrue("Bitmap height > 0", bmp.height > 0)
            }
        }

        // Verify dynamic chainage fitting never exceeds available width even for long chainages
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (chainageSample in listOf("0+000", "196+494", "1458+925")) {
            val availableWidthPx = 110f
            val fittedSize = CtecsWidgetRenderer.computeFittedTextSizePx(
                text = chainageSample,
                paint = paint,
                maxWidthPx = availableWidthPx,
                maxHeightPx = 42f,
                preferredMaxPx = 40f,
                minPx = 10f
            )
            paint.textSize = fittedSize
            val measured = paint.measureText(chainageSample)
            assertTrue(
                "Measured width ($measured) for $chainageSample must fit inside $availableWidthPx",
                measured <= availableWidthPx + 0.5f
            )
        }
    }

    @Test
    fun widgetSettings_retainsPerWidgetInstanceConfigAndSupportsTransparentAndLightThemes() {
        var settings = CtecsWidgetSettings.defaultSettings()
        val instance42 = settings.resolveForInstance(42, CtecsWidgetType.DASHBOARD).copy(
            themeMode = WidgetThemeMode.LIGHT,
            backgroundStyle = WidgetBackgroundStyle.TRANSPARENT,
            accentColor = WidgetAccentColorPreset.EMERALD,
            showMiniMap = false
        )
        settings = settings.withInstanceConfig(instance42)

        val roundTrip = CtecsWidgetSettings.fromJsonString(settings.toJsonString())
        val resolved42 = roundTrip.resolveForInstance(42, CtecsWidgetType.DASHBOARD)
        val resolved99 = roundTrip.resolveForInstance(99, CtecsWidgetType.COMPACT)

        assertEquals(WidgetThemeMode.LIGHT, resolved42.themeMode)
        assertEquals(WidgetBackgroundStyle.TRANSPARENT, resolved42.backgroundStyle)
        assertEquals(WidgetAccentColorPreset.EMERALD, resolved42.accentColor)
        assertFalse(resolved42.showMiniMap)

        // Widget 99 still uses default settings
        assertEquals(WidgetThemeMode.DARK, resolved99.themeMode)
        assertEquals(WidgetBackgroundStyle.SOLID, resolved99.backgroundStyle)

        // Transparent background renders transparent corner pixels
        val transparentResult = CtecsWidgetRenderer.renderWidgetBitmap(
            context = context,
            navState = LockScreenNavUiState(chainageText = "168+400", speedText = "0 km/h", isLiveGpsValid = true),
            instanceConfig = resolved42,
            widthDp = 260,
            heightDp = 130
        )
        assertEquals(Color.TRANSPARENT, transparentResult.bitmap.getPixel(0, 0))
    }

    @Test
    fun widgetUpdater_retainsChainageAcrossGpsLossAndBuildsRemoteViewsForAllProviders() {
        val liveNav = LockScreenNavUiState(
            chainageText = "168+850",
            reliableSide = "LHS",
            directionArrow = "↑",
            roadOrStretchName = "NH-65",
            nextMarkerLabel = "Next KM 170+000",
            distanceToNextText = "1.15 km",
            distanceToNextMetres = 1150L,
            speedKmh = 0,
            speedText = "0 km/h",
            locationName = "Sangareddy Corridor",
            isLiveGpsValid = true,
            hasRouteData = true
        )
        CtecsWidgetUpdater.cacheTelemetryState(context, liveNav, GeoPoint(17.4085, 78.4085))

        val (cached, cachedPoint) = CtecsWidgetUpdater.resolveCurrentNavState(context)
        assertNotNull(cached)
        assertNotNull(cachedPoint)
        assertEquals("168+850", cached.chainageText)
        assertEquals("LHS", cached.reliableSide)

        // Build RemoteViews for all 4 widget types
        val defaults = CtecsWidgetSettings.defaultSettings()
        for (type in CtecsWidgetType.entries) {
            val instanceCfg = defaults.resolveForInstance(77 + type.ordinal, type)
            val rv = CtecsWidgetUpdater.buildRemoteViewsForInstance(
                context = context,
                appWidgetId = 77 + type.ordinal,
                instanceConfig = instanceCfg,
                navState = cached,
                widthDp = 250,
                heightDp = 120,
                location = cachedPoint,
                bearing = 85f,
                routeData = buildSampleRouteWithKmStones(),
                isTrackingActive = true
            )
            assertNotNull("RemoteViews for $type must be constructed", rv)
        }

        // Verify all 4 AppWidgetProviders handle onAppWidgetOptionsChanged and onDeleted cleanly
        val providers = listOf(
            CtecsCompactWidgetProvider(),
            CtecsNavigationWidgetProvider(),
            CtecsMiniMapWidgetProvider(),
            CtecsDashboardWidgetProvider()
        )
        val mgr = AppWidgetManager.getInstance(context)
        providers.forEachIndexed { idx, provider ->
            provider.onAppWidgetOptionsChanged(context, mgr, 200 + idx, Bundle())
            provider.onDeleted(context, intArrayOf(200 + idx))
        }
    }

    @Test
    fun kmStoneArrivalDetector_detectsLhsAndRhsArrivalsIrregularStonesAndPreventsDuplicateAlerts() {
        val detector = KmStoneArrivalDetector()
        val route = buildSampleRouteWithKmStones()
        val settings = KmStoneAlertSettings(
            enabled = true,
            proximityThresholdMetres = 20,
            maxGpsAccuracyMetres = 25,
            repeatAlertPreventionEnabled = true,
            repeatAlertDistanceMetres = 60,
            advanceWarningOption = KmAdvanceWarningOption.M_200
        )

        // 1. Start at 168+500 (LHS increasing towards irregular KM Stone 168+850, 350m away)
        val ev1 = detector.evaluate(
            locationState = buildValidLocation(speedMps = 12f),
            matchResult = buildMatch(168_500L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 1_000L
        )
        assertTrue("No alert at 350m away when advance warning is 200m", ev1.isEmpty())

        // 2. Move to 168+660 (190m before 168+850 -> within 200m Advance Warning window)
        val ev2 = detector.evaluate(
            locationState = buildValidLocation(speedMps = 12f),
            matchResult = buildMatch(168_660L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 5_000L
        )
        assertEquals(1, ev2.size)
        assertEquals(KmStoneAlertKind.ADVANCE_WARNING, ev2.first().kind)
        assertEquals(168_850L, ev2.first().stoneChainageMetres)

        // 3. Jitter at 168+665 must NOT duplicate the advance warning
        val ev2Dup = detector.evaluate(
            locationState = buildValidLocation(speedMps = 12f),
            matchResult = buildMatch(168_665L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 6_000L
        )
        assertTrue("Advance warning must only fire once per stone approach", ev2Dup.isEmpty())

        // 4. Arrive within ±20m of irregular KM Stone 168+850 (at 168+842)
        val ev3 = detector.evaluate(
            locationState = buildValidLocation(speedMps = 12f),
            matchResult = buildMatch(168_842L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 12_000L
        )
        assertEquals(1, ev3.size)
        assertEquals(KmStoneAlertKind.ARRIVAL, ev3.first().kind)
        assertEquals(168_850L, ev3.first().stoneChainageMetres)
        assertEquals("168+850", ev3.first().stoneFormattedChainage)

        // 5. GPS jitter near 168+850 (e.g. 168+848, 168+855) must NOT trigger duplicate arrival alert
        val evJitter1 = detector.evaluate(
            locationState = buildValidLocation(speedMps = 2f),
            matchResult = buildMatch(168_848L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 14_000L
        )
        val evJitter2 = detector.evaluate(
            locationState = buildValidLocation(speedMps = 0f),
            matchResult = buildMatch(168_855L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 18_000L
        )
        assertTrue(evJitter1.isEmpty())
        assertTrue(evJitter2.isEmpty())

        // 6. Move > 60m away (to 168+930) to re-arm, then reverse on RHS back to 168+850
        detector.evaluate(
            locationState = buildValidLocation(speedMps = 10f),
            matchResult = buildMatch(168_930L, "LHS"),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings,
            nowMs = 25_000L
        )
        val evReturnRhs = detector.evaluate(
            locationState = buildValidLocation(speedMps = 10f),
            matchResult = buildMatch(168_852L, "RHS"),
            routeData = route,
            projectRoadSide = RoadSide.RHS,
            settings = settings,
            nowMs = 35_000L
        )
        assertEquals("Re-armed KM stone must trigger arrival on return passage", 1, evReturnRhs.size)
        assertEquals(KmStoneAlertKind.ARRIVAL, evReturnRhs.first().kind)
        assertEquals("RHS", evReturnRhs.first().directionSide)
    }

    @Test
    fun kmStoneArrivalDetector_ignoresManualOverrideAndInaccurateGps() {
        val detector = KmStoneArrivalDetector()
        val route = buildSampleRouteWithKmStones()
        val settings = KmStoneAlertSettings(enabled = true, maxGpsAccuracyMetres = 20)

        // Manual override active right on KM 170+000 -> must NOT trigger
        val manualEv = detector.evaluate(
            locationState = buildValidLocation(accuracy = 3.5f),
            matchResult = buildMatch(170_000L, "LHS", isManual = true),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings
        )
        assertTrue("Manual chainage override must not trigger KM stone arrival", manualEv.isEmpty())

        // Poor GPS accuracy (45m > 20m max) -> must NOT trigger
        val poorGpsEv = detector.evaluate(
            locationState = buildValidLocation(accuracy = 45f),
            matchResult = buildMatch(170_000L, "LHS", isManual = false),
            routeData = route,
            projectRoadSide = RoadSide.LHS,
            settings = settings
        )
        assertTrue("Poor GPS accuracy must not trigger KM stone arrival", poorGpsEv.isEmpty())
    }

    @Test
    fun kmStoneAlertFeedbackManager_respectsRecordingSuppressionAndResolvesWaveforms() {
        val settings = KmStoneAlertSettings(
            enabled = true,
            vibrationEnabled = true,
            hapticFeedbackEnabled = true,
            soundEnabled = true,
            vibrationPattern = KmVibrationPattern.DOUBLE_PULSE,
            vibrationIntensity = KmVibrationIntensity.HIGH,
            soundOption = KmAlertSoundOption.DIGITAL_CHIME
        )

        val timings = settings.resolveVibrationTimings(settings.vibrationPattern)
        assertTrue(timings.size >= 3)
        assertEquals(255, KmVibrationIntensity.HIGH.amplitude)

        val sampleEvent = KmStoneAlertEvent(
            kind = KmStoneAlertKind.ARRIVAL,
            stoneChainageMetres = 170_000L,
            stoneFormattedChainage = "170+000",
            currentChainageMetres = 170_002L,
            currentFormattedChainage = "170+002",
            directionSide = "LHS",
            distanceMetres = 2L,
            routeId = "route_nh65",
            timestampMs = 10_000L
        )

        // When Camera or Survey recording is active, sound MUST be suppressed
        val suppressedRecord = KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = sampleEvent,
            settings = settings,
            isRecordingActive = true
        )
        assertFalse(suppressedRecord.soundTriggered)
        assertEquals("170+000", suppressedRecord.stoneChainage)
    }

    @Test
    fun widgetAndKmStoneAlertSettingsUi_rendersAndSupportsCustomisationAndReset() {
        var currentWidgetSettings = CtecsWidgetSettings.defaultSettings()
        var currentAlertSettings = KmStoneAlertSettings.defaultSettings()

        composeRule.setContent {
            val widgetState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(currentWidgetSettings)
            }
            val alertState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(currentAlertSettings)
            }
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.verticalScroll(
                    androidx.compose.foundation.rememberScrollState()
                )
            ) {
                WidgetSettingsSectionContent(
                    widgetSettings = widgetState.value,
                    accentColor = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                    onUpdateSettings = { transform ->
                        currentWidgetSettings = transform(currentWidgetSettings)
                        widgetState.value = currentWidgetSettings
                    },
                    onUpdateInstanceConfig = { cfg ->
                        currentWidgetSettings = currentWidgetSettings.withInstanceConfig(cfg)
                        widgetState.value = currentWidgetSettings
                    },
                    onRemoveInstanceOverride = { id ->
                        currentWidgetSettings = currentWidgetSettings.withoutInstanceConfig(id)
                        widgetState.value = currentWidgetSettings
                    },
                    onRefreshWidgetsNow = {},
                    onResetDefaults = {
                        currentWidgetSettings = CtecsWidgetSettings.defaultSettings()
                        widgetState.value = currentWidgetSettings
                    }
                )
                KmStoneAlertSettingsSectionContent(
                    settings = alertState.value,
                    accentColor = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                    onUpdateSettings = { transform ->
                        currentAlertSettings = transform(currentAlertSettings)
                        alertState.value = currentAlertSettings
                    },
                    onResetDefaults = {
                        currentAlertSettings = KmStoneAlertSettings.defaultSettings()
                        alertState.value = currentAlertSettings
                    }
                )
            }
        }

        composeRule.onNodeWithTag("widget_settings_root").assertExists()
        composeRule.onNodeWithTag("km_stone_alert_settings_root").assertExists()

        // Scroll to and switch widget theme to Light & background to Soft Flat
        composeRule.onNodeWithTag("widget_theme_light")
            .performScrollTo()
            .performClick()
        assertEquals(WidgetThemeMode.LIGHT, currentWidgetSettings.themeMode)

        composeRule.onNodeWithTag("widget_bg_soft_flat")
            .performScrollTo()
            .performClick()
        assertEquals(WidgetBackgroundStyle.SOFT_FLAT, currentWidgetSettings.backgroundStyle)

        // Test vibration button in KM Stone Alert Settings
        composeRule.onNodeWithTag("km_alert_test_vibration_btn")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun chainageNavigationResolver_calculatesDistanceDirectionAndResolvesCoordinatesAccurately() {
        val points = listOf(
            GeoPoint(17.4000, 78.4000),
            GeoPoint(17.4500, 78.4500),
            GeoPoint(17.5000, 78.5000)
        )
        val markers = listOf(
            RouteMarker("m1", "KM 195+000", Chainage.fromMetres(195_000L), GeoPoint(17.4000, 78.4000)),
            RouteMarker("m2", "KM 200+000", Chainage.fromMetres(200_000L), GeoPoint(17.4500, 78.4500)),
            RouteMarker("m3", "KM 205+000", Chainage.fromMetres(205_000L), GeoPoint(17.5000, 78.5000))
        )
        val route = RouteData(
            id = "route_195_205",
            name = "NH-65 195-205",
            fileName = "nh65.kmz",
            markers = markers,
            alignments = listOf(RouteAlignment("align_1", "Main Carriageway", points)),
            bounds = GeoBounds(17.4000, 17.5000, 78.4000, 78.5000),
            startChainage = Chainage.fromMetres(195_000L),
            endChainage = Chainage.fromMetres(205_000L)
        )

        // Example from specification: Current Chainage "196+494", Destination "201+000" -> Distance "4.506 km", Increasing Chainage
        val currentMatch = buildMatch(196_494L, "LHS")
        val calc = com.example.navigation.ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "201+000",
            matchResult = currentMatch,
            activeRoute = route,
            projectRoadSide = RoadSide.LHS,
            detectedSideOverride = "LHS"
        )

        assertTrue(calc.isValidChainageInput)
        assertEquals("196+494", calc.formattedCurrentChainage)
        assertEquals("201+000", calc.formattedDestinationChainage)
        assertEquals(4_506L, calc.chainageDifferenceMetres)
        assertEquals("4.506 km", calc.formattedChainageDifference)
        assertEquals(
            com.example.navigation.ChainageTravelDirection.INCREASING,
            calc.travelDirection
        )
        assertTrue(calc.isLocationResolved)
        assertNotNull(calc.destinationPoint)
        assertTrue(calc.canStartNavigation)

        // Out-of-range chainage must NOT invent coordinates
        val outOfRange = com.example.navigation.ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "250+000",
            matchResult = currentMatch,
            activeRoute = route
        )
        assertTrue(outOfRange.isValidChainageInput)
        assertFalse(outOfRange.isLocationResolved)
        assertFalse(outOfRange.canStartNavigation)
        assertNotNull(outOfRange.unavailableReason)

        // Invalid chainage input is rejected
        val invalid = com.example.navigation.ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "-196+9999",
            matchResult = currentMatch,
            activeRoute = route
        )
        assertFalse(invalid.isValidChainageInput)
        assertFalse(invalid.canStartNavigation)

        // Verify Google Maps & Mappls intents pass resolved coordinates and NEVER pass chainage text as coordinates
        val destPt = calc.destinationPoint!!
        val gmapsIntent = com.example.navigation.ExternalNavigationLauncher.buildDirectionsIntent(
            app = com.example.navigation.ExternalNavApp.GOOGLE_MAPS,
            originPoint = GeoPoint(17.4150, 78.4150),
            destinationPoint = destPt,
            destinationChainageLabel = "201+000"
        )
        val uriStr = gmapsIntent.dataString.orEmpty()
        assertTrue(uriStr.contains("destination=${String.format(java.util.Locale.US, "%.6f,%.6f", destPt.latitude, destPt.longitude)}"))
        assertFalse(uriStr.contains("destination=201+000"))
    }
}

