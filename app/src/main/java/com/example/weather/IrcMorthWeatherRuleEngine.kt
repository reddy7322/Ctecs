package com.example.weather

import com.example.model.ActivitySuitabilityAssessment
import com.example.model.ActualSiteMeasurements
import com.example.model.BitumenBinderType
import com.example.model.ContractSpecificationConfig
import com.example.model.ForecastConfidence
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.HourlyWorkWindow
import com.example.model.MoRTHRevision
import com.example.model.SpecificationRuleCheck
import com.example.model.SurfaceMoistureCondition
import com.example.model.TackCoatCondition
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherObservation
import com.example.model.WorkSuitabilityStatus
import java.util.Locale

/**
 * Specification-backed Highway Construction Weather Intelligence Rule Engine.
 *
 * Engineering Principles (Strictly Enforced):
 * - NEVER produces arbitrary numerical weather scores (e.g., "82/100").
 * - NEVER invents IRC/MoRTH clause numbers or unsupported numerical thresholds.
 * - Treats mandatory MoRTH Fifth Revision (2013) Cl. 501.5.1 and IRC:105-2019 Cl. 3.3.4
 *   restrictions for bituminous laying as binding specification restrictions.
 * - Explicitly respects MoRTH Sixth Revision (2026) stated NH effective date (1 January 2027)
 *   and requires contract verification rather than silently overriding MoRTH 5th Revision.
 * - Explicitly distinguishes Forecast Air Temperature from Measured Pavement Surface Temperature
 *   and prompts for site surface-temperature verification before confirming bituminous suitability.
 * - Evaluates all 14 highway construction activities independently using activity-specific rules.
 */
object IrcMorthWeatherRuleEngine {

    private const val MORTH_5TH_CL_501_5_1 = "MoRTH 5th Rev (2013) Section 500, Clause 501.5.1"
    private const val IRC_105_2019_CL_3_3_4 = "IRC:105-2019, Clause 3.3.4"
    private const val MORTH_5TH_DBM_REF = "MoRTH 5th Rev Cl. 505 & Cl. 501.5.1 / IRC:105-2019 Cl. 3.3.4"
    private const val MORTH_5TH_BC_REF = "MoRTH 5th Rev Cl. 507.4.1 (via Cl. 501.5.1) / IRC:105-2019 Cl. 3.3.4"
    private const val MORTH_6TH_NOTICE_REF = "MoRTH 6th Rev (2026) — NH Implementation Effective 1 Jan 2027 (Contract Verification Required)"

    const val MANDATORY_WIND_LIMIT_KMH = 40.0
    const val MANDATORY_CONVENTIONAL_SURFACE_TEMP_MIN_C = 10.0
    const val MANDATORY_MODIFIED_SURFACE_TEMP_MIN_C = 15.0

    /**
     * Evaluates all 14 highway construction activities independently for the active live weather
     * observation, contract specification configuration, and actual on-site measurements.
     */
    fun evaluateAllActivities(
        observation: WeatherObservation?,
        contractSpec: ContractSpecificationConfig,
        siteMeasurements: ActualSiteMeasurements = ActualSiteMeasurements(),
        referenceTimeMs: Long = System.currentTimeMillis(),
        staleThresholdMs: Long = WeatherForecastBundle.DEFAULT_FORECAST_STALE_MS
    ): List<ActivitySuitabilityAssessment> {
        return HighwayWorkActivity.entries.map { activity ->
            evaluateActivity(
                activity = activity,
                observation = observation,
                contractSpec = contractSpec,
                siteMeasurements = siteMeasurements,
                referenceTimeMs = referenceTimeMs,
                staleThresholdMs = staleThresholdMs
            )
        }
    }

    /**
     * Evaluates a single [HighwayWorkActivity] against applicable MoRTH/IRC specification rules.
     */
    fun evaluateActivity(
        activity: HighwayWorkActivity,
        observation: WeatherObservation?,
        contractSpec: ContractSpecificationConfig,
        siteMeasurements: ActualSiteMeasurements = ActualSiteMeasurements(),
        referenceTimeMs: Long = System.currentTimeMillis(),
        staleThresholdMs: Long = WeatherForecastBundle.DEFAULT_FORECAST_STALE_MS
    ): ActivitySuitabilityAssessment {
        if (observation == null) {
            return ActivitySuitabilityAssessment(
                activity = activity,
                status = WorkSuitabilityStatus.INSUFFICIENT_DATA,
                summaryReason = "Live weather telemetry unavailable. Acquire GPS fix or refresh weather before evaluating ${activity.displayName}.",
                applicableReference = resolveReferenceHeader(activity, contractSpec, referenceTimeMs),
                checks = emptyList(),
                requiresSiteSurfaceTempVerification = activity.isBituminousHotMix,
                dataTimestampMs = 0L,
                isForecastStale = true
            )
        }

        val isStale = !observation.isFreshAt(referenceTimeMs, staleThresholdMs)
        val refHeader = resolveReferenceHeader(activity, contractSpec, referenceTimeMs)
        val checks = mutableListOf<SpecificationRuleCheck>()

        // Check MoRTH 6th Revision effective-date & contract verification guard
        if (contractSpec.morthRevision == MoRTHRevision.SIXTH_REVISION_2026) {
            val isEffective = contractSpec.isSixthRevisionEffective(referenceTimeMs)
            if (!contractSpec.sixthRevisionContractVerified) {
                checks += SpecificationRuleCheck(
                    ruleName = "MoRTH 6th Revision Contract Applicability",
                    clauseReference = MORTH_6TH_NOTICE_REF,
                    isMandatoryRestriction = false,
                    status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                    observedValue = if (isEffective) "Effective (Unverified Contract)" else "Pre-1 Jan 2027 (Unverified)",
                    detail = if (isEffective) {
                        "Verify project contract/BOQ adopts MoRTH 6th Revision (2026) and confirm any amended clause limits with the Engineer."
                    } else {
                        "MoRTH 6th Revision (2026) National Highway implementation date is 1 January 2027. Confirm explicit contract amendment before applying; baseline MoRTH 5th Revision safety checks remain enforced."
                    },
                    requiresSiteVerification = true
                )
            }
        }

        if (isStale) {
            checks += SpecificationRuleCheck(
                ruleName = "Weather Data Freshness",
                clauseReference = "Field Telemetry Verification",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = observation.freshnessDescription(referenceTimeMs),
                detail = "Cached weather observation is stale. Refresh live weather and verify site conditions directly.",
                requiresSiteVerification = true
            )
        }

        val input = WeatherSnapshotInput(
            airTempC = observation.temperatureC,
            feelsLikeC = observation.feelsLikeC,
            humidityPercent = observation.humidityPercent,
            windSpeedKmh = observation.windSpeedKmh,
            windGustsKmh = observation.windGustsKmh ?: observation.windSpeedKmh,
            rainProbabilityPercent = observation.rainProbabilityPercent ?: 0,
            precipitationMm = observation.precipitationMm ?: 0.0,
            condition = observation.condition,
            conditionLabel = observation.conditionLabel,
            wmoCode = observation.wmoCode,
            visibilityKm = observation.visibilityKm
        )

        val evaluation = evaluateSnapshotForActivity(
            activity = activity,
            weather = input,
            contractSpec = contractSpec,
            siteMeasurements = siteMeasurements,
            includeSiteMeasurementChecks = true
        )
        checks.addAll(evaluation.checks)

        val finalStatus = aggregateStatus(checks, isStaleOnly = isStale && evaluation.status == WorkSuitabilityStatus.SUITABLE)
        val summaryReason = buildSummaryReason(activity, finalStatus, checks, evaluation.requiresSurfaceTempCheck)

        return ActivitySuitabilityAssessment(
            activity = activity,
            status = finalStatus,
            summaryReason = summaryReason,
            applicableReference = refHeader,
            checks = checks,
            requiresSiteSurfaceTempVerification = evaluation.requiresSurfaceTempCheck,
            dataTimestampMs = observation.observedAtMs,
            isForecastStale = isStale
        )
    }

