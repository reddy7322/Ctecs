package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.backup.SettingsBackupManager
import com.example.data.PreferencesManager
import com.example.model.BitumenBinderType
import com.example.model.ContractSpecificationConfig
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.MoRTHRevision
import com.example.model.TemperatureUnit
import com.example.model.WeatherConditionType
import com.example.model.WeatherIntelligenceSettings
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tools.ChainageCalculatorScreen
import com.example.ui.weather.WeatherIntelligenceSettingsSectionContent
import com.example.ui.weather.WeatherSettingsPopup
import com.example.weather.IrcMorthWeatherRuleEngine
import com.example.weather.WeatherDelayRiskAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Verifies complete removal of activity targets and target-based features across
 * CTECS models, engines, backup/migration, Weather Settings, and Highway Tools,
 * while ensuring all actual work/weather functionality remains intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActivityTargetsRemovalVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun legacyWeatherSettingsJsonWithObsoleteTargetKeysMigratesWithoutDataLoss() {
        val legacyJson = """
            {
              "temperatureUnit": "FAHRENHEIT",
              "animationsEnabled": false,
              "primaryActivity": "DBM_LAYING",
              "alertUnsuitableBituminousConditions": true,
              "alertWorkTargetChanges": true,
              "savedProductionRatesByActivity": {
                "BC_LAYING": 850.0,
                "DBM_LAYING": 620.0
              },
              "defaultProductionRateSource": "USER_ENTERED",
              "contractSpec": {
                "morthRevision": "SIXTH_REVISION_2026",
                "sixthRevisionContractVerified": true,
                "dbmBinderType": "MODIFIED_PMB"
              }
            }
        """.trimIndent()

        val parsed = WeatherIntelligenceSettings.fromJsonString(legacyJson)
        assertEquals(TemperatureUnit.FAHRENHEIT, parsed.temperatureUnit)
        assertFalse(parsed.animationsEnabled)
        assertEquals(HighwayWorkActivity.DBM_LAYING, parsed.primaryActivity)
        assertTrue(parsed.alertUnsuitableBituminousConditions)
        assertEquals(MoRTHRevision.SIXTH_REVISION_2026, parsed.contractSpec.morthRevision)
        assertEquals(BitumenBinderType.MODIFIED_PMB, parsed.contractSpec.dbmBinderType)

        // Re-serialized JSON must never re-emit obsolete target fields
        val serialized = parsed.toJsonString()
        assertFalse(serialized.contains("alertWorkTargetChanges"))
        assertFalse(serialized.contains("savedProductionRatesByActivity"))
        assertFalse(serialized.contains("defaultProductionRateSource"))
    }

    @Test
    fun backupExportNeverIncludesObsoleteWorkTargetKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        val backupManager = SettingsBackupManager(context, prefs)
        val exported = backupManager.collectCompleteExportEntries()
        assertTrue(exported.any { it.key == PreferencesManager.KEY_WEATHER_INTELLIGENCE_SETTINGS_JSON.name })
        assertTrue(exported.none { it.key == "weather_work_target_input_json" })
    }

    @Test
    fun weatherDelayRiskAnalyzerProducesPureWeatherImpactWithoutTargetText() {
        val dayStartMs = Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
            set(2026, Calendar.NOVEMBER, 25, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val hourly = (0..23).map { hour ->
            val isRain = hour in 12..13
            HourlyWeatherPoint(
                epochMs = dayStartMs + hour * 3_600_000L,
                localHourOfDay = hour,
                temperatureC = 26.0,
                feelsLikeC = 27.0,
                rainProbabilityPercent = if (isRain) 85 else 10,
                precipitationMm = if (isRain) 4.0 else 0.0,
                windSpeedKmh = 12.0,
                windGustsKmh = 18.0,
                windDirectionDegrees = 180,
                humidityPercent = if (isRain) 90 else 50,
                condition = if (isRain) WeatherConditionType.RAINY else WeatherConditionType.SUNNY,
                conditionLabel = if (isRain) "Rainy" else "Sunny",
                wmoCode = if (isRain) 63 else 0,
                isDay = hour in 6..18,
                visibilityKm = 10.0,
                cloudCoverPercent = if (isRain) 95 else 20,
                pressureHpa = 1012.0,
                uvIndex = 5.0,
                dewPointC = 14.0
            )
        }

        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = hourly,
            contractSpec = ContractSpecificationConfig()
        )
        val summary = WeatherDelayRiskAnalyzer.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, timeline)
        assertTrue(summary.warnings.any { it.title.contains("Rain", ignoreCase = true) })
        assertNotNull(summary.nextSuitableWindowLabel)
        assertFalse(summary.weatherImpactSummary.contains("target", ignoreCase = true))
    }

    @Test
    fun weatherSettingsAndPopupHaveZeroTargetControls() {
        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                WeatherIntelligenceSettingsSectionContent(
                    settings = WeatherIntelligenceSettings(animationsEnabled = false),
                    accentColor = Color(0xFF38BDF8),
                    onUpdateSettings = {},
                    onOpenWeatherDashboard = {},
                    onResetDefaults = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_intelligence_settings_content").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_saved_production_rate_input").assertDoesNotExist()
        composeRule.onNodeWithText("PROJECT WORK TARGETS", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun weatherSettingsPopupOmitsTargetChangeAlertToggle() {
        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                WeatherSettingsPopup(
                    visible = true,
                    settings = WeatherIntelligenceSettings(animationsEnabled = false),
                    lastUpdatedLabel = "Just now",
                    isOfflineCached = false,
                    onUpdateSettings = {},
                    onManualRefresh = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_settings_popup").assertIsDisplayed()
        composeRule.onNodeWithTag("ws_alert_window").assertDoesNotExist()
        composeRule.onNodeWithText("Working-Window Changes").assertDoesNotExist()
    }

    @Test
    fun chainageCalculatorOmitsWorkProgressAndTargetTrackerWhileKeepingChainageTools() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                ChainageCalculatorScreen(context = context)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chainage Distance Calculator").assertIsDisplayed()
        composeRule.onNodeWithText("Work Progress & Target Tracker").assertDoesNotExist()
        composeRule.onNodeWithText("Target Tracker", substring = true, ignoreCase = true).assertDoesNotExist()
    }
}
