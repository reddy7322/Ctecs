package com.example.ui.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlin.math.roundToLong

/**
 * CTECS – Manual Chainage Input & Smart KMZ Chainage Correction Dialog.
 *
 * Provides:
 * 1. Manual Chainage Input:
 *    - Supports formats such as "198+880", "198880", and "200+000".
 *    - Validates and automatically formats the entered chainage.
 *    - Allows manual correction while GPS tracking is active.
 *    - Provides Apply, Cancel, and Reset options.
 *    - Clearly indicates when MANUAL override is active.
 * 2. Smart KMZ Chainage Correction & Management:
 *    - Add, Edit, Delete, Enable/Disable, and Reset custom correction points for the selected KMZ route.
 *    - Displays correction points in route order with:
 *      - Correction Point Chainage
 *      - GPS coordinates
 *      - Route position (KMZ chainage & cumulative route distance)
 *      - Distance from previous correction point
 *    - Warns on duplicates, invalid chainages, out-of-route positions, unexpected chainage jumps,
 *      and route direction order conflicts.
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
    val manualChainage by viewModel.manualChainage.collectAsStateWithLifecycle()
    val manualChainageText by viewModel.manualChainageText.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var pointToEdit by remember { mutableStateOf<ChainageCorrectionPoint?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val primaryAccent = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val isManualOverrideActive = matchResult.source == ChainageSource.MANUAL || manualChainage != null || !manualChainageText.isNullOrBlank()

    // Ensure correction points are displayed in route order
    val orderedCorrectionPoints = remember(correctionPoints, activeRoute) {
        SmartChainageCorrectionEngine.sortPointsInRouteOrder(correctionPoints, activeRoute)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            val isCompactViewport = maxHeight < 720.dp
            val maxDialogHeight = (maxHeight * 0.96f).coerceAtLeast(460.dp)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = dialogColors.containerColor),
                border = BorderStroke(1.dp, dialogColors.borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth(0.95f)
                    .heightIn(max = maxDialogHeight)
                    .testTag("chainage_management_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = if (isCompactViewport) 8.dp else 10.dp
                        )
                ) {
                    // ----------------------------------------------------
                    // 1. HEADER: Title & Active Source Badge (MANUAL / CUSTOM CORRECTION / KMZ / GPS)
                    // ----------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(primaryAccent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinearScale,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Chainage Input & Correction",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dialogColors.titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Active: ${matchResult.displayChainage} (${matchResult.detectedSide})",
                                    fontSize = 10.5.sp,
                                    color = dialogColors.textSecondaryColor,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ChainageSourceBadge(
                                source = matchResult.source,
                                isDark = dialogColors.isDark
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = dialogColors.textSecondaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ----------------------------------------------------
                    // 2. MODE TABS: Manual Chainage Input | Smart KMZ Correction
                    // ----------------------------------------------------
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, dialogColors.borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabs = listOf(
                                "Manual Chainage Input" to "tab_manual_chainage",
                                "Smart KMZ Correction (${orderedCorrectionPoints.size})" to "tab_smart_kmz_correction"
                            )
                            tabs.forEachIndexed { idx, (label, tag) ->
                                val isSelected = selectedTab == idx
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) primaryAccent else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedTab = idx }
                                        .testTag(tag)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else dialogColors.textSecondaryColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ----------------------------------------------------
                    // 3. TAB CONTENT
                    // ----------------------------------------------------
                    if (selectedTab == 0) {
                        ManualChainageInputTabContent(
                            viewModel = viewModel,
                            matchResult = matchResult,
                            isManualOverrideActive = isManualOverrideActive,
                            manualChainageText = manualChainageText ?: manualChainage?.format(),
                            primaryAccent = primaryAccent,
                            dialogColors = dialogColors,
                            onDismiss = onDismiss
                        )
                    } else {
                        // If Manual Override is currently active, show a clear priority notice banner
                        if (isManualOverrideActive) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (dialogColors.isDark) Color(0xFF78350F).copy(alpha = 0.35f) else Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Manual Override Active (${matchResult.displayChainage})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (dialogColors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                        )
                                        Text(
                                            text = "Manual override takes priority (#1). Reset manual override to use Smart KMZ Correction.",
                                            fontSize = 9.5.sp,
                                            color = if (dialogColors.isDark) Color(0xFFFEF3C7) else Color(0xFFB45309)
                                        )
                                    }
                                    FlatActionButton(
                                        text = "Reset Manual",
                                        icon = Icons.Default.RestartAlt,
                                        containerColor = Color(0xFFD97706),
                                        contentColor = Color.White,
                                        onClick = { viewModel.clearManualChainage() },
                                        height = 28.dp
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            SmartKmzCalibrationContent(
                                activeRoute = activeRoute,
                                projectName = projectProfile.highwayId,
                                matchResult = matchResult,
                                correctionPoints = orderedCorrectionPoints,
                                isCorrectionsEnabled = isCorrectionsEnabled,
                                primaryAccent = primaryAccent,
                                dialogColors = dialogColors,
                                isCompactViewport = isCompactViewport,
                                onSelectAlignment = { viewModel.selectAlignment(it) },
                                onToggleMaster = { enabled ->
                                    viewModel.setRouteCorrectionsEnabled(enabled)
                                },
                                onTogglePoint = { pt, en ->
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

                        Spacer(modifier = Modifier.height(if (isCompactViewport) 6.dp else 8.dp))

                        // Bottom Management Actions for Smart KMZ Correction
                        if (activeRoute != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                FlatActionButton(
                                    text = "Add Correction Point",
                                    icon = Icons.Default.Add,
                                    containerColor = primaryAccent,
                                    contentColor = Color.White,
                                    height = if (isCompactViewport) 34.dp else 36.dp,
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (orderedCorrectionPoints.isNotEmpty()) {
                                        FlatActionButton(
                                            text = "Reset Corrections",
                                            icon = Icons.Default.Delete,
                                            containerColor = if (dialogColors.isDark) Color(0xFF3F1D24) else Color(0xFFFEE2E2),
                                            contentColor = Color(0xFFEF4444),
                                            height = if (isCompactViewport) 32.dp else 34.dp,
                                            onClick = { showResetConfirmDialog = true },
                                            modifier = Modifier.weight(1.05f)
                                        )
                                    }

                                    FlatActionButton(
                                        text = "Export KMZ",
                                        icon = Icons.Default.Share,
                                        containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                        contentColor = primaryAccent,
                                        height = if (isCompactViewport) 32.dp else 34.dp,
                                        onClick = {
                                            exportCalibratedKmzFile(context, activeRoute, orderedCorrectionPoints, coroutineScope)
                                        },
                                        modifier = Modifier
                                            .weight(0.95f)
                                            .testTag("export_calibrated_kmz_button")
                                    )

                                    FlatActionButton(
                                        text = "Save Changes",
                                        icon = Icons.Default.Check,
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White,
                                        height = if (isCompactViewport) 32.dp else 34.dp,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1.05f)
                                    )
                                }
                            }
                        } else {
                            FlatActionButton(
                                text = "Close",
                                containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                contentColor = dialogColors.textColor,
                                height = 36.dp,
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // Sub-dialog: Add / Edit Correction Point
    if (showAddEditDialog && activeRoute != null) {
        AddEditCorrectionPointDialog(
            activeRoute = activeRoute,
            projectId = projectProfile.highwayId,
            existingPoints = orderedCorrectionPoints,
            editingPoint = pointToEdit,
            currentGpsPoint = currentGpsPoint,
            currentGpsAccuracy = currentGpsAccuracy,
            currentRawChainage = matchResult.rawUncorrectedChainage ?: matchResult.chainage,
            currentRouteDistanceM = matchResult.distanceAlongAlignmentMetres,
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

    // Reset Confirmation Dialog (Never silently discard existing correction points)
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
                        text = "Reset Custom Chainage Corrections?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Remove all ${orderedCorrectionPoints.size} custom correction points for '${activeRoute.name}'? The original KMZ route data will remain intact and chainage will revert to original KMZ values.",
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
                            text = "Reset Corrections",
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
// TAB 0: MANUAL CHAINAGE INPUT
// -------------------------------------------------------------
@Composable
private fun ManualChainageInputTabContent(
    viewModel: MainViewModel,
    matchResult: ChainageMatchResult,
    isManualOverrideActive: Boolean,
    manualChainageText: String?,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onDismiss: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val initialInput = remember(manualChainageText, matchResult.displayChainage) {
        when {
            !manualChainageText.isNullOrBlank() -> manualChainageText
            matchResult.chainage != null -> matchResult.chainage.format()
            else -> ""
        }
    }
    var chainageInput by remember { mutableStateOf(initialInput) }
    val parsedChainage = remember(chainageInput) { Chainage.parse(chainageInput) }
    val isInputValid = chainageInput.isNotBlank() && parsedChainage != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Active Status Indicator Banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isManualOverrideActive) {
                Color(0xFFDC2626).copy(alpha = 0.14f)
            } else {
                if (dialogColors.isDark) Color(0xFF131D2E) else Color(0xFFF0F9FF)
            },
            border = BorderStroke(
                1.dp,
                if (isManualOverrideActive) Color(0xFFEF4444) else primaryAccent.copy(alpha = 0.35f)
            ),
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
                        text = if (isManualOverrideActive) {
                            "MANUAL CHAINAGE OVERRIDE ACTIVE"
                        } else {
                            "AUTOMATIC TRACKING (${matchResult.source.label})"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isManualOverrideActive) Color(0xFFEF4444) else primaryAccent
                    )
                    Text(
                        text = if (isManualOverrideActive) {
                            "Locked at ${matchResult.displayChainage} until Reset or changed"
                        } else {
                            "Live Chainage: ${matchResult.displayChainage} • Side: ${matchResult.detectedSide}"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogColors.titleColor
                    )
                }
                ChainageSourceBadge(
                    source = matchResult.source,
                    isDark = dialogColors.isDark
                )
            }
        }

        // Manual Chainage Input Field
        Text(
            text = "Enter Manual Chainage (supports \"198+880\", \"198880\", \"200+000\"):",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = dialogColors.textSecondaryColor
        )

        OutlinedTextField(
            value = chainageInput,
            onValueChange = { chainageInput = it },
            label = { Text("Manual Chainage") },
            placeholder = { Text("e.g. 198+880, 198880, or 200+000") },
            singleLine = true,
            isError = chainageInput.isNotBlank() && parsedChainage == null,
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    if (parsedChainage != null && chainageInput.trim() != parsedChainage.format()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primaryAccent.copy(alpha = 0.16f),
                            modifier = Modifier.clickable {
                                chainageInput = parsedChainage.format()
                            }
                        ) {
                            Text(
                                text = "→ ${parsedChainage.format()}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (chainageInput.isNotEmpty()) {
                        IconButton(
                            onClick = { chainageInput = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = dialogColors.textSecondaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    if (parsedChainage != null) {
                        viewModel.setManualChainage(parsedChainage.format())
                        onDismiss()
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = dialogColors.textColor,
                unfocusedTextColor = dialogColors.textColor,
                focusedBorderColor = primaryAccent,
                unfocusedBorderColor = dialogColors.inputBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_chainage_input")
        )

        // Live Validation & Auto-Format Preview Strip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                chainageInput.isBlank() -> if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                parsedChainage != null -> Color(0x1A10B981)
                else -> Color(0x1AEF4444)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    chainageInput.isBlank() -> "Enter a chainage value to override live chainage."
                    parsedChainage != null -> "Validated & Auto-Formatted: ${parsedChainage.format()} (${parsedChainage.metres} metres)"
                    else -> "Invalid chainage format. Enter formats like 198+880, 198880, or 200+000."
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    chainageInput.isBlank() -> dialogColors.textSecondaryColor
                    parsedChainage != null -> Color(0xFF10B981)
                    else -> Color(0xFFEF4444)
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Quick Nudge Buttons (-100m, -50m, -10m, +10m, +50m, +100m) + Use Live KMZ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(-100L to "-100m", -50L to "-50m", -10L to "-10m", 10L to "+10m", 50L to "+50m", 100L to "+100m").forEach { (delta, label) ->
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, dialogColors.borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable {
                            val base = parsedChainage?.metres
                                ?: matchResult.chainage?.metres
                                ?: matchResult.rawUncorrectedChainage?.metres
                                ?: 0L
                            chainageInput = Chainage((base + delta).coerceAtLeast(0L)).format()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = dialogColors.textColor
                        )
                    }
                }
            }
        }

        val liveKmzRef = matchResult.rawUncorrectedChainage
        if (liveKmzRef != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = primaryAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        chainageInput = liveKmzRef.format()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fill from Live KMZ Route Position (${liveKmzRef.format()})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Apply, Reset, and Cancel Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlatActionButton(
                text = "Cancel",
                containerColor = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                contentColor = dialogColors.textColor,
                onClick = onDismiss,
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("cancel_manual_chainage_button")
            )

            FlatActionButton(
                text = "Reset",
                icon = Icons.Default.RestartAlt,
                containerColor = if (dialogColors.isDark) Color(0xFF3F1D24) else Color(0xFFFEE2E2),
                contentColor = Color(0xFFEF4444),
                enabled = isManualOverrideActive || chainageInput.isNotEmpty(),
                onClick = {
                    chainageInput = ""
                    viewModel.clearManualChainage()
                    onDismiss()
                },
                modifier = Modifier
                    .weight(0.95f)
                    .testTag("clear_manual_chainage_button")
            )

            FlatActionButton(
                text = "Apply",
                icon = Icons.Default.Check,
                containerColor = primaryAccent,
                contentColor = Color.White,
                enabled = isInputValid,
                onClick = {
                    val formatted = parsedChainage?.format() ?: chainageInput.trim()
                    viewModel.setManualChainage(formatted)
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1.15f)
                    .testTag("save_manual_chainage_button")
            )
        }
    }
}

// -------------------------------------------------------------
// MATERIAL FLAT BUTTON COMPONENT
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
    height: Dp = 36.dp,
    fontSize: TextUnit = 11.5.sp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
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
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = modifier.height(height)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// TAB 1: SMART KMZ CHAINAGE CORRECTION & MANAGEMENT
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartKmzCalibrationContent(
    activeRoute: RouteData?,
    projectName: String,
    matchResult: ChainageMatchResult,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    isCompactViewport: Boolean,
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
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(primaryAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No KMZ Highway Route Selected",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Import and select a KML/KMZ route in the Routes tab to add custom chainage correction points. Manual chainage override remains available in the Manual Chainage Input tab.",
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
    val routeAuditWarnings = remember(correctionPoints, activeRoute) {
        SmartChainageCorrectionEngine.auditCorrectionPoints(correctionPoints, activeRoute)
    }

    val sectionSpacing = if (isCompactViewport) 5.dp else 6.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        // 1. Selected Route, Project & Enable/Disable Correction Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (dialogColors.isDark) Color(0xFF162032) else Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, dialogColors.borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = if (projectName.isNotBlank()) "${activeRoute.name} ($projectName)" else activeRoute.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = dialogColors.titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val rRange = if (activeRoute.startChainage != null && activeRoute.endChainage != null) {
                            "${activeRoute.startChainage.format()} → ${activeRoute.endChainage.format()}"
                        } else "KMZ Route"
                        val dirStr = if (diagnostics.isChainageIncreasing) "Increasing" else "Decreasing"
                        Text(
                            text = "$rRange ($dirStr) • ${correctionPoints.count { it.isEnabled }}/${correctionPoints.size} Points Enabled",
                            fontSize = 10.sp,
                            color = primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isCorrectionsEnabled) "ENABLED" else "DISABLED",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCorrectionsEnabled) Color(0xFF10B981) else dialogColors.textSecondaryColor
                        )
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Switch(
                                checked = isCorrectionsEnabled,
                                onCheckedChange = onToggleMaster,
                                colors = SwitchDefaults.colors(checkedThumbColor = primaryAccent),
                                modifier = Modifier
                                    .scale(0.72f)
                                    .testTag("toggle_smart_corrections")
                            )
                        }
                    }
                }

                if (activeRoute.alignments.size > 1) {
                    val isAuto = activeRoute.selectedAlignmentId == null
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AlignmentFlatChip(
                            label = "Auto (Best Match)",
                            isSelected = isAuto,
                            primaryAccent = primaryAccent,
                            dialogColors = dialogColors,
                            onClick = { onSelectAlignment(null) }
                        )
                        activeRoute.alignments.forEachIndexed { idx, align ->
                            val cleanLabel = formatCleanAlignmentName(
                                rawName = align.name,
                                index = idx,
                                total = activeRoute.alignments.size,
                                carriagewayType = align.carriagewayType
                            )
                            AlignmentFlatChip(
                                label = cleanLabel,
                                isSelected = activeRoute.selectedAlignmentId == align.id,
                                primaryAccent = primaryAccent,
                                dialogColors = dialogColors,
                                onClick = { onSelectAlignment(align.id) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Live KMZ vs Smart Corrected Chainage Status Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (dialogColors.isDark) Color(0xFF131D2E) else Color(0xFFF0F9FF),
            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = if (isCompactViewport) 6.dp else 7.dp
                )
            ) {
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
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${matchResult.detectedSide} • ±${String.format(Locale.US, "%.0fm", matchResult.gpsAccuracy)} GPS • ${String.format(Locale.US, "%.1fm", matchResult.offsetMetres)} Offset",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogColors.textSecondaryColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (dialogColors.isDark) Color(0xFF0F172A) else Color.White,
                    border = BorderStroke(1.dp, dialogColors.borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Original KMZ",
                                fontSize = 10.sp,
                                color = dialogColors.textSecondaryColor,
                                maxLines = 1
                            )
                            Text(
                                text = matchResult.rawUncorrectedChainage?.format() ?: matchResult.chainage?.format() ?: "---",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.textColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = primaryAccent
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Active Chainage (${matchResult.source.label})",
                                fontSize = 10.sp,
                                color = primaryAccent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = matchResult.displayChainage,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (matchResult.correctionPointInfo != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Interpolated Section: ${matchResult.correctionPointInfo}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val currentKmz = matchResult.rawUncorrectedChainage ?: matchResult.chainage
                if (currentKmz != null) {
                    Spacer(modifier = Modifier.height(5.dp))
                    FlatActionButton(
                        text = "Add Correction Point at Current Position (${currentKmz.format()})",
                        icon = Icons.Default.MyLocation,
                        containerColor = primaryAccent.copy(alpha = if (dialogColors.isDark) 0.22f else 0.14f),
                        contentColor = primaryAccent,
                        onClick = onQuickCalibrateHere,
                        height = 30.dp,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Route-Wide Correction Warnings Banner (if any issues detected)
        if (routeAuditWarnings.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (dialogColors.isDark) Color(0xFF451A03).copy(alpha = 0.6f) else Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    routeAuditWarnings.forEach { warn ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = warn,
                                fontSize = 10.sp,
                                color = if (dialogColors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                            )
                        }
                    }
                }
            }
        }

        // 4. Correction Points List (Displayed in Route Order)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CORRECTION POINTS IN ROUTE ORDER (${correctionPoints.size})",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = dialogColors.textSecondaryColor
            )
            if (correctionPoints.isNotEmpty()) {
                Text(
                    text = "Tap point to edit",
                    fontSize = 9.5.sp,
                    color = dialogColors.textSecondaryColor.copy(alpha = 0.8f)
                )
            }
        }

        if (correctionPoints.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, dialogColors.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Custom Correction Points Added",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add custom correction points along '${activeRoute.name}' to automatically interpolate corrected chainage by route distance.",
                        fontSize = 10.sp,
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
                    val currRouteDist = SmartChainageCorrectionEngine.resolveRouteDistanceMetres(pt, activeRoute)
                    val prevPt = if (index > 0) correctionPoints[index - 1] else null
                    val prevRouteDist = if (prevPt != null) {
                        SmartChainageCorrectionEngine.resolveRouteDistanceMetres(prevPt, activeRoute)
                    } else null
                    val distFromPrevMetres = if (prevRouteDist != null) {
                        val geomSpan = abs(currRouteDist - prevRouteDist).roundToLong()
                        if (geomSpan > 0L) geomSpan else abs(pt.originalKmzMetres - prevPt!!.originalKmzMetres)
                    } else null

                    CorrectionPointCard(
                        index = index + 1,
                        point = pt,
                        routeDistanceMetres = currRouteDist,
                        distFromPrevMetres = distFromPrevMetres,
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

private fun formatCleanAlignmentName(
    rawName: String,
    index: Int,
    total: Int,
    carriagewayType: String
): String {
    val stripped = rawName
        .replace(Regex("\\[[^\\]]*\\](:\\d+)?"), "")
        .replace(Regex("(?i)\\b(block\\s*reference|acdbpolyline|polyline|linestring|entity)\\b"), "")
        .replace(Regex("[:_\\-]+$"), "")
        .trim()
    val baseName = when {
        stripped.isNotBlank() -> stripped
        total == 1 -> "Main Alignment"
        else -> "Alignment ${index + 1}"
    }
    return if (baseName.contains("($carriagewayType)", ignoreCase = true)) {
        baseName
    } else {
        "$baseName ($carriagewayType)"
    }
}

@Composable
private fun AlignmentFlatChip(
    label: String,
    isSelected: Boolean,
    primaryAccent: Color,
    dialogColors: DialogThemeTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (isSelected) {
            primaryAccent.copy(alpha = if (dialogColors.isDark) 0.24f else 0.14f)
        } else {
            if (dialogColors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) primaryAccent else dialogColors.borderColor
        ),
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) primaryAccent else dialogColors.textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// CORRECTION POINT ITEM CARD (Displays all required Section 5 details)
// - Correction Point Chainage
// - Route position (KMZ chainage & route distance)
// - GPS coordinates
// - Distance from previous correction point
// - Enable/Disable, Edit, Delete
// -------------------------------------------------------------
@Composable
private fun CorrectionPointCard(
    index: Int,
    point: ChainageCorrectionPoint,
    routeDistanceMetres: Double,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Row 1: #Index + Correction Point Chainage + Shift Badge + Enable/Disable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = primaryAccent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Point $index",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "Correct: ${point.correctedChainage.format()}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (point.isEnabled) dialogColors.titleColor else dialogColors.textSecondaryColor,
                        maxLines = 1,
                        softWrap = false
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
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = point.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = primaryAccent),
                        modifier = Modifier.scale(0.72f)
                    )
                }
            }

            // Row 2: Route Position & Distance from Previous Correction Point
            val routeDistFormatted = String.format(Locale.US, "%.0fm", routeDistanceMetres)
            val prevSpanStr = if (distFromPrevMetres != null) {
                "From Prev: ${distFromPrevMetres}m"
            } else {
                "From Prev: Start (0m)"
            }
            Text(
                text = "Route Pos: KMZ ${point.originalKmzChainage.format()} ($routeDistFormatted along route) • $prevSpanStr",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = dialogColors.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Row 3: GPS Coordinates + Optional Note + Edit & Delete Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val gpsStr = if (point.latitude != 0.0 || point.longitude != 0.0) {
                    String.format(Locale.US, "GPS: %.5f, %.5f", point.latitude, point.longitude)
                } else {
                    "GPS: Route Projected"
                }
                val notePart = if (point.description.isNotBlank()) " • ${point.description}" else ""
                Text(
                    text = "$gpsStr$notePart",
                    fontSize = 9.5.sp,
                    color = dialogColors.textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = primaryAccent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable(onClick = onEdit)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Correction Point",
                                tint = primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Edit",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable(onClick = onDelete)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Correction Point",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Delete",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-DIALOG: ADD / EDIT CORRECTION POINT
// Supports selecting Current GPS Position OR Route Location / Marker
// -------------------------------------------------------------
@Composable
private fun AddEditCorrectionPointDialog(
    activeRoute: RouteData,
    projectId: String,
    existingPoints: List<ChainageCorrectionPoint>,
    editingPoint: ChainageCorrectionPoint?,
    currentGpsPoint: GeoPoint?,
    currentGpsAccuracy: Float,
    currentRawChainage: Chainage?,
    currentRouteDistanceM: Double,
    primaryAccent: Color,
    onDismiss: () -> Unit,
    onSave: (ChainageCorrectionPoint) -> Unit
) {
    val dialogColors = rememberDialogColors()

    // Position Selection Mode: 0 = Current GPS Position, 1 = Route Location / KMZ Position
    var positionSourceMode by remember {
        mutableIntStateOf(if (editingPoint == null && currentGpsPoint != null && currentRawChainage != null) 0 else 1)
    }

    var originalKmzInput by remember {
        mutableStateOf(
            editingPoint?.originalKmzChainage?.format()
                ?: currentRawChainage?.format()
                ?: activeRoute.startChainage?.format()
                ?: "198+500"
        )
    }
    var correctedInput by remember {
        mutableStateOf(
            editingPoint?.correctedChainage?.format()
                ?: currentRawChainage?.format()
                ?: activeRoute.startChainage?.format()
                ?: "198+500"
        )
    }
    var descriptionInput by remember {
        mutableStateOf(editingPoint?.description ?: "")
    }

    val parsedOriginal = remember(originalKmzInput) { Chainage.parse(originalKmzInput) }
    val parsedCorrected = remember(correctedInput) { Chainage.parse(correctedInput) }

    // Resolve exact route position (distance along geometry + projected coordinates)
    val resolvedRoutePos = remember(activeRoute, parsedOriginal, positionSourceMode, currentGpsPoint, editingPoint) {
        val targetMetres = parsedOriginal?.metres
            ?: currentRawChainage?.metres
            ?: activeRoute.startChainage?.metres
            ?: 0L
        val gpsForResolve = when {
            positionSourceMode == 0 && currentGpsPoint != null -> currentGpsPoint
            editingPoint != null && editingPoint.originalKmzMetres == targetMetres -> editingPoint.point
            else -> null
        }
        SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
            routeData = activeRoute,
            targetKmzMetres = targetMetres,
            fallbackGps = gpsForResolve
        )
    }

    val calculatedOffset = remember(parsedOriginal, parsedCorrected) {
        if (parsedOriginal != null && parsedCorrected != null) {
            parsedCorrected.metres - parsedOriginal.metres
        } else 0L
    }

    val tentativePoint = remember(
        editingPoint,
        projectId,
        parsedOriginal,
        parsedCorrected,
        resolvedRoutePos,
        descriptionInput
    ) {
        val origM = parsedOriginal?.metres ?: 0L
        val corrM = parsedCorrected?.metres ?: 0L
        ChainageCorrectionPoint(
            id = editingPoint?.id ?: java.util.UUID.randomUUID().toString(),
            routeId = activeRoute.id,
            projectId = projectId,
            latitude = resolvedRoutePos.point.latitude,
            longitude = resolvedRoutePos.point.longitude,
            originalKmzMetres = origM,
            correctedMetres = corrM,
            routeDistanceMetres = resolvedRoutePos.routeDistanceMetres,
            description = descriptionInput.trim(),
            isEnabled = editingPoint?.isEnabled ?: true
        )
    }

    // Distance from previous correction point in route order
    val distFromPreviousMetres = remember(tentativePoint, existingPoints, activeRoute) {
        val otherPoints = existingPoints.filter { it.id != tentativePoint.id }
        val ordered = SmartChainageCorrectionEngine.sortPointsInRouteOrder(otherPoints + tentativePoint, activeRoute)
        val idx = ordered.indexOfFirst { it.id == tentativePoint.id }
        if (idx > 0) {
            val prev = ordered[idx - 1]
            val prevDist = SmartChainageCorrectionEngine.resolveRouteDistanceMetres(prev, activeRoute)
            val currDist = SmartChainageCorrectionEngine.resolveRouteDistanceMetres(tentativePoint, activeRoute)
            val span = abs(currDist - prevDist).roundToLong()
            if (span > 0L) span else abs(tentativePoint.originalKmzMetres - prev.originalKmzMetres)
        } else null
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
                .widthIn(max = 490.dp)
                .fillMaxWidth(0.94f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (editingPoint != null) "Edit Correction Point" else "Add Correction Point",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = dialogColors.titleColor
                )

                // Position Source Selector: Current GPS Position vs Route Location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (positionSourceMode == 0) primaryAccent.copy(alpha = 0.18f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (positionSourceMode == 0) primaryAccent else dialogColors.borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                positionSourceMode = 0
                                if (currentRawChainage != null) {
                                    originalKmzInput = currentRawChainage.format()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = if (positionSourceMode == 0) primaryAccent else dialogColors.textSecondaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (currentRawChainage != null) {
                                    "Current GPS (${currentRawChainage.format()})"
                                } else {
                                    "Current GPS Position"
                                },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (positionSourceMode == 0) primaryAccent else dialogColors.textSecondaryColor,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (positionSourceMode == 1) primaryAccent.copy(alpha = 0.18f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (positionSourceMode == 1) primaryAccent else dialogColors.borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { positionSourceMode = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (positionSourceMode == 1) primaryAccent else dialogColors.textSecondaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "KMZ Route Location",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (positionSourceMode == 1) primaryAccent else dialogColors.textSecondaryColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Quick Route Marker Picker when in Route Location mode
                if (positionSourceMode == 1 && activeRoute.markers.isNotEmpty()) {
                    val sampleMarkers = remember(activeRoute.markers) {
                        val list = activeRoute.markers
                        if (list.size <= 12) list
                        else {
                            val step = (list.size / 10).coerceAtLeast(1)
                            list.filterIndexed { idx, _ -> idx % step == 0 || idx == list.lastIndex }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        sampleMarkers.forEach { m ->
                            val formattedCh = m.chainage.format()
                            val isSelectedMarker = parsedOriginal?.metres == m.chainage.metres
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelectedMarker) primaryAccent.copy(alpha = 0.2f) else if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isSelectedMarker) primaryAccent else dialogColors.borderColor),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        originalKmzInput = formattedCh
                                    }
                            ) {
                                Text(
                                    text = formattedCh,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelectedMarker) primaryAccent else dialogColors.textColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Original KMZ Route Position Input
                OutlinedTextField(
                    value = originalKmzInput,
                    onValueChange = {
                        originalKmzInput = it
                        positionSourceMode = 1
                    },
                    label = { Text("KMZ Route Position (Original Chainage)") },
                    placeholder = { Text("e.g. 198+450 or 198450") },
                    singleLine = true,
                    isError = originalKmzInput.isNotBlank() && parsedOriginal == null,
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

                // Correct Chainage Input
                OutlinedTextField(
                    value = correctedInput,
                    onValueChange = { correctedInput = it },
                    label = { Text("Correct Chainage (True Highway Chainage)") },
                    placeholder = { Text("e.g. 198+500, 198500, or 200+000") },
                    singleLine = true,
                    isError = correctedInput.isNotBlank() && parsedCorrected == null,
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

                // Quick Offset Nudge Buttons (-50m, -10m, +10m, +50m)
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
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val base = parsedCorrected?.metres ?: parsedOriginal?.metres ?: 0L
                                    correctedInput = Chainage((base + delta).coerceAtLeast(0L)).format()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dialogColors.textColor
                                )
                            }
                        }
                    }
                }

                // Resolved Metadata Summary: Route Position, GPS Coordinates, Distance from Previous Point, Shift
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (parsedOriginal != null && parsedCorrected != null) {
                        if (calculatedOffset >= 0) Color(0x1A10B981) else Color(0x1A38BDF8)
                    } else {
                        Color(0x1AEF4444)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (parsedOriginal != null && parsedCorrected != null) {
                            val sign = if (calculatedOffset >= 0) "+$calculatedOffset" else "$calculatedOffset"
                            Text(
                                text = "Correction: ${parsedOriginal.format()} → ${parsedCorrected.format()} (Shift: ${sign}m)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (calculatedOffset >= 0) Color(0xFF10B981) else primaryAccent
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Route Pos: %.0fm along KMZ • GPS: %.5f, %.5f",
                                    resolvedRoutePos.routeDistanceMetres,
                                    resolvedRoutePos.point.latitude,
                                    resolvedRoutePos.point.longitude
                                ),
                                fontSize = 10.sp,
                                color = dialogColors.textSecondaryColor
                            )
                            Text(
                                text = if (distFromPreviousMetres != null) {
                                    "Distance from Previous Correction Point: ${distFromPreviousMetres}m"
                                } else {
                                    "Distance from Previous Correction Point: First Point on Route (0m)"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = dialogColors.textSecondaryColor
                            )
                        } else {
                            Text(
                                text = "Invalid chainage format. Enter valid chainages (e.g. 198+500, 198500, or 200+000).",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                // Landmark / Description (Optional)
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Landmark / Note (Optional)") },
                    placeholder = { Text("e.g. KM Stone 198+500, Culvert, Toll Plaza") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = dialogColors.inputBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Validation Warnings Banner (Duplicates, Out of KMZ Range, Jumps, Direction Conflicts)
                if (validation.warnings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (dialogColors.isDark) Color(0xFF451A03).copy(alpha = 0.65f) else Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            validation.warnings.forEach { warn ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = warn,
                                        fontSize = 10.5.sp,
                                        color = if (dialogColors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }

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
                        text = "Save Correction Point",
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
// BADGE DISPLAYING ACTIVE SOURCE (MANUAL / CUSTOM CORRECTION / KMZ / GPS)
// -------------------------------------------------------------
@Composable
fun ChainageSourceBadge(
    source: ChainageSource,
    isDark: Boolean = com.example.ui.theme.isAppDarkTheme(),
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor, label) = when (source) {
        ChainageSource.MANUAL -> Quadruple(
            if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.55f) else Color(0xFFFEE2E2),
            if (isDark) Color(0xFFEF4444) else Color(0xFFFCA5A5),
            if (isDark) Color(0xFFFECACA) else Color(0xFFB91C1C),
            "MANUAL"
        )
        ChainageSource.CUSTOM_CORRECTION -> Quadruple(
            if (isDark) Color(0xFF4C1D95).copy(alpha = 0.55f) else Color(0xFFEDE9FE),
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
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
