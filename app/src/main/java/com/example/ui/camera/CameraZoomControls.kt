package com.example.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraLensZoomCapabilities
import com.example.camera.CameraUltraWideZoomManager
import com.example.ui.theme.CtecsTheme
import kotlin.math.abs

/**
 * Clean, compact, borderless flat zoom controller bar for CameraScreen.
 * Provides:
 * - Zoom-out (−) and Zoom-in (+) buttons
 * - Current zoom ratio badge with 1.0x reset
 * - Hardware-detected quick zoom presets (e.g. 0.6x Ultra-Wide if supported, 1x, 2x, 3x, 5x, 8x)
 *   Strictly omits unsupported zoom presets so 0.6x is never displayed on devices without a wider FOV.
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
    zoomCapabilities: CameraLensZoomCapabilities? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showFineSlider by remember { mutableStateOf(false) }
    val c = CtecsTheme.colors
    val activeAccent = c.accent
    val pillBg = if (isLandscape) Color(0x88080B12) else Color(0xCC0D111A)
    val chipBg = Color(0x55171E2B)

    val effectiveMinZoom = remember(minZoomRatio, zoomCapabilities) {
        zoomCapabilities?.effectiveMinZoomRatio ?: minZoomRatio
    }
    val effectiveMaxZoom = remember(maxZoomRatio, zoomCapabilities) {
        maxOf(maxZoomRatio, zoomCapabilities?.maxZoomRatio ?: maxZoomRatio).coerceAtLeast(effectiveMinZoom)
    }

    // Dynamically calculate strictly hardware-supported presets (0.6x or nearest genuine wide FOV, 1x, 2x, 3x, 5x, 8x)
    val presets = remember(effectiveMinZoom, effectiveMaxZoom, zoomCapabilities) {
        CameraUltraWideZoomManager.resolveZoomPresets(
            minZoomRatio = effectiveMinZoom,
            maxZoomRatio = effectiveMaxZoom,
            capabilities = zoomCapabilities
        )
    }

    val formattedZoom = CameraUltraWideZoomManager.formatZoomBadge(currentZoomRatio)
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
                    val sliderFraction = CameraUltraWideZoomManager.zoomToSliderFraction(
                        zoom = currentZoomRatio,
                        minZoom = effectiveMinZoom,
                        maxZoom = effectiveMaxZoom
                    )
                    Slider(
                        value = sliderFraction,
                        onValueChange = { fraction ->
                            val newZoom = CameraUltraWideZoomManager.sliderFractionToZoom(
                                fraction = fraction,
                                minZoom = effectiveMinZoom,
                                maxZoom = effectiveMaxZoom
                            )
                            onZoomChanged(newZoom)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("camera_zoom_fine_slider")
                    )
                    IconButton(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (ignored: Exception) {}
                            onResetZoom()
                        },
                        modifier = Modifier
                            .size(32.dp)
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
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Zoom-Out Button (−)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            } catch (ignored: Exception) {}
                            val step = when {
                                currentZoomRatio <= 1.05f -> 0.1f
                                currentZoomRatio <= 5.0f -> 0.2f
                                currentZoomRatio <= 20.0f -> 1.0f
                                else -> 5.0f
                            }
                            val nextZoom = (currentZoomRatio - step).coerceAtLeast(effectiveMinZoom)
                            onZoomChanged(nextZoom)
                        }
                        .testTag("zoom_out_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = if (currentZoomRatio > effectiveMinZoom + 0.01f) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Dynamic Hardware-Detected Quick Zoom Preset Buttons (e.g. 0.5/0.6, 1, 2, 5, 10, 30, 100)
                presets.forEach { preset ->
                    val tolerance = when {
                        preset < 1.0f -> 0.08f
                        preset < 10.0f -> 0.15f
                        else -> (preset * 0.06f).coerceAtLeast(0.5f)
                    }
                    val isSelected = abs(currentZoomRatio - preset) < tolerance
                    val label = CameraUltraWideZoomManager.formatPresetChipLabel(preset)
                    val presetDescription = if (preset < 1.0f) {
                        "${label}x Ultra-Wide zoom"
                    } else if (abs(preset - 1.0f) < 0.05f) {
                        "1.0x Standard zoom"
                    } else {
                        "${label}x zoom"
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(min = 28.dp)
                            .size(width = if (label.length >= 3) 34.dp else 28.dp, height = 28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) activeAccent else chipBg)
                            .clickable {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                } catch (ignored: Exception) {}
                                onZoomChanged(preset)
                            }
                            .semantics { contentDescription = presetDescription }
                            .testTag("zoom_preset_$label"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = if (label.length >= 3) 8.5.sp else if (preset < 1.0f) 9.sp else 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Current Zoom Ratio Display & Slider Toggle Badge
                Box(
                    modifier = Modifier
                        .widthIn(min = 32.dp)
                        .size(width = if (formattedZoom.length >= 4) 38.dp else 32.dp, height = 28.dp)
                        .clip(CircleShape)
                        .background(if (isNonDefaultZoom || showFineSlider) activeAccent.copy(alpha = 0.24f) else chipBg)
                        .clickable {
                            showFineSlider = !showFineSlider
                        }
                        .semantics { contentDescription = "Current zoom $formattedZoom, tap for fine slider" }
                        .testTag("zoom_ratio_display"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formattedZoom,
                        color = if (isNonDefaultZoom || showFineSlider) activeAccent else Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                // Zoom-In Button (+)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            } catch (ignored: Exception) {}
                            val step = when {
                                currentZoomRatio < 1.0f -> 0.1f
                                currentZoomRatio < 5.0f -> 0.2f
                                currentZoomRatio < 20.0f -> 1.0f
                                else -> 5.0f
                            }
                            val nextZoom = (currentZoomRatio + step).coerceAtMost(effectiveMaxZoom)
                            onZoomChanged(nextZoom)
                        }
                        .testTag("zoom_in_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = if (currentZoomRatio < effectiveMaxZoom - 0.01f) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
