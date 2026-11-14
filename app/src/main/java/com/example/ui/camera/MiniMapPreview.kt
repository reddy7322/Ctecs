package com.example.ui.camera

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.MiniMapStyleConfig
import com.example.model.RouteData
import com.example.overlay.MiniMapRenderer
import java.util.Locale

@Composable
fun MiniMapPreview(
    location: GeoPoint?,
    bearing: Float?,
    routeData: RouteData?,
    highwayTag: String,
    modifier: Modifier = Modifier,
    gpsAccuracy: Float? = null,
    showGpsBadge: Boolean = false,
    showProjectTag: Boolean = false,
    overrideIsDark: Boolean? = null,
    mapType: String = "standard",
    mapThemePreference: String = "system",
    showAttribution: Boolean = false,
    transparentBackground: Boolean = false,
    miniMapConfig: MiniMapStyleConfig? = null,
    chainageLabel: String? = null,
    sideLabel: String? = null,
    isGpsRetained: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsState()

    val appliedModifier = if (modifier == Modifier) {
        if (miniMapConfig != null) {
            Modifier.size(
                width = miniMapConfig.resolveWidthDp().dp,
                height = miniMapConfig.resolveHeightDp().dp
            )
        } else {
            Modifier.size(width = 68.dp, height = 68.dp)
        }
    } else {
        modifier
    }

    // Configurable Mini Map Pipeline (pixel-matched with Photo & Video Output Renderer)
    if (miniMapConfig != null) {
        val cornerRadius = miniMapConfig.resolveCornerRadiusDp().dp
        val shape = RoundedCornerShape(cornerRadius)
        Box(
            modifier = appliedModifier.clip(shape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Observe tile updates cleanly
                @Suppress("UNUSED_VARIABLE")
                val triggerTileRedraw = tileUpdate
                val w = size.width
                val h = size.height
                if (w > 2f && h > 2f) {
                    val dpScale = density.density.coerceAtLeast(1f)
                    drawIntoCanvas { composeCanvas ->
                        MiniMapRenderer.drawMiniMap(
                            context = context,
                            canvas = composeCanvas.nativeCanvas,
                            bounds = RectF(0f, 0f, w, h),
                            config = miniMapConfig,
                            location = location,
                            isGpsRetained = isGpsRetained,
                            bearing = bearing,
                            routeData = routeData,
                            chainageLabel = chainageLabel,
                            sideLabel = sideLabel,
                            dpScale = dpScale
                        )
                    }
                }
            }
        }
        return
    }

    // Legacy / RoutesScreen / PhotosScreen compact inset mode (real geometry only)
    val isAppDark = com.example.ui.theme.isAppDarkTheme()
    val isDark = overrideIsDark ?: com.example.ui.map.MapThemeConfig.resolveIsDark(mapThemePreference, isAppDark)
    val mapTheme = com.example.ui.map.rememberMapThemeColors(isDark)

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = appliedModifier
            .clip(shape)
            .background(if (transparentBackground) Color.Transparent else mapTheme.containerBackground)
            .border(
                width = 0.8.dp,
                color = if (transparentBackground) Color.White.copy(alpha = 0.38f) else mapTheme.containerBorder,
                shape = shape
            )
    ) {
        val centerGeo = location ?: routeData?.bounds?.center

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val triggerTileRedraw = tileUpdate
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            var hasOsmTiles = false
            if (centerGeo != null && !transparentBackground) {
                val osmBmp = OsmTileManager.renderMapBitmap(
                    context = context,
                    center = centerGeo,
                    widthPx = w.toInt().coerceAtLeast(1),
                    heightPx = h.toInt().coerceAtLeast(1),
                    zoom = 15,
                    darkTheme = mapTheme.isDark,
                    mapType = mapType
                )
                if (osmBmp != null) {
                    drawImage(
                        image = osmBmp.asImageBitmap(),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.toInt(), h.toInt())
                    )
                    if (mapTheme.isDark) {
                        drawRect(color = Color(0x22000000))
                    }
                    hasOsmTiles = true
                }
            }

            if (!hasOsmTiles) {
                if (!transparentBackground) {
                    drawRect(color = mapTheme.mapBackground)
                }
                val gridLineColor = if (transparentBackground) {
                    Color.White.copy(alpha = 0.16f)
                } else {
                    mapTheme.gridLineColor
                }
                val gridStep = w / 4f
                for (i in 1..3) {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(i * gridStep, 0f),
                        end = Offset(i * gridStep, h),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, i * gridStep),
                        end = Offset(w, i * gridStep),
                        strokeWidth = 1f
                    )
                }
            }

            if (centerGeo != null) {
                fun toScreen(pt: GeoPoint): Offset {
                    val (sx, sy) = OsmTileManager.geoToScreen(pt, centerGeo, w, h, zoom = 15)
                    return Offset(sx, sy)
                }

                if (routeData != null && routeData.alignments.isNotEmpty()) {
                    for (align in routeData.alignments) {
                        if (align.points.size < 2) continue
                        val path = Path()
                        var started = false
                        for (pt in align.points) {
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
                            color = mapTheme.routeCasingColor,
                            style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = path,
                            color = mapTheme.routeCenterColor,
                            style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    for (m in routeData.markers) {
                        val off = toScreen(m.point)
                        if (off.x in 0f..w && off.y in 0f..h) {
                            drawCircle(
                                color = mapTheme.markerDotColor,
                                radius = 2.8f,
                                center = off
                            )
                        }
                    }
                }

                if (location != null) {
                    val pinHeadR = 7.5f
                    val pinTip = center
                    val pinCenter = Offset(center.x, center.y - 9f)

                    drawCircle(
                        color = Color(0x66000000),
                        radius = 3.5f,
                        center = Offset(pinTip.x, pinTip.y + 1f)
                    )

                    val pinPath = Path().apply {
                        moveTo(pinTip.x, pinTip.y)
                        lineTo(pinCenter.x - pinHeadR * 0.85f, pinCenter.y + 1f)
                        arcTo(
                            rect = Rect(
                                pinCenter.x - pinHeadR,
                                pinCenter.y - pinHeadR,
                                pinCenter.x + pinHeadR,
                                pinCenter.y + pinHeadR
                            ),
                            startAngleDegrees = 145f,
                            sweepAngleDegrees = 250f,
                            forceMoveTo = false
                        )
                        close()
                    }
                    drawPath(pinPath, color = mapTheme.userPinColor)

                    drawCircle(
                        color = mapTheme.userPinInnerColor,
                        radius = 2.5f,
                        center = pinCenter
                    )

                    if (bearing != null) {
                        rotate(degrees = bearing, pivot = center) {
                            val arrowPath = Path().apply {
                                moveTo(center.x, center.y - 17f)
                                lineTo(center.x - 3.5f, center.y - 11f)
                                lineTo(center.x + 3.5f, center.y - 11f)
                                close()
                            }
                            drawPath(arrowPath, color = mapTheme.userBearingColor)
                        }
                    }
                }
            }
        }

        if (showGpsBadge && gpsAccuracy != null) {
            val isGood = gpsAccuracy <= 10f
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(mapTheme.badgeBg, RoundedCornerShape(10.dp))
                    .border(0.5.dp, mapTheme.badgeBorderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isGood) Color(0xFF22C55E) else Color(0xFFFBBF24))
                )
                Text(
                    text = String.format(Locale.US, "GPS ±%.1fm", gpsAccuracy),
                    color = mapTheme.badgeTextColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showAttribution) {
            Text(
                text = if (mapType.equals("satellite", ignoreCase = true)) "© Esri" else "© OSM",
                color = mapTheme.attributionTextColor,
                fontSize = 7.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 5.dp)
            )
        }

        if (showProjectTag && highwayTag.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .background(mapTheme.badgeBg, RoundedCornerShape(8.dp))
                    .border(0.5.dp, mapTheme.badgeBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 1.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = highwayTag,
                    color = mapTheme.badgeTextColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}