    /**
     * Generates a dynamic hourly work suitability timeline for the selected activity from live
     * hourly weather forecast data. NEVER recommends laying during mandatory restriction violations.
     */
    fun generateHourlyWorkTimeline(
        activity: HighwayWorkActivity,
        hourlyPoints: List<HourlyWeatherPoint>,
        contractSpec: ContractSpecificationConfig,
        siteMeasurements: ActualSiteMeasurements = ActualSiteMeasurements()
    ): List<HourlyWorkWindow> {
        return hourlyPoints.mapIndexed { index, pt ->
            val snapshot = WeatherSnapshotInput(
                airTempC = pt.temperatureC,
                feelsLikeC = pt.feelsLikeC,
                humidityPercent = pt.humidityPercent,
                windSpeedKmh = pt.windSpeedKmh,
                windGustsKmh = pt.windGustsKmh,
                rainProbabilityPercent = pt.rainProbabilityPercent,
                precipitationMm = pt.precipitationMm,
                condition = pt.condition,
                conditionLabel = pt.conditionLabel,
                wmoCode = pt.wmoCode,
                visibilityKm = pt.visibilityKm
            )
            // Only apply live site measurements to the first 2 immediate hours; later hours rely on forecast
            val eval = evaluateSnapshotForActivity(
                activity = activity,
                weather = snapshot,
                contractSpec = contractSpec,
                siteMeasurements = if (index <= 1) siteMeasurements else ActualSiteMeasurements(),
                includeSiteMeasurementChecks = index <= 1
            )
            val restrictions = eval.checks
                .filter { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED || it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION }
                .map { "${it.ruleName}: ${it.observedValue}" }
            val hasMandatoryViolation = eval.checks.any {
                it.isMandatoryRestriction && it.status == WorkSuitabilityStatus.NOT_RECOMMENDED
            }
            val confidence = when {
                index < 6 -> ForecastConfidence.HIGH
                index < 18 -> ForecastConfidence.MODERATE
                else -> ForecastConfidence.LOW
            }
            val primaryReason = when (eval.status) {
                WorkSuitabilityStatus.NOT_RECOMMENDED ->
                    eval.checks.firstOrNull { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }?.detail
                        ?: "Unsuitable weather conditions for ${activity.shortName}."
                WorkSuitabilityStatus.SUITABLE_WITH_CAUTION ->
                    eval.checks.firstOrNull { it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION }?.detail
                        ?: "Verify site conditions before proceeding with ${activity.shortName}."
                WorkSuitabilityStatus.INSUFFICIENT_DATA ->
                    "Insufficient forecast or site specification data."
                WorkSuitabilityStatus.SUITABLE ->
                    if (activity.isBituminousHotMix) {
                        "Forecast within limits; verify site pavement surface temperature & dry base before laying."
                    } else {
                        "Forecast conditions within acceptable limits for ${activity.shortName}."
                    }
            }

            HourlyWorkWindow(
                hourPoint = pt,
                activity = activity,
                status = eval.status,
                confidence = confidence,
                primaryReason = primaryReason,
                restrictions = restrictions,
                isMandatoryViolation = hasMandatoryViolation
            )
        }
    }

    private data class WeatherSnapshotInput(
        val airTempC: Double,
        val feelsLikeC: Double,
        val humidityPercent: Int,
        val windSpeedKmh: Double,
        val windGustsKmh: Double,
        val rainProbabilityPercent: Int,
        val precipitationMm: Double,
        val condition: WeatherConditionType,
        val conditionLabel: String,
        val wmoCode: Int,
        val visibilityKm: Double?
    )

    private data class SnapshotEvalResult(
        val status: WorkSuitabilityStatus,
        val checks: List<SpecificationRuleCheck>,
        val requiresSurfaceTempCheck: Boolean
    )

