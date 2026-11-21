package com.example

import android.app.Application
import android.hardware.camera2.CameraCharacteristics
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.camera.CameraHardwareLensEntry
import com.example.camera.CameraLensZoomCapabilities
import com.example.camera.CameraUltraWideZoomManager
import com.example.data.PreferencesManager
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HourlyWeatherForecast
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionState
import com.example.model.WeatherConditionCategory
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.navigation.SmartDirectionManager
import com.example.ui.camera.CameraManualDirectionRedBulb
import com.example.ui.camera.CameraSideOption
import com.example.ui.camera.CameraZoomControlBar
import com.example.ui.camera.resolveCameraSideDisplayState
import com.example.ui.home.HomeLiveWeatherCard
import com.example.ui.map.MiniMapViewDistanceMode
import com.example.ui.map.MiniMapViewDistancePreference
import com.example.ui.map.MiniMapViewportMath
import com.example.ui.map.SmartAutoMiniMapDistanceEngine
import com.example.ui.settings.MiniMapSettingsSection
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.weather.LiveHighwayLocationSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnhancementsAndBugFixesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleWeatherObservation(): WeatherObservation {
        return WeatherObservation(
            temperatureCelsius = 31.4,
            feelsLikeCelsius = 33.0,
            humidityPercent = 52,
            windSpeedKmh = 12.5,
            windDirectionDegrees = 225,
            precipitationMm = 0.0,
            rainProbabilityPercent = 20,
            weatherCode = 2,
            conditionLabel = "Partly Cloudy",
            category = WeatherConditionCategory.PARTLY_CLOUDY,
            isDaytime = true,
            latitude = 29.8543,
            longitude = 77.8880,
            observedAtMs = System.currentTimeMillis(),
            sourceLabel = "Open-Meteo",
            isStale = false
        )
    }

    private fun sampleFullDayHourly(): List<HourlyWeatherForecast> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return (0..23).map { hour ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            HourlyWeatherForecast(
                epochMs = cal.timeInMillis,
                localHour = hour,
                temperatureCelsius = 22.0 + (hour % 12),
                feelsLikeCelsius = 23.0 + (hour % 12),
                rainProbabilityPercent = if (hour in 14..16) 65 else 15,
                precipitationMm = if (hour in 14..16) 2.4 else 0.0,
                windSpeedKmh = 10.0,
                windDirectionDegrees = 180,
                humidityPercent = 55,
                uvIndex = if (hour in 10..15) 7.0 else 1.0,
                weatherCode = if (hour in 14..16) 61 else 2,
                conditionLabel = if (hour in 14..16) "Light Rain" else "Partly Cloudy",
                category = if (hour in 14..16) WeatherConditionCategory.RAIN else WeatherConditionCategory.PARTLY_CLOUDY,
                isDaytime = hour in 6..18
            )
        }
    }

    // =========================================================================
    // 1. HOME PAGE – WEATHER CARD COLLAPSIBLE HOURLY FORECAST
    // =========================================================================

    @Test
    fun homeWeatherCard_defaultCollapsed_singleTapTogglesHourly_doubleTapOpensDashboard() {
        val expanded = mutableStateOf(false)
        var dashboardOpens = 0

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                HomeLiveWeatherCard(
                    weatherUiState = WeatherUiState.Available(sampleWeatherObservation()),
                    resolvedLocationName = "NH-58 Roorkee",
                    is24HourFormat = false,
                    isDark = true,
                    mainTextColor = Color.White,
                    subTextColor = Color.LightGray,
                    isCompact = false,
                    isVisible = false,
                    onRefresh = {},
                    hourlyForecast = sampleFullDayHourly(),
                    currentLocalHour = 11,
                    animationsEnabled = false,
                    isHourlyExpanded = expanded.value,
                    onToggleHourlyExpanded = { expanded.value = !expanded.value },
                    onOpenWeatherDashboard = { dashboardOpens++ }
                )
            }
        }

        composeRule.waitForIdle()
        // Default collapsed: hourly forecast hidden, expand indicator visible
        composeRule.onNodeWithTag("home_live_weather_card").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_hourly_expand_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_hourly_forecast_row").assertDoesNotExist()

        // First single tap expands hourly forecast
        composeRule.onNodeWithTag("home_live_weather_card").performClick()
        composeRule.waitForIdle()
        assertTrue(expanded.value)
        assertEquals(0, dashboardOpens)
        composeRule.onNodeWithTag("home_hourly_forecast_row").assertIsDisplayed()
        composeRule.onNodeWithTag("home_hourly_item_11").assertIsDisplayed()

        // Second single tap collapses hourly forecast
        composeRule.onNodeWithTag("home_live_weather_card").performClick()
        composeRule.waitForIdle()
        assertFalse(expanded.value)
        composeRule.onNodeWithTag("home_hourly_forecast_row").assertDoesNotExist()

        // Double tap opens dedicated Weather Dashboard
        composeRule.onNodeWithTag("home_live_weather_card").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(1, dashboardOpens)
    }

    @Test
    fun homeWeatherCard_expandedWithEmptyHourly_showsGracefulUnavailableMessage() {
        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                HomeLiveWeatherCard(
                    weatherUiState = WeatherUiState.Available(sampleWeatherObservation()),
                    resolvedLocationName = "NH-58 Roorkee",
                    is24HourFormat = false,
                    isDark = true,
                    mainTextColor = Color.White,
                    subTextColor = Color.LightGray,
                    isCompact = false,
                    isVisible = false,
                    onRefresh = {},
                    hourlyForecast = emptyList(),
                    currentLocalHour = 11,
                    animationsEnabled = false,
                    isHourlyExpanded = true,
                    onToggleHourlyExpanded = {},
                    onOpenWeatherDashboard = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_hourly_unavailable_text", useUnmergedTree = true).assertIsDisplayed()
    }

    // =========================================================================
    // 2. HOME MINI MAP DYNAMIC SIZING & TILE-RELOAD STABILITY
    // =========================================================================

    @Test
    fun miniMapTileRenderPlan_quantizesRenderSize_preventingTileReloadsDuringResizeAnimation() {
        // Simulate Home Mini Map height animating smoothly from 340px to 290px as Weather Card expands
        val planStart = MiniMapViewportMath.resolveTileRenderPlan(
            viewportWidthPx = 420f,
            viewportHeightPx = 340f,
            rotationDegrees = 18f
        )
        val planMid = MiniMapViewportMath.resolveTileRenderPlan(
            viewportWidthPx = 420f,
            viewportHeightPx = 315f,
            rotationDegrees = 18f
        )
        val planEnd = MiniMapViewportMath.resolveTileRenderPlan(
            viewportWidthPx = 420f,
            viewportHeightPx = 290f,
            rotationDegrees = 18f
        )

        assertEquals(0, planStart.renderSizePx % 128)
        assertEquals(planStart.renderSizePx, planMid.renderSizePx)
        assertEquals(planMid.renderSizePx, planEnd.renderSizePx)
        assertTrue(planStart.renderSizePx >= 420)
    }

    // =========================================================================
    // 3. SMART DYNAMIC MINI MAP VIEWING DISTANCE & 250M DEFAULT + PERSISTENCE
    // =========================================================================

    @Test
    fun miniMapDefaultViewDistance_is250m_andAll10OptionsAndCustomValidationWork() {
        assertEquals(250.0, MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES, 0.001)
        assertEquals(MiniMapViewDistanceMode.DIST_250M, MiniMapViewDistanceMode.DEFAULT)
        assertEquals(10, MiniMapViewDistanceMode.entries.size)

        // Verify all 10 required modes exist
        val expectedLabels = listOf(
            "100 M", "250 M", "500 M", "1 KM", "2 KM",
            "5 KM", "10 KM", "20 KM", "Custom Distance", "Smart Auto"
        )
        assertEquals(expectedLabels, MiniMapViewDistanceMode.entries.map { it.label })

        // Custom distance validation (valid: 50 M .. 50 KM; invalid: empty, 0, negative, <50m, >50km, non-numeric)
        assertEquals(750.0, MiniMapViewDistancePreference.parseAndValidateCustomDistance("750", unitIsKm = false)!!, 0.01)
        assertEquals(3500.0, MiniMapViewDistancePreference.parseAndValidateCustomDistance("3.5", unitIsKm = true)!!, 0.01)
        assertNull(MiniMapViewDistancePreference.parseAndValidateCustomDistance("", unitIsKm = false))
        assertNull(MiniMapViewDistancePreference.parseAndValidateCustomDistance("abc", unitIsKm = false))
        assertNull(MiniMapViewDistancePreference.parseAndValidateCustomDistance("-200", unitIsKm = false))
        assertNull(MiniMapViewDistancePreference.parseAndValidateCustomDistance("30", unitIsKm = false))
        assertNull(MiniMapViewDistancePreference.parseAndValidateCustomDistance("75", unitIsKm = true))
    }

    @Test
    fun smartAutoMiniMapDistanceEngine_adaptsToSpeedAccuracyCurvesAndStationaryState() {
        val baseLat = 28.6000
        val baseLng = 77.2000
        val straightPts = (0..40).map { i -> GeoPoint(baseLat + i * 0.0002, baseLng) }
        val sharpCurvePts = listOf(
            GeoPoint(baseLat, baseLng),
            GeoPoint(baseLat + 0.0005, baseLng),
            GeoPoint(baseLat + 0.0005, baseLng + 0.0006), // 90-degree bend
            GeoPoint(baseLat + 0.0005, baseLng + 0.0012)
        )
        val straightRoute = RouteData(
            id = "straight",
            name = "Straight Highway",
            fileName = "straight.kmz",
            markers = emptyList(),
            alignments = listOf(RouteAlignment("a1", "Main", straightPts)),
            bounds = GeoBounds(baseLat, baseLat + 0.008, baseLng, baseLng),
            startChainage = Chainage(0L),
            endChainage = Chainage(1000L)
        )
        val curveRoute = RouteData(
            id = "curve",
            name = "Curved Road",
            fileName = "curve.kmz",
            markers = emptyList(),
            alignments = listOf(RouteAlignment("a2", "Curve", sharpCurvePts)),
            bounds = GeoBounds(baseLat, baseLat + 0.001, baseLng, baseLng + 0.002),
            startChainage = Chainage(0L),
            endChainage = Chainage(300L)
        )

        // Stationary defaults to 250m and holds steady against small drift
        val stationaryRange = SmartAutoMiniMapDistanceEngine.computeSmartAutoRangeMetres(
            SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = 0.2f,
                gpsAccuracyMetres = 5f,
                location = straightPts[10],
                routeData = straightRoute
            )
        )
        assertEquals(250.0, stationaryRange, 5.0)

        // Highway speed (22 m/s ~ 80 km/h) expands range substantially over low speed (3 m/s)
        val lowSpeedRange = SmartAutoMiniMapDistanceEngine.computeSmartAutoRangeMetres(
            SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = 3.0f,
                gpsAccuracyMetres = 5f,
                location = straightPts[10],
                routeData = straightRoute
            )
        )
        val highwaySpeedRange = SmartAutoMiniMapDistanceEngine.computeSmartAutoRangeMetres(
            SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = 22.0f,
                gpsAccuracyMetres = 5f,
                location = straightPts[10],
                routeData = straightRoute
            )
        )
        assertTrue("Highway range ($highwaySpeedRange) must exceed low-speed range ($lowSpeedRange)", highwaySpeedRange > lowSpeedRange * 2.5)

        // Sharp curve tightens view compared to straight stretch at same speed
        val curveRange = SmartAutoMiniMapDistanceEngine.computeSmartAutoRangeMetres(
            SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = 14.0f,
                gpsAccuracyMetres = 5f,
                location = GeoPoint(baseLat + 0.0005, baseLng + 0.00005),
                routeData = curveRoute
            )
        )
        val straightSameSpeed = SmartAutoMiniMapDistanceEngine.computeSmartAutoRangeMetres(
            SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = 14.0f,
                gpsAccuracyMetres = 5f,
                location = straightPts[10],
                routeData = straightRoute
            )
        )
        assertTrue("Curve range ($curveRange) must be tighter than straight highway ($straightSameSpeed)", curveRange < straightSameSpeed)

        // Poor GPS accuracy expands floor so uncertainty circle isn't over-zoomed
        val poorGpsRange = SmartAutoMiniMapDistanceEngine.computeSmartAutoRangeMetres(
            SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = 0.0f,
                gpsAccuracyMetres = 90f,
                location = straightPts[10],
                routeData = straightRoute
            )
        )
        assertTrue("Poor GPS accuracy range ($poorGpsRange) must expand beyond 250m", poorGpsRange >= 600.0)
    }

    @Test
    fun preferencesManager_persistsMiniMapViewDistanceModeAndCustomValue() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app)

        val customPref = MiniMapViewDistancePreference(
            mode = MiniMapViewDistanceMode.CUSTOM,
            customDistanceMetres = 1750.0
        )
        prefs.updateMiniMapViewDistance(customPref)
        val loadedCustom = prefs.miniMapViewDistanceFlow.first()
        assertEquals(MiniMapViewDistanceMode.CUSTOM, loadedCustom.mode)
        assertEquals(1750.0, loadedCustom.validatedCustomMetres, 0.01)

        val autoPref = MiniMapViewDistancePreference(
            mode = MiniMapViewDistanceMode.SMART_AUTO,
            customDistanceMetres = 1750.0
        )
        prefs.updateMiniMapViewDistance(autoPref)
        val loadedAuto = prefs.miniMapViewDistanceFlow.first()
        assertEquals(MiniMapViewDistanceMode.SMART_AUTO, loadedAuto.mode)
    }

    @Test
    fun miniMapSettingsSection_displaysMiniMapTopViewDistanceWithAll10OptionsAndCustomValidation() {
        var appliedPref = MiniMapViewDistancePreference()
        val prefState = mutableStateOf(appliedPref)

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MiniMapSettingsSection(
                        savedOverlay = OverlayConfig(showMapInset = true),
                        liveLocation = GeoPoint(28.6139, 77.2090),
                        liveBearing = 0f,
                        activeRoute = null,
                        currentChainage = "10+250",
                        currentSide = "LHS",
                        accentColor = Color(0xFF00C2FF),
                        accentContentColor = Color.Black,
                        isExpanded = true,
                        onToggleExpand = {},
                        onSaveConfig = {},
                        onResetMiniMapDefaults = {},
                        onShowMessage = {},
                        viewDistancePreference = prefState.value,
                        onUpdateViewDistance = { updated ->
                            appliedPref = updated
                            prefState.value = updated
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Mini Map Top View Distance").performScrollTo().assertIsDisplayed()

        // Verify all 10 distance chips exist and Smart Auto can be selected
        for (mode in MiniMapViewDistanceMode.entries) {
            composeRule.onNodeWithTag("minimap_distance_${mode.storageKey}").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("minimap_distance_smart_auto").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(MiniMapViewDistanceMode.SMART_AUTO, appliedPref.mode)

        // Select Custom Distance, test invalid value rejection, then apply valid value
        composeRule.onNodeWithTag("minimap_distance_custom").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("minimap_custom_distance_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("minimap_custom_distance_input").performTextInput("10")
        composeRule.onNodeWithTag("minimap_custom_distance_apply").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("minimap_custom_distance_error").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("minimap_custom_distance_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("minimap_custom_distance_input").performTextInput("850")
        composeRule.onNodeWithTag("minimap_custom_distance_apply").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(MiniMapViewDistanceMode.CUSTOM, appliedPref.mode)
        assertEquals(850.0, appliedPref.validatedCustomMetres, 0.01)
    }

    // =========================================================================
    // 4. CAMERA MANUAL DIRECTION INDICATOR (RED BULB ONLY IN MANUAL MODE)
    // =========================================================================

    @Test
    fun cameraManualDirectionIndicator_redBulbShownOnlyInManualMode_andNoManualAutoText() {
        val profile = ProjectProfile(roadSide = RoadSide.LHS)
        val autoState = SmartDirectionState(
            controlMode = DirectionControlMode.AUTO,
            detectionState = DirectionDetectionState.CONFIRMED_LHS,
            confirmedSide = "LHS",
            isManualOverride = false
        )
        val manualState = SmartDirectionState(
            controlMode = DirectionControlMode.MANUAL_LHS,
            detectionState = DirectionDetectionState.MANUAL_OVERRIDE,
            confirmedSide = "LHS",
            isManualOverride = true
        )

        val autoDisplay = resolveCameraSideDisplayState(autoState, profile)
        val manualDisplay = resolveCameraSideDisplayState(manualState, profile)

        assertEquals(CameraSideOption.LHS, autoDisplay.activeOption)
        assertEquals("LHS", autoDisplay.displayLabel)
        assertEquals("LHS", manualDisplay.displayLabel)
        assertFalse(autoDisplay.displayLabel.contains("AUTO", ignoreCase = true))
        assertFalse(manualDisplay.displayLabel.contains("MANUAL", ignoreCase = true))
        assertFalse(manualDisplay.displayLabel.contains("MAN", ignoreCase = true))

        // Verify red bulb composable renders with accessibility description & tag when active,
        // and disappears immediately when switching back to Auto.
        val isManual = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                if (isManual.value) {
                    CameraManualDirectionRedBulb()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_manual_direction_red_bulb").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Manual direction active").assertIsDisplayed()

        isManual.value = false
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("camera_manual_direction_red_bulb").assertCountEquals(0)
        composeRule.onAllNodesWithText("MANUAL", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("AUTO", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    // =========================================================================
    // 5. DYNAMIC LHS/RHS DIRECTION SYNC WITH WEATHER LIVE HIGHWAY SNAPSHOT
    // =========================================================================

    @Test
    fun weatherLiveHighwayLocationSnapshot_syncsAuthoritativeSmartDirectionSide() {
        val match = ChainageMatchResult(
            chainage = Chainage(196_450L),
            displayChainage = "196+450",
            confidence = MatchConfidence.HIGH,
            side = "RHS", // stale fallback
            smartDirectionState = SmartDirectionState(
                controlMode = DirectionControlMode.AUTO,
                detectionState = DirectionDetectionState.CONFIRMED_LHS,
                confirmedSide = "LHS",
                isManualOverride = false
            )
        )
        val snapshot = LiveHighwayLocationSnapshot.from(
            matchResult = match,
            projectProfile = ProjectProfile(highwayId = "NH-58", roadSide = RoadSide.RHS),
            activeRouteName = "NH-58",
            locationPoint = GeoPoint(29.8543, 77.8880),
            resolvedLocationName = "Roorkee",
            gpsAccuracy = 5f,
            isGpsRetained = false
        )
        assertEquals("LHS", snapshot.side)
        assertTrue(snapshot.headlineSummary.contains("NH-58 | 196+450 | LHS"))
    }

    // =========================================================================
    // 6. CAMERA DYNAMIC WIDE-ANGLE & MULTI-LENS ZOOM FUNCTIONALITY
    // =========================================================================

    @Test
    fun cameraDynamicZoom_adaptsToRealHardwareRanges_andComputesCorrectHardwareTargetRatio() {
        // Device A: Flagship 0.5x–100x logical multi-camera
        val flagshipEntries = listOf(
            CameraHardwareLensEntry("0", CameraCharacteristics.LENS_FACING_BACK, 0.5f, 100.0f, 4.3f, true, setOf("2", "3", "4"))
        )
        val flagshipCaps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = flagshipEntries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 0.5f,
            activeCameraMaxZoom = 100.0f
        )
        assertEquals(0.5f, flagshipCaps.widePresetRatio ?: -1f, 0.001f)
        assertEquals("0", flagshipCaps.logicalWideCameraId)
        assertEquals(listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f, 30.0f, 100.0f), CameraUltraWideZoomManager.resolveZoomPresets(0.5f, 100.0f, flagshipCaps))

        // Logical multi-camera target hardware zoom for 0.5x must NOT be clamped to 1.0f
        val hwTargetLogical = CameraUltraWideZoomManager.computeTargetHardwareZoomRatio(
            requestedZoom = 0.5f,
            hardwareMinZoom = 0.5f,
            hardwareMaxZoom = 100.0f,
            useStandaloneUltraWide = false,
            widePresetRatio = 0.5f
        )
        assertEquals(0.5f, hwTargetLogical, 0.001f)

        // Device B: 0.6x–10x logical multi-camera
        val midEntries = listOf(
            CameraHardwareLensEntry("0", CameraCharacteristics.LENS_FACING_BACK, 0.6f, 10.0f, 4.2f, true)
        )
        val midCaps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = midEntries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 0.6f,
            activeCameraMaxZoom = 10.0f
        )
        assertEquals(0.6f, midCaps.widePresetRatio ?: -1f, 0.001f)
        assertEquals(listOf(0.6f, 1.0f, 2.0f, 5.0f, 10.0f), CameraUltraWideZoomManager.resolveZoomPresets(0.6f, 10.0f, midCaps))

        // Device C: Standalone physical ultra-wide lens filtered by CameraX bindable IDs
        val standaloneEntries = listOf(
            CameraHardwareLensEntry("0", CameraCharacteristics.LENS_FACING_BACK, 1.0f, 10.0f, 4.5f, false),
            CameraHardwareLensEntry("2", CameraCharacteristics.LENS_FACING_BACK, 1.0f, 4.0f, 2.25f, false), // 0.5x equiv, bindable
            CameraHardwareLensEntry("5", CameraCharacteristics.LENS_FACING_BACK, 1.0f, 2.0f, 1.8f, false)   // NOT bindable by CameraX
        )
        val standaloneCaps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = standaloneEntries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 1.0f,
            activeCameraMaxZoom = 10.0f,
            bindableCameraIds = setOf("0", "2")
        )
        assertTrue(standaloneCaps.usesStandalonePhysicalUltraWide)
        assertEquals("2", standaloneCaps.standaloneUltraWideCameraId)
        assertEquals(0.5f, standaloneCaps.widePresetRatio ?: -1f, 0.001f)

        // Standalone ultra-wide target hardware zoom: 0.5x maps to native 1.0x on ultra-wide sensor, 0.75x maps to 1.5x
        assertEquals(
            1.0f,
            CameraUltraWideZoomManager.computeTargetHardwareZoomRatio(0.5f, 1.0f, 4.0f, true, 0.5f),
            0.001f
        )
        assertEquals(
            1.5f,
            CameraUltraWideZoomManager.computeTargetHardwareZoomRatio(0.75f, 1.0f, 4.0f, true, 0.5f),
            0.001f
        )

        // Logarithmic zoom slider round-trips accurately across 0.5x..100x
        for (z in listOf(0.5f, 1.0f, 2.0f, 10.0f, 30.0f, 100.0f)) {
            val frac = CameraUltraWideZoomManager.zoomToSliderFraction(z, 0.5f, 100.0f)
            val roundTrip = CameraUltraWideZoomManager.sliderFractionToZoom(frac, 0.5f, 100.0f)
            assertEquals(z, roundTrip, z * 0.01f)
        }
    }

    @Test
    fun cameraZoomControlBar_rendersDynamic05xTo100xPresetsAndTriggersCallbacks() {
        var selectedZoom = 1.0f
        val caps = CameraLensZoomCapabilities(
            minZoomRatio = 0.5f,
            maxZoomRatio = 100.0f,
            widePresetRatio = 0.5f,
            isLogicalMultiCamera = true
        )

        composeRule.setContent {
            MaterialTheme {
                CameraZoomControlBar(
                    currentZoomRatio = selectedZoom,
                    minZoomRatio = 0.5f,
                    maxZoomRatio = 100.0f,
                    zoomCapabilities = caps,
                    onZoomChanged = { selectedZoom = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("zoom_preset_0.5").assertIsDisplayed().performClick()
        assertEquals(0.5f, selectedZoom, 0.001f)
        composeRule.onNodeWithTag("zoom_preset_1").assertIsDisplayed()
        composeRule.onNodeWithTag("zoom_preset_10").assertIsDisplayed()
        composeRule.onNodeWithTag("zoom_preset_100").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(100.0f, selectedZoom, 0.01f)
    }
}
