package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.geo.GeoUtils
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.MiniMapCornerShape
import com.example.model.MiniMapDirectionStyle
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.MiniMapStyleConfig
import com.example.model.RouteData
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Shared high-performance Android Canvas renderer for the Dynamic Mini Map overlay.
 *
 * Used identically by:
 * - Captured photo output ([OverlayRenderer])
 * - Live & post-processed video frames ([RealtimeVideoOverlayManager], [VideoOverlayProcessor])
 * - Camera viewfinder preview ([com.example.ui.camera.MiniMapPreview])
 * - Settings live preview ([com.example.ui.settings.MiniMapSettingsSection])
 *
 * Guarantees genuine KMZ/KML alignment geometry, real GPS positioning, retained last valid
 * position on temporary GPS loss, and zero fake road curves.
 */
object MiniMapRenderer {

    private data class TileCacheKey(
        val latQ: Long,
        val lonQ: Long,
        val zoom: Int,
        val sizePx: Int,
        val darkTheme: Boolean,
        val mapType: String
    )

    @Volatile
    private var cachedTileKey: TileCacheKey? = null
    @Volatile
    private var cachedTileBitmap: Bitmap? = null

    fun parseColorSafe(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }

    fun resolveCornerRadiusPx(config: MiniMapStyleConfig, dpScale: Float): Float {
        return config.resolveCornerRadiusDp().toFloat() * dpScale
    }

    /**
     * Computes a suitable Web Mercator zoom level so the current GPS coordinate
     * and the nearest imported KMZ/KML alignment remain visible together.
     */
    fun computeAutoZoom(center: GeoPoint?, routeData: RouteData?): Float {
        if (center == null) return 15.0f
        if (routeData == null || routeData.alignments.isEmpty()) return 15.8f

        var minDistMeters = Double.MAX_VALUE
        for (align in routeData.alignments) {
            val pts = align.points
            if (pts.isEmpty()) continue
            val step = max(1, pts.size / 120)
            var idx = 0
            while (idx < pts.size) {
                val d = GeoUtils.distanceMetres(center, pts[idx])
                if (d < minDistMeters) minDistMeters = d
                idx += step
            }
            val lastD = GeoUtils.distanceMetres(center, pts.last())
            if (lastD < minDistMeters) minDistMeters = lastD
        }

        return when {
            minDistMeters == Double.MAX_VALUE -> 15.8f
            minDistMeters <= 90.0 -> 16.0f
            minDistMeters <= 220.0 -> 15.3f
            minDistMeters <= 480.0 -> 14.5f
            minDistMeters <= 1100.0 -> 13.6f
            minDistMeters <= 2600.0 -> 12.6f
            else -> {
                // Keep reasonable regional scale without zooming out to continent level
                val ratio = (2600.0 / minDistMeters).coerceIn(0.25, 1.0)
                (12.6 + ln(ratio) / ln(2.0)).toFloat().coerceIn(11.5f, 16.0f)
            }
        }
    }

    /**
     * Resolves reliable travel/road direction in degrees (0 = North, 90 = East).
     * Prefers live GPS bearing, then nearest KMZ/KML alignment segment tangent bearing.
     */
    fun resolveEffectiveBearing(
        center: GeoPoint?,
        gpsBearing: Float?,
        routeData: RouteData?
    ): Float? {
        if (gpsBearing != null && !gpsBearing.isNaN()) {
            return ((gpsBearing % 360f) + 360f) % 360f
        }
        if (center == null || routeData == null || routeData.alignments.isEmpty()) {
            return null
        }

        var bestDist = Double.MAX_VALUE
        var bestBearing: Float? = null
        for (align in routeData.alignments) {
            val pts = align.points
            if (pts.size < 2) continue
            for (i in 0 until pts.size - 1) {
                val d = GeoUtils.distanceMetres(center, pts[i])
                if (d < bestDist) {
                    bestDist = d
                    bestBearing = computeBearingDeg(pts[i], pts[i + 1])
                }
            }
        }
        return if (bestDist <= 2000.0) bestBearing else null
    }

