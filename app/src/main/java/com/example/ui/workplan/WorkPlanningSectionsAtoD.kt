package com.example.ui.workplan

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HighwayWorkPlan
import com.example.model.HighwayWorkPlanCalculation
import com.example.model.MachineryCatalogEntry
import com.example.model.MoRTHRevision
import com.example.model.PlannedMachineryItem
import com.example.model.PlanningWorkActivity
import com.example.model.RoadStretchWidthItem
import com.example.model.ShiftTimingCalculator
import com.example.model.ThicknessCompactionSpec
import com.example.model.TransportStatus
import com.example.model.WorkPlanningSettings
import com.example.model.WorkPlanningStandardEngine
import com.example.model.WorkShiftType
import com.example.model.WorkSuitabilityStatus
import com.example.model.WorkingTimePeriod
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.ctecsBorderlessInputColors
import java.util.Locale

@Composable
internal fun WorkPlanExpandableCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    tagPrefix: String,
    animationsEnabled: Boolean = true,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = CtecsTheme.colors
    val arrowDeg by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(if (animationsEnabled) 200 else 0),
        label = "wp_arrow_$tagPrefix"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .testTag("wp_section_$tagPrefix")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("wp_toggle_$tagPrefix"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.accent.copy(alpha = if (c.isDark) 0.18f else 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp).rotate(arrowDeg)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(if (animationsEnabled) 200 else 0)) + fadeIn(tween(if (animationsEnabled) 150 else 0)),
            exit = shrinkVertically(tween(if (animationsEnabled) 180 else 0)) + fadeOut(tween(if (animationsEnabled) 120 else 0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .testTag("wp_body_$tagPrefix"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun WpField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    placeholder: String = "",
    tag: String = ""
) {
    val c = CtecsTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, fontSize = 11.sp, color = c.textMuted) }) else null,
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(11.dp),
        colors = ctecsBorderlessInputColors(c.secondarySurface),
        modifier = modifier.let { if (tag.isNotBlank()) it.testTag(tag) else it }
    )
}

