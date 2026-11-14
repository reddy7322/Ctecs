package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.backup.BackupCategory
import com.example.backup.BackupHistoryItem
import com.example.backup.BackupPreviewInfo
import com.example.backup.BackupRestoreReport
import com.example.backup.BackupValidationStatus
import com.example.backup.RestoreMode
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import com.example.ui.theme.isAppDarkTheme
import com.example.ui.theme.rememberDialogColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreContent(
    viewModel: MainViewModel,
    accentColor: Color,
    accentContentColor: Color
) {
    val context = LocalContext.current
    val isDark = isAppDarkTheme()
    val history by viewModel.backupHistory.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val progress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val progressMessage by viewModel.backupProgressMessage.collectAsStateWithLifecycle()
    val lastExportResult by viewModel.lastBackupExportResult.collectAsStateWithLifecycle()
    val pendingPreview by viewModel.pendingBackupPreview.collectAsStateWithLifecycle()
    val lastRestoreReport by viewModel.lastRestoreReport.collectAsStateWithLifecycle()

    var defaultRestoreMode by remember { mutableStateOf(RestoreMode.COMPLETE) }
    var itemPendingDelete by remember { mutableStateOf<BackupHistoryItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshBackupHistory()
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportSettingsBackup(uri)
        }
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.inspectBackupUriForRestore(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Two Prominent Actions: Export Backup & Import & Restore
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val fileName = viewModel.settingsBackupManager.generateDefaultBackupFileName()
                    exportSafLauncher.launch(fileName)
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = accentContentColor
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("backup_export_button")
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            OutlinedButton(
                onClick = {
                    importSafLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, accentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("backup_import_button")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import & Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Background progress feedback
        if (isBusy) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = progressMessage ?: "Processing backup...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        // 2. Last Backup Info & Validation Status
        val latestItem = history.firstOrNull()
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Backup Info",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (latestItem != null) {
                        ValidationStatusBadge(status = latestItem.validationStatus)
                    } else if (lastExportResult != null && !lastExportResult!!.success) {
                        ValidationStatusBadge(status = BackupValidationStatus.MALFORMED_ARCHIVE)
                    }
                }

                if (latestItem != null) {
                    Text(
                        text = latestItem.fileName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatBackupDate(latestItem.createdAtEpochMs)} • v${latestItem.appVersionName} • ${formatBytes(latestItem.sizeBytes)} • ${latestItem.totalSettingsCount} settings",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = latestItem.validationSummary,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No backups created on this device yet. Tap Export Backup to save all settings cleanly.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Restore Options
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.30f else 0.38f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Restore Options",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = defaultRestoreMode == RestoreMode.COMPLETE,
                        onClick = { defaultRestoreMode = RestoreMode.COMPLETE },
                        label = { Text("Complete Restore", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = accentContentColor
                        ),
                        modifier = Modifier.testTag("restore_mode_complete_chip")
                    )
                    FilterChip(
                        selected = defaultRestoreMode == RestoreMode.SELECTIVE,
                        onClick = { defaultRestoreMode = RestoreMode.SELECTIVE },
                        label = { Text("Selective Categories", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = accentContentColor
                        ),
                        modifier = Modifier.testTag("restore_mode_selective_chip")
                    )
                }
                Text(
                    text = if (defaultRestoreMode == RestoreMode.COMPLETE) {
                        "Restores all backed-up categories safely while preserving existing media, projects & unbacked items."
                    } else {
                        "Choose specific categories (General, Camera, Mini Map, Calibration, DPR, etc.) during import preview."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4. Backup History
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "Backup History (${history.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = {
                    val fileName = viewModel.settingsBackupManager.generateDefaultBackupFileName()
                    exportSafLauncher.launch(fileName)
                },
                enabled = !isBusy
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Backup", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
            }
        }

        if (history.isEmpty()) {
            Text(
                text = "Your exported backups will appear here for one-tap restore, sharing, or management.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEach { item ->
                    BackupHistoryCard(
                        item = item,
                        accentColor = accentColor,
                        isBusy = isBusy,
                        onRestoreClick = { viewModel.inspectHistoryItemForRestore(item) },
                        onShareClick = {
                            val shareUri = viewModel.getShareBackupUri(item)
                            if (shareUri != null) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(Intent.EXTRA_SUBJECT, item.fileName)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share CTECS Settings Backup"))
                            } else {
                                viewModel.showToast("Unable to share backup file", ToastType.ERROR)
                            }
                        },
                        onDeleteClick = { itemPendingDelete = item }
                    )
                }
            }
        }
    }

    // Preview & Selective/Complete Confirmation Dialog
    pendingPreview?.let { preview ->
        BackupPreviewConfirmationDialog(
            preview = preview,
            initialMode = defaultRestoreMode,
            accentColor = accentColor,
            accentContentColor = accentContentColor,
            onConfirmRestore = { mode, selectedCategories ->
                viewModel.confirmRestoreFromPreview(mode, selectedCategories)
            },
            onDismiss = { viewModel.dismissBackupPreview() }
        )
    }

    // Detailed Restore Summary Dialog
    lastRestoreReport?.let { report ->
        BackupRestoreSummaryDialog(
            report = report,
            accentColor = accentColor,
            onDismiss = { viewModel.dismissRestoreReport() }
        )
    }

    // Delete Backup Confirmation Dialog
    itemPendingDelete?.let { target ->
        val dialogColors = rememberDialogColors()
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            shape = dialogColors.shape,
            containerColor = dialogColors.containerColor,
            title = { Text("Delete Backup File?", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
            text = {
                Text(
                    "Remove '${target.fileName}' from device backup history? Your current app settings and saved media will not be affected.",
                    color = dialogColors.textSecondaryColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBackupHistoryItem(target)
                        itemPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }
}

@Composable
private fun BackupHistoryCard(
    item: BackupHistoryItem,
    accentColor: Color,
    isBusy: Boolean,
    onRestoreClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                ValidationStatusBadge(status = item.validationStatus)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatBackupDate(item.createdAtEpochMs)} • v${item.appVersionName} • ${formatBytes(item.sizeBytes)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRestoreClick,
                    enabled = !isBusy && (item.validationStatus == BackupValidationStatus.VALID || item.validationStatus == BackupValidationStatus.VALID_WITH_WARNINGS),
                    modifier = Modifier.testTag("history_restore_${item.fileName}")
                ) {
                    Icon(Icons.Default.RestorePage, contentDescription = "Restore backup", modifier = Modifier.size(16.dp), tint = accentColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                }
                IconButton(
                    onClick = onShareClick,
                    enabled = !isBusy,
                    modifier = Modifier.size(48.dp).testTag("history_share_${item.fileName}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share backup", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onDeleteClick,
                    enabled = !isBusy,
                    modifier = Modifier.size(48.dp).testTag("history_delete_${item.fileName}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete backup", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun BackupPreviewConfirmationDialog(
    preview: BackupPreviewInfo,
    initialMode: RestoreMode,
    accentColor: Color,
    accentContentColor: Color,
    onConfirmRestore: (RestoreMode, Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColors = rememberDialogColors()
    var restoreMode by remember(preview) { mutableStateOf(initialMode) }
    var selectedCategories by remember(preview) {
        mutableStateOf(preview.availableCategories.ifEmpty { BackupCategory.entries.toSet() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (preview.isRestorable) Icons.Default.VerifiedUser else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (preview.isRestorable) accentColor else MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (preview.isRestorable) "Confirm Backup Restore" else "Invalid Backup File",
                    fontWeight = FontWeight.Bold,
                    color = dialogColors.titleColor
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(preview.sourceLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dialogColors.textColor, modifier = Modifier.weight(1f))
                    ValidationStatusBadge(status = preview.validationStatus)
                }
                Text(preview.validationMessage, fontSize = 12.sp, color = dialogColors.textSecondaryColor)

                preview.manifest?.let { m ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Created: ${formatBackupDate(m.createdAtEpochMs)}", fontSize = 11.sp, color = dialogColors.textColor)
                            Text("Source App: v${m.appVersionName} • Device: ${m.deviceManufacturer} ${m.deviceModel}", fontSize = 11.sp, color = dialogColors.textSecondaryColor)
                            Text("Contents: ${m.totalSettingsCount} settings • ${m.calibrationProfileCount} calibration profiles • ${m.controlPointCount} control points • ${m.supportingFiles.size} asset(s)", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (preview.warnings.isNotEmpty() || preview.adaptedSettingsPreview.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Compatibility Notes", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            (preview.warnings + preview.adaptedSettingsPreview).forEach { note ->
                                Text("• $note", fontSize = 11.sp, color = dialogColors.textColor)
                            }
                        }
                    }
                }

                if (preview.isRestorable) {
                    HorizontalDivider(color = dialogColors.borderColor)
                    Text("Restore Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dialogColors.titleColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = restoreMode == RestoreMode.COMPLETE,
                            onClick = { restoreMode = RestoreMode.COMPLETE },
                            label = { Text("Complete", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = restoreMode == RestoreMode.SELECTIVE,
                            onClick = { restoreMode = RestoreMode.SELECTIVE },
                            label = { Text("Selective", fontSize = 11.sp) }
                        )
                    }

                    if (restoreMode == RestoreMode.SELECTIVE) {
                        Text("Select Categories to Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dialogColors.titleColor)
                        BackupCategory.entries.forEach { cat ->
                            val hasCategory = cat in preview.availableCategories
                            if (hasCategory) {
                                val checked = cat in selectedCategories
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategories = if (checked) {
                                                selectedCategories - cat
                                            } else {
                                                selectedCategories + cat
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedCategories = if (isChecked) {
                                                selectedCategories + cat
                                            } else {
                                                selectedCategories - cat
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cat.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dialogColors.textColor)
                                        Text(cat.description, fontSize = 10.sp, color = dialogColors.textSecondaryColor)
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Safety guarantee: Existing photos, videos, Room projects, KMZ files, and permanent control points will never be deleted.",
                        fontSize = 11.sp,
                        color = dialogColors.textSecondaryColor
                    )
                }
            }
        },
        confirmButton = {
            if (preview.isRestorable) {
                Button(
                    onClick = { onConfirmRestore(restoreMode, selectedCategories) },
                    enabled = restoreMode == RestoreMode.COMPLETE || selectedCategories.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = accentContentColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_backup_restore_button")
                ) {
                    Text("Restore Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (preview.isRestorable) "Cancel" else "Close", color = dialogColors.textSecondaryColor)
            }
        }
    )
}

@Composable
private fun BackupRestoreSummaryDialog(
    report: BackupRestoreReport,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val dialogColors = rememberDialogColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (report.success) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (report.success) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (report.success) "Restore Complete" else "Restore Failed",
                    fontWeight = FontWeight.Bold,
                    color = dialogColors.titleColor
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (report.success) {
                    Text("Settings Restored: ${report.restoredSettingsCount}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dialogColors.textColor)
                    Text("Categories: ${report.restoredCategories.joinToString { it.displayName }}", fontSize = 11.sp, color = dialogColors.textSecondaryColor)
                    if (report.restoredCalibrationProfilesCount > 0 || report.restoredControlPointsCount > 0) {
                        Text(
                            "Calibration Merged: ${report.restoredCalibrationProfilesCount} profile(s), ${report.restoredControlPointsCount} control point(s)",
                            fontSize = 11.sp,
                            color = accentColor
                        )
                    }
                    if (report.restoredSupportingFilesCount > 0) {
                        Text("Supporting Assets Restored: ${report.restoredSupportingFilesCount}", fontSize = 11.sp, color = dialogColors.textSecondaryColor)
                    }
                    if (report.adaptedSettings.isNotEmpty()) {
                        Text("Adapted for This Device:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                        report.adaptedSettings.forEach { Text("• $it", fontSize = 11.sp, color = dialogColors.textSecondaryColor) }
                    }
                    if (report.skippedSettings.isNotEmpty()) {
                        Text("Skipped:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dialogColors.textSecondaryColor)
                        report.skippedSettings.forEach { Text("• $it", fontSize = 11.sp, color = dialogColors.textSecondaryColor) }
                    }
                } else {
                    Text(report.errorMessage ?: "Unexpected restore error.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    if (report.rolledBack) {
                        Text("Automatic rollback succeeded: all previous settings remain unchanged.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = dialogColors.textColor)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ValidationStatusBadge(status: BackupValidationStatus) {
    val (bg, fg) = when (status) {
        BackupValidationStatus.VALID -> Color(0xFF10B981).copy(alpha = 0.16f) to Color(0xFF059669)
        BackupValidationStatus.VALID_WITH_WARNINGS -> Color(0xFFF59E0B).copy(alpha = 0.16f) to Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f) to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatBackupDate(epochMs: Long): String {
    if (epochMs <= 0L) return "Unknown date"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.2f MB", kb / 1024.0)
}
