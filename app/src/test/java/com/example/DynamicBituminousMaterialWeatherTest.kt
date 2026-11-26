package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.ActualSiteMeasurements
import com.example.model.BituminousBinderGrade
import com.example.model.BituminousMaterialConfig
import com.example.model.BituminousWorkCategory
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.DelayRiskSummary
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.OtherBituminousWorkType
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Verifies dynamic Bituminous Material & Binder Selection in the Weather Settings popup,
 * dynamic prediction engine behavior, PMB VG-40 vs VG-40 separation, Insufficient Specification
 * Data handling, UI reactivity, and local persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DynamicBituminousMaterialWeatherTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeTimeMs(hour: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
            set(2026, Calendar.NOVEMBER, 25, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun makeHour(
        hour: Int,
        tempC: Double = 24.0,
        rainProb: Int = 5,
        precipMm: Double = 0.0,
        windKmh: Double = 10.0,
        condition: WeatherConditionType = WeatherConditionType.SUNNY,
        humidity: Int = 50
    ): HourlyWeatherPoint {
        return HourlyWeatherPoint(
            epochMs = makeTimeMs(hour),
            localHourOfDay = hour,
            temperatureC = tempC,
            feelsLikeC = tempC + 0.5,
            rainProbabilityPercent = rainProb,
            precipitationMm = precipMm,
            windSpeedKmh = windKmh,
            windGustsKmh = windKmh + 5.0,
            windDirectionDegrees = 180,
            humidityPercent = humidity,
            condition = condition,
            conditionLabel = condition.defaultLabel,
            wmoCode = if (condition == WeatherConditionType.RAINY) 63 else 0,
            isDay = hour in 6..18,
            visibilityKm = 10.0,
            cloudCoverPercent = if (rainProb > 50) 85 else 15,
            pressureHpa = 1013.0,
            uvIndex = 5.0,
            dewPointC = 12.0
        )
    }

    private fun buildBundle(hours: List<HourlyWeatherPoint>, observedAtMs: Long = makeTimeMs(9)): WeatherForecastBundle {
        val current = hours.first()
        val obs = WeatherObservation(
            temperatureC = current.temperatureC,
            feelsLikeC = current.feelsLikeC,
            humidityPercent = current.humidityPercent,
            windSpeedKmh = current.windSpeedKmh,
            windDirectionDegrees = current.windDirectionDegrees,
            condition = current.condition,
            conditionLabel = current.conditionLabel,
            wmoCode = current.wmoCode,
            isDay = current.isDay,
            latitude = 28.6139,
            longitude = 77.2090,
            locationName = "NH-44 Delhi-Panipat",
            observedAtMs = observedAtMs,
            fetchedAtMs = observedAtMs,
            rainProbabilityPercent = current.rainProbabilityPercent,
            visibilityKm = current.visibilityKm,
            uvIndex = current.uvIndex
        )
        return WeatherForecastBundle(
            current = obs,
            hourly = hours,
            daily = listOf(
                DailyWeatherPoint(
                    dateEpochMs = makeTimeMs(0),
                    dayIndexFromToday = 0,
                    minTempC = 14.0,
                    maxTempC = 27.0,
                    condition = WeatherConditionType.SUNNY,
                    conditionLabel = "Clear",
                    wmoCode = 0,
                    rainProbabilityPercent = 10,
                    precipitationSumMm = 0.0,
                    windSpeedMaxKmh = 14.0,
                    windGustsMaxKmh = 18.0,
                    dominantWindDirectionDegrees = 210,
                    uvIndexMax = 6.0
                )
            ),
            alerts = emptyList(),
            fetchedAtMs = observedAtMs
        )
    }

    @Test
    fun applicableBinders_areStrictlyFilteredPerWorkCategory() {
        val bcBinders = BituminousMaterialConfig.applicableBindersFor(BituminousWorkCategory.BC)
        assertTrue(BituminousBinderGrade.VG_30 in bcBinders)
        assertTrue(BituminousBinderGrade.VG_40 in bcBinders)
        assertTrue(BituminousBinderGrade.PMB in bcBinders)
        assertTrue(BituminousBinderGrade.PMB_VG_40 in bcBinders)
        assertTrue(BituminousBinderGrade.CRMB in bcBinders)
        assertTrue(BituminousBinderGrade.OTHER_CUSTOM in bcBinders)
        assertFalse("BC must not expose VG-10 spray binder", BituminousBinderGrade.VG_10 in bcBinders)
        assertFalse("BC must not expose RS-1 emulsion", BituminousBinderGrade.EMULSION_RS1 in bcBinders)

        val tackBinders = BituminousMaterialConfig.applicableBindersFor(
            BituminousWorkCategory.OTHER_BITUMINOUS,
            OtherBituminousWorkType.TACK_COAT
        )
        assertTrue(BituminousBinderGrade.EMULSION_RS1 in tackBinders)
        assertTrue(BituminousBinderGrade.VG_10 in tackBinders)
        assertFalse("Tack coat must not expose PMB VG-40 hot-mix binder", BituminousBinderGrade.PMB_VG_40 in tackBinders)

        val primeBinders = BituminousMaterialConfig.applicableBindersFor(
            BituminousWorkCategory.OTHER_BITUMINOUS,
            OtherBituminousWorkType.PRIME_COAT
        )
        assertEquals(listOf(BituminousBinderGrade.EMULSION_SS1, BituminousBinderGrade.OTHER_CUSTOM), primeBinders)

        val smaBinders = BituminousMaterialConfig.applicableBindersFor(
            BituminousWorkCategory.OTHER_BITUMINOUS,
            OtherBituminousWorkType.SMA
        )
        assertTrue(BituminousBinderGrade.PMB in smaBinders)
        assertTrue(BituminousBinderGrade.PMB_VG_40 in smaBinders)
        assertFalse("SMA must not expose VG-10", BituminousBinderGrade.VG_10 in smaBinders)
    }

    @Test
    fun pmbVg40_isEvaluatedDistinctlyFromConventionalVg40() {
        // 13°C clear dry weather: conventional VG-40 (>=10°C) vs polymer-modified PMB VG-40 (>=15°C)
        val hours = listOf(makeHour(hour = 8, tempC = 13.0, rainProb = 0, windKmh = 10.0))

        val vg40Cfg = BituminousMaterialConfig(
            workCategory = BituminousWorkCategory.BC,
            binderGrade = BituminousBinderGrade.VG_40,
            bcBinderGrade = BituminousBinderGrade.VG_40
        )
        val pmbVg40Cfg = BituminousMaterialConfig(
            workCategory = BituminousWorkCategory.BC,
            binderGrade = BituminousBinderGrade.PMB_VG_40,
            bcBinderGrade = BituminousBinderGrade.PMB_VG_40
        )

        val vg40Timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = hours,
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = vg40Cfg
        )
        val pmbVg40Timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = hours,
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = pmbVg40Cfg
        )

        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, vg40Timeline.first().status)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, pmbVg40Timeline.first().status)
        assertNotEquals(vg40Timeline.first().status, pmbVg40Timeline.first().status)
        assertEquals("VG-40", vg40Timeline.first().binderGradeLabel)
        assertEquals("PMB VG-40", pmbVg40Timeline.first().binderGradeLabel)
        assertTrue(
            "PMB VG-40 reason must state 15°C modified binder requirement",
            pmbVg40Timeline.first().primaryReason.contains("15°C")
        )
    }

    @Test
    fun bcAndDbm_respondDynamicallyToThinLiftWindAndBinderChoices() {
        // 16°C, 27 km/h wind: thin BC wearing course gets thin-lift thermal loss caution; DBM with VG-30 stays suitable
        val hours = listOf(makeHour(hour = 10, tempC = 16.0, rainProb = 0, windKmh = 27.0))

        val bcVg30 = BituminousMaterialConfig(
            workCategory = BituminousWorkCategory.BC,
            binderGrade = BituminousBinderGrade.VG_30,
            bcBinderGrade = BituminousBinderGrade.VG_30
        )
        val dbmVg30 = BituminousMaterialConfig(
            workCategory = BituminousWorkCategory.DBM,
            binderGrade = BituminousBinderGrade.VG_30,
            dbmBinderGrade = BituminousBinderGrade.VG_30
        )
        val dbmVg40 = BituminousMaterialConfig(
            workCategory = BituminousWorkCategory.DBM,
            binderGrade = BituminousBinderGrade.VG_40,
            dbmBinderGrade = BituminousBinderGrade.VG_40
        )

        val bcResult = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = hours,
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = bcVg30
        ).first()
        val dbmVg30Result = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.DBM_LAYING,
            hourlyPoints = hours,
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = dbmVg30
        ).first()
        val dbmVg40Result = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.DBM_LAYING,
            hourlyPoints = hours,
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = dbmVg40
        ).first()

        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, bcResult.status)
        assertEquals(WorkSuitabilityStatus.SUITABLE, dbmVg30Result.status)
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, dbmVg40Result.status)
        assertTrue(bcResult.workCategoryLabel.startsWith("BC"))
        assertTrue(dbmVg30Result.workCategoryLabel.startsWith("DBM"))
    }

    @Test
    fun customCategoryAndCustomBinder_withoutJmfLimits_reportsInsufficientSpecificationDataWithoutInventingLimits() {
        val dryHour = makeHour(hour = 11, tempC = 25.0, rainProb = 0, windKmh = 10.0)
        val rainyHour = makeHour(hour = 12, tempC = 24.0, rainProb = 80, precipMm = 2.0, condition = WeatherConditionType.RAINY)

        val customMissingSpec = BituminousMaterialConfig(
            workCategory = BituminousWorkCategory.CUSTOM,
            customWorkCategoryName = "Warm Mix BC",
            binderGrade = BituminousBinderGrade.OTHER_CUSTOM,
            customBinderSpecification = "PG 76-22",
            customMinSurfaceTempC = null,
            isCustomCategoryActive = true
        )
        assertTrue(customMissingSpec.hasInsufficientSpecificationData)
        assertNull(customMissingSpec.effectiveMinSurfaceTempC)

        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = listOf(dryHour, rainyHour),
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = customMissingSpec
        )
        assertEquals(WorkSuitabilityStatus.INSUFFICIENT_DATA, timeline[0].status)
        assertTrue(timeline[0].insufficientSpecData)
        assertTrue(timeline[0].primaryReason.contains("Insufficient Specification Data"))
        assertEquals("Warm Mix BC", timeline[0].workCategoryLabel)
        assertEquals("PG 76-22", timeline[0].binderGradeLabel)

        // Rain still overrides to Not Recommended even when spec temp is missing
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, timeline[1].status)

        // Once user enters explicit approved JMF min surface temp (16°C), engine evaluates against it
        val customWithJmf = customMissingSpec.copy(customMinSurfaceTempC = 16.0)
        assertFalse(customWithJmf.hasInsufficientSpecificationData)
        val evaluated = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = listOf(makeHour(hour = 7, tempC = 14.0), dryHour),
            contractSpec = ContractSpecificationConfig(),
            bituminousConfig = customWithJmf
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, evaluated[0].status)
        assertEquals(WorkSuitabilityStatus.SUITABLE, evaluated[1].status)
    }

    @Test
    fun weatherSettingsPopup_dynamicSelectionAndReset_updatesWeatherCardsImmediately() {
        val hours = listOf(
            makeHour(hour = 9, tempC = 13.0, rainProb = 0, windKmh = 10.0, humidity = 54),
            makeHour(hour = 10, tempC = 22.0, rainProb = 5, windKmh = 11.0, humidity = 48),
            makeHour(hour = 11, tempC = 25.0, rainProb = 5, windKmh = 12.0, humidity = 45)
        )
        val bundle = buildBundle(hours, observedAtMs = makeTimeMs(9))
        val siteMeasurements = ActualSiteMeasurements(pavementSurfaceTempC = 26.0)
        val initialSettings = WeatherIntelligenceSettings(animationsEnabled = false)
        val initialTimeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = hours,
            contractSpec = initialSettings.contractSpec,
            siteMeasurements = siteMeasurements,
            bituminousConfig = initialSettings.bituminousConfig
        )

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Available(bundle.current),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-44 Panipat",
                    is24HourFormat = true,
                    settings = initialSettings,
                    assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, initialSettings.contractSpec),
                    selectedActivity = HighwayWorkActivity.BC_LAYING,
                    hourlyWorkTimeline = initialTimeline,
                    siteMeasurements = siteMeasurements,
                    workTargetInput = WorkTargetInput(),
                    workTargetPrediction = WorkTargetPredictionEngine.predictWorkTarget(WorkTargetInput(), initialTimeline),
                    delayRiskSummary = WorkTargetPredictionEngine.analyzeDelayRisks(
                        HighwayWorkActivity.BC_LAYING,
                        initialTimeline,
                        WorkTargetPredictionEngine.predictWorkTarget(WorkTargetInput(), initialTimeline)
                    ),
                    actionableAlerts = emptyList(),
                    onSelectActivity = {},
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onUpdateWorkTargetInput = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = makeTimeMs(9)
                )
            }
        }

        // Initial default state: BC Laying + VG-30 + measured surface temp on first card
        composeRule.onNodeWithTag("weather_suitability_activity_name").assertTextContains("BC", substring = true)
        composeRule.onNodeWithTag("hourly_prediction_binder_0").assertTextContains("VG-30", substring = true)
        composeRule.onNodeWithTag("hourly_prediction_surface_temp_0").assertExists()
        composeRule.onNodeWithTag("hourly_prediction_humidity_0").assertTextContains("54% RH", substring = true)

        // Open Weather Settings Popup -> select DBM -> select PMB VG-40
        composeRule.onNodeWithTag("weather_settings_button").performClick()
        composeRule.onNodeWithTag("ws_bituminous_material_section").assertIsDisplayed()
        composeRule.onNodeWithTag("ws_bituminous_category_dropdown").performClick()
        composeRule.onNodeWithTag("ws_bituminous_cat_dbm").performClick()
        composeRule.onNodeWithTag("ws_bituminous_binder_dropdown").performClick()
        composeRule.onNodeWithTag("ws_bituminous_binder_pmb_vg_40").performClick()
        composeRule.onNodeWithTag("weather_settings_close").performClick()

        // Weather Page updates immediately without navigating away
        composeRule.onNodeWithTag("weather_suitability_activity_name").assertTextContains("DBM Laying")
        composeRule.onNodeWithTag("weather_prediction_binder_badge").assertTextContains("PMB VG-40")
        composeRule.onNodeWithTag("hourly_prediction_binder_0").assertTextContains("PMB VG-40")
        // Hour 0 is 13°C -> PMB VG-40 requires >=15°C -> Not Recommended
        composeRule.onNodeWithTag("hourly_prediction_status_0").assertTextContains("Not Recommended")

        // Reopen popup -> switch to Other Bituminous Works -> SMA -> verify immediate update
        composeRule.onNodeWithTag("weather_settings_button").performClick()
        composeRule.onNodeWithTag("ws_bituminous_category_dropdown").performClick()
        composeRule.onNodeWithTag("ws_bituminous_cat_other_bituminous").performClick()
        composeRule.onNodeWithTag("ws_bituminous_other_dropdown").performClick()
        composeRule.onNodeWithTag("ws_bituminous_other_sma").performClick()
        composeRule.onNodeWithTag("weather_settings_close").performClick()

        composeRule.onNodeWithTag("weather_suitability_activity_name").assertTextContains("SMA")
        composeRule.onNodeWithTag("hourly_prediction_binder_0").assertTextContains("PMB")

        // Reopen popup -> select Custom with custom name and no JMF temp -> Insufficient Spec
        composeRule.onNodeWithTag("weather_settings_button").performClick()
        composeRule.onNodeWithTag("ws_bituminous_category_dropdown").performClick()
        composeRule.onNodeWithTag("ws_bituminous_cat_custom").performClick()
        composeRule.onNodeWithTag("ws_bituminous_custom_category_input").performTextInput("Mastic Asphalt")
        composeRule.onNodeWithTag("weather_settings_close").performClick()

        composeRule.onNodeWithTag("weather_suitability_activity_name").assertTextContains("Mastic Asphalt")
        composeRule.onNodeWithTag("hourly_prediction_status_0").assertTextContains("Insufficient Spec")

        // Reopen popup -> Reset to Default -> reverts cleanly to BC Laying + VG-30
        composeRule.onNodeWithTag("weather_settings_button").performClick()
        composeRule.onNodeWithTag("ws_bituminous_reset_btn").performClick()
        composeRule.onNodeWithTag("weather_settings_close").performClick()

        composeRule.onNodeWithTag("weather_suitability_activity_name").assertTextContains("BC Laying")
        composeRule.onNodeWithTag("hourly_prediction_binder_0").assertTextContains("VG-30")
    }

    @Test
    fun bituminousConfiguration_persistsLocallyAndRestoresAcrossRestarts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesManager(context)

        val customSettings = WeatherIntelligenceSettings(
            primaryActivity = HighwayWorkActivity.DBM_LAYING,
            bituminousConfig = BituminousMaterialConfig(
                workCategory = BituminousWorkCategory.DBM,
                binderGrade = BituminousBinderGrade.PMB_VG_40,
                bcBinderGrade = BituminousBinderGrade.CRMB,
                dbmBinderGrade = BituminousBinderGrade.PMB_VG_40,
                customBinderSpecification = "Project PMB VG-40",
                customMinSurfaceTempC = 16.5,
                isCustomCategoryActive = false
            )
        )

        prefs.updateWeatherIntelligenceSettings(customSettings)

        // Simulate app restart by creating a fresh PreferencesManager instance
        val restored = PreferencesManager(context).getInitialWeatherIntelligenceSettings()
        assertEquals(BituminousWorkCategory.DBM, restored.bituminousConfig.workCategory)
        assertEquals(BituminousBinderGrade.PMB_VG_40, restored.bituminousConfig.binderGrade)
        assertEquals(BituminousBinderGrade.CRMB, restored.bituminousConfig.bcBinderGrade)
        assertEquals(BituminousBinderGrade.PMB_VG_40, restored.bituminousConfig.dbmBinderGrade)
        assertEquals("Project PMB VG-40", restored.bituminousConfig.customBinderSpecification)
        assertEquals(16.5, restored.bituminousConfig.customMinSurfaceTempC ?: -1.0, 0.001)
    }
}
