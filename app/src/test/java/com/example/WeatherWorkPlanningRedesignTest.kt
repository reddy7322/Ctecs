package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.ActualSiteMeasurements
import com.example.model.BackupDuplicateResolution
import com.example.model.BackupFrequency
import com.example.model.ChainageMath
import com.example.model.CompactionPlanningInput
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.HighwayWorkActivity
import com.example.model.HighwayWorkPlan
import com.example.model.HourlyWeatherPoint
import com.example.model.MachineryFilterPreference
import com.example.model.MoRTHRevision
import com.example.model.PdfExportScope
import com.example.model.PlannedMachineryItem
import com.example.model.PlanningWorkActivity
import com.example.model.RoadStretchWidthItem
import com.example.model.RollingCamberMode
import com.example.model.ShiftTimingCalculator
import com.example.model.ThicknessCompactionSpec
import com.example.model.TransportPlanningInput
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.model.WindSpeedUnit
import com.example.model.WorkPlanSaveStatus
import com.example.model.WorkPlanningSettings
import com.example.model.WorkPlanningStandardEngine
import com.example.model.WorkShiftType
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetInput
import com.example.model.WorkingTimePeriod
import com.example.ui.AppTab
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.weather.WeatherDashboardScreen
import com.example.ui.workplan.WorkPlanningScreen
import com.example.weather.HighwayWorkPlanningEngine
import com.example.weather.IrcMorthWeatherRuleEngine
import com.example.weather.MachineryCatalogRepository
import com.example.weather.WorkPlanBackupManager
import com.example.weather.WorkPlanPdfExporter
import com.example.weather.WorkTargetPredictionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
 * Comprehensive 25-test Final Acceptance Suite for the CTECS Weather Page, Separate Work Planning Page,
 * Contextual Animated Settings Popups, Three-Theme System (Light / Dark / AMOLED), Backup & Restore,
 * and A4 PDF Export redesign.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherWorkPlanningRedesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeTimeMs(hour: Int, dayOffset: Int = 0): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
            set(2026, Calendar.NOVEMBER, 25 + dayOffset, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun makeHour(
        hour: Int,
        dayOffset: Int = 0,
        tempC: Double = 27.0,
        rainProb: Int = 10,
        precipMm: Double = 0.0,
        windKmh: Double = 11.0,
        condition: WeatherConditionType = WeatherConditionType.SUNNY
    ): HourlyWeatherPoint = HourlyWeatherPoint(
        epochMs = makeTimeMs(hour, dayOffset),
        localHourOfDay = hour,
        temperatureC = tempC,
        feelsLikeC = tempC + 1.0,
        rainProbabilityPercent = rainProb,
        precipitationMm = precipMm,
        windSpeedKmh = windKmh,
        windGustsKmh = windKmh + 5.0,
        windDirectionDegrees = 180,
        humidityPercent = 52,
        condition = condition,
        conditionLabel = condition.defaultLabel,
        wmoCode = if (condition == WeatherConditionType.RAINY) 63 else 0,
        isDay = hour in 6..18,
        visibilityKm = 10.0,
        cloudCoverPercent = if (rainProb >= 50) 80 else 15,
        pressureHpa = 1012.0,
        uvIndex = if (hour in 9..16) 5.5 else 0.0,
        dewPointC = 14.0
    )

    private fun make24hBundle(fetchedAtMs: Long = makeTimeMs(8)): WeatherForecastBundle {
        val hours = (0..23).map { h -> makeHour(h) }
        val obs = WeatherObservation(
            temperatureC = 28.0,
            feelsLikeC = 29.0,
            humidityPercent = 50,
            windSpeedKmh = 12.0,
            windDirectionDegrees = 190,
            condition = WeatherConditionType.SUNNY,
            conditionLabel = "Clear",
            wmoCode = 0,
            isDay = true,
            latitude = 28.61,
            longitude = 77.21,
            locationName = "NH-44",
            observedAtMs = fetchedAtMs,
            fetchedAtMs = fetchedAtMs,
            rainProbabilityPercent = 10,
            precipitationMm = 0.0,
            windGustsKmh = 18.0,
            visibilityKm = 10.0,
            uvIndex = 6.0
        )
        return WeatherForecastBundle(
            current = obs,
            hourly = hours,
            daily = (0..6).map { d ->
                DailyWeatherPoint(
                    dateEpochMs = makeTimeMs(0, d),
                    dayIndexFromToday = d,
                    minTempC = 17.0,
                    maxTempC = 30.0,
                    condition = WeatherConditionType.SUNNY,
                    conditionLabel = "Clear",
                    wmoCode = 0,
                    rainProbabilityPercent = 10,
                    precipitationSumMm = 0.0,
                    windSpeedMaxKmh = 16.0,
                    windGustsMaxKmh = 22.0,
                    dominantWindDirectionDegrees = 190,
                    uvIndexMax = 6.5
                )
            },
            alerts = emptyList(),
            fetchedAtMs = fetchedAtMs
        )
    }

    // Acceptance Test 1 & 2: Separate Weather & Work Planning pages + Contextual Weather Settings Popup isolated
    @Test
    fun test01_02_separatePagesAndIsolatedWeatherSettingsPopup() {
        val nowMs = makeTimeMs(8)
        val bundle = make24hBundle(nowMs)
        val assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, ContractSpecificationConfig(), referenceTimeMs = nowMs)
        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(HighwayWorkActivity.BC_LAYING, bundle.hourly, ContractSpecificationConfig())
        val pred = WorkTargetPredictionEngine.predictWorkTarget(WorkTargetInput(activity = HighwayWorkActivity.BC_LAYING), timeline)
        val risk = WorkTargetPredictionEngine.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, timeline, pred)
        var switchedToWorkPlan = false

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                var ws by remember { mutableStateOf(WeatherIntelligenceSettings(animationsEnabled = false)) }
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Available(bundle.current),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-44",
                    is24HourFormat = true,
                    settings = ws,
                    assessments = assessments,
                    selectedActivity = HighwayWorkActivity.BC_LAYING,
                    hourlyWorkTimeline = timeline,
                    siteMeasurements = ActualSiteMeasurements(),
                    workTargetInput = WorkTargetInput(activity = HighwayWorkActivity.BC_LAYING),
                    workTargetPrediction = pred,
                    delayRiskSummary = risk,
                    actionableAlerts = emptyList(),
                    onSelectActivity = {},
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onUpdateWorkTargetInput = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = nowMs,
                    onUpdateWeatherSettings = { ws = it(ws) },
                    onNavigateToWorkPlanning = { switchedToWorkPlan = true }
                )
            }
        }

        // No embedded Work Planning section on Weather Page
        composeRule.onNodeWithTag("highway_work_planning_toggle").assertDoesNotExist()
        // Open contextual Weather Settings Popup
        composeRule.onNodeWithTag("weather_settings_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_settings_popup").assertIsDisplayed()
        // Contains Weather Display / Prediction controls, NOT Work Planning controls
        composeRule.onNodeWithTag("ws_toggle_hourly").assertExists()
        composeRule.onNodeWithTag("wps_auto_save").assertDoesNotExist()
        // Close popup and verify smooth navigation to separate Work Planning page
        composeRule.onNodeWithTag("weather_settings_close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_open_work_planning_btn").performClick()
        assertTrue(switchedToWorkPlan)
    }

    // Acceptance Test 3 & 13: Separate Work Planning Page + Contextual Settings Popup isolated + Rolling Diagram renders
    @Test
    fun test03_13_separateWorkPlanningPagePopupAndRollingDiagram() {
        val bundle = make24hBundle()
        var navigatedToWeather = false

        composeRule.setContent {
            MyApplicationTheme(themeMode = "amoled") {
                var plan by remember {
                    mutableStateOf(
                        HighwayWorkPlan(
                            projectName = "NH-44 Pkg IV",
                            startChainage = "188+840",
                            endChainage = "189+240",
                            workWidthMeters = 7.0,
                            layerThicknessMm = 40.0,
                            materialDensityTPerM3 = 2.38
                        )
                    )
                }
                var wpSettings by remember { mutableStateOf(WorkPlanningSettings()) }
                WorkPlanningScreen(
                    plan = plan,
                    savedPlans = listOf(plan),
                    machineryCatalog = MachineryCatalogRepository.builtInCatalog(),
                    isRefreshingCatalog = false,
                    workPlanningSettings = wpSettings,
                    saveStatus = WorkPlanSaveStatus.SAVED,
                    localBackups = emptyList(),
                    forecastBundle = bundle,
                    contractSpec = ContractSpecificationConfig(),
                    siteMeasurements = ActualSiteMeasurements(),
                    animationsEnabled = false,
                    onUpdatePlan = { plan = it },
                    onNewPlan = {},
                    onSavePlan = {},
                    onRetrySave = {},
                    onOpenPlan = {},
                    onDuplicatePlan = {},
                    onRenamePlan = { _, _ -> },
                    onDeletePlan = {},
                    onUpdateWorkPlanningSettings = { wpSettings = it(wpSettings) },
                    onRefreshMachineryCatalog = {},
                    onBuildBackupJson = { "{}" },
                    onInspectBackupJson = {
                        WorkPlanBackupManager(ApplicationProvider.getApplicationContext()).inspectBackupJson(it, emptyList())
                    },
                    onApplyBackupImport = { _, _ -> },
                    onSaveLocalSnapshot = {},
                    onSafeRestoreLocalBackup = {},
                    onNavigateToWeather = { navigatedToWeather = true },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("work_planning_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("wp_top_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("wp_save_status_badge").assertIsDisplayed()

        // Contextual Work Planning Settings Popup contains ONLY Work Planning preferences
        composeRule.onNodeWithTag("work_planning_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("work_planning_settings_popup").assertIsDisplayed()
        composeRule.onNodeWithTag("wps_show_diagram").assertExists()
        composeRule.onNodeWithTag("ws_toggle_hourly").assertDoesNotExist()
        composeRule.onNodeWithTag("wp_settings_close").performClick()
        composeRule.waitForIdle()

        // Expand Section 5 (Compaction & Rolling) and verify Rolling Pattern Diagram renders
        composeRule.onNodeWithTag("work_planning_list").performScrollToNode(hasTestTag("wp_toggle_sec5"))
        composeRule.onNodeWithTag("wp_toggle_sec5").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("work_planning_list").performScrollToNode(hasTestTag("rolling_pattern_diagram"))
        composeRule.onNodeWithTag("rolling_pattern_diagram").assertExists()

        // Switch back to Weather Page smoothly
        composeRule.onNodeWithTag("wp_open_weather_btn").performClick()
        assertTrue(navigatedToWeather)
    }

    // Acceptance Test 5: Three distinct themes — Light (White), Dark (Deep Charcoal), AMOLED (True Black #000000)
    @Test
    fun test05_threeDistinctThemesLightDarkCharcoalAndAmoledTrueBlack() {
        var lightBg: Color? = null
        var darkBg: Color? = null
        var amoledBg: Color? = null
        var darkIsAmoled = true
        var amoledIsAmoled = false

        composeRule.setContent {
            MyApplicationTheme(themeMode = "light") {
                lightBg = CtecsTheme.colors.background
            }
            MyApplicationTheme(themeMode = "dark") {
                darkBg = CtecsTheme.colors.background
                darkIsAmoled = CtecsTheme.colors.isAmoled
            }
            MyApplicationTheme(themeMode = "amoled") {
                amoledBg = CtecsTheme.colors.background
                amoledIsAmoled = CtecsTheme.colors.isAmoled
            }
        }
        composeRule.waitForIdle()

        assertEquals(Color(0xFFFFFFFF), lightBg)
        assertEquals(Color(0xFF12151C), darkBg)
        assertEquals(Color(0xFF000000), amoledBg)
        assertNotEquals("Dark and AMOLED backgrounds must be visually distinct", darkBg, amoledBg)
        assertFalse(darkIsAmoled)
        assertTrue(amoledIsAmoled)
    }

    // Acceptance Test 6: Chainage math across km transition, reversed warning, invalid format, and manual override
    @Test
    fun test06_chainageMathKmTransitionsReversedInvalidAndManualOverride() {
        val crossKm = ChainageMath.calculateLength("188+840", "189+240")
        assertEquals(400.0, crossKm.calculatedLengthMeters ?: 0.0, 0.01)
        assertFalse(crossKm.isReversed)
        assertNull(crossKm.warningMessage)

        val reversed = ChainageMath.calculateLength("189+240", "188+840")
        assertEquals(400.0, reversed.calculatedLengthMeters ?: 0.0, 0.01)
        assertTrue(reversed.isReversed)
        assertNotNull(reversed.warningMessage)

        val invalid = ChainageMath.calculateLength("ABC+XYZ", "189+240")
        assertNull(invalid.calculatedLengthMeters)
        assertNotNull(invalid.warningMessage)

        val planAuto = HighwayWorkPlan(startChainage = "188+840", endChainage = "189+240", workLengthMeters = 999.0, useManualLengthOverride = false)
        assertEquals(400.0, planAuto.effectiveLengthMeters ?: 0.0, 0.01)
        assertTrue(planAuto.isLengthFromChainage)

        val planManual = planAuto.copy(useManualLengthOverride = true)
        assertEquals(999.0, planManual.effectiveLengthMeters ?: 0.0, 0.01)
        assertFalse(planManual.isLengthFromChainage)
    }

    // Acceptance Test 7: Width & Lane inputs NEVER double-multiply + multi-stretch area sum
    @Test
    fun test07_widthNeverDoubleMultipliesAndSupportsMultiStretch() {
        val planExplicitWorkW = HighwayWorkPlan(
            startChainage = "10+000",
            endChainage = "11+000",
            workWidthMeters = 7.0,
            individualLaneWidthMeters = 3.5,
            numberOfLanes = 4 // Must NOT multiply 3.5 * 4 = 14 when workWidthMeters=7.0 is set!
        )
        assertEquals(7.0, planExplicitWorkW.effectiveWorkWidthMeters ?: 0.0, 0.01)

        val planWithStretches = planExplicitWorkW.copy(
            stretches = listOf(RoadStretchWidthItem(label = "Slip Road", lengthMeters = 200.0, widthMeters = 3.5))
        )
        val calc = HighwayWorkPlanningEngine.calculatePlan(planWithStretches, null)
        // Main 1000 * 7 = 7000 m² + Stretch 200 * 3.5 = 700 m² -> Total 7700 m²
        assertEquals(7700.0, calc.workAreaSqm ?: 0.0, 0.01)
    }

    // Acceptance Test 8 & 15: Day, Night (midnight crossing), Custom multi-period shifts & shift-exact weather
    @Test
    fun test08_15_dayNightMidnightCrossingCustomPeriodsAndShiftExactWeather() {
        val nightShift = ShiftTimingCalculator.calculate(
            shiftType = WorkShiftType.NIGHT_SHIFT,
            startDateIso = "2026-11-25",
            startHour24 = 20,
            startMinute = 0,
            endDateIso = "2026-11-26",
            endHour24 = 6,
            endMinute = 0,
            breakDurationMinutes = 30,
            mobilisationMinutes = 30,
            loadingWaitingMinutes = 15,
            workingPeriods = emptyList()
        )
        assertTrue(nightShift.crossesMidnight)
        assertEquals(10.0, nightShift.totalShiftHours, 0.01)
        assertEquals(9.5, nightShift.effectiveWorkingHours, 0.01)
        assertEquals(listOf(20, 21, 22, 23, 0, 1, 2, 3, 4, 5), nightShift.activeHours24List)

        val customShift = ShiftTimingCalculator.calculate(
            shiftType = WorkShiftType.CUSTOM_SHIFT,
            startDateIso = "2026-11-25",
            startHour24 = 8,
            startMinute = 0,
            endDateIso = "2026-11-25",
            endHour24 = 17,
            endMinute = 0,
            breakDurationMinutes = 0,
            mobilisationMinutes = 0,
            loadingWaitingMinutes = 0,
            workingPeriods = listOf(
                WorkingTimePeriod(startDateIso = "2026-11-25", startHour24 = 7, endDateIso = "2026-11-25", endHour24 = 11),
                WorkingTimePeriod(startDateIso = "2026-11-25", startHour24 = 14, endDateIso = "2026-11-25", endHour24 = 18)
            )
        )
        assertEquals(8.0, customShift.totalShiftHours, 0.01)
        assertEquals(listOf(7, 8, 9, 10, 14, 15, 16, 17), customShift.activeHours24List)

        // Night shift weather alignment evaluates overnight hours 20..05 from forecast bundle
        val nightPlan = HighwayWorkPlan(
            shiftType = WorkShiftType.NIGHT_SHIFT,
            workDateIso = "2026-11-25",
            shiftStartDateIso = "2026-11-25",
            shiftEndDateIso = "2026-11-26",
            plannedStartHour24 = 20,
            plannedEndHour24 = 6,
            breakDurationMinutes = 30
        )
        val bundle = make24hBundle(makeTimeMs(8))
        val calc = HighwayWorkPlanningEngine.calculatePlan(nightPlan, bundle, nowMs = makeTimeMs(8))
        assertEquals(listOf(20, 21, 22, 23, 0, 1, 2, 3, 4, 5), calc.hourlySlots.map { it.hour24 })
    }

    // Acceptance Test 9 & 10: Never invent density; Compacted vs Loose thickness requires approved factor
    @Test
    fun test09_10_neverInventsDensityOrLooseMultiplier() {
        val planNoDensity = HighwayWorkPlan(
            startChainage = "10+000",
            endChainage = "11+000",
            workWidthMeters = 7.0,
            layerThicknessMm = 40.0,
            materialDensityTPerM3 = null
        )
        val calcNoDensity = HighwayWorkPlanningEngine.calculatePlan(planNoDensity, null)
        assertEquals(7000.0, calcNoDensity.workAreaSqm ?: 0.0, 0.01)
        assertEquals(280.0, calcNoDensity.materialVolumeM3 ?: 0.0, 0.01)
        assertNull("Density must never be invented when user did not enter approved JMF density", calcNoDensity.estimatedMaterialTonnes)

        val calcWithDensity = HighwayWorkPlanningEngine.calculatePlan(planNoDensity.copy(materialDensityTPerM3 = 2.40), null)
        assertEquals(672.0, calcWithDensity.estimatedMaterialTonnes ?: 0.0, 0.01)

        // Loose thickness: without approved factor returns null; with approved factor 1.25 returns 50 mm
        val unverifiedSpec = ThicknessCompactionSpec(approvedLooseToCompactedFactor = null)
        assertNull(unverifiedSpec.recommendedLooseThicknessMm(40.0))

        val verifiedSpec = ThicknessCompactionSpec(
            approvedLooseToCompactedFactor = 1.25,
            achievedCompactedThicknessMm = 42.0,
            verifiedToleranceMm = 3.0
        )
        assertEquals(50.0, verifiedSpec.recommendedLooseThicknessMm(40.0) ?: 0.0, 0.01)
        assertEquals(2.0, verifiedSpec.thicknessVariationMm(40.0) ?: 0.0, 0.01)
        assertEquals(true, verifiedSpec.isWithinVerifiedTolerance(40.0))
    }

    // Acceptance Test 11, 12, 14, 17: Material-specific temps, activity-specific rolling/PTR, traceable MoRTH, 23+ activities
    @Test
    fun test11_12_14_17_materialTempsActivityRollingTraceableMorthAnd23Activities() {
        assertTrue("Must support 23+ highway construction activities", PlanningWorkActivity.entries.size >= 23)

        val bcTemp = WorkPlanningStandardEngine.temperatureGuidanceForActivity(PlanningWorkActivity.BC_LAYING, "PMB-40")
        assertTrue(bcTemp.isBituminous)
        assertTrue(bcTemp.guidanceLines.any { it.contains("PMB", ignoreCase = true) })

        val pqcTemp = WorkPlanningStandardEngine.temperatureGuidanceForActivity(PlanningWorkActivity.PQC, "M40")
        assertFalse(pqcTemp.isBituminous)
        assertTrue(pqcTemp.guidanceLines.any { it.contains("concrete", ignoreCase = true) })

        // PTR recommended for BC, NOT recommended for SMA / GSB / Road Marking
        val bcComp = WorkPlanningStandardEngine.compactionGuidanceForActivity(PlanningWorkActivity.BC_LAYING, null, "HAMM HD 99", "HAMM GRW 280i")
        assertTrue(bcComp.isCompactionApplicable)
        assertTrue(bcComp.isPtrRecommended)
        assertEquals(3, bcComp.stages.size)
        assertTrue(bcComp.stages.all { it.isProvisional })

        val smaComp = WorkPlanningStandardEngine.compactionGuidanceForActivity(PlanningWorkActivity.SMA_LAYING, 8, "HAMM HD 99", null)
        assertFalse("PTR must NOT be recommended for SMA", smaComp.isPtrRecommended)
        assertTrue(smaComp.stages.none { it.isProvisional })

        val markingComp = WorkPlanningStandardEngine.compactionGuidanceForActivity(PlanningWorkActivity.ROAD_MARKING, null, null, null)
        assertFalse(markingComp.isCompactionApplicable)

        // MoRTH 5th Rev 2013 vs 6th Rev 2026 traceable references; generic works flagged for verification
        val rev5 = WorkPlanningStandardEngine.referencesForActivity(PlanningWorkActivity.BC_LAYING, MoRTHRevision.FIFTH_REVISION_2013, "JMF-BC-01", "TRIAL-01")
        val rev6 = WorkPlanningStandardEngine.referencesForActivity(PlanningWorkActivity.BC_LAYING, MoRTHRevision.SIXTH_REVISION_2026)
        assertTrue(rev5.any { it.clauseOrTableReference.contains("507") })
        assertTrue(rev5.any { it.clauseOrTableReference == "JMF-BC-01" })
        assertTrue(rev6.any { it.editionOrRevision.contains("2026") })

        val genericRefs = WorkPlanningStandardEngine.referencesForActivity(PlanningWorkActivity.OTHER_HIGHWAY_WORKS, MoRTHRevision.FIFTH_REVISION_2013)
        assertTrue(genericRefs.any { it.verificationStatus == WorkPlanningStandardEngine.SPEC_VERIFICATION_REQUIRED })
    }

    // Acceptance Test 16, 18, 19, 20: Preferences persistence, wind/weather settings round-trip, & WorkPlanningSettings persistence
    @Test
    fun test16_18_19_20_preferencesPersistenceAndSettingsIsolation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        val wpSettings = WorkPlanningSettings(
            defaultActivity = PlanningWorkActivity.DBM_LAYING,
            defaultShift = WorkShiftType.NIGHT_SHIFT,
            machineryFilter = MachineryFilterPreference.LATEST_CURRENT,
            morthRevision = MoRTHRevision.SIXTH_REVISION_2026,
            backupFrequency = BackupFrequency.DAILY,
            pdfDefaultScope = PdfExportScope.MATERIAL_COMPACTION,
            showRollingDiagram = false
        )
        prefs.updateWorkPlanningSettings(wpSettings)

        val weatherSettings = WeatherIntelligenceSettings(
            windSpeedUnit = WindSpeedUnit.MS,
            forecastDaysCount = 5,
            predictionIntervalHours = 2,
            showHourlyForecastSection = false
        )
        prefs.updateWeatherIntelligenceSettings(weatherSettings)

        val reloaded = PreferencesManager(context)
        val loadedWp = reloaded.getInitialWorkPlanningSettings()
        val loadedW = reloaded.getInitialWeatherIntelligenceSettings()

        assertEquals(PlanningWorkActivity.DBM_LAYING, loadedWp.defaultActivity)
        assertEquals(WorkShiftType.NIGHT_SHIFT, loadedWp.defaultShift)
        assertEquals(MachineryFilterPreference.LATEST_CURRENT, loadedWp.machineryFilter)
        assertEquals(MoRTHRevision.SIXTH_REVISION_2026, loadedWp.morthRevision)
        assertEquals(BackupFrequency.DAILY, loadedWp.backupFrequency)
        assertFalse(loadedWp.showRollingDiagram)

        assertEquals(WindSpeedUnit.MS, loadedW.windSpeedUnit)
        assertEquals(5, loadedW.forecastDaysCount)
        assertEquals(2, loadedW.predictionIntervalHours)
        assertFalse(loadedW.showHourlyForecastSection)
    }

    // Acceptance Tests 21, 22, 23: Backup Export, Validation, Duplicate Handling (Skip/Replace/Import as New) & Safe Restore
    @Test
    fun test21_22_23_backupExportValidationDuplicateHandlingAndSafeRestore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mgr = WorkPlanBackupManager(context)

        val existingA = HighwayWorkPlan(planId = "plan-1", planTitle = "Original BC Plan", projectName = "NH-44", startChainage = "10+000", endChainage = "10+500")
        val existingB = HighwayWorkPlan(planId = "plan-2", planTitle = "Existing WMM Plan", projectName = "NH-44")
        val incomingUpdatedA = existingA.copy(planTitle = "Updated BC Plan")
        val incomingNewC = HighwayWorkPlan(planId = "plan-3", planTitle = "New DBM Plan", projectName = "NH-48")

        val json = mgr.createBackupJson(listOf(incomingUpdatedA, incomingNewC), WorkPlanningSettings())

        // Corrupt JSON rejected cleanly
        val bad = mgr.inspectBackupJson("{corrupt", listOf(existingA, existingB))
        assertFalse(bad.isValid)
        assertNotNull(bad.errorMessage)

        // Valid JSON inspected with duplicate detection
        val insp = mgr.inspectBackupJson(json, listOf(existingA, existingB))
        assertTrue(insp.isValid)
        assertEquals(setOf("plan-1"), insp.duplicatePlanIds)

        // SKIP keeps original plan-1 and adds plan-3
        val skipped = mgr.applyImport(listOf(existingA, existingB), insp, BackupDuplicateResolution.SKIP)
        assertEquals(1, skipped.skippedCount)
        assertEquals(1, skipped.importedCount)
        assertEquals("Original BC Plan", skipped.mergedPlans.first { it.planId == "plan-1" }.planTitle)

        // REPLACE updates plan-1 and adds plan-3
        val replaced = mgr.applyImport(listOf(existingA, existingB), insp, BackupDuplicateResolution.REPLACE)
        assertEquals(1, replaced.replacedCount)
        assertEquals("Updated BC Plan", replaced.mergedPlans.first { it.planId == "plan-1" }.planTitle)

        // IMPORT_AS_NEW keeps original plan-1 AND creates new copy + plan-3
        val asNew = mgr.applyImport(listOf(existingA, existingB), insp, BackupDuplicateResolution.IMPORT_AS_NEW)
        assertEquals(4, asNew.mergedPlans.size)

        // Safe Restore fails cleanly on invalid backup without touching data
        val failRestore = mgr.safeRestoreBackup("{broken}", listOf(existingA), WorkPlanningSettings())
        assertTrue(failRestore.isFailure)

        // Safe Restore creates pre-restore safety backup before restoring valid backup
        val okRestore = mgr.safeRestoreBackup(json, listOf(existingA, existingB), WorkPlanningSettings())
        assertTrue(okRestore.isSuccess)
        val restoreRes = okRestore.getOrThrow()
        assertNotNull(restoreRes.safetyBackupFileName)
        assertTrue(restoreRes.safetyBackupFileName!!.startsWith("safety_pre_restore"))
        assertEquals(2, restoreRes.mergedPlans.size)
    }

    // Acceptance Test 24: Real A4 PDF generation from actual plan data across scopes
    @Test
    fun test24_a4PdfGenerationProducesRealValidPdfAcrossScopes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = MachineryCatalogRepository.builtInCatalog()
        val plan = HighwayWorkPlan(
            projectName = "NH-44 Package III",
            roadOrHighwayName = "NH-44",
            workDateIso = "2026-11-25",
            startChainage = "188+840",
            endChainage = "189+240",
            workWidthMeters = 7.0,
            layerThicknessMm = 40.0,
            materialDensityTPerM3 = 2.38,
            preparedBy = "A. Sharma",
            checkedBy = "R. Verma",
            approvedBy = "S. Rao",
            machinery = listOf(
                PlannedMachineryItem(catalogEntry = catalog.first { it.id == "hmp_apollo_anp1500" }, actualOperatingCapacity = 110.0),
                PlannedMachineryItem(catalogEntry = catalog.first { it.id == "paver_vogele_1900_3g" }, actualOperatingCapacity = 160.0)
            ),
            transport = TransportPlanningInput(roadLeadDistanceKm = 12.0, dumperCount = 8, actualDumperPayloadTonnes = 18.0),
            compaction = CompactionPlanningInput(tandemRollerDrumWidthM = 1.68, tandemRollerCount = 2, requiredPasses = 8)
        )
        val calc = HighwayWorkPlanningEngine.calculatePlan(plan, make24hBundle())

        PdfExportScope.entries.forEach { scope ->
            val pdfFile = WorkPlanPdfExporter.generatePdf(
                context = context,
                plan = plan,
                calc = calc,
                scope = scope,
                includeRollingDiagram = true,
                includeApprovalBlock = true
            )
            assertTrue("PDF must exist for scope $scope", pdfFile.exists())
            assertTrue("PDF must be non-empty for scope $scope", pdfFile.length() > 500L)
            val header = pdfFile.inputStream().use { inp ->
                val buf = ByteArray(5)
                inp.read(buf)
                String(buf, Charsets.US_ASCII)
            }
            assertEquals("Output must be a genuine PDF file (%PDF-)", "%PDF-", header)
        }
    }

    // Acceptance Test 4 & 25: Main Settings excludes weather_intelligence; all unrelated CTECS tabs intact & no Site Register
    @Test
    fun test04_25_tabsIntactNoSiteRegisterAndWorkPlanningTabRegistered() {
        val tabNames = AppTab.entries.map { it.name }
        assertTrue(tabNames.contains("WEATHER"))
        assertTrue(tabNames.contains("WORK_PLANNING"))
        assertTrue(tabNames.contains("CAMERA"))
        assertTrue(tabNames.contains("PHOTOS"))
        assertTrue(tabNames.contains("ROUTES"))
        assertTrue(tabNames.contains("SURVEY"))
        assertTrue(tabNames.contains("HIGHWAY_TOOLS"))
        assertTrue(tabNames.contains("SETTINGS"))
        assertFalse(tabNames.any { it.contains("REGISTER", ignoreCase = true) })
    }
}
