package com.example.ui.camera

import android.graphics.Bitmap
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo.GeoUtils
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.RouteData
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
    showProjectTag: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsState()

    val appliedModifier = if (modifier == Modifier) {
        Modifier.size(width = 110.dp, height = 80.dp)
    } else {
        modifier
    }

    Box(
        modifier = appliedModifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE60F172A))
            .border(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        val centerGeo = location ?: routeData?.bounds?.center

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            // 1. OpenStreetMap Tile Background (Real geographic map data)
            var hasOsmTiles = false
            if (centerGeo != null) {
                val osmBmp = OsmTileManager.renderMapBitmap(
                    context = context,
                    center = centerGeo,
                    widthPx = w.toInt().coerceAtLeast(1),
                    heightPx = h.toInt().coerceAtLeast(1),
                    zoom = 15
                )
                if (osmBmp != null) {
                    drawImage(
                        image = osmBmp.asImageBitmap(),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.toInt(), h.toInt())
                    )
                    // Dim slightly for minimalist dark Glass UI look
                    drawRect(color = Color(0x33000000))
                    hasOsmTiles = true
                }
            }

            // Fallback grid when tiles are loading or offline
            if (!hasOsmTiles) {
                val gridStep = w / 4f
                for (i in 1..3) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(i * gridStep, 0f),
                        end = Offset(i * gridStep, h),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, i * gridStep),
                        end = Offset(w, i * gridStep),
                        strokeWidth = 1f
                    )
                }
            }

            if (centerGeo != null) {
                // Projection conversion
                fun toScreen(pt: GeoPoint): Offset {
                    val (sx, sy) = OsmTileManager.geoToScreen(pt, centerGeo, w, h, zoom = 15)
                    return Offset(sx, sy)
                }

                // 2. KMZ/KML Route Alignments
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
                        // Outer casing
                        drawPath(
                            path = path,
                            color = Color(0xFF0284C7),
                            style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Inner centerline
                        drawPath(
                            path = path,
                            color = Color(0xFF38BDF8),
                            style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Nearby chainage marker dots
                    for (m in routeData.markers) {
                        val off = toScreen(m.point)
                        if (off.x in 0f..w && off.y in 0f..h) {
                            drawCircle(
                                color = Color(0xFFFBBF24),
                                radius = 2.8f,
                                center = off
                            )
                        }
                    }
                } else if (!hasOsmTiles) {
                    // Stylized road curve fallback only when neither route nor OSM tile is loaded
                    val genericPath = Path().apply {
                        moveTo(0f, h - 16f)
                        cubicTo(w * 0.4f, h - 30f, w * 0.6f, 30f, w, 20f)
                    }
                    drawPath(genericPath, Color(0xFF0284C7), style = Stroke(width = 5f))
                    drawPath(genericPath, Color(0xFF38BDF8), style = Stroke(width = 3f))
                }

                // 3. Current GPS Location Pin (Red marker with drop shadow)
                val pinHeadR = 7.5f
                val pinTip = center
                val pinCenter = Offset(center.x, center.y - 9f)

                // Shadow under pin tip
                drawCircle(
                    color = Color(0x77000000),
                    radius = 3.5f,
                    center = Offset(pinTip.x, pinTip.y + 1f)
                )

                // Pin shape
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
                drawPath(pinPath, color = Color(0xFFEF4444))

                // Inner white dot
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = pinCenter
                )

                // Heading / Bearing direction arrow
                if (bearing != null) {
                    rotate(degrees = bearing, pivot = center) {
                        val arrowPath = Path().apply {
                            moveTo(center.x, center.y - 17f)
                            lineTo(center.x - 3.5f, center.y - 11f)
                            lineTo(center.x + 3.5f, center.y - 11f)
                            close()
                        }
                        drawPath(arrowPath, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Top-left GPS Accuracy pill (only shown when showGpsBadge is explicitly true)
        if (showGpsBadge && gpsAccuracy != null) {
            val isGood = gpsAccuracy <= 10f
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(Color(0xD9000000), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
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
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top-right OpenStreetMap Attribution
        Text(
            text = "© OSM",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 5.dp)
        )

        // Bottom Center Highway / Project tag pill (only shown when showProjectTag is explicitly true)
        if (showProjectTag && highwayTag.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .background(Color(0xD9000000), RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 1.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = highwayTag,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}
