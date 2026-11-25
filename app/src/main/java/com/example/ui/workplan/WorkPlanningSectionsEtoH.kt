package com.example.ui.workplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HighwayWorkPlan
import com.example.model.HighwayWorkPlanCalculation
import com.example.model.MachineryCategory
import com.example.model.ManpowerRoleAllocation
import com.example.model.ManpowerTeamGroup
import com.example.model.RollingCamberMode
import com.example.model.WorkPlanningSettings
import com.example.model.WorkPlanningStandardEngine
import com.example.model.WorkSuitabilityStatus
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.ctecsBorderlessInputColors
import com.example.weather.LocalBackupRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun Section5CompactionRollingContent(
    plan: HighwayWorkPlan,
    calc: HighwayWorkPlanCalculation,
    settings: WorkPlanningSettings,
    animationsEnabled: Boolean,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    val tandemMachine = plan.machinery.firstOrNull {
        it.catalogEntry.category == MachineryCategory.TANDEM_ROLLER ||
            it.catalogEntry.category == MachineryCategory.EARTHWORK_SUBGRADE
    }
    val ptrMachine = plan.machinery.firstOrNull {
        it.catalogEntry.category == MachineryCategory.PTR_ROLLER
    }
    val guidance = WorkPlanningStandardEngine.compactionGuidanceForActivity(
        activity = plan.activity,
        trialPassesEntered = plan.compaction.requiredPasses,
        tandemModel = tandemMachine?.catalogEntry?.fullDisplayTitle,
        ptrModel = ptrMachine?.catalogEntry?.fullDisplayTitle
    )

    val cp = plan.compaction
    val compRes = calc.compactionResult

    // PTR / Roller Suitability Status Pill
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background((if (guidance.isPtrRecommended) c.accent else c.warning).copy(alpha = 0.12f))
            .padding(10.dp)
            .testTag("wp_ptr_guidance_box"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = if (guidance.isPtrRecommended) "PTR SUITABILITY: RECOMMENDED (JMF/TRIAL)" else "PTR SUITABILITY: NOT PRESCRIBED / VERIFY SPEC",
            color = if (guidance.isPtrRecommended) c.accent else c.warning,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(guidance.ptrSuitabilityNote, color = c.textSecondary, fontSize = 11.sp)
    }

    if (guidance.isCompactionApplicable) {
        // Editable Rolling Parameters
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField(
                label = "JMF Trial Passes",
                value = cp.requiredPasses?.toString() ?: "",
                onValueChange = { onUpdatePlan(plan.copy(compaction = cp.copy(requiredPasses = it.toIntOrNull()?.takeIf { p -> p > 0 }))) },
                modifier = Modifier.weight(1f),
                numeric = true,
                placeholder = "Per trial",
                tag = "wp_comp_passes"
            )
            WpField(
                label = "Drum Width (m)",
                value = cp.tandemRollerDrumWidthM?.let { wpFmt(it) } ?: "",
                onValueChange = { onUpdatePlan(plan.copy(compaction = cp.copy(tandemRollerDrumWidthM = it.toDoubleOrNull()))) },
                modifier = Modifier.weight(1f),
                numeric = true,
                placeholder = "e.g. 1.7",
                tag = "wp_comp_drum"
            )
            WpField(
                label = "Overlap (m)",
                value = wpFmt(cp.overlapM),
                onValueChange = { onUpdatePlan(plan.copy(compaction = cp.copy(overlapM = it.toDoubleOrNull()?.coerceIn(0.05, 0.8) ?: cp.overlapM))) },
                modifier = Modifier.weight(0.9f),
                numeric = true
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WpField(
                label = "Roller Speed (km/h)",
                value = wpFmt(cp.rollerSpeedKmh),
                onValueChange = { onUpdatePlan(plan.copy(compaction = cp.copy(rollerSpeedKmh = it.toDoubleOrNull()?.coerceIn(0.5, 10.0) ?: cp.rollerSpeedKmh))) },
                modifier = Modifier.weight(1f),
                numeric = true
            )
            WpField(
                label = "Tandem Rollers",
                value = cp.tandemRollerCount.takeIf { it > 0 }?.toString() ?: "",
                onValueChange = { onUpdatePlan(plan.copy(compaction = cp.copy(tandemRollerCount = it.toIntOrNull()?.coerceAtLeast(0) ?: 0))) },
                modifier = Modifier.weight(0.9f),
                numeric = true
            )
            WpField(
                label = "PTR Count",
                value = cp.ptrCount.takeIf { it > 0 }?.toString() ?: "",
                onValueChange = { onUpdatePlan(plan.copy(compaction = cp.copy(ptrCount = it.toIntOrNull()?.coerceAtLeast(0) ?: 0))) },
                modifier = Modifier.weight(0.8f),
                numeric = true
            )
        }

        // Camber / Superelevation Sequence Selector
        Text("CAMBER / SUPERELEVATION ROLLING SEQUENCE", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            items(RollingCamberMode.entries) { mode ->
                val active = plan.camberMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) c.accent else c.secondarySurface)
                        .clickable { onUpdatePlan(plan.copy(camberMode = mode)) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("wp_camber_${mode.name.lowercase()}")
                ) {
                    Text(mode.label, color = if (active) c.onAccent else c.textPrimary, fontSize = 10.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        // Animated Top-View Rolling Pattern Diagram
        if (settings.showRollingDiagram) {
            val roadW = plan.effectiveWorkWidthMeters ?: cp.paverWorkingWidthM ?: 7.0
            val drumW = cp.tandemRollerDrumWidthM ?: tandemMachine?.catalogEntry?.workingWidthM ?: 1.68
            val passLabel = cp.requiredPasses?.let { "$it JMF passes" } ?: "Provisional passes"
            RollingPatternDiagram(
                roadWidthM = roadW,
                rollerDrumWidthM = drumW,
                overlapM = cp.overlapM,
                camberMode = plan.camberMode,
                passesLabel = passLabel,
                animationsEnabled = animationsEnabled
            )
        }

        // Breakdown / Intermediate / Finish Rolling Stages
        if (settings.showCompactionRecommendations && guidance.stages.isNotEmpty()) {
            Text("ROLLING STAGES (${plan.effectiveActivityName.uppercase(Locale.US)})", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            guidance.stages.forEach { stage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.secondarySurface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stage.stageTitle, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (stage.isProvisional) "PROVISIONAL" else "TRIAL VERIFIED",
                            color = if (stage.isProvisional) c.warning else c.success,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("Roller: ${stage.rollerType} · Mode: ${stage.operatingMode}", color = c.textSecondary, fontSize = 10.5.sp)
                    Text("Sequence: ${stage.rollingSequence}", color = c.textMuted, fontSize = 10.sp)
                    Text("Passes: ${stage.recommendedPassesText}", color = c.accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Compaction Capacity Summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background((if (compRes.hasCompactionBottleneck) c.error else c.secondarySurface).copy(alpha = if (compRes.hasCompactionBottleneck) 0.12f else 1f))
                .padding(10.dp)
                .testTag("wp_compaction_summary"),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "Coverage: ${compRes.totalRollingCapacitySqmPerHour?.let { "${wpFmt(it)} m²/hr" } ?: "—"} · Capacity: ${compRes.totalRollingCapacityTonnesPerHour?.let { "${wpFmt(it)} t/hr" } ?: "—"}",
                color = if (compRes.hasCompactionBottleneck) c.error else c.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(compRes.advisoryNote, color = c.textSecondary, fontSize = 10.5.sp)
            Text(guidance.densityVerificationReminder, color = c.warning, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    } else {
        Text(guidance.densityVerificationReminder, color = c.textSecondary, fontSize = 11.sp)
    }
}

@Composable
internal fun Section6ManpowerContent(
    plan: HighwayWorkPlan,
    calc: HighwayWorkPlanCalculation,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    var selectedTeam by remember { mutableStateOf(ManpowerTeamGroup.PAVING_TEAM) }
    var customRoleName by remember { mutableStateOf("") }

    val mpSummary = calc.manpowerSummary

    // Shortage summary banner
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background((if (mpSummary.totalShortage > 0) c.warning else c.success).copy(alpha = 0.12f))
            .padding(10.dp)
            .testTag("wp_manpower_summary"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Required: ${mpSummary.totalRequired} · Available: ${mpSummary.totalAvailable} · Deployed: ${mpSummary.totalDeployed} · Shortage: ${mpSummary.totalShortage}",
            color = if (mpSummary.totalShortage > 0) c.warning else c.success,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
        if (mpSummary.affectedActivities.isNotEmpty()) {
            Text(
                text = "Affected: ${mpSummary.affectedActivities.joinToString(", ")}",
                color = c.textSecondary,
                fontSize = 10.5.sp
            )
        }
    }

    // Team selector tabs
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items(ManpowerTeamGroup.entries) { team ->
            val active = team == selectedTeam
            val teamShortage = plan.manpower.filter { it.team == team }.sumOf { it.shortageCount }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) c.accent else c.secondarySurface)
                    .clickable { selectedTeam = team }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("wp_team_${team.name.lowercase()}")
            ) {
                Text(
                    text = if (teamShortage > 0) "${team.label} (-$teamShortage)" else team.label,
                    color = if (active) c.onAccent else c.textPrimary,
                    fontSize = 10.5.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }

    val teamRoles = plan.manpower.filter { it.team == selectedTeam }
    teamRoles.forEach { role ->
        val shortage = role.shortageCount
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.secondarySurface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(role.roleName, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (shortage > 0) {
                    Text("Short -$shortage", color = c.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { onUpdatePlan(plan.copy(manpower = plan.manpower.filterNot { it.id == role.id })) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ${role.roleName}", tint = c.textMuted, modifier = Modifier.size(16.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WpField("Req", role.requiredCount.toString(), { v ->
                    val n = v.toIntOrNull()?.coerceIn(0, 999) ?: 0
                    onUpdatePlan(plan.copy(manpower = plan.manpower.map { if (it.id == role.id) it.copy(requiredCount = n) else it }))
                }, Modifier.weight(1f), numeric = true)
                WpField("Avail", role.availableCount.toString(), { v ->
                    val n = v.toIntOrNull()?.coerceIn(0, 999) ?: 0
                    onUpdatePlan(plan.copy(manpower = plan.manpower.map { if (it.id == role.id) it.copy(availableCount = n) else it }))
                }, Modifier.weight(1f), numeric = true)
                WpField("Deploy", role.deployedCount.toString(), { v ->
                    val n = v.toIntOrNull()?.coerceIn(0, 999) ?: 0
                    onUpdatePlan(plan.copy(manpower = plan.manpower.map { if (it.id == role.id) it.copy(deployedCount = n) else it }))
                }, Modifier.weight(1f), numeric = true)
            }
        }
    }

    // Add Custom Role row
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WpField(
            label = "Add Custom Role (${selectedTeam.label})",
            value = customRoleName,
            onValueChange = { customRoleName = it },
            modifier = Modifier.weight(1f),
            tag = "wp_custom_role_input"
        )
        Button(
            onClick = {
                val trimmed = customRoleName.trim()
                if (trimmed.isNotEmpty()) {
                    val newRole = ManpowerRoleAllocation(
                        team = selectedTeam,
                        roleName = trimmed,
                        requiredCount = 1,
                        availableCount = 1,
                        deployedCount = 1,
                        affectedActivityHint = selectedTeam.label
                    )
                    onUpdatePlan(plan.copy(manpower = plan.manpower + newRole))
                    customRoleName = ""
                }
            },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            modifier = Modifier.testTag("wp_add_custom_role_btn")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add role", modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(2.dp))
            Text("Add", fontSize = 11.sp)
        }
    }
}

@Composable
internal fun Section7ProductionSummaryContent(
    plan: HighwayWorkPlan,
    calc: HighwayWorkPlanCalculation,
    settings: WorkPlanningSettings,
    onUpdatePlan: (HighwayWorkPlan) -> Unit
) {
    val c = CtecsTheme.colors
    val suitabilityColor = when (calc.overallWeatherSuitability) {
        WorkSuitabilityStatus.SUITABLE -> c.success
        WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> c.warning
        WorkSuitabilityStatus.NOT_RECOMMENDED -> c.error
        WorkSuitabilityStatus.INSUFFICIENT_DATA -> c.textMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.secondarySurface)
            .padding(12.dp)
            .testTag("wp_production_summary_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ESTIMATED PLANNING VALUES", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Text(calc.overallWeatherSuitability.label, color = suitabilityColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
        SummaryMetricLine("Work Area / Volume", "${calc.workAreaSqm?.let { "${wpFmt(it)} m²" } ?: "—"} · ${calc.materialVolumeM3?.let { "${wpFmt(it)} m³" } ?: "—"}")
        SummaryMetricLine("Estimated Material Qty", calc.estimatedMaterialTonnes?.let { "${wpFmt(it)} tonnes" } ?: "Enter Approved Density (JMF)")
        SummaryMetricLine("Effective Hourly Output", calc.effectiveHourlyProduction?.let { "${wpFmt(it)} ${calc.productionUnit}/hr" } ?: "Enter actual equipment/transport capacities")
        SummaryMetricLine("Estimated Shift Output", calc.estimatedDailyProduction?.let { "${wpFmt(it)} ${calc.productionUnit} (${wpFmt(plan.effectiveWorkingHoursValue)}h)" } ?: "—")
        SummaryMetricLine("Expected Completion Time", calc.expectedCompletionHours?.let { "${wpFmt(it)} hrs (Est. End: ${plan.shiftTimingResult.estimatedCompletionTimeLabel})" } ?: "—")
        SummaryMetricLine("Limiting Bottleneck", calc.limitingBottleneck, valueColor = c.warning)
        if (settings.showCalculationDetails) {
            SummaryMetricLine("Plant / Paver / Transport", "HMP ${calc.hmpCapacityTonnesPerHour?.let { "${wpFmt(it)}t/h" } ?: "—"} · Paver ${calc.paverCapacityTonnesPerHour?.let { "${wpFmt(it)}t/h" } ?: "—"} · Haul ${calc.transportResult.estimatedSupplyTonnesPerHour?.let { "${wpFmt(it)}t/h" } ?: "—"}")
        }
        SummaryMetricLine("Best Working Window", calc.bestWorkingWindowText, valueColor = suitabilityColor)
    }

    // Dynamic Context-Aware Engineering Recommendation
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.accent.copy(alpha = if (c.isDark) 0.14f else 0.09f))
            .padding(10.dp)
            .testTag("wp_dynamic_recommendation"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(calc.dynamicRecommendation, color = c.textPrimary, fontSize = 11.5.sp, lineHeight = 16.sp)
    }

    // Report Approval Metadata
    Text("APPROVAL & REPORT SIGN-OFF", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpField("Prepared By", plan.preparedBy, { onUpdatePlan(plan.copy(preparedBy = it)) }, Modifier.weight(1f))
        WpField("Checked By", plan.checkedBy, { onUpdatePlan(plan.copy(checkedBy = it)) }, Modifier.weight(1f))
        WpField("Approved By", plan.approvedBy, { onUpdatePlan(plan.copy(approvedBy = it)) }, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryMetricLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = c.textMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
        Text(
            text = value,
            color = valueColor ?: c.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun Section8SavedPlansBackupContent(
    activePlanId: String,
    savedPlans: List<HighwayWorkPlan>,
    localBackups: List<LocalBackupRecord>,
    onOpenPlan: (HighwayWorkPlan) -> Unit,
    onDuplicatePlan: (HighwayWorkPlan) -> Unit,
    onRenamePlan: (String, String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onExportBackupJson: () -> Unit,
    onImportBackupJson: () -> Unit,
    onSaveLocalSnapshot: () -> Unit,
    onSafeRestoreLocalBackup: (LocalBackupRecord) -> Unit
) {
    val c = CtecsTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var renamingPlan by remember { mutableStateOf<HighwayWorkPlan?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var confirmingRestore by remember { mutableStateOf<LocalBackupRecord?>(null) }

    val filteredPlans = remember(savedPlans, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.US)
        if (q.isEmpty()) savedPlans
        else savedPlans.filter { p ->
            p.effectivePlanTitle.lowercase(Locale.US).contains(q) ||
                p.projectName.lowercase(Locale.US).contains(q) ||
                p.workDateIso.lowercase(Locale.US).contains(q) ||
                p.effectiveActivityName.lowercase(Locale.US).contains(q) ||
                p.startChainage.lowercase(Locale.US).contains(q) ||
                p.endChainage.lowercase(Locale.US).contains(q) ||
                p.shiftType.label.lowercase(Locale.US).contains(q)
        }
    }

    Text("MY WORK PLANS (${savedPlans.size})", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp)) },
        placeholder = { Text("Search by project, date, activity, chainage, shift…", fontSize = 11.sp, color = c.textMuted) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = ctecsBorderlessInputColors(c.secondarySurface),
        modifier = Modifier.fillMaxWidth().testTag("wp_saved_plans_search")
    )

    if (filteredPlans.isEmpty()) {
        Text(
            text = if (savedPlans.isEmpty()) "No saved work plans yet. Tap Save Plan to store your work offline." else "No saved plans match \"$searchQuery\".",
            color = c.textMuted,
            fontSize = 11.sp
        )
    } else {
        filteredPlans.forEach { sp ->
            val isCurrent = sp.planId == activePlanId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isCurrent) c.accent.copy(alpha = if (c.isDark) 0.16f else 0.10f) else c.secondarySurface)
                    .padding(10.dp)
                    .testTag("wp_saved_plan_card_${sp.planId}"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sp.effectivePlanTitle, color = c.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${sp.effectiveActivityName} · ${sp.shiftType.label} · ${sp.workDateIso.ifBlank { "No Date" }}",
                            color = c.textSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sp.startChainage.isNotBlank() || sp.projectName.isNotBlank()) {
                            Text(
                                text = listOf(sp.projectName, sp.formattedLocation).filter { it.isNotBlank() }.joinToString(" · "),
                                color = c.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (isCurrent) {
                        Text("ACTIVE", color = c.accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onOpenPlan(sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("wp_open_plan_${sp.planId}")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("Open", fontSize = 10.5.sp)
                    }
                    IconButton(
                        onClick = { onDuplicatePlan(sp) },
                        modifier = Modifier.size(40.dp).testTag("wp_dup_plan_${sp.planId}")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate plan", tint = c.textSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = {
                            renamingPlan = sp
                            renameDraft = sp.effectivePlanTitle
                        },
                        modifier = Modifier.size(40.dp).testTag("wp_rename_plan_${sp.planId}")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename plan", tint = c.textSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { onDeletePlan(sp.planId) },
                        modifier = Modifier.size(40.dp).testTag("wp_delete_plan_${sp.planId}")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete plan", tint = c.error, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }

    // Backup, Export, Import & Safe Restore
    Text("BACKUP, EXPORT & SAFE RESTORE", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onExportBackupJson,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            modifier = Modifier.weight(1f).testTag("wp_btn_export_backup")
        ) {
            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text("Export JSON", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = onImportBackupJson,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("wp_btn_import_backup")
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text("Import JSON", fontSize = 11.sp)
        }
    }
    OutlinedButton(
        onClick = onSaveLocalSnapshot,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("wp_btn_local_snapshot")
    ) {
        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(4.dp))
        Text("Create Safe Local Backup Snapshot", fontSize = 11.sp)
    }

    if (localBackups.isNotEmpty()) {
        val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }
        localBackups.forEach { rec ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.secondarySurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rec.fileName, color = c.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${rec.planCount} plan(s) · ${dateFmt.format(Date(rec.createdAtMs))}", color = c.textMuted, fontSize = 10.sp)
                }
                TextButton(onClick = { confirmingRestore = rec }) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(3.dp))
                    Text("Restore", fontSize = 11.sp)
                }
            }
        }
    }

    renamingPlan?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingPlan = null },
            title = { Text("Rename Work Plan", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                WpField("Plan Title", renameDraft, { renameDraft = it }, Modifier.fillMaxWidth(), tag = "wp_rename_input")
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = renameDraft.trim()
                    if (trimmed.isNotEmpty()) onRenamePlan(target.planId, trimmed)
                    renamingPlan = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPlan = null }) { Text("Cancel") }
            }
        )
    }

    confirmingRestore?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmingRestore = null },
            title = { Text("Safe Restore Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "An automatic safety backup of your current work plans will be saved before restoring ${rec.fileName} (${rec.planCount} plan(s)). Continue?",
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSafeRestoreLocalBackup(rec)
                    confirmingRestore = null
                }) { Text("Restore Safely") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRestore = null }) { Text("Cancel") }
            }
        )
    }
}
