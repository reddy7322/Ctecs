package com.example.siteregister.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siteregister.engine.HighwayMeasurementEngine
import com.example.siteregister.model.BcLayingEntry
import com.example.siteregister.model.EditRequestStatus
import com.example.siteregister.model.GlassGridEntry
import com.example.siteregister.model.HighwayActivityCatalog
import com.example.siteregister.model.HighwayActivityCategory
import com.example.siteregister.model.HighwayActivityTemplate
import com.example.siteregister.model.MeasurementMethod
import com.example.siteregister.model.MeasurementUnit
import com.example.siteregister.model.ObservationCategory
import com.example.siteregister.model.ObservationSeverity
import com.example.siteregister.model.QualityTestCategory
import com.example.siteregister.model.QualityTestReportRecord
import com.example.siteregister.model.RectificationStatus
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.model.SiteObservationRecord
import com.example.siteregister.model.SiteSegmentRecord
import com.example.siteregister.model.TackCoatTestRecord
import com.example.siteregister.model.TackCoatTrayRow
import com.example.siteregister.model.TestResultStatus
import com.example.ui.theme.CtecsTheme
import java.util.Locale

// ============================================================================
// 1. ALL 41 HIGHWAY ACTIVITIES PICKER + NEW REGISTER FORM
// ============================================================================

