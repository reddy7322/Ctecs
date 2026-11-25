package com.example.ui.workplan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RollingCamberMode
import com.example.ui.theme.CtecsTheme
import java.util.Locale
import kotlin.math.ceil

/**
 * Compact animated top-view Rolling Pattern Diagram.
 * Dynamically reflects road width, drum width, overlap, camber sequence (edge-to-centre / lower-to-upper),
 * joint rolling track, and roller travel direction. Respects reduced-motion setting.
 */
@Composable
fun RollingPatternDiagram(
    roadWidthM: Double,
    rollerDrumWidthM: Double,
    overlapM: Double,
    camberMode: RollingCamberMode,
    passesLabel: String,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val safeRoadW = roadWidthM.coerceIn(1.5, 25.0)
    val safeDrumW = rollerDrumWidthM.coerceIn(0.6, 3.0)
    val safeOverlap = overlapM.coerceIn(0.05, safeDrumW * 0.5)
    val effectiveStripW = (safeDrumW - safeOverlap).coerceAtLeast(0.4)
    val trackCount = ceil(safeRoadW / effectiveStripW).toInt().coerceIn(1, 8)

    val phase = if (animationsEnabled) {
        val infinite = rememberInfiniteTransition(label = "roller_pass")
        val p by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "roller_x"
        )
        p
    } else {
        0.35f
    }

    val sequenceLabel = when (camberMode) {
        RollingCamberMode.CROWN_CENTER -> "Longitudinal Joint → Outer Edges → Centre Crown"
        RollingCamberMode.SUPERELEVATION_LEFT -> "Lower Left Edge → Upper Right Edge"
        RollingCamberMode.SUPERELEVATION_RIGHT -> "Lower Right Edge → Upper Left Edge"
        RollingCamberMode.FLAT -> "Joint First → Progressive Overlapping Tracks"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.secondarySurface)
            .padding(10.dp)
            .testTag("rolling_pattern_diagram"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOP-VIEW ROLLING PATTERN",
                color = c.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "$trackCount tracks · ${String.format(Locale.US, "%.2f", effectiveStripW)}m eff.",
                color = c.textMuted,
                fontSize = 10.sp
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((34 + trackCount * 16).coerceIn(84, 152).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (c.isDark) Color(0xFF0A0F18) else Color(0xFFEAF0F7))
        ) {
            val padX = 14.dp.toPx()
            val padY = 10.dp.toPx()
            val roadW = (size.width - padX * 2).coerceAtLeast(40f)
            val roadH = (size.height - padY * 2).coerceAtLeast(36f)
            val stripH = roadH / trackCount

            // Roadway surface boundary
            drawRoundRect(
                color = if (c.isDark) Color(0xFF1A2232) else Color(0xFFD6E0EC),
                topLeft = Offset(padX, padY),
                size = Size(roadW, roadH),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            // Longitudinal joint / centreline highlight
            val jointY = when (camberMode) {
                RollingCamberMode.CROWN_CENTER -> padY + roadH / 2f
                RollingCamberMode.SUPERELEVATION_RIGHT -> padY + roadH - 2f
                else -> padY + 2f
            }
            drawLine(
                color = c.warning.copy(alpha = 0.85f),
                start = Offset(padX, jointY),
                end = Offset(padX + roadW, jointY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )

            // Draw individual roller tracks & overlap bands
            for (i in 0 until trackCount) {
                val y0 = padY + i * stripH
                val trackColor = if (i % 2 == 0) c.accent.copy(alpha = 0.18f) else c.accentSecondary.copy(alpha = 0.16f)
                drawRect(
                    color = trackColor,
                    topLeft = Offset(padX + 2f, y0 + 1f),
                    size = Size(roadW - 4f, (stripH - 2f).coerceAtLeast(2f))
                )
                // Overlap band between adjacent tracks
                if (i > 0) {
                    val overlapPx = (stripH * (safeOverlap / safeDrumW).toFloat()).coerceIn(2f, 6f)
                    drawRect(
                        color = c.warning.copy(alpha = 0.35f),
                        topLeft = Offset(padX + 2f, y0 - overlapPx / 2f),
                        size = Size(roadW - 4f, overlapPx)
                    )
                }

                // Direction guide line
                val midY = y0 + stripH / 2f
                drawLine(
                    color = c.accent.copy(alpha = 0.55f),
                    start = Offset(padX + 10f, midY),
                    end = Offset(padX + roadW - 10f, midY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            // Animated roller drum icon sweeping along the active pass track
            val activeTrack = ((phase * trackCount).toInt()).coerceIn(0, trackCount - 1)
            val localProgress = (phase * trackCount) - activeTrack
            val goingRight = activeTrack % 2 == 0
            val rollerNormX = if (goingRight) localProgress else (1f - localProgress)
            val drumW = 24.dp.toPx()
            val rollerX = padX + 6f + rollerNormX * (roadW - drumW - 12f).coerceAtLeast(1f)
            val rollerY = padY + activeTrack * stripH + 2f
            drawRoundRect(
                color = c.success,
                topLeft = Offset(rollerX, rollerY),
                size = Size(drumW, (stripH - 4f).coerceAtLeast(8f)),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Width ${String.format(Locale.US, "%.1f", safeRoadW)}m · Drum ${String.format(Locale.US, "%.2f", safeDrumW)}m · Overlap ${(safeOverlap * 1000).toInt()}mm",
                color = c.textSecondary,
                fontSize = 10.sp
            )
            Text(
                text = passesLabel,
                color = c.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "Sequence: $sequenceLabel · Dashed amber line = Joint / Reference",
            color = c.textMuted,
            fontSize = 10.sp
        )
    }
}
