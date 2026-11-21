package com.example.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BitumenBinderType
import com.example.model.HighwayWorkActivity
import com.example.model.MoRTHRevision
import com.example.model.TemperatureUnit
import com.example.model.ValueSourceTag
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WorkTargetInput
import com.example.ui.theme.CtecsTheme
import java.util.Locale

/**
 * Modular Settings -> Weather & Highway Work Intelligence content covering all 9 subsections:
 * 1. Weather Display
 * 2. Hourly and Daily Forecast
 * 3. Weather Notifications
 * 4. Highway Work Activities
 * 5. BC / DBM Suitability
 * 6. Applicable IRC / MoRTH References
 * 7. Project Work Targets
 * 8. Production Rates
 * 9. Forecast and Data Preferences
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherIntelligenceSettingsSectionContent(
    settings: WeatherIntelligenceSettings,
    workTargetInput: WorkTargetInput,
    accentColor: Color,
    onUpdateSettings: ((WeatherIntelligenceSettings) -> WeatherIntelligenceSettings) -> Unit,
    onUpdateWorkTargetInput: (WorkTargetInput) -> Unit,
    onOpenWeatherDashboard: () -> Unit,
    onResetDefaults: () -> Unit
) {
    val c = CtecsTheme.colors
    var rateInputText by remember(settings.primaryActivity, settings.savedProductionRatesByActivity) {
        val currentRate = settings.savedProductionRatesByActivity[settings.primaryActivity]
            ?: workTargetInput.expectedProductionRatePerHour
        mutableStateOf(currentRate?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var ircRefText by remember(settings.contractSpec.ircReferences) {
        mutableStateOf(settings.contractSpec.ircReferences)
    }
    var amendmentText by remember(settings.contractSpec.contractAmendments) {
        mutableStateOf(settings.contractSpec.contractAmendments)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_intelligence_settings_content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick action to open dedicated Weather Dashboard
        Button(
            onClick = onOpenWeatherDashboard,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_weather_dashboard_button")
        ) {
            Text("Open Weather & Highway Intelligence Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // 1. Weather Display
        SettingsSubsectionHeader("1. Weather Display")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TemperatureUnit.entries.forEach { unit ->
                FilterChip(
                    selected = settings.temperatureUnit == unit,
                    onClick = { onUpdateSettings { it.copy(temperatureUnit = unit) } },
                    label = { Text(unit.label, fontSize = 11.sp) },
                    modifier = Modifier.testTag("settings_temp_unit_${unit.name.lowercase()}")
                )
            }
        }
        WeatherSettingSwitchRow(
            title = "Animated Weather Icons",
            subtitle = "Smooth condition-aware animations (falls back to static icons when OFF)",
            checked = settings.animationsEnabled,
            tag = "settings_weather_animations_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(animationsEnabled = enabled) } }
        )
        WeatherSettingSwitchRow(
            title = "Home Card Full-Day Hourly Strip",
            subtitle = "Show 12:00 AM–11:00 PM horizontal hourly forecast on Home Page",
            checked = settings.showHomeHourlyStrip,
            tag = "settings_home_hourly_strip_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(showHomeHourlyStrip = enabled) } }
        )
        WeatherSettingSwitchRow(
            title = "Rain Probability Chip on Home Card",
            subtitle = "Display live precipitation probability alongside humidity and wind",
            checked = settings.showRainProbabilityOnHomeCard,
            tag = "settings_home_rain_prob_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(showRainProbabilityOnHomeCard = enabled) } }
        )

        // 2. Hourly and Daily Forecast
        SettingsSubsectionHeader("2. Hourly and Daily Forecast")
        WeatherSettingSwitchRow(
            title = "Hourly Temperature Trend Chart",
            subtitle = "Show temperature curve above 24-hour forecast on Weather Dashboard",
            checked = settings.showHourlyTrendChart,
            tag = "settings_hourly_chart_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(showHourlyTrendChart = enabled) } }
        )
        WeatherSettingSwitchRow(
            title = "7-Day Forecast Uncertainty Indicators",
            subtitle = "Flag higher meteorological uncertainty on extended days 4–7",
            checked = settings.showSevenDayUncertaintyIndicators,
            tag = "settings_7day_uncertainty_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(showSevenDayUncertaintyIndicators = enabled) } }
        )

        // 3. Weather Notifications
        SettingsSubsectionHeader("3. Weather Notifications")
        WeatherSettingSwitchRow(
            title = "Enable Weather & Work Alerts",
            subtitle = "Deduplicated notifications for severe weather and restricted laying windows",
            checked = settings.weatherAlertsEnabled,
            tag = "settings_weather_alerts_master_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(weatherAlertsEnabled = enabled) } }
        )
        if (settings.weatherAlertsEnabled) {
            WeatherSettingSwitchRow(
                title = "Expected & Heavy Rain Alerts",
                subtitle = "Trigger when rain probability ≥ ${settings.alertRainProbabilityThresholdPercent}% or heavy rain arrives",
                checked = settings.alertExpectedRain && settings.alertHeavyRain,
                tag = "settings_alert_rain_switch",
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(alertExpectedRain = enabled, alertHeavyRain = enabled) }
                }
            )
            WeatherSettingSwitchRow(
                title = "Thunderstorm & Strong Wind Alerts",
                subtitle = "Alert for storms and winds ≥ ${settings.alertStrongWindThresholdKmh} km/h (MoRTH limit)",
                checked = settings.alertThunderstorms && settings.alertStrongWind,
                tag = "settings_alert_storm_wind_switch",
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(alertThunderstorms = enabled, alertStrongWind = enabled) }
                }
            )
            WeatherSettingSwitchRow(
                title = "Extreme Temperature & Fog Alerts",
                subtitle = "Warn on ambient ≥ ${settings.alertHighTempThresholdC}°C, ≤ ${settings.alertLowTempThresholdC}°C, or fog",
                checked = settings.alertExtremeTemps && settings.alertFogVisibility,
                tag = "settings_alert_temp_fog_switch",
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(alertExtremeTemps = enabled, alertFogVisibility = enabled) }
                }
            )
            WeatherSettingSwitchRow(
                title = "Unsuitable BC/DBM & Work Target Alerts",
                subtitle = "Notify when bituminous laying becomes restricted or daily targets drop",
                checked = settings.alertUnsuitableBituminousConditions && settings.alertWorkTargetChanges,
                tag = "settings_alert_bituminous_target_switch",
                onCheckedChange = { enabled ->
                    onUpdateSettings {
                        it.copy(
                            alertUnsuitableBituminousConditions = enabled,
                            alertWorkTargetChanges = enabled
                        )
                    }
                }
            )
        }

        // 4. Highway Work Activities
        SettingsSubsectionHeader("4. Highway Work Activities")
        Text(
            text = "Primary Highway Activity & Alert Filter:",
            color = c.textMuted,
            fontSize = 10.5.sp
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HighwayWorkActivity.entries.forEach { act ->
                FilterChip(
                    selected = settings.primaryActivity == act,
                    onClick = {
                        onUpdateSettings { it.copy(primaryActivity = act) }
                        onUpdateWorkTargetInput(workTargetInput.copy(activity = act))
                    },
                    label = { Text(act.shortName, fontSize = 10.sp) }
                )
            }
        }

        // 5. BC / DBM Suitability
        SettingsSubsectionHeader("5. BC / DBM Suitability")
        Text("BC Binder Grade (MoRTH Cl. 507.4.1 / 501.5.1):", color = c.textMuted, fontSize = 10.5.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                BitumenBinderType.CONVENTIONAL_VG30,
                BitumenBinderType.CONVENTIONAL_VG40,
                BitumenBinderType.MODIFIED_PMB,
                BitumenBinderType.MODIFIED_CRMB
            ).forEach { binder ->
                FilterChip(
                    selected = settings.contractSpec.bcBinderType == binder,
                    onClick = {
                        onUpdateSettings { it.copy(contractSpec = it.contractSpec.copy(bcBinderType = binder)) }
                    },
                    label = { Text(binder.displayName.substringBefore(" ("), fontSize = 10.sp) }
                )
            }
        }
        Text("DBM Binder Grade (MoRTH Cl. 505 / 501.5.1):", color = c.textMuted, fontSize = 10.5.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                BitumenBinderType.CONVENTIONAL_VG30,
                BitumenBinderType.CONVENTIONAL_VG40,
                BitumenBinderType.MODIFIED_PMB,
                BitumenBinderType.MODIFIED_CRMB
            ).forEach { binder ->
                FilterChip(
                    selected = settings.contractSpec.dbmBinderType == binder,
                    onClick = {
                        onUpdateSettings { it.copy(contractSpec = it.contractSpec.copy(dbmBinderType = binder)) }
                    },
                    label = { Text(binder.displayName.substringBefore(" ("), fontSize = 10.sp) }
                )
            }
        }

        // 6. Applicable IRC / MoRTH References
        SettingsSubsectionHeader("6. Applicable IRC / MoRTH References")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MoRTHRevision.entries.forEach { rev ->
                FilterChip(
                    selected = settings.contractSpec.morthRevision == rev,
                    onClick = {
                        onUpdateSettings { it.copy(contractSpec = it.contractSpec.copy(morthRevision = rev)) }
                    },
                    label = { Text(rev.shortLabel, fontSize = 10.5.sp) },
                    modifier = Modifier.testTag("settings_morth_rev_${rev.name.lowercase()}")
                )
            }
        }
        OutlinedTextField(
            value = ircRefText,
            onValueChange = { txt ->
                ircRefText = txt
                onUpdateSettings { it.copy(contractSpec = it.contractSpec.copy(ircReferences = txt)) }
            },
            label = { Text("IRC Specification References", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amendmentText,
            onValueChange = { txt ->
                amendmentText = txt
                onUpdateSettings { it.copy(contractSpec = it.contractSpec.copy(contractAmendments = txt)) }
            },
            label = { Text("Contract Amendments / Engineer Weather Notes", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 7 & 8. Project Work Targets & Production Rates
        SettingsSubsectionHeader("7 & 8. Project Work Targets & Production Rates")
        OutlinedTextField(
            value = rateInputText,
            onValueChange = { txt ->
                rateInputText = txt
                val parsed = txt.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    onUpdateSettings { s ->
                        val updatedMap = s.savedProductionRatesByActivity.toMutableMap().apply {
                            put(s.primaryActivity, parsed)
                        }
                        s.copy(savedProductionRatesByActivity = updatedMap)
                    }
                    onUpdateWorkTargetInput(workTargetInput.copy(expectedProductionRatePerHour = parsed))
                }
            },
            label = { Text("Saved Rate for ${settings.primaryActivity.shortName} (${settings.primaryActivity.productionUnitLabel})", fontSize = 10.5.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_saved_production_rate_input")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueSourceTag.entries.forEach { tag ->
                FilterChip(
                    selected = settings.defaultProductionRateSource == tag,
                    onClick = {
                        onUpdateSettings { it.copy(defaultProductionRateSource = tag) }
                        onUpdateWorkTargetInput(workTargetInput.copy(productionRateSource = tag))
                    },
                    label = { Text(tag.label, fontSize = 10.sp) }
                )
            }
        }

        // 9. Forecast and Data Preferences
        SettingsSubsectionHeader("9. Forecast and Data Preferences")
        Text(
            text = "Auto-Refresh Interval: ${settings.autoRefreshIntervalMinutes} min • Stale Threshold: ${settings.staleDataThresholdMinutes} min",
            color = c.textMuted,
            fontSize = 10.5.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 15, 30, 60).forEach { mins ->
                FilterChip(
                    selected = settings.autoRefreshIntervalMinutes == mins,
                    onClick = { onUpdateSettings { it.copy(autoRefreshIntervalMinutes = mins) } },
                    label = { Text("${mins}m", fontSize = 10.5.sp) }
                )
            }
        }
        WeatherSettingSwitchRow(
            title = "Use Offline Cached Forecast When Disconnected",
            subtitle = "Keep last persisted 24h & 7-day forecast accessible offline with stale badge",
            checked = settings.preferCachedForecastOffline,
            tag = "settings_offline_forecast_switch",
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(preferCachedForecastOffline = enabled) } }
        )

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onResetDefaults,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_weather_intelligence_reset")
        ) {
            Text("Reset Weather & Highway Intelligence Defaults", fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun SettingsSubsectionHeader(title: String) {
    val c = CtecsTheme.colors
    Text(
        text = title.uppercase(Locale.US),
        color = c.accent,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.secondarySurface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun WeatherSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = c.textMuted,
                fontSize = 10.5.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
