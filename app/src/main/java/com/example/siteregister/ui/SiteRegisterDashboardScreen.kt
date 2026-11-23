package com.example.siteregister.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.ProjectMembership
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.model.SiteRegisterRecord
import com.example.siteregister.model.SyncState
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.smartFadeSurface
import java.util.Locale

@Composable
fun SiteRegisterDashboardScreen(
    state: SiteRegisterUiState,
    onBack: () -> Unit,
    onNavigateSection: (SiteRegisterNavSection) -> Unit,
    onOpenRegister: (String) -> Unit,
    onSelectProject: (ProjectMembership) -> Unit,
    onTriggerSync: () -> Unit,
    onJoinSharedCode: (String) -> Unit,
    onShiftFilter: (String) -> Unit,
    onDateFilter: (String) -> Unit,
    onSearchQuery: (String) -> Unit,
    onLogout: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val c = CtecsTheme.colors
    var showShareJoinDialog by remember { mutableStateOf(false) }
    var showProjectSwitcher by remember { mutableStateOf(false) }
    val approvedMemberships = remember(state.userMemberships) {
        state.userMemberships.filter { it.approvalStatus == MembershipApprovalStatus.APPROVED }
    }
    val kpis = state.kpis
    val role = state.activeMembership?.assignedRole

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = state.activeProject?.projectName ?: "Site Register",
            subtitle = buildString {
                append(role?.displayLabel ?: "Member")
                state.activeMembership?.organizationName?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
            },
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = onTriggerSync,
                    modifier = Modifier.size(44.dp).testTag("sr_sync_btn")
                ) {
                    Icon(
                        imageVector = if (state.isOnline) Icons.Default.Sync else Icons.Default.CloudOff,
                        contentDescription = "Sync now",
                        tint = if (state.pendingSyncCount > 0) c.warning else c.success
                    )
                }
                IconButton(
                    onClick = { onNavigateSection(SiteRegisterNavSection.SETTINGS) },
                    modifier = Modifier.size(44.dp).testTag("sr_settings_btn")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = c.textPrimary)
                }
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.size(44.dp).testTag("sr_logout_btn")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = c.textSecondary)
                }
            }
        )

        SrBannerStrip(state.bannerMessage, state.bannerIsError, onDismissBanner)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live Connection / Sync / Multi-Project Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val liveColor = when {
                        state.isRealtimeLive -> c.success
                        state.isOnline -> c.accent
                        else -> c.warning
                    }
                    val liveLabel = when {
                        state.isRealtimeLive -> "LIVE"
                        state.isOnline -> "Online"
                        else -> "Offline"
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(liveColor.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(liveColor))
                        Text(liveLabel, color = liveColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface)
                            .clickable(onClick = onTriggerSync)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (state.pendingSyncCount > 0) "${state.pendingSyncCount} Pending Sync" else "Synced • ${srFormatTime(state.lastSyncTimeMs)}",
                            color = if (state.pendingSyncCount > 0) c.warning else c.textSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                    }

                    if (approvedMemberships.size > 1) {
                        SrPillChip(
                            label = "Switch (${approvedMemberships.size})",
                            selected = false,
                            onClick = { showProjectSwitcher = true }
                        )
                    }
                }
            }

            // KPI Metric Grid (Compact 2-Row Borderless)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SrKpiTile("Registers", "${kpis.totalRegisters}", "Today: ${kpis.todayEntries}", c.accent, Modifier.weight(1f))
                        SrKpiTile("Length", "${String.format(Locale.US, "%.1f", kpis.totalLengthM)} m", "Area: ${String.format(Locale.US, "%.0f", kpis.totalAreaSqm)} m²", c.accentSecondary, Modifier.weight(1f))
                        SrKpiTile("Volume", "${String.format(Locale.US, "%.1f", kpis.totalVolumeCum)} m³", "Mat: ${String.format(Locale.US, "%.1f", kpis.totalMaterialMt)} MT", c.success, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SrKpiTile("PMC Queue", "${kpis.pmcPendingApprovals}", "Approved: ${kpis.approvedRegisters}", c.warning, Modifier.weight(1f))
                        SrKpiTile("Edit Reqs", "${kpis.pendingEditRequests}", "Drafts: ${kpis.pendingSubmissions}", c.accent, Modifier.weight(1f))
                        SrKpiTile("QA & Obs", "${kpis.qualityTestReports} Tests", "Open Obs: ${kpis.openObservations}", c.error, Modifier.weight(1f))
                    }
                }
            }

            // Quick Actions Strip (All 41 Activities, Tests, Observations, PMC Admin, Audit, Exports, Live Share)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (role?.canCreateRegister == true) {
                        SrQuickActionBtn(
                            label = "New Register",
                            icon = Icons.Default.Add,
                            accent = c.accent,
                            tag = "sr_new_register_btn",
                            onClick = { onNavigateSection(SiteRegisterNavSection.ACTIVITY_PICKER) }
                        )
                    }
                    SrQuickActionBtn(
                        label = "Quality Tests",
                        icon = Icons.Default.Science,
                        accent = c.accentSecondary,
                        tag = "sr_tests_btn",
                        onClick = { onNavigateSection(SiteRegisterNavSection.QUALITY_TESTS) }
                    )
                    SrQuickActionBtn(
                        label = "Observations",
                        icon = Icons.Default.ReportProblem,
                        accent = c.error,
                        tag = "sr_obs_btn",
                        onClick = { onNavigateSection(SiteRegisterNavSection.OBSERVATIONS) }
                    )
                    SrQuickActionBtn(
                        label = "Join Live Code",
                        icon = Icons.Default.QrCodeScanner,
                        accent = c.success,
                        tag = "sr_join_share_btn",
                        onClick = { showShareJoinDialog = true }
                    )
                    if (role?.canGenerateReports == true) {
                        SrQuickActionBtn(
                            label = "Excel / PDF",
                            icon = Icons.Default.Download,
                            accent = c.warning,
                            tag = "sr_export_btn",
                            onClick = { onNavigateSection(SiteRegisterNavSection.EXPORT_CENTER) }
                        )
                    }
                    if (role?.canManageProjectAndUsers == true || state.profile?.isSystemAdmin == true) {
                        SrQuickActionBtn(
                            label = "PMC Admin",
                            icon = Icons.Default.AdminPanelSettings,
                            accent = c.warning,
                            tag = "sr_pmc_admin_btn",
                            onClick = { onNavigateSection(SiteRegisterNavSection.PMC_ADMIN) }
                        )
                    }
                    if (role?.canViewAuditHistory == true || state.profile?.isSystemAdmin == true) {
                        SrQuickActionBtn(
                            label = "Audit Log",
                            icon = Icons.Default.History,
                            accent = c.textSecondary,
                            tag = "sr_audit_btn",
                            onClick = { onNavigateSection(SiteRegisterNavSection.AUDIT_HISTORY) }
                        )
                    }
                }
            }

            // Search & Shift / Date Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQuery,
                        label = "Search registers by activity, number, or chainage"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL" to "All Shifts", "DAY" to "Day Shift", "NIGHT" to "Night Shift").forEach { (key, lbl) ->
                            SrPillChip(
                                label = lbl,
                                selected = state.selectedShiftFilter == key,
                                onClick = { onShiftFilter(key) }
                            )
                        }
                        val today = srTodayIsoDate()
                        SrPillChip(
                            label = if (state.selectedDateFilter == today) "Today ($today)" else "Today Only",
                            selected = state.selectedDateFilter == today,
                            onClick = { onDateFilter(if (state.selectedDateFilter == today) "" else today) }
                        )
                    }
                }
            }

            item {
                SrSectionHeader(
                    title = "DAILY SITE REGISTERS",
                    trailing = "${state.filteredRegisters.size} shown"
                )
            }

            if (state.filteredRegisters.isEmpty()) {
                item {
                    SrSurfaceCard {
                        Text(
                            text = "No site registers match your filter yet.",
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'New Register' to select from all 41 Highway Work Activities (BC, DBM, Tack Coat, Glass Grid, GSB, WMM, PQC, Bridges, Earthwork, Drainage, Safety).",
                            color = c.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(state.filteredRegisters, key = { it.id }) { reg ->
                    SrRegisterListItem(
                        reg = reg,
                        onClick = { onOpenRegister(reg.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showShareJoinDialog) {
        var codeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showShareJoinDialog = false },
            containerColor = c.surface,
            title = { Text("Open Live Shared Register (Read-Only)", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a unique Register Sharing Code (e.g. CTECS-BC-7K92P6). Sharing codes strictly provide live read-only monitoring and never grant edit or approval rights.",
                        color = c.textSecondary,
                        fontSize = 12.sp
                    )
                    SrTextField(codeInput, { codeInput = it.uppercase() }, "Sharing Code")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showShareJoinDialog = false
                    onJoinSharedCode(codeInput)
                }) { Text("Open Live", color = c.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showShareJoinDialog = false }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }

    if (showProjectSwitcher) {
        AlertDialog(
            onDismissRequest = { showProjectSwitcher = false },
            containerColor = c.surface,
            title = { Text("Switch Active Highway Project", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    approvedMemberships.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.secondarySurface)
                                .clickable {
                                    showProjectSwitcher = false
                                    onSelectProject(m)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.projectName.ifBlank { m.projectId.take(8) }, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Text(m.organizationName, color = c.textSecondary, fontSize = 11.5.sp)
                            }
                            SrStatusBadge(m.assignedRole.displayLabel, c.accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectSwitcher = false }) { Text("Close", color = c.textSecondary) }
            }
        )
    }
}

@Composable
private fun SrKpiTile(
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .smartFadeSurface(surfaceColor = c.surface, cornerRadius = 14.dp, edgeAlpha = 0.85f, centerAlpha = 1f)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, color = c.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SrQuickActionBtn(
    label: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier
            .height(44.dp)
            .smartFadeSurface(surfaceColor = c.surface, cornerRadius = 13.dp, edgeAlpha = 0.85f, centerAlpha = 1f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(17.dp))
        Text(label, color = c.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SrRegisterListItem(
    reg: SiteRegisterRecord,
    onClick: () -> Unit
) {
    val c = CtecsTheme.colors
    val statusColor = registerStatusColor(reg.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .smartFadeSurface(surfaceColor = c.surface, cornerRadius = 16.dp, edgeAlpha = 0.85f, centerAlpha = 1f)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = reg.activityName,
                color = c.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            SrStatusBadge(
                label = "${reg.status.displayLabel} • R${reg.currentRevision}",
                color = statusColor,
                leadingLock = reg.status == RegisterStatus.APPROVED_LOCKED
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${reg.registerNumber} • ${reg.workDate} (${reg.shift})",
                color = c.textSecondary,
                fontSize = 11.5.sp
            )
            Text(
                text = "${reg.chainageFrom.ifBlank { "--" }} → ${reg.chainageTo.ifBlank { "--" }} (${reg.side}/${reg.lane})",
                color = c.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    append("L: ${String.format(Locale.US, "%.1f", reg.totalLengthM)}m")
                    append(" • A: ${String.format(Locale.US, "%.1f", reg.totalAreaSqm)}m²")
                    if (reg.totalVolumeCum > 0.0) append(" • V: ${String.format(Locale.US, "%.2f", reg.totalVolumeCum)}m³")
                    reg.totalMaterialQty?.let { append(" • ${String.format(Locale.US, "%.2f", it)} ${reg.totalMaterialUnit.symbol}") }
                },
                color = c.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            val syncTint = when (reg.syncState) {
                SyncState.SYNCED -> c.success
                SyncState.PENDING_SYNC -> c.warning
                SyncState.CONFLICT, SyncState.FAILED -> c.error
            }
            Text(reg.syncState.label, color = syncTint, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
