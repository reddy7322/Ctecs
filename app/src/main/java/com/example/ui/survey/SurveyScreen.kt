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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlin.math.hypot

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
    val checkpointPrompt by surveyManager.checkpointPrompt.collectAsStateWithLifecycle()
    val endingChainageReached by surveyManager.endingChainageReachedPrompt.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val allSessions by surveyManager.repository.allSessions.collectAsStateWithLifecycle(emptyList())

    // ----------------------------------------------------
    // SCREEN WAKE LOCK: Keep display awake during active survey recording/paused
    // ----------------------------------------------------
    val isSurveyActive = activeSession?.status == SurveyStatus.RECORDING || activeSession?.status == SurveyStatus.PAUSED
    com.example.util.KeepScreenOn(enabled = isSurveyActive, requesterId = "SurveyScreen")

    // Theme state
    val colors = rememberSurveyThemeColors()
    val isDark = colors.isDark
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    val currentGps = locationState.point
    val currentAccuracy = locationState.accuracy
    val currentSpeedMps = locationState.speed ?: 0f
    val currentBearing = locationState.bearing ?: 0f

    // UI Dialog & Review States
    var showNewConfigDialog by remember { mutableStateOf(false) }
    var showAddKmStoneDialog by remember { mutableStateOf(false) }
    var showChangeOffsetDialog by remember { mutableStateOf(false) }
    var showQuickSettingsDialog by remember { mutableStateOf(false) }
    var showSavedSurveysDialog by remember { mutableStateOf(false) }
    var reviewSessionTarget by remember { mutableStateOf<SurveySession?>(null) }

    // Layer Visibility
    var showGpsTrack by remember { mutableStateOf(true) }
    var showCentreline by remember { mutableStateOf(true) }
    var showLaneTrack by remember { mutableStateOf(true) }
    var showMarkers by remember { mutableStateOf(true) }
    var show200mLabels by remember { mutableStateOf(true) }
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

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
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

            // Render OSM Street or Satellite tile background with active theme
            val osmBitmap = OsmTileManager.renderMapBitmap(
                context = context,
                center = anchorPoint,
                widthPx = w.toInt().coerceAtLeast(1),
                heightPx = h.toInt().coerceAtLeast(1),
                zoom = currentZoom,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                darkTheme = isDark,
                mapType = mapType
            )

            if (osmBitmap != null) {
                drawImage(image = osmBitmap.asImageBitmap())
            } else {
                drawRect(color = colors.background)
            }

            // 1. Original Travelled GPS Track
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
                            color = colors.green,
                            style = Stroke(width = 6f)
                        )
                    }
                }
            }

            // 2. Highway Centreline (Generated Gold Dashed Line) & 200m Chainage Labels
            var centrelinePts: List<GeoPoint> = emptyList()
            if (activeSession != null && activePoints.size >= 2) {
                centrelinePts = SurveyGeometryEngine.generateCentrelineTrack(
                    points = activePoints,
                    offsetDistanceM = activeSession!!.centrelineOffsetMetres,
                    offsetSide = activeSession!!.offsetSide
                )
                if (showCentreline && centrelinePts.size >= 2) {
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
                        color = colors.gold,
                        style = Stroke(
                            width = 5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f)
                        )
                    )
                }

                // 2b. Centreline Chainage Labels Every 200 Metres
                if (show200mLabels && centrelinePts.size >= 2) {
                    val labels200m = SurveyGeometryEngine.generate200mChainageLabels(
                        centrelinePts = centrelinePts,
                        startChainageMetres = activeSession!!.startChainageMetres,
                        direction = activeSession!!.direction
                    )

                    val labelPaint = android.graphics.Paint().apply {
                        color = if (isDark) android.graphics.Color.parseColor("#FFD700") else android.graphics.Color.parseColor("#92400E")
                        textSize = 22f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val bgLabelPaint = android.graphics.Paint().apply {
                        color = if (isDark) android.graphics.Color.parseColor("#DD0F172A") else android.graphics.Color.parseColor("#EEFEF3C7")
                        style = android.graphics.Paint.Style.FILL
                    }
                    val strokeLabelPaint = android.graphics.Paint().apply {
                        color = if (isDark) android.graphics.Color.parseColor("#66FFD700") else android.graphics.Color.parseColor("#99D97706")
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }

                    var lastDrawnX = -9999f
                    var lastDrawnY = -9999f

                    labels200m.forEach { lbl ->
                        // Zoom adaptive visibility:
                        // Zoom >= 16: show all 200m labels
                        // Zoom 14..15: show 400m / 1000m labels
                        // Zoom < 14: show only round 1000m (1 KM) labels
                        val is1Km = lbl.chainageMetres % 1000L == 0L
                        val is400m = lbl.chainageMetres % 400L == 0L
                        val shouldRenderAtZoom = when {
                            currentZoom >= 16 -> true
                            currentZoom in 14..15 -> is400m || is1Km
                            else -> is1Km
                        }

                        if (shouldRenderAtZoom) {
                            val (lx, ly) = OsmTileManager.geoToScreen(
                                lbl.position, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                            )
                            if (lx in -40f..(w + 40f) && ly in -40f..(h + 40f)) {
                                // Prevent screen overlap clutter: ensure at least 45 screen pixels distance
                                val distFromLast = hypot(lx - lastDrawnX, ly - lastDrawnY)
                                if (distFromLast >= 45f || is1Km) {
                                    // Centreline tick marker dot
                                    drawCircle(
                                        color = colors.gold,
                                        radius = if (is1Km) 4.5f else 3f,
                                        center = Offset(lx, ly)
                                    )

                                    val textStr = lbl.displayChainage
                                    val textW = labelPaint.measureText(textStr)

                                    // Draw pill background + chainage label
                                    drawContext.canvas.nativeCanvas.drawRoundRect(
                                        lx - textW / 2f - 7f,
                                        ly - 28f,
                                        lx + textW / 2f + 7f,
                                        ly - 4f,
                                        6f,
                                        6f,
                                        bgLabelPaint
                                    )
                                    drawContext.canvas.nativeCanvas.drawRoundRect(
                                        lx - textW / 2f - 7f,
                                        ly - 28f,
                                        lx + textW / 2f + 7f,
                                        ly - 4f,
                                        6f,
                                        6f,
                                        strokeLabelPaint
                                    )
                                    drawContext.canvas.nativeCanvas.drawText(textStr, lx, ly - 10f, labelPaint)

                                    lastDrawnX = lx
                                    lastDrawnY = ly
                                }
                            }
                        }
                    }
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
                    color = colors.sky,
                    style = Stroke(width = 3.5f)
                )
            }

            // 4. KM Stone Markers (Pin Circles with Labels)
            if (showMarkers) {
                val textPaint = android.graphics.Paint().apply {
                    color = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#0F172A")
                    textSize = 28f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val bgPaint = android.graphics.Paint().apply {
                    color = if (isDark) android.graphics.Color.parseColor("#CC0F172A") else android.graphics.Color.parseColor("#E6FFFFFF")
                    style = android.graphics.Paint.Style.FILL
                }

                activeMarkers.forEach { marker ->
                    val (mx, my) = OsmTileManager.geoToScreen(
                        marker.geoPoint, anchorPoint, w, h, currentZoom, panOffsetX, panOffsetY
                    )
                    if (mx in -50f..(w + 50f) && my in -50f..(h + 50f)) {
                        // Marker circle pin
                        drawCircle(color = colors.red, radius = 14f, center = Offset(mx, my))
                        drawCircle(color = if (isDark) Color.White else Color(0xFFF8FAFC), radius = 5f, center = Offset(mx, my))

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
                    color = colors.sky.copy(alpha = 0.18f),
                    radius = accPx,
                    center = Offset(vx, vy)
                )
                // Vehicle pulse ring
                drawCircle(
                    color = colors.sky.copy(alpha = pulseAlpha),
                    radius = 20f,
                    center = Offset(vx, vy)
                )
                // Vehicle center core
                drawCircle(color = colors.sky, radius = 9f, center = Offset(vx, vy))
                drawCircle(color = if (isDark) Color.White else Color(0xFF0F172A), radius = 4f, center = Offset(vx, vy))
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
                color = colors.headerBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
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
                            Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val status = activeSession?.status ?: SurveyStatus.NOT_STARTED
                                val statusColor = when (status) {
                                    SurveyStatus.RECORDING -> colors.green
                                    SurveyStatus.PAUSED -> colors.gold
                                    SurveyStatus.READY_TO_RESUME -> colors.sky
                                    SurveyStatus.COMPLETED -> Color(0xFFA855F7)
                                    else -> colors.textMuted
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
                                Text("• ±${currentAccuracy.toInt()}m", color = colors.textSecondary, fontSize = 10.sp)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Quick Settings Button (Interval, Road Side, Lane, Ending Chainage)
                        if (activeSession != null) {
                            SurveyGlassIconButton(
                                icon = Icons.Default.Tune,
                                contentDescription = "Survey Settings",
                                onClick = { showQuickSettingsDialog = true },
                                variant = GlassButtonVariant.SKY,
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                        }

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

                        // Theme Mode Switcher Button (Light / Dark / System)
                        SurveyGlassIconButton(
                            icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme: $themeMode",
                            onClick = { showThemeDialog = true },
                            variant = GlassButtonVariant.GOLD,
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
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF78350F).copy(alpha = 0.95f) else Color(0xFFFEF3C7)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFFFFD700) else Color(0xFFD97706)
                    ),
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
                            Text(
                                "Relocation: ${String.format(Locale.US, "%.0f m", relocationPrompt!!.distanceM)} from last point",
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFFD700) else Color(0xFF92400E),
                                fontSize = 12.sp
                            )
                            Text(
                                "Resume as new segment to avoid false straight-line jump?",
                                fontSize = 10.sp,
                                color = if (isDark) Color.White else Color(0xFF78350F)
                            )
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

            // Layer toggle (Centreline / 200m labels / Lane / Track)
            SurveyGlassIconButton(
                icon = Icons.Default.Layers,
                contentDescription = "Toggle 200m Labels & Centreline",
                onClick = {
                    show200mLabels = !show200mLabels
                    showCentreline = !showCentreline
                },
                variant = if (show200mLabels) GlassButtonVariant.GOLD else GlassButtonVariant.NEUTRAL,
                active = show200mLabels,
                size = 38.dp,
                iconSize = 18.dp
            )
        }

        // ----------------------------------------------------
        // 4. FLOATING HUD INFO CARD & BOTTOM CONTROLS
        // Streamlined & prioritized per user requirement #8:
        // 1. Current Chainage - large and prominent
        // 2. Starting & Ending Chainage
        // 3. Remaining distance (when ending chainage configured)
        // 4. Road Side - LHS/RHS/BHS
        // 5. Lane - Inner/Outer/Fast/Slow
        // 6. GPS accuracy & status
        // 7. Total surveyed distance
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Row 1: Large Prominent Current Chainage + Speed & Distance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val chainageDisplay = activeSession?.displayCurrentChainage ?: "190+000"
                            Text(
                                text = chainageDisplay,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.gold,
                                letterSpacing = 0.5.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.sky.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${activeSession?.displayRoadSide ?: "RHS"} • ${activeSession?.displayLaneName ?: "Fast Lane"}",
                                        fontSize = 10.5.sp,
                                        color = colors.sky,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                val dirText = activeSession?.direction?.label?.substringBefore(" ") ?: "Increasing"
                                Text(
                                    text = dirText,
                                    fontSize = 10.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // Speed & Surveyed Distance
                        Column(horizontalAlignment = Alignment.End) {
                            val speedKmh = (currentSpeedMps * 3.6f).coerceAtLeast(0f)
                            Text(
                                text = String.format(Locale.US, "%.1f km/h", speedKmh),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            val distKm = ((activeSession?.totalDistanceTravelledMetres ?: 0.0) / 1000.0)
                            Text(
                                text = String.format(Locale.US, "%.2f km surveyed", distKm),
                                fontSize = 11.sp,
                                color = colors.green,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 6.dp))

                    // Row 2: Starting Chainage, Ending Chainage & Remaining Distance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Start: ${activeSession?.displayStartChainage ?: "0+000"}",
                                fontSize = 10.5.sp,
                                color = colors.textSecondary
                            )

                            if (activeSession?.hasPlannedEndChainage == true) {
                                Text(
                                    text = "→ End: ${activeSession!!.displayPlannedEndChainage}",
                                    fontSize = 10.5.sp,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Remaining distance badge (when planned ending chainage configured)
                        val remMetres = activeSession?.remainingDistanceMetres
                        if (remMetres != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (remMetres <= 500L) colors.gold.copy(alpha = 0.2f) else colors.sky.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, if (remMetres <= 500L) colors.gold else colors.sky)
                            ) {
                                Text(
                                    text = "Remaining: ${String.format(Locale.US, "%.2f km", remMetres / 1000.0)}",
                                    fontSize = 10.5.sp,
                                    color = if (remMetres <= 500L) colors.gold else colors.sky,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            val intervalText = activeSession?.confirmationIntervalMetres?.let { "Auto-check: ${it / 1000}km" } ?: "Interval: Off"
                            Text(
                                text = intervalText,
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }
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
                    // Settings / Offset Adjust Button
                    SurveyGlassButton(
                        text = if (isCompact) "Settings" else "Survey Settings",
                        icon = Icons.Default.Tune,
                        onClick = {
                            if (activeSession != null) {
                                showQuickSettingsDialog = true
                            } else {
                                showNewConfigDialog = true
                            }
                        },
                        variant = GlassButtonVariant.SKY,
                        modifier = Modifier.weight(if (isCompact) 0.95f else 1.05f),
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
    // DIALOGS & NOTIFICATION FLOWS
    // ----------------------------------------------------

    // 1. New Survey Configuration Dialog
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

    // 2. Smart Chainage Confirmation Popup (10s auto-confirm, strictly confirm / edit chainage)
    if (checkpointPrompt != null) {
        val prompt = checkpointPrompt!!
        SmartChainageConfirmationDialog(
            prompt = prompt,
            onConfirm = {
                coroutineScope.launch {
                    surveyManager.confirmCheckpoint(prompt)
                    viewModel.showToast("Checkpoint confirmed: ${Chainage(prompt.checkpointChainageMetres).format()}", ToastType.SUCCESS)
                }
            },
            onConfirmWithCorrection = { correctedCh ->
                coroutineScope.launch {
                    surveyManager.confirmCheckpoint(prompt, correctedCh)
                    viewModel.showToast("Chainage calibrated to ${Chainage(correctedCh).format()} (GPS location untouched)", ToastType.SUCCESS)
                }
            }
        )
    }

    // 3. Planned Ending Chainage Reached Dialog (Never stops automatically)
    if (endingChainageReached && activeSession != null) {
        EndingChainageReachedDialog(
            session = activeSession!!,
            onFinishSurvey = {
                coroutineScope.launch {
                    surveyManager.dismissEndingChainagePrompt()
                    val finalized = surveyManager.stopSurvey()
                    if (finalized != null) {
                        reviewSessionTarget = finalized
                        viewModel.showToast("Survey finished!", ToastType.SUCCESS)
                    }
                }
            },
            onContinueSurvey = {
                surveyManager.dismissEndingChainagePrompt()
                viewModel.showToast("Continuing survey beyond planned ending chainage", ToastType.INFO)
            }
        )
    }

    // 4. Quick Survey Settings Dialog (on-the-fly updates without stopping)
    if (showQuickSettingsDialog && activeSession != null) {
        SurveyQuickSettingsDialog(
            session = activeSession!!,
            onUpdateRoadSide = { side ->
                coroutineScope.launch {
                    surveyManager.updateRoadSide(side)
                    viewModel.showToast("Road Side updated to ${side.label}", ToastType.SUCCESS)
                }
            },
            onUpdateLane = { lane, customName ->
                coroutineScope.launch {
                    surveyManager.updateLane(lane, customName)
                    viewModel.showToast("Lane updated to ${lane.label}", ToastType.SUCCESS)
                }
            },
            onUpdateInterval = { intervalM ->
                coroutineScope.launch {
                    surveyManager.updateConfirmationInterval(intervalM)
                    val text = intervalM?.let { "Every ${it / 1000} KM" } ?: "Off"
                    viewModel.showToast("Confirmation Interval: $text", ToastType.SUCCESS)
                }
            },
            onUpdatePlannedEnd = { plannedEndM ->
                coroutineScope.launch {
                    surveyManager.updatePlannedEndChainage(plannedEndM)
                }
            },
            onDismiss = { showQuickSettingsDialog = false }
        )
    }

    // 5. Add KM Stone Dialog
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

    // 6. Change Offset Dialog
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

    // 7. Saved Surveys Dialog
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

    // 8. Relocation Dialog
    if (relocationPrompt != null) {
        RelocationDialog(
            relocation = relocationPrompt!!,
            onConfirmNewSegment = { surveyManager.resolveRelocation(startNewSegment = true) },
            onConfirmConnect = { surveyManager.resolveRelocation(startNewSegment = false) },
            onDismiss = { surveyManager.dismissRelocation() }
        )
    }

    // 9. Theme Selector Dialog
    if (showThemeDialog) {
        SurveyThemeSelectorDialog(
            currentTheme = themeMode,
            onSelectTheme = { selectedMode ->
                viewModel.setThemeMode(selectedMode)
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}
