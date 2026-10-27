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

@Composable
fun MiniMapPreview(
    location: GeoPoint?,
    bearing: Float?,
    routeData: RouteData?,
    highwayTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE60F172A))
            .border(1.5.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
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

                // Current location pin
                drawCircle(
                    color = Color(0x66EF4444),
                    radius = 9f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 4.5f,
                    center = center
                )
                drawCircle(
                    color = Color.White,
                    radius = 2f,
                    center = center
                )

                // Heading arrow
                if (bearing != null) {
                    rotate(degrees = bearing, pivot = center) {
                        val arrowPath = Path().apply {
                            moveTo(center.x, center.y - 12f)
                            lineTo(center.x - 3.5f, center.y - 6f)
                            lineTo(center.x + 3.5f, center.y - 6f)
                            close()
                        }
                        drawPath(arrowPath, color = Color(0xFFEF4444))
                    }
                }
            }
        }

        // Road Name tag
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0xCC090D16))
                .fillMaxSize(0.24f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = highwayTag.ifBlank { "Route" },
                color = Color(0xFF94A3B8),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
