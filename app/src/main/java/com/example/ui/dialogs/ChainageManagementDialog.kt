package com.example.ui.dialogs

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import com.example.model.KmzManualCalibration
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.ctecsBorderlessInputColors
import com.example.ui.theme.rememberDialogColors
import kotlin.math.roundToLong

/**
 * Compact, single-reference Manual KMZ/KML Chainage Calibration popup.
 *
 * Rules:
 * - Offset = Manual Reference Chainage - Original KMZ/KML Chainage at the reference position
 * - Applies only within ±2,000 m (2 km) along the selected road alignment
 * - Recalibrating cleanly replaces any previous calibration on the alignment
 * - Reset restores original KMZ/KML chainage without deleting KMZ/KML files
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

    var manualRefInput by rememberSaveable(activeRoute?.id, effectiveAlignment?.id) {
        mutableStateOf(activeCalibration?.manualReferenceChainage?.format().orEmpty())
    }
    var gpsSnapshotStatus by remember(activeRoute?.id, effectiveAlignment?.id) {
        mutableStateOf<String?>(null)
    }

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
                    gpsPoint = currentGpsPoint
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
        activeCalibration,
        effectiveAlignment?.id
    ) {
        if (activeRoute != null && liveRawKmzChainage != null) {
            val mapForEval = if (activeCalibration != null) {
                mapOf(
                    KmzCalibrationEngine.storageKey(activeCalibration.routeId, activeCalibration.alignmentId) to activeCalibration
                )
            } else {
                emptyMap()
            }
            KmzCalibrationEngine.evaluateAndApply(
                rawChainage = liveRawKmzChainage,
                distanceAlongAlignmentMetres = matchResult.distanceAlongAlignmentMetres,
                routeData = activeRoute,
                calibrations = mapForEval,
                matchedAlignmentId = effectiveAlignment?.id.orEmpty(),
                matchedAlignmentName = effectiveAlignment?.name.orEmpty()
            )
        } else {
            null
        }
    }

    val previewOffsetMetres: Long? = remember(parsedManualRef, liveRawKmzChainage, activeCalibration) {
        when {
            parsedManualRef != null && liveRawKmzChainage != null ->
                parsedManualRef.metres - liveRawKmzChainage.metres
            activeCalibration != null -> activeCalibration.offsetMetres
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
                .widthIn(max = 460.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
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
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // Current Original KMZ/KML Chainage + Optional GPS Capture Button
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
                            Text(
                                text = "Original KMZ/KML Chainage",
                                style = MaterialTheme.typography.labelSmall,
                                color = dialogColors.textSecondaryColor
                            )
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
                                else -> "GPS ±${currentGpsAccuracy.toInt()}m • Current alignment position"
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
                            Text(
                                "Applies offset within ±2,000 m (2 km) along alignment",
                                color = dialogColors.textSecondaryColor
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
                        enabled = activeCalibration != null,
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

                SmartFadeDivider()

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
            }
        }
    }
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
