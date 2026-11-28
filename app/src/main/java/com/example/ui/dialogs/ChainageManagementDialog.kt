package com.example.ui.dialogs

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.KmzCalibrationEngine
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.KmzCalibrationCoverageStatus
import com.example.model.KmzCalibrationRecordStatus
import com.example.model.KmzCalibrationSuggestion
import com.example.model.KmzCalibrationSuggestionStatus
import com.example.model.KmzManualCalibration
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.calibration.SmartAnimatedAlignmentTrack
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.ctecsBorderlessInputColors
import com.example.ui.theme.rememberDialogColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Smart KMZ/KML Manual Reference Chainage Calibration Dialog.
 *
 * Includes:
 * - Centralized Manual Reference Chainage Calibration (±2 km coverage in both directions)
 * - Smart Calibration Suggestion Engine (GPS <= ±5 m, lateral offset <= 20 m, never auto-applied)
 * - Left / Right Parallel Centerline Offset Detection display
 * - Smart Animated Alignment Tracking along real offline KMZ/KML geometry
 * - Smart Calibration Chainage List with full Edit (with history) and Delete (with confirmation)
 */
@Composable
fun ChainageManagementDialog(
    viewModel: MainViewModel,
    matchResult: ChainageMatchResult,
    activeRoute: RouteData?,
    currentGpsPoint: GeoPoint?,
    currentGpsAccuracy: Float,
    activeCalibration: KmzManualCalibration?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isImportingRoute by viewModel.isImportingRoute.collectAsStateWithLifecycle()
    val routeImportStatus by viewModel.routeImportStatus.collectAsStateWithLifecycle()
    val routeImportError by viewModel.routeImportError.collectAsStateWithLifecycle()
    val calibrationList by viewModel.routeKmzCalibrationsList.collectAsStateWithLifecycle()
    val calibrationSuggestion by viewModel.calibrationSuggestionState.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveDisplayFileName(context, uri) ?: "imported_route.kmz"
            viewModel.importRoute(uri, fileName)
        }
    }

    KmzCalibrationDialogContent(
        matchResult = matchResult,
        activeRoute = activeRoute,
        currentGpsPoint = currentGpsPoint,
        currentGpsAccuracy = currentGpsAccuracy,
        activeCalibration = activeCalibration,
        calibrationList = calibrationList,
        calibrationSuggestion = calibrationSuggestion,
        currentSpeedMps = locationState.speed,
        isImportingRoute = isImportingRoute,
        routeImportStatus = routeImportStatus,
        routeImportError = routeImportError,
        onSelectAlignment = { alignId -> viewModel.selectAlignment(alignId) },
        onImportKmzClick = {
            safLauncher.launch(
                arrayOf(
                    "application/vnd.google-earth.kmz",
                    "application/vnd.google-earth.kml+xml",
                    "application/zip",
                    "application/octet-stream",
                    "*/*"
                )
            )
        },
        onCaptureGpsClick = { viewModel.refreshLocation() },
        onApplyCalibration = { refText, origMetres, gpsPt, alignId ->
            viewModel.applyManualKmzCalibration(
                manualReferenceInput = refText,
                originalKmzMetresOverride = origMetres,
                gpsPointOverride = gpsPt,
                alignmentIdOverride = alignId
            )
        },
        onEditCalibration = { calId, newManualRef, newOrigRef, newGpsPt ->
            viewModel.editManualKmzCalibration(
                calibrationId = calId,
                newManualReferenceInput = newManualRef,
                newOriginalKmzInput = newOrigRef,
                newGpsPointOverride = newGpsPt,
                newGpsAccuracyOverride = currentGpsAccuracy
            )
        },
        onDeleteCalibration = { calId ->
            viewModel.deleteManualKmzCalibration(calibrationId = calId)
        },
        onResetCalibration = { alignId ->
            viewModel.resetManualKmzCalibration(alignmentIdOverride = alignId)
        },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KmzCalibrationDialogContent(
    matchResult: ChainageMatchResult,
    activeRoute: RouteData?,
    currentGpsPoint: GeoPoint?,
    currentGpsAccuracy: Float,
    activeCalibration: KmzManualCalibration?,
    calibrationList: List<KmzManualCalibration> = emptyList(),
    calibrationSuggestion: KmzCalibrationSuggestion? = null,
    currentSpeedMps: Float? = null,
    isImportingRoute: Boolean = false,
    routeImportStatus: String? = null,
    routeImportError: String? = null,
    onSelectAlignment: (String?) -> Unit = {},
    onImportKmzClick: () -> Unit = {},
    onCaptureGpsClick: () -> Unit = {},
    onApplyCalibration: (
        manualReferenceInput: String,
        originalKmzMetres: Long?,
        gpsPoint: GeoPoint?,
        alignmentId: String?
    ) -> Unit,
    onEditCalibration: (
        calibrationId: String,
        newManualReferenceInput: String,
        newOriginalKmzInput: String?,
        newGpsPoint: GeoPoint?
    ) -> Unit = { _, _, _, _ -> },
    onDeleteCalibration: (calibrationId: String) -> Unit = {},
    onResetCalibration: (alignmentId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    val dialogColors = rememberDialogColors()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val effectiveAlignment = remember(activeRoute, matchResult.matchedAlignmentId) {
        activeRoute?.let {
            KmzCalibrationEngine.resolveEffectiveAlignment(
                routeData = it,
                alignmentId = matchResult.matchedAlignmentId.takeIf { id -> id.isNotBlank() }
            )
        }
    }

    val effectiveCalibrationList = remember(calibrationList, activeCalibration) {
        if (calibrationList.isNotEmpty()) {
            calibrationList
        } else if (activeCalibration != null) {
            listOf(activeCalibration)
        } else {
            emptyList()
        }
    }

    val calibrationMapForEval = remember(effectiveCalibrationList) {
        effectiveCalibrationList.associateBy { KmzCalibrationEngine.recordStorageKey(it) }
    }

    var manualRefInput by rememberSaveable(activeRoute?.id, effectiveAlignment?.id) {
        mutableStateOf(activeCalibration?.manualReferenceChainage?.format().orEmpty())
    }
    var gpsSnapshotStatus by remember(activeRoute?.id, effectiveAlignment?.id) {
        mutableStateOf<String?>(null)
    }

    var calibrationToEdit by remember { mutableStateOf<KmzManualCalibration?>(null) }
    var calibrationToDelete by remember { mutableStateOf<KmzManualCalibration?>(null) }

    val liveRawKmzChainage: Chainage? = remember(matchResult, activeRoute?.id, currentGpsPoint) {
        when {
            matchResult.rawUncorrectedChainage != null &&
                matchResult.confidence != MatchConfidence.NO_MATCH -> matchResult.rawUncorrectedChainage
            (matchResult.source == ChainageSource.KMZ || matchResult.source == ChainageSource.CUSTOM_CORRECTION) &&
                matchResult.chainage != null &&
                matchResult.confidence != MatchConfidence.NO_MATCH -> matchResult.chainage
            activeRoute != null && currentGpsPoint != null && effectiveAlignment != null -> {
                KmzCalibrationEngine.resolveAlignmentPositionFromGps(
                    routeData = activeRoute,
                    alignment = effectiveAlignment,
                    gpsPoint = currentGpsPoint,
                    gpsAccuracy = currentGpsAccuracy
                )?.let { Chainage(it.originalKmzMetres) }
            }
            else -> null
        }
    }

    val (parsedManualRef, parseError) = remember(manualRefInput) {
        if (manualRefInput.isBlank()) null to null
        else KmzCalibrationEngine.parseManualReferenceChainage(manualRefInput)
    }

    val liveEval = remember(
        matchResult,
        liveRawKmzChainage,
        activeRoute,
        calibrationMapForEval,
        effectiveAlignment?.id
    ) {
        if (activeRoute != null && liveRawKmzChainage != null) {
            KmzCalibrationEngine.evaluateAndApply(
                rawChainage = liveRawKmzChainage,
                distanceAlongAlignmentMetres = matchResult.distanceAlongAlignmentMetres,
                routeData = activeRoute,
                calibrations = calibrationMapForEval,
                matchedAlignmentId = effectiveAlignment?.id.orEmpty(),
                matchedAlignmentName = effectiveAlignment?.name.orEmpty()
            )
        } else {
            null
        }
    }

    val effectiveSuggestion = remember(
        calibrationSuggestion,
        activeRoute,
        matchResult,
        currentGpsPoint,
        currentGpsAccuracy,
        calibrationMapForEval
    ) {
        calibrationSuggestion ?: KmzCalibrationEngine.evaluateCalibrationSuggestion(
            routeData = activeRoute,
            matchResult = matchResult,
            gpsPoint = currentGpsPoint,
            gpsAccuracyMetres = currentGpsAccuracy,
            calibrations = calibrationMapForEval,
            stableSampleCount = KmzCalibrationEngine.DEFAULT_REQUIRED_STABLE_SAMPLES
        )
    }

    val previewOffsetMetres: Long? = remember(parsedManualRef, liveRawKmzChainage, activeCalibration) {
        when {
            parsedManualRef != null && liveRawKmzChainage != null ->
                parsedManualRef.metres - liveRawKmzChainage.metres
            activeCalibration != null -> activeCalibration.offsetMetres
            else -> null
        }
    }

    val previewCorrectedChainage: Chainage? = remember(parsedManualRef, liveRawKmzChainage, previewOffsetMetres) {
        when {
            parsedManualRef != null && liveRawKmzChainage != null ->
                Chainage(liveRawKmzChainage.metres + (parsedManualRef.metres - liveRawKmzChainage.metres))
            liveRawKmzChainage != null && previewOffsetMetres != null ->
                Chainage(liveRawKmzChainage.metres + previewOffsetMetres)
            else -> null
        }
    }

    val coverageStatus: KmzCalibrationCoverageStatus = remember(liveEval, activeCalibration, currentGpsPoint, activeRoute) {
        when {
            liveEval != null -> liveEval.status
            activeCalibration != null && currentGpsPoint == null -> KmzCalibrationCoverageStatus.GPS_UNAVAILABLE
            activeCalibration != null -> KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE
            else -> KmzCalibrationCoverageStatus.NOT_CALIBRATED
        }
    }

    val statusColor = when (coverageStatus) {
        KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE -> c.success
        KmzCalibrationCoverageStatus.OUTSIDE_2KM_RANGE,
        KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH,
        KmzCalibrationCoverageStatus.GPS_UNAVAILABLE -> c.warning
        KmzCalibrationCoverageStatus.NOT_CALIBRATED -> dialogColors.textSecondaryColor
    }

    val canApply = activeRoute != null &&
        liveRawKmzChainage != null &&
        parsedManualRef != null &&
        parseError == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .imePadding(),
            shape = RoundedCornerShape(22.dp),
            color = dialogColors.containerColor,
            border = BorderStroke(1.dp, dialogColors.borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "KMZ Chainage Calibration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = dialogColors.titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("calibration_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close calibration dialog",
                            tint = dialogColors.textSecondaryColor
                        )
                    }
                }

                // Active KMZ/KML Alignment Summary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.secondarySurface,
                    border = BorderStroke(1.dp, c.subtleSeparator)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = activeRoute?.name ?: "No KMZ/KML Loaded",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = dialogColors.titleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("calibration_alignment_name")
                                    )
                                    val subAlignText = when {
                                        activeRoute == null -> "Import a KMZ/KML alignment to calibrate"
                                        effectiveAlignment != null -> effectiveAlignment.name
                                        else -> activeRoute.fileName
                                    }
                                    Text(
                                        text = subAlignText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dialogColors.textSecondaryColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = onImportKmzClick,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("calibration_import_kmz_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeRoute == null) "Import KMZ" else "Change KMZ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (activeRoute != null && activeRoute.alignments.size > 1) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                activeRoute.alignments.forEach { align ->
                                    val selected = align.id == effectiveAlignment?.id
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectAlignment(align.id) },
                                        label = {
                                            Text(
                                                text = align.name.ifBlank { align.id },
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = c.accent.copy(alpha = 0.16f),
                                            selectedLabelColor = c.accent
                                        )
                                    )
                                }
                            }
                        }

                        if (isImportingRoute) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = routeImportStatus ?: "Loading KMZ/KML...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dialogColors.textSecondaryColor
                                )
                            }
                        }
                        if (!routeImportError.isNullOrBlank()) {
                            Text(
                                text = routeImportError,
                                style = MaterialTheme.typography.labelSmall,
                                color = c.error
                            )
                        }
                    }
                }

                // Smart Calibration Suggestion Banner (GPS <= ±5 m & Lateral Offset <= 20 m)
                SmartCalibrationSuggestionCard(
                    suggestion = effectiveSuggestion,
                    onQuickFillSuggestedReference = { suggestedChainageText ->
                        if (manualRefInput.isBlank()) {
                            manualRefInput = suggestedChainageText
                        }
                    }
                )

                // Current Original KMZ/KML Chainage + Signed Lateral Offset + GPS Capture Button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.secondarySurface,
                    border = BorderStroke(1.dp, c.subtleSeparator)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Original KMZ/KML Chainage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dialogColors.textSecondaryColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = c.accent.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = effectiveSuggestion.parallelOffset.formattedBadge,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = c.accent,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("calibration_lateral_offset_chip")
                                    )
                                }
                            }
                            Text(
                                text = liveRawKmzChainage?.format() ?: "--+---",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.titleColor,
                                modifier = Modifier.testTag("calibration_original_chainage_text")
                            )
                            val gpsLine = gpsSnapshotStatus ?: when {
                                currentGpsPoint == null -> "Waiting for valid GPS location on alignment"
                                liveRawKmzChainage == null -> "GPS ±${currentGpsAccuracy.toInt()}m (off alignment)"
                                else -> "GPS ±${String.format(Locale.US, "%.1f", currentGpsAccuracy)}m • ${effectiveSuggestion.parallelOffset.formattedSummary}"
                            }
                            Text(
                                text = gpsLine,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = dialogColors.textSecondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                gpsSnapshotStatus = if (currentGpsPoint != null) {
                                    "Refreshed GPS location (±${currentGpsAccuracy.toInt()}m)"
                                } else {
                                    "Requesting live GPS location..."
                                }
                                onCaptureGpsClick()
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("calibration_gps_capture_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Refresh GPS location for reference chainage",
                                tint = c.accent
                            )
                        }
                    }
                }

                // Manual Reference Chainage Input
                OutlinedTextField(
                    value = manualRefInput,
                    onValueChange = { manualRefInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calibration_manual_reference_input"),
                    label = { Text("Manual Reference Chainage") },
                    placeholder = { Text("e.g. 192+230") },
                    singleLine = true,
                    isError = parseError != null && manualRefInput.isNotBlank(),
                    supportingText = if (parseError != null && manualRefInput.isNotBlank()) {
                        { Text(parseError, color = c.error) }
                    } else {
                        {
                            val previewStr = if (parsedManualRef != null && liveRawKmzChainage != null && previewOffsetMetres != null) {
                                val sign = if (previewOffsetMetres >= 0L) "+$previewOffsetMetres m" else "$previewOffsetMetres m"
                                "Preview Corrected: ${previewCorrectedChainage?.format()} (Offset $sign • ±2 km coverage)"
                            } else {
                                "Applies offset within ±2,000 m (2 km) in both directions along alignment"
                            }
                            Text(
                                text = previewStr,
                                color = dialogColors.textSecondaryColor,
                                modifier = Modifier.testTag("calibration_preview_corrected_chainage_text")
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canApply) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onApplyCalibration(
                                    manualRefInput.trim(),
                                    liveRawKmzChainage?.metres,
                                    currentGpsPoint,
                                    effectiveAlignment?.id
                                )
                            }
                        }
                    ),
                    colors = ctecsBorderlessInputColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Compact Action Buttons: Apply Calibration + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onApplyCalibration(
                                manualRefInput.trim(),
                                liveRawKmzChainage?.metres,
                                currentGpsPoint,
                                effectiveAlignment?.id
                            )
                        },
                        enabled = canApply,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent,
                            contentColor = c.onAccent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("calibration_apply_button")
                    ) {
                        Text(
                            text = "Apply Calibration",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            manualRefInput = ""
                            onResetCalibration(effectiveAlignment?.id)
                        },
                        enabled = activeCalibration != null || effectiveCalibrationList.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, dialogColors.borderColor),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("calibration_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset calibration",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Compact Status & Offset Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = statusColor.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calibration_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = when (coverageStatus) {
                                        KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE -> Icons.Default.CheckCircle
                                        KmzCalibrationCoverageStatus.NOT_CALIBRATED -> Icons.Default.Tune
                                        else -> Icons.Default.Warning
                                    },
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = coverageStatus.shortLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.testTag("calibration_coverage_status_text")
                                )
                            }

                            val offsetLabel = when {
                                previewOffsetMetres != null ->
                                    if (previewOffsetMetres >= 0L) "+${previewOffsetMetres} m" else "${previewOffsetMetres} m"
                                else -> "--"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "Offset $offsetLabel",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = statusColor,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("calibration_offset_text")
                                )
                            }
                        }

                        if (activeCalibration != null) {
                            val distText = liveEval?.distanceFromAnchorMetres?.let { d ->
                                "${d.roundToLong()} m from Ref ${activeCalibration.manualReferenceChainage.format()}"
                            } ?: "Ref ${activeCalibration.manualReferenceChainage.format()} (Orig ${activeCalibration.originalKmzChainage.format()})"
                            Text(
                                text = distText,
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogColors.titleColor
                            )
                        }

                        Text(
                            text = liveEval?.statusMessage ?: coverageStatus.detailLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = dialogColors.textSecondaryColor
                        )
                    }
                }

                SmartFadeDivider()

                // Smart Animated Alignment Tracking & Parallel Centerline Offset Canvas
                SmartAnimatedAlignmentTrack(
                    activeRoute = activeRoute,
                    effectiveAlignment = effectiveAlignment,
                    matchResult = matchResult,
                    currentGpsPoint = currentGpsPoint,
                    currentGpsAccuracy = currentGpsAccuracy,
                    speedMps = currentSpeedMps,
                    calibrations = effectiveCalibrationList,
                    activeCalibration = liveEval?.calibration ?: activeCalibration
                )

                SmartFadeDivider()

                // Smart Calibration Chainage List (Scrollable list with Edit, Delete, History, and Status)
                SmartCalibrationChainageListSection(
                    calibrations = effectiveCalibrationList,
                    activeRoute = activeRoute,
                    activeAlignmentId = effectiveAlignment?.id,
                    currentDistanceAlongMetres = matchResult.distanceAlongAlignmentMetres
                        .takeIf { matchResult.confidence != MatchConfidence.NO_MATCH },
                    activeCalibrationId = (liveEval?.calibration ?: activeCalibration)?.id,
                    onEditClick = { cal -> calibrationToEdit = cal },
                    onDeleteClick = { cal -> calibrationToDelete = cal }
                )
            }
        }
    }

    // Edit Calibration Reference Modal
    calibrationToEdit?.let { targetCal ->
        EditCalibrationReferenceDialog(
            calibration = targetCal,
            currentGpsPoint = currentGpsPoint,
            currentGpsAccuracy = currentGpsAccuracy,
            liveOriginalKmzChainage = liveRawKmzChainage,
            onSaveEdit = { newManualRefText, newOrigText, newGpsPt ->
                onEditCalibration(targetCal.id, newManualRefText, newOrigText, newGpsPt)
                calibrationToEdit = null
            },
            onDismiss = { calibrationToEdit = null }
        )
    }

    // Delete Calibration Reference Confirmation Dialog
    calibrationToDelete?.let { targetCal ->
        DeleteCalibrationConfirmDialog(
            calibration = targetCal,
            onConfirmDelete = {
                onDeleteCalibration(targetCal.id)
                calibrationToDelete = null
            },
            onDismiss = { calibrationToDelete = null }
        )
    }
}

