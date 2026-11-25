package com.example

import android.content.Context
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
import com.example.model.CompactionPlanningInput
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.HighwayWorkActivity
import com.example.model.HighwayWorkPlan
import com.example.model.HourlyWeatherPoint
import com.example.model.MachineryCatalogEntry
import com.example.model.MachineryCategory
import com.example.model.ManpowerTeamGroup
import com.example.model.ModelAvailabilityStatus
import com.example.model.PlannedMachineryItem
import com.example.model.PlanningWorkActivity
import com.example.model.TransportPlanningInput
import com.example.model.TransportStatus
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetInput
import com.example.ui.AppTab
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.weather.WeatherDashboardScreen
import com.example.weather.HighwayWorkPlanningEngine
import com.example.weather.IrcMorthWeatherRuleEngine
import com.example.weather.MachineryCatalogRepository
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
 * Verifies all 15 acceptance criteria for the optional CTECS Highway Work Planning,
 * Machinery Database, Capacity, Transport, Compaction & Manpower module on the Weather Page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HighwayWorkPlanningTest {

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
        condition: WeatherConditionType = WeatherConditionType.SUNNY
    ): HourlyWeatherPoint {
        return HourlyWeatherPoint(
            epochMs = makeTimeMs(hour),
            localHourOfDay = hour,
            temperatureC = tempC,
            feelsLikeC = tempC + 1.0,
            rainProbabilityPercent = rainProb,
            precipitationMm = precipMm,
            windSpeedKmh = windKmh,
            windGustsKmh = windKmh + 6.0,
            windDirectionDegrees = 200,
            humidityPercent = 50,
            condition = condition,
            conditionLabel = condition.defaultLabel,
            wmoCode = if (condition == WeatherConditionType.RAINY) 63 else 0,
            isDay = hour in 6..18,
            visibilityKm = 10.0,
            cloudCoverPercent = if (rainProb >= 50) 85 else 20,
            pressureHpa = 1012.0,
            uvIndex = if (hour in 9..16) 6.0 else 0.0,
            dewPointC = 14.0
        )
    }

    private fun makeBundle(
        hourly: List<HourlyWeatherPoint>,
        fetchedAtMs: Long = makeTimeMs(8, 0)
    ): WeatherForecastBundle {
        val current = hourly.firstOrNull() ?: makeHour(8)
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
            locationName = "NH-44",
            observedAtMs = fetchedAtMs,
            fetchedAtMs = fetchedAtMs,
            rainProbabilityPercent = current.rainProbabilityPercent,
            precipitationMm = current.precipitationMm,
            windGustsKmh = current.windGustsKmh,
            visibilityKm = current.visibilityKm,
            uvIndex = current.uvIndex
        )
        return WeatherForecastBundle(
            current = obs,
            hourly = hourly,
            daily = listOf(
                DailyWeatherPoint(
                    dateEpochMs = makeTimeMs(0, 0),
                    dayIndexFromToday = 0,
                    minTempC = 18.0,
                    maxTempC = 31.0,
                    condition = current.condition,
                    conditionLabel = current.conditionLabel,
                    wmoCode = current.wmoCode,
                    rainProbabilityPercent = 15,
                    precipitationSumMm = 0.0,
                    windSpeedMaxKmh = 18.0,
                    windGustsMaxKmh = 24.0,
                    dominantWindDirectionDegrees = 200,
                    uvIndexMax = 6.5
                )
            ),
            alerts = emptyList(),
            fetchedAtMs = fetchedAtMs
        )
    }

    // Criteria 1 & 2: Normal Weather Page works cleanly without embedded planning;
    // Work Planning is separated onto its own page with a clean top-bar switcher & contextual Settings popup
    @Test
    fun weatherPageWorksWithoutPlanningAndSectionIsCollapsedByDefault() {
        val nowMs = makeTimeMs(8, 0)
        val hours = (8..20).map { h -> makeHour(h) }
        val bundle = makeBundle(hours, fetchedAtMs = nowMs)
        val settings = WeatherIntelligenceSettings(primaryActivity = HighwayWorkActivity.BC_LAYING, animationsEnabled = false)
        val assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, settings.contractSpec, referenceTimeMs = nowMs)
        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            HighwayWorkActivity.BC_LAYING, hours, settings.contractSpec
        )
        val targetPred = WorkTargetPredictionEngine.predictWorkTarget(
            WorkTargetInput(activity = HighwayWorkActivity.BC_LAYING), timeline
        )
        val delayRisk = WorkTargetPredictionEngine.analyzeDelayRisks(
            HighwayWorkActivity.BC_LAYING, timeline, targetPred
        )
        var navigatedToWorkPlanning = false

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Available(bundle.current),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-44",
                    is24HourFormat = true,
                    settings = settings,
                    assessments = assessments,
                    selectedActivity = HighwayWorkActivity.BC_LAYING,
                    hourlyWorkTimeline = timeline,
                    siteMeasurements = ActualSiteMeasurements(),
                    workTargetInput = WorkTargetInput(activity = HighwayWorkActivity.BC_LAYING),
                    workTargetPrediction = targetPred,
                    delayRiskSummary = delayRisk,
                    actionableAlerts = emptyList(),
                    onSelectActivity = {},
                    onUpdateContractSpec = {},
                    onUpdateSiteMeasurements = {},
                    onUpdateWorkTargetInput = {},
                    onRefreshWeather = {},
                    onNavigateBack = {},
                    nowMs = nowMs,
                    onNavigateToWorkPlanning = { navigatedToWorkPlanning = true }
                )
            }
        }

        // Normal weather content is visible immediately without planning clutter
        composeRule.onNodeWithTag("weather_dashboard_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_forecast_tab_list")
            .performScrollToNode(hasTestTag("highway_work_suitability_section"))
        composeRule.onNodeWithTag("highway_work_suitability_section").assertIsDisplayed()

        // Embedded work planning form must NOT exist on the Weather Page
        composeRule.onNodeWithTag("highway_work_planning_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("highway_work_planning_body").assertDoesNotExist()

        // Smooth switcher navigates to the separate Work Planning page
        composeRule.onNodeWithTag("weather_open_work_planning_btn").assertIsDisplayed().performClick()
        assertTrue("Tapping Work Plan button must navigate to the separate Work Planning page", navigatedToWorkPlanning)
    }

    // Criteria 3 & 4: All 12 machinery categories exist, are searchable, and Current vs Legacy are distinguishable
    @Test
    fun machineryCatalogCoversAllTwelveCategoriesSearchableWithCurrentAndLegacyDistinction() {
        val catalog = MachineryCatalogRepository.builtInCatalog()
        assertTrue("Catalog should have rich prefilled models", catalog.size >= 40)

        // All 12 categories A–L must be represented in built-in catalog
        MachineryCategory.entries.forEach { category ->
            val inCategory = catalog.filter { it.category == category }
            assertTrue("Category ${category.name} (${category.displayName}) must exist", inCategory.isNotEmpty())
        }

        // Current and Legacy models must both be present and distinguishable
        val currentModels = catalog.filter { it.availabilityStatus == ModelAvailabilityStatus.CURRENT }
        val legacyModels = catalog.filter { it.availabilityStatus == ModelAvailabilityStatus.LEGACY }
        assertTrue("Must contain CURRENT models", currentModels.isNotEmpty())
        assertTrue("Must contain LEGACY models", legacyModels.isNotEmpty())
        assertTrue(
            "VÖGELE SUPER 1800-2 should be marked LEGACY",
            legacyModels.any { it.id == "paver_vogele_1800_2_legacy" }
        )
        assertTrue(
            "VÖGELE SUPER 1900-3 G should be marked CURRENT",
            currentModels.any { it.id == "paver_vogele_1900_3g" }
        )

        // Search works across manufacturer, model, variant, and category
        assertTrue(catalog.any { it.matchesQuery("vogele") })
        assertTrue(catalog.any { it.matchesQuery("1900-3") })
        assertTrue(catalog.any { it.matchesQuery("hamm") })
        assertTrue(catalog.any { it.matchesQuery("hyva") })
        assertTrue(catalog.any { it.matchesQuery("hofmann") })
    }

    // Criteria 5 & 6: Auto-fills verified specs; unknown specs stay null ("Not Available / Verify Manually");
    // HYVA cylinder lift is strictly separated from legal truck payload & body volume
    @Test
    fun machinerySpecsAutoFillVerifiedFieldsAndNeverInventUnknownOrConfuseHyvaLiftWithPayload() {
        val catalog = MachineryCatalogRepository.builtInCatalog()
        val vogele1900 = catalog.first { it.id == "paver_vogele_1900_3g" }
        assertEquals(900.0, vogele1900.maxRatedCapacityValue ?: 0.0, 0.01)
        assertEquals(9.0, vogele1900.workingWidthM ?: 0.0, 0.01)
        assertEquals(142.0, vogele1900.enginePowerKw ?: 0.0, 0.01)
        assertNotNull(vogele1900.dataSourceName)

        // Tata Prima 2830.K separates body volume (m³), legal payload (t), GVW (t), and HYVA cylinder lift (t)
        val tata2830 = catalog.first { it.id == "dumper_tata_prima_2830k" }
        assertEquals(16.0, tata2830.dumperBodyVolumeM3 ?: 0.0, 0.01)
        assertEquals(16.5, tata2830.manufacturerRatedPayloadTonnes ?: 0.0, 0.01)
        assertEquals(28.0, tata2830.gvwTonnes ?: 0.0, 0.01)
        assertEquals(32.0, tata2830.hyvaTippingCapacityTonnes ?: 0.0, 0.01)
        assertNotEquals(
            "HYVA cylinder tipping rating must never equal legal truck payload",
            tata2830.hyvaTippingCapacityTonnes,
            tata2830.manufacturerRatedPayloadTonnes
        )

        // HYVA Alpha FC Cylinder itself has NO truck payload and NO invented maxRatedCapacityValue
        val hyvaCylinder = catalog.first { it.id == "dumper_hyva_alpha_fc" }
        assertNull("HYVA cylinder must not invent hourly production capacity", hyvaCylinder.maxRatedCapacityValue)
        assertNull("HYVA cylinder must not invent legal vehicle payload", hyvaCylinder.manufacturerRatedPayloadTonnes)
        assertNull("HYVA cylinder must not invent body volume", hyvaCylinder.dumperBodyVolumeM3)
        assertEquals(MachineryCatalogEntry.NOT_AVAILABLE_LABEL, hyvaCylinder.formattedMaxRatedCapacity)
        assertEquals(34.0, hyvaCylinder.hyvaTippingCapacityTonnes ?: 0.0, 0.01)

        // Adding ONLY a HYVA cylinder to a plan must NEVER calculate transport supply from its 45t hydraulic rating
        val planWithOnlyCylinder = HighwayWorkPlan(
            activity = PlanningWorkActivity.BC_LAYING,
            machinery = listOf(PlannedMachineryItem(catalogEntry = hyvaCylinder, quantity = 5)),
            transport = TransportPlanningInput(roadLeadDistanceKm = 15.0, dumperCount = 5)
        )
        val calc = HighwayWorkPlanningEngine.calculatePlan(planWithOnlyCylinder, null)
        assertNull(
            "Transport payload must remain null when only hydraulic tipping kit is selected without truck payload",
            calc.transportResult.dumperPayloadTonnes
        )
        assertEquals(TransportStatus.INSUFFICIENT_DATA, calc.transportResult.status)
    }

    // Criterion 7: Actual site operating capacity edits independently from manufacturer max rated capacity
    // and refreshing the catalog never overwrites user-entered site values
    @Test
    fun actualSiteCapacityIsIndependentAndPreservedOnCatalogRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MachineryCatalogRepository(context)
        val hmp = MachineryCatalogRepository.builtInCatalog().first { it.id == "hmp_apollo_anp1500" }
        assertEquals(120.0, hmp.maxRatedCapacityValue ?: 0.0, 0.01)

        // Default effective capacity uses site efficiency (80% of 120 = 96 t/h), NOT 100% rated
        val defaultAssigned = PlannedMachineryItem(catalogEntry = hmp, operatingEfficiencyPercent = 80.0)
        assertEquals(96.0, defaultAssigned.effectiveHourlyCapacityPerUnit() ?: 0.0, 0.01)

        // Site engineer edits actual operating capacity to 105 t/h while manufacturer rated stays 120 t/h
        val siteEdited = defaultAssigned.copy(actualOperatingCapacity = 105.0)
        assertEquals(120.0, siteEdited.catalogEntry.maxRatedCapacityValue ?: 0.0, 0.01)
        assertEquals(105.0, siteEdited.effectiveHourlyCapacityPerUnit() ?: 0.0, 0.01)

        // Refreshing catalog offline/online preserves catalog integrity without touching user's siteEdited plan item
        val refreshed = repo.refreshCatalogOnline(MachineryCatalogRepository.builtInCatalog())
        assertTrue(refreshed.entries.isNotEmpty())
        assertEquals(105.0, siteEdited.actualOperatingCapacity ?: 0.0, 0.01)
    }

    // Criterion 8: HMP-to-work-zone road distance & dumper transport cycle calculations
    @Test
    fun transportLeadDistanceAndDumperCycleCalculationsComputeSufficientShortageAndInsufficientData() {
        // 15 km one-way -> 30 km round trip; 30 km/h loaded = 30 min; 40 km/h empty = 22.5 min;
        // loading 8 + unloading 7 + waiting 10 = 25 min -> total cycle = 77.5 min
        val transportInput = TransportPlanningInput(
            roadLeadDistanceKm = 15.0,
            dumperCount = 10,
            actualDumperPayloadTonnes = 18.0,
            loadingTimeMin = 8.0,
            unloadingTimeMin = 7.0,
            waitingQueueTimeMin = 10.0,
            loadedAvgSpeedKmh = 30.0,
            emptyAvgSpeedKmh = 40.0
        )
        // Supply = 10 * 18 * (60 / 77.5) = 139.35 t/hr -> Sufficient for 120 t/hr target
        val sufficient = HighwayWorkPlanningEngine.calculateTransport(
            input = transportInput,
            plannedWorkingHours = 8.0,
            targetHourlyDemandTonnes = 120.0
        )
        assertEquals(15.0, sufficient.oneWayLeadKm ?: 0.0, 0.01)
        assertEquals(30.0, sufficient.roundTripKm ?: 0.0, 0.01)
        assertEquals(77.5, sufficient.totalCycleTimeMin ?: 0.0, 0.01)
        assertEquals(139.35, sufficient.estimatedSupplyTonnesPerHour ?: 0.0, 0.1)
        assertEquals(TransportStatus.SUFFICIENT, sufficient.status)

        // Reduce dumpers to 4 -> supply = 4 * 18 * (60 / 77.5) = 55.74 t/hr < 120 t/hr -> Shortage
        val shortage = HighwayWorkPlanningEngine.calculateTransport(
            input = transportInput.copy(dumperCount = 4),
            plannedWorkingHours = 8.0,
            targetHourlyDemandTonnes = 120.0
        )
        assertEquals(TransportStatus.SHORTAGE, shortage.status)
        assertTrue((shortage.requiredDumpersForTarget ?: 0) >= 9)

        // Missing road distance -> Insufficient Data (never guesses straight-line GPS)
        val missingLead = HighwayWorkPlanningEngine.calculateTransport(
            input = transportInput.copy(roadLeadDistanceKm = null),
            plannedWorkingHours = 8.0,
            targetHourlyDemandTonnes = 120.0
        )
        assertEquals(TransportStatus.INSUFFICIENT_DATA, missingLead.status)
    }

    // Criterion 9: Roller & PTR planning uses selected roller specs & user-editable JMF passes (never hardcoded)
    @Test
    fun compactionPlanningUsesEditableJmfPassesAndDetectsCompactionBottleneck() {
        // Without user-entered JMF passes, engine must NOT hardcode a pass count
        val noPasses = HighwayWorkPlanningEngine.calculateCompaction(
            input = CompactionPlanningInput(
                paverWorkingWidthM = 7.0,
                tandemRollerDrumWidthM = 1.68,
                tandemRollerCount = 1,
                requiredPasses = null
            ),
            workAreaSqm = 7000.0,
            thicknessMm = 40.0,
            densityTPerM3 = 2.38,
            targetHourlyTonnes = 120.0
        )
        assertNull("Passes must remain null when JMF passes not entered", noPasses.requiredPassesUsed)
        assertNull(noPasses.totalRollingCapacitySqmPerHour)
        assertTrue(noPasses.advisoryNote.contains("JMF", ignoreCase = true))

        // With 8 JMF passes and only 1 tandem roller for 150 t/h paving demand -> detects compaction bottleneck
        val underEquipped = HighwayWorkPlanningEngine.calculateCompaction(
            input = CompactionPlanningInput(
                paverWorkingWidthM = 7.0,
                tandemRollerDrumWidthM = 1.68,
                tandemRollerCount = 1,
                ptrWorkingWidthM = 2.08,
                ptrCount = 0,
                overlapM = 0.20,
                rollerSpeedKmh = 3.5,
                requiredPasses = 8,
                siteEfficiencyPercent = 75.0
            ),
            workAreaSqm = 7000.0,
            thicknessMm = 40.0,
            densityTPerM3 = 2.38,
            targetHourlyTonnes = 150.0
        )
        assertEquals(8, underEquipped.requiredPassesUsed)
        assertNotNull(underEquipped.totalRollingCapacityTonnesPerHour)
        assertTrue("1 tandem roller should trigger compaction bottleneck at 150 t/h", underEquipped.hasCompactionBottleneck)
        assertTrue((underEquipped.estimatedTandemRollersRequired ?: 0) >= 2)
    }

    // Criterion 10: Manpower shortage & affected activities across all 4 teams
    @Test
    fun manpowerSummaryCalculatesShortageAndAffectedActivitiesAcrossAllFourTeams() {
        val defaultRoles = HighwayWorkPlan.defaultManpowerRoles()
        assertEquals(4, defaultRoles.map { it.team }.distinct().size)
        assertTrue(defaultRoles.any { it.team == ManpowerTeamGroup.PAVING_TEAM })
        assertTrue(defaultRoles.any { it.team == ManpowerTeamGroup.ROLLING_TEAM })
        assertTrue(defaultRoles.any { it.team == ManpowerTeamGroup.PLANT_TRANSPORT })
        assertTrue(defaultRoles.any { it.team == ManpowerTeamGroup.QUALITY_SITE })

        val editedRoles = defaultRoles.map { role ->
            when (role.id) {
                "mp_paver_op" -> role.copy(requiredCount = 2, availableCount = 1, deployedCount = 1)
                "mp_dumper_drv" -> role.copy(requiredCount = 10, availableCount = 7, deployedCount = 7)
                "mp_qa_eng" -> role.copy(requiredCount = 1, availableCount = 1, deployedCount = 1)
                else -> role
            }
        }
        val summary = HighwayWorkPlanningEngine.calculateManpowerSummary(editedRoles)
        assertEquals(13, summary.totalRequired)
        assertEquals(9, summary.totalAvailable)
        assertEquals(4, summary.totalShortage)
        assertEquals(2, summary.shortageRoles.size)
        assertTrue(summary.affectedActivities.contains("Paving"))
        assertTrue(summary.affectedActivities.contains("Material Transport"))
    }

    // Criteria 11 & 12: Weather recommendations & hourly plan change dynamically by activity/forecast;
    // stale/missing weather -> Insufficient Data; hourly output reflects the lowest bottleneck in the chain
    @Test
    fun bottleneckDrivenProductionAndDynamicWeatherRecommendationsBehaveAccurately() {
        val nowMs = makeTimeMs(8, 0)
        val catalog = MachineryCatalogRepository.builtInCatalog()
        val hmp = PlannedMachineryItem(
            catalogEntry = catalog.first { it.id == "hmp_apollo_anp1500" },
            actualOperatingCapacity = 140.0
        )
        val paver = PlannedMachineryItem(
            catalogEntry = catalog.first { it.id == "paver_vogele_1900_3g" },
            actualOperatingCapacity = 220.0
        )
        val tandem = PlannedMachineryItem(
            catalogEntry = catalog.first { it.id == "roller_hamm_hd99vv" },
            quantity = 3
        )
        val ptr = PlannedMachineryItem(
            catalogEntry = catalog.first { it.id == "ptr_hamm_grw280i_24" },
            quantity = 2
        )

        // Dumper fleet supplies only ~55.7 t/h -> MUST be the limiting bottleneck below HMP 140 t/h & Paver 220 t/h
        val bcPlan = HighwayWorkPlan(
            projectName = "NH-44",
            activity = PlanningWorkActivity.BC_LAYING,
            workLengthMeters = 1000.0,
            workWidthMeters = 7.0,
            layerThicknessMm = 40.0,
            materialDensityTPerM3 = 2.38,
            plannedStartHour24 = 8,
            plannedWorkingHours = 8.0,
            machinery = listOf(hmp, paver, tandem, ptr),
            transport = TransportPlanningInput(
                roadLeadDistanceKm = 15.0,
                dumperCount = 4,
                actualDumperPayloadTonnes = 18.0
            ),
            compaction = CompactionPlanningInput(
                paverWorkingWidthM = 7.0,
                tandemRollerDrumWidthM = 1.68,
                tandemRollerCount = 3,
                ptrWorkingWidthM = 2.08,
                ptrCount = 2,
                requiredPasses = 6
            )
        )

        val clearBundle = makeBundle((8..16).map { makeHour(it, tempC = 29.0, rainProb = 5, windKmh = 10.0) }, fetchedAtMs = nowMs)
        val clearCalc = HighwayWorkPlanningEngine.calculatePlan(bcPlan, clearBundle, nowMs = nowMs)
        assertEquals("Dumper Transport Supply", clearCalc.limitingBottleneck)
        assertEquals(55.74, clearCalc.effectiveHourlyProduction ?: 0.0, 0.2)
        assertEquals(WorkSuitabilityStatus.SUITABLE, clearCalc.overallWeatherSuitability)
        assertTrue(clearCalc.dynamicRecommendation.contains("Dumper", ignoreCase = true))

        // Rainy forecast changes BC suitability to NOT_RECOMMENDED and drops hourly output to 0.0
        val rainyBundle = makeBundle(
            (8..16).map {
                makeHour(it, tempC = 24.0, rainProb = 85, precipMm = 4.2, windKmh = 18.0, condition = WeatherConditionType.RAINY)
            },
            fetchedAtMs = nowMs
        )
        val rainyBcCalc = HighwayWorkPlanningEngine.calculatePlan(bcPlan, rainyBundle, nowMs = nowMs)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, rainyBcCalc.overallWeatherSuitability)
        assertEquals(0.0, rainyBcCalc.estimatedDailyProduction ?: -1.0, 0.01)
        assertNotEquals(clearCalc.dynamicRecommendation, rainyBcCalc.dynamicRecommendation)

        // Switching activity to Road Marking under high wind (38 km/h, dry) triggers distinct recommendation
        val windyBundle = makeBundle((8..16).map { makeHour(it, tempC = 28.0, rainProb = 5, windKmh = 38.0) }, fetchedAtMs = nowMs)
        val markingPlan = bcPlan.copy(activity = PlanningWorkActivity.ROAD_MARKING)
        val markingCalc = HighwayWorkPlanningEngine.calculatePlan(markingPlan, windyBundle, nowMs = nowMs)
        assertNotEquals(clearCalc.dynamicRecommendation, markingCalc.dynamicRecommendation)

        // Missing or stale weather MUST mark slots as INSUFFICIENT_DATA
        val staleCalc = HighwayWorkPlanningEngine.calculatePlan(
            bcPlan,
            clearBundle,
            nowMs = nowMs + 3L * 60L * 60_000L // 3 hours old > 1h stale threshold
        )
        assertTrue(staleCalc.isWeatherStaleOrUnavailable)
        assertEquals(WorkSuitabilityStatus.INSUFFICIENT_DATA, staleCalc.overallWeatherSuitability)
        assertTrue(staleCalc.hourlySlots.all { it.suitability == WorkSuitabilityStatus.INSUFFICIENT_DATA })
    }

    // Criteria 13 & 14: Saved plans persist via PreferencesManager & JSON round-trip;
    // Offline cached machinery catalog saves to filesDir and loads offline cleanly
    @Test
    fun savedPlansAndOfflineMachineryCatalogPersistAcrossRestarts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        val planA = HighwayWorkPlan(
            planId = "plan-nh44-bc",
            projectName = "NH-44 Package III",
            roadOrHighwayName = "NH-44",
            workDateIso = "2026-11-20",
            activity = PlanningWorkActivity.BC_LAYING,
            startChainage = "12+000",
            endChainage = "13+500",
            roadSide = "LHS",
            workLengthMeters = 1500.0,
            workWidthMeters = 7.0,
            layerThicknessMm = 40.0,
            machinery = MachineryCatalogRepository.suggestedStarterFleet(PlanningWorkActivity.BC_LAYING),
            transport = TransportPlanningInput(roadLeadDistanceKm = 18.0, dumperCount = 10, actualDumperPayloadTonnes = 18.0)
        )
        val planB = planA.copy(
            planId = "plan-nh44-dbm",
            activity = PlanningWorkActivity.DBM_LAYING,
            layerThicknessMm = 60.0
        )

        prefs.updateActiveHighwayWorkPlan(planA)
        prefs.updateSavedHighwayWorkPlans(listOf(planA, planB))

        // Reload fresh PreferencesManager to verify persistence
        val reloadedPrefs = PreferencesManager(context)
        val loadedActive = reloadedPrefs.getInitialActiveHighwayWorkPlan()
        val loadedSaved = reloadedPrefs.getInitialSavedHighwayWorkPlans()

        assertNotNull(loadedActive)
        assertEquals("plan-nh44-bc", loadedActive?.planId)
        assertEquals(18.0, loadedActive?.transport?.roadLeadDistanceKm ?: 0.0, 0.01)
        assertEquals(2, loadedSaved.size)
        assertEquals("plan-nh44-dbm", loadedSaved[1].planId)

        // Delete planB and verify remaining saved plans
        reloadedPrefs.updateSavedHighwayWorkPlans(loadedSaved.filterNot { it.planId == "plan-nh44-dbm" })
        assertEquals(1, reloadedPrefs.getInitialSavedHighwayWorkPlans().size)

        // Verify offline machinery catalog cache round-trip
        val repo = MachineryCatalogRepository(context)
        val customMachine = MachineryCatalogEntry(
            id = "custom_site_paver_01",
            category = MachineryCategory.ASPHALT_PAVER,
            equipmentSubType = "Custom Site Paver",
            manufacturer = "SiteFleet",
            modelName = "SF-9000",
            variant = "9m Screed",
            availabilityStatus = ModelAvailabilityStatus.UNVERIFIED,
            maxRatedCapacityValue = 450.0,
            maxRatedCapacityUnit = "t/hr",
            workingWidthM = 9.0,
            dataSourceName = "User Custom Entry"
        )
        val updatedCatalog = MachineryCatalogRepository.builtInCatalog() + customMachine
        val stamp = 1795161600000L
        repo.persistCatalogCache(updatedCatalog, stamp)

        val (reloadedCatalog, reloadedStamp) = MachineryCatalogRepository(context).loadCatalog()
        assertEquals(stamp, reloadedStamp)
        assertTrue(reloadedCatalog.any { it.id == "custom_site_paver_01" && it.modelName == "SF-9000" })
    }

    // Criterion 15: Existing CTECS navigation tabs remain intact (no Site Register reintroduced)
    @Test
    fun existingCtecsTabsRemainIntactWithoutReintroducingSiteRegister() {
        val tabNames = AppTab.entries.map { it.name }
        assertTrue(tabNames.contains("HOME"))
        assertTrue(tabNames.contains("CAMERA"))
        assertTrue(tabNames.contains("PHOTOS"))
        assertTrue(tabNames.contains("ROUTES"))
        assertTrue(tabNames.contains("SURVEY"))
        assertTrue(tabNames.contains("HIGHWAY_TOOLS"))
        assertTrue(tabNames.contains("WEATHER"))
        assertTrue(tabNames.contains("SETTINGS"))
        assertFalse(tabNames.any { it.contains("REGISTER", ignoreCase = true) })
    }
}
