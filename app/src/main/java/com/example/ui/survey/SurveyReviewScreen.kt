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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.model.Chainage
import com.example.survey.engine.SurveyGeometryEngine
import com.example.survey.export.KmzSurveyExporter
import com.example.survey.model.OffsetSide
import com.example.survey.model.SurveyCalibrationPoint
import com.example.survey.model.SurveyDirection
import com.example.survey.model.SurveyLane
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

    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var markers by remember { mutableStateOf<List<SurveyMarker>>(emptyList()) }
    var calibrations by remember { mutableStateOf<List<SurveyCalibrationPoint>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) }

    // Editable metadata
    var surveyName by remember(session.name) { mutableStateOf(session.name) }
    var highwayName by remember(session.highwayName) { mutableStateOf(session.highwayName) }
    var stretchName by remember(session.stretchName) { mutableStateOf(session.stretchName) }
    var offsetM by remember(session.centrelineOffsetMetres) { mutableDoubleStateOf(session.centrelineOffsetMetres) }
    var offsetSide by remember(session.offsetSide) { mutableStateOf(session.offsetSide) }
    var isExporting by remember { mutableStateOf(false) }

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
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            text = "${session.highwayName} • ${session.surveyDate}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0A0F1D)
    ) { innerPadding ->
        if (isLoadingData) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Summary Metrics Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("SURVEY METRICS", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${session.displayStartChainage} → ${session.displayCurrentChainage}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF22C55E).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f km", session.totalDistanceTravelledMetres / 1000.0),
                                        color = Color(0xFF22C55E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Lane", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text(session.displayLaneName, fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("Direction", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text(session.direction.label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("Offset", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text("${session.centrelineOffsetMetres}m ${session.offsetSide.name}", fontSize = 12.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Survey Points: ${points.size}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                Text("KM Stones: ${markers.size}", fontSize = 11.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                Text("Segments: ${points.map { it.segmentIndex }.distinct().size}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                // 2. Export & Direct Integration Actions Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("EXPORT & CTECS INTEGRATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)

                            // Direct 1-tap route integration
                            Button(
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Directly into CTECS Routes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // KMZ & KML Export Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export KMZ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export KML", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 3. Metadata Editing Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("EDIT SURVEY ATTRIBUTES", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = surveyName,
                                onValueChange = { surveyName = it },
                                label = { Text("Survey Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                )
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = highwayName,
                                    onValueChange = { highwayName = it },
                                    label = { Text("Highway Name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFFFD700),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                    )
                                )
                                OutlinedTextField(
                                    value = stretchName,
                                    onValueChange = { stretchName = it },
                                    label = { Text("Stretch") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFFFD700),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                    )
                                )
                            }

                            Button(
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFFFFD700)),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Attributes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 4. KM Stones List
                item {
                    Text("KM STONE MARKERS (${markers.size})", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }

                if (markers.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No KM stone markers were triggered during this survey.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                } else {
                    items(markers) { marker ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
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
                                        Text(marker.displayChainage, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                                        ) {
                                            Text(marker.markerType.shortBadge, fontSize = 9.sp, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                        }
                                        if (marker.isManuallyCalibrated) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF22C55E).copy(alpha = 0.2f)
                                            ) {
                                                Text("CALIBRATED", fontSize = 8.sp, color = Color(0xFF22C55E), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${String.format(Locale.US, "%.5f, %.5f", marker.latitude, marker.longitude)} • ±${marker.accuracy.toInt()}m ${if (marker.notes.isNotBlank()) " • " + marker.notes else ""}",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.surveyManager.deleteMarker(marker.id)
                                            markers = markers.filter { it.id != marker.id }
                                            viewModel.showToast("Marker removed", ToastType.INFO)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444).copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
