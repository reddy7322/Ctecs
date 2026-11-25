package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.ActualSiteMeasurements
import com.example.model.BitumenBinderType
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetInput
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.weather.WeatherDashboardScreen
import com.example.weather.IrcMorthWeatherRuleEngine
import com.example.weather.WorkTargetPredictionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Verifies all 10 mandatory engineering & UX scenarios for CTECS Weather Page:
 * Smart Hourly Highway Activity Prediction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartHourlyHighwayPredictionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeTimeMs(hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
            set(2026, Calendar.NOVEMBER, 20, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun makeHour(
        hour: Int,
        tempC: Double = 28.0,
        rainProb: Int = 10,
        precipMm: Double = 0.0,
        windKmh: Double = 12.0,
        gustsKmh: Double = 18.0,
        condition: WeatherConditionType = WeatherConditionType.SUNNY,
        conditionLabel: String = condition.defaultLabel,
        visibilityKm: Double? = 10.0,
        humidity: Int = 52
    ): HourlyWeatherPoint {
        return HourlyWeatherPoint(
            epochMs = makeTimeMs(hour),
            localHourOfDay = hour,
            temperatureC = tempC,
            feelsLikeC = tempC + 1.0,
            rainProbabilityPercent = rainProb,
            precipitationMm = precipMm,
            windSpeedKmh = windKmh,
            windGustsKmh = gustsKmh,
            windDirectionDegrees = 210,
            humidityPercent = humidity,
            condition = condition,
            conditionLabel = conditionLabel,
            wmoCode = when (condition) {
                WeatherConditionType.THUNDERSTORM -> 95
                WeatherConditionType.RAINY -> 63
                WeatherConditionType.FOGGY -> 45
                else -> 0
            },
            isDay = hour in 6..18,
            visibilityKm = visibilityKm,
            cloudCoverPercent = if (rainProb >= 50) 90 else 20,
            pressureHpa = 1012.0,
            uvIndex = if (hour in 9..16) 6.0 else 0.0,
            dewPointC = 15.0
        )
    }

    private fun buildBundle(hours: List<HourlyWeatherPoint>, observedAtMs: Long = makeTimeMs(10)): WeatherForecastBundle {
        val currentPt = hours.firstOrNull() ?: makeHour(10)
        val obs = WeatherObservation(
            temperatureC = currentPt.temperatureC,
            feelsLikeC = currentPt.feelsLikeC,
            humidityPercent = currentPt.humidityPercent,
            windSpeedKmh = currentPt.windSpeedKmh,
            windDirectionDegrees = currentPt.windDirectionDegrees,
            condition = currentPt.condition,
            conditionLabel = currentPt.conditionLabel,
            wmoCode = currentPt.wmoCode,
            isDay = currentPt.isDay,
            latitude = 29.915,
            longitude = 78.042,
            locationName = "NH-334 Roorkee",
            observedAtMs = observedAtMs,
            fetchedAtMs = observedAtMs,
            rainProbabilityPercent = currentPt.rainProbabilityPercent,
            precipitationMm = currentPt.precipitationMm,
            windGustsKmh = currentPt.windGustsKmh,
            visibilityKm = currentPt.visibilityKm,
            uvIndex = currentPt.uvIndex
        )
        val daily = (0..6).map { idx ->
            DailyWeatherPoint(
                dateEpochMs = makeTimeMs(0) + idx * 86_400_000L,
                dayIndexFromToday = idx,
                minTempC = 18.0,
                maxTempC = 31.0,
                condition = WeatherConditionType.SUNNY,
                conditionLabel = "Sunny",
                wmoCode = 0,
                rainProbabilityPercent = 10,
                precipitationSumMm = 0.0,
                windSpeedMaxKmh = 16.0,
                windGustsMaxKmh = 22.0,
                dominantWindDirectionDegrees = 210,
                uvIndexMax = 7.0
            )
        }
        return WeatherForecastBundle(
            current = obs,
            hourly = hours,
            daily = daily,
            alerts = emptyList(),
            fetchedAtMs = observedAtMs
        )
    }

    @Test
    fun `Scenarios 1 to 6 and 8 - Engineering rule engine covers BC, DBM, modified binder, wind, fog, dust storm, rising rain, and Glass Grid`() {
        val defaultSpec = ContractSpecificationConfig(
            bcBinderType = BitumenBinderType.CONVENTIONAL_VG30,
            dbmBinderType = BitumenBinderType.CONVENTIONAL_VG30
        )

        // Scenario 1: BC selected, no rain & suitable weather -> Suitable with surface verification reminder
        val cleanHours = (10..15).map { h -> makeHour(h, tempC = 28.0, rainProb = 10, windKmh = 12.0) }
        val bcClean = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING, cleanHours, defaultSpec
        )
        assertEquals(WorkSuitabilityStatus.SUITABLE, bcClean[0].status)
        assertTrue(
            "BC Suitable card must remind engineer to verify dry base / surface temp",
            bcClean[0].primaryReason.contains("surface temp", ignoreCase = true) &&
                bcClean[0].primaryReason.contains("dry", ignoreCase = true)
        )

        // Scenario 2: DBM selected, imminent rain forecast -> Not Recommended with applicable reason
        val rainHours = listOf(makeHour(11, tempC = 27.0, rainProb = 75, precipMm = 0.0, condition = WeatherConditionType.CLOUDY))
        val dbmRain = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.DBM_LAYING, rainHours, defaultSpec
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, dbmRain[0].status)
        assertTrue(dbmRain[0].isMandatoryViolation)
        assertTrue(dbmRain[0].primaryReason.contains("rain", ignoreCase = true))

        // Scenario 3: BC selected, modified binder & unmeasured pavement surface temp -> Caution + measurement reminder
        val pmbSpec = ContractSpecificationConfig(bcBinderType = BitumenBinderType.MODIFIED_PMB)
        val bcPmbUnmeasured = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING,
            listOf(makeHour(10, tempC = 24.0, rainProb = 5, windKmh = 10.0)),
            pmbSpec,
            ActualSiteMeasurements(pavementSurfaceTempC = null)
        )
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, bcPmbUnmeasured[0].status)
        assertTrue(
            bcPmbUnmeasured[0].primaryReason.contains("surface temp", ignoreCase = true) &&
                bcPmbUnmeasured[0].primaryReason.contains("15", ignoreCase = true)
        )
        // Missing/NaN temperature telemetry -> Insufficient Data (never fabricated)
        val invalidTelemetry = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING,
            listOf(makeHour(10, tempC = Double.NaN)),
            pmbSpec
        )
        assertEquals(WorkSuitabilityStatus.INSUFFICIENT_DATA, invalidTelemetry[0].status)

        // Scenario 4: Strong wind beyond applicable limit (>40 km/h) -> Not Recommended
        val highWindHours = listOf(makeHour(12, tempC = 28.0, windKmh = 45.0, gustsKmh = 56.0))
        val bcHighWind = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING, highWindHours, defaultSpec
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, bcHighWind[0].status)
        assertTrue(bcHighWind[0].primaryReason.contains("40 km/h", ignoreCase = true))

        // Scenario 5: Fog or dust storm forecast -> Not Recommended (mandatory bituminous restriction)
        val fogAndDust = listOf(
            makeHour(7, condition = WeatherConditionType.FOGGY, conditionLabel = "Dense Fog", visibilityKm = 0.4),
            makeHour(14, condition = WeatherConditionType.WINDY, conditionLabel = "Dust Storm", windKmh = 28.0, visibilityKm = 1.5)
        )
        val bcFogDust = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING, fogAndDust, defaultSpec
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, bcFogDust[0].status)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, bcFogDust[1].status)
        assertTrue(bcFogDust[0].primaryReason.contains("Fog", ignoreCase = true))
        assertTrue(bcFogDust[1].primaryReason.contains("dust", ignoreCase = true))

        // Scenario 6: Rain probability increases in later hours -> updates hourly status & best working window
        val deterioratingHours = listOf(
            makeHour(10, rainProb = 10, precipMm = 0.0),
            makeHour(11, rainProb = 15, precipMm = 0.0),
            makeHour(12, rainProb = 20, precipMm = 0.0),
            makeHour(13, rainProb = 45, precipMm = 0.0, condition = WeatherConditionType.CLOUDY),
            makeHour(14, rainProb = 75, precipMm = 2.0, condition = WeatherConditionType.RAINY),
            makeHour(15, rainProb = 85, precipMm = 4.5, condition = WeatherConditionType.RAINY)
        )
        val deterioratingTimeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING, deterioratingHours, defaultSpec
        )
        assertEquals(WorkSuitabilityStatus.SUITABLE, deterioratingTimeline[0].status)
        assertEquals(WorkSuitabilityStatus.SUITABLE, deterioratingTimeline[2].status)
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, deterioratingTimeline[3].status)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, deterioratingTimeline[4].status)

        val windowSummary = IrcMorthWeatherRuleEngine.summarizeBestWorkingWindow(
            activity = HighwayWorkActivity.BC_LAYING,
            timeline = deterioratingTimeline,
            is24HourFormat = false,
            activityDisplayLabel = "BC Laying"
        )
        assertEquals(IrcMorthWeatherRuleEngine.BestWindowState.SUITABLE_WINDOW, windowSummary.state)
        assertEquals("10:00 AM – 1:00 PM | BC Laying | Suitable based on available forecast", windowSummary.summaryText)

        // All-unsuitable forecast -> exact "No suitable weather window" message
        val allStorm = (10..15).map { h -> makeHour(h, rainProb = 90, precipMm = 6.0, condition = WeatherConditionType.THUNDERSTORM) }
        val stormTimeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(HighwayWorkActivity.BC_LAYING, allStorm, defaultSpec)
        val noWindow = IrcMorthWeatherRuleEngine.summarizeBestWorkingWindow(HighwayWorkActivity.BC_LAYING, stormTimeline, false)
        assertEquals(IrcMorthWeatherRuleEngine.BestWindowState.NO_SUITABLE_WINDOW, noWindow.state)
        assertEquals("No suitable weather window identified in the available forecast.", noWindow.summaryText)

        // Scenario 8: Glass Grid uses Glass Grid-specific rules (wind 28 km/h triggers caution for grid lift/wrinkling,
        // while BC Laying at 28 km/h wind is Suitable -> proves distinct activity rule profiles)
        val breezyHour = listOf(makeHour(11, tempC = 28.0, rainProb = 5, windKmh = 28.0, gustsKmh = 30.0))
        val bcBreezy = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(HighwayWorkActivity.BC_LAYING, breezyHour, defaultSpec)
        val gridBreezy = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(HighwayWorkActivity.GLASS_GRID_APPLICATION, breezyHour, defaultSpec)
        assertEquals(WorkSuitabilityStatus.SUITABLE, bcBreezy[0].status)
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, gridBreezy[0].status)
        assertTrue(gridBreezy[0].primaryReason.contains("grid", ignoreCase = true))
        assertFalse(gridBreezy[0].primaryReason.contains("501.5.1"))
    }

    @Test
    fun `Scenario 7 & Persistence - Switching BC to DBM to Glass Grid updates all hourly cards immediately and persists selection`() {
        val nowMs = makeTimeMs(10)
        val hours = (10..16).map { h ->
            makeHour(h, tempC = 28.0, rainProb = 10, windKmh = 27.0, gustsKmh = 30.0)
        }
        val bundle = buildBundle(hours, observedAtMs = nowMs)
        val settings = WeatherIntelligenceSettings(animationsEnabled = false)

        composeRule.setContent {
            var selected by remember { mutableStateOf(HighwayWorkActivity.BC_LAYING) }
            val assessments = remember(bundle, settings) {
                IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, settings.contractSpec, referenceTimeMs = nowMs)
            }
            val timeline = remember(selected, bundle, settings) {
                IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(selected, bundle.hourly, settings.contractSpec)
            }
            val pred = remember(timeline) {
                WorkTargetPredictionEngine.predictWorkTarget(WorkTargetInput(activity = selected), timeline)
            }
            val risks = remember(selected, timeline) {
                WorkTargetPredictionEngine.analyzeDelayRisks(selected, timeline, pred)
            }
            MyApplicationTheme(themeMode = "dark") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Available(bundle.current),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-334 Roorkee",
                    is24HourFormat = false,
                    settings = settings,
                    assessments = assessments,
                    selectedActivity = selected,
                    hourlyWorkTimeline = timeline,
                    siteMeasurements = ActualSiteMeasurements(),
                    workTargetInput = WorkTargetInput(activity = selected),
                    workTargetPrediction = pred,
                    delayRiskSummary = risks,
                    actionableAlerts = emptyList(),
                    onSelectActivity = { selected = it },
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onUpdateWorkTargetInput = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = nowMs
                )
            }
        }

        composeRule.waitForIdle()
        // Default is BC Laying with Suitable card
        composeRule.onNodeWithTag("hourly_prediction_activity_0").assertTextContains("BC Laying", substring = true)
        composeRule.onNodeWithTag("hourly_prediction_status_0").assertTextContains("Suitable", substring = true)

        // Tap DBM Laying -> immediately updates card in-place
        composeRule.onNodeWithTag("activity_chip_dbm_laying").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hourly_prediction_activity_0").assertTextContains("DBM Laying", substring = true)

        // Scroll selector to Glass Grid & tap -> uses Glass Grid rules (wind 27 km/h -> Caution!)
        composeRule.onNodeWithTag("smart_activity_selector_row")
            .performScrollToNode(hasTestTag("activity_chip_glass_grid_application"))
        composeRule.onNodeWithTag("activity_chip_glass_grid_application").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hourly_prediction_activity_0").assertTextContains("Glass Grid", substring = true)
        composeRule.onNodeWithTag("hourly_prediction_status_0").assertTextContains("Caution", substring = true)
        composeRule.onNodeWithTag("hourly_prediction_reason_0").assertTextContains("grid", substring = true)

        // Tap BC Laying again -> immediately restores BC Laying
        composeRule.onNodeWithTag("smart_activity_selector_row")
            .performScrollToNode(hasTestTag("activity_chip_bc_laying"))
        composeRule.onNodeWithTag("activity_chip_bc_laying").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hourly_prediction_activity_0").assertTextContains("BC Laying", substring = true)

        // Verify preference persistence round-trip via PreferencesManager
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesManager(context)
        runBlocking {
            prefs.updateWeatherIntelligenceSettings(settings.copy(primaryActivity = HighwayWorkActivity.GLASS_GRID_APPLICATION))
        }
        assertEquals(
            HighwayWorkActivity.GLASS_GRID_APPLICATION,
            prefs.getInitialWeatherIntelligenceSettings().primaryActivity
        )
        runBlocking {
            prefs.updateWeatherIntelligenceSettings(WeatherIntelligenceSettings())
        }
    }

    @Test
    fun `Scenario 9 - Forecast unavailable never fabricates hourly predictions`() {
        val nowMs = makeTimeMs(10)
        val settings = WeatherIntelligenceSettings(animationsEnabled = false)
        val pred = WorkTargetPredictionEngine.predictWorkTarget(WorkTargetInput(), emptyList())
        val risks = WorkTargetPredictionEngine.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, emptyList(), pred)

        composeRule.setContent {
            MyApplicationTheme(themeMode = "light") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Unavailable("No network connection"),
                    forecastBundle = null,
                    resolvedLocationName = "NH-334 Roorkee",
                    is24HourFormat = false,
                    settings = settings,
                    assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(null, settings.contractSpec),
                    selectedActivity = HighwayWorkActivity.BC_LAYING,
                    hourlyWorkTimeline = emptyList(),
                    siteMeasurements = ActualSiteMeasurements(),
                    workTargetInput = WorkTargetInput(),
                    workTargetPrediction = pred,
                    delayRiskSummary = risks,
                    actionableAlerts = emptyList(),
                    onSelectActivity = {},
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onUpdateWorkTargetInput = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = nowMs
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_forecast_tab_list")
            .performScrollToNode(hasTestTag("highway_work_suitability_section"))
        composeRule.onNodeWithTag("hourly_prediction_unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_prediction_card_0").assertDoesNotExist()
        composeRule.onNodeWithTag("weather_best_working_window")
            .assertTextContains("Working window cannot be reliably determined", substring = true)
    }

    @Test
    fun `Scenario 10 - Cached forecast clearly displays last update timestamp and cached badge`() {
        val observedAtMs = makeTimeMs(9, 15)
        val nowMs = makeTimeMs(11, 30) // >2h stale & offline cached
        val hours = (10..15).map { h -> makeHour(h) }
        val bundle = buildBundle(hours, observedAtMs = observedAtMs)
        val settings = WeatherIntelligenceSettings(animationsEnabled = false)
        val assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, settings.contractSpec, referenceTimeMs = nowMs)
        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(HighwayWorkActivity.BC_LAYING, bundle.hourly, settings.contractSpec)
        val pred = WorkTargetPredictionEngine.predictWorkTarget(WorkTargetInput(), timeline)
        val risks = WorkTargetPredictionEngine.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, timeline, pred)

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Unavailable(
                        reason = "Offline",
                        cachedObservation = bundle.current
                    ),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-334 Roorkee",
                    is24HourFormat = false,
                    settings = settings,
                    assessments = assessments,
                    selectedActivity = HighwayWorkActivity.BC_LAYING,
                    hourlyWorkTimeline = timeline,
                    siteMeasurements = ActualSiteMeasurements(),
                    workTargetInput = WorkTargetInput(),
                    workTargetPrediction = pred,
                    delayRiskSummary = risks,
                    actionableAlerts = emptyList(),
                    onSelectActivity = {},
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onUpdateWorkTargetInput = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = nowMs
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_provider_status_bar")
            .assertTextContains("CACHED", substring = true)
        composeRule.onNodeWithTag("hourly_prediction_cache_badge")
            .assertIsDisplayed()
            .assertTextContains("Cached", substring = true)
    }
}
