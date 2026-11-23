package com.example.siteregister.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siteregister.model.MeasurementUnit
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectRole
import com.example.siteregister.model.RegistrationRequestRecord
import com.example.ui.theme.CtecsTheme

// ============================================================================
// 1. PMC ADMIN CONSOLE (DYNAMIC PROJECT CODES + USER REGISTRATION APPROVALS)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiteRegisterPmcAdminScreen(
    state: SiteRegisterUiState,
    vm: SiteRegisterViewModel,
    onCreateNewProject: () -> Unit,
    onBack: () -> Unit
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    var maxRegs by remember { mutableStateOf("50") }
    var expiryDays by remember { mutableStateOf("30") }
    val permittedOrgs = remember { mutableStateListOf(*OrganizationType.entries.toTypedArray()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "PMC Project & User Control",
            subtitle = state.activeProject?.projectName.orEmpty(),
            onBack = onBack,
            actions = {
                SrPillChip("New Project", selected = false, icon = Icons.Default.Add, onClick = onCreateNewProject)
            }
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError) { vm.clearBanner() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dynamic PMC Project Code Generator
            SrSurfaceCard {
                SrSectionHeader("DYNAMIC PROJECT CODE GENERATOR", "Unpredictable Cryptographic Codes")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrganizationType.entries.forEach { org ->
                        val active = org in permittedOrgs
                        SrPillChip(org.displayLabel, active, {
                            if (active && permittedOrgs.size > 1) permittedOrgs.remove(org) else if (!active) permittedOrgs.add(org)
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(maxRegs, { maxRegs = it }, "Max Users", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    SrTextField(expiryDays, { expiryDays = it }, "Expiry (Days)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                SrPrimaryButton(
                    text = "Generate New PMC Project Code",
                    onClick = {
                        vm.generateNewProjectCode(
                            permittedOrgs = permittedOrgs.toList(),
                            maxRegistrations = maxRegs.toIntOrNull(),
                            expiryDays = expiryDays.toIntOrNull()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_generate_project_code_btn"
                )
            }

            // Active Project Codes & QR
            state.projectCodes.forEach { code ->
                SrSurfaceCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(code.projectCode, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Used: ${code.currentRegistrations}/${code.maxRegistrations ?: "∞"} • " +
                                    if (code.isRevoked) "REVOKED" else if (code.isEnabled) "ACTIVE" else "PAUSED",
                                color = if (code.isRevoked) c.error else if (code.isEnabled) c.success else c.warning,
                                fontSize = 11.5.sp
                            )
                        }
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("CTECS Project Code", code.projectCode))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy project code", tint = c.textPrimary)
                        }
                    }
                    if (!code.isRevoked) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            SrQrCodeBox(codeContent = code.projectCode, caption = code.projectCode)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SrTonalButton(
                                text = if (code.isEnabled) "Pause" else "Enable",
                                onClick = { vm.toggleProjectCodeEnabled(code) },
                                accent = c.warning,
                                modifier = Modifier.weight(1f)
                            )
                            SrTonalButton(
                                text = "Revoke",
                                onClick = { vm.revokeProjectCode(code) },
                                accent = c.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Pending User Registrations Review Queue
            SrSectionHeader("USER REGISTRATION APPROVAL QUEUE", "${state.registrationRequests.size} request(s)")
            if (state.registrationRequests.isEmpty()) {
                SrSurfaceCard {
                    Text("No user registration requests yet.", color = c.textSecondary, fontSize = 12.5.sp)
                    Text("Share a PMC Project Code above for site engineers, supervisors, IE, or client reviewers to request project access.", color = c.textMuted, fontSize = 11.5.sp)
                }
            } else {
                state.registrationRequests.forEach { req ->
                    PmcRegistrationRequestCard(req = req, vm = vm)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PmcRegistrationRequestCard(
    req: RegistrationRequestRecord,
    vm: SiteRegisterViewModel
) {
    val c = CtecsTheme.colors
    var chosenRole by remember(req.id) { mutableStateOf(req.assignedRole ?: ProjectRole.SITE_ENGINEER) }
    var orgVerified by remember(req.id) { mutableStateOf(req.organizationVerified || !req.isUnlistedOrganization) }
    var remarks by remember(req.id) { mutableStateOf(req.pmcRemarks) }

    SrSurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(req.fullName, color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            SrStatusBadge(
                req.status.label,
                when (req.status) {
                    MembershipApprovalStatus.APPROVED -> c.success
                    MembershipApprovalStatus.PENDING, MembershipApprovalStatus.INFO_REQUESTED -> c.warning
                    else -> c.error
                }
            )
        }
        Text("${req.username} • ${req.email} • ${req.mobileNumber}", color = c.textSecondary, fontSize = 11.5.sp)
        Text("Org: ${req.requestedOrganizationName} (${req.requestedOrganizationType.displayLabel}) • Desig: ${req.requestedDesignation}", color = c.textPrimary, fontSize = 12.sp)
        Text("Code Used: ${req.projectCodeUsed} • Requested: ${srFormatTime(req.createdAtMs)}", color = c.textMuted, fontSize = 11.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = orgVerified, onCheckedChange = { orgVerified = it })
            Text(
                if (req.isUnlistedOrganization) "Verify & Approve Unlisted Organization" else "Organization Verified",
                color = c.textPrimary,
                fontSize = 12.sp
            )
        }

        Text("Assign Project Role (Strict Separation from Organization Type):", color = c.textSecondary, fontSize = 11.5.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ProjectRole.entries.filter { it != ProjectRole.SYSTEM_ADMINISTRATOR }.forEach { r ->
                SrPillChip(r.displayLabel, chosenRole == r, { chosenRole = r })
            }
        }
        SrTextField(remarks, { remarks = it }, "PMC Review Remarks")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SrPrimaryButton(
                text = "Approve",
                containerColor = c.success,
                onClick = { vm.reviewRegistrationRequest(req, MembershipApprovalStatus.APPROVED, chosenRole, orgVerified, remarks) },
                modifier = Modifier.weight(1f)
            )
            SrTonalButton(
                text = "Reject",
                accent = c.error,
                onClick = { vm.reviewRegistrationRequest(req, MembershipApprovalStatus.REJECTED, chosenRole, orgVerified, remarks) },
                modifier = Modifier.weight(0.8f)
            )
            SrTonalButton(
                text = "Revoke",
                accent = c.warning,
                onClick = { vm.reviewRegistrationRequest(req, MembershipApprovalStatus.REVOKED, chosenRole, orgVerified, remarks) },
                modifier = Modifier.weight(0.8f)
            )
        }
    }
}

// ============================================================================
// 2. PERMANENT APPEND-ONLY AUDIT HISTORY VIEWER
// ============================================================================

@Composable
fun SiteRegisterAuditHistoryScreen(
    state: SiteRegisterUiState,
    onBack: () -> Unit
) {
    val c = CtecsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "Permanent Audit History",
            subtitle = "Append-Only • ${state.auditLogs.size} events",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.auditLogs, key = { it.id }) { ev ->
                SrSurfaceCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ev.action, color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(srFormatTime(ev.timestampMs), color = c.textMuted, fontSize = 11.sp)
                    }
                    Text(
                        "${ev.userName.ifBlank { ev.userId.take(8) }} • ${ev.organizationType} • ${ev.assignedRole}" +
                            (ev.revisionNumber?.let { " • Rev $it" } ?: ""),
                        color = c.textPrimary,
                        fontSize = 12.sp
                    )
                    if (ev.beforeSummary.isNotBlank()) {
                        Text("Before: ${ev.beforeSummary}", color = c.textMuted, fontSize = 11.sp)
                    }
                    if (ev.afterSummary.isNotBlank()) {
                        Text("After: ${ev.afterSummary}", color = c.textSecondary, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. DAILY EXCEL (.XLSX 14 SHEETS) & PRINTABLE PDF EXPORT CENTER
// ============================================================================

@Composable
fun SiteRegisterExportScreen(
    state: SiteRegisterUiState,
    vm: SiteRegisterViewModel,
    onBack: () -> Unit
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    var reportDate by remember { mutableStateOf("") } // blank = all project dates
    var activityFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "Daily Excel & PDF Export",
            subtitle = "14-Sheet Workbook & Printable PDF",
            onBack = onBack
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError) { vm.clearBanner() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.pendingSyncCount > 0) {
                SrSurfaceCard {
                    Text(
                        "Pending Offline Sync Warning: ${state.pendingSyncCount} record(s) have not yet synced to Supabase. Exported files will carry a clear sync status banner.",
                        color = c.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            SrSurfaceCard {
                SrSectionHeader("EXPORT SCOPE", "Non-Overwriting Unique Filenames")
                SrTextField(reportDate, { reportDate = it }, "Filter Work Date (YYYY-MM-DD, leave blank for all)")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SrPillChip("All Activities", activityFilter == "ALL", { activityFilter = "ALL" })
                    listOf("BC_LAYING", "DBM_LAYING", "TACK_COAT", "GLASS_GRID", "GSB", "WMM", "PQC").forEach { code ->
                        SrPillChip(code.replace('_', ' '), activityFilter == code, { activityFilter = code })
                    }
                }
                SrPrimaryButton(
                    text = if (state.isExporting) "Generating Excel..." else "Export Daily Excel Workbook (.xlsx • 14 Sheets)",
                    icon = Icons.Default.TableChart,
                    enabled = !state.isExporting,
                    onClick = { vm.exportDailyReport(formatExcel = true, reportDate = reportDate, activityFilter = activityFilter) },
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_export_excel_btn"
                )
                SrTonalButton(
                    text = if (state.isExporting) "Generating PDF..." else "Export Printable PDF Report (Landscape + Portrait)",
                    icon = Icons.Default.PictureAsPdf,
                    enabled = !state.isExporting,
                    accent = c.warning,
                    onClick = { vm.exportDailyReport(formatExcel = false, reportDate = reportDate, activityFilter = activityFilter) },
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_export_pdf_btn"
                )
            }

            state.lastExportResult?.let { res ->
                SrSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = c.success)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(res.fileName, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("Sheets/Pages: ${res.sheetOrPageCount} • Size: ${res.file.length() / 1024} KB", color = c.textSecondary, fontSize = 11.5.sp)
                        }
                    }
                    res.pendingSyncWarning?.let { w ->
                        Text(w, color = c.warning, fontSize = 11.5.sp)
                    }
                    SrPrimaryButton(
                        text = "Share / Open Exported File",
                        icon = Icons.Default.Share,
                        onClick = {
                            val shareIntent = Intent.createChooser(vm.buildShareIntent(res), "Share CTECS Report")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ============================================================================
// 4. SITE REGISTER SETTINGS SCREEN
// ============================================================================

@Composable
fun SiteRegisterSettingsScreen(
    state: SiteRegisterUiState,
    vm: SiteRegisterViewModel,
    onBack: () -> Unit
) {
    val c = CtecsTheme.colors
    var contractor by remember { mutableStateOf(state.settings.defaultContractor) }
    var shift by remember { mutableStateOf(state.settings.defaultShift) }
    var lane by remember { mutableStateOf(state.settings.defaultLane) }
    var depthUnit by remember { mutableStateOf(state.settings.defaultDepthUnit) }
    var precision by remember { mutableStateOf(state.settings.decimalPrecision.toString()) }
    var shareExpiry by remember { mutableStateOf(state.settings.sharingCodeExpiryHours.toString()) }
    var includePhotos by remember { mutableStateOf(state.settings.includePhotosInPdf) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "Site Register Settings",
            subtitle = "Defaults, Units & Export Preferences",
            onBack = onBack
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError) { vm.clearBanner() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SrSurfaceCard {
                SrSectionHeader("PROJECT & FIELD DEFAULTS")
                SrTextField(contractor, { contractor = it }, "Default Contractor Name")
                SrTextField(lane, { lane = it }, "Default Lane / Carriageway")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrPillChip("Day Shift", shift == "DAY", { shift = "DAY" })
                    SrPillChip("Night Shift", shift == "NIGHT", { shift = "NIGHT" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Default Depth Unit:", color = c.textSecondary, fontSize = 12.sp)
                    listOf(MeasurementUnit.MM, MeasurementUnit.CM, MeasurementUnit.M).forEach { u ->
                        SrPillChip(u.symbol, depthUnit == u, { depthUnit = u })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(precision, { precision = it }, "Decimal Precision (1-5)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    SrTextField(shareExpiry, { shareExpiry = it }, "Share Expiry (hrs)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includePhotos, onCheckedChange = { includePhotos = it })
                    Text("Include Quality & Observation Photos in PDF", color = c.textPrimary, fontSize = 12.5.sp)
                }
                SrPrimaryButton(
                    text = "Save Site Register Preferences",
                    onClick = {
                        vm.updateSettings(
                            state.settings.copy(
                                defaultContractor = contractor.trim(),
                                defaultShift = shift,
                                defaultLane = lane.trim().ifBlank { "MCW" },
                                defaultDepthUnit = depthUnit,
                                decimalPrecision = (precision.toIntOrNull() ?: 3).coerceIn(1, 5),
                                sharingCodeExpiryHours = (shareExpiry.toIntOrNull() ?: 24).coerceIn(1, 720),
                                includePhotosInPdf = includePhotos
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
