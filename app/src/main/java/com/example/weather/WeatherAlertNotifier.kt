package com.example.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.model.ActivitySuitabilityAssessment
import com.example.model.HighwayWorkActivity
import com.example.model.WeatherAlertItem
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetPrediction
import java.util.Locale

/**
 * Deduplicated, activity-aware Weather & Highway Construction Alert Engine.
 * Evaluates live forecast conditions against user alert preferences and dispatches
 * Android notifications safely without spamming duplicate alerts.
 */
class WeatherAlertNotifier {

    private val dispatchedAlertSignatures = LinkedHashMap<String, Long>()

    /**
     * Pure evaluator returning filtered actionable alerts for UI banners and notifications.
     */
    fun evaluateActionableAlerts(
        bundle: WeatherForecastBundle?,
        settings: WeatherIntelligenceSettings,
        assessments: List<ActivitySuitabilityAssessment>,
        targetPrediction: WorkTargetPrediction?,
        nowMs: Long = System.currentTimeMillis()
    ): List<WeatherAlertItem> {
        if (!settings.weatherAlertsEnabled || bundle == null) return emptyList()
        val alerts = mutableListOf<WeatherAlertItem>()
        val next12 = bundle.next24Hours(nowMs).take(12)

        // 1. Thunderstorms
        if (settings.alertThunderstorms) {
            val storm = next12.firstOrNull { it.condition == WeatherConditionType.THUNDERSTORM }
            if (storm != null) {
                alerts += WeatherAlertItem(
                    id = "alert_storm_${storm.epochMs / 3_600_000L}",
                    title = "Thunderstorm Alert (${storm.formatCompactHourAmPm()})",
                    description = "Thunderstorm forecasted near ${bundle.current.locationName.ifBlank { "site" }}. Pause exposed highway works.",
                    severity = WeatherAlertSeverity.SEVERE,
                    startEpochMs = storm.epochMs,
                    endEpochMs = storm.epochMs + 3_600_000L,
                    category = "THUNDERSTORM"
                )
            }
        }

        // 2. Heavy rain / Expected rain
        val heavyRain = next12.firstOrNull { it.precipitationMm >= 7.5 || it.wmoCode in listOf(65, 82) }
        if (settings.alertHeavyRain && heavyRain != null) {
            alerts += WeatherAlertItem(
                id = "alert_heavy_rain_${heavyRain.epochMs / 3_600_000L}",
                title = "Heavy Rain Expected (${heavyRain.formatCompactHourAmPm()})",
                description = String.format(Locale.US, "%.1f mm/hr rain forecasted. Protect prepared surfaces and tack coat.", heavyRain.precipitationMm),
                severity = WeatherAlertSeverity.SEVERE,
                startEpochMs = heavyRain.epochMs,
                endEpochMs = heavyRain.epochMs + 3_600_000L,
                category = "HEAVY_RAIN"
            )
        } else if (settings.alertExpectedRain) {
            val rain = next12.firstOrNull { it.rainProbabilityPercent >= settings.alertRainProbabilityThresholdPercent || it.precipitationMm >= 0.3 }
            if (rain != null) {
                alerts += WeatherAlertItem(
                    id = "alert_rain_${rain.epochMs / 3_600_000L}",
                    title = "Rain Expected (${rain.rainProbabilityPercent}% at ${rain.formatCompactHourAmPm()})",
                    description = "Rainfall probability reaches ${rain.rainProbabilityPercent}%. Review active bituminous & marking schedules.",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = rain.epochMs,
                    endEpochMs = rain.epochMs + 3_600_000L,
                    category = "RAIN"
                )
            }
        }

        // 3. Strong wind
        if (settings.alertStrongWind) {
            val windy = next12.firstOrNull { it.windSpeedKmh >= settings.alertStrongWindThresholdKmh }
            if (windy != null) {
                alerts += WeatherAlertItem(
                    id = "alert_wind_${windy.epochMs / 3_600_000L}",
                    title = "Strong Wind Alert (${windy.windSpeedKmh.toInt()} km/h)",
                    description = "Wind expected to reach ${windy.windSpeedKmh.toInt()} km/h at ${windy.formatCompactHourAmPm()} (threshold ${settings.alertStrongWindThresholdKmh} km/h).",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = windy.epochMs,
                    endEpochMs = windy.epochMs + 3_600_000L,
                    category = "STRONG_WIND"
                )
            }
        }

        // 4. Extreme temperatures
        if (settings.alertExtremeTemps) {
            val hot = next12.firstOrNull { it.temperatureC >= settings.alertHighTempThresholdC }
            if (hot != null) {
                alerts += WeatherAlertItem(
                    id = "alert_high_temp_${hot.epochMs / 10_800_000L}",
                    title = "High Temperature Alert (${hot.temperatureC.toInt()}°C)",
                    description = "Ambient temperature expected to reach ${hot.temperatureC.toInt()}°C at ${hot.formatCompactHourAmPm()}. Apply hot-weather concreting & crew safety precautions.",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = hot.epochMs,
                    endEpochMs = hot.epochMs + 3_600_000L,
                    category = "EXTREME_HEAT"
                )
            }
            val cold = next12.firstOrNull { it.temperatureC <= settings.alertLowTempThresholdC }
            if (cold != null) {
                alerts += WeatherAlertItem(
                    id = "alert_low_temp_${cold.epochMs / 10_800_000L}",
                    title = "Low Temperature Alert (${cold.temperatureC.toInt()}°C)",
                    description = "Ambient temperature drops to ${cold.temperatureC.toInt()}°C at ${cold.formatCompactHourAmPm()}. Verify actual pavement surface temp before bituminous laying.",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = cold.epochMs,
                    endEpochMs = cold.epochMs + 3_600_000L,
                    category = "LOW_TEMP"
                )
            }
        }

        // 5. Fog / low visibility
        if (settings.alertFogVisibility) {
            val fog = next12.firstOrNull { it.condition == WeatherConditionType.FOGGY || (it.visibilityKm != null && it.visibilityKm < 1.0) }
            if (fog != null) {
                alerts += WeatherAlertItem(
                    id = "alert_fog_${fog.epochMs / 3_600_000L}",
                    title = "Fog / Low Visibility (${fog.formatCompactHourAmPm()})",
                    description = "Fog forecasted — bituminous laying restricted under MoRTH Cl. 501.5.1 and highway traffic caution required.",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = fog.epochMs,
                    endEpochMs = fog.epochMs + 3_600_000L,
                    category = "FOG"
                )
            }
        }

        // 6. Activity-filtered unsuitable BC/DBM conditions
        if (settings.alertUnsuitableBituminousConditions) {
            val monitoredBituminous = assessments.filter {
                it.activity in setOf(HighwayWorkActivity.BC_LAYING, HighwayWorkActivity.DBM_LAYING) &&
                    it.activity in settings.enabledAlertActivities &&
                    it.status == WorkSuitabilityStatus.NOT_RECOMMENDED
            }
            if (monitoredBituminous.isNotEmpty()) {
                val names = monitoredBituminous.joinToString(" / ") { it.activity.shortName }
                alerts += WeatherAlertItem(
                    id = "alert_bituminous_${names}_${nowMs / 3_600_000L}",
                    title = "$names Laying Not Recommended",
                    description = monitoredBituminous.first().summaryReason,
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = nowMs,
                    endEpochMs = nowMs + 3_600_000L,
                    category = "BITUMINOUS_RESTRICTION"
                )
            }
        }

        // 7. Planned work target change alert
        if (settings.alertWorkTargetChanges &&
            targetPrediction != null &&
            targetPrediction.isCalculationComplete &&
            targetPrediction.activity in settings.enabledAlertActivities &&
            targetPrediction.expectedCompletionPercent < 80.0 &&
            targetPrediction.weatherInterruptionHours > 0.0
        ) {
            alerts += WeatherAlertItem(
                id = "alert_target_${targetPrediction.activity.name}_${nowMs / 3_600_000L}",
                title = "${targetPrediction.activity.shortName} Target Reduced (${targetPrediction.expectedCompletionPercent.toInt()}%)",
                description = String.format(
                    Locale.US,
                    "Weather interruptions (%.1f hr) reduced estimated daily target to %.1f %s.",
                    targetPrediction.weatherInterruptionHours,
                    targetPrediction.weatherAdjustedTargetQuantity,
                    targetPrediction.quantityUnit
                ),
                severity = WeatherAlertSeverity.ADVISORY,
                startEpochMs = nowMs,
                endEpochMs = nowMs + 3_600_000L,
                category = "WORK_TARGET"
            )
        }

        return alerts
    }

