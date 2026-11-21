package com.example.ui.survey

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.OsmTileManager
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.model.MiniMapOrientationMode
import com.example.survey.data.SaveStatus
import com.example.survey.engine.SurveyGeometryEngine
import com.example.survey.model.SurveySession
import com.example.survey.model.SurveyStatus
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import com.example.ui.map.MiniMapHeadingUnavailableBadge
import com.example.ui.map.MiniMapOfflineCoverageBadge
import com.example.ui.map.MiniMapViewportMath
import com.example.ui.map.miniMapNavigationGestures
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.smartMapEdgeFade
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
    val mapHeadingState by viewModel.mapHeadingState.collectAsStateWithLifecycle()
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
    val currentBearing = mapHeadingState.headingDegrees ?: locationState.bearing ?: 0f

    // UI Dialog & Review States
    var showNewConfigDialog by remember { mutableStateOf(false) }
    var showAddKmStoneDialog by remember { mutableStateOf(false) }
    var showChangeOffsetDialog by remember { mutableStateOf(false) }
    var showQuickSettingsDialog by remember { mutableStateOf(false) }
    var showSavedSurveysDialog by remember { mutableStateOf(false) }
    var reviewSessionTarget by remember { mutableStateOf<SurveySession?>(null) }

    // Layer Visibility (persisted across screen exits & app restarts)
    val showGpsTrack by viewModel.surveyShowGpsTrack.collectAsStateWithLifecycle()
    val showCentreline by viewModel.surveyShowCentreline.collectAsStateWithLifecycle()
    val showLaneTrack by viewModel.surveyShowLaneTrack.collectAsStateWithLifecycle()
    val showMarkers by viewModel.surveyShowMarkers.collectAsStateWithLifecycle()
    val show200mLabels by viewModel.surveyShow200mLabels.collectAsStateWithLifecycle()
    val mapType by viewModel.surveyMapType.collectAsStateWithLifecycle()
    val miniMapViewDistance by viewModel.miniMapViewDistancePreference.collectAsStateWithLifecycle()
    val smartDirectionState by viewModel.smartDirectionState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()

    var previousSmartRangeM by remember(miniMapViewDistance.mode) { mutableStateOf<Double?>(null) }
    val resolvedGroundRangeMetres = remember(
        miniMapViewDistance,
        locationState.speed,
        locationState.accuracy,
        currentGps?.latitude,
        currentGps?.longitude,
        activeRoute?.id
    ) {
        com.example.ui.map.SmartAutoMiniMapDistanceEngine.resolveGroundRangeMetres(
            preference = miniMapViewDistance,
            context = com.example.ui.map.SmartAutoMiniMapDistanceEngine.SmartDistanceContext(
                speedMps = locationState.speed,
                gpsAccuracyMetres = locationState.accuracy,
                location = currentGps,
                routeData = activeRoute,
                previousRangeMetres = previousSmartRangeM
            )
        ).also { previousSmartRangeM = it }
    }

    // Interactive Map State (Full-Earth continuous pan & pinch zoom, Always Direction-Up, configured/Smart Auto range)
    var exploredCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var continuousZoom by remember {
        mutableFloatStateOf(
            MiniMapViewportMath.computeZoomForGroundRangeMetres(
                latitude = (currentGps ?: activePoints.lastOrNull()?.geoPoint)?.latitude ?: 28.6139,
                viewportWidthPx = 360f,
                viewportHeightPx = 360f,
                groundRangeMetres = resolvedGroundRangeMetres
            )
        )
    }
    var hasUserAdjustedZoom by remember { mutableStateOf(false) }
    var lastAutoFitLat by remember {
        mutableStateOf((currentGps ?: activePoints.lastOrNull()?.geoPoint)?.latitude ?: 28.6139)
    }
    var isFollowingLocation by remember { mutableStateOf(true) }

    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()
    val isTileLoading by OsmTileManager.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(currentGps, isFollowingLocation) {
        if (isFollowingLocation) {
            exploredCenter = null
        }
    }

    // Smooth Direction-Up rotation driven by centralized MapHeadingManager (never auto-resets to North-Up)
    val activeHeadingDeg = mapHeadingState.headingDegrees ?: locationState.bearing
    val rawTargetMapRotation = remember(activeHeadingDeg) {
        MiniMapViewportMath.resolveDirectionUpRotation(
            mode = MiniMapOrientationMode.DIRECTION_UP,
            headingDegrees = activeHeadingDeg
        )
    }
    var unwrappedMapRotation by remember { mutableFloatStateOf(rawTargetMapRotation) }
    LaunchedEffect(rawTargetMapRotation) {
        unwrappedMapRotation = MiniMapViewportMath.unwrapShortestAngle(unwrappedMapRotation, rawTargetMapRotation)
    }
    val animatedMapRotation by animateFloatAsState(
        targetValue = unwrappedMapRotation,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "survey_map_direction_up_rotation"
    )
    val isDirectionUpActive = activeHeadingDeg != null

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

    val baseZoom = MiniMapViewportMath.splitContinuousZoom(continuousZoom).first

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

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val gpsOrRoutePoint = currentGps
            ?: activePoints.lastOrNull()?.geoPoint
            ?: GeoPoint(28.6139, 77.2090)

        val configuredSurveyZoom = remember(gpsOrRoutePoint.latitude, viewportWidthPx, viewportHeightPx, resolvedGroundRangeMetres) {
            MiniMapViewportMath.computeZoomForGroundRangeMetres(
                latitude = gpsOrRoutePoint.latitude,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                groundRangeMetres = resolvedGroundRangeMetres
            )
        }
        LaunchedEffect(miniMapViewDistance) {
            hasUserAdjustedZoom = false
            continuousZoom = configuredSurveyZoom
            lastAutoFitLat = gpsOrRoutePoint.latitude
        }
        LaunchedEffect(viewportWidthPx, viewportHeightPx, configuredSurveyZoom, hasUserAdjustedZoom) {
            if (!hasUserAdjustedZoom && viewportWidthPx > 1f && viewportHeightPx > 1f) {
                continuousZoom = configuredSurveyZoom
                lastAutoFitLat = gpsOrRoutePoint.latitude
            }
        }
        LaunchedEffect(gpsOrRoutePoint.latitude) {
            if (!hasUserAdjustedZoom && kotlin.math.abs(gpsOrRoutePoint.latitude - lastAutoFitLat) > 0.5) {
                continuousZoom = configuredSurveyZoom
                lastAutoFitLat = gpsOrRoutePoint.latitude
            }
        }

        val viewRangeMetres = remember(continuousZoom, gpsOrRoutePoint.latitude, viewportWidthPx, viewportHeightPx) {
            MiniMapViewportMath.computeGroundRangeMetres(
                continuousZoom = continuousZoom,
                latitude = gpsOrRoutePoint.latitude,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
        }

        val cameraCenter = if (isFollowingLocation) {
            MiniMapViewportMath.computeFollowCameraCenter(
                vehicleGeo = gpsOrRoutePoint,
                continuousZoom = continuousZoom,
                viewportHeightPx = viewportHeightPx,
                mapRotationDeg = animatedMapRotation,
                isDirectionUpActive = isDirectionUpActive
            )
        } else {
            exploredCenter ?: gpsOrRoutePoint
        }

        val renderPlan = remember(viewportWidthPx, viewportHeightPx, continuousZoom) {
            MiniMapViewportMath.resolveTileRenderPlan(
                widthPx = viewportWidthPx,
                heightPx = viewportHeightPx,
                continuousZoom = continuousZoom
            )
        }
        val tileResult = remember(
            cameraCenter,
            renderPlan,
            isDark,
            mapType,
            tileUpdate
        ) {
            OsmTileManager.renderMapBitmapWithCoverage(
                context = context,
                center = cameraCenter,
                widthPx = renderPlan.renderSizePx,
                heightPx = renderPlan.renderSizePx,
                zoom = renderPlan.renderBaseZoom,
                panOffsetX = 0f,
                panOffsetY = 0f,
                darkTheme = isDark,
                mapType = mapType
            )
        }

        // ----------------------------------------------------
        // 1. LIVE INTERACTIVE DIRECTION-UP OSM MAP CANVAS
        // ----------------------------------------------------
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .smartMapEdgeFade(
                    fadeToColor = colors.background,
                    edgeSize = 10.dp,
                    cornerRadius = 0.dp,
                    showSubtleOutline = false
                )
                .miniMapNavigationGestures(enabled = true) { centroid, pan, zoom ->
                    val startCenter = if (isFollowingLocation) cameraCenter else (exploredCenter ?: cameraCenter)
                    isFollowingLocation = false
                    if (kotlin.math.abs(zoom - 1f) > 0.002f) {
                        hasUserAdjustedZoom = true
                    }
                    val (nextCenter, nextZoom) = MiniMapViewportMath.applyFocalPinchAndPan(
                        currentCenter = startCenter,
                        currentZoom = continuousZoom,
                        focalX = centroid.x,
                        focalY = centroid.y,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        panX = pan.x,
                        panY = pan.y,
                        zoomChange = zoom,
                        mapRotationDeg = animatedMapRotation
                    )
                    exploredCenter = nextCenter
                    continuousZoom = nextZoom
                }
        ) {
            val _t = tileUpdate
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            val osmBitmap = tileResult.bitmap

            drawRect(color = colors.background)

            fun toUnrotated(pt: GeoPoint): Offset {
                return MiniMapViewportMath.geoToUnrotatedScreen(
                    pt = pt,
                    center = cameraCenter,
                    viewportWidthPx = w,
                    viewportHeightPx = h,
                    continuousZoom = continuousZoom
                )
            }

            fun toScreenUpright(pt: GeoPoint): Offset {
                return MiniMapViewportMath.unrotatedToScreenPoint(
                    unrotated = toUnrotated(pt),
                    viewportWidthPx = w,
                    viewportHeightPx = h,
                    mapRotationDeg = animatedMapRotation
                )
            }

            var centrelinePts: List<GeoPoint> = emptyList()
            if (activeSession != null && activePoints.size >= 2) {
                centrelinePts = SurveyGeometryEngine.generateCentrelineTrack(
                    points = activePoints,
                    offsetDistanceM = activeSession!!.centrelineOffsetMetres,
                    offsetSide = activeSession!!.offsetSide
                )
            }

            rotate(degrees = animatedMapRotation, pivot = Offset(cx, cy)) {
                if (osmBitmap != null) {
                    scale(scale = renderPlan.renderScale, pivot = Offset(cx, cy)) {
                        drawImage(
                            image = osmBitmap.asImageBitmap(),
                            dstOffset = IntOffset(
                                x = (cx - renderPlan.renderSizePx / 2f).toInt(),
                                y = (cy - renderPlan.renderSizePx / 2f).toInt()
                            ),
                            dstSize = IntSize(renderPlan.renderSizePx, renderPlan.renderSizePx)
                        )
                    }
                }

                // 1. Original Travelled GPS Track
                if (showGpsTrack && activePoints.size >= 2) {
                    val segments = activePoints.groupBy { it.segmentIndex }
                    segments.forEach { (_, segPoints) ->
                        if (segPoints.size >= 2) {
                            val path = Path()
                            val startOff = toUnrotated(segPoints[0].geoPoint)
                            path.moveTo(startOff.x, startOff.y)
                            for (i in 1 until segPoints.size) {
                                val off = toUnrotated(segPoints[i].geoPoint)
                                path.lineTo(off.x, off.y)
                            }
                            drawPath(
                                path = path,
                                color = colors.green,
                                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                // 2. Highway Centreline (Generated Gold Dashed Line)
                if (showCentreline && centrelinePts.size >= 2) {
                    val clPath = Path()
                    val startOff = toUnrotated(centrelinePts[0])
                    clPath.moveTo(startOff.x, startOff.y)
                    for (i in 1 until centrelinePts.size) {
                        val off = toUnrotated(centrelinePts[i])
                        clPath.lineTo(off.x, off.y)
                    }
                    drawPath(
                        path = clPath,
                        color = colors.gold,
                        style = Stroke(
                            width = 5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f)
                        )
                    )
                }

                // 3. Lane Alignment (Cyan Line)
                if (showLaneTrack && activePoints.size >= 2) {
                    val lanePath = Path()
                    val startOff = toUnrotated(activePoints[0].geoPoint)
                    lanePath.moveTo(startOff.x, startOff.y)
                    for (i in 1 until activePoints.size) {
                        val off = toUnrotated(activePoints[i].geoPoint)
                        lanePath.lineTo(off.x, off.y)
                    }
                    drawPath(
                        path = lanePath,
                        color = colors.sky,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 5. Live Vehicle Position Indicator (Accuracy Halo + Pulse Ring + Directional Navigation Arrow)
                if (currentGps != null) {
                    val vOff = toUnrotated(currentGps)
                    val accPx = (currentAccuracy / (viewRangeMetres / (w / 2f))).toFloat().coerceIn(16f, 150f)
                    drawCircle(
                        color = colors.sky.copy(alpha = 0.18f),
                        radius = accPx,
                        center = vOff
                    )
                    drawCircle(
                        color = colors.sky.copy(alpha = pulseAlpha),
                        radius = 20f,
                        center = vOff
                    )
                    if (activeHeadingDeg != null) {
                        rotate(degrees = activeHeadingDeg, pivot = vOff) {
                            val conePath = Path().apply {
                                moveTo(vOff.x, vOff.y - 22.dp.toPx())
                                lineTo(vOff.x - 9.dp.toPx(), vOff.y + 8.dp.toPx())
                                lineTo(vOff.x, vOff.y + 3.dp.toPx())
                                lineTo(vOff.x + 9.dp.toPx(), vOff.y + 8.dp.toPx())
                                close()
                            }
                            drawPath(path = conePath, color = colors.sky)
                        }
                    }
                    drawCircle(color = colors.sky, radius = 9f, center = vOff)
                    drawCircle(color = if (isDark) Color.White else Color(0xFF0F172A), radius = 4f, center = vOff)
                }
            }

            // 2b. Upright Centreline Chainage Labels Every 200 Metres (always horizontal even when rotated!)
            if (activeSession != null && show200mLabels && centrelinePts.size >= 2) {
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
                    val is1Km = lbl.chainageMetres % 1000L == 0L
                    val is400m = lbl.chainageMetres % 400L == 0L
                    val shouldRenderAtZoom = when {
                        baseZoom >= 16 -> true
                        baseZoom in 14..15 -> is400m || is1Km
                        else -> is1Km
                    }

                    if (shouldRenderAtZoom) {
                        val scr = toScreenUpright(lbl.position)
                        val lx = scr.x
                        val ly = scr.y
                        if (lx in -40f..(w + 40f) && ly in -40f..(h + 40f)) {
                            val distFromLast = hypot(lx - lastDrawnX, ly - lastDrawnY)
                            if (distFromLast >= 45f || is1Km) {
                                drawCircle(
                                    color = colors.gold,
                                    radius = if (is1Km) 4.5f else 3f,
                                    center = Offset(lx, ly)
                                )

                                val textStr = lbl.displayChainage
                                val textW = labelPaint.measureText(textStr)
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

            // 4. Upright KM Stone Markers (Pin Circles with Labels)
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
                    val scr = toScreenUpright(marker.geoPoint)
                    val mx = scr.x
                    val my = scr.y
                    if (mx in -50f..(w + 50f) && my in -50f..(h + 50f)) {
                        drawCircle(color = colors.red, radius = 14f, center = Offset(mx, my))
                        drawCircle(color = if (isDark) Color.White else Color(0xFFF8FAFC), radius = 5f, center = Offset(mx, my))

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
        }

        // Heading unavailable and offline coverage indicators cleanly positioned below toolbar
        val showHeadingBadge = mapHeadingState.isDirectionUnavailable && activeHeadingDeg == null && currentGps != null
        val showOfflineBadge = !isTileLoading && tileResult.hasMissingTiles
        if (showHeadingBadge || showOfflineBadge) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 66.dp)
            ) {
                if (showHeadingBadge) {
                    MiniMapHeadingUnavailableBadge(isDark = isDark)
                }
                if (showOfflineBadge) {
                    MiniMapOfflineCoverageBadge(
                        isDark = isDark,
                        hasPartialCoverage = tileResult.hasAnyCoverage
                    )
                }
            }
        }

        // ----------------------------------------------------
        // 2. TOP TOOLBAR (Material Design)
        // ----------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.headerBackground.copy(alpha = 0.94f),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
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
                            onClick = {
                                val nextType = if (mapType == "standard") "satellite" else "standard"
                                viewModel.updateSurveyMapPreferences(mapType = nextType)
                            },
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
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xEE171E2B) else Color(0xFFFFF7E6)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            // Re-centre Location (restores 25m viewing range & GPS follow)
            SurveyGlassIconButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Center Location",
                onClick = {
                    isFollowingLocation = true
                    exploredCenter = null
                    hasUserAdjustedZoom = false
                    continuousZoom = configuredSurveyZoom
                    lastAutoFitLat = gpsOrRoutePoint.latitude
                },
                variant = if (isFollowingLocation) GlassButtonVariant.SKY else GlassButtonVariant.NEUTRAL,
                active = isFollowingLocation,
                size = 38.dp,
                iconSize = 18.dp
            )

            // Zoom In
            SurveyGlassIconButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = "Zoom In",
                onClick = {
                    hasUserAdjustedZoom = true
                    continuousZoom = (continuousZoom + 0.75f).coerceAtMost(MiniMapViewportMath.MAX_ZOOM)
                },
                variant = GlassButtonVariant.NEUTRAL,
                size = 38.dp,
                iconSize = 18.dp
            )

            // Zoom Out
            SurveyGlassIconButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = "Zoom Out",
                onClick = {
                    hasUserAdjustedZoom = true
                    continuousZoom = (continuousZoom - 0.75f).coerceAtLeast(MiniMapViewportMath.MIN_ZOOM)
                },
                variant = GlassButtonVariant.NEUTRAL,
                size = 38.dp,
                iconSize = 18.dp
            )

            // Layer toggle (Centreline / 200m labels / Lane / Track)
            SurveyGlassIconButton(
                icon = Icons.Default.Layers,
                contentDescription = "Toggle 200m Labels & Centreline",
                onClick = {
                    viewModel.updateSurveyMapPreferences(
                        show200mLabels = !show200mLabels,
                        showCentreline = !showCentreline
                    )
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
        // ----------------------------------------------------
        // 4. FLOATING HUD INFO CARD & BOTTOM CONTROLS
        // Responsive Material UI Design with dynamic button arrangement
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.sky.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${activeSession?.displayRoadSide ?: smartDirectionState.confirmedBinarySide ?: "---"} • ${activeSession?.displayLaneName ?: "Fast Lane"}",
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

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 6.dp))

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
                                shape = RoundedCornerShape(8.dp),
                                color = if (remMetres <= 500L) colors.gold.copy(alpha = 0.16f) else colors.sky.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Remaining: ${String.format(Locale.US, "%.2f km", remMetres / 1000.0)}",
                                    fontSize = 10.5.sp,
                                    color = if (remMetres <= 500L) colors.gold else colors.sky,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
            // 5. ERGONOMIC SURVEY ACTION CONTROLS (Material Design Flat Buttons)
            // Dynamically arranged based on available width (wrapping on narrow displays)
            // ----------------------------------------------------
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 340.dp
                val isCompact = maxWidth < 400.dp
                val status = activeSession?.status ?: SurveyStatus.NOT_STARTED
                val isRecordingOrPaused = status == SurveyStatus.RECORDING || status == SurveyStatus.PAUSED || status == SurveyStatus.READY_TO_RESUME

                if (isNarrow) {
                    // Two-row wrapping on compact/split-screen viewports
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SurveyGlassButton(
                                text = "Settings",
                                icon = Icons.Default.Tune,
                                onClick = {
                                    if (activeSession != null) showQuickSettingsDialog = true else showNewConfigDialog = true
                                },
                                variant = GlassButtonVariant.SKY,
                                modifier = Modifier.weight(1f),
                                height = 44.dp
                            )
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
                                        modifier = Modifier.weight(1f),
                                        height = 44.dp
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
                                        modifier = Modifier.weight(1f),
                                        height = 44.dp
                                    )
                                }
                                else -> {
                                    SurveyGlassButton(
                                        text = "Start",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = { showNewConfigDialog = true },
                                        variant = GlassButtonVariant.GOLD,
                                        modifier = Modifier.weight(1f),
                                        height = 44.dp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SurveyGlassButton(
                                text = "+ KM Stone",
                                icon = Icons.Default.AddLocationAlt,
                                onClick = {
                                    if (activeSession != null) {
                                        showAddKmStoneDialog = true
                                    } else {
                                        viewModel.showToast("Start a survey before marking KM stones", ToastType.WARNING)
                                    }
                                },
                                variant = GlassButtonVariant.GOLD,
                                modifier = Modifier.weight(1.2f),
                                height = 44.dp
                            )

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
                                    modifier = Modifier.weight(0.8f),
                                    height = 44.dp
                                )
                            }
                        }
                    }
                } else {
                    // Standard responsive single-row layout
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
                            height = 44.dp
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
                                    height = 44.dp
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
                                    height = 44.dp
                                )
                            }
                            else -> {
                                SurveyGlassButton(
                                    text = if (isCompact) "Start" else "Start Survey",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { showNewConfigDialog = true },
                                    variant = GlassButtonVariant.GOLD,
                                    modifier = Modifier.weight(1.25f),
                                    height = 44.dp
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
                            height = 44.dp
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
                                height = 44.dp
                            )
                        }
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
