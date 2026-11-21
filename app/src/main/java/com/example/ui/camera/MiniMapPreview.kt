package com.example.ui.camera

import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.MapHeadingState
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyleConfig
import com.example.model.RouteData
import com.example.overlay.MiniMapRenderer
import com.example.ui.map.MiniMapHeadingUnavailableBadge
import com.example.ui.map.MiniMapOfflineCoverageBadge
import com.example.ui.map.MiniMapViewportMath
import com.example.ui.map.miniMapNavigationGestures
import com.example.ui.map.rememberResolvedMapColors
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.smartMapEdgeFade
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic Mini Map composable used in Camera Screen, Settings Preview, Routes Screen,
 * and Photo Info embedded map previews.
 *
 * - Always Direction-Up by default (never auto-resets to North-Up).
 * - Dynamically fits full available container dimensions with proper aspect ratio & zero clipping.
 * - Supports Google Maps-style focal-point pinch zoom, rotation-aware panning, and compact
 *   `+`, `−`, and Recenter controls when [interactive] is enabled.
 */
@Composable
fun MiniMapPreview(
    location: GeoPoint?,
    bearing: Float?,
    routeData: RouteData?,
    highwayTag: String,
    gpsAccuracy: Float = 0f,
    showGpsBadge: Boolean = false,
    showProjectTag: Boolean = false,
    mapThemePreference: String = "system",
    mapTypePreference: String = "standard",
    miniMapConfig: MiniMapStyleConfig? = null,
    chainageLabel: String = "",
    sideLabel: String = "",
    isGpsRetained: Boolean = false,
    mapHeadingState: MapHeadingState? = null,
    interactive: Boolean = false,
    autoFitRoute: Boolean = false,
    orientationMode: MiniMapOrientationMode = MiniMapOrientationMode.DIRECTION_UP,
    fadeToColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()

    // Unified configurable path shared by Camera Preview, Photo Capture, Video Recording, and Settings
    if (miniMapConfig != null) {
        val cornerRadiusDp = miniMapConfig.resolveCornerRadiusDp().dp
        val effectiveHeading = mapHeadingState?.headingDegrees ?: bearing
        val resolvedBearing = remember(location, effectiveHeading, routeData?.id) {
            MiniMapRenderer.resolveEffectiveBearing(
                center = location ?: routeData?.bounds?.center,
                gpsBearing = effectiveHeading,
                routeData = routeData
            )
        }
        val showHeadingUnavailable = mapHeadingState?.isDirectionUnavailable == true &&
            resolvedBearing == null &&
            location != null &&
            miniMapConfig.orientationMode != MiniMapOrientationMode.NORTH_UP

        BoxWithConstraints(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadiusDp))
                .clipToBounds()
        ) {
            val showCompactUnavailable = showHeadingUnavailable && maxHeight >= 96.dp && maxWidth >= 110.dp

            Canvas(modifier = Modifier.fillMaxSize()) {
                val _tick = tileUpdate
                val bounds = RectF(0f, 0f, size.width, size.height)
                drawIntoCanvas { canvas ->
                    MiniMapRenderer.drawMiniMap(
                        canvas = canvas.nativeCanvas,
                        bounds = bounds,
                        context = context,
                        config = miniMapConfig,
                        location = location,
                        routeData = routeData,
                        bearing = effectiveHeading,
                        chainageLabel = chainageLabel,
                        sideLabel = sideLabel,
                        isGpsRetained = isGpsRetained,
                        dpScale = density.density
                    )
                }
            }

            if (showCompactUnavailable) {
                MiniMapHeadingUnavailableBadge(
                    isDark = miniMapConfig.mapStyle == com.example.model.MiniMapStyle.DARK ||
                        miniMapConfig.mapStyle == com.example.model.MiniMapStyle.SATELLITE,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                )
            }
        }
        return
    }

    // Embedded / Routes / Photos dynamic interactive map path
    val mapTheme = rememberResolvedMapColors(mapThemePreference)
    val resolvedFadeColor = fadeToColor ?: CtecsTheme.colors.surface
    val isTileLoading by OsmTileManager.isLoading.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(mapTheme.mapBackground)
            .smartMapEdgeFade(
                fadeToColor = resolvedFadeColor,
                edgeSize = 11.dp,
                cornerRadius = 14.dp
            )
            .clipToBounds()
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val baseCenter = location ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)
        val defaultFit = remember(routeData?.id, baseCenter.latitude, autoFitRoute, viewportWidthPx, viewportHeightPx) {
            if (autoFitRoute && routeData != null && routeData.alignments.isNotEmpty()) {
                MiniMapViewportMath.computeRouteFitCamera(
                    routeData = routeData,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    paddingFraction = 0.14f
                )
            } else {
                val zoom25m = MiniMapViewportMath.computeZoomForGroundRangeMetres(
                    latitude = baseCenter.latitude,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    groundRangeMetres = MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
                )
                Pair(baseCenter, zoom25m)
            }
        }

        var isFollowingDefault by remember(routeData?.id, autoFitRoute) { mutableStateOf(true) }
        var exploredCenter by remember(routeData?.id, autoFitRoute) { mutableStateOf<GeoPoint?>(null) }
        var hasUserAdjustedZoom by remember(routeData?.id, autoFitRoute) { mutableStateOf(false) }
        var lastAutoFitLat by remember(routeData?.id, autoFitRoute) { mutableStateOf(baseCenter.latitude) }
        var continuousZoom by remember(routeData?.id, autoFitRoute) { mutableFloatStateOf(defaultFit.second) }

        LaunchedEffect(viewportWidthPx, viewportHeightPx, hasUserAdjustedZoom, isFollowingDefault) {
            if (isFollowingDefault && !hasUserAdjustedZoom && viewportWidthPx > 1f && viewportHeightPx > 1f) {
                continuousZoom = defaultFit.second
                exploredCenter = null
                lastAutoFitLat = baseCenter.latitude
            }
        }
        LaunchedEffect(baseCenter.latitude) {
            if (isFollowingDefault && !hasUserAdjustedZoom && kotlin.math.abs(baseCenter.latitude - lastAutoFitLat) > 0.5) {
                continuousZoom = defaultFit.second
                lastAutoFitLat = baseCenter.latitude
            }
        }

        // Direction-Up orientation (never auto-resets to North-Up)
        val anchorForBearing = location ?: routeData?.bounds?.center
        val effectiveBearing = remember(anchorForBearing, mapHeadingState?.headingDegrees, bearing, routeData?.id) {
            mapHeadingState?.headingDegrees
                ?: MiniMapRenderer.resolveEffectiveBearing(anchorForBearing, bearing, routeData)
        }
        val effectiveOrientation = if (autoFitRoute && location == null) {
            // When previewing a static route corridor with no live GPS, align along corridor bearing if present
            orientationMode
        } else {
            orientationMode
        }
        val targetRotation = remember(effectiveOrientation, effectiveBearing) {
            MiniMapViewportMath.resolveDirectionUpRotation(
                mode = effectiveOrientation,
                headingDegrees = effectiveBearing,
                fallbackBearing = bearing
            )
        }
        var unwrappedRotation by remember { mutableFloatStateOf(targetRotation) }
        LaunchedEffect(targetRotation) {
            unwrappedRotation = MiniMapViewportMath.unwrapShortestAngle(unwrappedRotation, targetRotation)
        }
        val animatedRotation by animateFloatAsState(
            targetValue = unwrappedRotation,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "preview_minimap_rotation"
        )

        val isDirectionUpActive = effectiveOrientation != MiniMapOrientationMode.NORTH_UP && effectiveBearing != null

        val cameraCenter = remember(
            isFollowingDefault,
            exploredCenter,
            location,
            baseCenter,
            defaultFit.first,
            continuousZoom,
            viewportHeightPx,
            animatedRotation,
            isDirectionUpActive,
            autoFitRoute
        ) {
            if (!isFollowingDefault && exploredCenter != null) {
                exploredCenter!!
            } else if (!autoFitRoute && location != null && isDirectionUpActive) {
                MiniMapViewportMath.computeFollowCameraCenter(
                    vehicleGeo = location,
                    continuousZoom = continuousZoom,
                    viewportHeightPx = viewportHeightPx,
                    mapRotationDeg = animatedRotation,
                    isDirectionUpActive = true
                )
            } else if (!autoFitRoute) {
                baseCenter
            } else {
                defaultFit.first
            }
        }

        val showHeadingUnavailable = mapHeadingState?.isDirectionUnavailable == true &&
            effectiveBearing == null &&
            location != null &&
            effectiveOrientation != MiniMapOrientationMode.NORTH_UP

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
            mapTheme.isDark,
            mapTypePreference,
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
                darkTheme = mapTheme.isDark,
                mapType = mapTypePreference
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .miniMapNavigationGestures(enabled = interactive) { centroid, pan, zoom ->
                    val startCenter = if (isFollowingDefault) cameraCenter else (exploredCenter ?: cameraCenter)
                    isFollowingDefault = false
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
                        mapRotationDeg = animatedRotation
                    )
                    exploredCenter = nextCenter
                    continuousZoom = nextZoom
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val _t = tileUpdate
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                val osmBitmap = tileResult.bitmap

                drawRect(color = mapTheme.mapBackground)

                rotate(degrees = animatedRotation, pivot = Offset(cx, cy)) {
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
                    } else {
                        val gridStep = 36.dp.toPx()
                        val extent = maxOf(w, h)
                        var g = -extent
                        while (g <= extent * 1.5f) {
                            drawLine(
                                color = mapTheme.gridLineColor,
                                start = Offset(cx + g, cy - extent),
                                end = Offset(cx + g, cy + extent),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = mapTheme.gridLineColor,
                                start = Offset(cx - extent, cy + g),
                                end = Offset(cx + extent, cy + g),
                                strokeWidth = 1f
                            )
                            g += gridStep
                        }
                    }

                    fun toScreen(pt: GeoPoint): Offset {
                        return MiniMapViewportMath.geoToUnrotatedScreen(
                            pt = pt,
                            center = cameraCenter,
                            viewportWidthPx = w,
                            viewportHeightPx = h,
                            continuousZoom = continuousZoom
                        )
                    }

                    // Route alignments
                    if (routeData != null && routeData.alignments.isNotEmpty()) {
                        for (alignment in routeData.alignments) {
                            if (alignment.points.size < 2) continue
                            val path = Path()
                            var started = false
                            for (pt in alignment.points) {
                                val off = toScreen(pt)
                                if (!started) {
                                    path.moveTo(off.x, off.y)
                                    started = true
                                } else {
                                    path.lineTo(off.x, off.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = mapTheme.routeCasingColor.copy(alpha = 0.85f),
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = path,
                                color = mapTheme.routeCenterColor,
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    // Nearby route markers
                    if (routeData != null && routeData.markers.isNotEmpty()) {
                        val stride = (routeData.markers.size / 40).coerceAtLeast(1)
                        val pad = maxOf(w, h) * 0.45f
                        var idx = 0
                        while (idx < routeData.markers.size) {
                            val mOff = toScreen(routeData.markers[idx].point)
                            if (mOff.x in -pad..(w + pad) && mOff.y in -pad..(h + pad)) {
                                drawCircle(color = mapTheme.markerBorderColor, radius = 4.dp.toPx(), center = mOff)
                                drawCircle(color = mapTheme.markerDotColor, radius = 2.6.dp.toPx(), center = mOff)
                            }
                            idx += stride
                        }
                    }

                    // Live / capture location marker + directional arrow
                    if (location != null) {
                        val userOff = toScreen(location)
                        val accRadiusPx = if (gpsAccuracy > 0f) {
                            (gpsAccuracy * 0.45f).coerceIn(10f, 24f)
                        } else {
                            12.dp.toPx()
                        }
                        drawCircle(
                            color = mapTheme.userPulseColor,
                            radius = accRadiusPx,
                            center = userOff
                        )

                        if (effectiveBearing != null) {
                            val rad = Math.toRadians(effectiveBearing.toDouble())
                            val arrowLen = 14.dp.toPx()
                            val tipX = userOff.x + (sin(rad) * arrowLen).toFloat()
                            val tipY = userOff.y - (cos(rad) * arrowLen).toFloat()
                            val arrowPath = Path().apply {
                                moveTo(tipX, tipY)
                                lineTo(
                                    userOff.x + (sin(rad - 2.3) * (arrowLen * 0.52f)).toFloat(),
                                    userOff.y - (cos(rad - 2.3) * (arrowLen * 0.52f)).toFloat()
                                )
                                lineTo(
                                    userOff.x + (sin(rad + 2.3) * (arrowLen * 0.52f)).toFloat(),
                                    userOff.y - (cos(rad + 2.3) * (arrowLen * 0.52f)).toFloat()
                                )
                                close()
                            }
                            drawPath(arrowPath, color = mapTheme.userBearingColor)
                        }

                        drawCircle(color = mapTheme.userPinInnerColor, radius = 6.dp.toPx(), center = userOff)
                        drawCircle(color = mapTheme.userPinColor, radius = 4.2.dp.toPx(), center = userOff)
                    }
                }
            }

            // Heading unavailable or offline coverage status indicator
            if (showHeadingUnavailable) {
                MiniMapHeadingUnavailableBadge(
                    isDark = mapTheme.isDark,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            } else if (!isTileLoading && tileResult.hasMissingTiles && this@BoxWithConstraints.maxHeight >= 88.dp) {
                MiniMapOfflineCoverageBadge(
                    isDark = mapTheme.isDark,
                    hasPartialCoverage = tileResult.hasAnyCoverage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }

            // GPS Accuracy Badge (Bottom-Left)
            if (showGpsBadge && gpsAccuracy > 0f) {
                val gpsColor = when {
                    gpsAccuracy <= 10f -> Color(0xFF10B981)
                    gpsAccuracy <= 25f -> Color(0xFFF59E0B)
                    else -> AccentRed
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = mapTheme.badgeBg,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(gpsColor)
                        )
                        Text(
                            text = "±${gpsAccuracy.toInt()}m",
                            color = mapTheme.badgeTextColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Compact minimalist +, -, Recenter controls when interactive
            if (interactive) {
                val ctrlBg = if (mapTheme.isDark) Color(0xE00D111A) else Color(0xF2FFFFFF)
                val ctrlIcon = if (mapTheme.isDark) Color.White else Color(0xFF101828)
                val activeBg = if (mapTheme.isDark) Color(0xFF1685FF) else Color(0xFF1677F2)

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ctrlBg,
                        shadowElevation = 0.dp
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    hasUserAdjustedZoom = true
                                    continuousZoom = (continuousZoom + 0.8f).coerceAtMost(MiniMapViewportMath.MAX_ZOOM)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("preview_map_zoom_in")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom in map",
                                    tint = ctrlIcon,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    hasUserAdjustedZoom = true
                                    continuousZoom = (continuousZoom - 0.8f).coerceAtLeast(MiniMapViewportMath.MIN_ZOOM)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("preview_map_zoom_out")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom out map",
                                    tint = ctrlIcon,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isFollowingDefault) activeBg else ctrlBg,
                        shadowElevation = 0.dp
                    ) {
                        IconButton(
                            onClick = {
                                isFollowingDefault = true
                                exploredCenter = null
                                hasUserAdjustedZoom = false
                                continuousZoom = defaultFit.second
                                lastAutoFitLat = baseCenter.latitude
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("preview_map_recenter")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Recenter map",
                                tint = if (isFollowingDefault) Color.White else ctrlIcon,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
