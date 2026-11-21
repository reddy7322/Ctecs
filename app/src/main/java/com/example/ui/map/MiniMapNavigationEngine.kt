package com.example.ui.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo.MapHeadingManager
import com.example.geo.OfflineMapSyncState
import com.example.geo.OfflineSyncStatus
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.MiniMapOrientationMode
import com.example.model.RouteData
import com.example.ui.theme.CtecsTheme
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
    const val MAX_ZOOM = 23.0f
    const val TILE_SIZE = 256.0
    const val EARTH_EQUATORIAL_CIRCUMFERENCE_METRES = 40_075_016.686

    /**
     * Global standard initial ground viewing range (metres) for every Mini Map across CTECS (250 M).
     * Always converted dynamically to continuous Web Mercator zoom using latitude, viewport
     * dimensions, and aspect ratio — never a hardcoded zoom number.
     */
    const val DEFAULT_GROUND_VIEW_RANGE_METRES = 250.0

    /**
     * Vertical screen fraction where the live vehicle marker is framed during Follow + Direction-Up mode.
     * 0.62 places the vehicle slightly below vertical center so ~62% of the viewport looks ahead.
     */
    const val FOLLOW_FORWARD_Y_FRACTION = 0.62f

    /**
     * Dynamically calculates the continuous Web Mercator zoom level required to show exactly
     * [groundRangeMetres] across the limiting viewport dimension (`min(width, height)`),
     * accounting for latitude distortion (`cos(lat)`), viewport pixel dimensions, and aspect ratio.
     */
    fun computeZoomForGroundRangeMetres(
        latitude: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        groundRangeMetres: Double = DEFAULT_GROUND_VIEW_RANGE_METRES
    ): Float {
        val safeRange = groundRangeMetres.coerceIn(5.0, 20_000_000.0)
        val clampedLat = if (latitude.isFinite()) latitude.coerceIn(-85.0, 85.0) else 0.0
        val cosLat = cos(Math.toRadians(clampedLat)).coerceAtLeast(0.05)
        val limitingSpanPx = if (viewportWidthPx > 1f && viewportHeightPx > 1f) {
            minOf(viewportWidthPx, viewportHeightPx).toDouble()
        } else {
            360.0
        }
        val metersPerPixel = safeRange / limitingSpanPx
        val worldScale = (EARTH_EQUATORIAL_CIRCUMFERENCE_METRES * cosLat) / (TILE_SIZE * metersPerPixel)
        val zoom = (ln(worldScale) / ln(2.0)).toFloat()
        return if (zoom.isFinite()) zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) else 19.0f
    }

    /**
     * Computes the actual ground distance (in metres) spanning the limiting viewport dimension
     * at [continuousZoom] and [latitude]. Exact inverse of [computeZoomForGroundRangeMetres].
     */
    fun computeGroundRangeMetres(
        continuousZoom: Float,
        latitude: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Double {
        val clampedZoom = continuousZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val clampedLat = if (latitude.isFinite()) latitude.coerceIn(-85.0, 85.0) else 0.0
        val cosLat = cos(Math.toRadians(clampedLat)).coerceAtLeast(0.05)
        val limitingSpanPx = if (viewportWidthPx > 1f && viewportHeightPx > 1f) {
            minOf(viewportWidthPx, viewportHeightPx).toDouble()
        } else {
            360.0
        }
        val metersPerPixel = (EARTH_EQUATORIAL_CIRCUMFERENCE_METRES * cosLat) /
            (TILE_SIZE * 2.0.pow(clampedZoom.toDouble()))
        return metersPerPixel * limitingSpanPx
    }

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
     * so pinch zoom and close-range 25m viewing remain smooth beyond integer tile zoom 19.
     */
    fun splitContinuousZoom(continuousZoom: Float): Pair<Int, Float> {
        val clamped = continuousZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val baseZoom = floor(clamped).toInt().coerceIn(OsmTileManager.MIN_TILE_ZOOM, OsmTileManager.MAX_TILE_ZOOM)
        val fracScale = 2.0.pow((clamped - baseZoom).toDouble()).toFloat().coerceIn(0.5f, 16.0f)
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

    data class TileRenderPlan(
        val renderBaseZoom: Int,
        val renderScale: Float,
        val renderSizePx: Int
    )

    /**
     * Resolves `(renderBaseZoom, renderScale, renderSizePx)` so that even on large high-DPI
     * portrait/landscape screens or rotated containers, `renderSizePx * renderScale` ALWAYS
     * covers the full rotated diagonal `hypot(widthPx, heightPx)` without ever cropping edges
     * or allocating oversized bitmaps.
     */
    fun resolveTileRenderPlan(
        widthPx: Float,
        heightPx: Float,
        continuousZoom: Float,
        maxBitmapPx: Int = 1152
    ): TileRenderPlan {
        val (initialBase, initialScale) = splitContinuousZoom(continuousZoom)
        var renderBase = initialBase
        var renderScale = initialScale
        val rawDiag = hypot(widthPx.toDouble().coerceAtLeast(1.0), heightPx.toDouble().coerceAtLeast(1.0))

        while (ceil(rawDiag / renderScale).toInt() + 12 > maxBitmapPx &&
            renderBase > OsmTileManager.MIN_TILE_ZOOM
        ) {
            renderBase -= 1
            renderScale *= 2f
        }

        val rawRequiredPx = (ceil(rawDiag / renderScale).toInt() + 12).coerceIn(64, 1800)
        // Quantize up to 128px buckets so smooth animated viewport resizing (e.g. Home Weather Card
        // expand/collapse) keeps identical bitmap dimensions without triggering unnecessary tile reloads.
        val bucketedPx = (((rawRequiredPx + 127) / 128) * 128).coerceIn(128, 1800)
        val renderSizePx = bucketedPx.coerceAtLeast(rawRequiredPx)
        return TileRenderPlan(
            renderBaseZoom = renderBase,
            renderScale = renderScale,
            renderSizePx = renderSizePx
        )
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
 * Global Mini Map Top View Distance options exposed in Settings -> Mini Map Settings
 * and applied consistently across all Mini Maps (Home, Camera, Screensaver, Survey, Routes, Photos).
 */
enum class MiniMapViewDistanceMode(
    val storageKey: String,
    val label: String,
    val fixedMetres: Double?
) {
    DIST_100M("100m", "100 M", 100.0),
    DIST_250M("250m", "250 M", 250.0),
    DIST_500M("500m", "500 M", 500.0),
    DIST_1KM("1km", "1 KM", 1_000.0),
    DIST_2KM("2km", "2 KM", 2_000.0),
    DIST_5KM("5km", "5 KM", 5_000.0),
    DIST_10KM("10km", "10 KM", 10_000.0),
    DIST_20KM("20km", "20 KM", 20_000.0),
    CUSTOM("custom", "Custom Distance", null),
    SMART_AUTO("smart_auto", "Smart Auto", null);

    companion object {
        val DEFAULT: MiniMapViewDistanceMode = DIST_250M

        fun fromStorageKey(key: String?): MiniMapViewDistanceMode {
            if (key.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.storageKey.equals(key.trim(), ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) }
                ?: DEFAULT
        }
    }
}

/**
 * Persisted user preference for "Mini Map Top View Distance".
 * Defaults to 250 M ([MiniMapViewDistanceMode.DIST_250M]).
 */
data class MiniMapViewDistancePreference(
    val mode: MiniMapViewDistanceMode = MiniMapViewDistanceMode.DEFAULT,
    val customDistanceMetres: Double = MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
) {
    val validatedCustomMetres: Double
        get() = clampCustomDistanceMetres(customDistanceMetres)

    fun fixedOrDefaultGroundRangeMetres(): Double {
        return when (mode) {
            MiniMapViewDistanceMode.CUSTOM -> validatedCustomMetres
            MiniMapViewDistanceMode.SMART_AUTO -> MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
            else -> mode.fixedMetres ?: MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
        }
    }

    fun summaryLabel(): String {
        return when (mode) {
            MiniMapViewDistanceMode.CUSTOM -> "Custom (${formatDistanceLabel(validatedCustomMetres)})"
            MiniMapViewDistanceMode.SMART_AUTO -> "Smart Auto"
            else -> mode.label
        }
    }

    companion object {
        const val MIN_CUSTOM_DISTANCE_METRES = 50.0
        const val MAX_CUSTOM_DISTANCE_METRES = 50_000.0

        fun clampCustomDistanceMetres(metres: Double): Double {
            if (!metres.isFinite()) return MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
            return metres.coerceIn(MIN_CUSTOM_DISTANCE_METRES, MAX_CUSTOM_DISTANCE_METRES)
        }

        /**
         * Parses and validates user-entered Custom Distance text.
         * Returns null when empty, non-numeric, or outside [50 M .. 50 KM].
         */
        fun parseAndValidateCustomDistance(rawText: String, unitIsKm: Boolean): Double? {
            val cleaned = rawText.trim().replace(",", "")
            if (cleaned.isEmpty()) return null
            val numeric = cleaned.toDoubleOrNull() ?: return null
            if (!numeric.isFinite() || numeric <= 0.0) return null
            val metres = if (unitIsKm) numeric * 1_000.0 else numeric
            if (metres < MIN_CUSTOM_DISTANCE_METRES || metres > MAX_CUSTOM_DISTANCE_METRES) {
                return null
            }
            return metres
        }

        fun formatDistanceLabel(metres: Double): String {
            val clamped = clampCustomDistanceMetres(metres)
            return if (clamped >= 1_000.0) {
                val km = clamped / 1_000.0
                if (abs(km - kotlin.math.round(km)) < 0.05) {
                    "${kotlin.math.round(km).toInt()} KM"
                } else {
                    String.format(java.util.Locale.US, "%.1f KM", km)
                }
            } else {
                "${kotlin.math.round(clamped).toInt()} M"
            }
        }
    }
}

/**
 * Smart Auto dynamic Mini Map viewing distance engine.
 *
 * Smoothly adjusts real ground viewing range in metres based on:
 * - Current movement speed (m/s) and moving vs stationary state
 * - GPS accuracy / confidence
 * - Local road alignment geometry (sharp curves, junctions/multiple alignments, straight stretches)
 * - Route context (on-alignment vs off-alignment)
 *
 * Includes stationary hold, deadband hysteresis, and exponential damping so viewing distance
 * never jumps abruptly or oscillates when stationary.
 */
object SmartAutoMiniMapDistanceEngine {
    const val STATIONARY_SPEED_MPS = 0.85f
    const val MIN_SMART_RANGE_METRES = 140.0
    const val MAX_SMART_RANGE_METRES = 3_000.0

    data class SmartDistanceContext(
        val speedMps: Float?,
        val gpsAccuracyMetres: Float,
        val location: GeoPoint?,
        val routeData: RouteData?,
        val previousRangeMetres: Double? = null
    )

    fun resolveGroundRangeMetres(
        preference: MiniMapViewDistancePreference,
        context: SmartDistanceContext
    ): Double {
        if (preference.mode != MiniMapViewDistanceMode.SMART_AUTO) {
            return preference.fixedOrDefaultGroundRangeMetres()
        }
        return computeSmartAutoRangeMetres(context)
    }

    fun computeSmartAutoRangeMetres(context: SmartDistanceContext): Double {
        val speed = context.speedMps?.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val isStationary = speed < STATIONARY_SPEED_MPS

        // 1. Speed-based baseline viewing distance (metres)
        val speedBaseRange = when {
            isStationary -> MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES // 250 M stable default
            speed < 4.5f -> 180.0 + ((speed - STATIONARY_SPEED_MPS) / (4.5f - STATIONARY_SPEED_MPS)) * 70.0
            speed < 13.9f -> 250.0 + ((speed - 4.5f) / (13.9f - 4.5f)) * 270.0 // 16–50 km/h -> 250..520 m
            speed < 25.0f -> 520.0 + ((speed - 13.9f) / (25.0f - 13.9f)) * 680.0 // 50–90 km/h -> 520..1200 m
            else -> (1200.0 + ((speed - 25.0f).coerceAtMost(15f) / 15f) * 1000.0) // > 90 km/h -> 1200..2200 m
        }

        // 2. Road alignment geometry & route context adjustment
        var geometryFactor = 1.0
        val loc = context.location
        val route = context.routeData
        if (loc != null && route != null && route.alignments.isNotEmpty()) {
            val analysis = analyzeLocalRoadGeometry(loc, route)
            when {
                analysis.isOffRoute -> geometryFactor = 1.35 // Wider view to locate road alignment
                analysis.hasIntersectionOrBranch -> geometryFactor = 0.74 // Tighter detail around junctions
                analysis.maxLocalDeflectionDeg >= 28.0 -> geometryFactor = 0.76 // Tighter view on curves
                analysis.maxLocalDeflectionDeg <= 8.0 && speed >= 11.0f -> geometryFactor = 1.15 // Straight highway overview
            }
        }

        var target = speedBaseRange * geometryFactor

        // 3. GPS accuracy / confidence safety expansion (never over-zoom into a large uncertainty circle)
        val accuracy = context.gpsAccuracyMetres
        if (accuracy.isFinite() && accuracy > 25f) {
            val accuracyFloor = (accuracy.toDouble() * 7.0).coerceAtMost(1_800.0)
            target = maxOf(target, accuracyFloor)
        }

        val clampedTarget = target.coerceIn(MIN_SMART_RANGE_METRES, MAX_SMART_RANGE_METRES)
        return applySmoothDamping(
            previousMetres = context.previousRangeMetres,
            targetMetres = clampedTarget,
            isStationary = isStationary
        )
    }

    fun applySmoothDamping(
        previousMetres: Double?,
        targetMetres: Double,
        isStationary: Boolean
    ): Double {
        val prev = previousMetres?.takeIf { it.isFinite() && it > 0.0 } ?: return targetMetres
        val relativeDiff = abs(targetMetres - prev) / prev.coerceAtLeast(1.0)
        // Stay rock-steady while stationary unless a major accuracy/context shift occurs
        if (isStationary && relativeDiff < 0.22) {
            return prev
        }
        // Small deadband prevents continuous micro-zoom breathing
        if (relativeDiff < 0.08) {
            return prev
        }
        val alpha = if (targetMetres > prev) 0.28 else 0.22
        return (prev + (targetMetres - prev) * alpha).coerceIn(MIN_SMART_RANGE_METRES, MAX_SMART_RANGE_METRES)
    }

    private data class RoadGeometrySummary(
        val isOffRoute: Boolean,
        val hasIntersectionOrBranch: Boolean,
        val maxLocalDeflectionDeg: Double
    )

    private fun analyzeLocalRoadGeometry(
        location: GeoPoint,
        routeData: RouteData
    ): RoadGeometrySummary {
        var nearestDistM = Double.MAX_VALUE
        var nearbyAlignmentCount = 0
        var maxDeflectionDeg = 0.0

        for (alignment in routeData.alignments) {
            val pts = alignment.points
            if (pts.size < 2) continue
            var minAlignDist = Double.MAX_VALUE
            var bestIdx = -1
            // Coarse step for very long alignments to stay O(1)-fast on UI frames
            val stride = if (pts.size > 600) pts.size / 300 else 1
            var i = 0
            while (i < pts.size) {
                val d = com.example.geo.GeoUtils.distanceMetres(location, pts[i])
                if (d < minAlignDist) {
                    minAlignDist = d
                    bestIdx = i
                }
                i += stride
            }
            if (minAlignDist < nearestDistM) {
                nearestDistM = minAlignDist
            }
            if (minAlignDist <= 180.0) {
                nearbyAlignmentCount++
                if (bestIdx >= 0) {
                    val startIdx = (bestIdx - 3).coerceAtLeast(0)
                    val endIdx = (bestIdx + 4).coerceAtMost(pts.size - 1)
                    var prevBearing: Double? = null
                    for (j in startIdx until endIdx) {
                        val segLen = com.example.geo.GeoUtils.distanceMetres(pts[j], pts[j + 1])
                        if (segLen < 4.0) continue
                        val brg = com.example.geo.GeoUtils.bearingDegrees(pts[j], pts[j + 1])
                        if (prevBearing != null) {
                            var diff = abs(brg - prevBearing) % 360.0
                            if (diff > 180.0) diff = 360.0 - diff
                            if (diff > maxDeflectionDeg) maxDeflectionDeg = diff
                        }
                        prevBearing = brg
                    }
                }
            }
        }

        return RoadGeometrySummary(
            isOffRoute = nearestDistM > 350.0,
            hasIntersectionOrBranch = nearbyAlignmentCount >= 2,
            maxLocalDeflectionDeg = maxDeflectionDeg
        )
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

/**
 * Clean, subtle indicator shown when map tiles are unavailable or incomplete offline,
 * preventing misleading blank tiles from looking like normal empty terrain.
 */
@Composable
fun MiniMapOfflineCoverageBadge(
    isDark: Boolean,
    hasPartialCoverage: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xD90D111A) else Color(0xEBFFFFFF)
    val fg = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    val label = if (hasPartialCoverage) "Partial offline map" else "Offline • Vector alignment"
    Surface(
        shape = CircleShape,
        color = bg,
        shadowElevation = 0.dp,
        modifier = modifier.testTag("minimap_offline_coverage_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = label,
                color = fg,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Shared compact Material 3 card displaying active KMZ/KML offline tile synchronization state,
 * progress bar, reused tile count, and accessible Pause / Resume / Retry action controls.
 */
@Composable
fun OfflineMapSyncStatusStrip(
    syncState: OfflineMapSyncState,
    onPauseSync: () -> Unit,
    onResumeSync: () -> Unit,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val statusColor = when (syncState.status) {
        OfflineSyncStatus.COMPLETE -> c.success
        OfflineSyncStatus.SYNCING, OfflineSyncStatus.PREPARING -> c.accent
        OfflineSyncStatus.PAUSED, OfflineSyncStatus.WAITING_FOR_WIFI -> c.warning
        OfflineSyncStatus.WAITING_FOR_NETWORK, OfflineSyncStatus.PARTIAL_ERROR -> c.error
        OfflineSyncStatus.IDLE -> c.textMuted
    }
    val statusIcon = when (syncState.status) {
        OfflineSyncStatus.COMPLETE -> Icons.Default.CloudDone
        OfflineSyncStatus.SYNCING, OfflineSyncStatus.PREPARING -> Icons.Default.CloudDownload
        OfflineSyncStatus.WAITING_FOR_WIFI -> Icons.Default.Wifi
        OfflineSyncStatus.WAITING_FOR_NETWORK -> Icons.Default.CloudOff
        else -> Icons.Default.CloudDownload
    }
    val statusHeadline = when (syncState.status) {
        OfflineSyncStatus.IDLE -> "Corridor Cache Standby"
        OfflineSyncStatus.PREPARING -> "Scanning Route Corridor…"
        OfflineSyncStatus.SYNCING -> "Preloading Corridor (${syncState.cachedTiles}/${syncState.totalTiles})"
        OfflineSyncStatus.PAUSED -> "Corridor Sync Paused (${syncState.cachedTiles}/${syncState.totalTiles})"
        OfflineSyncStatus.WAITING_FOR_WIFI -> "Waiting for Wi-Fi (${syncState.cachedTiles}/${syncState.totalTiles})"
        OfflineSyncStatus.WAITING_FOR_NETWORK -> "Offline • Cached ${syncState.cachedTiles}/${syncState.totalTiles}"
        OfflineSyncStatus.COMPLETE -> "Offline Corridor Ready (${syncState.totalTiles} tiles)"
        OfflineSyncStatus.PARTIAL_ERROR -> "Partial Offline Cache (${syncState.cachedTiles}/${syncState.totalTiles})"
    }

    val detailsLine = buildString {
        if (syncState.totalTiles > 0) {
            append("${(syncState.progressFraction * 100f).toInt()}% • ${syncState.formattedBytes}")
            if (syncState.reusedTiles > 0) {
                append(" • ${syncState.reusedTiles} reused")
            }
        } else if (!syncState.autoPreloadEnabled) {
            append("Auto-preload paused in settings")
        } else {
            append("Activate a KMZ/KML route to cache corridor tiles")
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.secondarySurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_sync_status_strip")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusHeadline,
                            tint = statusColor,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusHeadline,
                            color = c.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = detailsLine,
                            color = c.textSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when (syncState.status) {
                    OfflineSyncStatus.SYNCING, OfflineSyncStatus.PREPARING -> {
                        IconButton(
                            onClick = onPauseSync,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("offline_sync_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause offline map preload",
                                tint = c.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    OfflineSyncStatus.PAUSED -> {
                        IconButton(
                            onClick = onResumeSync,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("offline_sync_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume offline map preload",
                                tint = c.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    OfflineSyncStatus.WAITING_FOR_WIFI,
                    OfflineSyncStatus.WAITING_FOR_NETWORK,
                    OfflineSyncStatus.PARTIAL_ERROR,
                    OfflineSyncStatus.COMPLETE -> {
                        if (syncState.totalTiles > 0) {
                            IconButton(
                                onClick = onRetrySync,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("offline_sync_action_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh offline map preload",
                                    tint = statusColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    OfflineSyncStatus.IDLE -> Unit
                }
            }

            if (syncState.totalTiles > 0 && syncState.status != OfflineSyncStatus.COMPLETE) {
                LinearProgressIndicator(
                    progress = { syncState.progressFraction },
                    color = statusColor,
                    trackColor = c.surface,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

