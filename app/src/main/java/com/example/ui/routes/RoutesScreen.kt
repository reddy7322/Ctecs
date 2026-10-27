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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImportingRoute.collectAsStateWithLifecycle()

    var routeToDelete by remember { mutableStateOf<RouteEntity?>(null) }
    var showMarkerInspector by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "imported_route.kmz"
            viewModel.importRoute(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Highway Alignments", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    onInspectMarkers = { showMarkerInspector = true }
                )
            }

            // 2. Import & Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f).testTag("import_kmz_button")
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(6.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                        Text("Import KMZ/KML")
                    }

                    OutlinedButton(
                        onClick = { viewModel.loadBundledSampleRoute() },
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f).testTag("load_sample_route_button")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Load NH-58 KMZ")
                    }
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
                        text = "No route files imported yet. Tap 'Load NH-58 KMZ' or import your own .kmz/.kml file.",
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
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text("Delete Alignment") },
            text = { Text("Are you sure you want to delete '${route.name}'? Chainage matching will no longer use this route.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRoute(route.id)
                        routeToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text("Cancel")
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
}

@Composable
fun ActiveRouteCard(
    activeRoute: RouteData?,
    onInspectMarkers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ACTIVE ALIGNMENT",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (activeRoute != null) Color(0x3310B981) else Color(0x33EF4444)
                ) {
                    Text(
                        text = if (activeRoute != null) "Ready for Matching" else "No Active Route",
                        color = if (activeRoute != null) Color(0xFF10B981) else Color(0xFFEF4444),
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
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                val startStr = activeRoute.startChainage?.format() ?: "---"
                val endStr = activeRoute.endChainage?.format() ?: "---"

                Text(
                    text = "Chainage: $startStr  →  $endStr  (%.1f km coverage)".format(Locale.US, activeRoute.totalCoverageKm),
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Parsed Markers: ${activeRoute.markers.size} (50m intervals) • Alignments: ${activeRoute.alignments.size}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                Text(
                    text = "Bounds: %.4fN, %.4fE  to  %.4fN, %.4fE".format(
                        Locale.US,
                        activeRoute.bounds.minLat, activeRoute.bounds.minLng,
                        activeRoute.bounds.maxLat, activeRoute.bounds.maxLng
                    ),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onInspectMarkers,
                    modifier = Modifier.fillMaxWidth().testTag("inspect_markers_button")
                ) {
                    Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Inspect Parsed Chainage Markers")
                }
            } else {
                Text(
                    text = "Import a KMZ or KML highway alignment file to enable real-time chainage tracking.",
                    color = Color(0xFF94A3B8),
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
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant
        ),
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
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = Color(0xFF10B981),
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
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "Imported: ${sdf.format(Date(route.importedAt))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isActive) {
                    TextButton(onClick = onSelect) {
                        Text("Activate")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parsed Chainage Markers (${routeData.markers.size})", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                Text(
                    "Displaying chainage markers parsed from ${routeData.fileName}:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(routeData.markers.take(200)) { marker ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = marker.chainage.format(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = "%.5fN, %.5fE".format(Locale.US, marker.point.latitude, marker.point.longitude),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Divider(color = Color(0x33475569))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
