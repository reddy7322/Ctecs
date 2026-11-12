package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.AlignmentDiagnostics
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.theme.DialogThemeTokens
import com.example.ui.theme.isAppDarkTheme
import com.example.ui.theme.rememberDialogColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Unified Chainage Control Dialog — KMZ Chainage Correction & Calibration.
 *
 * Strictly focused on:
 * 1. Local storage KMZ/KML file selection & import via Android Storage Access Framework (SAF).
 * 2. Real-time KMZ/KML alignment status, marker range, and processing diagnostics.
 * 3. Precision KMZ Chainage Correction & Calibration (reference marker / GPS chainage calibration,
 *    offset delta calculation, piecewise interpolation control points, and one-tap reset).
 */
@Composable
fun ChainageManagementDialog(
    viewModel: MainViewModel,
    matchResult: ChainageMatchResult,
    activeRoute: RouteData?,
    currentGpsPoint: GeoPoint?,
    currentGpsAccuracy: Float,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dialogColors = rememberDialogColors()
    val primaryAccent = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val greenAccent = Color(0xFF10B981)
    val amberAccent = Color(0xFFF59E0B)
    val redAccent = Color(0xFFEF4444)

    val isImportingRoute by viewModel.isImportingRoute.collectAsStateWithLifecycle()
    val routeImportStatus by viewModel.routeImportStatus.collectAsStateWithLifecycle()
    val routeImportError by viewModel.routeImportError.collectAsStateWithLifecycle()

    var showResetCalibrationConfirm by remember { mutableStateOf(false) }
    var showRemoveAlignmentConfirm by remember { mutableStateOf(false) }

    // Android Storage Access Framework (SAF) OpenDocument launcher for .kmz and .kml files
    val safKmzKmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val resolvedName = resolveSafDisplayName(context, uri)
            viewModel.importRoute(uri, resolvedName)
        }
    }

    val launchKmzFilePicker: () -> Unit = {
        if (!isImportingRoute) {
            try {
                safKmzKmlLauncher.launch(
                    arrayOf(
                        "application/vnd.google-earth.kmz",
                        "application/vnd.google-earth.kml+xml",
                        "application/zip",
                        "application/xml",
                        "text/xml",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            } catch (_: Exception) {
                safKmzKmlLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = dialogColors.containerColor,
            border = BorderStroke(1.dp, dialogColors.borderColor),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.92f)
                .imePadding()
                .testTag("chainage_management_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ---------------------------------------------------------
                // FLAT HEADER BAR
                // ---------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryAccent.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Chainage Control",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.titleColor
                            )
                            Text(
                                text = "KMZ Chainage Correction & Calibration",
                                style = MaterialTheme.typography.labelSmall,
                                color = dialogColors.textSecondaryColor
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChainageSourceBadge(
                            source = matchResult.source,
                            isDark = dialogColors.isDark
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("close_chainage_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Chainage Control",
                                tint = dialogColors.textSecondaryColor
                            )
                        }
                    }
                }

                HorizontalDivider(color = dialogColors.dividerColor, thickness = 1.dp)

                // ---------------------------------------------------------
                // SCROLLABLE MAIN CONTENT
                // ---------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. KMZ / KML FILE IMPORT & ALIGNMENT STATUS SECTION
                    KmzAlignmentFileSection(
                        activeRoute = activeRoute,
                        isImporting = isImportingRoute,
                        importStatusMessage = routeImportStatus,
                        importErrorMessage = routeImportError,
                        primaryAccent = primaryAccent,
                        greenAccent = greenAccent,
                        redAccent = redAccent,
                        dialogColors = dialogColors,
                        onImportOrReplaceClick = launchKmzFilePicker,
                        onRemoveClick = { showRemoveAlignmentConfirm = true },
                        onDismissStatus = { viewModel.clearRouteImportMessages() },
                        onSelectAlignment = { alignId -> viewModel.selectAlignment(alignId) }
                    )

                    // 2. LIVE CHAINAGE & CALIBRATION STATUS SECTION
                    LiveCalibrationStatusSection(
                        activeRoute = activeRoute,
                        matchResult = matchResult,
                        correctionPoints = correctionPoints,
                        isCorrectionsEnabled = isCorrectionsEnabled,
                        primaryAccent = primaryAccent,
                        greenAccent = greenAccent,
                        amberAccent = amberAccent,
                        dialogColors = dialogColors,
                        onToggleEnabled = { enabled ->
                            viewModel.setRouteCorrectionsEnabled(enabled)
                        },
                        onRefreshGps = {
                            viewModel.refreshLocation()
                        }
                    )

                    // 3. UNIFIED KMZ CHAINAGE CORRECTION & CALIBRATION INPUT CARD
                    KmzChainageCalibrationSection(
                        activeRoute = activeRoute,
                        matchResult = matchResult,
                        currentGpsPoint = currentGpsPoint,
                        correctionPoints = correctionPoints,
                        isCorrectionsEnabled = isCorrectionsEnabled,
                        primaryAccent = primaryAccent,
                        greenAccent = greenAccent,
                        redAccent = redAccent,
                        dialogColors = dialogColors,
                        onImportClick = launchKmzFilePicker,
                        onSavePoint = { point, isUpdate ->
                            if (isUpdate) {
                                viewModel.updateCorrectionPoint(point)
                            } else {
                                viewModel.addCorrectionPoint(point)
                            }
                        },
                        onTogglePoint = { pt, enabled ->
                            if (activeRoute != null) {
                                viewModel.toggleCorrectionPoint(activeRoute.id, pt.id, enabled)
                            }
                        },
                        onDeletePoint = { pt ->
                            if (activeRoute != null) {
                                viewModel.deleteCorrectionPoint(activeRoute.id, pt.id)
                            }
                        },
                        onResetAllClick = {
                            showResetCalibrationConfirm = true
                        },
                        onScrollToTop = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                    )
                }

                HorizontalDivider(color = dialogColors.dividerColor, thickness = 1.dp)

                // ---------------------------------------------------------
                // FLAT FOOTER BAR
                // ---------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeRoute != null) {
                            "${activeRoute.markers.size} refs • ${correctionPoints.count { it.isEnabled }} active calibration(s)"
                        } else {
                            "No KMZ/KML alignment loaded"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = dialogColors.textSecondaryColor
                    )

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("done_chainage_dialog_button")
                    ) {
                        Text(
                            text = "Done",
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog: Remove Active KMZ Alignment
    if (showRemoveAlignmentConfirm && activeRoute != null) {
        AlertDialog(
            onDismissRequest = { showRemoveAlignmentConfirm = false },
            containerColor = dialogColors.containerColor,
            title = {
                Text(
                    text = "Remove KMZ Alignment?",
                    fontWeight = FontWeight.Bold,
                    color = dialogColors.titleColor
                )
            },
            text = {
                Text(
                    text = "Remove '${activeRoute.fileName.ifBlank { activeRoute.name }}' from active Chainage Control? You can re-import a KMZ or KML file from local storage at any time.",
                    color = dialogColors.textSecondaryColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoute(activeRoute.id)
                        showRemoveAlignmentConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = redAccent)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAlignmentConfirm = false }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }

    // Confirmation Dialog: Reset All Calibration Points
    if (showResetCalibrationConfirm && activeRoute != null) {
        AlertDialog(
            onDismissRequest = { showResetCalibrationConfirm = false },
            containerColor = dialogColors.containerColor,
            title = {
                Text(
                    text = "Reset Chainage Calibration?",
                    fontWeight = FontWeight.Bold,
                    color = dialogColors.titleColor
                )
            },
            text = {
                Text(
                    text = "This will clear all ${correctionPoints.size} calibration point(s) for '${activeRoute.name}' and restore chainage calculations to the original unmodified KMZ alignment.",
                    color = dialogColors.textSecondaryColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllCorrectionsForRoute(activeRoute.id)
                        showResetCalibrationConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = redAccent)
                ) {
                    Text("Reset Calibration", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetCalibrationConfirm = false }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }
}

/**
 * Resolves the user-visible filename from a Storage Access Framework (SAF) URI via ContentResolver.
 */
private fun resolveSafDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name.trim()
                    }
                }
            }
        } catch (_: Exception) {}
    } else if (uri.scheme == "file") {
        val path = uri.path
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.name.isNotBlank()) return file.name
        }
    }
    val rawLast = uri.lastPathSegment ?: "imported_alignment.kmz"
    val decoded = try { Uri.decode(rawLast) } catch (_: Exception) { rawLast }
    return decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { "imported_alignment.kmz" }
}

