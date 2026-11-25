package com.example.ui.weather

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActivitySuitabilityAssessment
import com.example.model.ContractSpecificationConfig
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWorkWindow
import com.example.model.TemperatureUnit
import com.example.model.WorkSuitabilityStatus
import com.example.ui.home.AnimatedWeatherIcon
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.weather.IrcMorthWeatherRuleEngine
import java.util.Locale

/**
 * Curated display order for the Smart Activity Selector matching highway field workflows.
 */
internal val SMART_PREDICTION_ACTIVITIES: List<HighwayWorkActivity> = listOf(
    HighwayWorkActivity.BC_LAYING,
    HighwayWorkActivity.DBM_LAYING,
    HighwayWorkActivity.TACK_COAT_APPLICATION,
    HighwayWorkActivity.PRIME_COAT_APPLICATION,
    HighwayWorkActivity.MILLING_OPERATIONS,
    HighwayWorkActivity.GLASS_GRID_APPLICATION,
    HighwayWorkActivity.CRACK_SEALING,
    HighwayWorkActivity.GSB_OPERATIONS,
    HighwayWorkActivity.WMM_OPERATIONS,
    HighwayWorkActivity.EARTHWORK,
    HighwayWorkActivity.CONCRETE_WORKS,
    HighwayWorkActivity.ROAD_MARKING,
    HighwayWorkActivity.SURFACE_PREPARATION_CLEANING,
    HighwayWorkActivity.GENERAL_HIGHWAY_MAINTENANCE
)

/**
 * Concise, engineer-friendly display name for chips and hourly prediction cards.
 */
internal fun smartActivityLabel(activity: HighwayWorkActivity): String = when (activity) {
    HighwayWorkActivity.BC_LAYING -> "BC Laying"
    HighwayWorkActivity.DBM_LAYING -> "DBM Laying"
    HighwayWorkActivity.TACK_COAT_APPLICATION -> "Tack Coat"
    HighwayWorkActivity.PRIME_COAT_APPLICATION -> "Prime Coat"
    HighwayWorkActivity.MILLING_OPERATIONS -> "Milling"
    HighwayWorkActivity.GLASS_GRID_APPLICATION -> "Glass Grid"
    HighwayWorkActivity.CRACK_SEALING -> "Crack Sealing"
    HighwayWorkActivity.GSB_OPERATIONS -> "GSB"
    HighwayWorkActivity.WMM_OPERATIONS -> "WMM"
    HighwayWorkActivity.EARTHWORK -> "Earthwork"
    HighwayWorkActivity.CONCRETE_WORKS -> "PQC / DLC"
    HighwayWorkActivity.ROAD_MARKING -> "Road Marking"
    HighwayWorkActivity.SURFACE_PREPARATION_CLEANING -> "Surface Prep"
    HighwayWorkActivity.GENERAL_HIGHWAY_MAINTENANCE -> "Maintenance"
}

internal fun formatHourlyTimeLabel(localHourOfDay: Int, is24HourFormat: Boolean): String {
    val h = ((localHourOfDay % 24) + 24) % 24
    return if (is24HourFormat) {
        String.format(Locale.US, "%02d:00", h)
    } else {
        when (h) {
            0 -> "12 AM"
            in 1..11 -> "$h AM"
            12 -> "12 PM"
            else -> "${h - 12} PM"
        }
    }
}

/**
 * Smart Hourly Highway Activity Prediction section placed immediately below the Main Weather Card.
 *
 * Includes:
 * 1. Compact horizontally scrollable Smart Activity Selector (BC Laying default, DBM Laying, Tack Coat,
 *    Prime Coat, Milling, Glass Grid, Crack Sealing, GSB, WMM, Earthwork, PQC / DLC, Road Marking, etc.)
 * 2. Best Available Working Window summary bar (handles suitable, caution, no suitable window, incomplete data)
 * 3. Horizontally scrollable Hourly Prediction Cards (next 12–24 hours) showing:
 *    - Hour & time
 *    - Selected activity
 *    - Suitability status badge (Suitable / Caution / Not Recommended / Insufficient Data)
 *    - Weather indicators (icon, condition, temperature, rain %, wind km/h)
 *    - Short MoRTH/IRC-based engineering reason directly below the weather indicators
 * 4. Clear cached/stale timestamp badge when live data is unavailable, never fabricating predictions
 * 5. Concise advisory disclaimer (never replaces site measurements or Engineer approval)
 */
