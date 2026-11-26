package com.example.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BituminousMaterialConfig
import com.example.model.BituminousWorkCategory
import com.example.model.HighwayWorkActivity
import com.example.model.OtherBituminousWorkType
import com.example.model.TemperatureUnit
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WindSpeedUnit
import com.example.ui.theme.CtecsTheme

/**
 * Contextual animated Settings popup exclusively for the WEATHER page.
 * Contains ONLY Weather Display, Bituminous Material Configuration, Activity Prediction,
 * Weather Alerts, and Weather Data controls.
 */
@Composable
fun WeatherSettingsPopup(
    visible: Boolean,
    settings: WeatherIntelligenceSettings,
    lastUpdatedLabel: String,
    isOfflineCached: Boolean,
    onUpdateSettings: ((WeatherIntelligenceSettings) -> WeatherIntelligenceSettings) -> Unit,
    onManualRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val c = CtecsTheme.colors
    val animMs = if (settings.animationsEnabled) 180 else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(animMs)) + scaleIn(tween(animMs), initialScale = 0.95f),
                exit = fadeOut(tween(animMs)) + scaleOut(tween(animMs), targetScale = 0.95f)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = c.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 660.dp)
                        .testTag("weather_settings_popup")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, tint = c.accent, modifier = Modifier.size(19.dp))
                                Column {
                                    Text("Weather Settings", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Bituminous material, display, alerts & data", color = c.textMuted, fontSize = 11.sp)
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp).testTag("weather_settings_close")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close weather settings", tint = c.textSecondary)
                            }
                        }

                        HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 1. Bituminous Material Configuration (Dynamic Work Category & Binder Grade)
                            BituminousMaterialConfigSection(
                                config = settings.bituminousConfig,
                                animationsEnabled = settings.animationsEnabled,
                                onConfigChange = { updatedCfg ->
                                    onUpdateSettings { current ->
                                        applyBituminousConfigUpdate(current, updatedCfg)
                                    }
                                },
                                onResetDefault = {
                                    onUpdateSettings { current ->
                                        applyBituminousConfigUpdate(current, BituminousMaterialConfig())
                                    }
                                }
                            )

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 2. Weather Display
                            PopupSectionHeader("WEATHER DISPLAY")
                            SegmentedOptionRow(
                                label = "Temperature Unit",
                                options = TemperatureUnit.entries.map { it to it.symbol },
                                selected = settings.temperatureUnit,
                                tagPrefix = "ws_temp_unit",
                                onSelect = { unit -> onUpdateSettings { it.copy(temperatureUnit = unit) } }
                            )
                            SegmentedOptionRow(
                                label = "Wind Speed Unit",
                                options = WindSpeedUnit.entries.map { it to it.symbol },
                                selected = settings.windSpeedUnit,
                                tagPrefix = "ws_wind_unit",
                                onSelect = { unit -> onUpdateSettings { it.copy(windSpeedUnit = unit) } }
                            )
                            SegmentedOptionRow(
                                label = "Forecast Layout",
                                options = listOf(true to "Compact", false to "Detailed"),
                                selected = settings.compactForecastLayout,
                                tagPrefix = "ws_layout",
                                onSelect = { compact -> onUpdateSettings { it.copy(compactForecastLayout = compact) } }
                            )
                            PopupSwitchRow(
                                title = "Hourly Forecast",
                                checked = settings.showHourlyForecastSection,
                                tag = "ws_toggle_hourly",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showHourlyForecastSection = v) } }
                            )
                            PopupSwitchRow(
                                title = "Daily Forecast",
                                checked = settings.showDailyForecastSection,
                                tag = "ws_toggle_daily",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showDailyForecastSection = v) } }
                            )
                            if (settings.showDailyForecastSection) {
                                SegmentedOptionRow(
                                    label = "Forecast Days",
                                    options = listOf(3 to "3 Days", 5 to "5 Days", 7 to "7 Days"),
                                    selected = settings.forecastDaysCount,
                                    tagPrefix = "ws_days",
                                    onSelect = { d -> onUpdateSettings { it.copy(forecastDaysCount = d) } }
                                )
                            }

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 3. Activity Prediction
                            PopupSectionHeader("ACTIVITY PREDICTION")
                            PopupSwitchRow(
                                title = "Enable Activity Prediction",
                                checked = settings.activityPredictionEnabled,
                                tag = "ws_toggle_activity_pred",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(activityPredictionEnabled = v) } }
                            )
                            if (settings.activityPredictionEnabled) {
                                SegmentedOptionRow(
                                    label = "Default Activity",
                                    options = listOf(
                                        HighwayWorkActivity.BC_LAYING to "BC",
                                        HighwayWorkActivity.DBM_LAYING to "DBM",
                                        HighwayWorkActivity.TACK_COAT_APPLICATION to "Tack",
                                        HighwayWorkActivity.CONCRETE_WORKS to "PQC"
                                    ),
                                    selected = settings.primaryActivity,
                                    tagPrefix = "ws_default_act",
                                    onSelect = { act ->
                                        onUpdateSettings { current ->
                                            val syncedBituminous = when (act) {
                                                HighwayWorkActivity.BC_LAYING -> current.bituminousConfig.withCategoryChanged(BituminousWorkCategory.BC)
                                                HighwayWorkActivity.DBM_LAYING -> current.bituminousConfig.withCategoryChanged(BituminousWorkCategory.DBM)
                                                HighwayWorkActivity.TACK_COAT_APPLICATION -> current.bituminousConfig.withOtherWorkTypeChanged(OtherBituminousWorkType.TACK_COAT)
                                                else -> current.bituminousConfig.copy(isCustomCategoryActive = false)
                                            }
                                            current.copy(
                                                primaryActivity = act,
                                                bituminousConfig = syncedBituminous
                                            )
                                        }
                                    }
                                )
                                SegmentedOptionRow(
                                    label = "Prediction Interval",
                                    options = listOf(1 to "1 hr", 2 to "2 hr", 3 to "3 hr"),
                                    selected = settings.predictionIntervalHours,
                                    tagPrefix = "ws_interval",
                                    onSelect = { hrs -> onUpdateSettings { it.copy(predictionIntervalHours = hrs) } }
                                )
                                PopupSwitchRow(
                                    title = "Suitability Indicators",
                                    checked = settings.showSuitabilityIndicators,
                                    tag = "ws_toggle_suitability",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(showSuitabilityIndicators = v) } }
                                )
                                PopupSwitchRow(
                                    title = "Best Working Window",
                                    checked = settings.showBestWorkingWindow,
                                    tag = "ws_toggle_best_window",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(showBestWorkingWindow = v) } }
                                )
                            }

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 4. Weather Alerts
                            PopupSectionHeader("WEATHER ALERTS")
                            PopupSwitchRow(
                                title = "Rain Alerts",
                                checked = settings.alertExpectedRain,
                                tag = "ws_alert_rain",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(alertExpectedRain = v, alertHeavyRain = v) } }
                            )
                            PopupSwitchRow(
                                title = "Wind Alerts",
                                checked = settings.alertStrongWind,
                                tag = "ws_alert_wind",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(alertStrongWind = v) } }
                            )
                            PopupSwitchRow(
                                title = "Fog & Visibility Alerts",
                                checked = settings.alertFogVisibility,
                                tag = "ws_alert_fog",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(alertFogVisibility = v) } }
                            )
                            PopupSwitchRow(
                                title = "Working-Window Changes",
                                checked = settings.alertWorkTargetChanges,
                                tag = "ws_alert_window",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(alertWorkTargetChanges = v) } }
                            )

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 4. Weather Data
                            PopupSectionHeader("WEATHER DATA")
                            PopupSwitchRow(
                                title = "Automatic Refresh",
                                checked = settings.autoRefreshEnabled,
                                tag = "ws_auto_refresh",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(autoRefreshEnabled = v) } }
                            )
                            SegmentedOptionRow(
                                label = "Data Freshness Window",
                                options = listOf(30 to "30m", 60 to "60m", 120 to "2h"),
                                selected = settings.staleDataThresholdMinutes,
                                tagPrefix = "ws_freshness",
                                onSelect = { m -> onUpdateSettings { it.copy(staleDataThresholdMinutes = m) } }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.secondarySurface)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isOfflineCached) "Status: Offline (Cached)" else "Status: Live Forecast",
                                        color = if (isOfflineCached) c.warning else c.success,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Last updated: ${lastUpdatedLabel.ifBlank { "Unavailable" }}",
                                        color = c.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = onManualRefresh,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                    modifier = Modifier.testTag("ws_manual_refresh_btn")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Refresh", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PopupSectionHeader(title: String) {
    val c = CtecsTheme.colors
    Text(
        text = title,
        color = c.accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
internal fun PopupSwitchRow(
    title: String,
    checked: Boolean,
    tag: String,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.textMuted,
                uncheckedTrackColor = c.secondarySurface
            )
        )
    }
}

@Composable
internal fun <T> SegmentedOptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    tagPrefix: String,
    onSelect: (T) -> Unit
) {
    val c = CtecsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { idx, (value, text) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) c.accent else c.secondarySurface)
                        .clickable { onSelect(value) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("${tagPrefix}_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = if (isSelected) c.onAccent else c.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
