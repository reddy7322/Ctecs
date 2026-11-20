package com.example.ui.routes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.ui.dialogs.ChainageManagementDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RouteEntity
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.camera.MiniMapPreview
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.isAppDarkTheme
import com.example.ui.theme.smartMapEdgeFade
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToSurvey: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val mapHeadingState by viewModel.mapHeadingState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImportingRoute.collectAsStateWithLifecycle()
    val routeImportStatus by viewModel.routeImportStatus.collectAsStateWithLifecycle()
    val routeImportError by viewModel.routeImportError.collectAsStateWithLifecycle()

    var routeToDelete by remember { mutableStateOf<RouteEntity?>(null) }
    var showMarkerInspector by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    val c = CtecsTheme.colors

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Highway Alignments",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = c.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Camera",
                            tint = c.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.background,
                    titleContentColor = c.textPrimary
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Active Route Hero Card
            item {
                ActiveRouteCard(
                    activeRoute = activeRoute,
                    liveLocation = locationState.point,
                    liveBearing = mapHeadingState.headingDegrees ?: locationState.bearing,
                    gpsAccuracy = locationState.accuracy,
                    mapHeadingState = mapHeadingState,
                    onInspectMarkers = { showMarkerInspector = true },
                    onCalibrate = { showCalibrationDialog = true }
                )
            }

            // 2. KML / KMZ File & URL Picker Component & Survey Action
            item {
                KmlKmzFilePickerCard(
                    onFileSelected = { uri, resolvedName ->
                        viewModel.importRoute(uri, resolvedName)
                    },
                    isImporting = isImporting,
                    onImportFromUrl = { url ->
                        viewModel.importRouteFromUrl(url)
                    },
                    importStatusMessage = routeImportStatus,
                    importErrorMessage = routeImportError,
                    onDismissStatus = { viewModel.clearRouteImportMessages() }
                )
            }

            item {
                Button(
                    onClick = onNavigateToSurvey,
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent.copy(alpha = 0.14f),
                        contentColor = c.accent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("survey_alignment_button")
                ) {
                    Icon(imageVector = Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Launch Live Alignment Survey", fontWeight = FontWeight.Bold)
                }
            }

            // 3. Imported Routes List Header
            item {
                Text(
                    text = "Saved Route Files (${allRoutes.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // List of saved route files
            if (allRoutes.isEmpty()) {
                item {
                    Text(
                        text = "No route files imported yet. Tap 'Import KMZ/KML' to add your alignment file.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(allRoutes, key = { it.id }) { route ->
                    RouteListItem(
                        route = route,
                        isActive = activeRoute?.id == route.id,
                        onSelect = { viewModel.selectActiveRoute(route.id) },
                        onDelete = { routeToDelete = route }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    routeToDelete?.let { route ->
        val dialogColors = com.example.ui.theme.rememberDialogColors()
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            shape = dialogColors.shape,
            containerColor = dialogColors.containerColor,
            tonalElevation = 0.dp,
            title = { Text("Delete Alignment", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
            text = { Text("Are you sure you want to delete '${route.name}'? Chainage matching will no longer use this route.", color = dialogColors.textSecondaryColor) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRoute(route.id)
                        routeToDelete = null
                    }
                ) {
                    Text("Delete", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }

    // Marker Inspector Modal
    if (showMarkerInspector && activeRoute != null) {
        MarkerInspectorDialog(
            routeData = activeRoute!!,
            onDismiss = { showMarkerInspector = false }
        )
    }

    // Smart Chainage Calibration Modal
    if (showCalibrationDialog && activeRoute != null) {
        val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
        val locationState by viewModel.locationState.collectAsStateWithLifecycle()
        val correctionsForRoute by viewModel.activeRouteCorrections.collectAsStateWithLifecycle()
        val isCorrectionsEnabled by viewModel.isRouteCorrectionsEnabled.collectAsStateWithLifecycle()

        ChainageManagementDialog(
            viewModel = viewModel,
            matchResult = matchResult,
            activeRoute = activeRoute,
            currentGpsPoint = locationState.point,
            currentGpsAccuracy = locationState.accuracy,
            correctionPoints = correctionsForRoute,
            isCorrectionsEnabled = isCorrectionsEnabled,
            initialTab = 1,
            onDismiss = { showCalibrationDialog = false }
        )
    }
}

@Composable
fun ActiveRouteCard(
    activeRoute: RouteData?,
    liveLocation: com.example.model.GeoPoint? = null,
    liveBearing: Float? = null,
    gpsAccuracy: Float = 0f,
    mapHeadingState: com.example.geo.MapHeadingState? = null,
    onInspectMarkers: () -> Unit,
    onCalibrate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ACTIVE ALIGNMENT",
                        color = c.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (activeRoute != null) c.success.copy(alpha = 0.16f) else c.error.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (activeRoute != null) "Ready for Matching" else "No Active Route",
                        color = if (activeRoute != null) c.success else c.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (activeRoute != null) {
                Text(
                    text = activeRoute.name,
                    color = c.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                val startStr = activeRoute.startChainage?.format() ?: "---"
                val endStr = activeRoute.endChainage?.format() ?: "---"

                Text(
                    text = "Chainage: $startStr  →  $endStr  (%.1f km coverage)".format(Locale.US, activeRoute.totalCoverageKm),
                    color = c.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Parsed Markers: ${activeRoute.markers.size} (50m intervals) • Alignments: ${activeRoute.alignments.size}",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )

                Text(
                    text = "Bounds: %.4fN, %.4fE  to  %.4fN, %.4fE".format(
                        Locale.US,
                        activeRoute.bounds.minLat, activeRoute.bounds.minLng,
                        activeRoute.bounds.maxLat, activeRoute.bounds.maxLng
                    ),
                    color = c.textMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Embedded Dynamic Interactive Map Preview of the Active Route Alignment
                MiniMapPreview(
                    location = liveLocation ?: activeRoute.bounds.center,
                    bearing = liveBearing,
                    routeData = activeRoute,
                    highwayTag = activeRoute.name,
                    gpsAccuracy = gpsAccuracy,
                    showGpsBadge = liveLocation != null && gpsAccuracy > 0f,
                    showProjectTag = true,
                    mapHeadingState = mapHeadingState,
                    interactive = true,
                    autoFitRoute = true,
                    orientationMode = com.example.model.MiniMapOrientationMode.DIRECTION_UP,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .smartMapEdgeFade(fadeToColor = c.surface, edgeSize = 10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onInspectMarkers,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.secondarySurface,
                            contentColor = c.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("inspect_markers_button")
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Markers", maxLines = 1, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onCalibrate,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("calibrate_route_button")
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Smart Calibration", maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "Import a KMZ or KML highway alignment file to enable real-time chainage tracking.",
                    color = c.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun RouteListItem(
    route: RouteEntity,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) c.secondarySurface else c.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = route.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = c.success,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val start = route.startChainageMetres?.let { "${it / 1000}+%03d".format(it % 1000) } ?: "---"
                val end = route.endChainageMetres?.let { "${it / 1000}+%03d".format(it % 1000) } ?: "---"

                Text(
                    text = "Range: $start → $end • ${route.markerCount} markers",
                    fontSize = 12.sp,
                    color = c.accent
                )
                Text(
                    text = "Imported: ${sdf.format(Date(route.importedAt))}",
                    fontSize = 11.sp,
                    color = c.textSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isActive) {
                    TextButton(onClick = onSelect) {
                        Text("Activate", color = c.accent, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = c.error)
                }
            }
        }
    }
}

@Composable
fun MarkerInspectorDialog(
    routeData: RouteData,
    onDismiss: () -> Unit
) {
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val c = CtecsTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        title = { Text("Parsed Chainage Markers (${routeData.markers.size})", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                Text(
                    "Displaying chainage markers parsed from ${routeData.fileName}:",
                    fontSize = 12.sp,
                    color = dialogColors.textSecondaryColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(routeData.markers.take(200)) { marker ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = marker.chainage.format(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = c.accent
                            )
                            Text(
                                text = "%.5fN, %.5fE".format(Locale.US, marker.point.latitude, marker.point.longitude),
                                fontSize = 12.sp,
                                color = dialogColors.textColor
                            )
                        }
                        SmartFadeDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = c.accent)
            }
        }
    )
}