// -----------------------------------------------------------------------------
// SECTION 1: KMZ / KML FILE IMPORT & ALIGNMENT STATUS
// -----------------------------------------------------------------------------
@Composable
private fun KmzAlignmentFileSection(
    activeRoute: RouteData?,
    isImporting: Boolean,
    importStatusMessage: String?,
    importErrorMessage: String?,
    primaryAccent: Color,
    greenAccent: Color,
    redAccent: Color,
    dialogColors: DialogThemeTokens,
    onImportOrReplaceClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onDismissStatus: () -> Unit,
    onSelectAlignment: (String?) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, dialogColors.borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kmz_alignment_file_section")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (activeRoute != null) Icons.Default.Route else Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (activeRoute != null) greenAccent else primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (activeRoute != null) "Active KMZ / KML Alignment" else "No KMZ / KML Alignment Loaded",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = dialogColors.titleColor
                        )
                        Text(
                            text = activeRoute?.fileName?.ifBlank { activeRoute.name }
                                ?: "Select a .kmz or .kml alignment file from local device storage",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (activeRoute != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (activeRoute != null) primaryAccent else dialogColors.textSecondaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (activeRoute != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = greenAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = greenAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Processing / Import Progress Indicator
            if (isImporting) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = primaryAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = importStatusMessage ?: "Extracting & parsing KMZ/KML alignment...",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        color = primaryAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            // Error Banner (if last import failed)
            if (!isImporting && !importErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = redAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, redAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = redAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = importErrorMessage,
                                fontSize = 11.sp,
                                color = if (dialogColors.isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                            )
                        }
                        IconButton(onClick = onDismissStatus, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = dialogColors.textSecondaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Success Status Banner
            if (!isImporting && importErrorMessage.isNullOrBlank() && !importStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = greenAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, greenAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = importStatusMessage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (dialogColors.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                            )
                        }
                        IconButton(onClick = onDismissStatus, modifier = Modifier.size(22.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss status",
                                tint = dialogColors.textSecondaryColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Alignment Metadata Summary when loaded
            if (activeRoute != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val startCh = activeRoute.startChainage?.format() ?: "---"
                val endCh = activeRoute.endChainage?.format() ?: "---"
                val totalKm = String.format(Locale.US, "%.2f km", activeRoute.totalCoverageKm)
                val diag = activeRoute.diagnostics

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (dialogColors.isDark) Color(0xFF0F172A) else Color.White,
                    border = BorderStroke(1.dp, dialogColors.borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Chainage Range", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                                Text(
                                    text = "$startCh → $endCh",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Markers", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                                Text(
                                    text = "${activeRoute.markers.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dialogColors.titleColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Coverage", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                                Text(
                                    text = totalKm,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dialogColors.titleColor
                                )
                            }
                        }

                        if (diag != null && (diag.rawLineStringCount > 0 || diag.rawPlacemarkCount > 0)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val dirLabel = if (diag.isChainageIncreasing) "Increasing" else "Decreasing"
                            Text(
                                text = "KML (${diag.selectedKmlEntryName}): ${diag.rawPointCount} Points • ${diag.rawLineStringCount} LineStrings • $dirLabel",
                                fontSize = 10.sp,
                                color = dialogColors.textSecondaryColor
                            )
                        }
                    }
                }

                // Multi-Alignment / Carriageway selector if route has > 1 alignment
                if (activeRoute.alignments.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = activeRoute.selectedAlignmentId == null,
                                onClick = { onSelectAlignment(null) },
                                label = { Text("Auto (All)", fontSize = 10.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = primaryAccent
                                )
                            )
                        }
                        items(activeRoute.alignments, key = { it.id }) { align ->
                            val isSelected = activeRoute.selectedAlignmentId == align.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectAlignment(align.id) },
                                label = {
                                    Text(
                                        text = "${align.name} (${align.carriagewayType})",
                                        fontSize = 10.5.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = primaryAccent
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Flat Action Row: Import / Replace KMZ/KML + Remove Alignment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onImportOrReplaceClick,
                    enabled = !isImporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag(if (activeRoute == null) "import_kmz_kml_button" else "replace_kmz_kml_button")
                ) {
                    Icon(
                        imageVector = if (activeRoute == null) Icons.Default.FileUpload else Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isImporting -> "Importing..."
                            activeRoute == null -> "Import KMZ / KML"
                            else -> "Replace KMZ / KML"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (activeRoute != null) {
                    OutlinedButton(
                        onClick = onRemoveClick,
                        enabled = !isImporting,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = redAccent),
                        border = BorderStroke(1.dp, redAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("remove_kmz_alignment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Alignment",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECTION 2: LIVE CHAINAGE & CALIBRATION STATUS
// -----------------------------------------------------------------------------
@Composable
private fun LiveCalibrationStatusSection(
    activeRoute: RouteData?,
    matchResult: ChainageMatchResult,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    primaryAccent: Color,
    greenAccent: Color,
    amberAccent: Color,
    dialogColors: DialogThemeTokens,
    onToggleEnabled: (Boolean) -> Unit,
    onRefreshGps: () -> Unit
) {
    val rawKmzChainage = matchResult.rawUncorrectedChainage ?: matchResult.chainage
    val calibratedChainage = matchResult.chainage
    val activeDeltaMetres = if (rawKmzChainage != null && calibratedChainage != null) {
        calibratedChainage.metres - rawKmzChainage.metres
    } else 0L

    val (confBg, confText, confLabel) = when (matchResult.confidence) {
        MatchConfidence.HIGH -> Triple(Color(0xFF065F46), Color(0xFF34D399), "High Confidence")
        MatchConfidence.MEDIUM -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), "Medium Confidence")
        MatchConfidence.LOW -> Triple(Color(0xFF7F1D1D), Color(0xFFF87171), "Low Confidence")
        MatchConfidence.NO_MATCH -> Triple(Color(0xFF374151), Color(0xFF9CA3AF), if (activeRoute == null) "Awaiting KMZ" else "Off Route / No GPS")
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (dialogColors.isDark) Color(0xFF131D2E) else Color(0xFFF0F9FF),
        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = RoundedCornerShape(4.dp), color = confBg) {
                        Text(
                            text = if (matchResult.isUncertain) "Uncertain Jump" else confLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = confText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Side: ${matchResult.detectedSide.ifBlank { "RHS" }}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogColors.textSecondaryColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isCorrectionsEnabled) "Calibration ON" else "Calibration OFF",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrectionsEnabled) greenAccent else dialogColors.textSecondaryColor
                    )
                    Switch(
                        checked = isCorrectionsEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = primaryAccent),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("toggle_smart_corrections")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Original KMZ Chainage vs Calibrated Chainage Box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color.White,
                border = BorderStroke(1.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Original KMZ Chainage",
                            fontSize = 10.sp,
                            color = dialogColors.textSecondaryColor
                        )
                        Text(
                            text = rawKmzChainage?.format() ?: "---",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = dialogColors.titleColor
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val deltaStr = when {
                            activeDeltaMetres > 0 -> "+${activeDeltaMetres}m"
                            activeDeltaMetres < 0 -> "${activeDeltaMetres}m"
                            else -> "0m"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (activeDeltaMetres != 0L) greenAccent.copy(alpha = 0.15f) else dialogColors.borderColor.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = deltaStr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeDeltaMetres != 0L) greenAccent else dialogColors.textSecondaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Calibrated Output",
                            fontSize = 10.sp,
                            color = primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = calibratedChainage?.format() ?: "---",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = primaryAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // GPS Accuracy, Perpendicular Offset, and Refresh Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "GPS: ±%.1fm • Alignment Offset: %.1fm",
                        matchResult.gpsAccuracy,
                        matchResult.offsetMetres
                    ),
                    fontSize = 10.5.sp,
                    color = dialogColors.textSecondaryColor
                )

                TextButton(
                    onClick = onRefreshGps,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh GPS",
                        tint = primaryAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh GPS", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = primaryAccent)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECTION 3: UNIFIED KMZ CHAINAGE CORRECTION & CALIBRATION WORKFLOW
// -----------------------------------------------------------------------------
@Composable
private fun KmzChainageCalibrationSection(
    activeRoute: RouteData?,
    matchResult: ChainageMatchResult,
    currentGpsPoint: GeoPoint?,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    primaryAccent: Color,
    greenAccent: Color,
    redAccent: Color,
    dialogColors: DialogThemeTokens,
    onImportClick: () -> Unit,
    onSavePoint: (ChainageCorrectionPoint, Boolean) -> Unit,
    onTogglePoint: (ChainageCorrectionPoint, Boolean) -> Unit,
    onDeletePoint: (ChainageCorrectionPoint) -> Unit,
    onResetAllClick: () -> Unit,
    onScrollToTop: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val liveRawChainage = matchResult.rawUncorrectedChainage ?: matchResult.chainage
    val defaultChainageStr = remember(activeRoute?.id, liveRawChainage) {
        liveRawChainage?.format()
            ?: activeRoute?.startChainage?.format()
            ?: "130+000"
    }

    var editingPoint by remember { mutableStateOf<ChainageCorrectionPoint?>(null) }
    var originalKmzInput by rememberSaveable(activeRoute?.id) { mutableStateOf(defaultChainageStr) }
    var correctedInput by rememberSaveable(activeRoute?.id) { mutableStateOf(defaultChainageStr) }
    var descriptionInput by rememberSaveable(activeRoute?.id) { mutableStateOf("") }
    var showMarkerDropdown by remember { mutableStateOf(false) }

    // Keep default input synced when route first loads if user hasn't edited yet
    LaunchedEffect(activeRoute?.id) {
        if (activeRoute != null) {
            val initialCh = (matchResult.rawUncorrectedChainage ?: matchResult.chainage ?: activeRoute.startChainage)?.format() ?: "130+000"
            originalKmzInput = initialCh
            correctedInput = initialCh
            editingPoint = null
        }
    }

    val parsedOriginal = remember(originalKmzInput) { Chainage.parse(originalKmzInput) }
    val parsedCorrected = remember(correctedInput) { Chainage.parse(correctedInput) }

    val calculatedOffset = remember(parsedOriginal, parsedCorrected) {
        if (parsedOriginal != null && parsedCorrected != null) {
            parsedCorrected.metres - parsedOriginal.metres
        } else 0L
    }

    val resolvedPosition = remember(activeRoute, parsedOriginal, currentGpsPoint) {
        if (activeRoute != null && parsedOriginal != null) {
            SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
                routeData = activeRoute,
                targetKmzMetres = parsedOriginal.metres,
                fallbackGps = currentGpsPoint
            )
        } else null
    }

    val tentativePoint = remember(editingPoint, activeRoute?.id, resolvedPosition, currentGpsPoint, parsedOriginal, parsedCorrected, descriptionInput) {
        ChainageCorrectionPoint(
            id = editingPoint?.id ?: UUID.randomUUID().toString(),
            routeId = activeRoute?.id ?: "pending-route",
            latitude = resolvedPosition?.point?.latitude ?: currentGpsPoint?.latitude ?: editingPoint?.latitude ?: 0.0,
            longitude = resolvedPosition?.point?.longitude ?: currentGpsPoint?.longitude ?: editingPoint?.longitude ?: 0.0,
            originalKmzMetres = parsedOriginal?.metres ?: 0L,
            correctedMetres = parsedCorrected?.metres ?: 0L,
            routeDistanceMetres = resolvedPosition?.routeDistanceMetres ?: editingPoint?.routeDistanceMetres ?: 0.0,
            description = descriptionInput.trim().ifBlank {
                val ch = parsedCorrected?.format() ?: ""
                if (ch.isNotBlank()) "Calibrated at $ch" else "KMZ Calibration Point"
            },
            isEnabled = editingPoint?.isEnabled ?: true
        )
    }

    val validation = remember(tentativePoint, correctionPoints, activeRoute) {
        if (activeRoute != null && parsedOriginal != null && parsedCorrected != null) {
            SmartChainageCorrectionEngine.validatePoint(tentativePoint, correctionPoints, activeRoute)
        } else null
    }

    // ---------------------------------------------------------
    // CALIBRATION INPUT CARD (ALWAYS VISIBLE IN CHAINAGE CONTROL)
    // ---------------------------------------------------------
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (dialogColors.isDark) Color(0xFF1E293B) else Color.White,
        border = BorderStroke(1.2.dp, primaryAccent.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kmz_chainage_correction_input")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (editingPoint != null) {
                            "Edit Calibration Point (${editingPoint?.originalKmzChainage?.format()})"
                        } else {
                            "KMZ Chainage Calibration Input"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (liveRawChainage != null) {
                        TextButton(
                            onClick = {
                                originalKmzInput = liveRawChainage.format()
                                if (correctedInput.isBlank()) {
                                    correctedInput = liveRawChainage.format()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(12.dp), tint = primaryAccent)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Use GPS (${liveRawChainage.format()})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryAccent)
                        }
                    }

                    if (editingPoint != null) {
                        TextButton(
                            onClick = {
                                editingPoint = null
                                descriptionInput = ""
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Cancel Edit", fontSize = 10.sp, color = redAccent)
                        }
                    }
                }
            }

            // Reference Marker Quick Selector (when KMZ has markers)
            if (activeRoute != null && activeRoute.markers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Range: ${activeRoute.startChainage?.format() ?: "0+000"} → ${activeRoute.endChainage?.format() ?: "---"}",
                        fontSize = 10.5.sp,
                        color = dialogColors.textSecondaryColor
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showMarkerDropdown = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("pick_reference_marker_button")
                        ) {
                            Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(12.dp), tint = primaryAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick KMZ Marker (${activeRoute.markers.size})", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = primaryAccent)
                        }

                        DropdownMenu(
                            expanded = showMarkerDropdown,
                            onDismissRequest = { showMarkerDropdown = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            // Sample up to 60 evenly spaced + boundary markers so dropdown stays fast even with 1,599 markers
                            val markers = activeRoute.markers
                            val sampledIndices = remember(markers.size) {
                                if (markers.size <= 60) {
                                    markers.indices.toList()
                                } else {
                                    val step = (markers.size - 1).toDouble() / 58.0
                                    val set = LinkedHashSet<Int>()
                                    set.add(0)
                                    for (k in 1..57) {
                                        set.add((k * step).toInt().coerceIn(0, markers.lastIndex))
                                    }
                                    if (markers.size >= 2) set.add(markers.lastIndex - 1)
                                    set.add(markers.lastIndex)
                                    set.toList()
                                }
                            }
                            sampledIndices.forEach { idx ->
                                val m = markers[idx]
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${m.chainage.format()} (${m.label})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        originalKmzInput = m.chainage.format()
                                        correctedInput = m.chainage.format()
                                        showMarkerDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Field 1: Original KMZ Chainage
            OutlinedTextField(
                value = originalKmzInput,
                onValueChange = { originalKmzInput = it },
                label = { Text("Original KMZ Chainage (Reference Position)") },
                placeholder = { Text("e.g. 130+000, 196+040, or 209+883") },
                trailingIcon = {
                    if (originalKmzInput.isNotBlank()) {
                        IconButton(onClick = { originalKmzInput = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear original chainage", tint = dialogColors.textSecondaryColor, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = dialogColors.textColor,
                    unfocusedTextColor = dialogColors.textColor,
                    focusedBorderColor = primaryAccent,
                    unfocusedBorderColor = dialogColors.inputBorder,
                    focusedLabelColor = primaryAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("orig_kmz_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Input Field 2: Calibrated / Corrected True Chainage
            OutlinedTextField(
                value = correctedInput,
                onValueChange = { correctedInput = it },
                label = { Text("Calibrated / Corrected True Chainage") },
                placeholder = { Text("e.g. 130+050 or 196+100") },
                trailingIcon = {
                    if (correctedInput.isNotBlank()) {
                        IconButton(onClick = { correctedInput = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear calibrated chainage", tint = dialogColors.textSecondaryColor, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = dialogColors.textColor,
                    unfocusedTextColor = dialogColors.textColor,
                    focusedBorderColor = primaryAccent,
                    unfocusedBorderColor = dialogColors.inputBorder,
                    focusedLabelColor = primaryAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("corrected_kmz_input")
            )

            // Live Calibration Offset Preview
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    calculatedOffset > 0L -> Color(0x1A10B981)
                    calculatedOffset < 0L -> Color(0x1AEF4444)
                    else -> if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shiftText = if (parsedOriginal != null && parsedCorrected != null) {
                        val sign = if (calculatedOffset >= 0) "+$calculatedOffset" else "$calculatedOffset"
                        val dirDesc = when {
                            calculatedOffset > 0 -> "(Forward Shift)"
                            calculatedOffset < 0 -> "(Backward Shift)"
                            else -> "(Zero Shift)"
                        }
                        "Calibration Offset: $sign m $dirDesc"
                    } else {
                        "Enter valid chainages (e.g. 130+000, 196+040)"
                    }

                    Text(
                        text = shiftText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (calculatedOffset >= 0) greenAccent else redAccent
                    )

                    if (parsedCorrected != null) {
                        Text(
                            text = "KM ${parsedCorrected.format()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent
                        )
                    }
                }
            }

            // Quick Shift Adjustment Chips
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(-100L, -50L, -10L, 10L, 50L, 100L).forEach { delta ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (dialogColors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val base = parsedCorrected?.metres ?: parsedOriginal?.metres ?: 0L
                                val newM = (base + delta).coerceAtLeast(0L)
                                correctedInput = Chainage(newM).format()
                            }
                    ) {
                        Text(
                            text = if (delta > 0) "+${delta}m" else "${delta}m",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = dialogColors.textColor,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Landmark / Milestone Description Input
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { descriptionInput = it },
                label = { Text("Landmark / Reference Note (Optional)") },
                placeholder = { Text("e.g. KM Stone 196, Culvert, Toll Plaza") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = dialogColors.textColor,
                    unfocusedTextColor = dialogColors.textColor,
                    focusedBorderColor = primaryAccent,
                    unfocusedBorderColor = dialogColors.inputBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Validation Warnings Banner
            if (validation != null && validation.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        validation.warnings.forEach { warn ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(13.dp))
                                Text(warn, fontSize = 10.5.sp, color = Color(0xFF92400E))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Apply / Save Calibration Button
            if (activeRoute == null) {
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("save_calibration_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import KMZ / KML First to Apply Calibration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSavePoint(tentativePoint, editingPoint != null)
                        editingPoint = null
                        descriptionInput = ""
                    },
                    enabled = parsedOriginal != null && parsedCorrected != null,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("save_calibration_button")
                ) {
                    Icon(
                        imageVector = if (editingPoint != null) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (editingPoint != null) "Update Calibration Point" else "Apply & Save Calibration Point",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // ROUTE ALIGNMENT SCHEMATIC & SAVED CALIBRATION POINTS
    // ---------------------------------------------------------
    if (activeRoute != null) {
        RouteCalibrationSchematicPreview(
            activeRoute = activeRoute,
            correctionPoints = correctionPoints,
            currentGpsPoint = currentGpsPoint,
            currentChainage = matchResult.chainage,
            primaryAccent = primaryAccent,
            dialogColors = dialogColors
        )

        // Header Row for Calibrated Control Points
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calibrated Control Points (${correctionPoints.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )

            if (correctionPoints.isNotEmpty()) {
                TextButton(
                    onClick = onResetAllClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = redAccent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("reset_calibration_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Calibration", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (correctionPoints.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFFAFAFA),
                border = BorderStroke(1.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Calibration Points Applied",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Chainage is currently using the unmodified KMZ alignment (${activeRoute.startChainage?.format() ?: "0+000"} → ${activeRoute.endChainage?.format() ?: "---"}). Enter a calibration above to apply an offset or multi-point interpolation.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = dialogColors.textSecondaryColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                correctionPoints.forEachIndexed { index, pt ->
                    val prevPt = if (index > 0) correctionPoints[index - 1] else null
                    val distFromPrev = if (prevPt != null) {
                        abs(pt.originalKmzMetres - prevPt.originalKmzMetres)
                    } else null

                    CorrectionPointCard(
                        index = index + 1,
                        point = pt,
                        distFromPrevMetres = distFromPrev,
                        primaryAccent = primaryAccent,
                        dialogColors = dialogColors,
                        onToggle = { onTogglePoint(pt, it) },
                        onEdit = {
                            editingPoint = pt
                            originalKmzInput = pt.originalKmzChainage.format()
                            correctedInput = pt.correctedChainage.format()
                            descriptionInput = pt.description
                            onScrollToTop()
                        },
                        onDelete = { onDeletePoint(pt) }
                    )
                }
            }

            // Export Row (Export Calibrated KMZ / JSON)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        exportCalibratedKmzFile(context, activeRoute, correctionPoints, coroutineScope)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryAccent),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("export_calibrated_kmz_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export Calibrated KMZ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        exportCorrectionPoints(context, activeRoute, correctionPoints)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dialogColors.textColor),
                    border = BorderStroke(1.dp, dialogColors.borderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("export_calibration_json_button")
                ) {
                    Text("Export JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// VISUAL SCHEMATIC / CORRIDOR PREVIEW COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun RouteCalibrationSchematicPreview(
    activeRoute: RouteData,
    correctionPoints: List<ChainageCorrectionPoint>,
    currentGpsPoint: GeoPoint?,
    currentChainage: Chainage?,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    modifier: Modifier = Modifier
) {
    val minMetres = activeRoute.startChainage?.metres
        ?: correctionPoints.minOfOrNull { it.originalKmzMetres }
        ?: 0L
    val rawMax = activeRoute.endChainage?.metres
        ?: correctionPoints.maxOfOrNull { it.originalKmzMetres }
        ?: (minMetres + 10000L)
    val maxMetres = if (rawMax <= minMetres) minMetres + 5000L else rawMax
    val spanMetres = (maxMetres - minMetres).toFloat().coerceAtLeast(100f)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, if (dialogColors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Corridor Calibration Schematic",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor,
                        fontSize = 11.sp
                    )
                }

                val totalKmStr = String.format(Locale.US, "%.1f km", abs(maxMetres - minMetres) / 1000.0)
                Text(
                    text = "${Chainage(minMetres).format()} → ${Chainage(maxMetres).format()} ($totalKmStr)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = primaryAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                val padX = 16.dp.toPx()
                val availW = (size.width - 2 * padX).coerceAtLeast(10f)
                val trackY = size.height * 0.5f

                val roadColor = if (dialogColors.isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                val roadHighlight = primaryAccent.copy(alpha = 0.35f)
                val centerDashColor = if (dialogColors.isDark) Color(0xFF64748B) else Color.White

                drawLine(
                    color = roadColor,
                    start = Offset(padX, trackY),
                    end = Offset(padX + availW, trackY),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                if (correctionPoints.isNotEmpty()) {
                    val minPtM = correctionPoints.minOf { it.originalKmzMetres }
                    val maxPtM = correctionPoints.maxOf { it.originalKmzMetres }
                    val startFrac = ((minPtM - minMetres).toFloat() / spanMetres).coerceIn(0f, 1f)
                    val endFrac = ((maxPtM - minMetres).toFloat() / spanMetres).coerceIn(0f, 1f)
                    val startX = padX + startFrac * availW
                    val endX = (padX + endFrac * availW).coerceAtLeast(startX + 6.dp.toPx())

                    drawLine(
                        color = roadHighlight,
                        start = Offset(startX, trackY),
                        end = Offset(endX, trackY),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                drawLine(
                    color = centerDashColor,
                    start = Offset(padX, trackY),
                    end = Offset(padX + availW, trackY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                )

                correctionPoints.forEach { pt ->
                    val frac = ((pt.originalKmzMetres - minMetres).toFloat() / spanMetres).coerceIn(0f, 1f)
                    val ptX = padX + frac * availW
                    if (pt.isEnabled) {
                        drawCircle(
                            color = Color(0xFF10B981).copy(alpha = 0.35f),
                            radius = 6.5.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                        drawCircle(
                            color = Color(0xFF10B981),
                            radius = 4.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF94A3B8),
                            radius = 3.5.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                    }
                }

                if (currentChainage != null) {
                    val gpsFrac = ((currentChainage.metres - minMetres).toFloat() / spanMetres).coerceIn(0f, 1f)
                    val gpsX = padX + gpsFrac * availW
                    drawCircle(
                        color = primaryAccent.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = Offset(gpsX, trackY)
                    )
                    drawCircle(
                        color = primaryAccent,
                        radius = 5.dp.toPx(),
                        center = Offset(gpsX, trackY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(gpsX, trackY)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// CORRECTION POINT ITEM CARD
// -----------------------------------------------------------------------------
@Composable
private fun CorrectionPointCard(
    index: Int,
    point: ChainageCorrectionPoint,
    distFromPrevMetres: Long?,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color.White
        ),
        border = BorderStroke(0.8.dp, if (point.isEnabled) primaryAccent.copy(alpha = 0.4f) else dialogColors.borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#$index",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent
                    )
                    Text(
                        text = "${point.originalKmzChainage.format()} → ${point.correctedChainage.format()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (point.isEnabled) dialogColors.titleColor else dialogColors.textSecondaryColor
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (point.offsetMetres >= 0) Color(0x3310B981) else Color(0x33EF4444)
                    ) {
                        Text(
                            text = point.displayOffset,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (point.offsetMetres >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                val desc = point.description.ifBlank { "Point at ${point.originalKmzChainage.format()}" }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = dialogColors.textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val distStr = if (distFromPrevMetres != null) "Span from prev: ${distFromPrevMetres}m" else "Anchor Point"
                Text(
                    text = distStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    color = dialogColors.textSecondaryColor.copy(alpha = 0.8f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit calibration point", tint = primaryAccent, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete calibration point", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
                Switch(
                    checked = point.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryAccent),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EXPORT HELPERS
// -----------------------------------------------------------------------------
private fun exportCorrectionPoints(
    context: Context,
    route: RouteData,
    points: List<ChainageCorrectionPoint>
) {
    try {
        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("routeId", route.id)
            put("routeName", route.name)
            put("routeFileName", route.fileName)
            val arr = JSONArray()
            points.forEach { arr.put(it.toJson()) }
            put("correctionPoints", arr)
        }
        val jsonStr = root.toString(2)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("CTECS Calibration JSON - ${route.name}", jsonStr)
        clipboard?.setPrimaryClip(clip)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${route.name}_calibration.json")
            putExtra(Intent.EXTRA_TEXT, jsonStr)
        }
        val chooser = Intent.createChooser(sendIntent, "Export KMZ Calibration JSON (${route.name})")
        context.startActivity(chooser)

        Toast.makeText(context, "Exported ${points.size} points. Copied to clipboard & share opened.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportCalibratedKmzFile(
    context: Context,
    route: RouteData,
    points: List<ChainageCorrectionPoint>,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        try {
            val file = SmartChainageCorrectionEngine.exportCalibratedKmz(context, route, points)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.google-earth.kmz"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Export Calibrated KMZ Alignment")
            context.startActivity(chooser)
            Toast.makeText(context, "Exported calibrated KMZ: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "KMZ Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// -----------------------------------------------------------------------------
// BADGE DISPLAYING ACTIVE SOURCE
// -----------------------------------------------------------------------------
@Composable
fun ChainageSourceBadge(
    source: ChainageSource,
    isDark: Boolean = isAppDarkTheme(),
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor, label) = when (source) {
        ChainageSource.MANUAL -> Quadruple(
            if (isDark) Color(0xFF78350F).copy(alpha = 0.5f) else Color(0xFFFEF3C7),
            if (isDark) Color(0xFFF59E0B) else Color(0xFFFCD34D),
            if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309),
            "MANUAL"
        )
        ChainageSource.CUSTOM_CORRECTION -> Quadruple(
            if (isDark) Color(0xFF4C1D95).copy(alpha = 0.5f) else Color(0xFFEDE9FE),
            if (isDark) Color(0xFF8B5CF6) else Color(0xFFC4B5FD),
            if (isDark) Color(0xFFDDD6FE) else Color(0xFF6D28D9),
            "CALIBRATED"
        )
        ChainageSource.KMZ -> Quadruple(
            if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.5f) else Color(0xFFE0F2FE),
            if (isDark) Color(0xFF0284C7) else Color(0xFFBAE6FD),
            if (isDark) Color(0xFFBAE6FD) else Color(0xFF0369A1),
            "KMZ"
        )
        ChainageSource.GPS -> Quadruple(
            if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
            if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
            if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            "GPS"
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
                color = textColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
