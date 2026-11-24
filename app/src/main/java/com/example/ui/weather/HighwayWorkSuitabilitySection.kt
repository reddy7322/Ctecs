package com.example.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActivitySuitabilityAssessment
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWorkWindow
import com.example.model.WorkSuitabilityStatus
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider

/**
 * Computes the single longest contiguous reliable working window (Suitable or Caution)
 * from [timeline], returning null when forecast data is unavailable or no window exists.
 */
internal fun computeBestWorkingWindowLabel(
    timeline: List<HourlyWorkWindow>,
    is24HourFormat: Boolean
): String? {
    if (timeline.isEmpty()) return null
    var bestStart = -1
    var bestLen = 0
    var curStart = -1
    var curLen = 0

    timeline.forEachIndexed { idx, window ->
        val workable = window.status == WorkSuitabilityStatus.SUITABLE ||
            window.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION
        if (workable) {
            if (curStart == -1) curStart = idx
            curLen++
            if (curLen > bestLen) {
                bestLen = curLen
                bestStart = curStart
            }
        } else {
            curStart = -1
            curLen = 0
        }
    }

    if (bestStart < 0 || bestLen <= 0) return null
    val startPt = timeline[bestStart].hourPoint
    val endPt = timeline[(bestStart + bestLen - 1).coerceAtMost(timeline.lastIndex)].hourPoint
    val startText = formatHourlyTimeLabel(startPt.localHourOfDay, is24HourFormat)
    val endText = formatHourlyTimeLabel(endPt.localHourOfDay, is24HourFormat)
    return if (bestLen == 1) startText else "$startText – $endText"
}

internal fun formatHourlyTimeLabel(localHourOfDay: Int, is24HourFormat: Boolean): String {
    val h = ((localHourOfDay % 24) + 24) % 24
    return if (is24HourFormat) {
        String.format(java.util.Locale.US, "%02d:00", h)
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
 * Simplified, compact Highway Weather Suitability section.
 *
 * Shows only:
 * - Selected activity name (with smooth expandable activity selector for relevant activities)
 * - Status: Suitable / Caution / Not Recommended / Insufficient Data
 * - One short reason
 * - Best available working window when reliable
 * - Concise advisory footnote (never replaces site approval or specifications)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactHighwaySuitabilitySection(
    assessments: List<ActivitySuitabilityAssessment>,
    selectedActivity: HighwayWorkActivity,
    hourlyWorkTimeline: List<HourlyWorkWindow>,
    is24HourFormat: Boolean,
    isPickerExpanded: Boolean,
    onTogglePicker: () -> Unit,
    onSelectActivity: (HighwayWorkActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val selectedAssessment = remember(assessments, selectedActivity) {
        assessments.firstOrNull { it.activity == selectedActivity }
    }
    val status = selectedAssessment?.status ?: WorkSuitabilityStatus.INSUFFICIENT_DATA
    val statusColor = suitabilityColor(status)
    val statusText = suitabilityStatusLabel(status)
    val shortReason = remember(selectedAssessment) {
        selectedAssessment?.summaryReason
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Weather data required to assess ${selectedActivity.shortName}."
    }
    val bestWindow = remember(hourlyWorkTimeline, is24HourFormat) {
        computeBestWorkingWindowLabel(hourlyWorkTimeline, is24HourFormat)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isPickerExpanded) 180f else 0f,
        animationSpec = tween(180),
        label = "suitability_picker_chevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("highway_work_suitability_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row: Activity selector + Status pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTogglePicker)
                .padding(vertical = 2.dp)
                .testTag("weather_suitability_activity_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "HIGHWAY WORK",
                    color = c.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedActivity.displayName,
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("weather_suitability_activity_name")
                    )
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (isPickerExpanded) "Hide activities" else "Change highway activity",
                        tint = c.textMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = if (c.isDark) 0.16f else 0.11f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("activity_status_${selectedActivity.name.lowercase()}")
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        // One concise reason
        Text(
            text = shortReason,
            color = c.textSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("weather_suitability_reason")
        )

        // Best available working window (shown only when reliable)
        if (bestWindow != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("weather_suitability_best_window")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Best window: $bestWindow",
                    color = c.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Smooth expandable activity selector (collapsed by default)
        AnimatedVisibility(
            visible = isPickerExpanded,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_suitability_activity_picker"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartFadeDivider(
                    color = if (c.isDark) c.elevatedSurface else c.subtleSeparator
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HighwayWorkActivity.entries.forEach { activity ->
                        val isSelected = activity == selectedActivity
                        val itemStatus = assessments.firstOrNull { it.activity == activity }?.status
                        val dotColor = itemStatus?.let { suitabilityColor(it) } ?: c.textMuted
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) c.accent.copy(alpha = if (c.isDark) 0.18f else 0.12f)
                                    else c.secondarySurface
                                )
                                .clickable {
                                    onSelectActivity(activity)
                                    onTogglePicker()
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("suitability_pick_${activity.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = activity.shortName,
                                color = if (isSelected) c.accent else c.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(1.dp))

        // Concise advisory disclaimer
        Text(
            text = "Advisory only — verify pavement conditions, specifications, and site approval before work.",
            color = c.textMuted,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            modifier = Modifier.testTag("weather_engineer_disclaimer")
        )
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