@Composable
fun SiteRegisterActivityPickerScreen(
    state: SiteRegisterUiState,
    defaultChainage: String,
    defaultSide: String,
    onCreateRegister: (
        template: HighwayActivityTemplate,
        workDate: String,
        shift: String,
        side: String,
        lane: String,
        chainageFrom: String,
        chainageTo: String,
        remarks: String
    ) -> Unit,
    onAddCustomActivity: (String, MeasurementUnit, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val c = CtecsTheme.colors
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<HighwayActivityCategory?>(null) }
    var chosenTemplate by remember { mutableStateOf<HighwayActivityTemplate?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val filtered = remember(state.allActivities, search, selectedCategory) {
        state.allActivities.filter { act ->
            (selectedCategory == null || act.category == selectedCategory) &&
                (search.isBlank() || act.name.contains(search, true) || act.code.contains(search, true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "Select Highway Activity",
            subtitle = "${state.allActivities.size} Standard & Custom Templates",
            onBack = onBack,
            actions = {
                if (state.activeMembership?.assignedRole?.canManageProjectAndUsers == true) {
                    SrPillChip("Custom +", selected = false, onClick = { showCustomDialog = true })
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SrTextField(search, { search = it }, "Search all 41 activities (BC, DBM, Tack Coat, GSB, PQC, Bridge...)")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SrPillChip("All (${state.allActivities.size})", selectedCategory == null, { selectedCategory = null })
                HighwayActivityCategory.entries.forEach { cat ->
                    SrPillChip(
                        label = cat.displayLabel.substringAfter(". "),
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.code }) { act ->
                SrSurfaceCard(
                    modifier = Modifier.clickable { chosenTemplate = act }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(act.name, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(act.category.displayLabel, color = c.textSecondary, fontSize = 11.5.sp)
                        }
                        SrStatusBadge(act.primaryUnit.symbol, c.accent)
                    }
                    if (act.specificFields.isNotEmpty()) {
                        Text(
                            text = act.specificFields.take(4).joinToString(" • "),
                            color = c.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    chosenTemplate?.let { tpl ->
        var workDate by remember { mutableStateOf(srTodayIsoDate()) }
        var shift by remember { mutableStateOf(state.settings.defaultShift) }
        var side by remember { mutableStateOf(defaultSide.ifBlank { "RHS" }) }
        var lane by remember { mutableStateOf(state.settings.defaultLane) }
        var chFrom by remember { mutableStateOf(defaultChainage) }
        var chTo by remember { mutableStateOf(defaultChainage) }
        var remarks by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { chosenTemplate = null },
            containerColor = c.surface,
            title = { Text("New ${tpl.name}", color = c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SrTextField(workDate, { workDate = it }, "Work Date (YYYY-MM-DD)")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SrPillChip("DAY", shift == "DAY", { shift = "DAY" })
                        SrPillChip("NIGHT", shift == "NIGHT", { shift = "NIGHT" })
                        SrPillChip("LHS", side == "LHS", { side = "LHS" })
                        SrPillChip("RHS", side == "RHS", { side = "RHS" })
                    }
                    SrTextField(lane, { lane = it }, "Lane (MCW / Service Road / Shoulder)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SrTextField(chFrom, { chFrom = it }, "From CH", modifier = Modifier.weight(1f))
                        SrTextField(chTo, { chTo = it }, "To CH", modifier = Modifier.weight(1f))
                    }
                    SrTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = tpl
                    chosenTemplate = null
                    onCreateRegister(selected, workDate, shift, side, lane, chFrom, chTo, remarks)
                }) { Text("Create Draft", color = c.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { chosenTemplate = null }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }

    if (showCustomDialog) {
        var name by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf(MeasurementUnit.SQM) }
        var volApp by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor = c.surface,
            title = { Text("Add Custom Project Activity", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(name, { name = it }, "Activity Name")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MeasurementUnit.M, MeasurementUnit.SQM, MeasurementUnit.CUM, MeasurementUnit.MT).forEach { u ->
                            SrPillChip(u.symbol, unit == u, { unit = u })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = volApp, onCheckedChange = { volApp = it })
                        Text("Volume Calculation Applicable", color = c.textPrimary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onAddCustomActivity(name, unit, volApp)
                    showCustomDialog = false
                }) { Text("Add Template", color = c.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }
}

// ============================================================================
// 2. REGISTER DETAIL WORKSPACE (MEASURE / BC+GRID / TESTS / OBS / SIGN+GOV)
// ============================================================================

@Composable
fun SiteRegisterDetailScreen(
    state: SiteRegisterUiState,
    vm: SiteRegisterViewModel,
    recentCtecsPhotoUris: List<String>,
    onLaunchCtecsCamera: () -> Unit,
    onBack: () -> Unit
) {
    val c = CtecsTheme.colors
    val reg = state.activeRegister
    if (reg == null) {
        onBack()
        return
    }
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Measure", "BC / Grid", "Tests", "Obs", "Sign & PMC")
    val template = remember(reg.activityCode, state.settings.customActivities) {
        HighwayActivityCatalog.findByCode(reg.activityCode, state.settings.customActivities)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = reg.activityName,
            subtitle = "${reg.registerNumber} • Rev ${reg.currentRevision} • ${reg.workDate}",
            onBack = onBack,
            actions = {
                if (state.canDeleteActiveRegister) {
                    IconButton(onClick = { vm.deleteActiveDraftRegister() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Draft", tint = c.error)
                    }
                }
            }
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError) { vm.clearBanner() }

        // Read-Only / Lock Banner
        if (state.sharedViewRegisterId == reg.id) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accent.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                Text(
                    "LIVE Shared Read-Only View (${state.sharedViewCodeLabel.orEmpty()}) • Editing & Approval Disabled",
                    color = c.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (reg.status == RegisterStatus.APPROVED_LOCKED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.success.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = c.success, modifier = Modifier.size(15.dp))
                Text(
                    "Approved – Locked (Rev ${reg.currentRevision}) • Approved by ${reg.approvedByName ?: "PMC"}",
                    color = c.success,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Compact Segmented Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                SrPillChip(label = label, selected = activeTab == idx, onClick = { activeTab = idx })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (activeTab) {
                0 -> MeasurementSegmentsTab(state, reg, template, vm)
                1 -> BcAndGlassGridTab(state, reg, vm)
                2 -> TestsAndOcrTab(state, vm, recentCtecsPhotoUris, onLaunchCtecsCamera)
                3 -> ObservationsInlineTab(state, vm, recentCtecsPhotoUris, onLaunchCtecsCamera)
                4 -> SignaturesAndPmcGovernanceTab(state, reg, vm)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MeasurementSegmentsTab(
    state: SiteRegisterUiState,
    reg: com.example.siteregister.model.SiteRegisterRecord,
    template: HighwayActivityTemplate,
    vm: SiteRegisterViewModel
) {
    val c = CtecsTheme.colors
    val summary = remember(state.activeRegisterSegments, template.volumeApplicable) {
        HighwayMeasurementEngine.calculateSegmentsWithOverlapGuard(
            segments = state.activeRegisterSegments,
            volumeApplicable = template.volumeApplicable
        )
    }

    // Totals summary card
    SrSurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Deduplicated Totals", color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            SrStatusBadge(reg.quantityClassification.badgeLabel, c.accent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Length: ${String.format(Locale.US, "%.2f", summary.deduplicatedLengthM)} m", color = c.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Area: ${String.format(Locale.US, "%.2f", summary.totalAreaSqm)} m²", color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            if (template.volumeApplicable) {
                Text("Vol: ${String.format(Locale.US, "%.3f", summary.totalVolumeCum)} m³", color = c.success, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (summary.hasOverlaps) {
            summary.overlapWarnings.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(16.dp))
                    Text(w, color = c.warning, fontSize = 11.5.sp)
                }
            }
        }
    }

    // Add Segment Form (enabled ONLY when editable)
    if (state.canEditActiveRegister) {
        var fromCh by remember { mutableStateOf(reg.chainageFrom) }
        var toCh by remember { mutableStateOf(reg.chainageTo) }
        var method by remember { mutableStateOf(if (template.supportsCrossSection) MeasurementMethod.AVERAGE_END_AREA else MeasurementMethod.UNIFORM_SECTION) }
        var wStart by remember { mutableStateOf("") }
        var wMid by remember { mutableStateOf("") }
        var wEnd by remember { mutableStateOf("") }
        var designD by remember { mutableStateOf("") }
        var looseD by remember { mutableStateOf("") }
        var compactedD by remember { mutableStateOf("") }
        var verifiedD by remember { mutableStateOf("") }
        var depthUnit by remember { mutableStateOf(template.defaultDepthUnit) }
        var area1 by remember { mutableStateOf("") }
        var area2 by remember { mutableStateOf("") }
        var surveyedArea by remember { mutableStateOf("") }
        var verifiedDensity by remember { mutableStateOf("") }
        var remarks by remember { mutableStateOf("") }

        SrSurfaceCard {
            SrSectionHeader("ADD MEASUREMENT SEGMENT", "Explicit Units Required")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MeasurementMethod.entries.forEach { m ->
                    SrPillChip(m.displayLabel.substringBefore(" ("), method == m, { method = m })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrTextField(fromCh, { fromCh = it }, "From CH (e.g. 201+000)", modifier = Modifier.weight(1f))
                SrTextField(toCh, { toCh = it }, "To CH (e.g. 201+100)", modifier = Modifier.weight(1f))
            }
            if (method == MeasurementMethod.AVERAGE_END_AREA) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(area1, { area1 = it }, "CS Area A₁ (m²)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(area2, { area2 = it }, "CS Area A₂ (m²)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            } else if (method == MeasurementMethod.IRREGULAR_SURVEYED) {
                SrTextField(surveyedArea, { surveyedArea = it }, "Surveyed Area (m²)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrTextField(wStart, { wStart = it }, "W_start (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(wMid, { wMid = it }, "W_mid (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(wEnd, { wEnd = it }, "W_end (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            }

            if (template.volumeApplicable) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Depth Unit:", color = c.textSecondary, fontSize = 11.5.sp)
                    listOf(MeasurementUnit.MM, MeasurementUnit.CM, MeasurementUnit.M).forEach { u ->
                        SrPillChip(u.symbol, depthUnit == u, { depthUnit = u })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrTextField(designD, { designD = it }, "Design (${depthUnit.symbol})", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(looseD, { looseD = it }, "Loose (${depthUnit.symbol})", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrTextField(compactedD, { compactedD = it }, "Actual Compacted", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(verifiedD, { verifiedD = it }, "Verified (PMC/Lab)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                SrTextField(
                    verifiedDensity,
                    { verifiedDensity = it },
                    "Verified Project Density (MT/m³ – optional, never auto-invented)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            SrTextField(remarks, { remarks = it }, "Segment Remarks")
            SrPrimaryButton(
                text = "Add Measured Segment",
                icon = Icons.Default.Add,
                onClick = {
                    vm.addSegmentToActiveRegister(
                        SiteSegmentRecord(
                            registerId = reg.id,
                            revisionNumber = reg.currentRevision,
                            fromChainage = fromCh,
                            toChainage = toCh,
                            side = reg.side,
                            lane = reg.lane,
                            startWidthM = wStart.toDoubleOrNull(),
                            middleWidthM = wMid.toDoubleOrNull(),
                            endWidthM = wEnd.toDoubleOrNull(),
                            designThickness = designD.toDoubleOrNull(),
                            looseThickness = looseD.toDoubleOrNull(),
                            actualCompactedThickness = compactedD.toDoubleOrNull(),
                            verifiedThickness = verifiedD.toDoubleOrNull(),
                            thicknessUnit = depthUnit,
                            verifiedDensity = verifiedDensity.toDoubleOrNull(),
                            densityUnit = if (verifiedDensity.toDoubleOrNull() != null) "MT/m³" else null,
                            measurementMethod = method,
                            crossSectionArea1Sqm = area1.toDoubleOrNull(),
                            crossSectionArea2Sqm = area2.toDoubleOrNull(),
                            surveyedAreaSqm = surveyedArea.toDoubleOrNull(),
                            remarks = remarks
                        )
                    )
                    wStart = ""; wMid = ""; wEnd = ""; compactedD = ""; verifiedD = ""; remarks = ""
                },
                modifier = Modifier.fillMaxWidth(),
                testTagName = "sr_add_segment_btn"
            )
        }
    }

    // Existing Segments List with Formula Transparency
    state.activeRegisterSegments.zip(summary.segmentResults).forEach { (seg, res) ->
        SrSurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Seg #${seg.segmentIndex}: ${seg.fromChainage} → ${seg.toChainage}", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                SrStatusBadge(res.classification.badgeLabel, if (res.hasOverlapWarning) c.warning else c.accent)
            }
            Text(
                text = buildString {
                    append("L=${String.format(Locale.US, "%.2f", res.lengthM)}m")
                    res.averageWidthM?.let { append(" • W_avg=${String.format(Locale.US, "%.2f", it)}m") }
                    append(" • Area=${String.format(Locale.US, "%.2f", res.areaSqm)}m²")
                    res.volumeCum?.let { append(" • Vol=${String.format(Locale.US, "%.3f", it)}m³") }
                    res.materialQuantity?.let { append(" • Est.Mat=${String.format(Locale.US, "%.2f", it)}MT") }
                },
                color = c.textPrimary,
                fontSize = 12.sp
            )
            res.varianceThickness?.let { v ->
                Text(
                    "Design vs Actual Variance: ${String.format(Locale.US, "%+.2f", v.variance)} ${seg.thicknessUnit.symbol}" +
                        (v.variancePercent?.let { " (${String.format(Locale.US, "%+.1f%%", it)})" } ?: ""),
                    color = c.accentSecondary,
                    fontSize = 11.5.sp
                )
            }
            res.formulaBreakdowns.forEach { fb ->
                Text("${fb.formula}  [${fb.inputsSummary}]", color = c.textMuted, fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun BcAndGlassGridTab(
    state: SiteRegisterUiState,
    reg: com.example.siteregister.model.SiteRegisterRecord,
    vm: SiteRegisterViewModel
) {
    val c = CtecsTheme.colors
    var subMode by remember { mutableIntStateOf(if (reg.activityCode.contains("GLASS") || reg.activityCode.contains("GEOGRID")) 1 else 0) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SrPillChip("BC / DBM / BM Vehicles", subMode == 0, { subMode = 0 })
        SrPillChip("Glass Grid / Geogrid", subMode == 1, { subMode = 1 })
    }

    if (subMode == 0) {
        val bcSummary = remember(state.activeRegisterBcEntries) {
            HighwayMeasurementEngine.calculateBcLayingSummary(state.activeRegisterBcEntries)
        }
        SrSurfaceCard {
            Text("BC / DBM Daily Vehicle Summary", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Vehicles: ${bcSummary.vehicleCount} • Dedup Length: ${String.format(Locale.US, "%.1f", bcSummary.deduplicatedLaidLengthM)}m • Area: ${String.format(Locale.US, "%.1f", bcSummary.deduplicatedLaidAreaSqm)}m² • Vol: ${String.format(Locale.US, "%.2f", bcSummary.deduplicatedCompactedVolumeCum)}m³ • Material: ${String.format(Locale.US, "%.2f", bcSummary.totalMeasuredMaterialMt)} MT",
                color = c.accent,
                fontSize = 12.sp
            )
            bcSummary.overlapWarnings.forEach { w -> Text("⚠ $w", color = c.warning, fontSize = 11.5.sp) }
        }

        if (state.canEditActiveRegister) {
            var vehicleNo by remember { mutableStateOf("") }
            var bcType by remember { mutableStateOf("BC-II") }
            var layer by remember { mutableStateOf("Wearing Course") }
            var chFrom by remember { mutableStateOf(reg.chainageFrom) }
            var chTo by remember { mutableStateOf(reg.chainageTo) }
            var layTemp by remember { mutableStateOf("") }
            var rollTemp by remember { mutableStateOf("") }
            var wS by remember { mutableStateOf("") }
            var wM by remember { mutableStateOf("") }
            var wE by remember { mutableStateOf("") }
            var dDes by remember { mutableStateOf("40") }
            var dLoose by remember { mutableStateOf("") }
            var dComp by remember { mutableStateOf("") }
            var matMt by remember { mutableStateOf("") }

            SrSurfaceCard {
                SrSectionHeader("ADD VEHICLE LAYING ENTRY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(vehicleNo, { vehicleNo = it.uppercase() }, "Vehicle No.", modifier = Modifier.weight(1f))
                    SrTextField(bcType, { bcType = it }, "Mix Type", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(chFrom, { chFrom = it }, "From CH", modifier = Modifier.weight(1f))
                    SrTextField(chTo, { chTo = it }, "To CH", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(layTemp, { layTemp = it }, "Laying °C", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(rollTemp, { rollTemp = it }, "Rolling °C", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrTextField(wS, { wS = it }, "W_start (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(wM, { wM = it }, "W_mid (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(wE, { wE = it }, "W_end (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrTextField(dDes, { dDes = it }, "Design mm", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(dLoose, { dLoose = it }, "Loose mm", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(dComp, { dComp = it }, "Comp. mm", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                SrTextField(matMt, { matMt = it }, "Measured Weighbridge Material (MT)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                SrPrimaryButton(
                    text = "Save Vehicle Laying Entry",
                    icon = Icons.Default.Add,
                    onClick = {
                        vm.addBcEntryToActiveRegister(
                            BcLayingEntry(
                                registerId = reg.id,
                                revisionNumber = reg.currentRevision,
                                serialNumber = 0,
                                vehicleNumber = vehicleNo.ifBlank { "TIPPER" },
                                bcType = bcType,
                                layer = layer,
                                fromChainage = chFrom,
                                toChainage = chTo,
                                side = reg.side,
                                lane = reg.lane,
                                layingTempC = layTemp.toDoubleOrNull(),
                                rollingTempC = rollTemp.toDoubleOrNull(),
                                startWidthM = wS.toDoubleOrNull(),
                                middleWidthM = wM.toDoubleOrNull(),
                                endWidthM = wE.toDoubleOrNull(),
                                designThicknessMm = dDes.toDoubleOrNull(),
                                looseThicknessMm = dLoose.toDoubleOrNull(),
                                compactedThicknessMm = dComp.toDoubleOrNull(),
                                measuredMaterialMt = matMt.toDoubleOrNull()
                            )
                        )
                        vehicleNo = ""; matMt = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.activeRegisterBcEntries.forEach { e ->
            SrSurfaceCard {
                Text("#${e.serialNumber} • ${e.vehicleNumber} (${e.bcType})", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text("${e.fromChainage} → ${e.toChainage} | Lay: ${e.layingTempC ?: "--"}°C • Roll: ${e.rollingTempC ?: "--"}°C | Comp: ${e.compactedThicknessMm ?: "--"} mm | ${e.measuredMaterialMt ?: "--"} MT", color = c.textSecondary, fontSize = 12.sp)
            }
        }
    } else {
        if (state.canEditActiveRegister) {
            var chFrom by remember { mutableStateOf(reg.chainageFrom) }
            var chTo by remember { mutableStateOf(reg.chainageTo) }
            var appLen by remember { mutableStateOf("100") }
            var appW by remember { mutableStateOf("7.5") }
            var rollW by remember { mutableStateOf("4.0") }
            var rolls by remember { mutableStateOf("2") }
            var overlapW by remember { mutableStateOf("0.2") }
            var ptrPasses by remember { mutableStateOf("3") }
            var tackStatus by remember { mutableStateOf("Uniform") }
            var alignment by remember { mutableStateOf("No wrinkles") }

            SrSurfaceCard {
                SrSectionHeader("ADD GLASS GRID / GEOGRID STRETCH")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(chFrom, { chFrom = it }, "From CH", modifier = Modifier.weight(1f))
                    SrTextField(chTo, { chTo = it }, "To CH", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(appLen, { appLen = it }, "Length (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(appW, { appW = it }, "Road Width (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrTextField(rollW, { rollW = it }, "Roll W (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    SrTextField(rolls, { rolls = it }, "Rolls", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    SrTextField(overlapW, { overlapW = it }, "Overlap (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(ptrPasses, { ptrPasses = it }, "PTR Passes", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    SrTextField(tackStatus, { tackStatus = it }, "Tack Coat Status", modifier = Modifier.weight(1f))
                }
                SrTextField(alignment, { alignment = it }, "Wrinkles / Alignment Inspection")
                SrPrimaryButton(
                    text = "Save Glass Grid Stretch",
                    icon = Icons.Default.Add,
                    onClick = {
                        vm.addGlassGridEntryToActiveRegister(
                            GlassGridEntry(
                                registerId = reg.id,
                                revisionNumber = reg.currentRevision,
                                fromChainage = chFrom,
                                toChainage = chTo,
                                applicationLengthM = appLen.toDoubleOrNull() ?: 0.0,
                                applicationWidthM = appW.toDoubleOrNull() ?: 0.0,
                                rollWidthM = rollW.toDoubleOrNull() ?: 0.0,
                                numberOfRolls = rolls.toIntOrNull() ?: 1,
                                overlapWidthM = overlapW.toDoubleOrNull() ?: 0.0,
                                applicationSide = reg.side,
                                tackCoatStatus = tackStatus,
                                ptrPasses = ptrPasses.toIntOrNull(),
                                wrinklesAlignment = alignment
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        state.activeRegisterGlassGrid.forEach { g ->
            val calc = HighwayMeasurementEngine.calculateGlassGridCoverage(g)
            SrSurfaceCard {
                Text("${g.fromChainage} → ${g.toChainage} (${g.numberOfRolls} rolls × ${g.rollWidthM}m)", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(calc.formulaSummary, color = c.accent, fontSize = 11.5.sp)
                Text("Tack Coat: ${g.tackCoatStatus} • PTR Passes: ${g.ptrPasses ?: "--"} • ${g.wrinklesAlignment}", color = c.textSecondary, fontSize = 11.5.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestsAndOcrTab(
    state: SiteRegisterUiState,
    vm: SiteRegisterViewModel,
    recentCtecsPhotoUris: List<String>,
    onLaunchCtecsCamera: () -> Unit
) {
    val c = CtecsTheme.colors
    var mode by remember { mutableIntStateOf(0) }
    val attachedUris = remember { mutableStateListOf<String>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) attachedUris.add(uri.toString())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SrPillChip("Tack Coat Spray Test", mode == 0, { mode = 0 })
        SrPillChip("Universal Quality Test + OCR", mode == 1, { mode = 1 })
    }

    if (mode == 0) {
        var ch by remember { mutableStateOf(state.activeRegister?.chainageFrom.orEmpty()) }
        var material by remember { mutableStateOf("RS-1 Bitumen Emulsion") }
        var temp by remember { mutableStateOf("") }
        var specMin by remember { mutableStateOf("") }
        var specMax by remember { mutableStateOf("") }
        var trayArea by remember { mutableStateOf("0.09") }
        var wtBefore by remember { mutableStateOf("") }
        var wtAfter by remember { mutableStateOf("") }
        val trays = remember { mutableStateListOf<TackCoatTrayRow>() }

        val previewSummary = remember(trays.toList(), specMin, specMax) {
            HighwayMeasurementEngine.calculateTackCoatSprayTest(
                trays = trays.toList(),
                specMinKgSqm = specMin.toDoubleOrNull(),
                specMaxKgSqm = specMax.toDoubleOrNull()
            )
        }

        SrSurfaceCard {
            SrSectionHeader("TACK COAT SPRAY RATE TEST", "No Invented Limits")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrTextField(ch, { ch = it }, "Chainage", modifier = Modifier.weight(1f))
                SrTextField(temp, { temp = it }, "Temp °C", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            SrTextField(material, { material = it }, "Material Type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrTextField(specMin, { specMin = it }, "Project Spec Min (kg/m²)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                SrTextField(specMax, { specMax = it }, "Project Spec Max (kg/m²)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SrTextField(trayArea, { trayArea = it }, "Tray m²", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                SrTextField(wtBefore, { wtBefore = it }, "Before kg", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                SrTextField(wtAfter, { wtAfter = it }, "After kg", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            SrTonalButton(
                text = "Add Tray Row (#${trays.size + 1})",
                icon = Icons.Default.Add,
                onClick = {
                    val a = trayArea.toDoubleOrNull() ?: 0.0
                    val b = wtBefore.toDoubleOrNull() ?: 0.0
                    val aft = wtAfter.toDoubleOrNull() ?: 0.0
                    trays.add(TackCoatTrayRow(trayNumber = trays.size + 1, trayAreaSqm = a, weightBeforeKg = b, weightAfterKg = aft))
                    wtBefore = ""; wtAfter = ""
                },
                modifier = Modifier.fillMaxWidth()
            )

            previewSummary.trayResults.forEach { tr ->
                Text(
                    text = if (tr.isValid) {
                        "Tray #${tr.trayNumber}: Bitumen=${String.format(Locale.US, "%.4f", tr.bitumenWeightKg)}kg • Rate=${String.format(Locale.US, "%.4f", tr.sprayRateKgPerSqm)} kg/m² [${tr.status.label}]"
                    } else {
                        "Tray #${tr.trayNumber}: ERROR – ${tr.validationError}"
                    },
                    color = if (tr.isValid) c.textPrimary else c.error,
                    fontSize = 11.5.sp
                )
            }
            if (trays.isNotEmpty()) {
                Text("${previewSummary.formulaSummary} • Overall: ${previewSummary.overallStatus.label}", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            SrPrimaryButton(
                text = "Save Tack Coat Spray Test",
                onClick = {
                    vm.saveTackCoatTest(
                        TackCoatTestRecord(
                            projectId = state.activeProject?.id.orEmpty(),
                            chainage = ch,
                            side = state.activeRegister?.side ?: "RHS",
                            materialType = material,
                            testDate = srTodayIsoDate(),
                            testedBy = state.profile?.fullName.orEmpty(),
                            temperatureC = temp.toDoubleOrNull(),
                            specMinRateKgSqm = specMin.toDoubleOrNull(),
                            specMaxRateKgSqm = specMax.toDoubleOrNull(),
                            trays = trays.toList(),
                            photoUris = attachedUris.toList()
                        )
                    )
                    trays.clear()
                },
                enabled = trays.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.tackCoatTests.forEach { t ->
            val sum = HighwayMeasurementEngine.calculateTackCoatSprayTest(t.trays, t.specMinRateKgSqm, t.specMaxRateKgSqm)
            SrSurfaceCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${t.testDate} • CH ${t.chainage} (${t.trays.size} trays)", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    SrStatusBadge(sum.overallStatus.label, if (sum.overallStatus == TestResultStatus.PASS) c.success else if (sum.overallStatus == TestResultStatus.FAIL) c.error else c.warning)
                }
                Text(sum.formulaSummary, color = c.textSecondary, fontSize = 11.5.sp)
            }
        }
    } else {
        var category by remember { mutableStateOf(QualityTestCategory.FIELD_DENSITY) }
        var title by remember { mutableStateOf("") }
        var chainage by remember { mutableStateOf(state.activeRegister?.chainageFrom.orEmpty()) }
        var specCriteria by remember { mutableStateOf("") }
        var ocrText by remember { mutableStateOf("") }
        var ocrHandwritten by remember { mutableStateOf(false) }
        var ocrVerified by remember { mutableStateOf(false) }
        var resultStatus by remember { mutableStateOf(TestResultStatus.NEEDS_REVIEW) }

        SrSurfaceCard {
            SrSectionHeader("UNIVERSAL QUALITY TEST + CTECS CAMERA & OCR")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QualityTestCategory.entries.forEach { cat ->
                    SrPillChip(cat.label.substringBefore(" ("), category == cat, { category = cat })
                }
            }
            SrTextField(title, { title = it }, "Test Report Title *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrTextField(chainage, { chainage = it }, "Chainage", modifier = Modifier.weight(1f))
                SrTextField(specCriteria, { specCriteria = it }, "Project Spec Criteria", modifier = Modifier.weight(1f))
            }

            // CTECS Camera + Photo Picker Integration (preserves timestamps, GPS, chainage, EXIF, watermark)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SrTonalButton("CTECS Camera", onLaunchCtecsCamera, icon = Icons.Default.CameraAlt, modifier = Modifier.weight(1f))
                SrTonalButton(
                    "Photo Picker",
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    icon = Icons.Default.PhotoLibrary,
                    modifier = Modifier.weight(1f)
                )
            }
            if (recentCtecsPhotoUris.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    recentCtecsPhotoUris.take(5).forEachIndexed { idx, uri ->
                        val isAdded = uri in attachedUris
                        SrPillChip(
                            label = if (isAdded) "CTECS Photo #${idx + 1} ✓" else "+ CTECS Photo #${idx + 1}",
                            selected = isAdded,
                            onClick = { if (isAdded) attachedUris.remove(uri) else attachedUris.add(uri) }
                        )
                    }
                }
            }
            Text("Attached Evidence Photos: ${attachedUris.size} (Preserves CTECS timestamp, GPS, chainage & EXIF)", color = c.textMuted, fontSize = 11.sp)

            // OCR Assisted Field Extraction (Never Auto-Approved)
            SrTextField(ocrText, { ocrText = it }, "Paste / Transcribe Report Text for OCR Field Suggestions", singleLine = false)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ocrHandwritten, onCheckedChange = { ocrHandwritten = it })
                    Text("Handwritten", color = c.textSecondary, fontSize = 11.5.sp)
                }
                SrPillChip("Extract OCR Fields", selected = false, icon = Icons.Default.DocumentScanner, onClick = {
                    vm.runOcrFieldExtraction(ocrText, ocrHandwritten)
                    ocrVerified = false
                })
            }
            state.lastOcrResult?.let { ocr ->
                Text("OCR Confidence: ${(ocr.overallConfidence * 100).toInt()}% • Manual Verification Mandatory", color = c.warning, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                ocr.suggestions.forEach { s ->
                    Text("• ${s.fieldName}: ${s.suggestedValue} (${(s.confidence * 100).toInt()}%)", color = c.textPrimary, fontSize = 11.5.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ocrVerified, onCheckedChange = { ocrVerified = it })
                    Text("I manually verified all OCR extracted values", color = c.textPrimary, fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TestResultStatus.entries.forEach { st ->
                    SrPillChip(st.label, resultStatus == st, { resultStatus = st })
                }
            }
            SrPrimaryButton(
                text = "Save Quality Test Report",
                onClick = {
                    if (title.isBlank()) return@SrPrimaryButton
                    val vals = state.lastOcrResult?.suggestions?.associate { it.fieldName to it.suggestedValue }.orEmpty()
                    vm.saveQualityTestReport(
                        QualityTestReportRecord(
                            projectId = state.activeProject?.id.orEmpty(),
                            category = category,
                            testTitle = title,
                            testDate = srTodayIsoDate(),
                            chainage = chainage,
                            testedBy = state.profile?.fullName.orEmpty(),
                            measuredValues = vals,
                            specCriteria = specCriteria,
                            resultStatus = resultStatus,
                            photoUris = attachedUris.toList(),
                            ocrExtractedText = ocrText,
                            ocrConfidence = state.lastOcrResult?.overallConfidence,
                            ocrVerifiedManually = ocrVerified
                        )
                    )
                    title = ""; ocrText = ""; attachedUris.clear()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.qualityTests.forEach { qt ->
            SrSurfaceCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(qt.testTitle, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    SrStatusBadge(qt.resultStatus.label, if (qt.resultStatus == TestResultStatus.PASS) c.success else if (qt.resultStatus == TestResultStatus.FAIL) c.error else c.warning)
                }
                Text("${qt.category.label} • CH ${qt.chainage} • ${qt.photoUris.size} photo(s) • OCR Verified: ${if (qt.ocrVerifiedManually) "Yes" else "No"}", color = c.textSecondary, fontSize = 11.5.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObservationsInlineTab(
    state: SiteRegisterUiState,
    vm: SiteRegisterViewModel,
    recentCtecsPhotoUris: List<String>,
    onLaunchCtecsCamera: () -> Unit
) {
    val c = CtecsTheme.colors
    var category by remember { mutableStateOf(ObservationCategory.SEGREGATION) }
    var severity by remember { mutableStateOf(ObservationSeverity.MEDIUM) }
    var desc by remember { mutableStateOf("") }
    var chFrom by remember { mutableStateOf(state.activeRegister?.chainageFrom.orEmpty()) }
    var chTo by remember { mutableStateOf(state.activeRegister?.chainageTo.orEmpty()) }
    var affL by remember { mutableStateOf("") }
    var affW by remember { mutableStateOf("") }
    var affD by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }

    if (state.activeMembership?.assignedRole?.canAddObservation == true) {
        SrSurfaceCard {
            SrSectionHeader("LOG SITE OBSERVATION & DEFECT")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ObservationCategory.entries.take(10).forEach { cat ->
                    SrPillChip(cat.label, category == cat, { category = cat })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ObservationSeverity.entries.forEach { sev ->
                    SrPillChip(sev.label, severity == sev, { severity = sev })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrTextField(chFrom, { chFrom = it }, "From CH", modifier = Modifier.weight(1f))
                SrTextField(chTo, { chTo = it }, "To CH", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SrTextField(affL, { affL = it }, "L (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                SrTextField(affW, { affW = it }, "W (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                SrTextField(affD, { affD = it }, "D (m)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            SrTextField(desc, { desc = it }, "Observation Description *", singleLine = false)
            SrTextField(action, { action = it }, "Corrective Action Required")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrTonalButton("Capture Photo", onLaunchCtecsCamera, icon = Icons.Default.CameraAlt, modifier = Modifier.weight(1f))
                SrPrimaryButton(
                    text = "Log Observation",
                    onClick = {
                        if (desc.isBlank()) return@SrPrimaryButton
                        val l = affL.toDoubleOrNull()
                        val w = affW.toDoubleOrNull()
                        val d = affD.toDoubleOrNull()
                        vm.saveObservation(
                            SiteObservationRecord(
                                projectId = state.activeProject?.id.orEmpty(),
                                activityName = state.activeRegister?.activityName.orEmpty(),
                                chainageFrom = chFrom,
                                chainageTo = chTo,
                                affectedLengthM = l,
                                affectedWidthM = w,
                                affectedDepthM = d,
                                affectedAreaSqm = if (l != null && w != null) l * w else null,
                                affectedVolumeCum = if (l != null && w != null && d != null) l * w * d else null,
                                category = category,
                                severity = severity,
                                description = desc,
                                correctiveAction = action,
                                responsibleContractor = state.activeProject?.contractorName.orEmpty(),
                                beforePhotoUris = recentCtecsPhotoUris.take(1)
                            )
                        )
                        desc = ""; action = ""
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    state.observations.forEach { obs ->
        SrSurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${obs.serialNumber} • ${obs.category.label} (${obs.severity.label})", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                SrStatusBadge(obs.status.label, if (obs.status == RectificationStatus.CLOSED || obs.status == RectificationStatus.VERIFIED) c.success else c.warning)
            }
            Text("${obs.chainageFrom}–${obs.chainageTo}: ${obs.description}", color = c.textSecondary, fontSize = 12.sp)
            if (obs.correctiveAction.isNotBlank()) {
                Text("Action: ${obs.correctiveAction}", color = c.accent, fontSize = 11.5.sp)
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RectificationStatus.entries.forEach { next ->
                    if (next != obs.status) {
                        SrPillChip("Mark ${next.label}", selected = false, onClick = {
                            vm.updateObservationRectification(obs, next, obs.correctiveAction, obs.afterPhotoUris, obs.finalRemarks)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SignaturesAndPmcGovernanceTab(
    state: SiteRegisterUiState,
    reg: com.example.siteregister.model.SiteRegisterRecord,
    vm: SiteRegisterViewModel
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val role = state.activeMembership?.assignedRole
    val isCreator = state.session?.userId == reg.createdBy

    // 1. PMC Approval & Controlled Creator-Only Reopening Card
    SrSurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PMC Approval & Lock Control", color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            SrStatusBadge(reg.status.displayLabel, registerStatusColor(reg.status), leadingLock = reg.status == RegisterStatus.APPROVED_LOCKED)
        }

        // Submit Draft / Rejected -> Pending PMC Approval
        if ((reg.status == RegisterStatus.DRAFT || reg.status == RegisterStatus.REJECTED) && (role?.canSubmitRegister == true || isCreator)) {
            SrPrimaryButton(
                text = "Submit Rev ${reg.currentRevision} for PMC Approval",
                icon = Icons.Default.Send,
                onClick = { vm.submitActiveRegisterForPmcApproval() },
                modifier = Modifier.fillMaxWidth(),
                testTagName = "sr_submit_pmc_btn"
            )
        }

        // PMC Approval / Rejection Controls (strictly PMC Representative / Admin; never self-approval)
        if ((reg.status == RegisterStatus.PENDING_PMC_APPROVAL || reg.status == RegisterStatus.SUBMITTED || reg.status == RegisterStatus.RESUBMITTED) &&
            (role?.canApproveRegister == true || state.profile?.isSystemAdmin == true)
        ) {
            var pmcRemarks by remember { mutableStateOf("") }
            SrTextField(pmcRemarks, { pmcRemarks = it }, "PMC Review Remarks")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SrPrimaryButton(
                    text = "Approve & Lock",
                    icon = Icons.Default.Lock,
                    containerColor = c.success,
                    onClick = { vm.pmcDecisionOnActiveRegister(approve = true, remarks = pmcRemarks) },
                    modifier = Modifier.weight(1f),
                    testTagName = "sr_pmc_approve_btn"
                )
                SrTonalButton(
                    text = "Reject",
                    accent = c.error,
                    onClick = { vm.pmcDecisionOnActiveRegister(approve = false, remarks = pmcRemarks) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Creator Request Edit Permission when Approved – Locked
        if ((reg.status == RegisterStatus.APPROVED_LOCKED || reg.status == RegisterStatus.EDIT_PERMISSION_EXPIRED) && isCreator) {
            var reason by remember { mutableStateOf("") }
            var correction by remember { mutableStateOf("") }
            var evidence by remember { mutableStateOf("") }
            SrSectionHeader("REQUEST TEMPORARY EDIT PERMISSION (CREATOR ONLY)")
            SrTextField(reason, { reason = it }, "Reason for Modification *")
            SrTextField(correction, { correction = it }, "Proposed Correction Summary *")
            SrTextField(evidence, { evidence = it }, "Supporting Photo / Test Reference")
            SrTonalButton(
                text = "Request PMC Edit Permission",
                icon = Icons.Default.LockOpen,
                accent = c.warning,
                onClick = { vm.requestEditPermissionOnActiveRegister(reason, correction, evidence) },
                modifier = Modifier.fillMaxWidth(),
                testTagName = "sr_request_edit_btn"
            )
        }

        // PMC Review Pending Edit Requests for this register
        val pendingEditReqs = state.editRequests.filter { it.registerId == reg.id && it.status == EditRequestStatus.PENDING }
        if (pendingEditReqs.isNotEmpty() && (role?.canManageEditPermissions == true || state.profile?.isSystemAdmin == true)) {
            pendingEditReqs.forEach { req ->
                var durationMin by remember { mutableStateOf("30") }
                var pmcNote by remember { mutableStateOf("") }
                Text("Edit Request from ${req.requestedByName}: ${req.reason}", color = c.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Proposed: ${req.proposedCorrection}", color = c.textSecondary, fontSize = 11.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(durationMin, { durationMin = it }, "Duration (min)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.9f))
                    SrTextField(pmcNote, { pmcNote = it }, "PMC Remarks", modifier = Modifier.weight(1.4f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrPrimaryButton(
                        text = "Grant Timed Edit",
                        icon = Icons.Default.Gavel,
                        onClick = { vm.pmcGrantEditPermission(req, durationMin.toIntOrNull() ?: 30, pmcNote) },
                        modifier = Modifier.weight(1f)
                    )
                    SrTonalButton("Reject", { vm.pmcRejectEditRequest(req, pmcNote) }, accent = c.error, modifier = Modifier.weight(0.7f))
                }
            }
        }

        // Active Edit Grant Banner + Revoke (PMC) or Submit Corrected Revision N+1 (Creator)
        val activeGrant = state.editPermissions.firstOrNull {
            it.registerId == reg.id && it.isCurrentlyValidFor(reg.createdBy, reg.currentRevision)
        }
        if (activeGrant != null) {
            Text(
                "Active Creator-Only Edit Window expires at ${srFormatTime(activeGrant.expiresAtMs)}",
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isCreator) {
                var changeSummary by remember { mutableStateOf("") }
                SrTextField(changeSummary, { changeSummary = it }, "Correction Summary for Revision ${reg.currentRevision + 1}")
                SrPrimaryButton(
                    text = "Submit Corrected Revision ${reg.currentRevision + 1} (Preserves Rev ${reg.currentRevision})",
                    icon = Icons.Default.Check,
                    onClick = { vm.submitCorrectedRevisionForActiveRegister(changeSummary) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (role?.canManageEditPermissions == true || state.profile?.isSystemAdmin == true) {
                SrTonalButton(
                    text = "Revoke Edit Permission Immediately",
                    accent = c.error,
                    onClick = { vm.pmcRevokeEditPermission(activeGrant) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 2. Digital Signatures (Strictly Separate from PMC Approval)
    SrSurfaceCard {
        SrSectionHeader("DIGITAL SIGNATURES", "Separate from PMC Approval")
        Text(
            "Digital signatures verify review/acknowledgment on Rev ${reg.currentRevision} and do NOT replace mandatory PMC Approval Lock.",
            color = c.textMuted,
            fontSize = 11.5.sp
        )
        if (role?.canSignRegister == true) {
            var sigPoints by remember { mutableStateOf("") }
            var sigRemarks by remember { mutableStateOf("") }
            SrSignaturePadCanvas(onSignatureChanged = { sigPoints = it })
            SrTextField(sigRemarks, { sigRemarks = it }, "Signature Remarks")
            SrPrimaryButton(
                text = "Record Digital Signature (${role.displayLabel})",
                icon = Icons.Default.Draw,
                onClick = { vm.saveDigitalSignature(sigPoints, sigRemarks) },
                enabled = sigPoints.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.activeRegisterSignatures.forEach { s ->
            Text(
                "✓ ${s.signerName} (${s.signerDesignation}, ${s.signerOrganization}) • ${s.signerRole.displayLabel} • Rev ${s.revisionNumber} • ${srFormatTime(s.signedAtMs)}",
                color = c.success,
                fontSize = 11.5.sp
            )
        }
    }

    // 3. Live Read-Only Register Sharing Code + QR
    SrSurfaceCard {
        SrSectionHeader("LIVE REGISTER SHARING CODE", "Read-Only Monitoring")
        SrTonalButton(
            text = "Generate Live Read-Only Sharing Code & QR",
            icon = Icons.Default.QrCode,
            onClick = { vm.generateSharingCodeForActiveRegister() },
            modifier = Modifier.fillMaxWidth()
        )
        state.sharingCodes.filter { it.registerId == reg.id }.take(2).forEach { code ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(code.code, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (code.isRevoked) "Revoked" else "Expires: ${code.expiresAtMs?.let { srFormatTime(it) } ?: "No expiry"} • Read-Only",
                        color = if (code.isRevoked) c.error else c.textSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("CTECS Share Code", code.code))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = c.textPrimary)
                }
                if (!code.isRevoked) {
                    SrPillChip("Revoke", selected = false, accentColor = c.error, onClick = { vm.revokeSharingCode(code) })
                }
            }
            if (!code.isRevoked) {
                BoxCenter { SrQrCodeBox(codeContent = code.code, caption = code.code) }
            }
        }
    }

    // 4. Immutable Revision History
    SrSurfaceCard {
        SrSectionHeader("REVISION HISTORY", "${state.activeRegisterRevisions.size} revision(s)")
        state.activeRegisterRevisions.forEach { rev ->
            Text(
                text = "Rev ${rev.revisionNumber} • ${rev.status.displayLabel} • ${if (rev.isLocked) "IMMUTABLE LOCKED" else "Active"} • ${rev.changeSummary}",
                color = if (rev.isLocked) c.success else c.textSecondary,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun BoxCenter(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        content()
    }
}
