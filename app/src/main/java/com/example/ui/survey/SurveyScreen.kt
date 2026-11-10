package com.example.ui.survey

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.OsmTileManager
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.data.SaveStatus
import com.example.survey.engine.SurveyGeometryEngine
import com.example.survey.model.SurveySession
import com.example.survey.model.SurveyStatus
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SurveyScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val surveyManager = viewModel.surveyManager
    val activeSession by surveyManager.activeSession.collectAsStateWithLifecycle()
    val activePoints by surveyManager.activePoints.collectAsStateWithLifecycle()
    val activeMarkers by surveyManager.activeMarkers.collectAsStateWithLifecycle()
    val saveStatus by surveyManager.saveStatus.collectAsStateWithLifecycle()
    val relocationPrompt by surveyManager.relocationPrompt.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val allSessions by surveyManager.repository.allSessions.collectAsStateWithLifecycle(emptyList())

    val currentGps = locationState.point
    val currentAccuracy = locationState.accuracy
    val currentSpeedMps = locationState.speed ?: 0f
    val currentBearing = locationState.bearing ?: 0f

    // UI Dialog & Review States
    var showNewConfigDialog by remember { mutableStateOf(false) }
    var showAddKmStoneDialog by remember { mutableStateOf(false) }
    var showChangeOffsetDialog by remember { mutableStateOf(false) }
    var showSavedSurveysDialog by remember { mutableStateOf(false) }
    var reviewSessionTarget by remember { mutableStateOf<SurveySession?>(null) }

    // Layer Visibility
    var showGpsTrack by remember { mutableStateOf(true) }
    var showCentreline by remember { mutableStateOf(true) }
    var showLaneTrack by remember { mutableStateOf(true) }
    var showMarkers by remember { mutableStateOf(true) }
    var mapType by remember { mutableStateOf("standard") }

    // Interactive Map State
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var viewRangeMetres by remember { mutableDoubleStateOf(800.0) }
    var isFollowingLocation by remember { mutableStateOf(true) }

    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()

    LaunchedEffect(currentGps, isFollowingLocation) {
        if (isFollowingLocation) {
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    // Pulse animation for recording status
    val infiniteTransition = rememberInfiniteTransition(label = "survey_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val currentZoom = remember(viewRangeMetres) {
        when {
            viewRangeMetres <= 250.0 -> 18
            viewRangeMetres <= 550.0 -> 17
            viewRangeMetres <= 1100.0 -> 16
            viewRangeMetres <= 2200.0 -> 15
            viewRangeMetres <= 4500.0 -> 14
            else -> 13
        }
    }

    // If review screen is open, show SurveyReviewScreen
    if (reviewSessionTarget != null) {
        BackHandler { reviewSessionTarget = null }
        SurveyReviewScreen(
            session = reviewSessionTarget!!,
            viewModel = viewModel,
            onNavigateBack = { reviewSessionTarget = null }
        )
        return
    }

    BackHandler {
        onNavigateBack()
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // ----------------------------------------------------
        // 1. LIVE INTERACTIVE OSM MAP CANVAS
        // ----------------------------------------------------
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        isFollowingLocation = false
                        viewRangeMetres = (viewRangeMetres / zoom).coerceIn(80.0, 10000.0)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            val _t = tileUpdate
            val w = size.width
            val h = size.height

            val anchorPoint = currentGps
                ?: activePoints.lastOrNull()?.geoPoint
                ?: GeoPoint(28.6139, 77.2090)

            // Render OSM Street or Satellite tile background
            val osmBitmap = OsmTileManager.renderMapBitmap(
                context = context,
                center = anchorPoint,
                widthPx = w.toInt().coerceAtLeast(1),
                heightPx = h.toInt().coerceAtLeast(1),
                zoom = currentZoom,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                darkTheme = true,
                mapType = mapType
            )

            if (osmBitmap != null) {
                drawImage(image = osmBitmap.asImageBitmap())
            } else {
                drawRect(color = Color(0xFF0F172A))
            }

            // 1. Original Travelled GPS Track (Emerald Green)
            if (showGpsTrack && activePoints.size >= 2) {
                val segments = activePoints.groupBy { it.segmentIndex }
                segments.forEach { (_, segPoints) ->
                    if (segPoints.size >= 2) {
                        val path = Path()
                        val (startX, startY) = OsmTileManager.geoToScreen(
                            segPoints[0].geoPoint, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                        )
                        path.moveTo(startX, startY)
                        for (i in 1 until segPoints.size) {
                            val (px, py) = OsmTileManager.geoToScreen(
                                segPoints[i].geoPoint, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                            )
                            path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF22C55E),
                            style = Stroke(width = 6f)
                        )
                    }
                }
            }

            // 2. Highway Centreline (Generated Gold Dashed Line)
            if (showCentreline && activeSession != null && activePoints.size >= 2) {
                val centrelinePts = SurveyGeometryEngine.generateCentrelineTrack(
                    points = activePoints,
                    offsetDistanceM = activeSession!!.centrelineOffsetMetres,
                    offsetSide = activeSession!!.offsetSide
                )
                if (centrelinePts.size >= 2) {
                    val clPath = Path()
                    val (clX, clY) = OsmTileManager.geoToScreen(
                        centrelinePts[0], anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                    )
                    clPath.moveTo(clX, clY)
                    for (i in 1 until centrelinePts.size) {
                        val (px, py) = OsmTileManager.geoToScreen(
                            centrelinePts[i], anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                        )
                        clPath.lineTo(px, py)
                    }
                    drawPath(
                        path = clPath,
                        color = Color(0xFFFFD700),
                        style = Stroke(
                            width = 5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f)
                        )
                    )
                }
            }

            // 3. Lane Alignment (Cyan Line)
            if (showLaneTrack && activePoints.size >= 2) {
                val lanePath = Path()
                val (lx, ly) = OsmTileManager.geoToScreen(
                    activePoints[0].geoPoint, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                )
                lanePath.moveTo(lx, ly)
                for (i in 1 until activePoints.size) {
                    val (px, py) = OsmTileManager.geoToScreen(
                        activePoints[i].geoPoint, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                    )
                    lanePath.lineTo(px, py)
                }
                drawPath(
                    path = lanePath,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = 3.5f)
                )
            }

            // 4. KM Stone Markers (Red/Gold Pin Circles with Labels)
            if (showMarkers) {
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 28f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#B30F172A")
                    style = android.graphics.Paint.Style.FILL
                }

                activeMarkers.forEach { marker ->
                    val (mx, my) = OsmTileManager.geoToScreen(
                        marker.geoPoint, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                    )
                    if (mx in -50f..(w + 50f) && my in -50f..(h + 50f)) {
                        // Marker circle pin
                        drawCircle(color = Color(0xFFEF4444), radius = 14f, center = Offset(mx, my))
                        drawCircle(color = Color.White, radius = 5f, center = Offset(mx, my))

                        // Chainage text pill above marker
                        val label = marker.displayChainage
                        val textWidth = textPaint.measureText(label)
                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            mx - textWidth / 2f - 10f,
                            my - 38f,
                            mx + textWidth / 2f + 10f,
                            my - 6f,
                            8f,
                            8f,
                            bgPaint
                        )
                        drawContext.canvas.nativeCanvas.drawText(label, mx, my - 14f, textPaint)
                    }
                }
            }

            // 5. Live Vehicle Position Indicator (Pulse Dot + Direction Cone)
            if (currentGps != null) {
                val (vx, vy) = OsmTileManager.geoToScreen(
                    currentGps, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                )
                // Accuracy circle
                val accPx = (currentAccuracy / (viewRangeMetres / (w / 2f))).toFloat().coerceIn(16f, 150f)
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                    radius = accPx,
                    center = Offset(vx, vy)
                )
                // Vehicle pulse ring
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
                    radius = 20f,
                    center = Offset(vx, vy)
                )
                // Vehicle center core
                drawCircle(color = Color(0xFF0284C7), radius = 9f, center = Offset(vx, vy))
                drawCircle(color = Color.White, radius = 4f, center = Offset(vx, vy))
            }
        }

        // ----------------------------------------------------
        // 2. TOP MINIMALIST GLASS TOOLBAR
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131B2E).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SurveyGlassIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onNavigateBack,
                            variant = GlassButtonVariant.NEUTRAL,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val title = activeSession?.highwayName?.ifBlank { activeSession?.name } ?: "Highway Survey"
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val status = activeSession?.status ?: SurveyStatus.NOT_STARTED
                                val statusColor = when (status) {
                                    SurveyStatus.RECORDING -> Color(0xFF22C55E)
                                    SurveyStatus.PAUSED -> Color(0xFFFFD700)
                                    SurveyStatus.READY_TO_RESUME -> Color(0xFF38BDF8)
                                    SurveyStatus.COMPLETED -> Color(0xFFA855F7)
                                    else -> Color.Gray
                                }
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(status.label, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("• ±${currentAccuracy.toInt()}m", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Toggle Map Style (Satellite / Street)
                        SurveyGlassIconButton(
                            icon = Icons.Default.Map,
                            contentDescription = "Map Style",
                            onClick = { mapType = if (mapType == "standard") "satellite" else "standard" },
                            variant = if (mapType == "satellite") GlassButtonVariant.GOLD else GlassButtonVariant.NEUTRAL,
                            active = mapType == "satellite",
                            size = 36.dp,
                            iconSize = 18.dp
                        )

                        // Saved Surveys Folder Button
                        SurveyGlassIconButton(
                            icon = Icons.Default.Folder,
                            contentDescription = "Saved Surveys",
                            onClick = { showSavedSurveysDialog = true },
                            variant = GlassButtonVariant.SKY,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                }
            }

            // Relocation Alert Banner (if movement detected while paused)
            if (relocationPrompt != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F).copy(alpha = 0.95f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Relocation: ${String.format(Locale.US, "%.0f m", relocationPrompt!!.distanceM)} from last point", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 12.sp)
                            Text("Resume as new segment to avoid false straight-line jump?", fontSize = 10.sp, color = Color.White)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SurveyGlassButton(
                                text = "New Seg",
                                onClick = { surveyManager.resolveRelocation(startNewSegment = true) },
                                variant = GlassButtonVariant.GOLD,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                height = 32.dp
                            )
                            SurveyGlassButton(
                                text = "Connect",
                                onClick = { surveyManager.resolveRelocation(startNewSegment = false) },
                                variant = GlassButtonVariant.SKY,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                height = 32.dp
                            )
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 3. MAP UTILITY CONTROLS (Right side floating)
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Re-centre Location
            SurveyGlassIconButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Center Location",
                onClick = { isFollowingLocation = true },
                variant = if (isFollowingLocation) GlassButtonVariant.SKY else GlassButtonVariant.NEUTRAL,
                active = isFollowingLocation,
                size = 38.dp,
                iconSize = 18.dp
            )

            // Zoom In
            SurveyGlassIconButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = "Zoom In",
                onClick = { viewRangeMetres = (viewRangeMetres * 0.65).coerceAtLeast(80.0) },
                variant = GlassButtonVariant.NEUTRAL,
                size = 38.dp,
                iconSize = 18.dp
            )

            // Zoom Out
            SurveyGlassIconButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = "Zoom Out",
                onClick = { viewRangeMetres = (viewRangeMetres * 1.5).coerceAtMost(10000.0) },
                variant = GlassButtonVariant.NEUTRAL,
                size = 38.dp,
                iconSize = 18.dp
            )

            // Layer toggle (Centreline / Lane / Track)
            SurveyGlassIconButton(
                icon = Icons.Default.Layers,
                contentDescription = "Toggle Layers",
                onClick = {
                    showCentreline = !showCentreline
                    showLaneTrack = !showLaneTrack
                },
                variant = if (showCentreline) GlassButtonVariant.GOLD else GlassButtonVariant.NEUTRAL,
                active = showCentreline,
                size = 38.dp,
                iconSize = 18.dp
            )
        }

        // ----------------------------------------------------
        // 4. FLOATING HUD INFO CARD & BOTTOM CONTROLS
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Live Survey Stats HUD Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E).copy(alpha = 0.94f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Row 1: Live Chainage & Direction
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val chainageDisplay = activeSession?.displayCurrentChainage ?: "0+000"
                            Text(
                                text = chainageDisplay,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                            val dirLabel = activeSession?.direction?.label ?: "Increasing (+)"
                            val sideLabel = activeSession?.offsetSide?.name ?: "RHS"
                            Text(
                                text = "$sideLabel • $dirLabel",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Speed & Distance
                        Column(horizontalAlignment = Alignment.End) {
                            val speedKmh = (currentSpeedMps * 3.6f).coerceAtLeast(0f)
                            Text(
                                text = String.format(Locale.US, "%.1f km/h", speedKmh),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val distKm = ((activeSession?.totalDistanceTravelledMetres ?: 0.0) / 1000.0)
                            Text(
                                text = String.format(Locale.US, "Dist: %.2f km", distKm),
                                fontSize = 11.sp,
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 6.dp))

                    // Row 2: Lane, Offset, Save status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Lane: ${activeSession?.displayLaneName ?: "MCW Fast Lane"}",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CL Offset: ${activeSession?.centrelineOffsetMetres ?: 7.0}m",
                                fontSize = 10.5.sp,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Auto-Save Status
                        val saveText = when (saveStatus) {
                            is SaveStatus.Saving -> "Saving..."
                            is SaveStatus.Saved -> "Saved"
                            is SaveStatus.Failed -> "Save err"
                            else -> "Idle"
                        }
                        Text(
                            text = "● $saveText",
                            fontSize = 10.sp,
                            color = when (saveStatus) {
                                is SaveStatus.Saved -> Color(0xFF22C55E)
                                is SaveStatus.Failed -> Color(0xFFEF4444)
                                else -> Color.White.copy(alpha = 0.6f)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------
            // 5. ERGONOMIC SURVEY ACTION CONTROLS (Dynamic Minimalist Glass UI)
            // ----------------------------------------------------
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < 370.dp
                val status = activeSession?.status ?: SurveyStatus.NOT_STARTED
                val isRecordingOrPaused = status == SurveyStatus.RECORDING || status == SurveyStatus.PAUSED || status == SurveyStatus.READY_TO_RESUME

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Offset Adjust Button
                    SurveyGlassButton(
                        text = if (isCompact) "CL" else "Offset",
                        icon = Icons.Default.Tune,
                        onClick = { showChangeOffsetDialog = true },
                        variant = GlassButtonVariant.SKY,
                        modifier = Modifier.weight(if (isCompact) 0.85f else 0.95f),
                        height = 42.dp
                    )

                    // Primary Action Button (Start / Pause / Resume)
                    when (status) {
                        SurveyStatus.RECORDING -> {
                            SurveyGlassButton(
                                text = "Pause",
                                icon = Icons.Default.Pause,
                                onClick = {
                                    coroutineScope.launch {
                                        surveyManager.pauseSurvey()
                                        viewModel.showToast("Survey paused", ToastType.INFO)
                                    }
                                },
                                variant = GlassButtonVariant.GOLD,
                                modifier = Modifier.weight(1.15f),
                                height = 42.dp
                            )
                        }
                        SurveyStatus.PAUSED, SurveyStatus.READY_TO_RESUME -> {
                            SurveyGlassButton(
                                text = "Resume",
                                icon = Icons.Default.PlayArrow,
                                onClick = {
                                    surveyManager.requestResumeSurvey(currentGps)
                                    viewModel.showToast("Resuming survey...", ToastType.INFO)
                                },
                                variant = GlassButtonVariant.SUCCESS,
                                modifier = Modifier.weight(1.15f),
                                height = 42.dp
                            )
                        }
                        else -> {
                            SurveyGlassButton(
                                text = if (isCompact) "Start" else "Start Survey",
                                icon = Icons.Default.PlayArrow,
                                onClick = { showNewConfigDialog = true },
                                variant = GlassButtonVariant.GOLD,
                                modifier = Modifier.weight(1.25f),
                                height = 42.dp
                            )
                        }
                    }

                    // Prominent + KM Stone Marker Button
                    SurveyGlassButton(
                        text = if (isCompact) "+ KM" else "+ KM Stone",
                        icon = Icons.Default.AddLocationAlt,
                        onClick = {
                            if (activeSession != null) {
                                showAddKmStoneDialog = true
                            } else {
                                viewModel.showToast("Start a survey before marking KM stones", ToastType.WARNING)
                            }
                        },
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.weight(if (isCompact) 1.05f else 1.25f),
                        height = 42.dp
                    )

                    // Stop / Review Button
                    if (isRecordingOrPaused) {
                        SurveyGlassButton(
                            text = "Stop",
                            icon = Icons.Default.Stop,
                            onClick = {
                                coroutineScope.launch {
                                    val finalized = surveyManager.stopSurvey()
                                    if (finalized != null) {
                                        reviewSessionTarget = finalized
                                        viewModel.showToast("Survey finalized!", ToastType.SUCCESS)
                                    }
                                }
                            },
                            variant = GlassButtonVariant.DESTRUCTIVE,
                            modifier = Modifier.weight(0.85f),
                            height = 42.dp
                        )
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // DIALOGS
    // ----------------------------------------------------
    if (showNewConfigDialog) {
        NewSurveyConfigDialog(
            initialProjectName = viewModel.projectProfile.value.highwayId,
            initialHighwayName = viewModel.projectProfile.value.highwayId.ifBlank { "NH-44" },
            currentGpsLocation = currentGps,
            onDismiss = { showNewConfigDialog = false },
            onStartSurvey = { session ->
                showNewConfigDialog = false
                coroutineScope.launch {
                    surveyManager.startNewSurvey(session)
                    viewModel.showToast("Live survey recording started!", ToastType.SUCCESS)
                }
            }
        )
    }

    if (showAddKmStoneDialog && activeSession != null) {
        AddKmStoneDialog(
            currentCalculatedChainageMetres = activeSession!!.currentChainageMetres,
            currentLocation = currentGps ?: activePoints.lastOrNull()?.geoPoint,
            currentAccuracy = currentAccuracy,
            selectedLaneName = activeSession!!.displayLaneName,
            onDismiss = { showAddKmStoneDialog = false },
            onConfirmMarker = { type, confirmedMetres, notes ->
                showAddKmStoneDialog = false
                coroutineScope.launch {
                    val pt = currentGps ?: activePoints.lastOrNull()?.geoPoint ?: GeoPoint(0.0, 0.0)
                    surveyManager.addKmStoneMarker(
                        point = pt,
                        accuracy = currentAccuracy,
                        markerType = type,
                        confirmedChainageMetres = confirmedMetres,
                        notes = notes
                    )
                    viewModel.showToast("KM Stone ${Chainage(confirmedMetres).format()} marked!", ToastType.SUCCESS)
                }
            }
        )
    }

    if (showChangeOffsetDialog && activeSession != null) {
        ChangeOffsetDialog(
            currentOffsetM = activeSession!!.centrelineOffsetMetres,
            currentSide = activeSession!!.offsetSide,
            defaultLaneOffsetM = activeSession!!.lane.defaultOffsetMetres,
            isAutoMode = activeSession!!.isOffsetAuto,
            onDismiss = { showChangeOffsetDialog = false },
            onSaveOffset = { newOffset, newSide, isAuto ->
                showChangeOffsetDialog = false
                coroutineScope.launch {
                    surveyManager.updateCentrelineOffset(newOffset, newSide, isAuto)
                    viewModel.showToast("Centreline offset updated: ${newOffset}m ${newSide.name}", ToastType.SUCCESS)
                }
            }
        )
    }

    if (showSavedSurveysDialog) {
        SavedSurveysDialog(
            sessions = allSessions,
            onSelectSession = { s ->
                showSavedSurveysDialog = false
                if (s.status == SurveyStatus.COMPLETED) {
                    reviewSessionTarget = s
                } else {
                    surveyManager.resumeExistingSession(s)
                    viewModel.showToast("Resumed ${s.highwayName}", ToastType.INFO)
                }
            },
            onDeleteSession = { id ->
                coroutineScope.launch {
                    surveyManager.repository.deleteSession(id)
                    viewModel.showToast("Survey deleted", ToastType.INFO)
                }
            },
            onDismiss = { showSavedSurveysDialog = false }
        )
    }

    if (relocationPrompt != null) {
        RelocationDialog(
            relocation = relocationPrompt!!,
            onConfirmNewSegment = { surveyManager.resolveRelocation(startNewSegment = true) },
            onConfirmConnect = { surveyManager.resolveRelocation(startNewSegment = false) },
            onDismiss = { surveyManager.dismissRelocation() }
        )
    }
}