@Composable
internal fun Section1WorkDetailsContent(
    plan: HighwayWorkPlan,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    val chCalc = plan.chainageLengthCalc
    val shiftTiming = plan.shiftTimingResult

    // Project Details
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Project Name", plan.projectName, { onUpdatePlan(plan.copy(projectName = it)) }, Modifier.weight(1f), tag = "wp_project_name")
        WpField("Highway / Road", plan.roadOrHighwayName, { onUpdatePlan(plan.copy(roadOrHighwayName = it)) }, Modifier.weight(1f), tag = "wp_highway_name")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Work Date (YYYY-MM-DD)", plan.workDateIso, { onUpdatePlan(plan.copy(workDateIso = it, shiftStartDateIso = it)) }, Modifier.weight(1f), placeholder = "2026-11-25")
        WpField("Location", plan.locationName, { onUpdatePlan(plan.copy(locationName = it)) }, Modifier.weight(1f), placeholder = "Section / Toll")
    }

    // Side selector
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("LHS", "RHS", "Both", "Median").forEach { side ->
            val selected = plan.roadSide.equals(side, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) c.accent else c.secondarySurface)
                    .clickable { onUpdatePlan(plan.copy(roadSide = side)) }
                    .padding(vertical = 8.dp)
                    .testTag("wp_side_${side.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(side, color = if (selected) c.onAccent else c.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Chainage Inputs & Auto Length
    Text("CHAINAGE & LENGTH", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Start Chainage", plan.startChainage, { onUpdatePlan(plan.copy(startChainage = it)) }, Modifier.weight(1f), placeholder = "188+840", tag = "wp_start_ch")
        WpField("End Chainage", plan.endChainage, { onUpdatePlan(plan.copy(endChainage = it)) }, Modifier.weight(1f), placeholder = "189+240", tag = "wp_end_ch")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.secondarySurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val lenText = plan.effectiveLengthMeters?.let { "${wpFmt(it)} m" } ?: "Not Set"
            val tagSource = if (plan.isLengthFromChainage) "Calculated from Chainage" else "Manual Override"
            Text("Effective Length: $lenText", color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("wp_effective_length"))
            Text(tagSource, color = if (plan.isLengthFromChainage) c.success else c.warning, fontSize = 10.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Manual", color = c.textSecondary, fontSize = 11.sp)
            Switch(
                checked = plan.useManualLengthOverride,
                onCheckedChange = { onUpdatePlan(plan.copy(useManualLengthOverride = it)) },
                modifier = Modifier.testTag("wp_manual_len_toggle"),
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent)
            )
        }
    }
    if (chCalc.warningMessage != null) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(c.warning.copy(alpha = 0.14f)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(15.dp))
            Text(chCalc.warningMessage, color = c.warning, fontSize = 11.sp, modifier = Modifier.testTag("wp_chainage_warning"))
        }
    }
    if (plan.useManualLengthOverride || chCalc.calculatedLengthMeters == null) {
        WpField(
            label = "Manual Length Override (m)",
            value = plan.workLengthMeters?.let { wpFmt(it) } ?: "",
            onValueChange = { onUpdatePlan(plan.copy(workLengthMeters = it.toDoubleOrNull())) },
            modifier = Modifier.fillMaxWidth(),
            numeric = true,
            tag = "wp_manual_length_input"
        )
    }

    // Width & Lane Inputs (strictly without auto-multiplying when actual work width is given)
    Text("WIDTH, LANES & STRETCHES", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField(
            "Actual Work Width (m)",
            plan.workWidthMeters?.let { wpFmt(it) } ?: "",
            { onUpdatePlan(plan.copy(workWidthMeters = it.toDoubleOrNull())) },
            Modifier.weight(1f),
            numeric = true,
            tag = "wp_work_width"
        )
        WpField(
            "Total Road Width (m)",
            plan.totalRoadWidthMeters?.let { wpFmt(it) } ?: "",
            { onUpdatePlan(plan.copy(totalRoadWidthMeters = it.toDoubleOrNull())) },
            Modifier.weight(1f),
            numeric = true
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField(
            "Lane Width (m)",
            plan.individualLaneWidthMeters?.let { wpFmt(it) } ?: "",
            { onUpdatePlan(plan.copy(individualLaneWidthMeters = it.toDoubleOrNull())) },
            Modifier.weight(1f),
            numeric = true
        )
        WpField(
            "No. of Lanes",
            plan.numberOfLanes?.toString() ?: "",
            { onUpdatePlan(plan.copy(numberOfLanes = it.toIntOrNull())) },
            Modifier.weight(1f),
            numeric = true
        )
    }
    // Optional additional stretches
    if (plan.stretches.isNotEmpty()) {
        plan.stretches.forEachIndexed { idx, stretch ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                WpField("Stretch ${idx + 1} L(m)", wpFmt(stretch.lengthMeters), { v ->
                    val next = plan.stretches.toMutableList()
                    next[idx] = stretch.copy(lengthMeters = v.toDoubleOrNull() ?: 0.0)
                    onUpdatePlan(plan.copy(stretches = next))
                }, Modifier.weight(1f), numeric = true)
                WpField("W(m)", wpFmt(stretch.widthMeters), { v ->
                    val next = plan.stretches.toMutableList()
                    next[idx] = stretch.copy(widthMeters = v.toDoubleOrNull() ?: 0.0)
                    onUpdatePlan(plan.copy(stretches = next))
                }, Modifier.weight(1f), numeric = true)
                IconButton(onClick = { onUpdatePlan(plan.copy(stretches = plan.stretches.filterNot { it.id == stretch.id })) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove stretch", tint = c.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    OutlinedButton(
        onClick = {
            val nextIdx = plan.stretches.size + 1
            onUpdatePlan(plan.copy(stretches = plan.stretches + RoadStretchWidthItem(label = "Stretch $nextIdx", lengthMeters = 100.0, widthMeters = plan.effectiveWorkWidthMeters ?: 3.5)))
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("wp_add_stretch")
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(4.dp))
        Text("Add Additional Stretch / Lane", fontSize = 11.sp)
    }

    // Day / Night / Custom Shift Planning & Multiple Working Periods
    Text("DAY / NIGHT SHIFT & TIME-TO-TIME SCHEDULE", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WorkShiftType.entries.forEach { shift ->
            val selected = plan.shiftType == shift
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) c.accent else c.secondarySurface)
                    .clickable {
                        val baseDate = plan.workDateIso.ifBlank { "2026-11-25" }
                        val updated = when (shift) {
                            WorkShiftType.DAY_SHIFT -> plan.copy(
                                shiftType = shift,
                                shiftStartDateIso = baseDate,
                                shiftEndDateIso = baseDate,
                                plannedStartHour24 = 8,
                                plannedStartMinute = 0,
                                plannedEndHour24 = 17,
                                plannedEndMinute = 0,
                                breakDurationMinutes = 60,
                                workingPeriods = emptyList()
                            )
                            WorkShiftType.NIGHT_SHIFT -> plan.copy(
                                shiftType = shift,
                                shiftStartDateIso = baseDate,
                                shiftEndDateIso = ShiftTimingCalculator.nextIsoDate(baseDate),
                                plannedStartHour24 = 20,
                                plannedStartMinute = 0,
                                plannedEndHour24 = 6,
                                plannedEndMinute = 0,
                                breakDurationMinutes = 30,
                                workingPeriods = emptyList()
                            )
                            WorkShiftType.CUSTOM_SHIFT -> plan.copy(
                                shiftType = shift,
                                workingPeriods = plan.workingPeriods.ifEmpty {
                                    listOf(
                                        WorkingTimePeriod(startDateIso = baseDate, startHour24 = 8, endDateIso = baseDate, endHour24 = 12, label = "Morning"),
                                        WorkingTimePeriod(startDateIso = baseDate, startHour24 = 13, endDateIso = baseDate, endHour24 = 17, label = "Afternoon")
                                    )
                                }
                            )
                        }
                        onUpdatePlan(updated)
                    }
                    .padding(vertical = 8.dp)
                    .testTag("wp_shift_${shift.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(shift.label.uppercase(Locale.US), color = if (selected) c.onAccent else c.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (plan.shiftType != WorkShiftType.CUSTOM_SHIFT) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Start Hour (0-23)", plan.plannedStartHour24.toString(), { v ->
                v.toIntOrNull()?.coerceIn(0, 23)?.let { onUpdatePlan(plan.copy(plannedStartHour24 = it)) }
            }, Modifier.weight(1f), numeric = true)
            WpField("End Hour (0-23)", plan.plannedEndHour24.toString(), { v ->
                v.toIntOrNull()?.coerceIn(0, 23)?.let { onUpdatePlan(plan.copy(plannedEndHour24 = it)) }
            }, Modifier.weight(1f), numeric = true)
            WpField("Break (min)", plan.breakDurationMinutes.toString(), { v ->
                v.toIntOrNull()?.coerceIn(0, 360)?.let { onUpdatePlan(plan.copy(breakDurationMinutes = it)) }
            }, Modifier.weight(1f), numeric = true)
        }
    } else {
        plan.workingPeriods.forEachIndexed { idx, p ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                WpField("P${idx + 1} Start (h)", p.startHour24.toString(), { v ->
                    val h = v.toIntOrNull()?.coerceIn(0, 23) ?: return@WpField
                    val list = plan.workingPeriods.toMutableList()
                    list[idx] = p.copy(startHour24 = h)
                    onUpdatePlan(plan.copy(workingPeriods = list))
                }, Modifier.weight(1f), numeric = true)
                WpField("End (h)", p.endHour24.toString(), { v ->
                    val h = v.toIntOrNull()?.coerceIn(0, 23) ?: return@WpField
                    val list = plan.workingPeriods.toMutableList()
                    list[idx] = p.copy(endHour24 = h)
                    onUpdatePlan(plan.copy(workingPeriods = list))
                }, Modifier.weight(1f), numeric = true)
                IconButton(onClick = { onUpdatePlan(plan.copy(workingPeriods = plan.workingPeriods.filterNot { it.id == p.id })) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete period", tint = c.error, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedButton(
            onClick = {
                val baseDate = plan.workDateIso.ifBlank { "2026-11-25" }
                onUpdatePlan(plan.copy(workingPeriods = plan.workingPeriods + WorkingTimePeriod(startDateIso = baseDate, startHour24 = 14, endDateIso = baseDate, endHour24 = 18)))
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("wp_add_period")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text("Add Time-to-Time Working Period", fontSize = 11.sp)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Mobilisation (min)", plan.mobilisationMinutes.toString(), { v ->
            v.toIntOrNull()?.coerceIn(0, 240)?.let { onUpdatePlan(plan.copy(mobilisationMinutes = it)) }
        }, Modifier.weight(1f), numeric = true)
        WpField("Loading/Wait (min)", plan.loadingWaitingMinutes.toString(), { v ->
            v.toIntOrNull()?.coerceIn(0, 240)?.let { onUpdatePlan(plan.copy(loadingWaitingMinutes = it)) }
        }, Modifier.weight(1f), numeric = true)
    }

    // Shift Calculation Summary Pill
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.secondarySurface)
            .padding(10.dp)
            .testTag("wp_shift_summary"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "Total Shift: ${wpFmt(shiftTiming.totalShiftHours)}h · Break: ${shiftTiming.breakMinutes}m · Effective: ${wpFmt(shiftTiming.effectiveWorkingHours)}h",
            color = c.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Actual Net Work: ${wpFmt(shiftTiming.actualNetWorkingHours)}h · Completion: ${shiftTiming.estimatedCompletionTimeLabel}${if (shiftTiming.crossesMidnight) " (Overnight)" else ""}",
            color = c.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
internal fun Section2MaterialThicknessContent(
    plan: HighwayWorkPlan,
    calc: HighwayWorkPlanCalculation,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    var activitySearch by remember { mutableStateOf("") }
    val filteredActivities = remember(activitySearch) {
        val q = activitySearch.trim().lowercase(Locale.US)
        if (q.isEmpty()) PlanningWorkActivity.entries
        else PlanningWorkActivity.entries.filter { it.displayName.lowercase(Locale.US).contains(q) || it.name.lowercase(Locale.US).contains(q) }
    }

    // Searchable Activity Selector (23+ work categories)
    OutlinedTextField(
        value = activitySearch,
        onValueChange = { activitySearch = it },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp)) },
        placeholder = { Text("Search activity (BC, DBM, SMA, WMM, PQC…)", fontSize = 11.sp, color = c.textMuted) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = ctecsBorderlessInputColors(c.secondarySurface),
        modifier = Modifier.fillMaxWidth().testTag("wp_activity_search")
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items(filteredActivities, key = { it.name }) { act ->
            val active = plan.activity == act
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) c.accent else c.secondarySurface)
                    .clickable {
                        onUpdatePlan(
                            plan.copy(
                                activity = act,
                                layerThicknessMm = act.defaultThicknessMm.takeIf { it > 0.0 } ?: plan.layerThicknessMm
                            )
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("wp_act_${act.name.lowercase()}")
            ) {
                Text(act.displayName, color = if (active) c.onAccent else c.textPrimary, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
    if (plan.activity == PlanningWorkActivity.OTHER_HIGHWAY_WORKS) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Custom Activity", plan.customActivityName, { onUpdatePlan(plan.copy(customActivityName = it)) }, Modifier.weight(1f))
            WpField("Custom Material", plan.customMaterialType, { onUpdatePlan(plan.copy(customMaterialType = it)) }, Modifier.weight(1f))
        }
    }

    // Compacted & Loose Thickness (NEVER universal multiplier)
    Text("THICKNESS & COMPACTION", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    val ts = plan.thicknessSpec
    val recLoose = ts.recommendedLooseThicknessMm(plan.layerThicknessMm)
    val variation = ts.thicknessVariationMm(plan.layerThicknessMm)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Target Compacted (mm)", plan.layerThicknessMm?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(layerThicknessMm = it.toDoubleOrNull()))
        }, Modifier.weight(1f), numeric = true, tag = "wp_compacted_thickness")
        WpField("Approved Loose Factor", ts.approvedLooseToCompactedFactor?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(thicknessSpec = ts.copy(approvedLooseToCompactedFactor = it.toDoubleOrNull())))
        }, Modifier.weight(1f), numeric = true, placeholder = "Trial/JMF", tag = "wp_loose_factor")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Factor Source (JMF/Trial)", ts.looseFactorSource, {
            onUpdatePlan(plan.copy(thicknessSpec = ts.copy(looseFactorSource = it)))
        }, Modifier.weight(1f))
        WpField("Actual Loose (mm)", ts.actualLooseThicknessMm?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(thicknessSpec = ts.copy(actualLooseThicknessMm = it.toDoubleOrNull(), isManualLooseThickness = it.isNotBlank())))
        }, Modifier.weight(1f), numeric = true)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Achieved Compacted (mm)", ts.achievedCompactedThicknessMm?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(thicknessSpec = ts.copy(achievedCompactedThicknessMm = it.toDoubleOrNull())))
        }, Modifier.weight(1f), numeric = true)
        WpField("Verified Tolerance (±mm)", ts.verifiedToleranceMm?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(thicknessSpec = ts.copy(verifiedToleranceMm = it.toDoubleOrNull())))
        }, Modifier.weight(1f), numeric = true)
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.secondarySurface).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (recLoose != null) {
                "Recommended Loose Thickness: ${wpFmt(recLoose)} mm (Factor ${wpFmt(ts.approvedLooseToCompactedFactor!!)})"
            } else {
                "Recommended Loose: ${ThicknessCompactionSpec.VERIFICATION_REQUIRED_LABEL}"
            },
            color = if (recLoose != null) c.textPrimary else c.warning,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("wp_recommended_loose")
        )
        if (variation != null) {
            val withinTol = ts.isWithinVerifiedTolerance(plan.layerThicknessMm)
            Text(
                text = "Thickness Variation: ${if (variation >= 0) "+" else ""}${wpFmt(variation)} mm${withinTol?.let { if (it) " · Within Tolerance" else " · Exceeds Tolerance" } ?: " · Enter Verified Tolerance"}",
                color = when (withinTol) {
                    true -> c.success
                    false -> c.error
                    null -> c.textSecondary
                },
                fontSize = 11.sp
            )
        }
    }

    // Approved Density & Automatic Area / Volume / Quantity (Never invent density)
    Text("AREA, VOLUME & QUANTITY", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Approved Density (t/m³)", plan.materialDensityTPerM3?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(materialDensityTPerM3 = it.toDoubleOrNull()))
        }, Modifier.weight(1f), numeric = true, placeholder = "Per JMF", tag = "wp_density")
        WpField("Actual Quantity (t)", plan.actualQuantityDelivered?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(actualQuantityDelivered = it.toDoubleOrNull()))
        }, Modifier.weight(1f), numeric = true)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.secondarySurface).padding(10.dp).testTag("wp_quantity_box"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val qtyDiff = if (plan.actualQuantityDelivered != null && calc.estimatedMaterialTonnes != null) {
            plan.actualQuantityDelivered - calc.estimatedMaterialTonnes
        } else null
        Text("Area: ${calc.workAreaSqm?.let { "${wpFmt(it)} m²" } ?: "—"} · Volume: ${calc.materialVolumeM3?.let { "${wpFmt(it)} m³" } ?: "—"}", color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Est. Quantity: ${calc.estimatedMaterialTonnes?.let { "${wpFmt(it)} tonnes" } ?: "Enter Approved Density (JMF)"}${qtyDiff?.let { " · Diff: ${if (it >= 0) "+" else ""}${wpFmt(it)} t" } ?: ""}",
            color = if (calc.estimatedMaterialTonnes != null) c.accent else c.warning,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun Section3MachineryTransportContent(
    plan: HighwayWorkPlan,
    calc: HighwayWorkPlanCalculation,
    machineryCatalog: List<MachineryCatalogEntry>,
    onOpenMachineryPicker: () -> Unit,
    onAutoSuggestFleet: () -> Unit,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onOpenMachineryPicker,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            modifier = Modifier.weight(1f).testTag("wp_btn_add_machinery")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Add Machinery", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = onAutoSuggestFleet,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("wp_btn_suggest_fleet")
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text("Auto-Fill Fleet", fontSize = 11.sp)
        }
    }

    if (plan.machinery.isEmpty()) {
        Text("No machinery selected yet (${machineryCatalog.size} verified models available offline).", color = c.textMuted, fontSize = 11.sp)
    } else {
        plan.machinery.forEachIndexed { idx, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.secondarySurface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.catalogEntry.fullDisplayTitle, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${item.catalogEntry.category.shortLabel} · ${item.catalogEntry.availabilityStatus.label} · Max Rated: ${item.catalogEntry.formattedMaxRatedCapacity}",
                            color = c.textMuted,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { onUpdatePlan(plan.copy(machinery = plan.machinery.filterNot { it.instanceId == item.instanceId })) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove machine", tint = c.error, modifier = Modifier.size(18.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WpField("Qty", item.quantity.toString(), { v ->
                        val q = v.toIntOrNull()?.coerceIn(1, 50) ?: return@WpField
                        val next = plan.machinery.toMutableList()
                        next[idx] = item.copy(quantity = q)
                        onUpdatePlan(plan.copy(machinery = next))
                    }, Modifier.weight(0.6f), numeric = true)
                    WpField("Actual Site Cap (${item.actualOperatingCapacityUnit.ifBlank { "t/h" }})", item.actualOperatingCapacity?.let { wpFmt(it) } ?: "", { v ->
                        val next = plan.machinery.toMutableList()
                        next[idx] = item.copy(actualOperatingCapacity = v.toDoubleOrNull())
                        onUpdatePlan(plan.copy(machinery = next))
                    }, Modifier.weight(1.4f), numeric = true, placeholder = "Separate from Max Rated")
                }
            }
        }
    }

    // HMP-to-Work-Zone Transport Planning
    Text("HMP-TO-WORK-ZONE TRANSPORT (ROAD LEAD)", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    val trIn = plan.transport
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Road Lead (km)", trIn.roadLeadDistanceKm?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(transport = trIn.copy(roadLeadDistanceKm = it.toDoubleOrNull())))
        }, Modifier.weight(1f), numeric = true, tag = "wp_road_lead")
        WpField("Dumpers", trIn.dumperCount.takeIf { it > 0 }?.toString() ?: "", {
            onUpdatePlan(plan.copy(transport = trIn.copy(dumperCount = it.toIntOrNull() ?: 0)))
        }, Modifier.weight(0.8f), numeric = true, tag = "wp_dumper_count")
        WpField("Legal Payload (t)", trIn.actualDumperPayloadTonnes?.let { wpFmt(it) } ?: "", {
            onUpdatePlan(plan.copy(transport = trIn.copy(actualDumperPayloadTonnes = it.toDoubleOrNull())))
        }, Modifier.weight(1f), numeric = true, tag = "wp_dumper_payload")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Load (m)", wpFmt(trIn.loadingTimeMin), {
            onUpdatePlan(plan.copy(transport = trIn.copy(loadingTimeMin = it.toDoubleOrNull() ?: trIn.loadingTimeMin)))
        }, Modifier.weight(1f), numeric = true)
        WpField("Unload (m)", wpFmt(trIn.unloadingTimeMin), {
            onUpdatePlan(plan.copy(transport = trIn.copy(unloadingTimeMin = it.toDoubleOrNull() ?: trIn.unloadingTimeMin)))
        }, Modifier.weight(1f), numeric = true)
        WpField("Wait (m)", wpFmt(trIn.waitingQueueTimeMin), {
            onUpdatePlan(plan.copy(transport = trIn.copy(waitingQueueTimeMin = it.toDoubleOrNull() ?: trIn.waitingQueueTimeMin)))
        }, Modifier.weight(1f), numeric = true)
    }

    val tr = calc.transportResult
    val statusColor = when (tr.status) {
        TransportStatus.SUFFICIENT -> c.success
        TransportStatus.SHORTAGE -> c.error
        TransportStatus.INSUFFICIENT_DATA -> c.warning
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(statusColor.copy(alpha = 0.12f)).padding(10.dp).testTag("wp_transport_result"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "Transport Status: ${tr.status.label} · Supply: ${tr.estimatedSupplyTonnesPerHour?.let { "${wpFmt(it)} t/hr" } ?: "—"} · Req: ${tr.requiredDumpersForTarget ?: "—"} dumpers",
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Round Trip: ${tr.roundTripKm?.let { "${wpFmt(it)} km" } ?: "—"} · Cycle: ${tr.totalCycleTimeMin?.let { "${wpFmt(it)} min" } ?: "—"} · ${tr.statusDetail}",
            color = c.textSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun Section4TemperatureWeatherContent(
    plan: HighwayWorkPlan,
    calc: HighwayWorkPlanCalculation,
    settings: WorkPlanningSettings,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    val ts = plan.temperatureSpec
    val tempGuide = WorkPlanningStandardEngine.temperatureGuidanceForActivity(plan.activity, ts.mixOrBinderGrade)

    Text(tempGuide.categoryTitle.uppercase(Locale.US), color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    if (tempGuide.isBituminous) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Mix / Binder Grade", ts.mixOrBinderGrade, { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(mixOrBinderGrade = it))) }, Modifier.weight(1f))
            WpField("Binder Temp (°C)", ts.binderTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(binderTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Aggregate (°C)", ts.aggregateTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(aggregateTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
            WpField("Plant Mix (°C)", ts.plantProductionTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(plantProductionTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
            WpField("Dispatch (°C)", ts.dispatchTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(dispatchTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Arrival (°C)", ts.arrivalTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(arrivalTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
            WpField("Laying (°C)", ts.layingTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(layingTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
            WpField("Rolling (°C)", ts.actualRollingTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(actualRollingTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Material / App Temp (°C)", ts.layingTempC?.let { wpFmt(it) } ?: "", { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(layingTempC = it.toDoubleOrNull()))) }, Modifier.weight(1f), numeric = true)
            WpField("Measurement Time", ts.measurementTimeLabel, { onUpdatePlan(plan.copy(temperatureSpec = ts.copy(measurementTimeLabel = it))) }, Modifier.weight(1f), placeholder = "e.g. 09:30")
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.secondarySurface).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tempGuide.guidanceLines.forEach { Text("• $it", color = c.textSecondary, fontSize = 10.sp) }
        Text("Ref: ${tempGuide.referenceNote}", color = c.textMuted, fontSize = 9.5.sp)
    }

    // Shift-Specific Hourly Weather Recommendations
    Text("SHIFT HOURLY WEATHER (${plan.shiftType.label.uppercase(Locale.US)})", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("wp_shift_weather_row")) {
        items(calc.hourlySlots) { slot ->
            val stColor = when (slot.suitability) {
                WorkSuitabilityStatus.SUITABLE -> c.success
                WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> c.warning
                WorkSuitabilityStatus.NOT_RECOMMENDED -> c.error
                WorkSuitabilityStatus.INSUFFICIENT_DATA -> c.textMuted
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.secondarySurface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.timeLabel24, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(slot.suitability.label, color = stColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "${slot.temperatureC?.let { "${Math.round(it)}°C" } ?: "—"} · Rain ${slot.rainProbabilityPercent?.let { "$it%" } ?: "—"} · Wind ${slot.windSpeedKmh?.let { "${Math.round(it)} km/h" } ?: "—"}",
                    color = c.textSecondary,
                    fontSize = 10.sp
                )
                Text(slot.engineeringExplanation, color = c.textMuted, fontSize = 9.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    // MoRTH / IRC Contract-Aware Traceable References
    if (settings.showSourceReferences) {
        Text("MoRTH / IRC SPECIFICATION EDITION", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MoRTHRevision.entries.forEach { rev ->
                val active = plan.morthRevision == rev
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) c.accent else c.secondarySurface)
                        .clickable { onUpdatePlan(plan.copy(morthRevision = rev)) }
                        .padding(vertical = 7.dp, horizontal = 6.dp)
                        .testTag("wp_morth_${rev.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(rev.shortLabel, color = if (active) c.onAccent else c.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField("Approved JMF Ref", plan.jmfReference, { onUpdatePlan(plan.copy(jmfReference = it)) }, Modifier.weight(1f))
            WpField("Trial Section Ref", plan.trialSectionReference, { onUpdatePlan(plan.copy(trialSectionReference = it)) }, Modifier.weight(1f))
        }
        val refs = WorkPlanningStandardEngine.referencesForActivity(plan.activity, plan.morthRevision, plan.jmfReference, plan.trialSectionReference)
        refs.forEach { r ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(c.secondarySurface).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("${r.standardName} · ${r.clauseOrTableReference}", color = c.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("${r.editionOrRevision} · ${r.verificationStatus}", color = c.accent, fontSize = 9.5.sp)
                Text(r.requirementSummary, color = c.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

internal fun wpFmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