@Composable
fun SmartHourlyHighwayPredictionSection(
    selectedActivity: HighwayWorkActivity,
    assessments: List<ActivitySuitabilityAssessment>,
    hourlyWorkTimeline: List<HourlyWorkWindow>,
    contractSpec: ContractSpecificationConfig,
    temperatureUnit: TemperatureUnit,
    is24HourFormat: Boolean,
    isDark: Boolean,
    shouldAnimate: Boolean,
    isCachedForecast: Boolean,
    isStaleForecast: Boolean,
    lastUpdatedLabel: String,
    onSelectActivity: (HighwayWorkActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val activityLabel = smartActivityLabel(selectedActivity)
    val bestWindow = remember(selectedActivity, hourlyWorkTimeline, is24HourFormat) {
        IrcMorthWeatherRuleEngine.summarizeBestWorkingWindow(
            activity = selectedActivity,
            timeline = hourlyWorkTimeline,
            is24HourFormat = is24HourFormat,
            activityDisplayLabel = activityLabel
        )
    }
    val binderHint = remember(selectedActivity, contractSpec) {
        if (selectedActivity.isBituminousHotMix) {
            val binder = contractSpec.binderFor(selectedActivity)
            if (binder.isModified) "${binder.displayName.substringBefore(" ")} · ≥15°C"
            else "${binder.displayName.substringBefore(" ")} · ≥10°C"
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(vertical = 12.dp)
            .testTag("highway_work_suitability_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header + Optional Binder / Cached-Stale Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "HIGHWAY WORK PREDICTION",
                    color = c.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = activityLabel,
                    color = c.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("weather_suitability_activity_name")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (binderHint != null) {
                    Text(
                        text = binderHint,
                        color = c.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.secondarySurface)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("weather_prediction_binder_badge")
                    )
                }
                if (isCachedForecast || isStaleForecast) {
                    val badgeLabel = buildString {
                        append(if (isCachedForecast) "Cached" else "Stale")
                        if (lastUpdatedLabel.isNotBlank()) {
                            append(" · ")
                            append(lastUpdatedLabel)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.warning.copy(alpha = if (isDark) 0.16f else 0.12f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag("hourly_prediction_cache_badge")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Cached forecast timestamp",
                            tint = c.warning,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = badgeLabel,
                            color = c.warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Smart Horizontally Scrollable Activity Selector
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("smart_activity_selector_row")
        ) {
            items(SMART_PREDICTION_ACTIVITIES, key = { it.name }) { activity ->
                val isSelected = activity == selectedActivity
                val itemStatus = assessments.firstOrNull { it.activity == activity }?.status
                val dotColor = itemStatus?.let { suitabilityColor(it) } ?: c.textMuted
                val chipBg = if (isSelected) {
                    c.accent.copy(alpha = if (isDark) 0.22f else 0.14f)
                } else {
                    c.secondarySurface
                }
                val chipBorder = if (isSelected) c.accent.copy(alpha = 0.65f) else Color.Transparent

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelectActivity(activity) }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                        .testTag("activity_chip_${activity.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = smartActivityLabel(activity),
                        color = if (isSelected) c.accent else c.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Compact Best Available Working Window Recommendation
        val windowAccent = when (bestWindow.state) {
            IrcMorthWeatherRuleEngine.BestWindowState.SUITABLE_WINDOW -> c.success
            IrcMorthWeatherRuleEngine.BestWindowState.CAUTION_WINDOW -> c.warning
            IrcMorthWeatherRuleEngine.BestWindowState.NO_SUITABLE_WINDOW -> c.error
            IrcMorthWeatherRuleEngine.BestWindowState.INCOMPLETE_DATA -> c.textMuted
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(windowAccent.copy(alpha = if (isDark) 0.13f else 0.08f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {}
                .testTag("weather_best_working_window"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = "Best working window",
                tint = windowAccent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = bestWindow.summaryText,
                color = c.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Hourly Activity Prediction Cards (Next 12–24 Hours) or Clean Unavailable State
        if (hourlyWorkTimeline.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.secondarySurface)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("hourly_prediction_unavailable"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = "Forecast unavailable",
                    tint = c.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Hourly forecast unavailable. Predictions are not generated without weather data — verify site conditions.",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        } else {
            val displayTimeline = remember(hourlyWorkTimeline) {
                hourlyWorkTimeline.take(24)
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart_hourly_prediction_row")
            ) {
                itemsIndexed(
                    items = displayTimeline,
                    key = { _, item -> "${item.activity.name}_${item.hourPoint.epochMs}" }
                ) { index, window ->
                    SmartHourlyPredictionCard(
                        index = index,
                        window = window,
                        activityLabel = activityLabel,
                        temperatureUnit = temperatureUnit,
                        is24HourFormat = is24HourFormat,
                        isDark = isDark,
                        shouldAnimate = shouldAnimate && index == 0
                    )
                }
            }
        }

        // Concise Advisory Disclaimer
        Text(
            text = "Advisory only (MoRTH / IRC guidance) — verify dry surface, pavement temperature & Engineer approval at site.",
            color = c.textMuted,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .testTag("weather_engineer_disclaimer")
        )
    }
}

@Composable
private fun SmartHourlyPredictionCard(
    index: Int,
    window: HourlyWorkWindow,
    activityLabel: String,
    temperatureUnit: TemperatureUnit,
    is24HourFormat: Boolean,
    isDark: Boolean,
    shouldAnimate: Boolean
) {
    val c = CtecsTheme.colors
    val pt = window.hourPoint
    val statusColor = suitabilityColor(window.status)
    val statusLabel = suitabilityStatusLabel(window.status)
    val rainTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.secondarySurface,
        border = BorderStroke(
            width = 1.dp,
            color = statusColor.copy(alpha = if (isDark) 0.34f else 0.28f)
        ),
        modifier = Modifier
            .width(184.dp)
            .testTag("hourly_prediction_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Row 1: Hour/Time + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatHourlyTimeLabel(pt.localHourOfDay, is24HourFormat),
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.testTag("hourly_prediction_time_$index")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        .semantics(mergeDescendants = true) {}
                        .testTag("hourly_prediction_status_$index")
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Row 2: Selected Activity Name
            Text(
                text = activityLabel,
                color = c.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hourly_prediction_activity_$index")
            )

            // Row 3: Weather Icon + Condition + Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    AnimatedWeatherIcon(
                        condition = pt.visualCondition,
                        isDark = isDark,
                        isAnimating = shouldAnimate,
                        isHeavyRain = pt.isHeavyRain,
                        size = 20.dp
                    )
                    Text(
                        text = pt.conditionLabel,
                        color = c.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (pt.temperatureC.isFinite()) temperatureUnit.format(pt.temperatureC) else "—",
                    color = c.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.testTag("hourly_prediction_temp_$index")
                )
            }

            // Row 4: Compact Weather Indicators (Rain % & Wind km/h)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = "Rain probability",
                        tint = if (pt.rainProbabilityPercent >= 35) rainTint else c.textMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (pt.rainProbabilityPercent in 0..100) "${pt.rainProbabilityPercent}%" else "—",
                        color = if (pt.rainProbabilityPercent >= 35) rainTint else c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Air,
                        contentDescription = "Wind speed",
                        tint = if (pt.windSpeedKmh > 35.0) c.warning else c.textMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (pt.windSpeedKmh.isFinite() && pt.windSpeedKmh >= 0.0) {
                            "${pt.windSpeedKmh.toInt()} km/h"
                        } else {
                            "—"
                        },
                        color = if (pt.windSpeedKmh > 35.0) c.warning else c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            SmartFadeDivider(
                color = if (isDark) c.elevatedSurface else c.subtleSeparator
            )

            // Row 5: Concise MoRTH / IRC Engineering Reason directly inside the card
            Text(
                text = window.primaryReason,
                color = c.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.5.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("hourly_prediction_reason_$index")
            )
        }
    }
}

internal fun suitabilityStatusLabel(status: WorkSuitabilityStatus): String = when (status) {
    WorkSuitabilityStatus.SUITABLE -> "Suitable"
    WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> "Caution"
    WorkSuitabilityStatus.NOT_RECOMMENDED -> "Not Recommended"
    WorkSuitabilityStatus.INSUFFICIENT_DATA -> "Insufficient Data"
}

@Composable
internal fun suitabilityColor(status: WorkSuitabilityStatus): Color {
    val c = CtecsTheme.colors
    return when (status) {
        WorkSuitabilityStatus.SUITABLE -> c.success
        WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> c.warning
        WorkSuitabilityStatus.NOT_RECOMMENDED -> c.error
        WorkSuitabilityStatus.INSUFFICIENT_DATA -> c.textMuted
    }
}