    private fun evaluateSnapshotForActivity(
        activity: HighwayWorkActivity,
        weather: WeatherSnapshotInput,
        contractSpec: ContractSpecificationConfig,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        return when (activity) {
            HighwayWorkActivity.BC_LAYING,
            HighwayWorkActivity.DBM_LAYING -> evaluateBituminousHotMix(
                activity = activity,
                weather = weather,
                contractSpec = contractSpec,
                siteMeasurements = siteMeasurements,
                includeSiteMeasurementChecks = includeSiteMeasurementChecks
            )

            HighwayWorkActivity.TACK_COAT_APPLICATION -> evaluateTackOrPrimeCoat(
                isTackCoat = true,
                weather = weather,
                siteMeasurements = siteMeasurements,
                includeSiteMeasurementChecks = includeSiteMeasurementChecks
            )

            HighwayWorkActivity.PRIME_COAT_APPLICATION -> evaluateTackOrPrimeCoat(
                isTackCoat = false,
                weather = weather,
                siteMeasurements = siteMeasurements,
                includeSiteMeasurementChecks = includeSiteMeasurementChecks
            )

            HighwayWorkActivity.MILLING_OPERATIONS -> evaluateMilling(weather)
            HighwayWorkActivity.GLASS_GRID_APPLICATION -> evaluateGlassGrid(weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.CRACK_SEALING -> evaluateCrackSealing(weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.SURFACE_PREPARATION_CLEANING -> evaluateSurfacePrep(weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.GSB_OPERATIONS -> evaluateGranularLayer("GSB", "MoRTH 5th Rev Cl. 401.4", weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.WMM_OPERATIONS -> evaluateGranularLayer("WMM", "MoRTH 5th Rev Cl. 406.3", weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.EARTHWORK -> evaluateEarthwork(weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.ROAD_MARKING -> evaluateRoadMarking(weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.CONCRETE_WORKS -> evaluateConcreteWorks(weather, siteMeasurements, includeSiteMeasurementChecks)
            HighwayWorkActivity.GENERAL_HIGHWAY_MAINTENANCE -> evaluateGeneralMaintenance(weather)
        }
    }

    /**
     * Separate rule evaluation for BC Laying (Cl. 507.4.1 -> Cl. 501.5.1) and DBM Laying (Cl. 505 -> Cl. 501.5.1),
     * enforcing all mandatory MoRTH Fifth Revision (2013) Cl. 501.5.1 and IRC:105-2019 Cl. 3.3.4 restrictions.
     */
    private fun evaluateBituminousHotMix(
        activity: HighwayWorkActivity,
        weather: WeatherSnapshotInput,
        contractSpec: ContractSpecificationConfig,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val checks = mutableListOf<SpecificationRuleCheck>()
        val isBc = activity == HighwayWorkActivity.BC_LAYING
        val binder = contractSpec.binderFor(activity)
        val mixDesignation = if (isBc) contractSpec.bcMixDesignation else contractSpec.dbmMixDesignation
        val clauseRef = if (isBc) MORTH_5TH_BC_REF else MORTH_5TH_DBM_REF
        val minSurfaceTemp = binder.minPavementSurfaceTempC // 10°C conventional, 15°C modified

        // 1. Standing water on surface (MANDATORY RESTRICTION)
        if (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.STANDING_WATER)) {
            checks += SpecificationRuleCheck(
                ruleName = "Standing Water on Surface",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "Standing water reported on site",
                detail = "Mandatory restriction: Bituminous laying (${activity.shortName}) is strictly prohibited when standing water is present on the surface."
            )
        }

        // 2. Damp base / binder course (MANDATORY RESTRICTION)
        if (includeSiteMeasurementChecks &&
            (siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.DAMP ||
                siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.WET_NO_STANDING_WATER)
        ) {
            checks += SpecificationRuleCheck(
                ruleName = "Damp Base / Binder Course",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = siteMeasurements.baseMoistureCondition.label,
                detail = "Mandatory restriction: ${activity.shortName} laying is prohibited on a damp or wet base/binder course."
            )
        }

        // 3. Active rainfall or imminent rain (MANDATORY RESTRICTION)
        val isActiveRain = weather.condition == WeatherConditionType.RAINY ||
            weather.condition == WeatherConditionType.THUNDERSTORM ||
            weather.condition == WeatherConditionType.SNOWY ||
            weather.precipitationMm >= 0.1
        val isImminentRain = weather.rainProbabilityPercent >= 60
        val isPossibleRain = weather.rainProbabilityPercent in 35..59

        checks += when {
            isActiveRain -> SpecificationRuleCheck(
                ruleName = "Active Rainfall / Storm",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "%s (%.1f mm, %d%%)", weather.conditionLabel, weather.precipitationMm, weather.rainProbabilityPercent),
                detail = "Mandatory restriction: ${activity.shortName} laying is prohibited during rainfall or thunderstorms."
            )
            isImminentRain -> SpecificationRuleCheck(
                ruleName = "Imminent Rain",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "${weather.rainProbabilityPercent}% rain probability",
                detail = "Mandatory restriction: Rain appears imminent (${weather.rainProbabilityPercent}% probability). Do not commence ${activity.shortName} laying."
            )
            isPossibleRain -> SpecificationRuleCheck(
                ruleName = "Rain Probability Watch",
                clauseReference = clauseRef,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.rainProbabilityPercent}% rain probability",
                detail = "Moderate rain risk (${weather.rainProbabilityPercent}%). Monitor sky/radar closely and keep emergency transverse-joint plan ready."
            )
            else -> SpecificationRuleCheck(
                ruleName = "Precipitation & Rain Check",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = "Dry (${weather.rainProbabilityPercent}% rain prob.)",
                detail = "No active or imminent rain forecasted."
            )
        }

        // 4. Fog or dust storms (MANDATORY RESTRICTION)
        val isFogOrDust = weather.condition == WeatherConditionType.FOGGY || (weather.visibilityKm != null && weather.visibilityKm < 0.8)
        checks += if (isFogOrDust) {
            SpecificationRuleCheck(
                ruleName = "Fog / Dust Storm / Poor Visibility",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = weather.visibilityKm?.let { String.format(Locale.US, "%s (Vis %.1f km)", weather.conditionLabel, it) } ?: weather.conditionLabel,
                detail = "Mandatory restriction: ${activity.shortName} laying is prohibited during fog or dust storms."
            )
        } else {
            SpecificationRuleCheck(
                ruleName = "Fog & Visibility Check",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = weather.visibilityKm?.let { String.format(Locale.US, "Clear (%.1f km)", it) } ?: "No fog forecasted",
                detail = "No fog forecasted. Confirm absence of localized dust storms on site."
            )
        }

        // 5. Wind speed > 40 km/h at 2m height (MANDATORY RESTRICTION)
        checks += when {
            weather.windSpeedKmh > MANDATORY_WIND_LIMIT_KMH -> SpecificationRuleCheck(
                ruleName = "Wind Speed Limit (>40 km/h at 2m)",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "%.1f km/h (gusts %.1f km/h)", weather.windSpeedKmh, weather.windGustsKmh),
                detail = "Mandatory restriction: Wind speed exceeds 40 km/h limit, causing rapid mat cooling and poor compaction for ${activity.shortName}."
            )
            weather.windSpeedKmh >= 32.0 || weather.windGustsKmh > MANDATORY_WIND_LIMIT_KMH -> SpecificationRuleCheck(
                ruleName = "Wind Speed Limit (>40 km/h at 2m)",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "%.1f km/h (gusts %.1f km/h)", weather.windSpeedKmh, weather.windGustsKmh),
                detail = "Wind approaches 40 km/h threshold (open-Meteo measures at 10m). Measure actual 2m wind speed on site before laying.",
                requiresSiteVerification = true
            )
            else -> SpecificationRuleCheck(
                ruleName = "Wind Speed Limit (≤40 km/h at 2m)",
                clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f km/h", weather.windSpeedKmh),
                detail = "Forecast wind speed is within the 40 km/h MoRTH/IRC limit."
            )
        }

        // 6. Pavement Surface Temperature (<10°C conventional / <15°C modified bitumen) (MANDATORY RESTRICTION)
        // CRITICAL: Forecast Air Temperature != Measured Pavement Surface Temperature!
        val measuredSurfaceTemp = if (includeSiteMeasurementChecks) siteMeasurements.pavementSurfaceTempC else null
        var requiresSurfaceTempVerification = false
        if (measuredSurfaceTemp != null) {
            checks += if (measuredSurfaceTemp < minSurfaceTemp) {
                SpecificationRuleCheck(
                    ruleName = "Pavement Surface Temp (${binder.displayName})",
                    clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                    isMandatoryRestriction = true,
                    status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                    observedValue = String.format(Locale.US, "Site Surface %.1f°C (Min %.0f°C)", measuredSurfaceTemp, minSurfaceTemp),
                    detail = "Mandatory restriction: Measured pavement surface temperature (${String.format(Locale.US, "%.1f°C", measuredSurfaceTemp)}) is below the required ${minSurfaceTemp.toInt()}°C minimum for ${binder.displayName}."
                )
            } else {
                SpecificationRuleCheck(
                    ruleName = "Pavement Surface Temp (${binder.displayName})",
                    clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                    isMandatoryRestriction = true,
                    status = WorkSuitabilityStatus.SUITABLE,
                    observedValue = String.format(Locale.US, "Site Surface %.1f°C (Min %.0f°C)", measuredSurfaceTemp, minSurfaceTemp),
                    detail = "Measured site pavement surface temperature satisfies the ${minSurfaceTemp.toInt()}°C minimum for ${binder.displayName}."
                )
            }
        } else {
            requiresSurfaceTempVerification = true
            checks += when {
                weather.airTempC < minSurfaceTemp -> SpecificationRuleCheck(
                    ruleName = "Pavement Surface Temp vs Forecast Air Temp (${binder.displayName})",
                    clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                    isMandatoryRestriction = true,
                    status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                    observedValue = String.format(Locale.US, "Forecast Air %.1f°C < %.0f°C Min (Surface Unmeasured)", weather.airTempC, minSurfaceTemp),
                    detail = "Forecast air temperature is below the ${minSurfaceTemp.toInt()}°C pavement surface minimum for ${binder.displayName}. Measure actual pavement surface temperature on site; laying prohibited if surface is <${minSurfaceTemp.toInt()}°C.",
                    requiresSiteVerification = true
                )
                weather.airTempC < minSurfaceTemp + 4.0 -> SpecificationRuleCheck(
                    ruleName = "Pavement Surface Temp Verification (${binder.displayName})",
                    clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                    isMandatoryRestriction = false,
                    status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                    observedValue = String.format(Locale.US, "Forecast Air %.1f°C (Surface Temp Not Measured)", weather.airTempC),
                    detail = "Forecast air temperature is NOT pavement surface temperature. Measure actual pavement surface temperature on site (must be ≥${minSurfaceTemp.toInt()}°C for ${binder.displayName}) before confirming laying.",
                    requiresSiteVerification = true
                )
                else -> SpecificationRuleCheck(
                    ruleName = "Pavement Surface Temp Verification (${binder.displayName})",
                    clauseReference = "$MORTH_5TH_CL_501_5_1 / $IRC_105_2019_CL_3_3_4",
                    isMandatoryRestriction = false,
                    status = WorkSuitabilityStatus.SUITABLE,
                    observedValue = String.format(Locale.US, "Forecast Air %.1f°C (Verify Site Surface ≥%.0f°C)", weather.airTempC, minSurfaceTemp),
                    detail = "Forecast air temperature is well above ${minSurfaceTemp.toInt()}°C, but actual pavement surface temperature must still be verified on site before laying ($mixDesignation).",
                    requiresSiteVerification = true
                )
            }
        }

        // 7. Activity-specific BC vs DBM mix, laying & rolling temperature + tack coat checks
        if (includeSiteMeasurementChecks) {
            val minLayingTemp = (if (isBc) contractSpec.bcLayingTempMinC else contractSpec.dbmLayingTempMinC)
                ?: binder.defaultLayingTempMinC
            val minRollingTemp = (if (isBc) contractSpec.bcRollingTempMinC else contractSpec.dbmRollingTempMinC)
                ?: binder.defaultRollingTempMinC

            siteMeasurements.layingTempC?.let { layingT ->
                checks += if (layingT < minLayingTemp) {
                    SpecificationRuleCheck(
                        ruleName = "${activity.shortName} Site Laying Temperature",
                        clauseReference = clauseRef,
                        isMandatoryRestriction = true,
                        status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                        observedValue = String.format(Locale.US, "%.1f°C (Min %.0f°C)", layingT, minLayingTemp),
                        detail = "Measured laying temperature is below the required ${minLayingTemp.toInt()}°C for $mixDesignation (${binder.displayName})."
                    )
                } else {
                    SpecificationRuleCheck(
                        ruleName = "${activity.shortName} Site Laying Temperature",
                        clauseReference = clauseRef,
                        isMandatoryRestriction = false,
                        status = WorkSuitabilityStatus.SUITABLE,
                        observedValue = String.format(Locale.US, "%.1f°C (Min %.0f°C)", layingT, minLayingTemp),
                        detail = "Site laying temperature meets ${activity.shortName} target for $mixDesignation."
                    )
                }
            }

            siteMeasurements.rollingTempC?.let { rollingT ->
                checks += if (rollingT < minRollingTemp) {
                    SpecificationRuleCheck(
                        ruleName = "${activity.shortName} Site Rolling Temperature",
                        clauseReference = clauseRef,
                        isMandatoryRestriction = true,
                        status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                        observedValue = String.format(Locale.US, "%.1f°C (Min %.0f°C)", rollingT, minRollingTemp),
                        detail = "Measured rolling temperature is below ${minRollingTemp.toInt()}°C; compaction density may not be achieved."
                    )
                } else {
                    SpecificationRuleCheck(
                        ruleName = "${activity.shortName} Site Rolling Temperature",
                        clauseReference = clauseRef,
                        isMandatoryRestriction = false,
                        status = WorkSuitabilityStatus.SUITABLE,
                        observedValue = String.format(Locale.US, "%.1f°C (Min %.0f°C)", rollingT, minRollingTemp),
                        detail = "Site rolling temperature is within ${activity.shortName} compaction window."
                    )
                }
            }

            // Tack coat condition specifically impacts BC (and DBM over bituminous layer)
            if (siteMeasurements.tackCoatCondition == TackCoatCondition.CONTAMINATED_OR_WASHED ||
                siteMeasurements.tackCoatCondition == TackCoatCondition.FRESH_NOT_CURED
            ) {
                checks += SpecificationRuleCheck(
                    ruleName = if (isBc) "BC Underlying Tack Coat Condition" else "DBM Underlying Coat Condition",
                    clauseReference = "MoRTH 5th Rev Cl. 503.4 / ${if (isBc) "Cl. 507.4" else "Cl. 505.4"}",
                    isMandatoryRestriction = true,
                    status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                    observedValue = siteMeasurements.tackCoatCondition.label,
                    detail = "Underlying tack coat must be fully broken/cured, clean, and unwashed before commencing ${activity.shortName} laying."
                )
            }
        }

        // BC wearing course has stricter sensitivity to thin-lift rapid cooling in windy/cool conditions
        if (isBc && weather.windSpeedKmh in 25.0..31.9 && weather.airTempC < 18.0) {
            checks += SpecificationRuleCheck(
                ruleName = "BC Thin-Lift Thermal Loss Advisory",
                clauseReference = MORTH_5TH_BC_REF,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Air %.1f°C, Wind %.1f km/h", weather.airTempC, weather.windSpeedKmh),
                detail = "BC wearing course cools faster than thicker DBM lifts under brisk wind. Keep breakdown roller directly behind paver."
            )
        }

        val aggregated = aggregateStatus(checks, isStaleOnly = false)
        return SnapshotEvalResult(aggregated, checks, requiresSurfaceTempVerification)
    }

    private fun evaluateTackOrPrimeCoat(
        isTackCoat: Boolean,
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val label = if (isTackCoat) "Tack Coat" else "Prime Coat"
        val ref = if (isTackCoat) "MoRTH 5th Rev Cl. 503.4 & Cl. 501.5.1" else "MoRTH 5th Rev Cl. 502.4 & Cl. 501.5.1"
        val checks = mutableListOf<SpecificationRuleCheck>()

        val isRain = weather.condition == WeatherConditionType.RAINY ||
            weather.condition == WeatherConditionType.THUNDERSTORM ||
            weather.precipitationMm >= 0.1 ||
            weather.rainProbabilityPercent >= 55
        if (isRain) {
            checks += SpecificationRuleCheck(
                ruleName = "Rain / Washout Prohibition",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "${weather.conditionLabel} (${weather.rainProbabilityPercent}% rain)",
                detail = "$label spraying is prohibited during rain or imminent rain due to emulsion washout and bond failure."
            )
        }
        if (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.STANDING_WATER ||
                siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.WET_NO_STANDING_WATER)
        ) {
            checks += SpecificationRuleCheck(
                ruleName = "Surface Moisture Condition",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = siteMeasurements.baseMoistureCondition.label,
                detail = if (isTackCoat) {
                    "Surface must be clean and dry before applying Tack Coat (Cl. 503.4)."
                } else {
                    "Standing water or saturated base prevents Prime Coat penetration (Cl. 502.4)."
                }
            )
        }
        if (weather.condition == WeatherConditionType.FOGGY) {
            checks += SpecificationRuleCheck(
                ruleName = "Fog Restriction",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "Foggy",
                detail = "Spraying $label during fog is prohibited under MoRTH Cl. 501.5.1."
            )
        }
        if (weather.windSpeedKmh > 30.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Spray Drift Wind Control",
                clauseReference = ref,
                isMandatoryRestriction = weather.windSpeedKmh > MANDATORY_WIND_LIMIT_KMH,
                status = if (weather.windSpeedKmh > MANDATORY_WIND_LIMIT_KMH) WorkSuitabilityStatus.NOT_RECOMMENDED else WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "%.1f km/h", weather.windSpeedKmh),
                detail = "High wind causes uneven distributor spray fan and binder drift; verify uniform application rate."
            )
        }
        if (weather.airTempC < 10.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Low Temperature Breaking/Curing",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "Air %.1f°C (<10°C)", weather.airTempC),
                detail = "Surface/ambient temperature below 10°C impairs emulsion breaking and curing (Cl. 501.5.1)."
            )
        }
        if (weather.humidityPercent >= 88 && checks.none { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }) {
            checks += SpecificationRuleCheck(
                ruleName = "High Humidity Curing Delay",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "Humidity ${weather.humidityPercent}%",
                detail = "High humidity slows emulsion breaking time; verify full break before paving or traffic."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "$label Weather Check",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, Wind %.1f km/h, Rain %d%%", weather.airTempC, weather.windSpeedKmh, weather.rainProbabilityPercent),
                detail = "Forecast conditions acceptable for $label. Confirm clean surface and calibrated distributor spray rate on site."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, requiresSurfaceTempCheck = false)
    }

    private fun evaluateMilling(weather: WeatherSnapshotInput): SnapshotEvalResult {
        val ref = "MoRTH 5th Rev Cl. 501.8.3 / IRC:SP:83 (Safety & Visibility)"
        val checks = mutableListOf<SpecificationRuleCheck>()
        if (weather.condition == WeatherConditionType.THUNDERSTORM) {
            checks += SpecificationRuleCheck(
                ruleName = "Thunderstorm Safety",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "Thunderstorm",
                detail = "Suspend heavy milling machinery and open-lane work zones during thunderstorms."
            )
        }
        if (weather.condition == WeatherConditionType.FOGGY || (weather.visibilityKm != null && weather.visibilityKm < 0.5)) {
            checks += SpecificationRuleCheck(
                ruleName = "Work-Zone Visibility",
                clauseReference = "$ref / IRC:SP:55",
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = weather.visibilityKm?.let { String.format(Locale.US, "Vis %.1f km", it) } ?: "Foggy",
                detail = "Poor visibility creates severe work-zone traffic hazard during highway milling."
            )
        }
        if (weather.precipitationMm >= 7.5 || weather.wmoCode in listOf(65, 82)) {
            checks += SpecificationRuleCheck(
                ruleName = "Heavy Rain & Milled Box Drainage",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "%.1f mm/hr", weather.precipitationMm),
                detail = "Heavy rain causes water ponding in milled boxes and compromises underlying granular/bituminous layers."
            )
        } else if (weather.precipitationMm >= 0.2 || weather.rainProbabilityPercent >= 60) {
            checks += SpecificationRuleCheck(
                ruleName = "Light Rain & Slurry Handling",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.rainProbabilityPercent}% rain",
                detail = "Milling may proceed cautiously with drainage protection, but do not expose milled surface ahead of heavy rain."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Milling Operational Conditions",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%s, Wind %.1f km/h", weather.conditionLabel, weather.windSpeedKmh),
                detail = "Forecast conditions suitable for profile milling with dust suppression and IRC:SP:55 work-zone traffic safety."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateGlassGrid(
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val ref = "MoRTH Section 700 / Manufacturer & Contract Specification"
        val checks = mutableListOf<SpecificationRuleCheck>()
        val isWetWeather = weather.condition == WeatherConditionType.RAINY ||
            weather.condition == WeatherConditionType.THUNDERSTORM ||
            weather.precipitationMm >= 0.1 ||
            weather.rainProbabilityPercent >= 50
        if (isWetWeather || (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition in listOf(
                SurfaceMoistureCondition.DAMP,
                SurfaceMoistureCondition.WET_NO_STANDING_WATER,
                SurfaceMoistureCondition.STANDING_WATER
            )))
        ) {
            checks += SpecificationRuleCheck(
                ruleName = "Dry Clean Surface Requirement",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = if (isWetWeather) "${weather.conditionLabel} (${weather.rainProbabilityPercent}%)" else siteMeasurements.baseMoistureCondition.label,
                detail = "Glass grid adhesive/tack bond fails on damp or wet pavements; surface must be completely dry and dust-free."
            )
        }
        if (weather.windSpeedKmh >= 25.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Roll Lifting / Wind Alignment Control",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = weather.windSpeedKmh > 38.0,
                status = if (weather.windSpeedKmh > 38.0) WorkSuitabilityStatus.NOT_RECOMMENDED else WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Wind %.1f km/h", weather.windSpeedKmh),
                detail = "Strong winds cause glass grid wrinkling and edge lift prior to bituminous overlay."
            )
        }
        if (weather.airTempC < 10.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Adhesive / Tack Activation Temp",
                clauseReference = "$ref (Verify Manufacturer Spec)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Air %.1f°C", weather.airTempC),
                detail = "Verify manufacturer minimum pavement surface temperature for self-adhesive or tack-bonded glass grid."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Glass Grid Laying Conditions",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, Wind %.1f km/h, Dry", weather.airTempC, weather.windSpeedKmh),
                detail = "Conditions suitable for tensioned glass grid installation. Verify dry, clean surface and immediate overlay plan."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateCrackSealing(
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val ref = "MoRTH Cl. 3004.3 / IRC:82"
        val checks = mutableListOf<SpecificationRuleCheck>()
        val isRain = weather.condition == WeatherConditionType.RAINY ||
            weather.condition == WeatherConditionType.THUNDERSTORM ||
            weather.precipitationMm >= 0.1 ||
            weather.rainProbabilityPercent >= 50
        if (isRain || (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition != SurfaceMoistureCondition.DRY && siteMeasurements.baseMoistureCondition != SurfaceMoistureCondition.UNKNOWN))) {
            checks += SpecificationRuleCheck(
                ruleName = "Dry Crack Walls Requirement",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = if (isRain) "${weather.conditionLabel} (${weather.rainProbabilityPercent}%)" else siteMeasurements.baseMoistureCondition.label,
                detail = "Moisture inside cracks prevents hot-pour/emulsion sealant adhesion and causes foaming."
            )
        }
        if (weather.airTempC < 10.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Low Temperature Sealant Adhesion",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "%.1f°C", weather.airTempC),
                detail = "Pre-warm/dry cracks with hot-air lance per contract specification when ambient temperature is low."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Crack Sealing Conditions",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, Dry", weather.airTempC),
                detail = "Forecast conditions suitable for routing, air-blasting, and crack sealing."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateSurfacePrep(
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val ref = "MoRTH 5th Rev Cl. 501.8 / Cl. 503.4.1"
        val checks = mutableListOf<SpecificationRuleCheck>()
        if (weather.condition == WeatherConditionType.THUNDERSTORM || weather.precipitationMm >= 2.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Active Rain / Storm",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "%s (%.1f mm)", weather.conditionLabel, weather.precipitationMm),
                detail = "Mechanical brooming and air-blasting cannot achieve a clean, dry surface during active rain."
            )
        } else if (weather.rainProbabilityPercent >= 50 || (includeSiteMeasurementChecks && siteMeasurements.baseMoistureCondition in listOf(
                SurfaceMoistureCondition.WET_NO_STANDING_WATER,
                SurfaceMoistureCondition.STANDING_WATER
            ))
        ) {
            checks += SpecificationRuleCheck(
                ruleName = "Surface Wetness / Re-Contamination Risk",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.rainProbabilityPercent}% rain risk",
                detail = "Surface cleaning may require re-brooming and drying before tack coat or bituminous laying."
            )
        }
        if (weather.windSpeedKmh > 35.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Windblown Dust Re-Deposition",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Wind %.1f km/h", weather.windSpeedKmh),
                detail = "High wind blows dust back onto cleaned surfaces; coordinate sweeping immediately ahead of tack coat."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Surface Cleaning Readiness",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = "Dry, Wind ${weather.windSpeedKmh.toInt()} km/h",
                detail = "Conditions suitable for mechanical brooming, air-blasting, and surface inspection."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateGranularLayer(
        layerName: String,
        clauseRef: String,
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val checks = mutableListOf<SpecificationRuleCheck>()
        val isHeavyRain = weather.condition == WeatherConditionType.THUNDERSTORM || weather.precipitationMm >= 4.0
        val isLightRain = weather.condition == WeatherConditionType.RAINY || weather.precipitationMm >= 0.3 || weather.rainProbabilityPercent >= 65
        if (isHeavyRain || (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.STANDING_WATER))) {
            checks += SpecificationRuleCheck(
                ruleName = "$layerName Moisture / Subgrade Saturation",
                clauseReference = clauseRef,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = if (isHeavyRain) String.format(Locale.US, "%s (%.1f mm)", weather.conditionLabel, weather.precipitationMm) else "Standing water",
                detail = "Excess water exceeds Optimum Moisture Content (OMC) tolerance and risks subgrade/granular rutting under rollers."
            )
        } else if (isLightRain) {
            checks += SpecificationRuleCheck(
                ruleName = "$layerName OMC Control",
                clauseReference = clauseRef,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.rainProbabilityPercent}% rain (${String.format(Locale.US, "%.1f mm", weather.precipitationMm)})",
                detail = "Verify field moisture stays within OMC tolerance (±1–2% per Cl. 401/406) before rolling."
            )
        }
        if (weather.airTempC >= 40.0 || weather.windSpeedKmh >= 35.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Rapid Moisture Evaporation",
                clauseReference = "$clauseRef (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "%.1f°C, Wind %.1f km/h", weather.airTempC, weather.windSpeedKmh),
                detail = "High evaporation rate may dry $layerName below OMC; keep water tanker ready during compaction."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "$layerName Laying & Compaction Weather",
                clauseReference = clauseRef,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, Rain %d%%", weather.airTempC, weather.rainProbabilityPercent),
                detail = "Weather suitable for $layerName spreading, moisture conditioning, and vibratory rolling."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateEarthwork(
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val ref = "MoRTH 5th Rev Section 300, Cl. 305.3"
        val checks = mutableListOf<SpecificationRuleCheck>()
        val isRain = weather.condition == WeatherConditionType.THUNDERSTORM ||
            weather.precipitationMm >= 2.0 ||
            (weather.condition == WeatherConditionType.RAINY && weather.precipitationMm >= 0.5)
        if (isRain || (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition == SurfaceMoistureCondition.STANDING_WATER))) {
            checks += SpecificationRuleCheck(
                ruleName = "Wet Soil / Waterlogged Compaction Restriction",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = if (isRain) String.format(Locale.US, "%s (%.1f mm)", weather.conditionLabel, weather.precipitationMm) else "Standing water",
                detail = "Embankment/subgrade compaction is not permitted on saturated or waterlogged soil exceeding OMC tolerance."
            )
        } else if (weather.rainProbabilityPercent >= 60) {
            checks += SpecificationRuleCheck(
                ruleName = "Rain Exposure Protection",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.rainProbabilityPercent}% rain probability",
                detail = "Seal and grade embankment top with crossfall before rain to prevent ponding."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Earthwork & Subgrade Weather",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, Rain %d%%", weather.airTempC, weather.rainProbabilityPercent),
                detail = "Conditions suitable for excavation, embankment spreading, and OMC-controlled compaction."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateRoadMarking(
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val ref = "MoRTH 5th Rev Cl. 803.4 / IRC:35-2015"
        val checks = mutableListOf<SpecificationRuleCheck>()
        val isWet = weather.condition == WeatherConditionType.RAINY ||
            weather.condition == WeatherConditionType.THUNDERSTORM ||
            weather.condition == WeatherConditionType.FOGGY ||
            weather.precipitationMm >= 0.1 ||
            weather.rainProbabilityPercent >= 50
        if (isWet || (includeSiteMeasurementChecks && (siteMeasurements.hasStandingWater || siteMeasurements.baseMoistureCondition in listOf(
                SurfaceMoistureCondition.DAMP,
                SurfaceMoistureCondition.WET_NO_STANDING_WATER,
                SurfaceMoistureCondition.STANDING_WATER
            )))
        ) {
            checks += SpecificationRuleCheck(
                ruleName = "Dry Pavement Requirement (No Rain/Dew)",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = if (isWet) "${weather.conditionLabel} (${weather.rainProbabilityPercent}%)" else siteMeasurements.baseMoistureCondition.label,
                detail = "Thermoplastic road marking must only be applied to a completely dry, clean surface free from rain, dew, or dampness."
            )
        }
        if (weather.airTempC < 10.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Minimum Surface Temp for Thermoplastic",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "Air %.1f°C (<10°C)", weather.airTempC),
                detail = "Low pavement temperature impairs thermoplastic bonding and glass-bead embedment."
            )
        }
        if (weather.humidityPercent >= 85 && checks.none { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }) {
            checks += SpecificationRuleCheck(
                ruleName = "Condensation / Dew-Point Check",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "Humidity ${weather.humidityPercent}%",
                detail = "High humidity increases surface dew risk; confirm pavement surface is completely dry before marking."
            )
        }
        if (weather.windSpeedKmh >= 30.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Glass Bead Wind Dispersion",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Wind %.1f km/h", weather.windSpeedKmh),
                detail = "Strong wind disperses drop-on retroreflective glass beads; shield dispenser or wait for calmer wind."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Road Marking Weather",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, RH %d%%, Wind %.1f km/h", weather.airTempC, weather.humidityPercent, weather.windSpeedKmh),
                detail = "Conditions suitable for thermoplastic road marking and glass-bead application."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateConcreteWorks(
        weather: WeatherSnapshotInput,
        siteMeasurements: ActualSiteMeasurements,
        includeSiteMeasurementChecks: Boolean
    ): SnapshotEvalResult {
        val ref = "MoRTH 5th Rev Section 600 & 1700 / IS:456 / IRC:15"
        val checks = mutableListOf<SpecificationRuleCheck>()
        if (weather.condition == WeatherConditionType.THUNDERSTORM || weather.precipitationMm >= 2.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Rain Protection During Concreting",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "%s (%.1f mm)", weather.conditionLabel, weather.precipitationMm),
                detail = "Unprotected concreting in rain alters water-cement ratio and damages fresh surface finish/texturing."
            )
        } else if (weather.rainProbabilityPercent >= 55) {
            checks += SpecificationRuleCheck(
                ruleName = "Rain Tarpaulin / Tent Readiness",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.rainProbabilityPercent}% rain probability",
                detail = "Keep waterproof curing covers/tents ready on site prior to starting concrete pour."
            )
        }
        if (weather.airTempC >= 40.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Hot-Weather Concreting Control (IS:7861 / MoRTH Cl. 602)",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Air %.1f°C", weather.airTempC),
                detail = "High ambient temperature elevates plastic shrinkage risk. Verify concrete placement temp (≤30°C for PQC / contract limit) and immediate curing compound application."
            )
        } else if (weather.airTempC <= 4.5) {
            checks += SpecificationRuleCheck(
                ruleName = "Cold-Weather Concreting Control",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = String.format(Locale.US, "Air %.1f°C", weather.airTempC),
                detail = "Near-freezing temperature requires cold-weather concreting methodology and Engineer approval."
            )
        }
        if (weather.windSpeedKmh >= 30.0 && weather.humidityPercent < 50) {
            checks += SpecificationRuleCheck(
                ruleName = "Plastic Shrinkage Evaporation Risk",
                clauseReference = "$ref (Advisory)",
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = String.format(Locale.US, "Wind %.1f km/h, RH %d%%", weather.windSpeedKmh, weather.humidityPercent),
                detail = "Wind + low humidity accelerates surface moisture evaporation; deploy windbreaks/fog spray and immediate curing."
            )
        }
        if (includeSiteMeasurementChecks && siteMeasurements.hasStandingWater) {
            checks += SpecificationRuleCheck(
                ruleName = "Formwork / DLC Surface Ponding",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "Standing water reported",
                detail = "Remove all standing water from formwork/base before placing concrete."
            )
        }
        if (checks.isEmpty()) {
            checks += SpecificationRuleCheck(
                ruleName = "Concreting Weather",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, RH %d%%, Rain %d%%", weather.airTempC, weather.humidityPercent, weather.rainProbabilityPercent),
                detail = "Forecast conditions suitable for concreting subject to mix temperature, slump, and curing verification."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun evaluateGeneralMaintenance(weather: WeatherSnapshotInput): SnapshotEvalResult {
        val ref = "MoRTH Section 3000 / IRC:82 / IRC:SP:55"
        val checks = mutableListOf<SpecificationRuleCheck>()
        if (weather.condition == WeatherConditionType.THUNDERSTORM || weather.windSpeedKmh > 55.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Severe Storm / Crew Safety",
                clauseReference = ref,
                isMandatoryRestriction = true,
                status = WorkSuitabilityStatus.NOT_RECOMMENDED,
                observedValue = "${weather.conditionLabel}, Wind ${weather.windSpeedKmh.toInt()} km/h",
                detail = "Restrict routine highway maintenance to emergency drainage/hazard clearance during severe storms."
            )
        } else if (weather.condition == WeatherConditionType.FOGGY || weather.precipitationMm >= 2.0) {
            checks += SpecificationRuleCheck(
                ruleName = "Work-Zone Visibility & Wet Maintenance",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE_WITH_CAUTION,
                observedValue = "${weather.conditionLabel} (${String.format(Locale.US, "%.1f mm", weather.precipitationMm)})",
                detail = "Prioritize drainage clearance and high-visibility traffic control; defer hot-mix pothole patching until dry."
            )
        } else {
            checks += SpecificationRuleCheck(
                ruleName = "Routine Maintenance Weather",
                clauseReference = ref,
                isMandatoryRestriction = false,
                status = WorkSuitabilityStatus.SUITABLE,
                observedValue = String.format(Locale.US, "%.1f°C, %s", weather.airTempC, weather.conditionLabel),
                detail = "Conditions suitable for routine shoulder, drain, signage, and corridor maintenance."
            )
        }
        return SnapshotEvalResult(aggregateStatus(checks, false), checks, false)
    }

    private fun aggregateStatus(
        checks: List<SpecificationRuleCheck>,
        isStaleOnly: Boolean
    ): WorkSuitabilityStatus {
        if (checks.isEmpty()) return WorkSuitabilityStatus.INSUFFICIENT_DATA
        if (checks.any { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }) {
            return WorkSuitabilityStatus.NOT_RECOMMENDED
        }
        if (checks.any { it.status == WorkSuitabilityStatus.INSUFFICIENT_DATA }) {
            return WorkSuitabilityStatus.INSUFFICIENT_DATA
        }
        if (isStaleOnly || checks.any { it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION }) {
            return WorkSuitabilityStatus.SUITABLE_WITH_CAUTION
        }
        return WorkSuitabilityStatus.SUITABLE
    }

    private fun buildSummaryReason(
        activity: HighwayWorkActivity,
        status: WorkSuitabilityStatus,
        checks: List<SpecificationRuleCheck>,
        requiresSurfaceTempCheck: Boolean
    ): String {
        return when (status) {
            WorkSuitabilityStatus.NOT_RECOMMENDED -> {
                val blockers = checks.filter { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }
                val primary = blockers.firstOrNull()
                if (primary != null) {
                    "${primary.ruleName} (${primary.observedValue}): ${primary.detail}"
                } else {
                    "Mandatory specification restrictions prohibit ${activity.displayName} under current conditions."
                }
            }
            WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> {
                val cautions = checks.filter { it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION }
                val primary = cautions.firstOrNull()
                primary?.detail ?: "Proceed cautiously with site verification for ${activity.displayName}."
            }
            WorkSuitabilityStatus.INSUFFICIENT_DATA -> {
                "Insufficient weather or site verification data for ${activity.displayName}."
            }
            WorkSuitabilityStatus.SUITABLE -> {
                if (requiresSurfaceTempCheck) {
                    "Forecast within limits for ${activity.shortName}. Measure actual pavement surface temperature and confirm dry base on site before laying."
                } else {
                    "Current weather conditions meet applicable guidance for ${activity.displayName}. Final execution remains subject to site inspection and Engineer approval."
                }
            }
        }
    }

    private fun resolveReferenceHeader(
        activity: HighwayWorkActivity,
        contractSpec: ContractSpecificationConfig,
        referenceTimeMs: Long
    ): String {
        val base = when (activity) {
            HighwayWorkActivity.BC_LAYING -> MORTH_5TH_BC_REF
            HighwayWorkActivity.DBM_LAYING -> MORTH_5TH_DBM_REF
            else -> activity.primaryReference
        }
        return if (contractSpec.morthRevision == MoRTHRevision.SIXTH_REVISION_2026) {
            val statusLabel = if (contractSpec.isSixthRevisionEffective(referenceTimeMs)) {
                "6th Rev (2026) Selected"
            } else {
                "6th Rev (2026 — NH Effective 1 Jan 2027)"
            }
            "$statusLabel • Baseline: $base"
        } else {
            base
        }
    }
}
