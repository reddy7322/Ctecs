package com.example.ui.workplan

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.ActualSiteMeasurements
import com.example.model.BackupDuplicateResolution
import com.example.model.ContractSpecificationConfig
import com.example.model.HighwayWorkPlan
import com.example.model.MachineryCatalogEntry
import com.example.model.MachineryFilterPreference
import com.example.model.ModelAvailabilityStatus
import com.example.model.PdfExportScope
import com.example.model.PlannedMachineryItem
import com.example.model.WeatherForecastBundle
import com.example.model.WorkPlanSaveStatus
import com.example.model.WorkPlanningSettings
import com.example.model.WorkSuitabilityStatus
import com.example.ui.theme.CtecsTheme
import com.example.ui.weather.MachineryPickerDialog
import com.example.weather.HighwayWorkPlanningEngine
import com.example.weather.LocalBackupRecord
import com.example.weather.MachineryCatalogRepository
import com.example.weather.WorkPlanBackupInspection
import com.example.weather.WorkPlanPdfExporter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated, completely separate internal WORK PLANNING page.
 * Strictly separated from the Weather Page while remaining smoothly connected to live weather
 * and offering its own contextual animated Settings popup (WorkPlanningSettingsPopup).
 */
@Composable
fun WorkPlanningScreen(
    plan: HighwayWorkPlan,
    savedPlans: List<HighwayWorkPlan>,
    machineryCatalog: List<MachineryCatalogEntry>,
    isRefreshingCatalog: Boolean,
    workPlanningSettings: WorkPlanningSettings,
    saveStatus: WorkPlanSaveStatus,
    localBackups: List<LocalBackupRecord>,
    forecastBundle: WeatherForecastBundle?,
    contractSpec: ContractSpecificationConfig,
    siteMeasurements: ActualSiteMeasurements,
    animationsEnabled: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    staleThresholdMs: Long = 60L * 60_000L,
    onUpdatePlan: (HighwayWorkPlan) -> Unit,
    onNewPlan: () -> Unit,
    onSavePlan: () -> Unit,
    onRetrySave: () -> Unit,
    onOpenPlan: (HighwayWorkPlan) -> Unit,
    onDuplicatePlan: (HighwayWorkPlan) -> Unit,
    onRenamePlan: (String, String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onUpdateWorkPlanningSettings: ((WorkPlanningSettings) -> WorkPlanningSettings) -> Unit,
    onRefreshMachineryCatalog: () -> Unit,
    onBuildBackupJson: () -> String,
    onInspectBackupJson: (String) -> WorkPlanBackupInspection,
    onApplyBackupImport: (WorkPlanBackupInspection, BackupDuplicateResolution) -> Unit,
    onSaveLocalSnapshot: () -> Unit,
    onSafeRestoreLocalBackup: (LocalBackupRecord) -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateBack: () -> Unit,
    onNotifyUser: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Accordion section expansion state (compact by default — Section 1 expanded for immediate workflow)
    var expandedKeys by remember { mutableStateOf(setOf("sec1_work_details")) }
    fun toggleSec(key: String) {
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
    }

    var showSettingsPopup by remember { mutableStateOf(false) }
    var showMachineryPicker by remember { mutableStateOf(false) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var selectedPdfScope by remember(workPlanningSettings.pdfDefaultScope) {
        mutableStateOf(workPlanningSettings.pdfDefaultScope)
    }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingBackupInspection by remember { mutableStateOf<WorkPlanBackupInspection?>(null) }
    var pendingPdfFileToSave by remember { mutableStateOf<File?>(null) }

    val settingsGearRotation by animateFloatAsState(
        targetValue = if (showSettingsPopup) 90f else 0f,
        animationSpec = tween(if (animationsEnabled) 220 else 0),
        label = "wp_settings_gear_rot"
    )

    // Real-time deterministic engineering calculation
    val calculation = remember(plan, forecastBundle, contractSpec, siteMeasurements, nowMs, staleThresholdMs) {
        HighwayWorkPlanningEngine.calculatePlan(
            plan = plan,
            forecastBundle = forecastBundle,
            contractSpec = contractSpec,
            siteMeasurements = siteMeasurements,
            nowMs = nowMs,
            staleThresholdMs = staleThresholdMs
        )
    }

    // Filter machinery catalogue strictly per Work Planning Settings preferences
    val filteredCatalog = remember(machineryCatalog, workPlanningSettings.machineryFilter, workPlanningSettings.showOnlyVerifiedSpecs) {
        machineryCatalog.filter { entry ->
            val statusOk = when (workPlanningSettings.machineryFilter) {
                MachineryFilterPreference.ALL -> true
                MachineryFilterPreference.LATEST_CURRENT -> entry.availabilityStatus == ModelAvailabilityStatus.CURRENT
                MachineryFilterPreference.LEGACY -> entry.availabilityStatus == ModelAvailabilityStatus.LEGACY
            }
            val verifiedOk = !workPlanningSettings.showOnlyVerifiedSpecs || entry.dataSourceName.isNotBlank()
            statusOk && verifiedOk
        }
    }

    // SAF Launchers (Zero Broad Storage Permissions)
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = onBuildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                onNotifyUser("Work plans backup exported")
            }.onFailure {
                onNotifyUser("Backup export failed: ${it.localizedMessage ?: "I/O error"}")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                pendingBackupInspection = onInspectBackupJson(raw)
            }.onFailure {
                onNotifyUser("Failed to read backup file")
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingPdfFileToSave
        if (uri != null && src != null && src.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                }
                onNotifyUser("Work Plan PDF saved")
            }.onFailure {
                onNotifyUser("Failed to save PDF")
            }
        }
    }

    fun buildPdfForScope(pdfScope: PdfExportScope) {
        runCatching {
            generatedPdfFile = WorkPlanPdfExporter.generatePdf(
                context = context,
                plan = plan,
                calc = calculation,
                scope = pdfScope,
                includeRollingDiagram = workPlanningSettings.pdfIncludeRollingDiagram,
                includeApprovalBlock = workPlanningSettings.pdfIncludeApprovalBlock
            )
        }.onFailure {
            onNotifyUser("PDF generation failed")
        }
    }

    fun sharePdfFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CTECS Work Plan - ${plan.effectivePlanTitle}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Work Plan PDF"))
        }.onFailure {
            onNotifyUser("Unable to open share sheet")
        }
    }

    val saveBadgeColor = when (saveStatus) {
        WorkPlanSaveStatus.IDLE -> c.textMuted
        WorkPlanSaveStatus.SAVED -> c.success
        WorkPlanSaveStatus.SAVING -> c.accent
        WorkPlanSaveStatus.OFFLINE_SAVED_LOCALLY -> c.warning
        WorkPlanSaveStatus.SAVE_FAILED -> c.error
    }

    val suitabilityColor = when (calculation.overallWeatherSuitability) {
        WorkSuitabilityStatus.SUITABLE -> c.success
        WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> c.warning
        WorkSuitabilityStatus.NOT_RECOMMENDED -> c.error
        WorkSuitabilityStatus.INSUFFICIENT_DATA -> c.textMuted
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("work_planning_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Back, Title, Auto-Save Badge, Weather Switcher, Contextual Settings Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp).testTag("wp_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
                    }
                    Column {
                        Text(
                            text = "Work Planning",
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = saveStatus == WorkPlanSaveStatus.SAVE_FAILED) { onRetrySave() }
                                .semantics(mergeDescendants = true) {}
                                .testTag("wp_save_status_badge")
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(saveBadgeColor))
                            Text(
                                text = saveStatus.label,
                                color = saveBadgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.secondarySurface)
                            .clickable(onClick = onNavigateToWeather)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("wp_open_weather_btn")
                    ) {
                        Icon(Icons.Rounded.Cloud, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                        Text("Weather", color = c.textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(
                        onClick = { showSettingsPopup = true },
                        modifier = Modifier.size(48.dp).testTag("work_planning_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Work Planning Settings",
                            tint = c.textPrimary,
                            modifier = Modifier.rotate(settingsGearRotation)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .align(Alignment.CenterHorizontally)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("work_planning_list"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Compact Top Summary Card
                item(key = "top_summary") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.surface)
                            .padding(14.dp)
                            .testTag("wp_top_summary_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plan.effectiveActivityName,
                                    color = c.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = plan.formattedLocation,
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(suitabilityColor.copy(alpha = if (c.isDark) 0.18f else 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                                    .testTag("wp_summary_suitability")
                            ) {
                                Text(
                                    text = calculation.overallWeatherSuitability.label,
                                    color = suitabilityColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryStatChip(
                                label = "Length",
                                value = plan.effectiveLengthMeters?.let { "${wpFmt(it)} m" } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatChip(
                                label = "Width",
                                value = plan.effectiveWorkWidthMeters?.let { "${wpFmt(it)} m" } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatChip(
                                label = plan.shiftType.label.substringBefore(" "),
                                value = "${wpFmt(plan.effectiveWorkingHoursValue)} h",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatChip(
                                label = "Target Qty",
                                value = calculation.estimatedMaterialTonnes?.let { "${wpFmt(it)} t" }
                                    ?: calculation.materialVolumeM3?.let { "${wpFmt(it)} m³" }
                                    ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Action Bar: New Plan | Save Plan | Calculate Plan | Export PDF | Saved Plans
                item(key = "action_bar") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("wp_action_bar")
                    ) {
                        item {
                            ActionPillButton(Icons.Rounded.Add, "New Plan", tag = "wp_act_new_plan") {
                                onNewPlan()
                                expandedKeys = setOf("sec1_work_details")
                            }
                        }
                        item {
                            ActionPillButton(Icons.Rounded.Save, "Save Plan", highlighted = true, tag = "wp_act_save_plan") {
                                onSavePlan()
                            }
                        }
                        item {
                            ActionPillButton(Icons.Rounded.Calculate, "Calculate Plan", tag = "wp_act_calculate") {
                                expandedKeys = expandedKeys + "sec7_production"
                                scope.launch { listState.animateScrollToItem(8) }
                            }
                        }
                        item {
                            ActionPillButton(Icons.Rounded.PictureAsPdf, "Export PDF", tag = "wp_act_export_pdf") {
                                buildPdfForScope(selectedPdfScope)
                                showPdfDialog = true
                            }
                        }
                        item {
                            ActionPillButton(Icons.Rounded.Folder, "Saved (${savedPlans.size})", tag = "wp_act_saved_plans") {
                                expandedKeys = expandedKeys + "sec8_saved_plans"
                                scope.launch { listState.animateScrollToItem(9) }
                            }
                        }
                    }
                }

                // Section 1: Work Details (Project, Chainage, Width, Day/Night/Custom Shift)
                item(key = "sec1") {
                    WorkPlanExpandableCard(
                        title = "1. Work Details & Shift",
                        subtitle = "${plan.startChainage.ifBlank { "Chainage" }} – ${plan.endChainage.ifBlank { "—" }} · ${plan.shiftType.label}",
                        icon = Icons.Rounded.Construction,
                        isExpanded = "sec1_work_details" in expandedKeys,
                        tagPrefix = "sec1",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec1_work_details") }
                    ) {
                        Section1WorkDetailsContent(plan = plan, onUpdatePlan = onUpdatePlan)
                    }
                }

                // Section 2: Material & Thickness
                item(key = "sec2") {
                    WorkPlanExpandableCard(
                        title = "2. Material & Thickness",
                        subtitle = "${plan.effectiveActivityName} · ${plan.layerThicknessMm?.let { "${wpFmt(it)} mm" } ?: "Thickness"} · ${calculation.workAreaSqm?.let { "${wpFmt(it)} m²" } ?: "Area"}",
                        icon = Icons.Rounded.Layers,
                        isExpanded = "sec2_material" in expandedKeys,
                        tagPrefix = "sec2",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec2_material") }
                    ) {
                        Section2MaterialThicknessContent(plan = plan, calc = calculation, onUpdatePlan = onUpdatePlan)
                    }
                }

                // Section 3: Machinery & Transport
                item(key = "sec3") {
                    WorkPlanExpandableCard(
                        title = "3. Machinery & Transport",
                        subtitle = "${plan.machinery.size} machine(s) · ${plan.transport.dumperCount} dumpers · ${calculation.transportResult.status.label}",
                        icon = Icons.Rounded.Build,
                        isExpanded = "sec3_machinery" in expandedKeys,
                        tagPrefix = "sec3",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec3_machinery") }
                    ) {
                        Section3MachineryTransportContent(
                            plan = plan,
                            calc = calculation,
                            machineryCatalog = filteredCatalog,
                            onOpenMachineryPicker = { showMachineryPicker = true },
                            onAutoSuggestFleet = {
                                val starter = MachineryCatalogRepository.suggestedStarterFleet(plan.activity, filteredCatalog)
                                if (starter.isNotEmpty()) {
                                    onUpdatePlan(plan.copy(machinery = starter))
                                    onNotifyUser("Suggested starter fleet added")
                                }
                            },
                            onUpdatePlan = onUpdatePlan
                        )
                    }
                }

                // Section 4: Temperature & Weather
                item(key = "sec4") {
                    WorkPlanExpandableCard(
                        title = "4. Temperature, Weather & MoRTH",
                        subtitle = "${calculation.overallWeatherSuitability.label} · ${plan.morthRevision.shortLabel}",
                        icon = Icons.Rounded.Thermostat,
                        isExpanded = "sec4_temp_weather" in expandedKeys,
                        tagPrefix = "sec4",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec4_temp_weather") }
                    ) {
                        Section4TemperatureWeatherContent(
                            plan = plan,
                            calc = calculation,
                            settings = workPlanningSettings,
                            onUpdatePlan = onUpdatePlan
                        )
                    }
                }

                // Section 5: Compaction & Rolling
                item(key = "sec5") {
                    WorkPlanExpandableCard(
                        title = "5. Compaction & Rolling Pattern",
                        subtitle = "${plan.camberMode.label} · ${plan.compaction.requiredPasses?.let { "$it passes" } ?: "Trial verification"}",
                        icon = Icons.Rounded.Engineering,
                        isExpanded = "sec5_compaction" in expandedKeys,
                        tagPrefix = "sec5",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec5_compaction") }
                    ) {
                        Section5CompactionRollingContent(
                            plan = plan,
                            calc = calculation,
                            settings = workPlanningSettings,
                            animationsEnabled = animationsEnabled,
                            onUpdatePlan = onUpdatePlan
                        )
                    }
                }

                // Section 6: Manpower
                item(key = "sec6") {
                    WorkPlanExpandableCard(
                        title = "6. Manpower & Teams",
                        subtitle = "Req ${calculation.manpowerSummary.totalRequired} · Avail ${calculation.manpowerSummary.totalAvailable} · Short ${calculation.manpowerSummary.totalShortage}",
                        icon = Icons.Rounded.Groups,
                        isExpanded = "sec6_manpower" in expandedKeys,
                        tagPrefix = "sec6",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec6_manpower") }
                    ) {
                        Section6ManpowerContent(plan = plan, calc = calculation, onUpdatePlan = onUpdatePlan)
                    }
                }

                // Section 7: Production Summary
                item(key = "sec7") {
                    WorkPlanExpandableCard(
                        title = "7. Production Summary & Sign-Off",
                        subtitle = calculation.effectiveHourlyProduction?.let { "${wpFmt(it)} ${calculation.productionUnit}/hr · ${calculation.limitingBottleneck}" }
                            ?: calculation.limitingBottleneck,
                        icon = Icons.Rounded.Assessment,
                        isExpanded = "sec7_production" in expandedKeys,
                        tagPrefix = "sec7",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec7_production") }
                    ) {
                        Section7ProductionSummaryContent(
                            plan = plan,
                            calc = calculation,
                            settings = workPlanningSettings,
                            onUpdatePlan = onUpdatePlan
                        )
                    }
                }

                // Section 8: Saved Plans & Backup
                item(key = "sec8") {
                    WorkPlanExpandableCard(
                        title = "8. Saved Plans & Backup",
                        subtitle = "${savedPlans.size} saved plan(s) · ${localBackups.size} local backup(s)",
                        icon = Icons.Rounded.Folder,
                        isExpanded = "sec8_saved_plans" in expandedKeys,
                        tagPrefix = "sec8",
                        animationsEnabled = animationsEnabled,
                        onToggle = { toggleSec("sec8_saved_plans") }
                    ) {
                        Section8SavedPlansBackupContent(
                            activePlanId = plan.planId,
                            savedPlans = savedPlans,
                            localBackups = localBackups,
                            onOpenPlan = { loaded ->
                                onOpenPlan(loaded)
                                expandedKeys = setOf("sec1_work_details")
                            },
                            onDuplicatePlan = onDuplicatePlan,
                            onRenamePlan = onRenamePlan,
                            onDeletePlan = onDeletePlan,
                            onExportBackupJson = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportBackupLauncher.launch("CTECS_WorkPlans_Backup_$stamp.json")
                            },
                            onImportBackupJson = {
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            onSaveLocalSnapshot = onSaveLocalSnapshot,
                            onSafeRestoreLocalBackup = onSafeRestoreLocalBackup
                        )
                    }
                }

                item(key = "bottom_pad") {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Contextual Work Planning Settings Popup (ONLY Work Planning settings)
    WorkPlanningSettingsPopup(
        visible = showSettingsPopup,
        settings = workPlanningSettings,
        isRefreshingCatalog = isRefreshingCatalog,
        catalogCount = machineryCatalog.size,
        onUpdateSettings = onUpdateWorkPlanningSettings,
        onRefreshMachineryCatalog = onRefreshMachineryCatalog,
        onDismiss = { showSettingsPopup = false }
    )

    // Cascading Machinery Picker Dialog
    if (showMachineryPicker) {
        MachineryPickerDialog(
            catalog = filteredCatalog,
            suggestedCategories = MachineryCatalogRepository.suggestedCategoriesForActivity(plan.activity),
            onSelectMachine = { entry ->
                val newItem = PlannedMachineryItem(
                    catalogEntry = entry,
                    quantity = 1,
                    actualOperatingCapacity = null,
                    actualOperatingCapacityUnit = entry.maxRatedCapacityUnit.ifBlank { "t/h" }
                )
                onUpdatePlan(plan.copy(machinery = plan.machinery + newItem))
                showMachineryPicker = false
            },
            onDismiss = { showMachineryPicker = false }
        )
    }

    // In-App A4 PDF Preview, Scope Selection, Save & Share Dialog
    if (showPdfDialog) {
        WorkPlanPdfExportDialog(
            selectedScope = selectedPdfScope,
            generatedPdfFile = generatedPdfFile,
            onSelectScope = { selectedPdfScope = it },
            onGeneratePdf = { buildPdfForScope(it) },
            onSavePdfToDevice = { file ->
                pendingPdfFileToSave = file
                savePdfLauncher.launch(file.name)
            },
            onSharePdf = { file -> sharePdfFile(file) },
            onDismiss = { showPdfDialog = false }
        )
    }

    // Backup Import Validation & Duplicate Handling Dialog
    pendingBackupInspection?.let { inspection ->
        WorkPlanBackupImportDialog(
            inspection = inspection,
            onConfirmImport = { resolution ->
                onApplyBackupImport(inspection, resolution)
                pendingBackupInspection = null
            },
            onDismiss = { pendingBackupInspection = null }
        )
    }
}

@Composable
private fun SummaryStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.secondarySurface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = c.textMuted, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    tag: String,
    onClick: () -> Unit
) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) c.accent else c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (highlighted) c.onAccent else c.accent, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            color = if (highlighted) c.onAccent else c.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
