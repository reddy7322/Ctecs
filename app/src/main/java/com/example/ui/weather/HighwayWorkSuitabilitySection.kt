package com.example.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActivitySuitabilityAssessment
import com.example.model.ActualSiteMeasurements
import com.example.model.BitumenBinderType
import com.example.model.ContractSpecificationConfig
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWorkWindow
import com.example.model.MoRTHRevision
import com.example.model.SurfaceMoistureCondition
import com.example.model.TackCoatCondition
import com.example.model.TemperatureUnit
import com.example.model.WeatherForecastBundle
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkTargetPrediction
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.weather.IrcMorthWeatherRuleEngine
import java.util.Locale
import kotlin.math.roundToInt

private data class ActivityWindowBrief(
    val recommendedWindow: String,
    val peakRainPercent: Int?,
    val workableHours: Int,
    val confidenceLabel: String
)

private fun summarizeTimeline(timeline: List<HourlyWorkWindow>): ActivityWindowBrief {
    if (timeline.isEmpty()) {
        return ActivityWindowBrief(
            recommendedWindow = "Forecast-dependent",
            peakRainPercent = null,
            workableHours = 0,
            confidenceLabel = "Forecast-dependent"
        )
    }
    val workable = timeline.filter {
        it.status == WorkSuitabilityStatus.SUITABLE || it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION
    }
    val peakRain = timeline.maxOfOrNull { it.hourPoint.rainProbabilityPercent }
    // Longest contiguous suitable/caution block for concise recommended window
    var bestStart = -1
    var bestLen = 0
    var curStart = -1
    var curLen = 0
    timeline.forEachIndexed { idx, w ->
        val ok = w.status == WorkSuitabilityStatus.SUITABLE || w.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION
        if (ok) {
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
    val windowLabel = if (bestStart >= 0 && bestLen > 0) {
        val startStr = timeline[bestStart].hourPoint.formatCompactHourAmPm()
        val endStr = timeline[(bestStart + bestLen - 1).coerceAtMost(timeline.lastIndex)].hourPoint.formatCompactHourAmPm()
        if (bestLen == 1) startStr else "$startStr–$endStr"
    } else {
        "None in 24h"
    }
    return ActivityWindowBrief(
        recommendedWindow = windowLabel,
        peakRainPercent = peakRain,
        workableHours = workable.size,
        confidenceLabel = timeline.first().confidence.label
    )
}

/**
 * Compact Smart-Dropdown HIGHWAY WORK WEATHER SUITABILITY section inside Weather Dashboard.
 *
 * Keeps BC Laying, DBM Laying, Work Planning Timeline, Other Highway Works, and IRC/MoRTH
 * References compact by default with essential values first, while keeping mandatory safety
 * and specification warnings visible even when collapsed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighwayWorkSuitabilitySection(
    assessments: List<ActivitySuitabilityAssessment>,
    selectedActivity: HighwayWorkActivity,
    hourlyWorkTimeline: List<HourlyWorkWindow>,
    contractSpec: ContractSpecificationConfig,
    siteMeasurements: ActualSiteMeasurements,
    temperatureUnit: TemperatureUnit,
    onSelectActivity: (HighwayWorkActivity) -> Unit,
    onUpdateContractSpec: (ContractSpecificationConfig) -> Unit,
    onUpdateSiteMeasurements: (ActualSiteMeasurements) -> Unit,
    nowMs: Long = System.currentTimeMillis(),
    forecastBundle: WeatherForecastBundle? = null,
    workTargetPrediction: WorkTargetPrediction? = null,
    expandedSections: Set<String> = emptySet(),
    onToggleSection: (String) -> Unit = {}
) {
    val c = CtecsTheme.colors
    var expandedOtherActivities by remember { mutableStateOf<Set<HighwayWorkActivity>>(emptySet()) }
    var showSiteInputsInsideRef by remember { mutableStateOf(false) }

    val bcAssessment = remember(assessments) {
        assessments.firstOrNull { it.activity == HighwayWorkActivity.BC_LAYING }
    }
    val dbmAssessment = remember(assessments) {
        assessments.firstOrNull { it.activity == HighwayWorkActivity.DBM_LAYING }
    }
    val otherAssessments = remember(assessments) {
        assessments.filter {
            it.activity != HighwayWorkActivity.BC_LAYING && it.activity != HighwayWorkActivity.DBM_LAYING
        }
    }

    val next24 = remember(forecastBundle, nowMs) { forecastBundle?.next24Hours(nowMs).orEmpty() }
    val bcBrief = remember(next24, contractSpec, siteMeasurements, selectedActivity, hourlyWorkTimeline) {
        val tl = if (selectedActivity == HighwayWorkActivity.BC_LAYING && hourlyWorkTimeline.isNotEmpty()) {
            hourlyWorkTimeline
        } else {
            IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
                HighwayWorkActivity.BC_LAYING, next24, contractSpec, siteMeasurements
            )
        }
        summarizeTimeline(tl)
    }
    val dbmBrief = remember(next24, contractSpec, siteMeasurements, selectedActivity, hourlyWorkTimeline) {
        val tl = if (selectedActivity == HighwayWorkActivity.DBM_LAYING && hourlyWorkTimeline.isNotEmpty()) {
            hourlyWorkTimeline
        } else {
            IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
                HighwayWorkActivity.DBM_LAYING, next24, contractSpec, siteMeasurements
            )
        }
        summarizeTimeline(tl)
    }
    val selectedTimelineBrief = remember(hourlyWorkTimeline) { summarizeTimeline(hourlyWorkTimeline) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("highway_work_suitability_section")
    ) {
        // 1. BC Laying Suitability (Dedicated Compact Smart Dropdown)
        bcAssessment?.let { assessment ->
            item(key = "section_bc_suitability") {
                PrimaryBituminousSuitabilitySection(
                    sectionKey = WeatherSectionKeys.BC_SUITABILITY,
                    title = "BC Laying Suitability",
                    assessment = assessment,
                    brief = bcBrief,
                    targetPrediction = workTargetPrediction?.takeIf {
                        it.activity == HighwayWorkActivity.BC_LAYING && it.missingInputWarnings.isEmpty()
                    },
                    siteMeasurements = siteMeasurements,
                    isExpanded = WeatherSectionKeys.BC_SUITABILITY in expandedSections,
                    onToggle = {
                        onSelectActivity(HighwayWorkActivity.BC_LAYING)
                        onToggleSection(WeatherSectionKeys.BC_SUITABILITY)
                    }
                )
            }
        }

        // 2. DBM Laying Suitability (Dedicated Compact Smart Dropdown)
        dbmAssessment?.let { assessment ->
            item(key = "section_dbm_suitability") {
                PrimaryBituminousSuitabilitySection(
                    sectionKey = WeatherSectionKeys.DBM_SUITABILITY,
                    title = "DBM Laying Suitability",
                    assessment = assessment,
                    brief = dbmBrief,
                    targetPrediction = workTargetPrediction?.takeIf {
                        it.activity == HighwayWorkActivity.DBM_LAYING && it.missingInputWarnings.isEmpty()
                    },
                    siteMeasurements = siteMeasurements,
                    isExpanded = WeatherSectionKeys.DBM_SUITABILITY in expandedSections,
                    onToggle = {
                        onSelectActivity(HighwayWorkActivity.DBM_LAYING)
                        onToggleSection(WeatherSectionKeys.DBM_SUITABILITY)
                    }
                )
            }
        }

        // 3. Work Planning Timeline (Smart Dropdown)
        item(key = "section_work_planning_timeline") {
            val tlSummary = "${selectedActivity.shortName} • ${selectedTimelineBrief.workableHours}h workable • Window: ${selectedTimelineBrief.recommendedWindow}"
            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.WORK_PLANNING_TIMELINE,
                title = "Work Planning Timeline",
                summaryText = tlSummary,
                badgeText = selectedActivity.shortName,
                badgeColor = c.accent,
                icon = Icons.Rounded.Schedule,
                iconTint = c.accent,
                isExpanded = WeatherSectionKeys.WORK_PLANNING_TIMELINE in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.WORK_PLANNING_TIMELINE) },
                modifier = Modifier.testTag("hourly_work_suitability_timeline")
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(HighwayWorkActivity.entries, key = { it.name }) { act ->
                        val active = act == selectedActivity
                        Text(
                            text = act.shortName,
                            color = if (active) c.accent else c.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (active) c.accent.copy(alpha = if (c.isDark) 0.20f else 0.14f)
                                    else c.secondarySurface
                                )
                                .clickable { onSelectActivity(act) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }

                if (hourlyWorkTimeline.isEmpty()) {
                    Text(
                        text = "Refresh live weather to populate 24h timeline.",
                        color = c.warning,
                        fontSize = 11.sp
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hourlyWorkTimeline.take(24), key = { it.hourPoint.epochMs }) { win ->
                            val statusColor = suitabilityColor(win.status)
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(statusColor.copy(alpha = if (c.isDark) 0.16f else 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("timeline_hour_${win.hourPoint.localHourOfDay}"),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = win.hourPoint.formatCompactHourAmPm(),
                                    color = c.textPrimary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = compactStatusLabel(win.status),
                                    color = statusColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${temperatureUnit.format(win.hourPoint.temperatureC)} • ${win.hourPoint.rainProbabilityPercent}%",
                                    color = c.textSecondary,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "${win.hourPoint.windSpeedKmh.roundToInt()} km/h",
                                    color = c.textMuted,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Other Highway Work Suitability (12 Activities inside Smart Dropdown)
        item(key = "section_other_highway_work") {
            val okCount = otherAssessments.count { it.status == WorkSuitabilityStatus.SUITABLE }
            val cautionCount = otherAssessments.count { it.status == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION }
            val stopCount = otherAssessments.count { it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }
            val otherSummary = "$okCount Suitable • $cautionCount Caution • $stopCount Not Rec."

            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.OTHER_HIGHWAY_WORK,
                title = "Other Highway Work Suitability",
                summaryText = otherSummary,
                badgeText = "${otherAssessments.size}",
                badgeColor = if (stopCount > 0) c.warning else c.success,
                icon = Icons.Rounded.Construction,
                iconTint = c.accent,
                isExpanded = WeatherSectionKeys.OTHER_HIGHWAY_WORK in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.OTHER_HIGHWAY_WORK) }
            ) {
                otherAssessments.forEach { assessment ->
                    val rowExpanded = assessment.activity in expandedOtherActivities
                    CompactActivityItemRow(
                        assessment = assessment,
                        isExpanded = rowExpanded,
                        onToggle = {
                            onSelectActivity(assessment.activity)
                            expandedOtherActivities = if (rowExpanded) {
                                expandedOtherActivities - assessment.activity
                            } else {
                                expandedOtherActivities + assessment.activity
                            }
                        }
                    )
                }
            }
        }

        // 5. IRC/MoRTH References & Actual Site Measurements (Smart Dropdown)
        item(key = "section_irc_morth_references") {
            val activeBinder = contractSpec.binderFor(selectedActivity)
            val sixthUnverified = contractSpec.morthRevision == MoRTHRevision.SIXTH_REVISION_2026 &&
                !contractSpec.sixthRevisionContractVerified
            val refSummary = "${contractSpec.morthRevision.shortLabel} • ${activeBinder.displayName.substringBefore(" (")} (≥${activeBinder.minPavementSurfaceTempC.toInt()}°C)"

            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.IRC_MORTH_REFERENCES,
                title = "IRC / MoRTH References",
                summaryText = refSummary,
                badgeText = if (siteMeasurements.hasAnyMeasurement) "SITE DATA" else "VERIFY SITE",
                badgeColor = if (siteMeasurements.hasAnyMeasurement) c.success else c.warning,
                icon = Icons.Rounded.Gavel,
                iconTint = c.accent,
                isExpanded = WeatherSectionKeys.IRC_MORTH_REFERENCES in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.IRC_MORTH_REFERENCES) },
                modifier = Modifier.testTag("irc_morth_spec_card"),
                alwaysVisibleContent = {
                    if (sixthUnverified) {
                        Text(
                            text = "MoRTH 6th Rev (2026) NH effective date: 1 Jan 2027 — confirm contract adoption.",
                            color = c.warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Forecast air temp ≠ pavement surface temp. Final approval rests with the Engineer.",
                        color = c.textMuted,
                        fontSize = 9.5.sp,
                        modifier = Modifier.testTag("weather_engineer_disclaimer")
                    )
                }
            ) {
                Text("Specification Revision", color = c.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MoRTHRevision.entries.forEach { rev ->
                        FilterChip(
                            selected = contractSpec.morthRevision == rev,
                            onClick = { onUpdateContractSpec(contractSpec.copy(morthRevision = rev)) },
                            label = { Text(rev.shortLabel, fontSize = 10.5.sp) },
                            modifier = Modifier.testTag("morth_rev_chip_${rev.name.lowercase()}")
                        )
                    }
                }

                if (contractSpec.morthRevision == MoRTHRevision.SIXTH_REVISION_2026) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.warning.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "6th Rev Contract Verified (NH eff. 1 Jan 2027)",
                            color = c.textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = contractSpec.sixthRevisionContractVerified,
                            onCheckedChange = { verified ->
                                onUpdateContractSpec(contractSpec.copy(sixthRevisionContractVerified = verified))
                            }
                        )
                    }
                }

                Text(
                    text = "Bituminous Binder (${selectedActivity.shortName})",
                    color = c.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        BitumenBinderType.CONVENTIONAL_VG30,
                        BitumenBinderType.CONVENTIONAL_VG40,
                        BitumenBinderType.MODIFIED_PMB,
                        BitumenBinderType.MODIFIED_CRMB
                    ).forEach { binder ->
                        FilterChip(
                            selected = activeBinder == binder,
                            onClick = {
                                val updated = if (selectedActivity == HighwayWorkActivity.DBM_LAYING) {
                                    contractSpec.copy(dbmBinderType = binder)
                                } else {
                                    contractSpec.copy(bcBinderType = binder)
                                }
                                onUpdateContractSpec(updated)
                            },
                            label = {
                                Text(
                                    "${binder.displayName.substringBefore(" (")} ≥${binder.minPavementSurfaceTempC.toInt()}°C",
                                    fontSize = 10.sp
                                )
                            },
                            modifier = Modifier.testTag("binder_chip_${binder.name.lowercase()}")
                        )
                    }
                }

                CompactMetricRow("IRC Reference", contractSpec.ircReferences)
                CompactMetricRow("Bituminous Cl.", "MoRTH Cl. 501.5.1, 505, 507.4.1")

                SmartFadeDivider()

                // Compact Nested Actual Site Measurements Verification Form
                CompactSiteMeasurementsBlock(
                    siteMeasurements = siteMeasurements,
                    isExpanded = showSiteInputsInsideRef,
                    onToggle = { showSiteInputsInsideRef = !showSiteInputsInsideRef },
                    onUpdate = onUpdateSiteMeasurements
                )

                Text(
                    text = ActivitySuitabilityAssessment.ENGINEER_APPROVAL_DISCLAIMER,
                    color = c.textMuted,
                    fontSize = 9.5.sp
                )
            }
        }

        item(key = "suitability_bottom_pad") { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun PrimaryBituminousSuitabilitySection(
    sectionKey: String,
    title: String,
    assessment: ActivitySuitabilityAssessment,
    brief: ActivityWindowBrief,
    targetPrediction: WorkTargetPrediction?,
    siteMeasurements: ActualSiteMeasurements,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val c = CtecsTheme.colors
    val statusColor = suitabilityColor(assessment.status)
    val mandatoryAlerts = remember(assessment.checks) {
        assessment.checks.filter {
            it.isMandatoryRestriction && it.status != WorkSuitabilityStatus.SUITABLE
        }
    }
    val rainTxt = brief.peakRainPercent?.let { "Rain $it%" } ?: "Rain —"
    val targetTxt = targetPrediction?.let {
        "Target ${String.format(Locale.US, "%.0f", it.weatherAdjustedTargetQuantity)} ${it.quantityUnit}"
    } ?: "Target: Set inputs"

    WeatherSmartSection(
        sectionKey = sectionKey,
        title = title,
        summaryText = "Window: ${brief.recommendedWindow} • $rainTxt • ${brief.workableHours}h",
        badgeText = compactStatusLabel(assessment.status),
        badgeColor = statusColor,
        icon = if (assessment.activity == HighwayWorkActivity.BC_LAYING) Icons.Rounded.Layers else Icons.Rounded.Engineering,
        iconTint = statusColor,
        isExpanded = isExpanded,
        onToggle = onToggle,
        modifier = Modifier.testTag("activity_card_${assessment.activity.name.lowercase()}"),
        alwaysVisibleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = targetTxt,
                    color = c.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = assessment.status.label,
                    color = statusColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("activity_status_${assessment.activity.name.lowercase()}")
                )
            }

            // Mandatory safety/spec warnings remain visible even when collapsed
            mandatoryAlerts.take(2).forEach { chk ->
                val chkColor = suitabilityColor(chk.status)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(chkColor.copy(alpha = if (c.isDark) 0.16f else 0.10f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = chkColor, modifier = Modifier.size(12.dp))
                    Text(
                        text = "${chk.ruleName}: ${chk.observedValue}",
                        color = c.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        CompactMetricRow("Technical Summary", assessment.summaryReason, valueColor = statusColor)
        CompactMetricRow("IRC / MoRTH Ref", assessment.applicableReference)
        CompactMetricRow("Recommended Window", brief.recommendedWindow)
        CompactMetricRow("Est. Working Hours", "${brief.workableHours} hrs (Next 24h)")
        CompactMetricRow("Confidence", brief.confidenceLabel)
        CompactMetricRow(
            "Site Verification",
            if (siteMeasurements.pavementSurfaceTempC != null) {
                "Measured surface ${siteMeasurements.pavementSurfaceTempC}°C • ${siteMeasurements.baseMoistureCondition.label}"
            } else {
                "Verify dry surface & pavement temp on site"
            },
            valueColor = if (siteMeasurements.pavementSurfaceTempC != null) c.success else c.warning
        )

        if (assessment.checks.isNotEmpty()) {
            SmartFadeDivider()
            assessment.checks.forEach { check ->
                val chkColor = suitabilityColor(check.status)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.secondarySurface)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = check.ruleName + if (check.isMandatoryRestriction) " *" else "",
                            color = c.textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = compactStatusLabel(check.status),
                            color = chkColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "${check.observedValue} • ${check.clauseReference}",
                        color = chkColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = check.detail,
                        color = c.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactActivityItemRow(
    assessment: ActivitySuitabilityAssessment,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val c = CtecsTheme.colors
    val statusColor = suitabilityColor(assessment.status)
    val statusIcon = when (assessment.status) {
        WorkSuitabilityStatus.SUITABLE -> Icons.Rounded.CheckCircle
        WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> Icons.Rounded.WarningAmber
        WorkSuitabilityStatus.NOT_RECOMMENDED -> Icons.Rounded.ErrorOutline
        WorkSuitabilityStatus.INSUFFICIENT_DATA -> Icons.AutoMirrored.Rounded.HelpOutline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.secondarySurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .testTag("activity_card_${assessment.activity.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = assessment.activity.displayName,
                color = c.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.testTag("activity_status_${assessment.activity.name.lowercase()}")
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                Text(
                    text = compactStatusLabel(assessment.status),
                    color = statusColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = c.textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = assessment.summaryReason,
                    color = c.textSecondary,
                    fontSize = 10.5.sp
                )
                Text(
                    text = "Ref: ${assessment.applicableReference}",
                    color = c.textMuted,
                    fontSize = 9.5.sp
                )
                assessment.checks.forEach { chk ->
                    val chkColor = suitabilityColor(chk.status)
                    Text(
                        text = "• ${chk.ruleName}: ${chk.observedValue} (${compactStatusLabel(chk.status)})",
                        color = chkColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactSiteMeasurementsBlock(
    siteMeasurements: ActualSiteMeasurements,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (ActualSiteMeasurements) -> Unit
) {
    val c = CtecsTheme.colors
    var surfaceTempText by remember(siteMeasurements.pavementSurfaceTempC) {
        mutableStateOf(siteMeasurements.pavementSurfaceTempC?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var layingTempText by remember(siteMeasurements.layingTempC) {
        mutableStateOf(siteMeasurements.layingTempC?.let { String.format(Locale.US, "%.0f", it) } ?: "")
    }
    var rollingTempText by remember(siteMeasurements.rollingTempC) {
        mutableStateOf(siteMeasurements.rollingTempC?.let { String.format(Locale.US, "%.0f", it) } ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.secondarySurface)
            .padding(9.dp)
            .testTag("site_measurements_verification_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Science, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                Column {
                    Text(
                        text = "Actual Site Measurements",
                        color = c.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (siteMeasurements.hasAnyMeasurement) {
                            "Surface ${siteMeasurements.pavementSurfaceTempC?.let { "${it}°C" } ?: "—"} • ${siteMeasurements.baseMoistureCondition.label}"
                        } else {
                            "Tap to log measured surface temp & moisture"
                        },
                        color = c.textMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse site measurements" else "Expand site measurements",
                tint = c.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = surfaceTempText,
                        onValueChange = { txt ->
                            surfaceTempText = txt
                            onUpdate(siteMeasurements.copy(pavementSurfaceTempC = txt.toDoubleOrNull(), measuredAtMs = System.currentTimeMillis()))
                        },
                        label = { Text("Surface °C", fontSize = 9.5.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_site_surface_temp")
                    )
                    OutlinedTextField(
                        value = layingTempText,
                        onValueChange = { txt ->
                            layingTempText = txt
                            onUpdate(siteMeasurements.copy(layingTempC = txt.toDoubleOrNull(), measuredAtMs = System.currentTimeMillis()))
                        },
                        label = { Text("Laying °C", fontSize = 9.5.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(0.85f)
                    )
                    OutlinedTextField(
                        value = rollingTempText,
                        onValueChange = { txt ->
                            rollingTempText = txt
                            onUpdate(siteMeasurements.copy(rollingTempC = txt.toDoubleOrNull(), measuredAtMs = System.currentTimeMillis()))
                        },
                        label = { Text("Rolling °C", fontSize = 9.5.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(0.85f)
                    )
                }

                Text("Base Moisture", color = c.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SurfaceMoistureCondition.entries.forEach { cond ->
                        FilterChip(
                            selected = siteMeasurements.baseMoistureCondition == cond,
                            onClick = {
                                onUpdate(
                                    siteMeasurements.copy(
                                        baseMoistureCondition = cond,
                                        hasStandingWater = cond == SurfaceMoistureCondition.STANDING_WATER,
                                        measuredAtMs = System.currentTimeMillis()
                                    )
                                )
                            },
                            label = { Text(cond.label, fontSize = 9.5.sp) },
                            modifier = Modifier.testTag("moisture_chip_${cond.name.lowercase()}")
                        )
                    }
                }

                Text("Tack Coat", color = c.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TackCoatCondition.entries.forEach { tc ->
                        FilterChip(
                            selected = siteMeasurements.tackCoatCondition == tc,
                            onClick = {
                                onUpdate(siteMeasurements.copy(tackCoatCondition = tc, measuredAtMs = System.currentTimeMillis()))
                            },
                            label = { Text(tc.label, fontSize = 9.5.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Standing Water Present", color = c.textPrimary, fontSize = 11.sp)
                    Switch(
                        checked = siteMeasurements.hasStandingWater,
                        onCheckedChange = { standing ->
                            onUpdate(
                                siteMeasurements.copy(
                                    hasStandingWater = standing,
                                    baseMoistureCondition = if (standing) SurfaceMoistureCondition.STANDING_WATER else siteMeasurements.baseMoistureCondition,
                                    measuredAtMs = System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier.testTag("switch_standing_water")
                    )
                }
            }
        }
    }
}

private fun compactStatusLabel(status: WorkSuitabilityStatus): String = when (status) {
    WorkSuitabilityStatus.SUITABLE -> "Suitable"
    WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> "Caution"
    WorkSuitabilityStatus.NOT_RECOMMENDED -> "Not Rec."
    WorkSuitabilityStatus.INSUFFICIENT_DATA -> "No Data"
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