    fun computeBearingDeg(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }

    fun resolveMapRotationDegrees(
        mode: MiniMapOrientationMode,
        effectiveBearing: Float?
    ): Float {
        return when (mode) {
            MiniMapOrientationMode.NORTH_UP -> 0f
            MiniMapOrientationMode.DIRECTION_UP -> -(effectiveBearing ?: 0f)
            MiniMapOrientationMode.AUTO -> if (effectiveBearing != null) -effectiveBearing else 0f
        }
    }

    fun drawMiniMap(
        context: Context?,
        canvas: Canvas,
        bounds: RectF,
        config: MiniMapStyleConfig,
        location: GeoPoint?,
        isGpsRetained: Boolean,
        bearing: Float?,
        routeData: RouteData?,
        chainageLabel: String?,
        sideLabel: String?,
        dpScale: Float
    ) {
        if (!config.enabled || config.opacity <= 0.01f) return
        if (bounds.width() <= 2f || bounds.height() <= 2f) return

        val cornerRadiusPx = resolveCornerRadiusPx(config, dpScale)
        val shadowPad = if (config.shadowEnabled) 8f * dpScale else 0f

        val layerSaveCount = if (config.opacity < 0.999f) {
            canvas.saveLayerAlpha(
                bounds.left - shadowPad,
                bounds.top - shadowPad,
                bounds.right + shadowPad,
                bounds.bottom + shadowPad,
                (config.opacity.coerceIn(0f, 1f) * 255f).toInt()
            )
        } else {
            canvas.save()
        }

        // 1. Optional subtle drop shadow (layered translucent passes for hardware/software safety)
        if (config.shadowEnabled && config.backgroundAlpha > 0.08f) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            for (pass in 1..3) {
                val expand = pass * 1.6f * dpScale
                val dy = pass * 1.2f * dpScale
                shadowPaint.color = Color.argb((42 / pass), 0, 0, 0)
                val shadowRect = RectF(
                    bounds.left - expand * 0.5f,
                    bounds.top + dy - expand * 0.25f,
                    bounds.right + expand * 0.5f,
                    bounds.bottom + dy + expand * 0.6f
                )
                canvas.drawRoundRect(shadowRect, cornerRadiusPx + expand * 0.3f, cornerRadiusPx + expand * 0.3f, shadowPaint)
            }
        }

