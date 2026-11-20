package com.example.ui.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo.MapHeadingManager
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.MiniMapOrientationMode
import com.example.model.RouteData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure, JVM-testable viewport math shared across every Mini Map in CTECS
 * (Home, Screensaver, Camera, Survey, Routes, Photos).
 */
object MiniMapViewportMath {
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 19.0f
    const val TILE_SIZE = 256.0

    /**
     * Vertical screen fraction where the live vehicle marker is framed during Follow + Direction-Up mode.
     * 0.62 places the vehicle slightly below vertical center so ~62% of the viewport looks ahead.
     */
    const val FOLLOW_FORWARD_Y_FRACTION = 0.62f

    /**
     * Resolves canvas rotation in degrees for the active orientation mode and heading.
     * Never silently switches mode to North-Up; returns 0f only when explicitly NORTH_UP
     * or when no heading has ever been determined yet.
     */
    fun resolveDirectionUpRotation(
        mode: MiniMapOrientationMode,
        headingDegrees: Float?,
        fallbackBearing: Float? = null
    ): Float {
        val active = headingDegrees ?: fallbackBearing
        return when (mode) {
            MiniMapOrientationMode.NORTH_UP -> 0f
            MiniMapOrientationMode.DIRECTION_UP,
            MiniMapOrientationMode.AUTO -> if (active != null && active.isFinite()) {
                -MapHeadingManager.normalizeAngle(active)
            } else {
                0f
            }
        }
    }

    /**
     * Shortest-path unwrap for continuous smooth rotation animations (no 359° <-> 1° full spin).
     */
    fun unwrapShortestAngle(previousUnwrapped: Float, targetDeg: Float): Float {
        var delta = (targetDeg - previousUnwrapped) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return previousUnwrapped + delta
    }

