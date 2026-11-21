package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.backup.SettingsBackupManager
import com.example.data.PreferencesManager
import com.example.model.ActualSiteMeasurements
import com.example.model.BitumenBinderType
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.ForecastConfidence
import com.example.model.GeoPoint
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.MoRTHRevision
import com.example.model.SurfaceMoistureCondition
import com.example.model.TackCoatCondition
import com.example.model.TemperatureUnit
import com.example.model.ValueSourceTag
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetInput
import com.example.ui.home.AnimatedWeatherIcon
import com.example.ui.home.HomeLiveWeatherCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.weather.WeatherDashboardScreen
import com.example.ui.weather.WeatherDashboardTab
import com.example.ui.weather.WeatherIntelligenceSettingsSectionContent
import com.example.weather.IrcMorthWeatherRuleEngine
import com.example.weather.OpenMeteoWeatherFetcher
import com.example.weather.SharedWeatherRepository
import com.example.weather.WeatherAlertNotifier
import com.example.weather.WeatherForecastFetcher
import com.example.weather.WorkTargetPredictionEngine
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
 * Comprehensive verification suite for CTECS Advanced Weather Forecast,
 * IRC/MoRTH Compliance Intelligence, Work Target Prediction, Delay Risks,
 * Alerts, Offline Caching, and Settings Persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherAndHighwayIntelligenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeTimestampMs(year: Int, month: Int, day: Int, hour: Int, min: Int = 0): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
            set(year, month, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun buildFullDayHourly(
        dayStartMs: Long,
        rainHours: Set<Int> = emptySet(),
        windyHours: Set<Int> = emptySet(),
        fogHours: Set<Int> = emptySet(),
        stormHours: Set<Int> = emptySet(),
        baseTempC: Double = 28.0
    ): List<HourlyWeatherPoint> {
        return (0..23).map { hour ->
            val isStorm = hour in stormHours
            val isRain = hour in rainHours
            val isFog = hour in fogHours
            val isWindy = hour in windyHours
            val cond = when {
                isStorm -> WeatherConditionType.THUNDERSTORM
                isRain -> WeatherConditionType.RAINY
                isFog -> WeatherConditionType.FOGGY
                hour in 6..18 -> WeatherConditionType.SUNNY
                else -> WeatherConditionType.NIGHT
            }
            HourlyWeatherPoint(
                epochMs = dayStartMs + hour * 3_600_000L,
                localHourOfDay = hour,
                temperatureC = baseTempC + kotlin.math.sin((hour - 6) * Math.PI / 12.0) * 4.0,
                feelsLikeC = baseTempC + 1.5,
                rainProbabilityPercent = when {
                    isStorm -> 90
                    isRain -> 80
                    else -> 10
                },
                precipitationMm = when {
                    isStorm -> 12.0
                    isRain -> 3.5
                    else -> 0.0
                },
                windSpeedKmh = if (isWindy) 46.0 else 14.0,
                windGustsKmh = if (isWindy) 58.0 else 20.0,
                windDirectionDegrees = 225,
                humidityPercent = if (isRain || isStorm) 88 else 52,
                condition = cond,
                conditionLabel = cond.defaultLabel,
                wmoCode = when {
                    isStorm -> 95
                    isRain -> 63
                    isFog -> 45
                    else -> 0
                },
                isDay = hour in 6..18,
                visibilityKm = if (isFog) 0.4 else 12.0,
                cloudCoverPercent = if (isRain || isStorm) 95 else 20,
                pressureHpa = 1011.0,
                uvIndex = if (hour in 9..16) 7.0 else 0.0,
                dewPointC = 16.0
            )
        }
    }

    private fun buildSampleBundle(
        nowMs: Long = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0),
        tempC: Double = 28.0,
        windKmh: Double = 14.0,
        rainProb: Int = 10,
        precipMm: Double = 0.0,
        condition: WeatherConditionType = WeatherConditionType.SUNNY,
        rainHours: Set<Int> = emptySet(),
        windyHours: Set<Int> = emptySet(),
        stormHours: Set<Int> = emptySet()
    ): WeatherForecastBundle {
        val dayStartMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 0, 0)
        val hourly = buildFullDayHourly(
            dayStartMs = dayStartMs,
            rainHours = rainHours,
            windyHours = windyHours,
            stormHours = stormHours,
            baseTempC = tempC
        )
        val daily = (0..6).map { idx ->
            DailyWeatherPoint(
                dateEpochMs = dayStartMs + idx * 86_400_000L,
                dayIndexFromToday = idx,
                minTempC = 18.0 + idx * 0.4,
                maxTempC = 31.0 + idx * 0.3,
                condition = if (idx == 2) WeatherConditionType.RAINY else WeatherConditionType.SUNNY,
                conditionLabel = if (idx == 2) "Rainy" else "Sunny",
                wmoCode = if (idx == 2) 63 else 0,
                rainProbabilityPercent = if (idx == 2) 75 else 15,
                precipitationSumMm = if (idx == 2) 14.2 else 0.0,
                windSpeedMaxKmh = 18.0,
                windGustsMaxKmh = 26.0,
                dominantWindDirectionDegrees = 210,
                uvIndexMax = 7.5,
                sunriseMs = dayStartMs + 6 * 3_600_000L + 30 * 60_000L,
                sunsetMs = dayStartMs + 17 * 3_600_000L + 45 * 60_000L
            )
        }
        val obs = WeatherObservation(
            temperatureC = tempC,
            feelsLikeC = tempC + 1.5,
            humidityPercent = 54,
            windSpeedKmh = windKmh,
            windDirectionDegrees = 225,
            condition = condition,
            conditionLabel = condition.defaultLabel,
            wmoCode = if (condition == WeatherConditionType.RAINY) 63 else 0,
            isDay = true,
            latitude = 29.915,
            longitude = 78.042,
            locationName = "NH-334 Roorkee–Haridwar",
            observedAtMs = nowMs,
            fetchedAtMs = nowMs,
            source = WeatherObservation.DEFAULT_SOURCE,
            rainProbabilityPercent = rainProb,
            precipitationMm = precipMm,
            windGustsKmh = windKmh + 6.0,
            pressureHpa = 1012.0,
            visibilityKm = 10.0,
            uvIndex = 6.5,
            dewPointC = 16.5,
            cloudCoverPercent = 25,
            sunriseMs = daily.first().sunriseMs,
            sunsetMs = daily.first().sunsetMs,
            todayMinTempC = daily.first().minTempC,
            todayMaxTempC = daily.first().maxTempC
        )
        val alerts = OpenMeteoWeatherFetcher.deriveWeatherAlerts(obs, hourly, nowMs)
        return WeatherForecastBundle(
            current = obs,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
            utcOffsetSeconds = 19800,
            timezoneId = "Asia/Kolkata",
            fetchedAtMs = nowMs,
            source = WeatherObservation.DEFAULT_SOURCE
        )
    }

    // -------------------------------------------------------------------------
    // 1. Open-Meteo Unified Parsing, Offline Cache, Stale Handling & Spatial Reuse
    // -------------------------------------------------------------------------

    @Test
    fun `OpenMeteoWeatherFetcher parses unified current, 24h hourly, and 7-day daily JSON accurately`() {
        val dayStartSec = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 0, 0) / 1000L
        val hourlyTimes = (0..23).joinToString(",") { (dayStartSec + it * 3600L).toString() }
        val hourlyTemps = (0..23).joinToString(",") { (20.0 + it * 0.4).toString() }
        val hourlyRainProb = (0..23).joinToString(",") { if (it == 14) "85" else "15" }
        val hourlyPrecip = (0..23).joinToString(",") { if (it == 14) "8.2" else "0.0" }
        val hourlyWind = (0..23).joinToString(",") { "16.5" }
        val hourlyCode = (0..23).joinToString(",") { if (it == 14) "65" else "0" }
        val dailyTimes = (0..6).joinToString(",") { (dayStartSec + it * 86400L).toString() }
        val dailyMax = (0..6).joinToString(",") { "32.0" }
        val dailyMin = (0..6).joinToString(",") { "18.0" }

        val json = """
            {
              "utc_offset_seconds": 19800,
              "timezone": "Asia/Kolkata",
              "current": {
                "time": ${dayStartSec + 36000L},
                "temperature_2m": 28.6,
                "relative_humidity_2m": 58,
                "apparent_temperature": 30.2,
                "is_day": 1,
                "precipitation": 0.0,
                "weather_code": 2,
                "cloud_cover": 40,
                "surface_pressure": 1011.4,
                "wind_speed_10m": 15.0,
                "wind_direction_10m": 180,
                "wind_gusts_10m": 22.0,
                "uv_index": 6.2,
                "dew_point_2m": 17.4,
                "visibility": 10000.0
              },
              "hourly": {
                "time": [$hourlyTimes],
                "temperature_2m": [$hourlyTemps],
                "precipitation_probability": [$hourlyRainProb],
                "precipitation": [$hourlyPrecip],
                "wind_speed_10m": [$hourlyWind],
                "weather_code": [$hourlyCode]
              },
              "daily": {
                "time": [$dailyTimes],
                "temperature_2m_max": [$dailyMax],
                "temperature_2m_min": [$dailyMin]
              }
            }
        """.trimIndent()

        val parsed = OpenMeteoWeatherFetcher.parseOpenMeteoForecastResponse(
            jsonBody = json,
            latitude = 29.915,
            longitude = 78.042,
            fetchedAtMs = (dayStartSec + 36000L) * 1000L
        )
        assertNotNull(parsed)
        assertEquals(28.6, parsed!!.current.temperatureC, 0.01)
        assertEquals(10.0, parsed.current.visibilityKm!!, 0.01)
        assertEquals(24, parsed.hourly.size)
        assertEquals(7, parsed.daily.size)
        assertEquals(ForecastConfidence.HIGH, parsed.daily[0].forecastConfidence)
        assertEquals(ForecastConfidence.LOW, parsed.daily[5].forecastConfidence)
        assertTrue("Heavy rain alert should be derived from 8.2 mm/hr hour", parsed.alerts.any { it.category == "HEAVY_RAIN" })

        // Verify full JSON round-trip for offline cache persistence
        val restored = WeatherForecastBundle.fromJsonString(parsed.toJsonString())
        assertNotNull(restored)
        assertEquals(24, restored!!.hourly.size)
        assertEquals(7, restored.daily.size)
        assertEquals("Asia/Kolkata", restored.timezoneId)
    }

    @Test
    fun `SharedWeatherRepository caches forecast bundle spatially and retains offline fallback when disconnected`() = runBlocking {
        var nowMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0)
        var calls = 0
        var online = true
        val repo = SharedWeatherRepository(
            fetcher = object : WeatherForecastFetcher {
                override suspend fun fetchForecastBundle(latitude: Double, longitude: Double, nowMs: Long): WeatherForecastBundle? {
                    calls++
                    return if (online) buildSampleBundle(nowMs = nowMs) else null
                }
            },
            clockMs = { nowMs }
        )

        val pt = GeoPoint(29.915, 78.042)
        val first = repo.refreshForLocation(pt, "NH-334")
        assertNotNull(first)
        assertEquals(1, calls)
        assertEquals(24, repo.forecastBundle.value?.hourly?.size)

        // Small movement within 2km and within TTL reuses cache without duplicate API call
        nowMs += 120_000L
        repo.refreshForLocation(GeoPoint(29.916, 78.042), "NH-334")
        assertEquals(1, calls)

        // Offline force refresh preserves cached forecast bundle gracefully
        online = false
        nowMs += 20 * 60_000L
        val offlineRes = repo.refreshForLocation(pt, "NH-334", forceRefresh = true)
        assertNotNull(offlineRes)
        assertNotNull(repo.forecastBundle.value)
        assertTrue(repo.forecastBundle.value!!.isStaleAt(nowMs + 70 * 60_000L))
    }

    // -------------------------------------------------------------------------
    // 2. Mandatory IRC:105-2019 Cl. 3.3.4 & MoRTH 5th Rev Cl. 501.5.1 Rules
    // -------------------------------------------------------------------------

    @Test
    fun `MoRTH 5th Rev Cl 501-5-1 and IRC 105-2019 Cl 3-3-4 enforce all 7 mandatory bituminous restrictions`() {
        val nowMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0)
        val spec = ContractSpecificationConfig(
            morthRevision = MoRTHRevision.FIFTH_REVISION_2013,
            bcBinderType = BitumenBinderType.CONVENTIONAL_VG30,
            dbmBinderType = BitumenBinderType.MODIFIED_PMB
        )
        val cleanObs = buildSampleBundle(nowMs = nowMs, tempC = 28.0, windKmh = 12.0, rainProb = 5).current

        // 1. Standing water -> NOT_RECOMMENDED
        val standingWater = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs, spec,
            ActualSiteMeasurements(hasStandingWater = true, pavementSurfaceTempC = 32.0), nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, standingWater.status)
        assertTrue(standingWater.violatedMandatoryChecks.any { it.ruleName.contains("Standing Water") })

        // 2. Damp base / binder course -> NOT_RECOMMENDED
        val dampBase = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs, spec,
            ActualSiteMeasurements(baseMoistureCondition = SurfaceMoistureCondition.DAMP, pavementSurfaceTempC = 32.0), nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, dampBase.status)

        // 3. Imminent or active rain -> NOT_RECOMMENDED
        val imminentRain = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs.copy(rainProbabilityPercent = 70), spec,
            ActualSiteMeasurements(pavementSurfaceTempC = 32.0), nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, imminentRain.status)

        // 4. Fog -> NOT_RECOMMENDED
        val foggy = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs.copy(condition = WeatherConditionType.FOGGY, conditionLabel = "Foggy"), spec,
            ActualSiteMeasurements(pavementSurfaceTempC = 25.0), nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, foggy.status)

        // 5. Wind > 40 km/h -> NOT_RECOMMENDED
        val highWind = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs.copy(windSpeedKmh = 44.0), spec,
            ActualSiteMeasurements(pavementSurfaceTempC = 32.0), nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, highWind.status)

        // 6. Conventional bitumen surface temp < 10°C -> NOT_RECOMMENDED (even if air temp is 14°C)
        val coldConv = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs.copy(temperatureC = 14.0), spec,
            ActualSiteMeasurements(pavementSurfaceTempC = 9.2, baseMoistureCondition = SurfaceMoistureCondition.DRY), nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, coldConv.status)

        // 7. Modified bitumen (PMB on DBM) surface temp 13°C (<15°C) -> NOT_RECOMMENDED,
        //    whereas Conventional VG-30 on BC at 13°C (>=10°C) -> SUITABLE!
        val site13C = ActualSiteMeasurements(pavementSurfaceTempC = 13.0, baseMoistureCondition = SurfaceMoistureCondition.DRY)
        val dbmPmbAt13C = IrcMorthWeatherRuleEngine.evaluateActivity(HighwayWorkActivity.DBM_LAYING, cleanObs, spec, site13C, nowMs)
        val bcVg30At13C = IrcMorthWeatherRuleEngine.evaluateActivity(HighwayWorkActivity.BC_LAYING, cleanObs, spec, site13C, nowMs)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, dbmPmbAt13C.status)
        assertEquals(WorkSuitabilityStatus.SUITABLE, bcVg30At13C.status)

        // 8. Forecast air temp vs unmeasured pavement surface temp prompts for site verification
        val unmeasured = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, cleanObs.copy(temperatureC = 12.0), spec,
            ActualSiteMeasurements(), nowMs
        )
        assertTrue(unmeasured.requiresSiteSurfaceTempVerification)
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, unmeasured.status)
    }

    @Test
    fun `MoRTH 6th Revision respects 1 Jan 2027 NH implementation date and never auto-replaces 5th Revision`() {
        val defaultSpec = ContractSpecificationConfig()
        assertEquals(MoRTHRevision.FIFTH_REVISION_2013, defaultSpec.morthRevision)

        val pre2027Ms = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0)
        val post2027Ms = makeTimestampMs(2027, Calendar.FEBRUARY, 1, 10, 0)
        val sixthSpecUnverified = ContractSpecificationConfig(
            morthRevision = MoRTHRevision.SIXTH_REVISION_2026,
            sixthRevisionContractVerified = false
        )
        assertFalse(sixthSpecUnverified.isSixthRevisionEffective(pre2027Ms))
        assertTrue(sixthSpecUnverified.isSixthRevisionEffective(post2027Ms))

        val obs = buildSampleBundle(nowMs = pre2027Ms, tempC = 28.0).current
        val eval = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, obs, sixthSpecUnverified,
            ActualSiteMeasurements(pavementSurfaceTempC = 32.0, baseMoistureCondition = SurfaceMoistureCondition.DRY),
            pre2027Ms
        )
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, eval.status)
        assertTrue(eval.checks.any { it.detail.contains("1 January 2027") })
    }

    @Test
    fun `All 14 highway construction activities evaluate independently with non-bituminous specific rules`() {
        val nowMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0)
        val obs = buildSampleBundle(nowMs = nowMs, tempC = 27.0, windKmh = 15.0, rainProb = 10).current
        val all = IrcMorthWeatherRuleEngine.evaluateAllActivities(
            observation = obs,
            contractSpec = ContractSpecificationConfig(),
            siteMeasurements = ActualSiteMeasurements(pavementSurfaceTempC = 30.0, baseMoistureCondition = SurfaceMoistureCondition.DRY),
            referenceTimeMs = nowMs
        )
        assertEquals(14, all.size)
        assertEquals(HighwayWorkActivity.entries.toSet(), all.map { it.activity }.toSet())
        assertTrue(all.all { it.status == WorkSuitabilityStatus.SUITABLE })

        // Missing observation -> INSUFFICIENT_DATA across all 14 activities
        val missing = IrcMorthWeatherRuleEngine.evaluateAllActivities(null, ContractSpecificationConfig())
        assertEquals(14, missing.size)
        assertTrue(missing.all { it.status == WorkSuitabilityStatus.INSUFFICIENT_DATA })

        // Tack coat contamination blocks BC Laying specifically
        val washedTack = IrcMorthWeatherRuleEngine.evaluateActivity(
            HighwayWorkActivity.BC_LAYING, obs, ContractSpecificationConfig(),
            ActualSiteMeasurements(pavementSurfaceTempC = 30.0, tackCoatCondition = TackCoatCondition.CONTAMINATED_OR_WASHED),
            nowMs
        )
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, washedTack.status)

        // Milling in light rain (30% -> 65%) is caution, while BC laying is NOT_RECOMMENDED
        val lightRainObs = obs.copy(rainProbabilityPercent = 65, precipitationMm = 0.4, condition = WeatherConditionType.RAINY)
        val bcRain = IrcMorthWeatherRuleEngine.evaluateActivity(HighwayWorkActivity.BC_LAYING, lightRainObs, ContractSpecificationConfig(), referenceTimeMs = nowMs)
        val millRain = IrcMorthWeatherRuleEngine.evaluateActivity(HighwayWorkActivity.MILLING_OPERATIONS, lightRainObs, ContractSpecificationConfig(), referenceTimeMs = nowMs)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, bcRain.status)
        assertEquals(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION, millRain.status)
    }

    // -------------------------------------------------------------------------
    // 3. Hourly Work Timeline, Work Target Math & Delay Risk Analysis
    // -------------------------------------------------------------------------

    @Test
    fun `Hourly work timeline, Work Target calculations, bottlenecks, and delay risk warnings are accurate and never invent missing rates`() {
        val nowMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 7, 0)
        // Hours 11, 12, 13 have rain; hour 15 has high wind > 40 km/h
        val bundle = buildSampleBundle(
            nowMs = nowMs,
            rainHours = setOf(11, 12, 13),
            windyHours = setOf(15)
        )
        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
            activity = HighwayWorkActivity.BC_LAYING,
            hourlyPoints = bundle.hourly,
            contractSpec = ContractSpecificationConfig()
        )
        assertEquals(24, timeline.size)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, timeline[11].status)
        assertEquals(WorkSuitabilityStatus.NOT_RECOMMENDED, timeline[15].status)
        assertTrue(timeline[11].isMandatoryViolation)
        assertTrue(timeline[15].isMandatoryViolation)

        // 1. Missing production rate MUST NOT be invented
        val missingRateInput = WorkTargetInput(
            activity = HighwayWorkActivity.BC_LAYING,
            startChainageKm = 10.0,
            endChainageKm = 11.0,
            workingWidthMeters = 7.0,
            layerThicknessMm = 40.0,
            expectedProductionRatePerHour = null
        )
        val missingPred = WorkTargetPredictionEngine.predictWorkTarget(missingRateInput, timeline)
        assertFalse(missingPred.isCalculationComplete)
        assertTrue(missingPred.missingInputWarnings.any { it.contains("Production Rate") })

        // 2. Complete calculation: Length = 1000m, Width = 7m -> Area = 7000 m², Thickness = 40mm -> Compacted Vol = 280 m³
        val validInput = WorkTargetInput(
            activity = HighwayWorkActivity.BC_LAYING,
            startChainageKm = 10.0,
            endChainageKm = 11.0,
            workingWidthMeters = 7.0,
            layerThicknessMm = 40.0,
            plannedStartHour = 7,
            availableWorkingHours = 10.0, // Hours 7..16 (4 unsuitable hours: 11,12,13,15 -> 6 suitable hours)
            expectedProductionRatePerHour = 1000.0,
            productionRateSource = ValueSourceTag.USER_ENTERED,
            prepAndInspectionHours = 0.5,
            wetSurfaceRecoveryHoursPerRainEvent = 1.5
        )
        val pred = WorkTargetPredictionEngine.predictWorkTarget(validInput, timeline)
        assertTrue(pred.isCalculationComplete)
        assertEquals(1000.0, pred.totalLengthMeters, 0.01)
        assertEquals(7000.0, pred.totalAreaSqm, 0.01)
        assertEquals(280.0, pred.compactedVolumeM3, 0.01)
        assertEquals(4.0, pred.weatherInterruptionHours, 0.01)
        assertEquals(1.5, pred.wetSurfaceRecoveryHours, 0.01)
        assertEquals(0.5, pred.prepAndInspectionHours, 0.01)
        assertEquals(4.0, pred.netEffectiveWorkingHours, 0.01) // 6 suitable - 1.5 recovery - 0.5 prep
        assertEquals(4000.0, pred.weatherAdjustedTargetQuantity, 0.1)
        assertEquals(3000.0, pred.remainingBalanceQuantity, 0.1)
        assertTrue(pred.estimateDisclaimer.contains("ESTIMATE ONLY"))

        // 3. HMP Capacity bottleneck caps effective hourly rate accurately
        // 80 TPH / (0.04m * 2.38 t/m³) = 840.34 m²/hr < 1000 m²/hr user rate
        val hmpCapped = WorkTargetPredictionEngine.predictWorkTarget(validInput.copy(hmpCapacityTph = 80.0), timeline)
        assertTrue(hmpCapped.effectiveHourlyRate < 850.0)
        assertTrue(hmpCapped.limitingResourceNote.contains("HMP"))

        // 4. Delay & Risk Analyzer detects rain, surface drying, and high wind warnings
        val risks = WorkTargetPredictionEngine.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, timeline, pred)
        assertTrue(risks.warnings.any { it.title.contains("Rain", ignoreCase = true) })
        assertTrue(risks.warnings.any { it.title.contains("Drying", ignoreCase = true) })
        assertTrue(risks.warnings.any { it.title.contains("Wind", ignoreCase = true) })
        assertNotNull(risks.nextSuitableWindowLabel)
    }

    // -------------------------------------------------------------------------
    // 4. Weather Alerts Activity Filtering & Deduplication
    // -------------------------------------------------------------------------

    @Test
    fun `WeatherAlertNotifier filters by enabled activities and deduplicates repeat alerts`() {
        val nowMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 9, 0)
        val bundle = buildSampleBundle(nowMs = nowMs, stormHours = setOf(11), windyHours = setOf(12))
        val settings = WeatherIntelligenceSettings(
            weatherAlertsEnabled = true,
            enabledAlertActivities = setOf(HighwayWorkActivity.BC_LAYING)
        )
        val assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(
            bundle.current.copy(windSpeedKmh = 45.0),
            settings.contractSpec,
            referenceTimeMs = nowMs
        )
        val notifier = WeatherAlertNotifier()
        val alerts = notifier.evaluateActionableAlerts(bundle, settings, assessments, null, nowMs)
        assertTrue(alerts.any { it.category == "THUNDERSTORM" })
        assertTrue(alerts.any { it.category == "STRONG_WIND" })
        assertTrue(alerts.any { it.category == "BITUMINOUS_RESTRICTION" })

        val firstPass = notifier.filterNewAlerts(alerts, nowMs)
        assertEquals(alerts.size, firstPass.size)
        val secondPassImmediate = notifier.filterNewAlerts(alerts, nowMs + 60_000L)
        assertTrue("Duplicate alerts within dedup window must be suppressed", secondPassImmediate.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 5. Preferences Persistence & Backup Manager Integration
    // -------------------------------------------------------------------------

    @Test
    fun `PreferencesManager and SettingsBackupManager persist and export weather intelligence settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesManager(context)
        val customSettings = WeatherIntelligenceSettings(
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            animationsEnabled = false,
            primaryActivity = HighwayWorkActivity.DBM_LAYING,
            contractSpec = ContractSpecificationConfig(
                morthRevision = MoRTHRevision.SIXTH_REVISION_2026,
                sixthRevisionContractVerified = true,
                dbmBinderType = BitumenBinderType.MODIFIED_PMB
            ),
            savedProductionRatesByActivity = mapOf(HighwayWorkActivity.DBM_LAYING to 650.0)
        )
        val customTarget = WorkTargetInput(
            activity = HighwayWorkActivity.DBM_LAYING,
            startChainageKm = 12.0,
            endChainageKm = 13.5,
            expectedProductionRatePerHour = 650.0
        )
        prefs.updateWeatherIntelligenceSettings(customSettings)
        prefs.updateWorkTargetInput(customTarget)

        val restoredSettings = prefs.getInitialWeatherIntelligenceSettings()
        val restoredTarget = prefs.getInitialWorkTargetInput()
        assertEquals(TemperatureUnit.FAHRENHEIT, restoredSettings.temperatureUnit)
        assertFalse(restoredSettings.animationsEnabled)
        assertEquals(MoRTHRevision.SIXTH_REVISION_2026, restoredSettings.contractSpec.morthRevision)
        assertEquals(650.0, restoredSettings.savedProductionRatesByActivity[HighwayWorkActivity.DBM_LAYING] ?: 0.0, 0.01)
        assertEquals(13.5, restoredTarget.endChainageKm ?: 0.0, 0.01)

        val backupManager = SettingsBackupManager(context, prefs)
        val exported = backupManager.collectCompleteExportEntries()
        assertTrue(exported.any { it.key == PreferencesManager.KEY_WEATHER_INTELLIGENCE_SETTINGS_JSON.name })
        assertTrue(exported.any { it.key == PreferencesManager.KEY_WORK_TARGET_INPUT_JSON.name })

        // Clean up back to defaults
        prefs.updateWeatherIntelligenceSettings(WeatherIntelligenceSettings())
        prefs.updateWorkTargetInput(WorkTargetInput())
    }

    // -------------------------------------------------------------------------
    // 6. Compose UI Verification: Home Card Full-Day Strip, Double-Tap, Weather Dashboard & Settings
    // -------------------------------------------------------------------------

    @Test
    fun `HomeLiveWeatherCard renders full-day 12AM-11PM hourly strip and opens Weather Dashboard on double tap`() {
        val bundle = buildSampleBundle()
        var dashboardOpens = 0
        var refreshes = 0

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                HomeLiveWeatherCard(
                    weatherUiState = WeatherUiState.Available(bundle.current),
                    resolvedLocationName = "NH-334 Roorkee–Haridwar",
                    is24HourFormat = false,
                    isDark = true,
                    mainTextColor = ComposeColor.White,
                    subTextColor = ComposeColor.LightGray,
                    isCompact = false,
                    isVisible = false,
                    onRefresh = { refreshes++ },
                    hourlyForecast = bundle.todayFullDayHours(bundle.current.observedAtMs),
                    currentLocalHour = 10,
                    animationsEnabled = false,
                    onOpenWeatherDashboard = { dashboardOpens++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_live_weather_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_hourly_forecast_row").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_rain_prob_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_hourly_item_10").assertIsDisplayed()

        // Single tap on available card does not open Weather Dashboard
        composeRule.onNodeWithTag("home_live_weather_card").performClick()
        assertEquals(0, dashboardOpens)

        // Double tap opens Weather Dashboard
        composeRule.onNodeWithTag("home_live_weather_card").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(1, dashboardOpens)

        // Refresh button works independently
        composeRule.onNodeWithTag("weather_refresh_button").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun `WeatherDashboardScreen renders Forecast, Highway Work Suitability, Targets & Risks, and all 10 animated weather icons`() {
        val nowMs = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0)
        val bundle = buildSampleBundle(nowMs = nowMs, rainHours = setOf(13, 14), stormHours = setOf(15))
        val settings = WeatherIntelligenceSettings(animationsEnabled = false)
        val assessments = IrcMorthWeatherRuleEngine.evaluateAllActivities(bundle.current, settings.contractSpec, referenceTimeMs = nowMs)
        val timeline = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(HighwayWorkActivity.BC_LAYING, bundle.hourly, settings.contractSpec)
        val targetInput = WorkTargetInput(
            activity = HighwayWorkActivity.BC_LAYING,
            startChainageKm = 10.0,
            endChainageKm = 11.0,
            expectedProductionRatePerHour = 800.0
        )
        val targetPred = WorkTargetPredictionEngine.predictWorkTarget(targetInput, timeline)
        val delayRisks = WorkTargetPredictionEngine.analyzeDelayRisks(HighwayWorkActivity.BC_LAYING, timeline, targetPred)

        composeRule.setContent {
            MyApplicationTheme(themeMode = "light") {
                WeatherDashboardScreen(
                    weatherUiState = WeatherUiState.Available(bundle.current),
                    forecastBundle = bundle,
                    resolvedLocationName = "NH-334 Roorkee–Haridwar",
                    is24HourFormat = false,
                    settings = settings,
                    assessments = assessments,
                    selectedActivity = HighwayWorkActivity.BC_LAYING,
                    hourlyWorkTimeline = timeline,
                    siteMeasurements = ActualSiteMeasurements(),
                    workTargetInput = targetInput,
                    workTargetPrediction = targetPred,
                    delayRiskSummary = delayRisks,
                    actionableAlerts = bundle.alerts,
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
        composeRule.onNodeWithTag("weather_dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_dashboard_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_dashboard_temp").assertTextContains("28°C")

        // Switch to Highway Work Suitability tab
        composeRule.onNodeWithTag("weather_tab_suitability").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("highway_work_suitability_section").assertIsDisplayed()
        composeRule.onNodeWithTag("irc_morth_spec_card").assertIsDisplayed()

        // Switch to Targets & Risks tab
        composeRule.onNodeWithTag("weather_tab_targets").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("work_target_and_risk_section").assertIsDisplayed()
        composeRule.onNodeWithTag("work_target_prediction_card").assertIsDisplayed()
    }

    @Test
    fun `WeatherIntelligenceSettingsSectionContent and all 10 weather icons render cleanly`() {
        var openedDashboard = false
        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                var state by remember { mutableStateOf(WeatherIntelligenceSettings()) }
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Row {
                        WeatherConditionType.entries.forEach { cond ->
                            AnimatedWeatherIcon(
                                condition = cond,
                                isDark = true,
                                isAnimating = false,
                                size = 24.dp,
                                isHeavyRain = cond == WeatherConditionType.RAINY
                            )
                        }
                    }
                    WeatherIntelligenceSettingsSectionContent(
                        settings = state,
                        workTargetInput = WorkTargetInput(),
                        accentColor = ComposeColor(0xFF38BDF8),
                        onUpdateSettings = { fn -> state = fn(state) },
                        onUpdateWorkTargetInput = {},
                        onOpenWeatherDashboard = { openedDashboard = true },
                        onResetDefaults = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_intelligence_settings_content").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_open_weather_dashboard_button").performClick()
        assertTrue(openedDashboard)
        composeRule.onNodeWithText("1. WEATHER DISPLAY").assertIsDisplayed()
    }
}
