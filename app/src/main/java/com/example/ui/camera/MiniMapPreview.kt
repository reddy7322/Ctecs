package com.example.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo.GeoUtils
import com.example.model.GeoPoint
import com.example.model.RouteData
import kotlin.math.cos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import java.util.Locale

@Composable
fun MiniMapPreview(
    location: GeoPoint?,
    bearing: Float?,
    routeData: RouteData?,
    highwayTag: String,
    modifier: Modifier = Modifier,
    gpsAccuracy: Float? = null
) {
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            // Grid lines
            val gridStep = w / 4f
            for (i in 1..3) {
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(i * gridStep, 0f),
                    end = Offset(i * gridStep, h),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, i * gridStep),
                    end = Offset(w, i * gridStep),
                    strokeWidth = 1f
                )
            }

            val centerGeo = location ?: routeData?.bounds?.center
            if (centerGeo != null) {
                val viewRangeMetres = 1200.0
                val midLat = Math.toRadians(centerGeo.latitude)
                val cosMidLat = cos(midLat)
                val metresPerPx = viewRangeMetres / w

                fun toScreen(pt: GeoPoint): Offset {
                    val dx = Math.toRadians(pt.longitude - centerGeo.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
                    val dy = Math.toRadians(pt.latitude - centerGeo.latitude) * GeoUtils.EARTH_RADIUS_METRES
                    val sx = (center.x + (dx / metresPerPx)).toFloat()
                    val sy = (center.y - (dy / metresPerPx)).toFloat()
                    return Offset(sx, sy)
                }

                // Draw route alignments
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
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Inner centerline
                        drawPath(
                            path = path,
                            color = Color(0xFF38BDF8),
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Nearby chainage dots
                    for (m in routeData.markers) {
                        if (GeoUtils.distanceMetres(m.point, centerGeo) < viewRangeMetres) {
                            val off = toScreen(m.point)
                            drawCircle(
                                color = Color(0xFFFBBF24),
                                radius = 2.5f,
                                center = off
                            )
                        }
                    }
                } else {
                    // Stylized road curve
                    val genericPath = Path().apply {
                        moveTo(0f, h - 16f)
                        cubicTo(w * 0.4f, h - 30f, w * 0.6f, 30f, w, 20f)
                    }
                    drawPath(genericPath, Color(0xFF0284C7), style = Stroke(width = 5f))
                    drawPath(genericPath, Color(0xFF38BDF8), style = Stroke(width = 3f))
                }

                // Current location pin (Classic Red Map Pin with drop shadow)
                val pinHeadR = 8f
                val pinTip = center
                val pinCenter = Offset(center.x, center.y - 10f)

                // Shadow under pin tip
                drawCircle(
                    color = Color(0x77000000),
                    radius = 4f,
                    center = Offset(pinTip.x, pinTip.y + 1f)
                )

                // Pin shape
                val pinPath = Path().apply {
                    moveTo(pinTip.x, pinTip.y)
                    lineTo(pinCenter.x - pinHeadR * 0.85f, pinCenter.y + 1f)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
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
                    radius = 2.8f,
                    center = pinCenter
                )

                // Heading arrow
                if (bearing != null) {
                    rotate(degrees = bearing, pivot = center) {
                        val arrowPath = Path().apply {
                            moveTo(center.x, center.y - 18f)
                            lineTo(center.x - 3.5f, center.y - 12f)
                            lineTo(center.x + 3.5f, center.y - 12f)
                            close()
                        }
                        drawPath(arrowPath, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Top-left GPS Accuracy pill matching reference screenshot
        if (gpsAccuracy != null) {
            val isGood = gpsAccuracy <= 10f
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xD9000000), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isGood) Color(0xFF22C55E) else Color(0xFFFBBF24))
                )
                Text(
                    text = String.format(Locale.US, "GPS ±%.1fm", gpsAccuracy),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Center Highway / Project tag pill (e.g. MH) matching reference screenshot
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .background(Color(0xD9000000), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = highwayTag.ifBlank { "MH" },
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}