@Composable
fun SmartCalibrationSuggestionCard(
    suggestion: KmzCalibrationSuggestion,
    onQuickFillSuggestedReference: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val badgeColor = when (suggestion.status) {
        KmzCalibrationSuggestionStatus.ELIGIBLE_SUGGEST_CALIBRATION -> c.success
        KmzCalibrationSuggestionStatus.WAIT_POOR_GPS_ACCURACY,
        KmzCalibrationSuggestionStatus.RECHECK_EXCESSIVE_LATERAL_OFFSET,
        KmzCalibrationSuggestionStatus.WAIT_STABILIZING_SAMPLES,
        KmzCalibrationSuggestionStatus.OUT_OF_ALIGNMENT_RANGE -> c.warning
        KmzCalibrationSuggestionStatus.CONFLICTING_REFERENCE_NEARBY -> c.accent
        KmzCalibrationSuggestionStatus.ALIGNMENT_OR_GPS_UNAVAILABLE -> c.textSecondary
    }

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = badgeColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.28f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_calibration_suggestion_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Smart Calibration Suggestion",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = suggestion.status.badgeLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            .testTag("smart_calibration_suggestion_badge")
                    )
                }
            }

            Text(
                text = suggestion.telemetryLine,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = c.textSecondary,
                modifier = Modifier.testTag("smart_calibration_suggestion_telemetry")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = suggestion.guidanceMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("smart_calibration_suggestion_guidance")
                )

                if (suggestion.isEligible && suggestion.originalKmzChainage != null) {
                    TextButton(
                        onClick = {
                            onQuickFillSuggestedReference(suggestion.originalKmzChainage.format())
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .testTag("suggestion_prefill_button")
                    ) {
                        Text(
                            text = "Prefill ${suggestion.originalKmzChainage.format()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.success
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartCalibrationChainageListSection(
    calibrations: List<KmzManualCalibration>,
    activeRoute: RouteData?,
    activeAlignmentId: String?,
    currentDistanceAlongMetres: Double?,
    activeCalibrationId: String?,
    onEditClick: (KmzManualCalibration) -> Unit,
    onDeleteClick: (KmzManualCalibration) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_calibration_chainage_list_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Smart Calibration Chainage List (${calibrations.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.testTag("smart_calibration_list_header")
            )
            Text(
                text = "±2 km coverage each dir",
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary
            )
        }

        if (calibrations.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = c.secondarySurface,
                border = BorderStroke(1.dp, c.subtleSeparator),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart_calibration_list_empty")
            ) {
                Text(
                    text = "No manual reference chainages saved yet. Enter a verified site chainage above to calibrate within ±2 km.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            calibrations.forEach { cal ->
                val recordStatus = remember(cal, activeRoute, activeAlignmentId, currentDistanceAlongMetres, activeCalibrationId) {
                    KmzCalibrationEngine.evaluateRecordStatus(
                        calibration = cal,
                        routeData = activeRoute,
                        activeAlignmentId = activeAlignmentId,
                        currentDistanceAlongMetres = currentDistanceAlongMetres,
                        activeCalibrationId = activeCalibrationId
                    )
                }
                val isHighlightedActive = recordStatus == KmzCalibrationRecordStatus.ACTIVE
                val statusBadgeColor = when (recordStatus) {
                    KmzCalibrationRecordStatus.ACTIVE -> c.success
                    KmzCalibrationRecordStatus.OUTSIDE_COVERAGE -> c.warning
                    KmzCalibrationRecordStatus.INACTIVE -> c.textSecondary
                    KmzCalibrationRecordStatus.INVALID -> c.error
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isHighlightedActive) c.success.copy(alpha = 0.08f) else c.secondarySurface,
                    border = BorderStroke(
                        width = if (isHighlightedActive) 1.5.dp else 1.dp,
                        color = if (isHighlightedActive) c.success.copy(alpha = 0.55f) else c.subtleSeparator
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calibration_list_item_${cal.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Top Row: Verified Ref Chainage, Offset Badge, Status Badge, Edit & Delete Icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Ref ${cal.manualReferenceChainage.format()}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary,
                                        modifier = Modifier.testTag("calibration_item_manual_ref_${cal.id}")
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = c.accent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = cal.formattedOffset,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = c.accent,
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .testTag("calibration_item_offset_${cal.id}")
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statusBadgeColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = recordStatus.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusBadgeColor,
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .testTag("calibration_item_status_${cal.id}")
                                        )
                                    }
                                }
                                Text(
                                    text = "Original KMZ/KML: ${cal.originalKmzChainage.format()} • Alignment: ${cal.alignmentName.ifBlank { cal.routeFileName.ifBlank { cal.routeId } }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("calibration_item_original_kmz_${cal.id}")
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onEditClick(cal) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("calibration_item_edit_button_${cal.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit calibration reference ${cal.manualReferenceChainage.format()}",
                                        tint = c.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteClick(cal) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("calibration_item_delete_button_${cal.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete calibration reference ${cal.manualReferenceChainage.format()}",
                                        tint = c.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Metadata Grid: GPS Accuracy, Signed Left/Right Centerline Offset, Coverage, Timestamp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GPS ${cal.formattedGpsAccuracy} • Centerline ${cal.formattedLateralOffset}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = c.textPrimary,
                                modifier = Modifier.testTag("calibration_item_gps_lateral_${cal.id}")
                            )
                            Text(
                                text = sdf.format(Date(cal.updatedAtMs)),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                color = c.textSecondary,
                                modifier = Modifier.testTag("calibration_item_datetime_${cal.id}")
                            )
                        }

                        Text(
                            text = "Coverage: ${cal.formattedCoverageRange}",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary,
                            modifier = Modifier.testTag("calibration_item_coverage_${cal.id}")
                        )

                        // Preserved Calibration Edit History indicator
                        if (cal.editHistory.isNotEmpty()) {
                            val latestHist = cal.editHistory.last()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.testTag("calibration_item_history_${cal.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = c.accentSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "History (${cal.editHistory.size}): Prev Ref ${latestHist.previousManualReferenceChainage.format()} (Orig ${latestHist.previousOriginalKmzChainage.format()}, Offset ${latestHist.formattedPreviousOffset})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = c.accentSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditCalibrationReferenceDialog(
    calibration: KmzManualCalibration,
    currentGpsPoint: GeoPoint?,
    currentGpsAccuracy: Float,
    liveOriginalKmzChainage: Chainage?,
    onSaveEdit: (
        newManualReferenceInput: String,
        newOriginalKmzInput: String?,
        newGpsPoint: GeoPoint?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    val dialogColors = rememberDialogColors()

    var manualRefText by rememberSaveable(calibration.id) {
        mutableStateOf(calibration.manualReferenceChainage.format())
    }
    var origRefText by rememberSaveable(calibration.id) {
        mutableStateOf(calibration.originalKmzChainage.format())
    }
    var useCurrentLiveGps by rememberSaveable(calibration.id) {
        mutableStateOf(false)
    }

    val (parsedManual, manualErr) = remember(manualRefText) {
        KmzCalibrationEngine.parseManualReferenceChainage(manualRefText)
    }
    val (parsedOrig, origErr) = remember(origRefText) {
        KmzCalibrationEngine.parseManualReferenceChainage(origRefText)
    }

    val recalculatedOffset = remember(parsedManual, parsedOrig) {
        if (parsedManual != null && parsedOrig != null) {
            parsedManual.metres - parsedOrig.metres
        } else null
    }

    val canSave = parsedManual != null && manualErr == null && parsedOrig != null && origErr == null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("calibration_edit_dialog"),
        title = {
            Text(
                text = "Edit Calibration Reference",
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = manualRefText,
                    onValueChange = { manualRefText = it },
                    label = { Text("Verified Manual Reference Chainage") },
                    singleLine = true,
                    isError = manualErr != null,
                    supportingText = manualErr?.let { err -> { Text(err, color = c.error) } },
                    colors = ctecsBorderlessInputColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_calibration_manual_ref_input")
                )

                OutlinedTextField(
                    value = origRefText,
                    onValueChange = { origRefText = it },
                    label = { Text("Original KMZ/KML Reference Chainage") },
                    singleLine = true,
                    isError = origErr != null,
                    supportingText = origErr?.let { err -> { Text(err, color = c.error) } },
                    colors = ctecsBorderlessInputColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_calibration_original_kmz_input")
                )

                if (currentGpsPoint != null && liveOriginalKmzChainage != null) {
                    OutlinedButton(
                        onClick = {
                            useCurrentLiveGps = true
                            origRefText = liveOriginalKmzChainage.format()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_calibration_use_live_gps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Use Live GPS Position (${liveOriginalKmzChainage.format()} • ±${currentGpsAccuracy.toInt()}m)",
                            fontSize = 12.sp
                        )
                    }
                }

                val offsetFormatted = recalculatedOffset?.let {
                    if (it >= 0L) "+$it m" else "$it m"
                } ?: "--"
                Text(
                    text = "Recalculated Correction Offset: $offsetFormatted",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = c.accent,
                    modifier = Modifier.testTag("edit_calibration_recalculated_offset_text")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveEdit(
                        manualRefText.trim(),
                        origRefText.trim(),
                        if (useCurrentLiveGps) currentGpsPoint else null
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    contentColor = c.onAccent
                ),
                modifier = Modifier.testTag("edit_calibration_save_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("edit_calibration_cancel_button")
            ) {
                Text("Cancel", color = dialogColors.textSecondaryColor)
            }
        }
    )
}

@Composable
fun DeleteCalibrationConfirmDialog(
    calibration: KmzManualCalibration,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    val dialogColors = rememberDialogColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("calibration_delete_confirm_dialog"),
        title = {
            Text(
                text = "Delete Calibration Reference?",
                fontWeight = FontWeight.Bold,
                color = dialogColors.titleColor
            )
        },
        text = {
            Text(
                text = "Delete reference ${calibration.manualReferenceChainage.format()} (Original KMZ ${calibration.originalKmzChainage.format()}, Offset ${calibration.formattedOffset})? " +
                    "This removes only this calibration record and restores original KMZ/KML chainage where no other calibration applies. Original KMZ/KML files are never deleted.",
                color = dialogColors.textSecondaryColor,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                modifier = Modifier.testTag("calibration_delete_confirm_button")
            ) {
                Text("Delete", color = c.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("calibration_delete_cancel_button")
            ) {
                Text("Cancel", color = dialogColors.textSecondaryColor)
            }
        }
    )
}

@Composable
fun ChainageSourceBadge(
    source: ChainageSource,
    isDark: Boolean = false,
    modifier: Modifier = Modifier,
    validationStatus: String? = null
) {
    val c = CtecsTheme.colors
    val badgeColor = when {
        validationStatus == KmzCalibrationCoverageStatus.OUTSIDE_2KM_RANGE.name ||
            validationStatus == KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH.name ||
            validationStatus == KmzCalibrationCoverageStatus.GPS_UNAVAILABLE.name -> c.warning
        source == ChainageSource.MANUAL -> c.error
        source == ChainageSource.CUSTOM_CORRECTION -> c.accentSecondary
        source == ChainageSource.KMZ -> c.success
        else -> c.textSecondary
    }
    val label = when {
        validationStatus == KmzCalibrationCoverageStatus.OUTSIDE_2KM_RANGE.name -> "OUTSIDE 2KM"
        validationStatus == KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH.name -> "ALIGN CHANGED"
        else -> source.label
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = if (isDark) 0.22f else 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = badgeColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun resolveDisplayFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/')
    }
}