    /**
     * Converts a screen-space drag delta `(screenDx, screenDy)` into unrotated world-tile pixel
     * delta at the base integer zoom, compensating for both active Direction-Up map rotation
     * and fractional zoom scale.
     */
    fun screenDeltaToWorldDelta(
        screenDx: Float,
        screenDy: Float,
        mapRotationDeg: Float,
        fractionalScale: Float
    ): Pair<Float, Float> {
        val safeScale = fractionalScale.coerceAtLeast(0.25f)
        val rad = Math.toRadians((-mapRotationDeg).toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val worldDx = (screenDx * cosR - screenDy * sinR) / safeScale
        val worldDy = (screenDx * sinR + screenDy * cosR) / safeScale
        return Pair(worldDx, worldDy)
    }

    /**
     * Computes base integer tile zoom and smooth fractional scale factor `2^(zoom - baseZoom)`
     * so pinch zoom is continuous without integer jump snapping.
     */
    fun splitContinuousZoom(continuousZoom: Float): Pair<Int, Float> {
        val clamped = continuousZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val baseZoom = floor(clamped).toInt().coerceIn(OsmTileManager.MIN_TILE_ZOOM, OsmTileManager.MAX_TILE_ZOOM)
        val fracScale = 2.0.pow((clamped - baseZoom).toDouble()).toFloat().coerceIn(0.5f, 2.5f)
        return Pair(baseZoom, fracScale)
    }

    /**
     * Offsets a GeoPoint by unrotated pixel deltas at a continuous (floating-point) zoom level.
     * Supports full-Earth latitude clamping and antimeridian longitude wrapping.
     */
    fun offsetGeoPointContinuous(
        center: GeoPoint,
        worldDeltaPxX: Float,
        worldDeltaPxY: Float,
        continuousZoom: Float
    ): GeoPoint {
        val clampedZoom = continuousZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val worldPixels = TILE_SIZE * 2.0.pow(clampedZoom.toDouble())
        val cx = (center.longitude + 180.0) / 360.0 * worldPixels
        val latRad = Math.toRadians(center.latitude.coerceIn(-85.0511, 85.0511))
        val cy = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * worldPixels

        val newX = cx - worldDeltaPxX.toDouble()
        val newY = (cy - worldDeltaPxY.toDouble()).coerceIn(1.0, worldPixels - 1.0)

        var newLng = (newX / worldPixels) * 360.0 - 180.0
        while (newLng > 180.0) newLng -= 360.0
        while (newLng < -180.0) newLng += 360.0

        val n = PI - 2.0 * PI * (newY / worldPixels)
        val newLat = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n))).coerceIn(-85.0511, 85.0511)
        return GeoPoint(newLat, newLng)
    }

    /**
     * Projects a GeoPoint onto unrotated canvas coordinates at continuous zoom relative to [center].
     */
    fun geoToUnrotatedScreen(
        pt: GeoPoint,
        center: GeoPoint,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        continuousZoom: Float
    ): Offset {
        val (baseZoom, fracScale) = splitContinuousZoom(continuousZoom)
        val (sx, sy) = OsmTileManager.geoToScreen(
            pt = pt,
            center = center,
            widthPx = viewportWidthPx,
            heightPx = viewportHeightPx,
            zoom = baseZoom,
            panOffsetX = 0f,
            panOffsetY = 0f
        )
        val cx = viewportWidthPx / 2f
        val cy = viewportHeightPx / 2f
        return Offset(
            x = cx + (sx - cx) * fracScale,
            y = cy + (sy - cy) * fracScale
        )
    }

    /**
     * Rotates an unrotated canvas point around the viewport center `(cx, cy)` by [mapRotationDeg]
     * to compute exact screen-space coordinates (ideal for drawing upright text labels on rotated maps!).
     */
    fun unrotatedToScreenPoint(
        unrotated: Offset,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        mapRotationDeg: Float
    ): Offset {
        if (abs(mapRotationDeg) < 0.01f) return unrotated
        val cx = viewportWidthPx / 2f
        val cy = viewportHeightPx / 2f
        val dx = unrotated.x - cx
        val dy = unrotated.y - cy
        val rad = Math.toRadians(mapRotationDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        return Offset(
            x = cx + dx * cosR - dy * sinR,
            y = cy + dx * sinR + dy * cosR
        )
    }

    /**
     * Applies Google Maps-style focal-point two-finger pinch zoom + rotation-aware pan.
     * Keeps the geographic coordinate under the pinch centroid anchored under the fingers!
     */
    fun applyFocalPinchAndPan(
        currentCenter: GeoPoint,
        currentZoom: Float,
        focalX: Float,
        focalY: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        panX: Float,
        panY: Float,
        zoomChange: Float,
        mapRotationDeg: Float
    ): Pair<GeoPoint, Float> {
        val safeZoomChange = if (zoomChange.isFinite() && zoomChange > 0.01f) zoomChange else 1f
        val zoomDelta = (ln(safeZoomChange.toDouble()) / ln(2.0)).toFloat()
        val newZoom = (currentZoom + zoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val actualScaleRatio = 2.0.pow((newZoom - currentZoom).toDouble()).toFloat()

        // 1. Apply rotation-aware pan at old zoom
        var updatedCenter = currentCenter
        if (panX != 0f || panY != 0f) {
            val (worldPanX, worldPanY) = screenDeltaToWorldDelta(panX, panY, mapRotationDeg, 1f)
            updatedCenter = offsetGeoPointContinuous(
                center = updatedCenter,
                worldDeltaPxX = worldPanX,
                worldDeltaPxY = worldPanY,
                continuousZoom = currentZoom
            )
        }

        // 2. Shift camera toward/away from pinch focal point so point under fingers stays locked
        if (abs(actualScaleRatio - 1f) > 0.0005f && viewportWidthPx > 1f && viewportHeightPx > 1f) {
            val offsetFromCenterX = focalX - viewportWidthPx / 2f
            val offsetFromCenterY = focalY - viewportHeightPx / 2f
            // Screen shift required at newZoom so focal point stays visually stationary
            val screenShiftX = offsetFromCenterX * (1f - actualScaleRatio)
            val screenShiftY = offsetFromCenterY * (1f - actualScaleRatio)
            val (worldShiftX, worldShiftY) = screenDeltaToWorldDelta(screenShiftX, screenShiftY, mapRotationDeg, 1f)
            updatedCenter = offsetGeoPointContinuous(
                center = updatedCenter,
                worldDeltaPxX = worldShiftX,
                worldDeltaPxY = worldShiftY,
                continuousZoom = newZoom
            )
        }

        return Pair(updatedCenter, newZoom)
    }

    /**
     * Computes camera center in Follow + Direction-Up mode so the live vehicle marker sits at
     * [followYFraction] down the viewport (default 0.62), leaving generous road-ahead viewing space.
     */
    fun computeFollowCameraCenter(
        vehicleGeo: GeoPoint,
        continuousZoom: Float,
        viewportHeightPx: Float,
        mapRotationDeg: Float,
        isDirectionUpActive: Boolean,
        followYFraction: Float = FOLLOW_FORWARD_Y_FRACTION
    ): GeoPoint {
        if (!isDirectionUpActive || viewportHeightPx <= 1f) return vehicleGeo
        // Vehicle should appear below viewport center by (followYFraction - 0.5) * h pixels on screen.
        // Moving the map DOWN in screen space (+screenDy) shifts camera center UP (ahead of vehicle).
        val screenShiftY = (followYFraction - 0.5f) * viewportHeightPx
        val (worldDx, worldDy) = screenDeltaToWorldDelta(
            screenDx = 0f,
            screenDy = screenShiftY,
            mapRotationDeg = mapRotationDeg,
            fractionalScale = 1f
        )
        return offsetGeoPointContinuous(
            center = vehicleGeo,
            worldDeltaPxX = worldDx,
            worldDeltaPxY = worldDy,
            continuousZoom = continuousZoom
        )
    }

    /**
     * Computes square tile-bitmap size large enough to cover all four viewport corners under any
     * rotation angle and fractional zoom scale (zero clipped corners).
     */
    fun computeDiagonalRenderSizePx(
        widthPx: Float,
        heightPx: Float,
        fractionalScale: Float,
        maxPx: Int = 1024
    ): Int {
        val safeScale = fractionalScale.coerceAtLeast(0.5f)
        val diag = ceil(hypot(widthPx.toDouble(), heightPx.toDouble()) / safeScale).toInt()
        return (diag + 8).coerceIn(64, maxPx)
    }

    /**
     * Dynamically computes the optimal center and continuous zoom to fit an entire [routeData]
     * inside a container of `[viewportWidthPx x viewportHeightPx]` without cropping or distortion.
     */
    fun computeRouteFitCamera(
        routeData: RouteData,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        paddingFraction: Float = 0.14f
    ): Pair<GeoPoint, Float> {
        val bounds = routeData.bounds
        val center = bounds.center
        if (viewportWidthPx <= 16f || viewportHeightPx <= 16f) {
            return Pair(center, 13.5f)
        }
        val usableW = (viewportWidthPx * (1f - paddingFraction * 2f)).coerceAtLeast(32.0f).toDouble()
        val usableH = (viewportHeightPx * (1f - paddingFraction * 2f)).coerceAtLeast(32.0f).toDouble()

        val lngSpan = abs(bounds.maxLng - bounds.minLng).coerceAtLeast(0.0015)
        val latMinRad = Math.toRadians(bounds.minLat.coerceIn(-85.0, 85.0))
        val latMaxRad = Math.toRadians(bounds.maxLat.coerceIn(-85.0, 85.0))
        val yMin = (1.0 - ln(tan(latMaxRad) + 1.0 / cos(latMaxRad)) / PI) / 2.0
        val yMax = (1.0 - ln(tan(latMinRad) + 1.0 / cos(latMinRad)) / PI) / 2.0
        val normY = abs(yMax - yMin).coerceAtLeast(0.00001)
        val normX = (lngSpan / 360.0).coerceAtLeast(0.00001)

        val zoomX = ln(usableW / (TILE_SIZE * normX)) / ln(2.0)
        val zoomY = ln(usableH / (TILE_SIZE * normY)) / ln(2.0)
        val fittedZoom = minOf(zoomX, zoomY).toFloat().coerceIn(3.0f, 16.5f)
        return Pair(center, fittedZoom)
    }
}

/**
 * Reliable Google Maps-style gesture modifier for Mini Maps:
 * - Smooth two-finger focal-point pinch zoom + simultaneous pan.
 * - Single-finger pan in all directions once past touch slop.
 * - Immediately consumes active pinch & map-drag pointer events so parent Scroll/LazyColumn
 *   containers never steal gestures or fight zoom/pan, while clean single taps pass through.
 */
fun Modifier.miniMapNavigationGestures(
    enabled: Boolean = true,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
): Modifier = composed {
    if (!enabled) return@composed this
    val latestOnTransform by rememberUpdatedState(onTransform)

    pointerInput(enabled) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            var zoomAccum = 1f
            var panAccum = Offset.Zero
            var pastTouchSlop = false

            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val pointerCount = event.changes.count { it.pressed }
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()

                    if (!pastTouchSlop) {
                        zoomAccum *= zoomChange
                        panAccum += panChange
                        val centroidSize = event.calculateCentroid(useCurrent = false)
                        val zoomMotion = abs(1f - zoomAccum) *
                            if (centroidSize != Offset.Unspecified) 180f else 0f
                        val panMotion = panAccum.getDistance()

                        // Immediately claim two-finger pinch or deliberate drag past touchSlop
                        if (pointerCount >= 2 || panMotion > touchSlop || zoomMotion > touchSlop) {
                            pastTouchSlop = true
                        }
                    }

                    if (pastTouchSlop) {
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val safeCentroid = if (centroid != Offset.Unspecified) {
                                centroid
                            } else {
                                Offset(size.width / 2f, size.height / 2f)
                            }
                            latestOnTransform(safeCentroid, panChange, zoomChange)
                        }
                        event.changes.forEach { change ->
                            if (change.positionChanged() || pointerCount >= 2) {
                                change.consume()
                            }
                        }
                    }
                }
            } while (!canceled && event.changes.any { it.pressed })
        }
    }
}

/**
 * Compact, minimalist indicator displayed ONLY when no reliable heading has ever been established.
 */
@Composable
fun MiniMapHeadingUnavailableBadge(
    isDark: Boolean,
    label: String = "Heading unavailable",
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xD90D111A) else Color(0xEBFFFFFF)
    val fg = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    Surface(
        shape = CircleShape,
        color = bg,
        shadowElevation = 0.dp,
        modifier = modifier.testTag("minimap_heading_unavailable_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ExploreOff,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                color = fg,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
