package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.ActualSiteMeasurements
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.TemperatureUnit
import com.example.model.WeatherAlertItem
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.model.WindSpeedUnit
import com.example.ui.AppTab
import com.example.ui.theme.CtecsColorTokens
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.weather.LiveHighwayLocationSnapshot
import com.example.ui.weather.WeatherDashboardScreen
import com.example.weather.IrcMorthWeatherRuleEngine
import com.example.weather.WeatherDelayRiskAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Verifies complete removal of the Work Planning module while ensuring the Weather Page,
 * Smart Hourly Highway Activity Prediction, contextual Weather Settings popup, Light/Dark/AMOLED
 * themes, unrelated AppTabs, and user preferences remain intact and independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkPlanningRemovalVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeTimeMs(hour: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
        set(2026, Calendar.NOVEMBER, 25, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun makeForecastBundle(nowMs: Long = makeTimeMs(9)): WeatherForecastBundle {
        val current = WeatherObservation(
            temperatureC = 27.0,
            feelsLikeC = 28.0,
            humidityPercent = 52,
            windSpeedKmh = 9.0,
            windDirectionDegrees = 180,
            condition = WeatherConditionType.SUNNY,
            conditionLabel = "Mainly Clear",
            wmoCode = 1,
            isDay = true,
            latitude = 29.3909,
            longitude = 76.9635,
            locationName = "NH-44 Panipat",
            observedAtMs = nowMs,
            fetchedAtMs = nowMs,
            rainProbabilityPercent = 5,
            precipitationMm = 0.0,
            windGustsKmh = 14.0,
            visibilityKm = 9.0,
            uvIndex = 5.5
        )
        val hourly = (0 until 24).map { offset ->
            val hour = (9 + offset) % 24
            HourlyWeatherPoint(
                epochMs = nowMs + offset * 3_600_000L,
                localHourOfDay = hour,
                temperatureC = 24.0 + (offset % 6),
                feelsLikeC = 25.0 + (offset % 6),
                rainProbabilityPercent = 5,
                precipitationMm = 0.0,
                windSpeedKmh = 10.0,
                windGustsKmh = 14.0,
                windDirectionDegrees = 180,
                humidityPercent = 50,
                condition = WeatherConditionType.SUNNY,
                conditionLabel = "Clear",
                wmoCode = 1,
                isDay = hour in 6..18,
                visibilityKm = 9.0,
                cloudCoverPercent = 20,
                pressureHpa = 1012.0,
                uvIndex = 4.0,
                dewPointC = 14.0
            )
        }
        val daily = (0..6).map { idx ->
            DailyWeatherPoint(
                dateEpochMs = makeTimeMs(0) + idx * 86_400_000L,
                dayIndexFromToday = idx,
                minTempC = 19.0,
                maxTempC = 31.0,
                condition = WeatherConditionType.SUNNY,
                conditionLabel = "Sunny",
                wmoCode = 0,
                rainProbabilityPercent = 10,
                precipitationSumMm = 0.0,
                windSpeedMaxKmh = 14.0,
                windGustsMaxKmh = 20.0,
                dominantWindDirectionDegrees = 180,
                uvIndexMax = 6.5
            )
        }
        return WeatherForecastBundle(
            current = current,
            hourly = hourly,
            daily = daily,
            alerts = emptyList(),
            fetchedAtMs = nowMs
        )
    }

    @Test
    fun test01_workPlanningTabAndSiteRegisterRemovedWhileCoreTabsIntact() {
        val tabNames = AppTab.entries.map { it.name }.toSet()
        assertFalse("WORK_PLANNING tab must be completely removed", tabNames.contains("WORK_PLANNING"))
        assertFalse("SITE_REGISTER must remain removed", tabNames.any { it.contains("SITE_REGISTER") })

        assertTrue(tabNames.contains("HOME"))
        assertTrue(tabNames.contains("CAMERA"))
        assertTrue(tabNames.contains("PHOTOS"))
        assertTrue(tabNames.contains("ROUTES"))
        assertTrue(tabNames.contains("SURVEY"))
        assertTrue(tabNames.contains("HIGHWAY_TOOLS"))
        assertTrue(tabNames.contains("SMART_NOTES"))
        assertTrue(tabNames.contains("WEATHER"))
        assertTrue(tabNames.contains("SETTINGS"))
    }

    @Test
    fun test02_weatherPageWorksIndependentlyWithZeroWorkPlanningButtonsOrPopups() {
        val nowMs = makeTimeMs(9)
        val bundle = makeForecastBundle(nowMs)
        val spec = ContractSpecificationConfig()
        val measurements = ActualSiteMeasurements()
        val assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, spec, measurements, referenceTimeMs = nowMs)
        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING, bundle.hourly, spec, measurements
        )
        val risk = WeatherDelayRiskAnalyzer.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, timeline)
        val alert = WeatherAlertItem(
            id = "a1",
            title = "Gusty Winds Expected",
            description = "Monitor tack coat spray drift",
            severity = WeatherAlertSeverity.WARNING,
            category = "wind",
            startEpochMs = nowMs,
            endEpochMs = nowMs + 3_600_000L
        )

        var updatedTempUnit = TemperatureUnit.CELSIUS

        composeRule.setContent {
            var currentSettings by remember { mutableStateOf(WeatherIntelligenceSettings(animationsEnabled = false)) }
            var currentActivity by remember { mutableStateOf(HighwayWorkActivity.BC_LAYING) }
            MyApplicationTheme(themeMode = "dark") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Available(bundle.current, isRefreshing = false),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-44 Panipat",
                    is24HourFormat = true,
                    settings = currentSettings,
                    assessments = assessments,
                    selectedActivity = currentActivity,
                    hourlyWorkTimeline = timeline,
                    siteMeasurements = measurements,
                    delayRiskSummary = risk,
                    actionableAlerts = listOf(alert),
                    onSelectActivity = { currentActivity = it },
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = nowMs,
                    liveHighwayLocation = LiveHighwayLocationSnapshot(
                        roadOrProjectName = "NH-44",
                        currentChainageText = "124+500",
                        sideOfRoad = "LHS",
                        gpsLocationName = "Panipat",
                        isGpsLive = true
                    ),
                    onUpdateWeatherSettings = { transform ->
                        currentSettings = transform(currentSettings)
                        updatedTempUnit = currentSettings.temperatureUnit
                    }
                )
            }
        }

        // No Work Planning controls, buttons, or popups exist on the Weather Page
        composeRule.onNodeWithTag("weather_open_work_planning_btn").assertDoesNotExist()
        composeRule.onNodeWithTag("highway_work_planning_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("highway_work_planning_body").assertDoesNotExist()
        composeRule.onNodeWithTag("work_planning_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("work_planning_settings_button").assertDoesNotExist()
        composeRule.onNodeWithTag("work_planning_settings_popup").assertDoesNotExist()

        // Weather Page core features remain visible & functional
        composeRule.onNodeWithTag("weather_dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_dashboard_hero_card").assertIsDisplayed()

        // Contextual Weather Settings popup opens and updates settings live without target-change toggles
        composeRule.onNodeWithTag("weather_settings_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("weather_settings_popup").assertIsDisplayed()
        composeRule.onNodeWithText("Weather Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("ws_alert_window").assertDoesNotExist()
        composeRule.onNodeWithTag("ws_temp_unit_1").performClick() // Switch to °F
        composeRule.waitForIdle()
        assertEquals(TemperatureUnit.FAHRENHEIT, updatedTempUnit)
        composeRule.onNodeWithTag("weather_settings_close").performClick()
        composeRule.onNodeWithTag("weather_settings_popup").assertDoesNotExist()
    }

    @Test
    fun test03_threeDistinctThemesLightDarkCharcoalAndAmoledTrueBlackRemainIntact() {
        var lightTokens: CtecsColorTokens? = null
        var darkTokens: CtecsColorTokens? = null
        var amoledTokens: CtecsColorTokens? = null

        composeRule.setContent {
            MyApplicationTheme(themeMode = "light") { lightTokens = CtecsTheme.colors }
            MyApplicationTheme(themeMode = "dark") { darkTokens = CtecsTheme.colors }
            MyApplicationTheme(themeMode = "amoled") { amoledTokens = CtecsTheme.colors }
        }

        val light = lightTokens!!
        val dark = darkTokens!!
        val amoled = amoledTokens!!

        assertFalse(light.isDark)
        assertTrue(dark.isDark)
        assertFalse(dark.isAmoled)
        assertTrue(amoled.isDark)
        assertTrue(amoled.isAmoled)
        assertNotEquals("Dark Charcoal and AMOLED True Black backgrounds must remain distinct", dark.background, amoled.background)
        assertNotEquals("Light and Dark backgrounds must remain distinct", light.background, dark.background)
    }

    @Test
    fun test04_unrelatedPreferencesAndWeatherSettingsPersistUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        prefs.updateThemeMode("amoled")
        val updatedWeather = WeatherIntelligenceSettings(
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            windSpeedUnit = WindSpeedUnit.MS,
            forecastDaysCount = 5,
            predictionIntervalHours = 2,
            compactForecastLayout = false
        )
        prefs.updateWeatherIntelligenceSettings(updatedWeather)

        val reloaded = PreferencesManager(context)
        assertEquals("amoled", reloaded.getInitialThemeMode())
        val loadedWeather = reloaded.getInitialWeatherIntelligenceSettings()
        assertEquals(TemperatureUnit.FAHRENHEIT, loadedWeather.temperatureUnit)
        assertEquals(WindSpeedUnit.MS, loadedWeather.windSpeedUnit)
        assertEquals(5, loadedWeather.forecastDaysCount)
        assertEquals(2, loadedWeather.predictionIntervalHours)
        assertFalse(loadedWeather.compactForecastLayout)
    }
}
