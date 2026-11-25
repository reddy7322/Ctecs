package com.example.ui.workplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BackupFrequency
import com.example.model.MachineryFilterPreference
import com.example.model.MoRTHRevision
import com.example.model.PdfExportScope
import com.example.model.PlanningWorkActivity
import com.example.model.WorkPlanningSettings
import com.example.model.WorkShiftType
import com.example.ui.theme.CtecsTheme
import com.example.ui.weather.PopupSectionHeader
import com.example.ui.weather.PopupSwitchRow
import com.example.ui.weather.SegmentedOptionRow

/**
 * Contextual animated Settings popup exclusively for the separate WORK PLANNING page.
 * Contains ONLY Planning, Machinery, Engineering, and Data & Export preferences.
 * Never embeds Weather Display settings or plan form inputs.
 */
@Composable
fun WorkPlanningSettingsPopup(
    visible: Boolean,
    settings: WorkPlanningSettings,
    isRefreshingCatalog: Boolean,
    catalogCount: Int,
    onUpdateSettings: ((WorkPlanningSettings) -> WorkPlanningSettings) -> Unit,
    onRefreshMachineryCatalog: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val c = CtecsTheme.colors

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
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.95f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.95f)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = c.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 650.dp)
                        .testTag("work_planning_settings_popup")
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
                                    Text("Work Planning Settings", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Defaults, machinery, standards & export", color = c.textMuted, fontSize = 11.sp)
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp).testTag("wp_settings_close")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close work planning settings", tint = c.textSecondary)
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
                            // 1. Planning Preferences
                            PopupSectionHeader("PLANNING PREFERENCES")
                            SegmentedOptionRow(
                                label = "Default Activity",
                                options = listOf(
                                    PlanningWorkActivity.BC_LAYING to "BC",
                                    PlanningWorkActivity.DBM_LAYING to "DBM",
                                    PlanningWorkActivity.WMM to "WMM",
                                    PlanningWorkActivity.PQC to "PQC"
                                ),
                                selected = settings.defaultActivity,
                                tagPrefix = "wps_def_act",
                                onSelect = { act -> onUpdateSettings { it.copy(defaultActivity = act) } }
                            )
                            SegmentedOptionRow(
                                label = "Default Shift",
                                options = WorkShiftType.entries.map { it to it.label.substringBefore(" ") },
                                selected = settings.defaultShift,
                                tagPrefix = "wps_def_shift",
                                onSelect = { sh -> onUpdateSettings { it.copy(defaultShift = sh) } }
                            )
                            SegmentedOptionRow(
                                label = "Default Units & Chainage Format",
                                options = listOf(true to "Metric (m, mm, t)", false to "Decimal km"),
                                selected = settings.chainagePlusFormat,
                                tagPrefix = "wps_units",
                                onSelect = { plus -> onUpdateSettings { it.copy(chainagePlusFormat = plus) } }
                            )

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 2. Machinery Preferences
                            PopupSectionHeader("MACHINERY PREFERENCES")
                            SegmentedOptionRow(
                                label = "Catalogue Model Filter",
                                options = MachineryFilterPreference.entries.map { it to it.label },
                                selected = settings.machineryFilter,
                                tagPrefix = "wps_mach_filter",
                                onSelect = { f -> onUpdateSettings { it.copy(machineryFilter = f) } }
                            )
                            PopupSwitchRow(
                                title = "Prioritise Verified Specs Only",
                                checked = settings.showOnlyVerifiedSpecs,
                                tag = "wps_verified_specs",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showOnlyVerifiedSpecs = v) } }
                            )
                            PopupSwitchRow(
                                title = "Offline Machinery Catalogue",
                                subtitle = "$catalogCount verified models cached locally",
                                checked = settings.offlineCatalogPreferred,
                                tag = "wps_offline_catalog",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(offlineCatalogPreferred = v) } }
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
                                Text("Refresh Machinery Catalogue", color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Button(
                                    onClick = onRefreshMachineryCatalog,
                                    enabled = !isRefreshingCatalog,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                    modifier = Modifier.testTag("wps_refresh_catalog")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text(if (isRefreshingCatalog) "Syncing…" else "Refresh", fontSize = 12.sp)
                                }
                            }

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 3. Engineering Preferences
                            PopupSectionHeader("ENGINEERING PREFERENCES")
                            SegmentedOptionRow(
                                label = "MoRTH Specification Edition",
                                options = listOf(
                                    MoRTHRevision.FIFTH_REVISION_2013 to "5th Rev (2013)",
                                    MoRTHRevision.SIXTH_REVISION_2026 to "6th Rev (2026)"
                                ),
                                selected = settings.morthRevision,
                                tagPrefix = "wps_morth_rev",
                                onSelect = { rev -> onUpdateSettings { it.copy(morthRevision = rev) } }
                            )
                            PopupSwitchRow(
                                title = "Show Calculation Details",
                                checked = settings.showCalculationDetails,
                                tag = "wps_show_calc",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showCalculationDetails = v) } }
                            )
                            PopupSwitchRow(
                                title = "Show Compaction Recommendations",
                                checked = settings.showCompactionRecommendations,
                                tag = "wps_show_comp",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showCompactionRecommendations = v) } }
                            )
                            PopupSwitchRow(
                                title = "Show Rolling Pattern Diagram",
                                checked = settings.showRollingDiagram,
                                tag = "wps_show_diagram",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showRollingDiagram = v) } }
                            )
                            PopupSwitchRow(
                                title = "Show MoRTH / IRC Source References",
                                checked = settings.showSourceReferences,
                                tag = "wps_show_refs",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(showSourceReferences = v) } }
                            )

                            HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                            // 4. Data & Export
                            PopupSectionHeader("DATA & EXPORT")
                            PopupSwitchRow(
                                title = "Automatic Work Plan Saving",
                                checked = settings.autoSaveEnabled,
                                tag = "wps_auto_save",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(autoSaveEnabled = v) } }
                            )
                            SegmentedOptionRow(
                                label = "Automatic Local Backup",
                                options = BackupFrequency.entries.map { it to it.label.substringBefore(" ") },
                                selected = settings.backupFrequency,
                                tagPrefix = "wps_backup_freq",
                                onSelect = { f -> onUpdateSettings { it.copy(backupFrequency = f) } }
                            )
                            SegmentedOptionRow(
                                label = "Default PDF Report Scope",
                                options = listOf(
                                    PdfExportScope.COMPLETE_WORK_PLAN to "Complete",
                                    PdfExportScope.MATERIAL_COMPACTION to "Material",
                                    PdfExportScope.MACHINERY_MANPOWER to "Machinery",
                                    PdfExportScope.WEATHER_RECOMMENDATIONS to "Weather"
                                ),
                                selected = settings.pdfDefaultScope,
                                tagPrefix = "wps_pdf_scope",
                                onSelect = { sc -> onUpdateSettings { it.copy(pdfDefaultScope = sc) } }
                            )
                            PopupSwitchRow(
                                title = "Include Rolling Diagram in PDF",
                                checked = settings.pdfIncludeRollingDiagram,
                                tag = "wps_pdf_diagram",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(pdfIncludeRollingDiagram = v) } }
                            )
                            PopupSwitchRow(
                                title = "Include Approval Block in PDF",
                                checked = settings.pdfIncludeApprovalBlock,
                                tag = "wps_pdf_approval",
                                onCheckedChange = { v -> onUpdateSettings { it.copy(pdfIncludeApprovalBlock = v) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
