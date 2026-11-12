package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageLogEntry
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.theme.DialogThemeTokens
import com.example.ui.theme.rememberDialogColors
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.rememberCoroutineScope
import com.example.model.MatchConfidence
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Unified Management Dialog for:
 * 1. Manual Chainage Override (Immediate input with flexible formats)
 * 2. Smart KMZ Chainage Calibration (Point-based route correction & interpolation)
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
    val dialogColors = rememberDialogColors()
    val auditLogs by viewModel.chainageAuditLog.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var pointToEdit by remember { mutableStateOf<ChainageCorrectionPoint?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val primaryAccent = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val onAccent = Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .heightIn(max = 740.dp)
            .testTag("chainage_management_dialog"),
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinearScale,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Chainage Control",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.titleColor
                            )
                            Text(
                                text = "Manual Input, KMZ Calibration & History",
                                style = MaterialTheme.typography.labelSmall,
                                color = dialogColors.textSecondaryColor
                            )
                        }
                    }

                    // Active Source Badge
                    ChainageSourceBadge(
                        source = matchResult.source,
                        isDark = dialogColors.isDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    contentColor = primaryAccent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = primaryAccent
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text("Manual", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(
                                    "KMZ (${correctionPoints.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(
                                    "History (${auditLogs.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    )
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (selectedTab) {
                    0 -> {
                        ManualChainageTab(
                            currentChainage = matchResult.chainage,
                            isManualActive = matchResult.source == ChainageSource.MANUAL,
                            primaryAccent = primaryAccent,
                            dialogColors = dialogColors,
                            onApplyManual = { inputStr ->
                                viewModel.setManualChainage(inputStr)
                                onDismiss()
                            },
                            onResetAuto = {
                                viewModel.clearManualChainage()
                                onDismiss()
                            }
                        )
                    }
                    1 -> {
                        SmartKmzCalibrationTab(
                            activeRoute = activeRoute,
                            matchResult = matchResult,
                            correctionPoints = correctionPoints,
                            isCorrectionsEnabled = isCorrectionsEnabled,
                            currentGpsPoint = currentGpsPoint,
                            primaryAccent = primaryAccent,
                            dialogColors = dialogColors,
                            onSelectAlignment = { viewModel.selectAlignment(it) },
                            onToggleMaster = { viewModel.setRouteCorrectionsEnabled(it) },
                            onTogglePoint = { pt, en -> viewModel.toggleCorrectionPoint(pt.routeId, pt.id, en) },
                            onEditPoint = { pt ->
                                pointToEdit = pt
                                showAddEditDialog = true
                            },
                            onDeletePoint = { pt -> viewModel.deleteCorrectionPoint(pt.routeId, pt.id) },
                            onAddNew = {
                                pointToEdit = null
                                showAddEditDialog = true
                            },
                            onResetAll = { showResetConfirmDialog = true }
                        )
                    }
                    else -> {
                        HistoryLogTab(
                            auditLogs = auditLogs,
                            activeRoute = activeRoute,
                            primaryAccent = primaryAccent,
                            dialogColors = dialogColors,
                            onClearHistory = { viewModel.clearChainageAuditLog() }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = dialogColors.textSecondaryColor, fontWeight = FontWeight.SemiBold)
            }
        }
    )

    // Sub-dialog: Add / Edit Correction Point
    if (showAddEditDialog && activeRoute != null) {
        AddEditCorrectionPointDialog(
            activeRoute = activeRoute,
            existingPoints = correctionPoints,
            editingPoint = pointToEdit,
            currentGpsPoint = currentGpsPoint,
            currentRawChainage = matchResult.rawUncorrectedChainage ?: matchResult.chainage,
            primaryAccent = primaryAccent,
            onDismiss = {
                showAddEditDialog = false
                pointToEdit = null
            },
            onSave = { point ->
                if (pointToEdit != null) {
                    viewModel.updateCorrectionPoint(point)
                } else {
                    viewModel.addCorrectionPoint(point)
                }
                showAddEditDialog = false
                pointToEdit = null
            }
        )
    }

    // Reset Confirmation
    if (showResetConfirmDialog && activeRoute != null) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = dialogColors.containerColor,
            title = { Text("Reset Corrections", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
            text = {
                Text(
                    "Are you sure you want to delete all calibration points for '${activeRoute.name}'? The original KMZ chainages will remain unchanged.",
                    color = dialogColors.textSecondaryColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllCorrectionsForRoute(activeRoute.id)
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Reset All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 1: MANUAL CHAINAGE OVERRIDE
// -------------------------------------------------------------
@Composable
private fun ManualChainageTab(
    currentChainage: Chainage?,
    isManualActive: Boolean,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onApplyManual: (String) -> Unit,
    onResetAuto: () -> Unit
) {
    var rawInput by remember { mutableStateOf(currentChainage?.format() ?: "198+880") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val parsedPreview = remember(rawInput) { Chainage.parse(rawInput) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Active Status Notice
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isManualActive) Color(0xFFFEF3C7) else if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
            border = BorderStroke(
                1.dp,
                if (isManualActive) Color(0xFFF59E0B) else dialogColors.borderColor
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isManualActive) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isManualActive) Color(0xFFB45309) else primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isManualActive) {
                        "Manual chainage override is ACTIVE (${currentChainage?.format()}). GPS chainage updates are paused."
                    } else {
                        "Manual entry overrides live GPS chainage across Home Page, Camera, and PDF DPRs until reset."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isManualActive) Color(0xFF92400E) else dialogColors.textColor,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Input Field
        OutlinedTextField(
            value = rawInput,
            onValueChange = { rawInput = it },
            label = { Text("Chainage (km+m, integer or decimal)") },
            placeholder = { Text("e.g. 198+880 or 198880") },
            trailingIcon = {
                if (rawInput.isNotBlank()) {
                    IconButton(onClick = { rawInput = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = dialogColors.textSecondaryColor)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    if (parsedPreview != null) {
                        onApplyManual(rawInput)
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = dialogColors.textColor,
                unfocusedTextColor = dialogColors.textColor,
                focusedBorderColor = primaryAccent,
                unfocusedBorderColor = dialogColors.inputBorder,
                focusedLabelColor = primaryAccent,
                unfocusedLabelColor = dialogColors.textSecondaryColor,
                cursorColor = primaryAccent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_chainage_text_field")
        )

        // Live Format Preview
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (parsedPreview != null) {
                    "Formatted: ${parsedPreview.format()} (${parsedPreview.kilometres} km ${parsedPreview.remainderMetres} m)"
                } else if (rawInput.isBlank()) {
                    "Enter chainage e.g. 198+880 or 198880"
                } else {
                    "Invalid chainage format"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (parsedPreview != null) Color(0xFF10B981) else if (rawInput.isBlank()) dialogColors.textSecondaryColor else Color(0xFFEF4444),
                fontWeight = if (parsedPreview != null) FontWeight.Bold else FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Delta Nudge Chips
        Text(
            text = "Quick Adjustments:",
            style = MaterialTheme.typography.labelSmall,
            color = dialogColors.textSecondaryColor,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val base = parsedPreview?.metres ?: currentChainage?.metres ?: 0L
                            val newM = (base + delta).coerceAtLeast(0L)
                            rawInput = Chainage(newM).format()
                        }
                ) {
                    Text(
                        text = if (delta > 0) "+${delta}m" else "${delta}m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = dialogColors.textColor,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons: Apply, Reset, Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isManualActive) {
                OutlinedButton(
                    onClick = onResetAuto,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("reset_manual_chainage_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Auto GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { onApplyManual(rawInput) },
                enabled = parsedPreview != null,
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(if (isManualActive) 1.2f else 1f)
                    .testTag("apply_manual_chainage_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Apply Override", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: SMART KMZ CALIBRATION & CORRECTION MANAGEMENT
// -------------------------------------------------------------
@Composable
private fun SmartKmzCalibrationTab(
    activeRoute: RouteData?,
    matchResult: ChainageMatchResult,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    currentGpsPoint: GeoPoint?,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onSelectAlignment: (String?) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onTogglePoint: (ChainageCorrectionPoint, Boolean) -> Unit,
    onEditPoint: (ChainageCorrectionPoint) -> Unit,
    onDeletePoint: (ChainageCorrectionPoint) -> Unit,
    onAddNew: () -> Unit,
    onResetAll: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    if (activeRoute == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AltRoute,
                contentDescription = null,
                tint = dialogColors.textSecondaryColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No KMZ Highway Alignment Loaded",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Import or activate a KML/KMZ alignment file to calibrate route chainages.",
                style = MaterialTheme.typography.bodySmall,
                color = dialogColors.textSecondaryColor,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val diagnostics = remember(activeRoute) {
        activeRoute.diagnostics ?: SmartChainageCorrectionEngine.performAlignmentAudit(activeRoute)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Route Info & Master Calibration Switch
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
            ),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeRoute.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val rRange = if (activeRoute.startChainage != null && activeRoute.endChainage != null) {
                        "${activeRoute.startChainage.format()} → ${activeRoute.endChainage.format()}"
                    } else "No chainage bounds"
                    Text(
                        text = "Range: $rRange • ${correctionPoints.size} Control Points",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryAccent
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isCorrectionsEnabled) "Active" else "Off",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrectionsEnabled) Color(0xFF10B981) else dialogColors.textSecondaryColor
                    )
                    Switch(
                        checked = isCorrectionsEnabled,
                        onCheckedChange = onToggleMaster,
                        colors = SwitchDefaults.colors(checkedThumbColor = primaryAccent),
                        modifier = Modifier.testTag("toggle_smart_corrections")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Multi-Alignment & Carriageway Selector (if multiple alignments exist)
        if (activeRoute.alignments.size > 1) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                ),
                border = BorderStroke(0.8.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = "KMZ Carriageway / Alignment (${activeRoute.alignments.size} Available):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogColors.textSecondaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                        items(activeRoute.alignments) { align ->
                            val isSelected = activeRoute.selectedAlignmentId == align.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectAlignment(align.id) },
                                label = {
                                    Text(
                                        "${align.name} (${align.carriagewayType})",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 3. Precision GPS-to-KMZ Alignment Matching Status Card
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dialogColors.isDark) Color(0xFF131D2E) else Color(0xFFF0F9FF)
            ),
            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header with Confidence Badge & Direction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (confBg, confText, confLabel) = when (matchResult.confidence) {
                        MatchConfidence.HIGH -> Triple(Color(0xFF065F46), Color(0xFF34D399), "High Confidence")
                        MatchConfidence.MEDIUM -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), "Medium Confidence")
                        MatchConfidence.LOW -> Triple(Color(0xFF7F1D1D), Color(0xFFF87171), "Low Confidence")
                        MatchConfidence.NO_MATCH -> Triple(Color(0xFF374151), Color(0xFF9CA3AF), "No Match")
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = confBg
                    ) {
                        Text(
                            text = if (matchResult.isUncertain) "Uncertain / Jump" else confLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = confText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "Dir: ${matchResult.alignmentDirection} • Side: ${matchResult.detectedSide}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.textSecondaryColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Accuracy, Offset, and Segment Identification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("GPS Accuracy", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                        Text("±${String.format(Locale.US, "%.1fm", matchResult.gpsAccuracy)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = dialogColors.textColor)
                    }
                    Column {
                        Text("Alignment Offset", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                        Text(String.format(Locale.US, "%.1fm", matchResult.offsetMetres), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = dialogColors.textColor)
                    }
                    Column {
                        Text("Matched Segment", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                        val segStr = if (matchResult.matchedSegmentIndex >= 0) "Seg #${matchResult.matchedSegmentIndex + 1}" else "---"
                        Text(segStr, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = dialogColors.textColor)
                    }
                    Column {
                        Text("Alignment", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                        val alignStr = matchResult.matchedAlignmentName.ifBlank { "Auto" }
                        Text(alignStr.take(10), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = primaryAccent)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Original KMZ vs Calibrated Chainage Comparison
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (dialogColors.isDark) Color(0xFF0F172A) else Color.White,
                    border = BorderStroke(0.5.dp, dialogColors.borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Original KMZ Chainage:", fontSize = 9.5.sp, color = dialogColors.textSecondaryColor)
                            Text(
                                text = matchResult.rawUncorrectedChainage?.format() ?: matchResult.chainage?.format() ?: "---",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.textColor
                            )
                        }

                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = primaryAccent)

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Calibrated Output:", fontSize = 9.5.sp, color = primaryAccent)
                            Text(
                                text = matchResult.chainage?.format() ?: "---",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryAccent
                            )
                        }
                    }
                }

                // Reference bracketing info
                if (matchResult.bracketingMarkersInfo != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "References: ${matchResult.sourceKmzReferenceStatus} (${matchResult.bracketingMarkersInfo})",
                        fontSize = 9.5.sp,
                        color = dialogColors.textSecondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Alignment Reference Validation & Diagnostics
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
            ),
            border = BorderStroke(0.8.dp, dialogColors.borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (diagnostics.qualityScorePercent >= 80) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (diagnostics.qualityScorePercent >= 80) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${diagnostics.qualityScorePercent}% Quality • ${diagnostics.totalMarkersCount} Refs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                }

                Text(
                    text = if (diagnostics.duplicateMarkersCount > 0) "${diagnostics.duplicateMarkersCount} Duplicates" else "Zero Duplicates",
                    fontSize = 10.sp,
                    color = if (diagnostics.duplicateMarkersCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Visual Preview: Route Highway Alignment Schematic with Dot Markers
        RouteCalibrationSchematicPreview(
            activeRoute = activeRoute,
            correctionPoints = correctionPoints,
            currentGpsPoint = currentGpsPoint,
            currentChainage = matchResult.chainage,
            primaryAccent = primaryAccent,
            dialogColors = dialogColors
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 6. Calibration Points List or Empty State
        if (correctionPoints.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFFAFAFA),
                border = BorderStroke(0.8.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Calibration Points Added Yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Add calibration control points at known chainages. CTECS interpolates corrections along the alignment polyline without altering GPS coordinates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogColors.textSecondaryColor,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                itemsIndexed(correctionPoints) { index, pt ->
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
                        onEdit = { onEditPoint(pt) },
                        onDelete = { onDeletePoint(pt) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7. Bottom Action Controls: Reset All, Export KMZ, Export JSON, Add Point
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (correctionPoints.isNotEmpty()) {
                TextButton(
                    onClick = onResetAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Export Calibrated KMZ
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
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Export KMZ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Export JSON
                if (correctionPoints.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            exportCorrectionPoints(context, activeRoute, correctionPoints)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dialogColors.textColor),
                        border = BorderStroke(1.dp, dialogColors.borderColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("export_calibration_json_button")
                    ) {
                        Text("JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onAddNew,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_correction_point_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Add Point", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VISUAL SCHEMATIC / MAP PREVIEW COMPONENT
// -------------------------------------------------------------
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
            // Header: Preview Title & Corridor Bounds
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
                        text = "Alignment Calibration Schematic",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor,
                        fontSize = 11.sp
                    )
                }

                val totalKmStr = String.format(Locale.US, "%.1f km", (maxMetres - minMetres) / 1000.0)
                Text(
                    text = "${Chainage(minMetres).format()} → ${Chainage(maxMetres).format()} ($totalKmStr)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = primaryAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Canvas drawing the highway alignment track and points
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val padX = 18.dp.toPx()
                val availW = size.width - 2 * padX
                val trackY = size.height * 0.5f

                val roadColor = if (dialogColors.isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                val roadHighlight = primaryAccent.copy(alpha = 0.35f)
                val centerDashColor = if (dialogColors.isDark) Color(0xFF64748B) else Color.White

                // 1. Draw Road Alignment Track Base
                drawLine(
                    color = roadColor,
                    start = Offset(padX, trackY),
                    end = Offset(padX + availW, trackY),
                    strokeWidth = 9.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 2. Highlight calibrated coverage span if points exist
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
                        strokeWidth = 9.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 3. Centerline dashed road markings
                drawLine(
                    color = centerDashColor,
                    start = Offset(padX, trackY),
                    end = Offset(padX + availW, trackY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                )

                // 4. Milestone tick marks at 0%, 25%, 50%, 75%, 100%
                for (pct in listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)) {
                    val tickX = padX + pct * availW
                    drawLine(
                        color = if (dialogColors.isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        start = Offset(tickX, trackY - 8.dp.toPx()),
                        end = Offset(tickX, trackY + 8.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 5. Draw Correction Points
                correctionPoints.forEach { pt ->
                    val frac = ((pt.originalKmzMetres - minMetres).toFloat() / spanMetres).coerceIn(0f, 1f)
                    val ptX = padX + frac * availW

                    if (pt.isEnabled) {
                        // Halo glow
                        drawCircle(
                            color = Color(0xFF10B981).copy(alpha = 0.35f),
                            radius = 7.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                        // Outer dot
                        drawCircle(
                            color = Color(0xFF10B981),
                            radius = 4.5.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                        // Inner core
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                    } else {
                        // Disabled dot
                        drawCircle(
                            color = Color(0xFF94A3B8),
                            radius = 3.5.dp.toPx(),
                            center = Offset(ptX, trackY)
                        )
                    }
                }

                // 6. Draw Current Live GPS Position Marker
                if (currentChainage != null) {
                    val gpsFrac = ((currentChainage.metres - minMetres).toFloat() / spanMetres).coerceIn(0f, 1f)
                    val gpsX = padX + gpsFrac * availW

                    // Outer pulse ring
                    drawCircle(
                        color = primaryAccent.copy(alpha = 0.35f),
                        radius = 9.dp.toPx(),
                        center = Offset(gpsX, trackY)
                    )
                    // Solid circle
                    drawCircle(
                        color = primaryAccent,
                        radius = 5.5.dp.toPx(),
                        center = Offset(gpsX, trackY)
                    )
                    // Inner white eye
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(gpsX, trackY)
                    )
                }
            }

            // Legend / status indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Active Point Dot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "Calibrated (${correctionPoints.count { it.isEnabled }})",
                            fontSize = 9.5.sp,
                            color = dialogColors.textSecondaryColor
                        )
                    }

                    // Disabled Point Dot
                    if (correctionPoints.any { !it.isEnabled }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF94A3B8))
                            )
                            Text(
                                text = "Disabled (${correctionPoints.count { !it.isEnabled }})",
                                fontSize = 9.5.sp,
                                color = dialogColors.textSecondaryColor
                            )
                        }
                    }
                }

                // Live GPS Marker Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(primaryAccent)
                    )
                    Text(
                        text = "GPS: ${currentChainage?.format() ?: "Awaiting"}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryAccent
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// JSON EXPORT HELPER FOR CALIBRATION POINTS
// -------------------------------------------------------------
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

// -------------------------------------------------------------
// TAB 3: HISTORICAL AUDIT LOG VIEW
// -------------------------------------------------------------
@Composable
private fun HistoryLogTab(
    auditLogs: List<ChainageLogEntry>,
    activeRoute: RouteData?,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onClearHistory: () -> Unit
) {
    var filterByRouteOnly by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredLogs = remember(auditLogs, filterByRouteOnly, activeRoute) {
        if (filterByRouteOnly && activeRoute != null) {
            auditLogs.filter { it.routeId.isBlank() || it.routeId == activeRoute.id }
        } else {
            auditLogs
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Top Controls: Filter by Route & Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeRoute != null) {
                FilterChip(
                    selected = filterByRouteOnly,
                    onClick = { filterByRouteOnly = !filterByRouteOnly },
                    label = {
                        Text(
                            text = if (filterByRouteOnly) "Only ${activeRoute.name}" else "All Routes",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryAccent.copy(alpha = 0.2f),
                        selectedLabelColor = primaryAccent
                    ),
                    modifier = Modifier.height(28.dp)
                )
            } else {
                Text(
                    text = "Recent Activity (${auditLogs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = dialogColors.textSecondaryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (auditLogs.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Log", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLogs.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFFAFAFA),
                border = BorderStroke(0.8.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = dialogColors.textSecondaryColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Chainage Adjustments Logged",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manual chainage overrides, resets, and KMZ calibration edits will be tracked here in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogColors.textSecondaryColor,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    HistoryLogEntryCard(
                        entry = entry,
                        dateFormatter = dateFormatter,
                        primaryAccent = primaryAccent,
                        dialogColors = dialogColors
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = dialogColors.containerColor,
            title = { Text("Clear History Log", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
            text = {
                Text(
                    "Are you sure you want to clear the entire chainage adjustment history log?",
                    color = dialogColors.textSecondaryColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }
}

@Composable
private fun HistoryLogEntryCard(
    entry: ChainageLogEntry,
    dateFormatter: SimpleDateFormat,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens
) {
    val (badgeBg, badgeText, badgeLabel) = when (entry.actionType) {
        "MANUAL_OVERRIDE" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "MANUAL")
        "MANUAL_RESET" -> Triple(Color(0xFFE2E8F0), Color(0xFF475569), "GPS RESET")
        "POINT_ADDED" -> Triple(Color(0xFFD1FAE5), Color(0xFF047857), "POINT ADDED")
        "POINT_EDITED" -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), "POINT UPDATED")
        "POINT_DELETED" -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "POINT DELETED")
        "ALL_RESET" -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "ALL RESET")
        "TOGGLED" -> Triple(Color(0xFFEDE9FE), Color(0xFF6D28D9), "TOGGLED")
        else -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), entry.actionType)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color.White
        ),
        border = BorderStroke(0.8.dp, dialogColors.borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = dateFormatter.format(Date(entry.timestamp)),
                    fontSize = 10.sp,
                    color = dialogColors.textSecondaryColor
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = dialogColors.textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (entry.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = dialogColors.textSecondaryColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CORRECTION POINT ITEM CARD
// -------------------------------------------------------------
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "#$index",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent
                    )
                    Text(
                        text = point.correctedChainage.format(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (point.isEnabled) dialogColors.titleColor else dialogColors.textSecondaryColor
                    )
                    // Offset pill
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

                val coordStr = "Orig: ${point.originalKmzChainage.format()} • ${String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)}"
                val distStr = if (distFromPrevMetres != null) " • ${distFromPrevMetres}m from prev" else ""
                Text(
                    text = "$coordStr$distStr",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    color = dialogColors.textSecondaryColor.copy(alpha = 0.8f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = primaryAccent, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
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

// -------------------------------------------------------------
// SUB-DIALOG: ADD / EDIT CORRECTION POINT
// -------------------------------------------------------------
@Composable
private fun AddEditCorrectionPointDialog(
    activeRoute: RouteData,
    existingPoints: List<ChainageCorrectionPoint>,
    editingPoint: ChainageCorrectionPoint?,
    currentGpsPoint: GeoPoint?,
    currentRawChainage: Chainage?,
    primaryAccent: Color,
    onDismiss: () -> Unit,
    onSave: (ChainageCorrectionPoint) -> Unit
) {
    val dialogColors = rememberDialogColors()
    var isUsingCurrentGps by remember { mutableStateOf(editingPoint == null && currentGpsPoint != null) }

    var latInput by remember {
        mutableStateOf(editingPoint?.latitude?.toString() ?: currentGpsPoint?.latitude?.toString() ?: "0.0")
    }
    var lngInput by remember {
        mutableStateOf(editingPoint?.longitude?.toString() ?: currentGpsPoint?.longitude?.toString() ?: "0.0")
    }

    var originalKmzInput by remember {
        mutableStateOf(editingPoint?.originalKmzChainage?.format() ?: currentRawChainage?.format() ?: "198+500")
    }
    var correctedInput by remember {
        mutableStateOf(editingPoint?.correctedChainage?.format() ?: currentRawChainage?.format() ?: "198+500")
    }
    var descriptionInput by remember {
        mutableStateOf(editingPoint?.description ?: "")
    }

    val parsedOriginal = remember(originalKmzInput) { Chainage.parse(originalKmzInput) }
    val parsedCorrected = remember(correctedInput) { Chainage.parse(correctedInput) }

    val calculatedOffset = remember(parsedOriginal, parsedCorrected) {
        if (parsedOriginal != null && parsedCorrected != null) {
            parsedCorrected.metres - parsedOriginal.metres
        } else 0L
    }

    // Validation check
    val tentativePoint = remember(editingPoint, latInput, lngInput, parsedOriginal, parsedCorrected, descriptionInput) {
        val lat = latInput.toDoubleOrNull() ?: 0.0
        val lng = lngInput.toDoubleOrNull() ?: 0.0
        val origM = parsedOriginal?.metres ?: 0L
        val corrM = parsedCorrected?.metres ?: 0L
        ChainageCorrectionPoint(
            id = editingPoint?.id ?: java.util.UUID.randomUUID().toString(),
            routeId = activeRoute.id,
            latitude = lat,
            longitude = lng,
            originalKmzMetres = origM,
            correctedMetres = corrM,
            description = descriptionInput.trim(),
            isEnabled = editingPoint?.isEnabled ?: true
        )
    }

    val validation = remember(tentativePoint, existingPoints, activeRoute) {
        SmartChainageCorrectionEngine.validatePoint(tentativePoint, existingPoints, activeRoute)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        title = {
            Text(
                text = if (editingPoint != null) "Edit Calibration Point" else "Add Calibration Point",
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Source Selection Chips: Current GPS Fix vs Manual Route Location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isUsingCurrentGps,
                        onClick = {
                            isUsingCurrentGps = true
                            if (currentGpsPoint != null) {
                                latInput = currentGpsPoint.latitude.toString()
                                lngInput = currentGpsPoint.longitude.toString()
                            }
                            if (currentRawChainage != null) {
                                originalKmzInput = currentRawChainage.format()
                            }
                        },
                        label = { Text("Use Current GPS Fix", fontSize = 11.5.sp) },
                        leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryAccent.copy(alpha = 0.2f),
                            selectedLabelColor = primaryAccent
                        )
                    )

                    FilterChip(
                        selected = !isUsingCurrentGps,
                        onClick = { isUsingCurrentGps = false },
                        label = { Text("Manual Location", fontSize = 11.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryAccent.copy(alpha = 0.2f),
                            selectedLabelColor = primaryAccent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Original KMZ Chainage Input
                OutlinedTextField(
                    value = originalKmzInput,
                    onValueChange = { originalKmzInput = it },
                    label = { Text("Original KMZ Chainage") },
                    placeholder = { Text("e.g. 198+450") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = dialogColors.inputBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("orig_kmz_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Corrected True Chainage Input
                OutlinedTextField(
                    value = correctedInput,
                    onValueChange = { correctedInput = it },
                    label = { Text("Corrected True Chainage") },
                    placeholder = { Text("e.g. 198+500") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = dialogColors.inputBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("corrected_kmz_input")
                )

                // Calculated Delta preview
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (parsedOriginal != null && parsedCorrected != null) {
                        val sign = if (calculatedOffset >= 0) "+$calculatedOffset" else "$calculatedOffset"
                        "Calibration Shift: $sign metres"
                    } else "Enter valid chainage formats (e.g. 198+500)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (calculatedOffset >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Description / Landmark (Optional)") },
                    placeholder = { Text("e.g. Culvert, KM Stone, Bridge") },
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
                if (validation.warnings.isNotEmpty()) {
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
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                                    Text(warn, fontSize = 10.5.sp, color = Color(0xFF92400E))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(tentativePoint) },
                enabled = parsedOriginal != null && parsedCorrected != null,
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
            ) {
                Text("Save Point", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = dialogColors.textSecondaryColor)
            }
        }
    )
}

// -------------------------------------------------------------
// BADGE DISPLAYING ACTIVE SOURCE
// -------------------------------------------------------------
@Composable
fun ChainageSourceBadge(
    source: ChainageSource,
    isDark: Boolean = com.example.ui.theme.isAppDarkTheme(),
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
            "CUSTOM CORRECTION"
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
