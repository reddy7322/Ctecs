package com.example.ui.calibration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo.ChainageMatcher
import com.example.geo.KmzCalibrationEngine
import com.example.model.CenterlineOffsetZone
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.KmzManualCalibration
import com.example.model.MatchConfidence
import com.example.model.ParallelCenterlineOffset
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.TravelChainageDirection
import com.example.ui.settings.isSystemReducedMotionEnabled
import com.example.ui.theme.CtecsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Interactive Animated Road Alignment Tracking Canvas & Parallel Centerline Offset HUD.
 *
 * Displays:
 * - Actual loaded KMZ/KML road centerline (100% offline geometry).
 * - Uncalibrated vs Active ±2 km calibrated road sections in both directions.
 * - Left and right parallel centerline offset guides & perpendicular GPS-to-centerline connector.
 * - Current GPS location with scaled GPS uncertainty halo.
 * - Projected GPS position on the centerline.
 * - Calibration reference markers (with active-reference highlight and tap-to-inspect details).
 * - Corrected chainage, forward/reverse movement direction, and signed lateral offset badge.
 *
 * Strictly avoids fabricating any animated position when GPS or alignment data is unavailable,
 * pauses moving-track animation when stationary, and respects system reduced-motion settings.
 */
@Composable
fun SmartAnimatedAlignmentTrack(
    activeRoute: RouteData?,
    effectiveAlignment: RouteAlignment?,
    matchResult: ChainageMatchResult,
    currentGpsPoint: GeoPoint?,
    currentGpsAccuracy: Float,
    speedMps: Float? = null,
    calibrations: List<KmzManualCalibration> = emptyList(),
    activeCalibration: KmzManualCalibration? = null,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }

    val cachedGeometry = remember(activeRoute?.id, effectiveAlignment?.id, effectiveAlignment?.points?.size) {
        if (activeRoute != null && effectiveAlignment != null && effectiveAlignment.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(effectiveAlignment, activeRoute.markers)
        } else {
            null
        }
    }

    val hasValidAlignment = cachedGeometry != null && cachedGeometry.points.size >= 2 && cachedGeometry.totalLengthM > 0.0
    val hasValidGpsOnAlignment = hasValidAlignment &&
        currentGpsPoint != null &&
        matchResult.confidence != MatchConfidence.NO_MATCH &&
        matchResult.projectedPoint != null

    // Track forward/reverse movement & stationary state from actual distanceAlongAlignment changes and GPS speed
    var previousDistanceAlongM by remember(activeRoute?.id, effectiveAlignment?.id) {
        mutableDoubleStateOf(Double.NaN)
    }
    var lastMovementTimestampMs by remember(activeRoute?.id, effectiveAlignment?.id) {
        mutableLongStateOf(0L)
    }
    var detectedMovementDir by remember(activeRoute?.id, effectiveAlignment?.id) {
        mutableStateOf(TravelChainageDirection.UNKNOWN)
    }

    LaunchedEffect(matchResult.distanceAlongAlignmentMetres, matchResult.timestamp, hasValidGpsOnAlignment) {
        if (hasValidGpsOnAlignment) {
            val currDist = matchResult.distanceAlongAlignmentMetres
            if (previousDistanceAlongM.isFinite()) {
                val delta = currDist - previousDistanceAlongM
                if (abs(delta) >= 0.6) {
                    lastMovementTimestampMs = System.currentTimeMillis()
                    val isIncreasingAlign = cachedGeometry?.direction != "DECREASING"
                    detectedMovementDir = when {
                        (delta > 0.0 && isIncreasingAlign) || (delta < 0.0 && !isIncreasingAlign) ->
                            TravelChainageDirection.FORWARD
                        else ->
                            TravelChainageDirection.REVERSE
                    }
                }
            }
            previousDistanceAlongM = currDist
        }
    }

    val effectiveTravelDir = when {
        matchResult.travelDirection != TravelChainageDirection.UNKNOWN -> matchResult.travelDirection
        detectedMovementDir != TravelChainageDirection.UNKNOWN -> detectedMovementDir
        else -> TravelChainageDirection.STATIONARY
    }

    val isMoving = hasValidGpsOnAlignment && (
        (speedMps != null && speedMps >= 0.45f) ||
            (lastMovementTimestampMs > 0L && System.currentTimeMillis() - lastMovementTimestampMs <= 3_500L)
        )

    // Smoothly animate projected distance along the real alignment polyline
    val targetDistFloat = if (hasValidGpsOnAlignment && cachedGeometry != null) {
        matchResult.distanceAlongAlignmentMetres.coerceIn(0.0, cachedGeometry.totalLengthM).toFloat()
    } else {
        0f
    }
    val animatedDistanceAlongM by animateFloatAsState(
        targetValue = targetDistFloat,
        animationSpec = if (reducedMotion) tween(0) else tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "animated_alignment_distance"
    )

    // Subtle moving track dash phase (active ONLY when vehicle is moving and reducedMotion is false)
    val trackFlowPhase: Float = if (isMoving && !reducedMotion) {
        val infinite = rememberInfiniteTransition(label = "alignment_track_flow")
        val phase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 36f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "track_flow_phase"
        )
        if (effectiveTravelDir == TravelChainageDirection.REVERSE) -phase else phase
    } else {
        0f
    }

    var zoomFactor by remember(activeRoute?.id, effectiveAlignment?.id) { mutableFloatStateOf(1.0f) }
    var panOffset by remember(activeRoute?.id, effectiveAlignment?.id) { mutableStateOf(Offset.Zero) }
    var inspectedCalibration by remember(activeRoute?.id, effectiveAlignment?.id) {
        mutableStateOf<KmzManualCalibration?>(null)
    }

    val parallelOffset = remember(matchResult, currentGpsPoint) {
        matchResult.parallelCenterlineOffset ?: ParallelCenterlineOffset(
            zone = if (!hasValidGpsOnAlignment) CenterlineOffsetZone.OUTSIDE else matchResult.centerlineOffsetZone,
            perpendicularDistanceMetres = matchResult.offsetMetres,
            signedLateralOffsetMetres = matchResult.signedLateralOffsetMetres,
            localTangentBearingDegrees = matchResult.localTangentBearingDegrees,
            projectedPoint = matchResult.projectedPoint,
            gpsPoint = currentGpsPoint,
            matchedAlignmentId = matchResult.matchedAlignmentId,
            matchedAlignmentName = matchResult.matchedAlignmentName
        )
    }

    val alignmentCalibrations = remember(calibrations, activeRoute?.id, effectiveAlignment?.id) {
        if (activeRoute == null) emptyList()
        else calibrations.filter { cal ->
            cal.routeId == activeRoute.id && (
                effectiveAlignment == null ||
                    cal.alignmentId == effectiveAlignment.id ||
                    cal.alignmentId == activeRoute.id ||
                    cal.alignmentId.isBlank()
                )
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.secondarySurface,
        border = BorderStroke(1.dp, c.subtleSeparator),
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_animated_alignment_track_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Telemetry & Parallel Centerline Offset Bar
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
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Animated Centerline & Parallel Offset",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                !hasValidAlignment -> "Load KMZ/KML alignment to view road track"
                                !hasValidGpsOnAlignment -> "Offline geometry loaded • Waiting for on-route GPS"
                                else -> "${effectiveAlignment?.name ?: activeRoute?.name.orEmpty()} • Tangent ${parallelOffset.localTangentBearingDegrees.roundToLong()}°"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Parallel Centerline Offset Badge ("L 4.2 m", "R 6.5 m", "ON LINE", "OUTSIDE")
                val offsetBadgeColor by animateColorAsState(
                    targetValue = when (parallelOffset.zone) {
                        CenterlineOffsetZone.ON_LINE -> c.success
                        CenterlineOffsetZone.LEFT -> c.accent
                        CenterlineOffsetZone.RIGHT -> c.accentSecondary
                        CenterlineOffsetZone.OUTSIDE -> c.warning
                    },
                    animationSpec = tween(200),
                    label = "parallel_offset_badge_color"
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = offsetBadgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, offsetBadgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = parallelOffset.formattedBadge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = offsetBadgeColor,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("parallel_centerline_offset_badge")
                    )
                }
            }

            // Interactive Alignment Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.background.copy(alpha = 0.85f))
                    .testTag("smart_animated_alignment_canvas_box")
            ) {
                if (!hasValidAlignment || cachedGeometry == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No KMZ/KML road alignment geometry available",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.testTag("alignment_track_empty_state")
                        )
                    }
                } else {
                    val markerHitPositions = remember { mutableListOf<Pair<KmzManualCalibration, Offset>>() }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cachedGeometry.alignmentId) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomFactor = (zoomFactor * zoom).coerceIn(1.0f, 8.0f)
                                    panOffset += pan
                                }
                            }
                            .pointerInput(alignmentCalibrations) {
                                detectTapGestures { tapOffset ->
                                    val hit = markerHitPositions.minByOrNull { (_, pos) ->
                                        (pos - tapOffset).getDistance()
                                    }
                                    if (hit != null && (hit.second - tapOffset).getDistance() <= 44f) {
                                        inspectedCalibration = if (inspectedCalibration?.id == hit.first.id) {
                                            null
                                        } else {
                                            hit.first
                                        }
                                    }
                                }
                            }
                    ) {
                        val pts = cachedGeometry.points
                        val minLat = pts.minOf { it.latitude }
                        val maxLat = pts.maxOf { it.latitude }
                        val minLng = pts.minOf { it.longitude }
                        val maxLng = pts.maxOf { it.longitude }
                        val midLatRad = Math.toRadians((minLat + maxLat) * 0.5)
                        val cosLat = cos(midLatRad).coerceAtLeast(0.1)

                        val spanX = ((maxLng - minLng) * cosLat).coerceAtLeast(1e-5)
                        val spanY = (maxLat - minLat).coerceAtLeast(1e-5)
                        val pad = 26.dp.toPx()
                        val availW = (size.width - pad * 2f).coerceAtLeast(10f)
                        val availH = (size.height - pad * 2f).coerceAtLeast(10f)
                        val baseScale = min(availW / spanX.toFloat(), availH / spanY.toFloat()) * zoomFactor

                        val centerLng = (minLng + maxLng) * 0.5
                        val centerLat = (minLat + maxLat) * 0.5
                        val cx = size.width * 0.5f + panOffset.x
                        val cy = size.height * 0.5f + panOffset.y

                        fun geoToCanvas(pt: GeoPoint): Offset {
                            val dx = ((pt.longitude - centerLng) * cosLat).toFloat() * baseScale
                            val dy = -(pt.latitude - centerLat).toFloat() * baseScale
                            return Offset(cx + dx, cy + dy)
                        }

                        // 1. Draw uncalibrated full road centerline
                        val fullPath = Path()
                        pts.forEachIndexed { idx, pt ->
                            val pos = geoToCanvas(pt)
                            if (idx == 0) fullPath.moveTo(pos.x, pos.y)
                            else fullPath.lineTo(pos.x, pos.y)
                        }

                        // Parallel Left & Right offset corridor rails
                        drawPath(
                            path = fullPath,
                            color = c.textMuted.copy(alpha = 0.16f),
                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = fullPath,
                            color = c.textSecondary.copy(alpha = 0.45f),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // 2. Highlight calibrated ±2 km sections along the real road centerline
                        alignmentCalibrations.forEach { cal ->
                            val isThisActive = activeCalibration?.id == cal.id
                            val covStart = (cal.anchorDistanceAlongMetres - cal.coverageRadiusMetres)
                                .coerceIn(0.0, cachedGeometry.totalLengthM)
                            val covEnd = (cal.anchorDistanceAlongMetres + cal.coverageRadiusMetres)
                                .coerceIn(0.0, cachedGeometry.totalLengthM)
                            if (covEnd > covStart + 1.0) {
                                val covPath = buildSubPolylinePath(
                                    cached = cachedGeometry,
                                    startDistM = covStart,
                                    endDistM = covEnd,
                                    geoToCanvas = ::geoToCanvas
                                )
                                val sectionColor = if (isThisActive) c.success else c.accent
                                drawPath(
                                    path = covPath,
                                    color = sectionColor.copy(alpha = if (isThisActive) 0.28f else 0.16f),
                                    style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                                drawPath(
                                    path = covPath,
                                    color = sectionColor,
                                    style = Stroke(width = if (isThisActive) 4.5.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // 3. Subtle moving track flow pulse when vehicle is moving
                        if (isMoving && !reducedMotion) {
                            drawPath(
                                path = fullPath,
                                color = c.accent.copy(alpha = 0.65f),
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(12f, 24f),
                                        phase = trackFlowPhase
                                    )
                                )
                            )
                        }

                        // 4. Draw calibration reference markers along alignment
                        markerHitPositions.clear()
                        alignmentCalibrations.forEach { cal ->
                            val refGeo = if (cal.anchorLatitude != null && cal.anchorLongitude != null) {
                                GeoPoint(cal.anchorLatitude, cal.anchorLongitude)
                            } else {
                                ChainageMatcher.interpolatePointAtDistanceAlongAlignment(
                                    cachedGeometry,
                                    cal.anchorDistanceAlongMetres
                                )
                            }
                            val refCanvasPos = geoToCanvas(refGeo)
                            markerHitPositions.add(cal to refCanvasPos)

                            val isThisActive = activeCalibration?.id == cal.id
                            val isThisInspected = inspectedCalibration?.id == cal.id
                            val markerColor = if (isThisActive) c.success else c.accentSecondary

                            if (isThisActive || isThisInspected) {
                                drawCircle(
                                    color = markerColor.copy(alpha = 0.26f),
                                    radius = 12.dp.toPx(),
                                    center = refCanvasPos
                                )
                            }
                            drawCircle(
                                color = c.surface,
                                radius = 7.dp.toPx(),
                                center = refCanvasPos
                            )
                            drawCircle(
                                color = markerColor,
                                radius = 5.5.dp.toPx(),
                                center = refCanvasPos
                            )
                        }

                        // 5. Draw live Projected Centerline Point, Perpendicular Lateral Offset Vector, and Actual GPS Point
                        // NEVER fabricate a position when GPS or alignment match is unavailable
                        if (hasValidGpsOnAlignment && currentGpsPoint != null) {
                            val animatedProjectedGeo = ChainageMatcher.interpolatePointAtDistanceAlongAlignment(
                                cached = cachedGeometry,
                                distanceAlongMetres = animatedDistanceAlongM.toDouble()
                            )
                            val projCanvasPos = geoToCanvas(animatedProjectedGeo)
                            val rawGpsCanvasPos = geoToCanvas(currentGpsPoint)

                            // Ensure visible lateral separation on canvas when offset >= 1m so engineer can clearly see Left/Right offset
                            val vec = rawGpsCanvasPos - projCanvasPos
                            val distPx = vec.getDistance()
                            val minVisualLateralPx = if (parallelOffset.zone == CenterlineOffsetZone.LEFT ||
                                parallelOffset.zone == CenterlineOffsetZone.RIGHT
                            ) {
                                (parallelOffset.perpendicularDistanceMetres.toFloat().coerceIn(2f, 28f)) * 1.15f
                            } else 0f

                            val gpsCanvasPos = if (distPx in 0.01f..<minVisualLateralPx) {
                                projCanvasPos + (vec / distPx) * minVisualLateralPx
                            } else {
                                rawGpsCanvasPos
                            }

                            // Perpendicular offset line connecting projected centerline point and GPS point
                            drawLine(
                                color = when (parallelOffset.zone) {
                                    CenterlineOffsetZone.LEFT -> c.accent
                                    CenterlineOffsetZone.RIGHT -> c.accentSecondary
                                    else -> c.success
                                },
                                start = projCanvasPos,
                                end = gpsCanvasPos,
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )

                            // Projected point on centerline
                            drawCircle(
                                color = c.success,
                                radius = 5.dp.toPx(),
                                center = projCanvasPos
                            )

                            // GPS uncertainty halo scaled by accuracy
                            val haloRadiusDp = (currentGpsAccuracy.coerceIn(3f, 35f) * 0.75f).dp
                            val haloColor = if (matchResult.isUncertain) c.warning else c.accent
                            drawCircle(
                                color = haloColor.copy(alpha = 0.20f),
                                radius = haloRadiusDp.toPx(),
                                center = gpsCanvasPos
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 6.5.dp.toPx(),
                                center = gpsCanvasPos
                            )
                            drawCircle(
                                color = haloColor,
                                radius = 4.8.dp.toPx(),
                                center = gpsCanvasPos
                            )
                        }
                    }

                    // Canvas Zoom & Recenter Overlay Controls (top-right)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SmallCanvasControlIcon(
                            icon = Icons.Default.Add,
                            contentDescription = "Zoom in alignment track",
                            testTag = "alignment_track_zoom_in_button",
                            onClick = { zoomFactor = (zoomFactor * 1.35f).coerceAtMost(8.0f) }
                        )
                        SmallCanvasControlIcon(
                            icon = Icons.Default.Remove,
                            contentDescription = "Zoom out alignment track",
                            testTag = "alignment_track_zoom_out_button",
                            onClick = { zoomFactor = (zoomFactor / 1.35f).coerceAtLeast(1.0f) }
                        )
                        SmallCanvasControlIcon(
                            icon = Icons.Default.CenterFocusStrong,
                            contentDescription = "Recenter alignment track",
                            testTag = "alignment_track_recenter_button",
                            onClick = {
                                zoomFactor = 1.0f
                                panOffset = Offset.Zero
                            }
                        )
                    }

                    // Bottom Overlay Telemetry Strip inside Canvas
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(c.surface.copy(alpha = 0.86f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CH ${matchResult.displayChainage}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = c.textPrimary,
                            modifier = Modifier.testTag("alignment_track_chainage_text")
                        )
                        val dirText = when (effectiveTravelDir) {
                            TravelChainageDirection.FORWARD -> "▲ FORWARD"
                            TravelChainageDirection.REVERSE -> "▼ REVERSE"
                            else -> "• STATIONARY"
                        }
                        Text(
                            text = dirText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.accent,
                            modifier = Modifier.testTag("alignment_track_direction_text")
                        )
                        val gpsUncertaintyText = if (currentGpsPoint == null) {
                            "No GPS"
                        } else if (matchResult.isUncertain) {
                            "Uncertain ±${currentGpsAccuracy.toInt()}m"
                        } else {
                            "GPS ±${String.format(Locale.US, "%.1f", currentGpsAccuracy)}m"
                        }
                        Text(
                            text = gpsUncertaintyText,
                            fontSize = 10.5.sp,
                            color = if (matchResult.isUncertain) c.warning else c.textSecondary,
                            modifier = Modifier.testTag("alignment_track_gps_uncertainty_text")
                        )
                    }
                }
            }

            // Parallel Centerline Offset Detailed Position Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parallel_centerline_offset_detail_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = parallelOffset.formattedPositionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textSecondary,
                    modifier = Modifier.testTag("parallel_centerline_position_label")
                )
                Text(
                    text = "Lateral offset: ${parallelOffset.formattedBadge}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
            }

            // Selectable Reference Marker Chips for quick inspection
            if (alignmentCalibrations.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    alignmentCalibrations.forEach { cal ->
                        val isSelected = inspectedCalibration?.id == cal.id
                        val isActive = activeCalibration?.id == cal.id
                        val chipColor = if (isActive) c.success else c.accent
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) chipColor.copy(alpha = 0.22f) else c.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected || isActive) chipColor else c.subtleSeparator
                            ),
                            modifier = Modifier
                                .clickable {
                                    inspectedCalibration = if (isSelected) null else cal
                                }
                                .testTag("alignment_track_marker_chip_${cal.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = chipColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Ref ${cal.manualReferenceChainage.format()} (${cal.formattedOffset})",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = c.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Inspected Reference Marker Details Popup Card
            AnimatedVisibility(visible = inspectedCalibration != null) {
                inspectedCalibration?.let { inspected ->
                    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inspected_calibration_marker_card")
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
                                    text = "Reference ${inspected.manualReferenceChainage.format()} • Offset ${inspected.formattedOffset}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = c.accent
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close marker inspection",
                                    tint = c.textSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { inspectedCalibration = null }
                                )
                            }
                            Text(
                                text = "Original KMZ: ${inspected.originalKmzChainage.format()} • Coverage: ${inspected.formattedCoverageRange}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textPrimary
                            )
                            Text(
                                text = "GPS ${inspected.formattedGpsAccuracy} • Lateral ${inspected.formattedLateralOffset} • ${sdf.format(Date(inspected.updatedAtMs))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallCanvasControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    val c = CtecsTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = c.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, c.subtleSeparator),
        modifier = Modifier
            .size(30.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = c.textPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun buildSubPolylinePath(
    cached: ChainageMatcher.CachedAlignmentGeometry,
    startDistM: Double,
    endDistM: Double,
    geoToCanvas: (GeoPoint) -> Offset
): Path {
    val path = Path()
    if (endDistM <= startDistM || cached.points.size < 2) return path

    val startPt = ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cached, startDistM)
    val startPos = geoToCanvas(startPt)
    path.moveTo(startPos.x, startPos.y)

    for (i in 1 until cached.points.size - 1) {
        val d = cached.cumulativeDistances[i]
        if (d in startDistM..endDistM) {
            val pos = geoToCanvas(cached.points[i])
            path.lineTo(pos.x, pos.y)
        }
    }

    val endPt = ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cached, endDistM)
    val endPos = geoToCanvas(endPt)
    path.lineTo(endPos.x, endPos.y)
    return path
}