    /**
     * Filters out alerts that were already dispatched within [dedupWindowMs] (default 90 minutes).
     */
    fun filterNewAlerts(
        alerts: List<WeatherAlertItem>,
        nowMs: Long = System.currentTimeMillis(),
        dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS
    ): List<WeatherAlertItem> {
        val iterator = dispatchedAlertSignatures.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value > dedupWindowMs) {
                iterator.remove()
            }
        }
        val fresh = mutableListOf<WeatherAlertItem>()
        for (alert in alerts) {
            val sig = "${alert.category}:${alert.id}"
            if (!dispatchedAlertSignatures.containsKey(sig)) {
                dispatchedAlertSignatures[sig] = nowMs
                fresh += alert
            }
        }
        return fresh
    }

    /**
     * Safely posts deduplicated Android notifications if POST_NOTIFICATIONS is granted.
     */
    fun dispatchSystemNotifications(
        context: Context,
        alerts: List<WeatherAlertItem>,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        if (alerts.isEmpty()) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return 0
        }
        val fresh = filterNewAlerts(alerts, nowMs)
        if (fresh.isEmpty()) return 0

        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        var posted = 0
        for (alert in fresh.take(3)) {
            try {
                val priority = when (alert.severity) {
                    WeatherAlertSeverity.SEVERE -> NotificationCompat.PRIORITY_HIGH
                    WeatherAlertSeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
                    WeatherAlertSeverity.ADVISORY -> NotificationCompat.PRIORITY_LOW
                }
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(alert.title)
                    .setContentText(alert.description)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(alert.description))
                    .setPriority(priority)
                    .setAutoCancel(true)
                    .build()
                manager.notify(NOTIFICATION_ID_BASE + (alert.category.hashCode() and 0x3FF), notification)
                posted++
            } catch (_: SecurityException) {
                // Gracefully skip if notification permission revoked
            } catch (_: Exception) {
                // Never crash on notification subsystem issues
            }
        }
        return posted
    }

    fun clearDedupCacheForTesting() {
        dispatchedAlertSignatures.clear()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Weather & Highway Work Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Live highway weather warnings, bituminous laying restrictions, and target change alerts."
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ctecs_weather_highway_alerts"
        private const val NOTIFICATION_ID_BASE = 7400
        const val DEFAULT_DEDUP_WINDOW_MS = 90L * 60L * 1000L // 90 minutes
    }
}
