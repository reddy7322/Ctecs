package com.example.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.donation.AdminSession
import com.example.donation.DonationActionResult
import com.example.donation.DonationAuditLogEntity
import com.example.donation.DonationConfig
import com.example.donation.DonationRepository
import com.example.donation.DonationStatus
import com.example.donation.DonationTransactionEntity
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminDateFilter(val label: String, val windowMs: Long?) {
    ALL("All Dates", null),
    TODAY("24h", 24L * 60 * 60 * 1000L),
    WEEK("7 Days", 7L * 24 * 60 * 60 * 1000L),
    MONTH("30 Days", 30L * 24 * 60 * 60 * 1000L)
}

/**
 * Protected Administrator Donation Verification & Audit panel.
 * Requires administrator authentication before exposing transactions, manual UTR review,
 * verified totals, or CSV report exports.
 */
@Composable
internal fun DonationAdminPanel(
    repository: DonationRepository,
    adminSession: AdminSession?,
    allTransactions: List<DonationTransactionEntity>,
    totalVerifiedInr: Double,
    auditLogs: List<DonationAuditLogEntity>,
    onFeedback: (String) -> Unit
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (adminSession == null) {
        val isFirstRunClaim = !repository.isBackendConfigured && !repository.hasLocalAdminClaimed
        var adminIdInput by remember { mutableStateOf("") }
        var passphraseInput by remember { mutableStateOf("") }
        var authError by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.secondarySurface)
                .padding(14.dp)
                .testTag("donation_admin_auth_gate"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Column {
                    Text(
                        text = if (isFirstRunClaim) "First-Run Admin Setup" else "Administrator Sign-In",
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFirstRunClaim) {
                            "Claim local admin access (PBKDF2 hashed) to review pending UTRs."
                        } else {
                            "Authentication required to verify donations and export audit reports."
                        },
                        color = c.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            MinimalAdminInput(
                label = "Administrator ID",
                value = adminIdInput,
                placeholder = "e.g. madhu_admin",
                tag = "admin_login_id_input",
                onValueChange = { adminIdInput = it; authError = null }
            )
            MinimalAdminInput(
                label = "Passphrase (min 8 chars)",
                value = passphraseInput,
                placeholder = "Enter administrator passphrase",
                isPassword = true,
                tag = "admin_login_pass_input",
                onValueChange = { passphraseInput = it; authError = null }
            )

            authError?.let { err ->
                Text(err, color = c.error, fontSize = 11.5.sp, modifier = Modifier.testTag("admin_login_error"))
            }

            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    isSubmitting = true
                    scope.launch {
                        when (val res = repository.authenticateAdmin(adminIdInput, passphraseInput, isFirstRunClaim)) {
                            is DonationActionResult.Success -> {
                                passphraseInput = ""
                                onFeedback(res.message)
                            }
                            is DonationActionResult.Error -> authError = res.message
                        }
                        isSubmitting = false
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .testTag("admin_login_submit_btn")
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isFirstRunClaim) "Claim & Unlock Admin" else "Authenticate Admin",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        return
    }

    // Authenticated Admin Dashboard
    var statusFilter by remember { mutableStateOf<DonationStatus?>(null) }
    var dateFilter by remember { mutableStateOf(AdminDateFilter.ALL) }
    var reviewingOrderRef by remember { mutableStateOf<String?>(null) }
    var evidenceInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf<String?>(null) }

    val nowMs = System.currentTimeMillis()
    val filtered = remember(allTransactions, statusFilter, dateFilter) {
        allTransactions.filter { tx ->
            val statusOk = statusFilter == null || tx.parsedStatus == statusFilter
            val dateOk = dateFilter.windowMs == null || (nowMs - tx.createdAtMs) <= dateFilter.windowMs!!
            statusOk && dateOk
        }
    }
    val timeFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("donation_admin_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Admin Summary Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.secondarySurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("VERIFIED TOTAL", color = c.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = DonationConfig.formatInrAmount(totalVerifiedInr),
                    color = c.success,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("admin_total_verified_text")
                )
                Text(
                    text = "Admin: ${adminSession.adminId} · ${filtered.size} shown",
                    color = c.textSecondary,
                    fontSize = 10.5.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.accent.copy(alpha = 0.14f))
                        .clickable {
                            scope.launch {
                                when (val res = repository.exportAdminReportCsv(filtered)) {
                                    is DonationActionResult.Success -> {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, res.data)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(share, "Export Donation Report"))
                                        }
                                        onFeedback("CSV report ready.")
                                    }
                                    is DonationActionResult.Error -> onFeedback(res.message)
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("admin_export_csv_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Export CSV", tint = c.accent, modifier = Modifier.size(16.dp))
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.error.copy(alpha = 0.14f))
                        .clickable { repository.logoutAdmin() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("admin_logout_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = "Lock admin", tint = c.error, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Status Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdminFilterChip("All", statusFilter == null, "admin_filter_all") { statusFilter = null }
            listOf(
                DonationStatus.UNDER_REVIEW,
                DonationStatus.PENDING,
                DonationStatus.PAID,
                DonationStatus.FAILED,
                DonationStatus.CANCELLED
            ).forEach { st ->
                AdminFilterChip(
                    label = st.displayLabel,
                    selected = statusFilter == st,
                    tag = "admin_filter_${st.name.lowercase(Locale.US)}"
                ) { statusFilter = st }
            }
        }

        // Date Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdminDateFilter.entries.forEach { df ->
                AdminFilterChip(
                    label = df.label,
                    selected = dateFilter == df,
                    tag = "admin_date_${df.name.lowercase(Locale.US)}"
                ) { dateFilter = df }
            }
        }

        // Transactions List
        if (filtered.isEmpty()) {
            Text(
                text = "No donation transactions match the active filters.",
                color = c.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            filtered.forEach { tx ->
                val isReviewing = reviewingOrderRef == tx.orderRef
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.secondarySurface)
                        .padding(11.dp)
                        .testTag("admin_tx_${tx.orderRef}"),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${DonationConfig.formatInrAmount(tx.amountInr)} · ${tx.orderRef}",
                            color = c.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        DonationStatusBadge(tx.parsedStatus)
                    }
                    Text(
                        text = "${timeFmt.format(Date(tx.createdAtMs))} · To: ${tx.recipientName} (${tx.upiId})",
                        color = c.textSecondary,
                        fontSize = 11.sp
                    )
                    if (tx.utr.isNotBlank()) {
                        Text(
                            text = "Submitted UTR: ${tx.utr}",
                            color = c.coffeeAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (tx.parsedStatus != DonationStatus.PAID) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.accent.copy(alpha = 0.14f))
                                .clickable {
                                    reviewingOrderRef = if (isReviewing) null else tx.orderRef
                                    verifyError = null
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("admin_review_btn_${tx.orderRef}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
                            Text(
                                text = if (isReviewing) "Close Review" else "Verify with Evidence",
                                color = c.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (isReviewing) {
                        SmartFadeDivider(color = c.subtleSeparator, modifier = Modifier.padding(vertical = 4.dp))
                        MinimalAdminInput(
                            label = "Independent Bank/Statement Evidence",
                            value = evidenceInput,
                            placeholder = "e.g. Bank credit ref UTR 432198765432 verified",
                            tag = "admin_evidence_input",
                            onValueChange = { evidenceInput = it; verifyError = null }
                        )
                        MinimalAdminInput(
                            label = "Verification Reason (Audit Trail)",
                            value = reasonInput,
                            placeholder = "e.g. Matched INR credit on recipient statement",
                            tag = "admin_reason_input",
                            onValueChange = { reasonInput = it; verifyError = null }
                        )
                        verifyError?.let { Text(it, color = c.error, fontSize = 11.sp) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        when (
                                            val res = repository.adminVerifyTransaction(
                                                orderRef = tx.orderRef,
                                                approveAsPaid = true,
                                                independentEvidence = evidenceInput,
                                                verificationReason = reasonInput
                                            )
                                        ) {
                                            is DonationActionResult.Success -> {
                                                reviewingOrderRef = null
                                                evidenceInput = ""
                                                reasonInput = ""
                                                onFeedback(res.message)
                                            }
                                            is DonationActionResult.Error -> verifyError = res.message
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = c.success, contentColor = Color.White),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_approve_paid_btn")
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Verify PAID", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        when (
                                            val res = repository.adminVerifyTransaction(
                                                orderRef = tx.orderRef,
                                                approveAsPaid = false,
                                                independentEvidence = evidenceInput,
                                                verificationReason = reasonInput
                                            )
                                        ) {
                                            is DonationActionResult.Success -> {
                                                reviewingOrderRef = null
                                                evidenceInput = ""
                                                reasonInput = ""
                                                onFeedback(res.message)
                                            }
                                            is DonationActionResult.Error -> verifyError = res.message
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.error.copy(alpha = 0.16f),
                                    contentColor = c.error
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_mark_failed_btn")
                            ) {
                                Text("Reject", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Recent Immutable Audit Log Entries
        if (auditLogs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "AUDIT HISTORY (${auditLogs.size})",
                color = c.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
            auditLogs.take(5).forEach { log ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.secondarySurface)
                        .padding(9.dp)
                ) {
                    Text(
                        text = "${log.orderRef}: ${log.previousStatus} → ${log.newStatus} (${log.actorRole})",
                        color = c.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${log.reason} · ${log.evidence}",
                        color = c.textMuted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminFilterChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val c = CtecsTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accent else c.secondarySurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            color = if (selected) c.onAccent else c.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun MinimalAdminInput(
    label: String,
    value: String,
    placeholder: String,
    tag: String,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val c = CtecsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(tag)
            )
        }
    }
}
