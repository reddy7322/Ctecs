package com.example.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CtecsTheme
import java.util.Locale
import kotlin.math.abs

/**
 * Clean, compact, borderless flat zoom controller bar for CameraScreen.
 * Provides:
 * - Zoom-out (−) and Zoom-in (+) buttons
 * - Current zoom ratio badge with 1.0x reset
 * - Dynamic quick zoom presets (e.g. 0.5x, 1x, 2x, 3x, 5x, 8x) based on hardware capabilities
 * - Interactive expandable fine-scrubbing zoom slider
 */
@Composable
fun CameraZoomControlBar(
    currentZoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomChanged: (Float) -> Unit,
    onResetZoom: () -> Unit = { onZoomChanged(1.0f) },
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showFineSlider by remember { mutableStateOf(false) }
    val c = CtecsTheme.colors
    val activeAccent = c.accent
    val pillBg = if (isLandscape) Color(0x88080B12) else Color(0xCC0D111A)
    val chipBg = Color(0x55171E2B)

    // Dynamically calculate supported presets based on device camera hardware
    val presets = remember(minZoomRatio, maxZoomRatio) {
        val list = mutableListOf<Float>()
        if (minZoomRatio <= 0.65f) {
            list.add(0.5f)
        }
        list.add(1.0f)
        if (maxZoomRatio >= 2.0f) list.add(2.0f)
        if (maxZoomRatio >= 3.0f) list.add(3.0f)
        if (maxZoomRatio >= 5.0f) list.add(5.0f)
        if (maxZoomRatio >= 8.0f) list.add(8.0f)
        list
    }

    val formattedZoom = if (currentZoomRatio < 1.0f) {
        String.format(Locale.US, "%.1fx", currentZoomRatio)
    } else if (abs(currentZoomRatio - currentZoomRatio.toInt().toFloat()) < 0.05f) {
        "${currentZoomRatio.toInt()}x"
    } else {
        String.format(Locale.US, "%.1fx", currentZoomRatio)
    }

    val isNonDefaultZoom = abs(currentZoomRatio - 1.0f) > 0.08f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expandable Fine Zoom Slider (flat borderless dark translucent pill)
        AnimatedVisibility(
            visible = showFineSlider,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xE60D111A),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .width(if (isLandscape) 320.dp else 290.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedZoom,
                        color = activeAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(42.dp)
                    )
                    Slider(
                        value = currentZoomRatio.coerceIn(minZoomRatio, maxZoomRatio),
                        onValueChange = { newZoom ->
                            onZoomChanged(newZoom)
                        },
                        valueRange = minZoomRatio..maxZoomRatio,
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (ignored: Exception) {}
                            onResetZoom()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(activeAccent.copy(alpha = 0.16f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Zoom to 1.0x",
                            tint = activeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Main Compact Borderless Zoom Controller Bar
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = pillBg,
            shadowElevation = 0.dp,
            modifier = Modifier.testTag("camera_zoom_control_bar")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Zoom-Out Button (−)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            } catch (ignored: Exception) {}
                            val nextZoom = (currentZoomRatio - 0.2f).coerceAtLeast(minZoomRatio)
                            onZoomChanged(nextZoom)
                        }
                        .testTag("zoom_out_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = if (currentZoomRatio > minZoomRatio) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Quick Zoom Preset Buttons (e.g. 0.5, 1, 2, 3, 5, 8)
                presets.forEach { preset ->
                    val isSelected = abs(currentZoomRatio - preset) < 0.12f
                    val label = if (preset < 1.0f) {
                        String.format(Locale.US, "%.1f", preset)
                    } else if (preset % 1.0f == 0.0f) {
                        "${preset.toInt()}"
                    } else {
                        String.format(Locale.US, "%.1f", preset)
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) activeAccent else chipBg)
                            .clickable {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                } catch (ignored: Exception) {}
                                onZoomChanged(preset)
                            }
                            .testTag("zoom_preset_$label"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }

                // Current Zoom Ratio Display & Slider Toggle Badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isNonDefaultZoom || showFineSlider) activeAccent.copy(alpha = 0.24f) else chipBg)
                        .clickable {
                            showFineSlider = !showFineSlider
                        }
                        .testTag("zoom_ratio_display"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formattedZoom,
                        color = if (isNonDefaultZoom || showFineSlider) activeAccent else Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Zoom-In Button (+)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            } catch (ignored: Exception) {}
                            val nextZoom = (currentZoomRatio + 0.2f).coerceAtMost(maxZoomRatio)
                            onZoomChanged(nextZoom)
                        }
                        .testTag("zoom_in_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = if (currentZoomRatio < maxZoomRatio) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
