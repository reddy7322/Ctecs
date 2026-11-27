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
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Pure Weather-Based Delay & Risk Analyzer for highway construction activities.
 * Evaluates live hourly weather timelines for storms, rainfall, post-rain drying,
 * wind, temperature extremes, and fog without any activity targets or quotas.
 */
object WeatherDelayRiskAnalyzer {

    /**
     * Generates specific Weather-Based Delay & Risk warnings, probability ranges,
     * lost-hour bounds, and next suitable weather window from the live hourly timeline.
     */
    fun analyzeDelayRisks(
        activity: HighwayWorkActivity,
        hourlyTimeline: List<HourlyWorkWindow>
    ): DelayRiskSummary {
        if (hourlyTimeline.isEmpty()) {
            return DelayRiskSummary(
                warnings = emptyList(),
                estimatedLostHoursMin = 0.0,
                estimatedLostHoursMax = 0.0,
                expectedDelayRangeLabel = "Insufficient forecast data",
                nextSuitableWindowLabel = null,
                weatherImpactSummary = "Refresh live forecast to compute weather delay risks.",
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
        val weatherImpact = if (unsuitableCount == 0) {
            "No weather-driven interruptions expected for ${activity.shortName} across the next 24 hours."
        } else {
            "$unsuitableCount of 24 forecasted hours are unsuitable for ${activity.shortName}; review hourly weather suitability before field execution."
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
            weatherImpactSummary = weatherImpact,
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
