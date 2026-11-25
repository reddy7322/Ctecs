package com.example.ui.workplan

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BackupDuplicateResolution
import com.example.model.PdfExportScope
import com.example.ui.theme.CtecsTheme
import com.example.weather.WorkPlanBackupInspection
import java.io.File

/**
 * Dialog for selecting PDF report scope, previewing generated A4 pages in-app, saving via SAF,
 * sharing via FileProvider, or re-exporting.
 */
@Composable
fun WorkPlanPdfExportDialog(
    selectedScope: PdfExportScope,
    generatedPdfFile: File?,
    onSelectScope: (PdfExportScope) -> Unit,
    onGeneratePdf: (PdfExportScope) -> Unit,
    onSavePdfToDevice: (File) -> Unit,
    onSharePdf: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    var pageBitmaps by remember(generatedPdfFile?.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPageIdx by remember(generatedPdfFile?.absolutePath) { mutableIntStateOf(0) }

    DisposableEffect(generatedPdfFile?.absolutePath) {
        val loaded = mutableListOf<Bitmap>()
        if (generatedPdfFile != null && generatedPdfFile.exists()) {
            runCatching {
                ParcelFileDescriptor.open(generatedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount.coerceAtMost(10)
                        for (i in 0 until count) {
                            renderer.openPage(i).use { page ->
                                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                loaded.add(bmp)
                            }
                        }
                    }
                }
            }
        }
        pageBitmaps = loaded
        onDispose {
            loaded.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = c.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .heightIn(max = 680.dp)
                .testTag("work_plan_pdf_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = c.accent, modifier = Modifier.size(19.dp))
                        Column {
                            Text("Export A4 Work Plan PDF", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Generated from actual saved plan data", color = c.textMuted, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close PDF export", tint = c.textSecondary)
                    }
                }

                HorizontalDivider(color = c.subtleSeparator, thickness = 0.7.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("REPORT SCOPE", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    PdfExportScope.entries.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { scope ->
                                val active = scope == selectedScope
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) c.accent else c.secondarySurface)
                                        .clickable {
                                            onSelectScope(scope)
                                            onGeneratePdf(scope)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                        .testTag("pdf_scope_${scope.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = scope.label,
                                        color = if (active) c.onAccent else c.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (pageBitmaps.isNotEmpty()) {
                        val safeIdx = currentPageIdx.coerceIn(0, pageBitmaps.lastIndex)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Preview · Page ${safeIdx + 1} of ${pageBitmaps.size}", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            if (pageBitmaps.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { currentPageIdx = (safeIdx - 1).coerceAtLeast(0) },
                                        enabled = safeIdx > 0,
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("Prev", fontSize = 11.sp) }
                                    OutlinedButton(
                                        onClick = { currentPageIdx = (safeIdx + 1).coerceAtMost(pageBitmaps.lastIndex) },
                                        enabled = safeIdx < pageBitmaps.lastIndex,
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("Next", fontSize = 11.sp) }
                                }
                            }
                        }

                        Image(
                            bitmap = pageBitmaps[safeIdx].asImageBitmap(),
                            contentDescription = "PDF Page ${safeIdx + 1} preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .testTag("pdf_preview_image")
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.secondarySurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tap Generate PDF to build A4 preview.", color = c.textMuted, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onGeneratePdf(selectedScope) },
                            colors = ButtonDefaults.buttonColors(containerColor = c.secondarySurface, contentColor = c.textPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("pdf_btn_regenerate")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (generatedPdfFile == null) "Generate" else "Re-export", fontSize = 11.sp)
                        }
                        if (generatedPdfFile != null) {
                            Button(
                                onClick = { onSavePdfToDevice(generatedPdfFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("pdf_btn_save")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Save", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onSharePdf(generatedPdfFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = c.success, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("pdf_btn_share")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Share", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Import Backup confirmation dialog with integrity summary & explicit duplicate resolution (Skip / Replace / Import as New).
 * Never overwrites silently.
 */
@Composable
fun WorkPlanBackupImportDialog(
    inspection: WorkPlanBackupInspection,
    onConfirmImport: (BackupDuplicateResolution) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    var selectedResolution by remember { mutableStateOf(BackupDuplicateResolution.SKIP) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface,
            modifier = Modifier.fillMaxWidth().testTag("backup_import_confirm_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Import Work Plans Backup", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!inspection.isValid) {
                    Text(inspection.errorMessage ?: "Invalid backup file.", color = c.error, fontSize = 12.sp)
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                } else {
                    Text(
                        text = "Validated schema v${inspection.schemaVersion} · ${inspection.plans.size} plan(s) found.",
                        color = c.success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (inspection.duplicatePlanIds.isNotEmpty()) {
                        Text(
                            text = "${inspection.duplicatePlanIds.size} duplicate plan ID(s) detected. Choose how to handle duplicates:",
                            color = c.warning,
                            fontSize = 12.sp
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BackupDuplicateResolution.entries.forEach { res ->
                                val active = res == selectedResolution
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) c.accent else c.secondarySurface)
                                        .clickable { selectedResolution = res }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                        .testTag("import_dup_${res.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(res.label, color = if (active) c.onAccent else c.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = { onConfirmImport(selectedResolution) },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            modifier = Modifier.testTag("backup_import_confirm_btn")
                        ) {
                            Text("Confirm Import")
                        }
                    }
                }
            }
        }
    }
}
