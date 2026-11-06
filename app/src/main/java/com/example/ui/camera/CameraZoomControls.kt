package com.example.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.ui.theme.DarkYellowSurface
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import java.util.Locale
import kotlin.math.abs

/**
 * Clean, compact, minimalist zoom controller bar for CameraScreen.
 * Provides:
 * - Zoom-out (−) and Zoom-in (+) buttons
 * - Current zoom ratio badge with 1.0x reset
 * - Dynamic quick zoom presets (e.g. 0.5x, 1x, 2x, 3x, 5x, 10x) based on hardware capabilities
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

    // Dynamically calculate supported presets based on device camera hardware
    val presets = remember(minZoomRatio, maxZoomRatio) {
        val list = mutableListOf<Float>()
        if (minZoomRatio <= 0.65f) {
            list.add(0.5f)
        }
        list.add(1.0f)
        if (maxZoomRatio >= 2.0f) {
            list.add(2.0f)
        }
        if (maxZoomRatio >= 3.0f) {
            list.add(3.0f)
        }
        if (maxZoomRatio >= 5.0f) {
            list.add(5.0f)
        }
        if (maxZoomRatio >= 8.0f && !list.contains(maxZoomRatio)) {
            val roundedMax = (maxZoomRatio * 10).toInt() / 10f
            if (roundedMax > 5.0f) {
                list.add(roundedMax)
            }
        }
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
        // Expandable Fine Zoom Slider
        AnimatedVisibility(
            visible = showFineSlider,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xD90F172A),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, HighlightYellow.copy(alpha = 0.8f)),
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
                        color = HighlightYellow,
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
                            thumbColor = HighlightYellow,
                            activeTrackColor = HighlightYellow,
                            inactiveTrackColor = SecondaryText.copy(alpha = 0.3f)
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
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Zoom to 1.0x",
                            tint = HighlightYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Main Compact Minimalist Zoom Controller Bar
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xB3111827),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
            shadowElevation = 6.dp,
            modifier = Modifier.testTag("camera_zoom_control_bar")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Zoom-Out Button (−)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x501F2937))
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
                        tint = if (currentZoomRatio > minZoomRatio) PrimaryText else SecondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Quick Zoom Preset Buttons (e.g. 0.5x, 1x, 2x, 3x, 5x)
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) HighlightYellow else Color(0x351F2937)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) HighlightYellow else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
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
                            color = if (isSelected) Color.Black else PrimaryText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }

                // Current Zoom Ratio Display & 1.0x Reset Badge
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isNonDefaultZoom) DarkYellowSurface else Color(0x601F2937))
                        .border(
                            width = 0.8.dp,
                            color = if (isNonDefaultZoom) HighlightYellow else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            if (isNonDefaultZoom) {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (ignored: Exception) {}
                                onResetZoom()
                            } else {
                                showFineSlider = !showFineSlider
                            }
                        }
                        .padding(horizontal = 8.dp)
                        .testTag("zoom_ratio_display"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = formattedZoom,
                            color = if (isNonDefaultZoom) HighlightYellow else PrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isNonDefaultZoom) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset to 1.0x",
                                tint = HighlightYellow,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Zoom-In Button (+)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x501F2937))
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
                        tint = if (currentZoomRatio < maxZoomRatio) PrimaryText else SecondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