        // 2. Style base colors & brightness/contrast filter
        val isDarkStyle = config.mapStyle == MiniMapStyle.DARK || config.mapStyle == MiniMapStyle.SATELLITE
        val isLightStyle = config.mapStyle == MiniMapStyle.LIGHT || config.mapStyle == MiniMapStyle.STANDARD
        val baseBgRgb = when (config.mapStyle) {
            MiniMapStyle.STANDARD -> Color.rgb(235, 241, 248)
            MiniMapStyle.SATELLITE -> Color.rgb(16, 26, 38)
            MiniMapStyle.DARK -> Color.rgb(15, 23, 42)
            MiniMapStyle.LIGHT -> Color.rgb(248, 250, 252)
        }
        val bgAlphaInt = (config.backgroundAlpha.coerceIn(0f, 1f) * 255f).toInt()
        val toneFilter = buildBrightnessContrastFilter(config.brightness, config.contrast)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlphaInt, Color.red(baseBgRgb), Color.green(baseBgRgb), Color.blue(baseBgRgb))
            style = Paint.Style.FILL
            if (toneFilter != null) colorFilter = toneFilter
        }
        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // 3. Clip inner map viewport
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val centerGeo = location ?: routeData?.bounds?.center
        val effectiveZoom = if (config.autoZoom) {
            computeAutoZoom(centerGeo, routeData)
        } else {
            config.zoomLevel.coerceIn(11.0f, 18.5f)
        }
        val baseZoom = floor(effectiveZoom).toInt().coerceIn(10, 18)
        val zoomScale = 2.0.pow((effectiveZoom - baseZoom).toDouble()).toFloat().coerceIn(0.5f, 2.5f)
        val effectiveBearing = resolveEffectiveBearing(centerGeo, bearing, routeData)
        val mapRotationDeg = resolveMapRotationDegrees(config.orientationMode, effectiveBearing)

        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val w = bounds.width()
        val h = bounds.height()

        // 4. Real map tiles background when available
        var hasOsmTiles = false
        if (context != null && centerGeo != null && config.backgroundAlpha > 0.04f) {
            val diagPx = ceil(hypot(w.toDouble(), h.toDouble()) / zoomScale).toInt().coerceIn(32, 768)
            val osmMapType = if (config.mapStyle == MiniMapStyle.SATELLITE) "satellite" else "standard"
            val osmBitmap = getOrRenderCachedTileBitmap(
                context = context,
                center = centerGeo,
                zoom = baseZoom,
                sizePx = diagPx,
                darkTheme = config.mapStyle == MiniMapStyle.DARK,
                mapType = osmMapType
            )
            if (osmBitmap != null) {
                hasOsmTiles = true
                val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = bgAlphaInt
                    if (toneFilter != null) colorFilter = toneFilter
                }
                canvas.save()
                canvas.rotate(mapRotationDeg, cx, cy)
                canvas.scale(zoomScale, zoomScale, cx, cy)
                canvas.drawBitmap(
                    osmBitmap,
                    cx - osmBitmap.width / 2f,
                    cy - osmBitmap.height / 2f,
                    tilePaint
                )
                canvas.restore()
            }
        }

        // 5. Coordinate grid fallback when tiles are unavailable (never fake road curves)
        if (!hasOsmTiles && config.backgroundAlpha > 0.04f) {
            val gridAlpha = ((if (isDarkStyle) 42 else 55) * config.backgroundAlpha).toInt().coerceIn(8, 120)
            val gridColor = if (isDarkStyle) Color.argb(gridAlpha, 226, 232, 240) else Color.argb(gridAlpha, 100, 116, 139)
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gridColor
                strokeWidth = 0.9f * dpScale
            }
            val gridStep = (min(w, h) / 4f).coerceAtLeast(12f)
            canvas.save()
            canvas.rotate(mapRotationDeg, cx, cy)
            var gx = cx - diagExtent(w, h)
            val endExtent = diagExtent(w, h)
            while (gx <= cx + endExtent) {
                canvas.drawLine(gx, cy - endExtent, gx, cy + endExtent, gridPaint)
                gx += gridStep
            }
            var gy = cy - endExtent
            while (gy <= cy + endExtent) {
                canvas.drawLine(cx - endExtent, gy, cx + endExtent, gy, gridPaint)
                gy += gridStep
            }
            canvas.restore()
        }

        // 6. Real KMZ/KML Road Alignment, Direction Chevrons, and Nearby Chainage Markers
        if (centerGeo != null) {
            val projWidth = w / zoomScale
            val projHeight = h / zoomScale
            val halfProjW = projWidth / 2f
            val halfProjH = projHeight / 2f
            val expandedLeft = bounds.left - hypot(w, h) * 0.65f
            val expandedRight = bounds.right + hypot(w, h) * 0.65f
            val expandedTop = bounds.top - hypot(w, h) * 0.65f
            val expandedBottom = bounds.bottom + hypot(w, h) * 0.65f

            fun toScreen(pt: GeoPoint): Pair<Float, Float> {
                val (rawSx, rawSy) = OsmTileManager.geoToScreen(
                    pt = pt,
                    center = centerGeo,
                    widthPx = projWidth,
                    heightPx = projHeight,
                    zoom = baseZoom
                )
                return Pair(
                    cx + (rawSx - halfProjW) * zoomScale,
                    cy + (rawSy - halfProjH) * zoomScale
                )
            }

            canvas.save()
            canvas.rotate(mapRotationDeg, cx, cy)

            if (config.showRoadAlignment && routeData != null && routeData.alignments.isNotEmpty()) {
                val routeColor = parseColorSafe(config.routeColorHex, Color.parseColor("#38BDF8"))
                val casingColor = if (isLightStyle && !hasOsmTiles) {
                    Color.argb(230, 15, 23, 42)
                } else {
                    Color.argb(225, 9, 14, 24)
                }

                val roadCasingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = casingColor
                    strokeWidth = (5.4f * dpScale).coerceIn(3.0f, 18f)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val roadCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = routeColor
                    strokeWidth = (3.1f * dpScale).coerceIn(1.8f, 11f)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val roadDirPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    strokeWidth = (1.5f * dpScale).coerceIn(1.0f, 4.5f)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                val dirArrowLocations = mutableListOf<Triple<Float, Float, Float>>()
                var distSinceDirArrow = 999f
                val arrowSpacing = 44f * dpScale

                for (align in routeData.alignments) {
                    val pts = align.points
                    if (pts.size < 2) continue
                    val path = Path()
                    var prevInPath = false
                    var lastX = 0f
                    var lastY = 0f

                    for (i in pts.indices) {
                        val (sx, sy) = toScreen(pts[i])
                        val inside = sx in expandedLeft..expandedRight && sy in expandedTop..expandedBottom
                        if (i == 0) {
                            lastX = sx
                            lastY = sy
                            if (inside) {
                                path.moveTo(sx, sy)
                                prevInPath = true
                            }
                            continue
                        }

                        val prevInside = lastX in expandedLeft..expandedRight && lastY in expandedTop..expandedBottom
                        if (inside || prevInside) {
                            if (!prevInPath) {
                                path.moveTo(lastX, lastY)
                            }
                            path.lineTo(sx, sy)
                            prevInPath = true

                            val segLen = hypot(sx - lastX, sy - lastY)
                            distSinceDirArrow += segLen
                            if (distSinceDirArrow >= arrowSpacing && segLen > 4f) {
                                val midX = (lastX + sx) * 0.5f
                                val midY = (lastY + sy) * 0.5f
                                if (midX in bounds.left..bounds.right && midY in bounds.top..bounds.bottom) {
                                    val segAngleDeg = Math.toDegrees(atan2((sy - lastY).toDouble(), (sx - lastX).toDouble())).toFloat()
                                    dirArrowLocations.add(Triple(midX, midY, segAngleDeg))
                                    distSinceDirArrow = 0f
                                }
                            }
                        } else {
                            prevInPath = false
                        }
                        lastX = sx
                        lastY = sy
                    }

                    if (!path.isEmpty) {
                        canvas.drawPath(path, roadCasingPaint)
                        canvas.drawPath(path, roadCenterPaint)
                    }
                }

                // Draw road direction chevrons along visible alignment segments
                val tickSize = 2.8f * dpScale
                for ((ax, ay, angleDeg) in dirArrowLocations) {
                    canvas.save()
                    canvas.translate(ax, ay)
                    canvas.rotate(angleDeg)
                    canvas.drawLine(-tickSize, -tickSize * 0.7f, tickSize * 0.5f, 0f, roadDirPaint)
                    canvas.drawLine(-tickSize, tickSize * 0.7f, tickSize * 0.5f, 0f, roadDirPaint)
                    canvas.restore()
                }
            }

            // Nearby chainage markers along the imported route
            if (config.showNearbyChainageMarkers && routeData != null && routeData.markers.isNotEmpty()) {
                val dotOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(230, 15, 23, 42)
                    style = Paint.Style.FILL
                }
                val dotInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FBBF24")
                    style = Paint.Style.FILL
                }
                val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDarkStyle) Color.WHITE else Color.parseColor("#0F172A")
                    textSize = (7.2f * dpScale).coerceIn(7f, 22f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val markerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDarkStyle) Color.argb(190, 15, 23, 42) else Color.argb(215, 255, 255, 255)
                    style = Paint.Style.FILL
                }

                val labeledPoints = mutableListOf<Pair<Float, Float>>()
                val minLabelSpacing = 38f * dpScale
                val showTextLabels = min(w, h) >= 58f * dpScale

                for (m in routeData.markers) {
                    val (sx, sy) = toScreen(m.point)
                    if (sx in (bounds.left + 4f)..(bounds.right - 4f) &&
                        sy in (bounds.top + 4f)..(bounds.bottom - 4f)
                    ) {
                        canvas.drawCircle(sx, sy, 3.2f * dpScale, dotOuterPaint)
                        canvas.drawCircle(sx, sy, 2.0f * dpScale, dotInnerPaint)

                        if (showTextLabels &&
                            hypot(sx - cx, sy - cy) > 20f * dpScale &&
                            labeledPoints.none { hypot(it.first - sx, it.second - sy) < minLabelSpacing }
                        ) {
                            labeledPoints.add(Pair(sx, sy))
                            val label = m.chainage.format()
                            val tw = markerTextPaint.measureText(label)
                            val padH = 3f * dpScale
                            val th = markerTextPaint.textSize
                            val rect = RectF(
                                sx + 4f * dpScale,
                                sy - th * 0.65f,
                                sx + 4f * dpScale + tw + padH * 2f,
                                sy + th * 0.55f
                            )
                            if (rect.right <= bounds.right - 3f * dpScale) {
                                canvas.save()
                                // Keep label upright regardless of map rotation
                                canvas.rotate(-mapRotationDeg, sx, sy)
                                canvas.drawRoundRect(rect, 3f * dpScale, 3f * dpScale, markerBgPaint)
                                canvas.drawText(label, rect.left + padH, rect.bottom - 2f * dpScale, markerTextPaint)
                                canvas.restore()
                            }
                        }
                    }
                }
            }

            canvas.restore()
        }

        // 7. Current GPS Position Marker & Direction Indicator (only when real/retained GPS exists)
        if (location != null) {
            val gpsColor = parseColorSafe(config.gpsMarkerColorHex, Color.parseColor("#EF4444"))

            if (config.showDirectionArrow && effectiveBearing != null) {
                val screenAngle = effectiveBearing + mapRotationDeg
                drawDirectionIndicator(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    angleDeg = screenAngle,
                    directionStyle = config.directionMarkerStyle,
                    accentColor = parseColorSafe(config.routeColorHex, Color.parseColor("#38BDF8")),
                    dpScale = dpScale
                )
            }

            if (config.showCurrentPositionMarker) {
                val outerRadius = 11.5f * dpScale
                val pinRadius = 5.4f * dpScale

                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isGpsRetained) {
                        Color.argb(95, 245, 158, 11)
                    } else {
                        Color.argb(72, Color.red(gpsColor), Color.green(gpsColor), Color.blue(gpsColor))
                    }
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, outerRadius, haloPaint)

                if (isGpsRetained) {
                    val retainedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F59E0B")
                        style = Paint.Style.STROKE
                        strokeWidth = 1.4f * dpScale
                        pathEffect = DashPathEffect(floatArrayOf(3f * dpScale, 2.5f * dpScale), 0f)
                    }
                    canvas.drawCircle(cx, cy, outerRadius, retainedRingPaint)
                }

                val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, pinRadius + 1.6f * dpScale, pinBorderPaint)

                val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gpsColor
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, pinRadius, pinPaint)

                val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, pinRadius * 0.38f, innerDotPaint)
            }
        } else {
            // Missing GPS graceful indicator (never invents fake coordinates)
            val statusText = if (routeData != null && routeData.alignments.isNotEmpty()) {
                "Waiting GPS"
            } else {
                "No GPS / KMZ"
            }
            drawCornerBadge(
                canvas = canvas,
                text = statusText,
                x = cx,
                y = bounds.bottom - 6f * dpScale,
                alignCenter = true,
                alignRight = false,
                bgColor = Color.argb(200, 15, 23, 42),
                textColor = Color.parseColor("#FBBF24"),
                textSizePx = (8.0f * dpScale).coerceIn(7.5f, 22f),
                dpScale = dpScale
            )
        }

        // 8. Upright Information Pills (Chainage Label, LHS/RHS Side, Last Valid GPS Badge)
        val badgeTextSize = (min(w, h) * 0.115f).coerceIn(7.0f * dpScale, 12.5f * dpScale)
        val pillBg = if (isDarkStyle) Color.argb(215, 10, 15, 28) else Color.argb(230, 255, 255, 255)
        val pillText = if (isDarkStyle) Color.WHITE else Color.parseColor("#0F172A")

        if (isGpsRetained && location != null) {
            drawCornerBadge(
                canvas = canvas,
                text = "LAST FIX",
                x = bounds.left + 4.5f * dpScale,
                y = bounds.top + 4.5f * dpScale,
                alignCenter = false,
                alignRight = false,
                bgColor = Color.argb(220, 120, 53, 15),
                textColor = Color.parseColor("#FDE68A"),
                textSizePx = badgeTextSize * 0.85f,
                dpScale = dpScale,
                isTop = true
            )
        }

        val cleanSide = sideLabel?.trim()?.uppercase()?.takeIf { it == "LHS" || it == "RHS" }
        if (config.showSideIndicator && cleanSide != null) {
            val sideColor = if (cleanSide == "LHS") Color.parseColor("#38BDF8") else Color.parseColor("#FACC15")
            drawCornerBadge(
                canvas = canvas,
                text = cleanSide,
                x = bounds.right - 4.5f * dpScale,
                y = bounds.top + 4.5f * dpScale,
                alignCenter = false,
                alignRight = true,
                bgColor = Color.argb(220, 15, 23, 42),
                textColor = sideColor,
                textSizePx = badgeTextSize * 0.92f,
                dpScale = dpScale,
                isTop = true
            )
        }

        val cleanChainage = chainageLabel?.trim()?.takeIf {
            it.isNotBlank() &&
                !it.equals("N/A", ignoreCase = true) &&
                !it.equals("None", ignoreCase = true) &&
                it != "--" &&
                it != "--+---"
        }
        if (config.showCurrentChainageLabel && cleanChainage != null) {
            drawCornerBadge(
                canvas = canvas,
                text = cleanChainage,
                x = cx,
                y = bounds.bottom - 4.5f * dpScale,
                alignCenter = true,
                alignRight = false,
                bgColor = pillBg,
                textColor = pillText,
                textSizePx = badgeTextSize,
                dpScale = dpScale,
                isTop = false
            )
        }

        canvas.restore() // Remove inner clip

        // 9. Border outline
        if (config.borderEnabled && config.borderThicknessDp > 0f) {
            val defaultBorder = if (isDarkStyle) Color.parseColor("#64748B") else Color.parseColor("#94A3B8")
            val borderColor = parseColorSafe(config.borderColorHex, defaultBorder)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = (config.borderThicknessDp * dpScale).coerceIn(1f, 16f)
            }
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, borderPaint)
        }

        canvas.restoreToCount(layerSaveCount)
    }

    private fun diagExtent(w: Float, h: Float): Float = hypot(w, h) * 0.65f

    private fun buildBrightnessContrastFilter(brightness: Float, contrast: Float): ColorMatrixColorFilter? {
        val c = contrast.coerceIn(0.4f, 1.8f)
        val bOffset = (brightness.coerceIn(0.3f, 1.8f) - 1f) * 255f
        if (kotlin.math.abs(c - 1f) < 0.01f && kotlin.math.abs(bOffset) < 1f) {
            return null
        }
        val t = 128f * (1f - c) + bOffset
        val cm = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return ColorMatrixColorFilter(cm)
    }

    private fun getOrRenderCachedTileBitmap(
        context: Context,
        center: GeoPoint,
        zoom: Int,
        sizePx: Int,
        darkTheme: Boolean,
        mapType: String
    ): Bitmap? {
        val key = TileCacheKey(
            latQ = (center.latitude * 100000.0).toLong(),
            lonQ = (center.longitude * 100000.0).toLong(),
            zoom = zoom,
            sizePx = sizePx,
            darkTheme = darkTheme,
            mapType = mapType
        )
        val existingKey = cachedTileKey
        val existingBmp = cachedTileBitmap
        if (existingKey == key && existingBmp != null && !existingBmp.isRecycled) {
            return existingBmp
        }
        val rendered = try {
            OsmTileManager.renderMapBitmap(
                context = context,
                center = center,
                widthPx = sizePx,
                heightPx = sizePx,
                zoom = zoom,
                darkTheme = darkTheme,
                mapType = mapType
            )
        } catch (_: Throwable) {
            null
        }
        if (rendered != null) {
            cachedTileKey = key
            cachedTileBitmap = rendered
        }
        return rendered
    }

    private fun drawDirectionIndicator(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angleDeg: Float,
        directionStyle: MiniMapDirectionStyle,
        accentColor: Int,
        dpScale: Float
    ) {
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)
        when (directionStyle) {
            MiniMapDirectionStyle.ARROW -> {
                val arrowPath = Path().apply {
                    moveTo(cx, cy - 18.5f * dpScale)
                    lineTo(cx - 5.0f * dpScale, cy - 10.0f * dpScale)
                    lineTo(cx, cy - 12.2f * dpScale)
                    lineTo(cx + 5.0f * dpScale, cy - 10.0f * dpScale)
                    close()
                }
                val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 15, 23, 42)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.8f * dpScale
                    strokeJoin = Paint.Join.ROUND
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    style = Paint.Style.FILL
                }
                canvas.drawPath(arrowPath, outlinePaint)
                canvas.drawPath(arrowPath, fillPaint)
            }
            MiniMapDirectionStyle.CHEVRON -> {
                val chevPath = Path().apply {
                    moveTo(cx - 6.0f * dpScale, cy - 11.0f * dpScale)
                    lineTo(cx, cy - 17.5f * dpScale)
                    lineTo(cx + 6.0f * dpScale, cy - 11.0f * dpScale)
                }
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 15, 23, 42)
                    style = Paint.Style.STROKE
                    strokeWidth = 4.2f * dpScale
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2.4f * dpScale
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(chevPath, bgPaint)
                canvas.drawPath(chevPath, fgPaint)
            }
            MiniMapDirectionStyle.CONE -> {
                val coneRadius = 26f * dpScale
                val coneRect = RectF(cx - coneRadius, cy - coneRadius, cx + coneRadius, cy + coneRadius)
                val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(95, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    style = Paint.Style.FILL
                }
                canvas.drawArc(coneRect, -114f, 48f, true, conePaint)
                val tipPath = Path().apply {
                    moveTo(cx, cy - 16.5f * dpScale)
                    lineTo(cx - 3.6f * dpScale, cy - 10.5f * dpScale)
                    lineTo(cx + 3.6f * dpScale, cy - 10.5f * dpScale)
                    close()
                }
                val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    style = Paint.Style.FILL
                }
                canvas.drawPath(tipPath, tipPaint)
            }
        }
        canvas.restore()
    }

    private fun drawCornerBadge(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        alignCenter: Boolean,
        alignRight: Boolean,
        bgColor: Int,
        textColor: Int,
        textSizePx: Float,
        dpScale: Float,
        isTop: Boolean = false
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = textSizePx
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val tw = textPaint.measureText(text)
        val padX = 4.5f * dpScale
        val padY = 2.2f * dpScale
        val pillW = tw + padX * 2f
        val pillH = textSizePx + padY * 2f

        val left = when {
            alignCenter -> x - pillW / 2f
            alignRight -> x - pillW
            else -> x
        }
        val top = if (isTop) y else y - pillH
        val rect = RectF(left, top, left + pillW, top + pillH)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 4f * dpScale, 4f * dpScale, bgPaint)

        val fm = textPaint.fontMetrics
        val textBaseY = rect.centerY() - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(text, rect.left + padX, textBaseY, textPaint)
    }
}
