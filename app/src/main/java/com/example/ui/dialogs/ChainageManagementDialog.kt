package com.example.ui.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.theme.DialogThemeTokens
import com.example.ui.theme.rememberDialogColors
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Unified KMZ Chainage Correction Calibration Dialog.
 *
 * Strictly focused on KMZ Chainage Correction Calibration:
 * - Eliminates duplicate manual override and history tabs.
 * - Uses smart Material Flat UI buttons that dynamically fit all device viewports.
 * - Prevents header badge text wrapping and clipping.
 * - Ensures calibration points immediately take effect by clearing stale overrides and enabling calibration.
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
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var pointToEdit by remember { mutableStateOf<ChainageCorrectionPoint?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val primaryAccent = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val isManualOverrideStale = matchResult.source == ChainageSource.MANUAL

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = dialogColors.containerColor),
            border = BorderStroke(1.dp, dialogColors.borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding()
                .testTag("chainage_management_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // ----------------------------------------------------
                // 1. HEADER: Title + Single-Line Non-Wrapping Source Badge + Close
                // ----------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(primaryAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinearScale,
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
                                color = dialogColors.titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "KMZ Chainage Correction Calibration",
                                style = MaterialTheme.typography.labelSmall,
                                color = dialogColors.textSecondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = dialogColors.textSecondaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = dialogColors.borderColor,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // If a stale manual override was left active from an earlier session, show a 1-tap clear banner
                if (isManualOverrideStale) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (dialogColors.isDark) Color(0xFF78350F).copy(alpha = 0.35f) else Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Manual Lock Active (${matchResult.chainage?.format() ?: "--"})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dialogColors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                )
                                Text(
                                    text = "Switch to live KMZ calibration for automatic chainage updates.",
                                    fontSize = 10.5.sp,
                                    color = if (dialogColors.isDark) Color(0xFFFEF3C7) else Color(0xFFB45309)
                                )
                            }
                            FlatActionButton(
                                text = "Use KMZ",
                                icon = Icons.Default.RestartAlt,
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White,
                                onClick = { viewModel.clearManualChainage() },
                                height = 34.dp
                            )
                        }
                    }
                }

                // ----------------------------------------------------
                // 2. MAIN KMZ CALIBRATION CONTENT
                // ----------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    SmartKmzCalibrationContent(
                        activeRoute = activeRoute,
                        matchResult = matchResult,
                        correctionPoints = correctionPoints,
                        isCorrectionsEnabled = isCorrectionsEnabled,
                        primaryAccent = primaryAccent,
                        dialogColors = dialogColors,
                        onSelectAlignment = { viewModel.selectAlignment(it) },
                        onToggleMaster = { enabled ->
                            if (enabled && isManualOverrideStale) {
                                viewModel.clearManualChainage()
                            }
                            viewModel.setRouteCorrectionsEnabled(enabled)
                        },
                        onTogglePoint = { pt, en ->
                            if (en && isManualOverrideStale) {
                                viewModel.clearManualChainage()
                            }
                            viewModel.toggleCorrectionPoint(pt.routeId, pt.id, en)
                        },
                        onEditPoint = { pt ->
                            pointToEdit = pt
                            showAddEditDialog = true
                        },
                        onDeletePoint = { pt ->
                            viewModel.deleteCorrectionPoint(pt.routeId, pt.id)
                        },
                        onQuickCalibrateHere = {
                            pointToEdit = null
                            showAddEditDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ----------------------------------------------------
                // 3. SMART DYNAMIC BOTTOM MATERIAL FLAT ACTION BAR
                // ----------------------------------------------------
                if (activeRoute != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 360.dp

                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FlatActionButton(
                                    text = "Calibrate / Add Point",
                                    icon = Icons.Default.Add,
                                    containerColor = primaryAccent,
                                    contentColor = Color.White,
                                    onClick = {
                                        pointToEdit = null
                                        showAddEditDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_correction_point_button")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (correctionPoints.isNotEmpty()) {
                                        FlatActionButton(
                                            text = "Reset",
                                            icon = Icons.Default.Delete,
                                            containerColor = if (dialogColors.isDark) Color(0xFF3F1D24) else Color(0xFFFEE2E2),
                                            contentColor = Color(0xFFEF4444),
                                            onClick = { showResetConfirmDialog = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    FlatActionButton(
                                        text = "Export KMZ",
                                        icon = Icons.Default.Share,
                                        containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                        contentColor = primaryAccent,
                                        onClick = {
                                            exportCalibratedKmzFile(context, activeRoute, correctionPoints, coroutineScope)
                                        },
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("export_calibrated_kmz_button")
                                    )
                                    FlatActionButton(
                                        text = "Close",
                                        containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                        contentColor = dialogColors.textColor,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (correctionPoints.isNotEmpty()) {
                                    FlatActionButton(
                                        text = "Reset",
                                        icon = Icons.Default.Delete,
                                        containerColor = if (dialogColors.isDark) Color(0xFF3F1D24) else Color(0xFFFEE2E2),
                                        contentColor = Color(0xFFEF4444),
                                        onClick = { showResetConfirmDialog = true },
                                        modifier = Modifier.weight(0.85f)
                                    )
                                }

                                FlatActionButton(
                                    text = "Export KMZ",
                                    icon = Icons.Default.Share,
                                    containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    contentColor = primaryAccent,
                                    onClick = {
                                        exportCalibratedKmzFile(context, activeRoute, correctionPoints, coroutineScope)
                                    },
                                    modifier = Modifier
                                        .weight(1.05f)
                                        .testTag("export_calibrated_kmz_button")
                                )

                                FlatActionButton(
                                    text = "Add Point",
                                    icon = Icons.Default.Add,
                                    containerColor = primaryAccent,
                                    contentColor = Color.White,
                                    onClick = {
                                        pointToEdit = null
                                        showAddEditDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("add_correction_point_button")
                                )
                            }
                        }
                    }
                } else {
                    FlatActionButton(
                        text = "Close",
                        containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        contentColor = dialogColors.textColor,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

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

    // Reset Confirmation Dialog
    if (showResetConfirmDialog && activeRoute != null) {
        Dialog(onDismissRequest = { showResetConfirmDialog = false }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = dialogColors.containerColor),
                border = BorderStroke(1.dp, dialogColors.borderColor),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reset KMZ Calibration?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Delete all ${correctionPoints.size} calibration points for '${activeRoute.name}'? The route will revert to its original KMZ chainages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogColors.textSecondaryColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlatActionButton(
                            text = "Cancel",
                            containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            contentColor = dialogColors.textColor,
                            onClick = { showResetConfirmDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        FlatActionButton(
                            text = "Reset All",
                            icon = Icons.Default.Delete,
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                            onClick = {
                                viewModel.resetAllCorrectionsForRoute(activeRoute.id)
                                showResetConfirmDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MATERIAL FLAT BUTTON COMPONENT FOR CHAINAGE CONTROL
// -------------------------------------------------------------
@Composable
private fun FlatActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    height: Dp = 42.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier.height(height)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// KMZ CALIBRATION & CORRECTION CONTENT
// -------------------------------------------------------------
@Composable
private fun SmartKmzCalibrationContent(
    activeRoute: RouteData?,
    matchResult: ChainageMatchResult,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onSelectAlignment: (String?) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onTogglePoint: (ChainageCorrectionPoint, Boolean) -> Unit,
    onEditPoint: (ChainageCorrectionPoint) -> Unit,
    onDeletePoint: (ChainageCorrectionPoint) -> Unit,
    onQuickCalibrateHere: () -> Unit
) {
    if (activeRoute == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(primaryAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No KMZ Highway Alignment Active",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Import and activate a KML/KMZ route alignment in the Routes tab to calibrate live highway chainage.",
                style = MaterialTheme.typography.bodySmall,
                color = dialogColors.textSecondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }

    val diagnostics = remember(activeRoute) {
        activeRoute.diagnostics ?: SmartChainageCorrectionEngine.performAlignmentAudit(activeRoute)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Active Route Info & Master Calibration Switch
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
                    } else "KMZ Alignment"
                    Text(
                        text = "$rRange • ${correctionPoints.count { it.isEnabled }}/${correctionPoints.size} Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isCorrectionsEnabled) "ON" else "OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
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

        // 2. Multi-Alignment & Carriageway Selector (if multiple alignments exist)
        if (activeRoute.alignments.size > 1) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = "Carriageway Alignment (${activeRoute.alignments.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
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
                                label = { Text("Auto (Best Match)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent.copy(alpha = 0.18f),
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
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent.copy(alpha = 0.18f),
                                    selectedLabelColor = primaryAccent
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. Live KMZ vs Calibrated Chainage Status Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (dialogColors.isDark) Color(0xFF131D2E) else Color(0xFFF0F9FF),
            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Confidence & Direction Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (confBg, confText, confLabel) = when (matchResult.confidence) {
                        MatchConfidence.HIGH -> Triple(Color(0xFF065F46), Color(0xFF34D399), "High Precision")
                        MatchConfidence.MEDIUM -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), "Medium Match")
                        MatchConfidence.LOW -> Triple(Color(0xFF7F1D1D), Color(0xFFF87171), "Low / Off-Track")
                        MatchConfidence.NO_MATCH -> Triple(Color(0xFF374151), Color(0xFF9CA3AF), "No KMZ Match")
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = confBg
                    ) {
                        Text(
                            text = if (matchResult.isUncertain) "Jump Filtered" else confLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = confText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = "${matchResult.detectedSide} • ±${String.format(Locale.US, "%.0fm", matchResult.gpsAccuracy)} GPS • ${String.format(Locale.US, "%.1fm", matchResult.offsetMetres)} Offset",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogColors.textSecondaryColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Original KMZ vs Calibrated Output Box
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
                                text = "Original KMZ",
                                fontSize = 10.sp,
                                color = dialogColors.textSecondaryColor
                            )
                            Text(
                                text = matchResult.rawUncorrectedChainage?.format() ?: matchResult.chainage?.format() ?: "---",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.textColor
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = primaryAccent
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Calibrated Output",
                                fontSize = 10.sp,
                                color = primaryAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = matchResult.chainage?.format() ?: "---",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryAccent
                            )
                        }
                    }
                }

                if (matchResult.correctionPointInfo != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Active Rule: ${matchResult.correctionPointInfo}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (correctionPoints.isNotEmpty() && matchResult.confidence == MatchConfidence.LOW) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Move closer to alignment (<35m) for custom calibration to engage.",
                        fontSize = 10.5.sp,
                        color = Color(0xFFF59E0B)
                    )
                }

                // Quick 1-Tap Calibrate Current Position Button
                if (matchResult.chainage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlatActionButton(
                        text = "Calibrate Here (${matchResult.rawUncorrectedChainage?.format() ?: matchResult.chainage.format()})",
                        icon = Icons.Default.MyLocation,
                        containerColor = primaryAccent.copy(alpha = if (dialogColors.isDark) 0.22f else 0.14f),
                        contentColor = primaryAccent,
                        onClick = onQuickCalibrateHere,
                        height = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 4. Compact Alignment Quality Strip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, dialogColors.borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (diagnostics.qualityScorePercent >= 80) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (diagnostics.qualityScorePercent >= 80) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "KMZ Health: ${diagnostics.qualityScorePercent}% (${diagnostics.totalMarkersCount} Markers)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogColors.titleColor
                    )
                }

                Text(
                    text = if (diagnostics.isChainageIncreasing) "Increasing" else "Decreasing",
                    fontSize = 10.5.sp,
                    color = dialogColors.textSecondaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 5. Calibration Control Points List
        Text(
            text = "CALIBRATION CONTROL POINTS (${correctionPoints.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = dialogColors.textSecondaryColor,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (correctionPoints.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Calibration Points Added",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Calibrate Here' or 'Add Point' to align KMZ chainage with physical highway KM stones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogColors.textSecondaryColor,
                        fontSize = 11.sp,
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
                        onEdit = { onEditPoint(pt) },
                        onDelete = { onDeletePoint(pt) }
                    )
                }
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
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (dialogColors.isDark) Color(0xFF1E293B) else Color.White,
        border = BorderStroke(
            1.dp,
            if (point.isEnabled) primaryAccent.copy(alpha = 0.45f) else dialogColors.borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = primaryAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "#$index",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${point.originalKmzChainage.format()} → ${point.correctedChainage.format()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (point.isEnabled) dialogColors.titleColor else dialogColors.textSecondaryColor
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (point.offsetMetres >= 0) Color(0x2610B981) else Color(0x26EF4444)
                    ) {
                        Text(
                            text = point.displayOffset,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (point.offsetMetres >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                val desc = if (point.description.isNotBlank()) {
                    point.description
                } else {
                    "KMZ Calibration Point"
                }
                val distInfo = if (distFromPrevMetres != null) " • ${distFromPrevMetres}m span" else ""
                Text(
                    text = "$desc$distInfo",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
                    color = dialogColors.textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Switch(
                    checked = point.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryAccent)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-DIALOG: ADD / EDIT CALIBRATION POINT
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

    var originalKmzInput by remember {
        mutableStateOf(editingPoint?.originalKmzChainage?.format() ?: currentRawChainage?.format() ?: "205+150")
    }
    var correctedInput by remember {
        mutableStateOf(editingPoint?.correctedChainage?.format() ?: currentRawChainage?.format() ?: "205+150")
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

    val tentativePoint = remember(editingPoint, currentGpsPoint, parsedOriginal, parsedCorrected, descriptionInput) {
        val lat = editingPoint?.latitude ?: currentGpsPoint?.latitude ?: 0.0
        val lng = editingPoint?.longitude ?: currentGpsPoint?.longitude ?: 0.0
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = dialogColors.containerColor),
            border = BorderStroke(1.dp, dialogColors.borderColor),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingPoint != null) "Edit Calibration Point" else "Add Calibration Point",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    if (currentRawChainage != null && editingPoint == null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primaryAccent.copy(alpha = 0.15f),
                            modifier = Modifier.clickable {
                                originalKmzInput = currentRawChainage.format()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Use Live KMZ (${currentRawChainage.format()})",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Original KMZ Chainage Input
                OutlinedTextField(
                    value = originalKmzInput,
                    onValueChange = { originalKmzInput = it },
                    label = { Text("Original KMZ Chainage") },
                    placeholder = { Text("e.g. 205+150") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = dialogColors.inputBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("orig_kmz_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Corrected True Chainage Input
                OutlinedTextField(
                    value = correctedInput,
                    onValueChange = { correctedInput = it },
                    label = { Text("Calibrated True Chainage") },
                    placeholder = { Text("e.g. 205+200") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = dialogColors.inputBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("corrected_kmz_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Offset Nudge Buttons (-50m, -10m, +10m, +50m)
                Text(
                    text = "Quick Calibrated Adjustment:",
                    fontSize = 11.sp,
                    color = dialogColors.textSecondaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-50L to "-50m", -10L to "-10m", 10L to "+10m", 50L to "+50m").forEach { (delta, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, dialogColors.borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val base = parsedCorrected?.metres ?: parsedOriginal?.metres ?: 0L
                                    correctedInput = Chainage((base + delta).coerceAtLeast(0L)).format()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dialogColors.textColor
                                )
                            }
                        }
                    }
                }

                // Calculated Delta preview
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (calculatedOffset >= 0) Color(0x1A10B981) else Color(0x1AEF4444),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (parsedOriginal != null && parsedCorrected != null) {
                            val sign = if (calculatedOffset >= 0) "+$calculatedOffset" else "$calculatedOffset"
                            "Calibration Shift: $sign metres (${parsedOriginal.format()} → ${parsedCorrected.format()})"
                        } else {
                            "Enter valid chainage format (e.g. 205+150)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (parsedOriginal != null && parsedCorrected != null) {
                            if (calculatedOffset >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        } else Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Description / Landmark
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Landmark / Note (Optional)") },
                    placeholder = { Text("e.g. KM Stone 205, Toll Plaza, Culvert") },
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
                        shape = RoundedCornerShape(8.dp),
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
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFB45309),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(warn, fontSize = 10.5.sp, color = Color(0xFF92400E))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlatActionButton(
                        text = "Cancel",
                        containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        contentColor = dialogColors.textColor,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    FlatActionButton(
                        text = "Save Calibration",
                        icon = Icons.Default.Check,
                        containerColor = primaryAccent,
                        contentColor = Color.White,
                        enabled = parsedOriginal != null && parsedCorrected != null,
                        onClick = { onSave(tentativePoint) },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
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
// BADGE DISPLAYING ACTIVE SOURCE (Non-Wrapping Single Line)
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
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
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
                letterSpacing = 0.3.sp,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
