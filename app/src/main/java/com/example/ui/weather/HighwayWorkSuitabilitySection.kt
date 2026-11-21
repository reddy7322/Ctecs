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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.model.WorkSuitabilityStatus
import com.example.ui.theme.CtecsTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * HIGHWAY WORK WEATHER SUITABILITY section inside Weather Dashboard.
 *
 * Displays:
 * - Active IRC/MoRTH specification reference selector (MoRTH 5th Rev 2013 vs 6th Rev 2026 effective 1 Jan 2027)
 * - Forecast Information vs Actual Site Measurements verification panel
 * - Smart Hourly Work Suitability Timeline for the selected activity
 * - Separate rule-driven assessments for all 14 highway construction activities
 * - Explicit Engineer Approval & Compliance disclaimer
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
    nowMs: Long = System.currentTimeMillis()
) {
    val c = CtecsTheme.colors
    var showSiteVerificationForm by remember { mutableStateOf(false) }
    var expandedActivity by remember { mutableStateOf<HighwayWorkActivity?>(selectedActivity) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("highway_work_suitability_section")
    ) {
        // 1. Applicable IRC & MoRTH Reference Header Card
        item(key = "spec_reference_card") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("irc_morth_spec_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Gavel, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "HIGHWAY WORK WEATHER SUITABILITY • IRC & MoRTH",
                            color = c.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MoRTHRevision.entries.forEach { rev ->
                            FilterChip(
                                selected = contractSpec.morthRevision == rev,
                                onClick = {
                                    onUpdateContractSpec(contractSpec.copy(morthRevision = rev))
                                },
                                label = { Text(rev.shortLabel, fontSize = 11.sp) },
                                modifier = Modifier.testTag("morth_rev_chip_${rev.name.lowercase()}")
                            )
                        }
                    }

                    if (contractSpec.morthRevision == MoRTHRevision.SIXTH_REVISION_2026) {
                        val effectiveNow = contractSpec.isSixthRevisionEffective(nowMs)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = c.warning.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (effectiveNow) {
                                        "MoRTH 6th Revision (2026) is past its 1 Jan 2027 NH effective date — verify project contract adoption."
                                    } else {
                                        "MoRTH 6th Revision (2026) National Highway implementation date is 1 January 2027. Confirm contract/amendment adoption before applying."
                                    },
                                    color = c.textPrimary,
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Contract Verified for 6th Revision",
                                        color = c.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = contractSpec.sixthRevisionContractVerified,
                                        onCheckedChange = { verified ->
                                            onUpdateContractSpec(contractSpec.copy(sixthRevisionContractVerified = verified))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Bitumen Binder Selector for BC / DBM mandatory surface temperature limits
                    Text(
                        text = "Active Bituminous Binder (${selectedActivity.shortName}):",
                        color = c.textMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val activeBinder = contractSpec.binderFor(selectedActivity)
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
                                        "${binder.displayName.substringBefore(" (")} (≥${binder.minPavementSurfaceTempC.toInt()}°C)",
                                        fontSize = 10.5.sp
                                    )
                                },
                                modifier = Modifier.testTag("binder_chip_${binder.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "IRC Ref: ${contractSpec.ircReferences}",
                        color = c.textMuted,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        // 2. Forecast Information vs Actual Site Measurements Verification Card
        item(key = "site_measurements_card") {
            SiteMeasurementVerificationCard(
                siteMeasurements = siteMeasurements,
                isExpanded = showSiteVerificationForm,
                onToggleExpanded = { showSiteVerificationForm = !showSiteVerificationForm },
                onUpdate = onUpdateSiteMeasurements
            )
        }

        // 3. Smart Hourly Work Suitability Timeline (Dynamic for Selected Activity)
        item(key = "hourly_work_timeline") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hourly_work_suitability_timeline")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HOURLY WORK SUITABILITY TIMELINE • ${selectedActivity.displayName.uppercase(Locale.US)}",
                        color = c.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Never recommends laying during mandatory MoRTH/IRC weather restriction violations. Forecast air temperature is NOT measured pavement surface temperature.",
                        color = c.textMuted,
                        fontSize = 10.5.sp
                    )

                    if (hourlyWorkTimeline.isEmpty()) {
                        Text(
                            text = "Hourly forecast unavailable. Refresh live weather to build the hourly work timeline.",
                            color = c.warning,
                            fontSize = 11.5.sp
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(hourlyWorkTimeline.take(24), key = { it.hourPoint.epochMs }) { win ->
                                val statusColor = suitabilityColor(win.status)
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(statusColor.copy(alpha = if (c.isDark) 0.16f else 0.11f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("timeline_hour_${win.hourPoint.localHourOfDay}"),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = win.hourPoint.formatCompactHourAmPm(),
                                        color = c.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = win.status.label,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${temperatureUnit.format(win.hourPoint.temperatureC)} • Rain ${win.hourPoint.rainProbabilityPercent}%",
                                        color = c.textSecondary,
                                        fontSize = 9.5.sp
                                    )
                                    Text(
                                        text = "Wind ${win.hourPoint.windSpeedKmh.roundToInt()} (G ${win.hourPoint.windGustsKmh.roundToInt()}) km/h • RH ${win.hourPoint.humidityPercent}%",
                                        color = c.textMuted,
                                        fontSize = 9.sp
                                    )
                                    Text(
                                        text = "Conf: ${win.confidence.label}",
                                        color = c.textMuted,
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. All 14 Highway Construction Activities
        item(key = "activities_header") {
            Text(
                text = "ALL 14 HIGHWAY CONSTRUCTION ACTIVITIES (TAP TO INSPECT & UPDATE TIMELINE)",
                color = c.textMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }

        items(assessments, key = { it.activity.name }) { assessment ->
            val isSelected = assessment.activity == selectedActivity
            val isExpanded = expandedActivity == assessment.activity
            ActivitySuitabilityCard(
                assessment = assessment,
                isSelected = isSelected,
                isExpanded = isExpanded,
                onClick = {
                    onSelectActivity(assessment.activity)
                    expandedActivity = if (expandedActivity == assessment.activity) null else assessment.activity
                }
            )
        }

        // 5. Explicit Engineering & Approval Disclaimer
        item(key = "engineer_disclaimer") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = c.secondarySurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_engineer_disclaimer")
            ) {
                Text(
                    text = ActivitySuitabilityAssessment.ENGINEER_APPROVAL_DISCLAIMER,
                    color = c.textMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        item(key = "suitability_bottom_pad") { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteMeasurementVerificationCard(
    siteMeasurements: ActualSiteMeasurements,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
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

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("site_measurements_verification_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Science, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            text = "ACTUAL SITE MEASUREMENTS (VS FORECAST)",
                            color = c.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (siteMeasurements.hasAnyMeasurement) {
                                "Surface: ${siteMeasurements.pavementSurfaceTempC?.let { "${it}°C" } ?: "Unmeasured"} • Base: ${siteMeasurements.baseMoistureCondition.label}"
                            } else {
                                "Forecast air temp ≠ pavement surface temp. Tap to log actual site readings."
                            },
                            color = c.textMuted,
                            fontSize = 10.5.sp,
                            maxLines = 2
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse site measurements" else "Expand site measurements",
                    tint = c.textMuted
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = surfaceTempText,
                            onValueChange = { txt ->
                                surfaceTempText = txt
                                onUpdate(
                                    siteMeasurements.copy(
                                        pavementSurfaceTempC = txt.toDoubleOrNull(),
                                        measuredAtMs = System.currentTimeMillis()
                                    )
                                )
                            },
                            label = { Text("Pavement Surface °C", fontSize = 10.sp) },
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
                                onUpdate(
                                    siteMeasurements.copy(
                                        layingTempC = txt.toDoubleOrNull(),
                                        measuredAtMs = System.currentTimeMillis()
                                    )
                                )
                            },
                            label = { Text("Laying °C", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedTextField(
                            value = rollingTempText,
                            onValueChange = { txt ->
                                rollingTempText = txt
                                onUpdate(
                                    siteMeasurements.copy(
                                        rollingTempC = txt.toDoubleOrNull(),
                                        measuredAtMs = System.currentTimeMillis()
                                    )
                                )
                            },
                            label = { Text("Rolling °C", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Text("Base / Binder Moisture Condition:", color = c.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                label = { Text(cond.label, fontSize = 10.sp) },
                                modifier = Modifier.testTag("moisture_chip_${cond.name.lowercase()}")
                            )
                        }
                    }

                    Text("Underlying Tack Coat Condition:", color = c.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TackCoatCondition.entries.forEach { tc ->
                            FilterChip(
                                selected = siteMeasurements.tackCoatCondition == tc,
                                onClick = {
                                    onUpdate(siteMeasurements.copy(tackCoatCondition = tc, measuredAtMs = System.currentTimeMillis()))
                                },
                                label = { Text(tc.label, fontSize = 10.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Standing Water Observed on Surface",
                            color = c.textPrimary,
                            fontSize = 11.5.sp
                        )
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
}

@Composable
private fun ActivitySuitabilityCard(
    assessment: ActivitySuitabilityAssessment,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val c = CtecsTheme.colors
    val statusColor = suitabilityColor(assessment.status)
    val statusIcon = when (assessment.status) {
        WorkSuitabilityStatus.SUITABLE -> Icons.Rounded.CheckCircle
        WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> Icons.Rounded.WarningAmber
        WorkSuitabilityStatus.NOT_RECOMMENDED -> Icons.Rounded.ErrorOutline
        WorkSuitabilityStatus.INSUFFICIENT_DATA -> Icons.AutoMirrored.Rounded.HelpOutline
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) statusColor.copy(alpha = if (c.isDark) 0.14f else 0.08f) else c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("activity_card_${assessment.activity.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assessment.activity.displayName,
                        color = c.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = assessment.applicableReference,
                        color = c.textMuted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("activity_status_${assessment.activity.name.lowercase()}")
                ) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
                    Text(
                        text = assessment.status.label,
                        color = statusColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Text(
                text = assessment.summaryReason,
                color = c.textSecondary,
                fontSize = 11.5.sp
            )

            AnimatedVisibility(visible = isExpanded && assessment.checks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    assessment.checks.forEach { check ->
                        val chkColor = suitabilityColor(check.status)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.secondarySurface)
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = check.ruleName + if (check.isMandatoryRestriction) " [MANDATORY]" else "",
                                    color = c.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = check.status.label,
                                    color = chkColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${check.observedValue} • ${check.clauseReference}",
                                color = chkColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = check.detail,
                                color = c.textSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
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
