package com.example.ui.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Chainage
import com.example.survey.export.KmzSurveyExporter
import com.example.survey.model.OffsetSide
import com.example.survey.model.SurveyCalibrationPoint
import com.example.survey.model.SurveyMarker
import com.example.survey.model.SurveyPoint
import com.example.survey.model.SurveySession
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SurveyReviewScreen(
    session: SurveySession,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = rememberSurveyThemeColors()

    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var markers by remember { mutableStateOf<List<SurveyMarker>>(emptyList()) }
    var calibrations by remember { mutableStateOf<List<SurveyCalibrationPoint>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) }

    // Editable metadata
    var surveyName by remember(session.name) { mutableStateOf(session.name) }
    var highwayName by remember(session.highwayName) { mutableStateOf(session.highwayName) }
    var stretchName by remember(session.stretchName) { mutableStateOf(session.stretchName) }
    var showChangeOffsetDialog by remember { mutableStateOf(false) }
    var currentOffsetM by remember(session.centrelineOffsetMetres) { mutableDoubleStateOf(session.centrelineOffsetMetres) }
    var currentSide by remember(session.offsetSide) { mutableStateOf(session.offsetSide) }
    var isAutoMode by remember(session.isOffsetAuto) { mutableStateOf(session.isOffsetAuto) }
    var isExporting by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session.id) {
        isLoadingData = true
        points = viewModel.surveyRepository.getPointsSync(session.id)
        markers = viewModel.surveyRepository.getMarkersSync(session.id)
        calibrations = viewModel.surveyRepository.getCalibrationsSync(session.id)
        isLoadingData = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SURVEY REVIEW & KMZ EXPORT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.gold
                        )
                        Text(
                            text = "${session.highwayName} • ${session.surveyDate}",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        SurveyGlassIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onNavigateBack,
                            variant = GlassButtonVariant.NEUTRAL,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        SurveyGlassIconButton(
                            icon = if (colors.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme Mode",
                            onClick = { showThemeDialog = true },
                            variant = GlassButtonVariant.GOLD,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.headerBackground,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        if (isLoadingData) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.gold)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Summary Dashboard View Card
                item {
                    SurveySummaryDashboardCard(
                        session = session,
                        points = points,
                        markers = markers,
                        calibrations = calibrations,
                        colors = colors
                    )
                }

                // 2. Export & Direct Integration Actions Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.sky.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "EXPORT & CTECS INTEGRATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.sky,
                                fontWeight = FontWeight.Bold
                            )

                            // Direct 1-tap route integration
                            SurveyGlassButton(
                                text = "Import Directly into CTECS Routes",
                                icon = Icons.Default.AltRoute,
                                onClick = {
                                    coroutineScope.launch {
                                        isExporting = true
                                        val res = KmzSurveyExporter.importIntoCtecsRoutes(
                                            context = context,
                                            session = session,
                                            points = points,
                                            markers = markers,
                                            calibrations = calibrations,
                                            routeRepository = viewModel.routeRepository
                                        )
                                        isExporting = false
                                        if (res.isSuccess) {
                                            viewModel.showToast("✓ Imported into CTECS Routes as Active Route!", ToastType.SUCCESS)
                                        } else {
                                            viewModel.showToast("Failed to import route: ${res.exceptionOrNull()?.message}", ToastType.ERROR)
                                        }
                                    }
                                },
                                enabled = !isExporting && points.isNotEmpty(),
                                variant = GlassButtonVariant.SUCCESS,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // KMZ & KML Export Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SurveyGlassButton(
                                    text = "Export KMZ",
                                    icon = Icons.Default.Download,
                                    onClick = {
                                        coroutineScope.launch {
                                            isExporting = true
                                            try {
                                                val file = KmzSurveyExporter.exportKmzFile(context, session, points, markers, calibrations)
                                                KmzSurveyExporter.shareSurveyFile(context, file, isKmz = true)
                                                viewModel.showToast("KMZ Exported: ${file.name}", ToastType.SUCCESS)
                                            } catch (e: Exception) {
                                                viewModel.showToast("KMZ export error: ${e.message}", ToastType.ERROR)
                                            } finally {
                                                isExporting = false
                                            }
                                        }
                                    },
                                    enabled = !isExporting && points.isNotEmpty(),
                                    variant = GlassButtonVariant.GOLD,
                                    modifier = Modifier.weight(1f)
                                )

                                SurveyGlassButton(
                                    text = "Export KML",
                                    icon = Icons.Default.Share,
                                    onClick = {
                                        coroutineScope.launch {
                                            isExporting = true
                                            try {
                                                val file = KmzSurveyExporter.exportKmlFile(context, session, points, markers, calibrations)
                                                KmzSurveyExporter.shareSurveyFile(context, file, isKmz = false)
                                                viewModel.showToast("KML Exported: ${file.name}", ToastType.SUCCESS)
                                            } catch (e: Exception) {
                                                viewModel.showToast("KML export error: ${e.message}", ToastType.ERROR)
                                            } finally {
                                                isExporting = false
                                            }
                                        }
                                    },
                                    enabled = !isExporting && points.isNotEmpty(),
                                    variant = GlassButtonVariant.SKY,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Metadata Editing Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "EDIT SURVEY ATTRIBUTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.gold,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = surveyName,
                                onValueChange = { surveyName = it },
                                label = { Text("Survey Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.inputFocusedBorder,
                                    unfocusedBorderColor = colors.inputBorder,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedLabelColor = colors.inputFocusedBorder,
                                    unfocusedLabelColor = colors.textSecondary,
                                    focusedContainerColor = colors.inputBackground,
                                    unfocusedContainerColor = colors.inputBackground
                                )
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = highwayName,
                                    onValueChange = { highwayName = it },
                                    label = { Text("Highway Name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.inputFocusedBorder,
                                        unfocusedBorderColor = colors.inputBorder,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        focusedLabelColor = colors.inputFocusedBorder,
                                        unfocusedLabelColor = colors.textSecondary,
                                        focusedContainerColor = colors.inputBackground,
                                        unfocusedContainerColor = colors.inputBackground
                                    )
                                )
                                OutlinedTextField(
                                    value = stretchName,
                                    onValueChange = { stretchName = it },
                                    label = { Text("Stretch") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.inputFocusedBorder,
                                        unfocusedBorderColor = colors.inputBorder,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        focusedLabelColor = colors.inputFocusedBorder,
                                        unfocusedLabelColor = colors.textSecondary,
                                        focusedContainerColor = colors.inputBackground,
                                        unfocusedContainerColor = colors.inputBackground
                                    )
                                )
                            }

                            // Centreline Offset status & edit row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Centreline Offset", fontSize = 11.sp, color = colors.textSecondary)
                                    Text(
                                        text = "${currentOffsetM}m ${currentSide.name} ${if (isAutoMode) "(Auto)" else "(Manual)"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.gold
                                    )
                                }
                                SurveyGlassButton(
                                    text = "Change Offset",
                                    onClick = { showChangeOffsetDialog = true },
                                    variant = GlassButtonVariant.SKY,
                                    height = 34.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            SurveyGlassButton(
                                text = "Save Attributes",
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.surveyManager.updateSessionMetadata(
                                            name = surveyName,
                                            highwayName = highwayName,
                                            stretchName = stretchName,
                                            startChainageMetres = session.startChainageMetres,
                                            direction = session.direction,
                                            lane = session.lane,
                                            customLaneName = session.customLaneName
                                        )
                                        viewModel.showToast("Survey attributes updated", ToastType.SUCCESS)
                                    }
                                },
                                variant = GlassButtonVariant.GOLD,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }

                // 4. KM Stones List
                item {
                    Text(
                        text = "KM STONE MARKERS (${markers.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.gold,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (markers.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No KM stone markers were triggered during this survey.",
                                    fontSize = 12.sp,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }
                } else {
                    items(markers) { marker ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(marker.displayChainage, fontWeight = FontWeight.Bold, color = colors.gold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.sky.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = marker.markerType.shortBadge,
                                                fontSize = 9.sp,
                                                color = colors.sky,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (marker.isManuallyCalibrated) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.green.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "CALIBRATED",
                                                    fontSize = 8.sp,
                                                    color = colors.green,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${String.format(Locale.US, "%.5f, %.5f", marker.latitude, marker.longitude)} • ±${marker.accuracy.toInt()}m ${if (marker.notes.isNotBlank()) " • " + marker.notes else ""}",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                SurveyGlassIconButton(
                                    icon = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.surveyManager.deleteMarker(marker.id)
                                            markers = markers.filter { it.id != marker.id }
                                            viewModel.showToast("Marker removed", ToastType.INFO)
                                        }
                                    },
                                    variant = GlassButtonVariant.DESTRUCTIVE,
                                    size = 32.dp,
                                    iconSize = 16.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangeOffsetDialog) {
        ChangeOffsetDialog(
            currentOffsetM = currentOffsetM,
            currentSide = currentSide,
            defaultLaneOffsetM = session.lane.defaultOffsetMetres,
            isAutoMode = isAutoMode,
            onDismiss = { showChangeOffsetDialog = false },
            onSaveOffset = { newOffset, newSide, auto ->
                showChangeOffsetDialog = false
                currentOffsetM = newOffset
                currentSide = newSide
                isAutoMode = auto
                coroutineScope.launch {
                    viewModel.surveyManager.updateCentrelineOffset(newOffset, newSide, auto)
                    viewModel.showToast("Centreline offset updated: ${newOffset}m ${newSide.name}", ToastType.SUCCESS)
                }
            }
        )
    }

    if (showThemeDialog) {
        val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = "system")
        SurveyThemeSelectorDialog(
            currentTheme = currentThemeMode,
            onSelectTheme = { newMode ->
                viewModel.setThemeMode(newMode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

/**
 * Summary dashboard view in the Survey Review screen that calculates and displays key metrics:
 * total distance, average speed, max speed, number of KM stone markers captured, duration, and chainage progression.
 * Fully adapts between Light and Dark themes.
 */
@Composable
fun SurveySummaryDashboardCard(
    session: SurveySession,
    points: List<SurveyPoint>,
    markers: List<SurveyMarker>,
    calibrations: List<SurveyCalibrationPoint>,
    colors: SurveyThemeColors = rememberSurveyThemeColors()
) {
    // 1. Total Distance Calculations
    val totalDistanceM = session.totalDistanceTravelledMetres
    val totalDistanceKm = totalDistanceM / 1000.0
    val totalDistStr = String.format(Locale.US, "%.2f km", totalDistanceKm)
    val totalDistSub = String.format(Locale.US, "%,.0f m", totalDistanceM)

    // 2. Speed Calculations (Average & Max Speed)
    val movingPoints = points.filter { it.speedMps > 0.3f }
    val avgSpeedKmh = if (movingPoints.isNotEmpty()) {
        movingPoints.map { it.speedMps }.average() * 3.6
    } else if (points.size >= 2) {
        val elapsedSec = (points.last().timestamp - points.first().timestamp) / 1000.0
        if (elapsedSec > 5.0) (totalDistanceM / elapsedSec) * 3.6 else 0.0
    } else {
        (session.lastSpeedMps ?: 0f) * 3.6
    }
    val maxSpeedKmh = if (points.isNotEmpty()) {
        (points.maxOfOrNull { it.speedMps } ?: 0f) * 3.6
    } else 0.0

    // 3. KM Stone Marker Metrics
    val totalMarkers = markers.size
    val fullKmCount = markers.count { it.markerType == com.example.survey.model.KmMarkerType.FULL_KM }
    val halfKmCount = markers.count { it.markerType == com.example.survey.model.KmMarkerType.HALF_KM }
    val quarterKmCount = markers.count { it.markerType == com.example.survey.model.KmMarkerType.QUARTER_KM }

    // 4. Survey Duration Calculation
    val durationMs = if (points.size >= 2) {
        (points.last().timestamp - points.first().timestamp).coerceAtLeast(0L)
    } else {
        (session.updatedAt - session.createdAt).coerceAtLeast(0L)
    }
    val hours = durationMs / 3600000
    val minutes = (durationMs % 3600000) / 60000
    val seconds = (durationMs % 60000) / 1000
    val durationStr = if (hours > 0) {
        String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02dm %02ds", minutes, seconds)
    }

    val chDiff = kotlin.math.abs(session.currentChainageMetres - session.startChainageMetres)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, colors.gold.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dashboard Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.gold.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = colors.gold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SUMMARY DASHBOARD",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.gold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = session.name.ifBlank { "Highway Survey Alignment" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.green.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.green.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(colors.green, CircleShape)
                        )
                        Text(
                            text = session.status.label,
                            color = colors.green,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.divider)

            // 4-Card Primary Metric Grid (2 Rows x 2 Columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Total Distance
                DashboardMetricTile(
                    title = "TOTAL DISTANCE",
                    value = totalDistStr,
                    subtext = totalDistSub,
                    icon = Icons.Default.AltRoute,
                    accentColor = colors.green,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Average Speed
                DashboardMetricTile(
                    title = "AVERAGE SPEED",
                    value = String.format(Locale.US, "%.1f km/h", avgSpeedKmh),
                    subtext = "Max: ${String.format(Locale.US, "%.1f", maxSpeedKmh)} km/h",
                    icon = Icons.Default.Speed,
                    accentColor = colors.sky,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 3: KM Stones Captured
                DashboardMetricTile(
                    title = "KM STONES CAPTURED",
                    value = "$totalMarkers",
                    subtext = "$fullKmCount Full • $halfKmCount Half",
                    icon = Icons.Default.Place,
                    accentColor = colors.gold,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )

                // Metric 4: Survey Duration
                DashboardMetricTile(
                    title = "SURVEY DURATION",
                    value = durationStr,
                    subtext = "${points.size} GPS points",
                    icon = Icons.Default.Timer,
                    accentColor = if (colors.isDark) Color(0xFFA855F7) else Color(0xFF7E22CE),
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }

            // Chainage Progression Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (colors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chainage Progression",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${Chainage(chDiff).format()} span",
                            fontSize = 11.sp,
                            color = colors.gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Start", fontSize = 9.5.sp, color = colors.textMuted)
                            Text(session.displayStartChainage, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colors.gold.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("End", fontSize = 9.5.sp, color = colors.textMuted)
                            Text(session.displayCurrentChainage, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.gold)
                        }
                    }
                }
            }

            // Alignment & Technical Metadata Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Road Side", fontSize = 10.sp, color = colors.textMuted)
                    Text(session.displayRoadSide, fontSize = 11.5.sp, color = colors.sky, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Lane", fontSize = 10.sp, color = colors.textMuted)
                    Text(session.displayLaneName, fontSize = 11.5.sp, color = colors.gold, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Planned End", fontSize = 10.sp, color = colors.textMuted)
                    Text(session.displayPlannedEndChainage, fontSize = 11.5.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Interval", fontSize = 10.sp, color = colors.textMuted)
                    val intvStr = session.confirmationIntervalMetres?.let { "${it / 1000} KM" } ?: "Off"
                    Text(intvStr, fontSize = 11.5.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricTile(
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    colors: SurveyThemeColors,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (colors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = if (colors.isDark) 0.35f else 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = subtext,
                fontSize = 9.5.sp,
                color = accentColor.copy(alpha = if (colors.isDark) 0.9f else 1.0f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
