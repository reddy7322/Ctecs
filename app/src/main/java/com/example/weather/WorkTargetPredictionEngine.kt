package com.example.weather

import com.example.model.DelayRiskSummary
import com.example.model.DelayRiskWarning
import com.example.model.ForecastConfidence
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.HourlyWorkWindow
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherConditionType
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetInput
import com.example.model.WorkTargetPrediction
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Highway Work Target & Completion Prediction Engine + Weather Delay & Risk Analyzer.
 *
 * Engineering Discipline:
 * - Uses transparent, standard civil-engineering formulas:
 *     Area (m²) = Length (m) × Width (m)
 *     Compacted Volume (m³) = Length (m) × Width (m) × Compacted Thickness (m)
 *     Weather-Adjusted Target = Net Suitable Working Time (hr) × Effective Hourly Rate
 * - NEVER invents missing production rates or hidden efficiencies; if expectedProductionRatePerHour
 *   is null or <= 0, flags the missing input explicitly so the engineer enters or selects a rate.
 * - Clearly labels all outputs as ESTIMATES (not guaranteed production or completion).
 */
object WorkTargetPredictionEngine {

    /**
     * Computes realistic daily target, weather-adjusted target, completion percentage,
     * delay estimate, remaining balance, and alternative work window.
     */
    fun predictWorkTarget(
        input: WorkTargetInput,
        hourlyTimeline: List<HourlyWorkWindow>
    ): WorkTargetPrediction {
        val missingWarnings = mutableListOf<String>()
        val lengthM = input.totalLengthMeters
        if (lengthM <= 0.0) {
            missingWarnings += "Enter valid Start and End Chainage to compute section length."
        }
        val widthM = input.workingWidthMeters.coerceAtLeast(0.0)
        if (widthM <= 0.0) {
            missingWarnings += "Enter working width (m)."
        }
        val thicknessM = (input.layerThicknessMm / 1000.0).coerceAtLeast(0.0)
        val areaSqm = lengthM * widthM
        val compactedVolumeM3 = if (thicknessM > 0.0) areaSqm * thicknessM else 0.0

        val usesVolumeUnit = input.activity in setOf(
            HighwayWorkActivity.GSB_OPERATIONS,
            HighwayWorkActivity.WMM_OPERATIONS,
            HighwayWorkActivity.EARTHWORK,
            HighwayWorkActivity.CONCRETE_WORKS
        )
        val usesLinearMeters = input.activity == HighwayWorkActivity.CRACK_SEALING
        val usesKm = input.activity == HighwayWorkActivity.GENERAL_HIGHWAY_MAINTENANCE

        val quantityUnit: String
        val plannedTargetQuantity: Double
        val quantityBasisNote: String
        when {
            usesVolumeUnit -> {
                quantityUnit = "m³ (compacted)"
                plannedTargetQuantity = compactedVolumeM3
                quantityBasisNote = "Compacted Volume = Length × Width × Compacted Thickness (distinct from loose/uncompacted haulage volume)."
                if (thicknessM <= 0.0) {
                    missingWarnings += "Enter compacted layer thickness (mm) for ${input.activity.shortName} volume."
                }
            }
            usesLinearMeters -> {
                quantityUnit = "m"
                plannedTargetQuantity = lengthM
                quantityBasisNote = "Linear Chainage Length = |End Chainage − Start Chainage| × 1,000 m."
            }
            usesKm -> {
                quantityUnit = "km"
                plannedTargetQuantity = lengthM / 1000.0
                quantityBasisNote = "Corridor Length (km) = |End Chainage − Start Chainage|."
            }
            else -> {
                quantityUnit = "m²"
                plannedTargetQuantity = areaSqm
                quantityBasisNote = if (thicknessM > 0.0) {
                    "Paving Area = Length × Width (Compacted Volume = ${String.format(Locale.US, "%.1f", compactedVolumeM3)} m³; loose uncompacted volume is higher per mix compaction factor)."
                } else {
                    "Surface Area = Length × Width."
                }
            }
        }

        val rawRate = input.expectedProductionRatePerHour
        if (rawRate == null || rawRate <= 0.0) {
            missingWarnings += "Enter Expected Production Rate (${input.activity.productionUnitLabel}) — CTECS does not invent production rates."
        }

        // Evaluate resource / plant bottlenecks strictly from user-entered capacities (never fabricated)
        val manpowerFactor = (input.manpowerAvailabilityPercent.coerceIn(10, 100)) / 100.0
        var effectiveRate = (rawRate ?: 0.0) * manpowerFactor
        var limitingResource = if (manpowerFactor < 1.0) {
            "Adjusted for ${input.manpowerAvailabilityPercent}% manpower/crew availability"
        } else {
            "Governed by ${input.productionRateSource.label.lowercase(Locale.US)} production rate"
        }
        val reductionReasons = mutableListOf<String>()
        if (manpowerFactor < 1.0) {
            reductionReasons += "Crew/equipment availability at ${input.manpowerAvailabilityPercent}%"
        }

        // Paver capacity cap (for area-based paving works)
        if (!usesVolumeUnit && !usesLinearMeters && !usesKm && input.paverCapacitySqmPerHour != null && input.paverCapacitySqmPerHour > 0.0) {
            if (effectiveRate > input.paverCapacitySqmPerHour) {
                effectiveRate = input.paverCapacitySqmPerHour
                limitingResource = "Limited by Paver Capacity (${String.format(Locale.US, "%.0f", input.paverCapacitySqmPerHour)} m²/hr)"
                reductionReasons += limitingResource
            }
        }

        // HMP plant capacity cap (for bituminous hot mix works when thickness > 0)
        if (input.activity.isBituminousHotMix && thicknessM > 0.0 && input.hmpCapacityTph != null && input.hmpCapacityTph > 0.0) {
            val density = input.compactBulkDensityTPerM3.coerceIn(1.5, 2.8)
            val maxSqmFromHmp = input.hmpCapacityTph / (thicknessM * density)
            if (effectiveRate > maxSqmFromHmp) {
                effectiveRate = maxSqmFromHmp
                limitingResource = String.format(
                    Locale.US,
                    "Limited by HMP Capacity (%.0f TPH ≈ %.0f m²/hr at %.0f mm)",
                    input.hmpCapacityTph,
                    maxSqmFromHmp,
                    input.layerThicknessMm
                )
                reductionReasons += limitingResource
            }
        }

        // Dumper haulage cycle cap (for bituminous hot mix works when dumpers count entered)
        if (input.activity.isBituminousHotMix && thicknessM > 0.0 && input.dumpersCount != null && input.dumpersCount > 0) {
            val tripsPerHrPerDumper = 60.0 / input.dumperCycleMinutes.coerceAtLeast(5.0)
            val haulTph = input.dumpersCount * input.dumperPayloadTonnes * tripsPerHrPerDumper
            val maxSqmFromDumpers = haulTph / (thicknessM * input.compactBulkDensityTPerM3.coerceIn(1.5, 2.8))
            if (effectiveRate > maxSqmFromDumpers) {
                effectiveRate = maxSqmFromDumpers
                limitingResource = String.format(
                    Locale.US,
                    "Limited by Haulage (%d dumpers ≈ %.0f TPH / %.0f m²/hr)",
                    input.dumpersCount,
                    haulTph,
                    maxSqmFromDumpers
                )
                reductionReasons += limitingResource
            }
        }

        // Roller availability check for paving/compaction works
        if (input.rollerCountAvailable != null && input.rollerCountAvailable == 0) {
            effectiveRate = 0.0
            limitingResource = "No rollers available on site (compaction required)"
            reductionReasons += limitingResource
        }

        // Analyze planned work shift window against hourly suitability timeline
        val shiftHoursCount = ceil(input.availableWorkingHours).toInt().coerceIn(1, 24)
        val plannedHoursSet = buildSet {
            for (offset in 0 until shiftHoursCount) {
                add((input.plannedStartHour + offset) % 24)
            }
        }
        val shiftWindows = hourlyTimeline.filter { w ->
            w.hourPoint.localHourOfDay in plannedHoursSet &&
                (input.nightWorkAllowed || w.hourPoint.isDay || w.hourPoint.localHourOfDay in 6..18)
        }.take(shiftHoursCount)

        var suitableHours = 0.0
        var cautionHours = 0.0
        var interruptedHours = 0.0
        var rainEvents = 0
        var prevWasWet = false

        if (shiftWindows.isNotEmpty()) {
            for (win in shiftWindows) {
                val isWetHour = win.hourPoint.precipitationMm >= 0.2 || win.hourPoint.rainProbabilityPercent >= 60
                if (isWetHour && !prevWasWet) {
                    rainEvents++
                }
                prevWasWet = isWetHour

                when (win.status) {
                    WorkSuitabilityStatus.SUITABLE -> suitableHours += 1.0
                    WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> {
                        suitableHours += 0.85 // Conservative allowance for site checks/slower operations
                        cautionHours += 1.0
                    }
                    WorkSuitabilityStatus.NOT_RECOMMENDED -> interruptedHours += 1.0
                    WorkSuitabilityStatus.INSUFFICIENT_DATA -> interruptedHours += 1.0
                }
            }
            // Scale if availableWorkingHours is fractional relative to shiftWindows.size
            val scale = input.availableWorkingHours / shiftWindows.size.toDouble().coerceAtLeast(1.0)
            if (scale < 1.0) {
                suitableHours *= scale
                interruptedHours *= scale
            }
        } else {
            missingWarnings += "Hourly weather forecast unavailable for the planned shift window."
        }

        // Wet-surface drying & recovery delay applies to moisture-sensitive activities after rain interruptions
        val isMoistureSensitive = input.activity.isBituminousHotMix || input.activity in setOf(
            HighwayWorkActivity.TACK_COAT_APPLICATION,
            HighwayWorkActivity.PRIME_COAT_APPLICATION,
            HighwayWorkActivity.GLASS_GRID_APPLICATION,
            HighwayWorkActivity.CRACK_SEALING,
            HighwayWorkActivity.ROAD_MARKING,
            HighwayWorkActivity.SURFACE_PREPARATION_CLEANING
        )
        val wetRecoveryHours = if (isMoistureSensitive && rainEvents > 0 && suitableHours > 0.0) {
            min(suitableHours, rainEvents * input.wetSurfaceRecoveryHoursPerRainEvent)
        } else {
            0.0
        }

        val prepHours = if (suitableHours > 0.0) min(input.prepAndInspectionHours, max(0.0, suitableHours - wetRecoveryHours)) else 0.0
        val netEffectiveHours = max(0.0, suitableHours - wetRecoveryHours - prepHours)

        if (interruptedHours > 0.0) {
            reductionReasons += String.format(Locale.US, "%.1f hr unsuitable weather interruption during planned shift", interruptedHours)
        }
        if (wetRecoveryHours > 0.0) {
            reductionReasons += String.format(Locale.US, "%.1f hr estimated surface drying & recovery after rain", wetRecoveryHours)
        }
        if (cautionHours > 0.0) {
            reductionReasons += String.format(Locale.US, "%.0f hr caution window (site checks / wind / temperature monitoring)", cautionHours)
        }
        if (prepHours > 0.0) {
            reductionReasons += String.format(Locale.US, "%.2f hr surface preparation & Engineer inspection allowance", prepHours)
        }

        val isComplete = missingWarnings.isEmpty() && effectiveRate > 0.0 && plannedTargetQuantity > 0.0
        val dailyWeatherAdjustedCapacity = netEffectiveHours * effectiveRate
        val weatherAdjustedTarget = if (isComplete) min(plannedTargetQuantity, dailyWeatherAdjustedCapacity) else 0.0
        val remainingBalance = max(0.0, plannedTargetQuantity - weatherAdjustedTarget)
        val completionPct = if (isComplete && plannedTargetQuantity > 0.0) {
            ((weatherAdjustedTarget / plannedTargetQuantity) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val estCompletionHours = if (isComplete && effectiveRate > 0.0) {
            plannedTargetQuantity / effectiveRate
        } else {
            null
        }
        val remainingHoursNeeded = if (isComplete && effectiveRate > 0.0) {
            remainingBalance / effectiveRate
        } else {
            null
        }
        val estDelayHours = interruptedHours + wetRecoveryHours
        val estCompletionDays = if (isComplete && dailyWeatherAdjustedCapacity > 0.0) {
            plannedTargetQuantity / dailyWeatherAdjustedCapacity
        } else if (isComplete && effectiveRate > 0.0 && input.availableWorkingHours > input.prepAndInspectionHours) {
            // Today has 0 effective weather hours; estimate based on clear-day capacity
            val clearDayNetHours = max(1.0, input.availableWorkingHours - input.prepAndInspectionHours)
            1.0 + (plannedTargetQuantity / (clearDayNetHours * effectiveRate))
        } else {
            null
        }

        val alternativeWindow = findBestAlternativeWindow(hourlyTimeline, input.nightWorkAllowed)
        val overallConfidence = when {
            shiftWindows.isEmpty() -> ForecastConfidence.LOW
            shiftWindows.any { it.confidence == ForecastConfidence.LOW } -> ForecastConfidence.MODERATE
            rainEvents > 0 || cautionHours > 0 -> ForecastConfidence.MODERATE
            else -> ForecastConfidence.HIGH
        }

        return WorkTargetPrediction(
            activity = input.activity,
            isCalculationComplete = isComplete,
            missingInputWarnings = missingWarnings,
            totalLengthMeters = lengthM,
            totalAreaSqm = areaSqm,
            compactedVolumeM3 = compactedVolumeM3,
            quantityBasisNote = quantityBasisNote,
            plannedTargetQuantity = plannedTargetQuantity,
            weatherAdjustedTargetQuantity = weatherAdjustedTarget,
            quantityUnit = quantityUnit,
            effectiveHourlyRate = effectiveRate,
            rateSourceTag = input.productionRateSource,
            limitingResourceNote = limitingResource,
            plannedWorkingHours = input.availableWorkingHours,
            suitableWeatherHours = suitableHours,
            weatherInterruptionHours = interruptedHours,
            wetSurfaceRecoveryHours = wetRecoveryHours,
            prepAndInspectionHours = prepHours,
            netEffectiveWorkingHours = netEffectiveHours,
            expectedCompletionPercent = completionPct,
            estimatedCompletionHours = estCompletionHours,
            estimatedDelayHours = estDelayHours,
            remainingBalanceQuantity = remainingBalance,
            remainingHoursNeeded = remainingHoursNeeded,
            expectedCompletionDaysEstimate = estCompletionDays,
            alternativeWorkWindowLabel = alternativeWindow,
            targetReductionReasons = reductionReasons,
            confidence = overallConfidence
        )
    }

    /**
     * Generates specific Weather-Based Delay & Risk warnings, probability ranges,
     * lost-hour bounds, and next suitable window from the live hourly timeline.
     */
    fun analyzeDelayRisks(
        activity: HighwayWorkActivity,
        hourlyTimeline: List<HourlyWorkWindow>,
        targetPrediction: WorkTargetPrediction? = null
    ): DelayRiskSummary {
        if (hourlyTimeline.isEmpty()) {
            return DelayRiskSummary(
                warnings = emptyList(),
                estimatedLostHoursMin = 0.0,
                estimatedLostHoursMax = 0.0,
                expectedDelayRangeLabel = "Insufficient forecast data",
                nextSuitableWindowLabel = null,
                dailyTargetImpactSummary = "Refresh live forecast to compute weather delay risks.",
                overallConfidence = ForecastConfidence.LOW
            )
        }

        val warnings = mutableListOf<DelayRiskWarning>()
        val next24 = hourlyTimeline.take(24)

        // 1. Thunderstorm / Severe weather warning
        val storms = next24.filter { it.hourPoint.condition == WeatherConditionType.THUNDERSTORM }
        if (storms.isNotEmpty()) {
            val maxProb = storms.maxOf { it.hourPoint.rainProbabilityPercent }.coerceAtLeast(65)
            warnings += DelayRiskWarning(
                title = "Thunderstorms & Severe Weather",
                severity = WeatherAlertSeverity.SEVERE,
                probabilityRangeLabel = "${(maxProb - 10).coerceAtLeast(50)}–${maxProb.coerceAtMost(95)}%",
                confidence = ForecastConfidence.MODERATE,
                impactedWindowLabel = formatWindowSpan(storms.first().hourPoint, storms.last().hourPoint),
                impactSummary = "Immediate suspension of ${activity.shortName}, crane/equipment stoppage, and drainage inspection required."
            )
        }

        // 2. Rain during BC/DBM laying or Heavy rain after surface prep
        val rainHours = next24.filter { it.hourPoint.precipitationMm >= 0.2 || it.hourPoint.rainProbabilityPercent >= 55 }
        val heavyRainHours = next24.filter { it.hourPoint.precipitationMm >= 7.5 || it.hourPoint.wmoCode in listOf(65, 82) }
        if (heavyRainHours.isNotEmpty()) {
            val maxProb = heavyRainHours.maxOf { it.hourPoint.rainProbabilityPercent }.coerceAtLeast(70)
            warnings += DelayRiskWarning(
                title = "Heavy Rainfall After Surface Prep / Laying Risk",
                severity = WeatherAlertSeverity.SEVERE,
                probabilityRangeLabel = "${(maxProb - 10).coerceAtLeast(55)}–${maxProb.coerceAtMost(95)}%",
                confidence = ForecastConfidence.MODERATE,
                impactedWindowLabel = formatWindowSpan(heavyRainHours.first().hourPoint, heavyRainHours.last().hourPoint),
                impactSummary = "High risk of tack-coat washout, surface ponding, and 2–4 hr post-rain drying/re-cleaning delay."
            )
        } else if (rainHours.isNotEmpty()) {
            val minProb = rainHours.minOf { it.hourPoint.rainProbabilityPercent }
            val maxProb = rainHours.maxOf { it.hourPoint.rainProbabilityPercent }
            warnings += DelayRiskWarning(
                title = if (activity.isBituminousHotMix) "Rain Risk During ${activity.shortName} Laying" else "Rainfall Work Interruption Risk",
                severity = WeatherAlertSeverity.WARNING,
                probabilityRangeLabel = "$minProb–$maxProb%",
                confidence = ForecastConfidence.MODERATE,
                impactedWindowLabel = formatWindowSpan(rainHours.first().hourPoint, rainHours.last().hourPoint),
                impactSummary = if (activity.isBituminousHotMix) {
                    "MoRTH Cl. 501.5.1 prohibits laying during imminent/active rain; plan transverse construction joint ahead of rain arrival."
                } else {
                    "Rain expected for ${rainHours.size} hr(s); allow surface drying and moisture re-testing before resuming."
                }
            )
        }

        // 3. Surface drying delay warning
        if (rainHours.isNotEmpty() && (activity.isBituminousHotMix || activity in setOf(
                HighwayWorkActivity.TACK_COAT_APPLICATION,
                HighwayWorkActivity.PRIME_COAT_APPLICATION,
                HighwayWorkActivity.ROAD_MARKING,
                HighwayWorkActivity.GLASS_GRID_APPLICATION
            ))
        ) {
            warnings += DelayRiskWarning(
                title = "Post-Rain Surface Drying Delay",
                severity = WeatherAlertSeverity.ADVISORY,
                probabilityRangeLabel = "60–85%",
                confidence = ForecastConfidence.MODERATE,
                impactedWindowLabel = "1.5–3.0 hrs after rainfall ceases",
                impactSummary = "Damp base/binder prevents bituminous & marking adhesion until fully dry and re-inspected."
            )
        }

        // 4. High wind warning
        val highWindHours = next24.filter { it.hourPoint.windSpeedKmh > 35.0 || it.hourPoint.windGustsKmh > 45.0 }
        if (highWindHours.isNotEmpty()) {
            val peakWind = highWindHours.maxOf { it.hourPoint.windSpeedKmh }
            warnings += DelayRiskWarning(
                title = "High Wind Speed Warning (Peak ${peakWind.toInt()} km/h)",
                severity = if (peakWind > 40.0) WeatherAlertSeverity.WARNING else WeatherAlertSeverity.ADVISORY,
                probabilityRangeLabel = "55–80%",
                confidence = ForecastConfidence.MODERATE,
                impactedWindowLabel = formatWindowSpan(highWindHours.first().hourPoint, highWindHours.last().hourPoint),
                impactSummary = "Wind >40 km/h at 2m prohibits bituminous laying (Cl. 501.5.1) and increases spray drift & thermal mat loss."
            )
        }

        // 5. Low temperature warning
        val lowTempHours = next24.filter { it.hourPoint.temperatureC < 12.0 }
        if (lowTempHours.isNotEmpty()) {
            val minT = lowTempHours.minOf { it.hourPoint.temperatureC }
            warnings += DelayRiskWarning(
                title = "Low Temperature Restriction Risk (Min ${String.format(Locale.US, "%.1f°C", minT)})",
                severity = WeatherAlertSeverity.WARNING,
                probabilityRangeLabel = "65–90%",
                confidence = ForecastConfidence.HIGH,
                impactedWindowLabel = formatWindowSpan(lowTempHours.first().hourPoint, lowTempHours.last().hourPoint),
                impactSummary = "Early morning / night surface temperatures may drop below 10°C (conventional) or 15°C (PMB/CRMB). Measure actual pavement surface temperature before laying."
            )
        }

        // 6. High temperature warning
        val highTempHours = next24.filter { it.hourPoint.temperatureC >= 40.0 }
        if (highTempHours.isNotEmpty()) {
            val maxT = highTempHours.maxOf { it.hourPoint.temperatureC }
            warnings += DelayRiskWarning(
                title = "High Ambient Temperature Advisory (Peak ${String.format(Locale.US, "%.1f°C", maxT)})",
                severity = WeatherAlertSeverity.ADVISORY,
                probabilityRangeLabel = "70–90%",
                confidence = ForecastConfidence.HIGH,
                impactedWindowLabel = formatWindowSpan(highTempHours.first().hourPoint, highTempHours.last().hourPoint),
                impactSummary = "Increased crew heat-stress risk, rapid WMM/GSB OMC drying, and concrete plastic shrinkage risk."
            )
        }

        // 7. Fog / low visibility warning
        val fogHours = next24.filter { it.hourPoint.condition == WeatherConditionType.FOGGY || (it.hourPoint.visibilityKm != null && it.hourPoint.visibilityKm < 1.0) }
        if (fogHours.isNotEmpty()) {
            warnings += DelayRiskWarning(
                title = "Fog & Reduced Visibility Risk",
                severity = WeatherAlertSeverity.WARNING,
                probabilityRangeLabel = "50–80%",
                confidence = ForecastConfidence.MODERATE,
                impactedWindowLabel = formatWindowSpan(fogHours.first().hourPoint, fogHours.last().hourPoint),
                impactSummary = "Bituminous laying prohibited in fog (Cl. 501.5.1); delay start until visibility clears for highway traffic safety."
            )
        }

        val unsuitableCount = next24.count { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }
        val cautionCount = next24.count { it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION }
        val lostMin = max(0.0, floor(unsuitableCount * 0.75 * 2.0) / 2.0)
        val lostMax = max(lostMin, ceil((unsuitableCount + cautionCount * 0.4 + if (rainHours.isNotEmpty()) 1.5 else 0.0) * 2.0) / 2.0)

        val delayLabel = when {
            lostMax <= 0.0 -> "0 hrs (Minimal weather delay expected)"
            lostMin == lostMax -> String.format(Locale.US, "~%.1f hrs estimated weather delay", lostMax)
            else -> String.format(Locale.US, "%.1f – %.1f hrs estimated weather delay", lostMin, lostMax)
        }

        val nextSuitable = findBestAlternativeWindow(next24, nightWorkAllowed = false)
        val dailyImpact = when {
            targetPrediction != null && targetPrediction.isCalculationComplete -> {
                val pct = targetPrediction.expectedCompletionPercent
                String.format(
                    Locale.US,
                    "Estimated %.0f%% of planned %s target achievable today (%.1f %s of %.1f %s).",
                    pct,
                    activity.shortName,
                    targetPrediction.weatherAdjustedTargetQuantity,
                    targetPrediction.quantityUnit,
                    targetPrediction.plannedTargetQuantity,
                    targetPrediction.quantityUnit
                )
            }
            unsuitableCount == 0 -> "No weather-driven target reduction expected across the next 24 hours."
            else -> "$unsuitableCount of 24 forecasted hours are unsuitable for ${activity.shortName}; adjust shift scheduling accordingly."
        }

        val overallConfidence = when {
            warnings.any { it.severity == WeatherAlertSeverity.SEVERE } -> ForecastConfidence.MODERATE
            warnings.isEmpty() -> ForecastConfidence.HIGH
            else -> ForecastConfidence.MODERATE
        }

        return DelayRiskSummary(
            warnings = warnings,
            estimatedLostHoursMin = lostMin,
            estimatedLostHoursMax = lostMax,
            expectedDelayRangeLabel = delayLabel,
            nextSuitableWindowLabel = nextSuitable,
            dailyTargetImpactSummary = dailyImpact,
            overallConfidence = overallConfidence
        )
    }

    /**
     * Finds the longest contiguous suitable/caution working window in the forecast timeline.
     */
    fun findBestAlternativeWindow(
        hourlyTimeline: List<HourlyWorkWindow>,
        nightWorkAllowed: Boolean
    ): String? {
        if (hourlyTimeline.isEmpty()) return null
        var bestStart = -1
        var bestEnd = -1
        var curStart = -1

        for (i in hourlyTimeline.indices) {
            val w = hourlyTimeline[i]
            val shiftAllowed = nightWorkAllowed || w.hourPoint.isDay || w.hourPoint.localHourOfDay in 6..18
            val workable = shiftAllowed && (w.status == WorkSuitabilityStatus.SUITABLE || w.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION)
            if (workable) {
                if (curStart == -1) curStart = i
                if ((i - curStart) > (bestEnd - bestStart)) {
                    bestStart = curStart
                    bestEnd = i
                }
            } else {
                curStart = -1
            }
        }
        if (bestStart == -1 || bestEnd == -1) return null
        val spanHours = (bestEnd - bestStart) + 1
        return "${hourlyTimeline[bestStart].hourPoint.formatCompactHourAmPm()} – ${hourlyTimeline[bestEnd].hourPoint.formatCompactHourAmPm()} ($spanHours hr window)"
    }

    private fun formatWindowSpan(start: HourlyWeatherPoint, end: HourlyWeatherPoint): String {
        return if (start.epochMs == end.epochMs) {
            start.formatHourAmPm()
        } else {
            "${start.formatCompactHourAmPm()} – ${end.formatCompactHourAmPm()}"
        }
    }
}
