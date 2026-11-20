package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.model.RouteData
import com.example.navigation.ChainageVoiceAssistantStage
import com.example.navigation.ChainageVoiceAssistantState

/**
 * Prominent animated microphone button beside the Destination Chainage input.
 * Displays calm acoustic pulse waves while listening, subtle morphing when processing,
 * and clear state-colored feedback without distracting full-screen overlays.
 */
@Composable
internal fun VoiceSearchMicButton(
    assistantState: ChainageVoiceAssistantState,
    voiceSearchEnabled: Boolean,
    accentBlue: Color,
    accentEmerald: Color,
    errorColor: Color,
    warningColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = assistantState.stage == ChainageVoiceAssistantStage.LISTENING
    val isProcessing = assistantState.stage == ChainageVoiceAssistantStage.PROCESSING
    val isError = assistantState.stage == ChainageVoiceAssistantStage.RECOGNITION_FAILED ||
        assistantState.stage == ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE ||
        assistantState.stage == ChainageVoiceAssistantStage.SERVICE_UNAVAILABLE
    val isConfirming = assistantState.stage == ChainageVoiceAssistantStage.AWAITING_CONFIRMATION ||
        assistantState.stage == ChainageVoiceAssistantStage.DESTINATION_RECOGNIZED ||
        assistantState.stage == ChainageVoiceAssistantStage.NAVIGATION_STARTED

    val targetTint = when {
        !voiceSearchEnabled -> Color(0xFF64748B)
        isListening -> Color(0xFFEF4444)
        isProcessing -> accentBlue
        isConfirming -> accentEmerald
        assistantState.stage == ChainageVoiceAssistantStage.AMBIGUOUS_CONFIRMATION -> warningColor
        isError -> errorColor
        else -> accentBlue
    }
    val tintColor by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 220),
        label = "voice_mic_tint"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "voice_mic_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 1150 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voice_pulse_ring"
    )

    val rmsBoost = if (isListening) (assistantState.rmsDb / 24f).coerceIn(0f, 0.16f) else 0f
    val buttonScale by animateFloatAsState(
        targetValue = if (isListening) 1.04f + rmsBoost else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "voice_mic_scale"
    )

    val micContentDesc = when {
        !voiceSearchEnabled -> "Voice search disabled in Settings"
        isListening -> "Stop listening for destination chainage"
        else -> "Start voice search for destination chainage"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .semantics { contentDescription = micContentDesc }
    ) {
        if (isListening || isProcessing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension * 0.36f
                val maxRadius = size.minDimension * 0.50f
                val ringRadius = baseRadius + (maxRadius - baseRadius) * pulseProgress
                val ringAlpha = (1f - pulseProgress) * (if (isListening) 0.42f else 0.25f)
                drawCircle(
                    color = tintColor.copy(alpha = ringAlpha),
                    radius = ringRadius
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = tintColor.copy(alpha = if (isListening) 0.22f else 0.14f),
            modifier = Modifier
                .size(48.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .clickable(enabled = voiceSearchEnabled, onClick = onClick)
                .testTag("navigate_chainage_mic_btn")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = when {
                        !voiceSearchEnabled -> Icons.Default.MicOff
                        isListening -> Icons.Default.Stop
                        isProcessing -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Compact, borderless voice assistant status indicator, recognized transcript display,
 * ambiguity candidate chooser, and explicit voice/touch confirmation strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceAssistantStatusPanel(
    assistantState: ChainageVoiceAssistantState,
    primaryText: Color,
    secondaryText: Color,
    accentBlue: Color,
    accentEmerald: Color,
    errorColor: Color,
    warningColor: Color,
    canStartNavigation: Boolean,
    onSelectAmbiguousCandidate: (Chainage) -> Unit,
    onConfirmAndStart: () -> Unit,
    onSpeakConfirmationReply: () -> Unit,
    onRepeatDestination: () -> Unit,
    onChangeDestination: () -> Unit,
    onRequestMicPermissionAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = assistantState.stage
    val isActiveOrInformative = stage != ChainageVoiceAssistantStage.IDLE ||
        assistantState.recognizedTranscript.isNotBlank() ||
        !assistantState.errorDetail.isNullOrBlank()

    val statusColor = when (stage) {
        ChainageVoiceAssistantStage.LISTENING -> Color(0xFFEF4444)
        ChainageVoiceAssistantStage.PROCESSING -> accentBlue
        ChainageVoiceAssistantStage.DESTINATION_RECOGNIZED,
        ChainageVoiceAssistantStage.AWAITING_CONFIRMATION,
        ChainageVoiceAssistantStage.NAVIGATION_STARTED -> accentEmerald
        ChainageVoiceAssistantStage.AMBIGUOUS_CONFIRMATION -> warningColor
        ChainageVoiceAssistantStage.RECOGNITION_FAILED,
        ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE,
        ChainageVoiceAssistantStage.SERVICE_UNAVAILABLE -> errorColor
        ChainageVoiceAssistantStage.IDLE -> secondaryText
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Minimalist status indicator pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isActiveOrInformative) statusColor.copy(alpha = 0.09f)
                    else Color.Transparent
                )
                .padding(horizontal = if (isActiveOrInformative) 10.dp else 2.dp, vertical = 5.dp)
                .testTag("navigate_chainage_voice_status")
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = assistantState.statusText,
                color = if (stage == ChainageVoiceAssistantStage.IDLE) secondaryText else statusColor,
                fontSize = 11.5.sp,
                fontWeight = if (stage == ChainageVoiceAssistantStage.IDLE) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Recognized speech transcript preview (editable via main input field)
        AnimatedVisibility(
            visible = assistantState.recognizedTranscript.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentBlue.copy(alpha = 0.07f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("navigate_chainage_voice_transcript")
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Heard: \"${assistantState.recognizedTranscript}\"",
                    color = primaryText.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Ambiguous recognition candidate selector (never silently picks wrong destination)
        AnimatedVisibility(
            visible = stage == ChainageVoiceAssistantStage.AMBIGUOUS_CONFIRMATION &&
                assistantState.ambiguousCandidates.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(warningColor.copy(alpha = 0.10f))
                    .padding(10.dp)
                    .testTag("navigate_chainage_ambiguous_panel")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = assistantState.errorDetail ?: "Select the intended destination chainage:",
                        color = primaryText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    assistantState.ambiguousCandidates.forEach { candidate ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = warningColor.copy(alpha = 0.18f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelectAmbiguousCandidate(candidate) }
                                .testTag("navigate_chainage_candidate_${candidate.format()}")
                        ) {
                            Text(
                                text = "KM ${candidate.format()}",
                                color = primaryText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Explicit Voice / Touch Confirmation Strip (Step 3 & 4)
        AnimatedVisibility(
            visible = stage == ChainageVoiceAssistantStage.AWAITING_CONFIRMATION && canStartNavigation,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentEmerald.copy(alpha = 0.10f))
                    .padding(10.dp)
                    .testTag("navigate_chainage_confirmation_bar")
            ) {
                Text(
                    text = assistantState.confirmationPrompt.ifBlank {
                        "Confirm destination to start navigation."
                    },
                    color = primaryText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onConfirmAndStart,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentEmerald,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("navigate_chainage_voice_confirm_start_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yes, Start", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onSpeakConfirmationReply,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("navigate_chainage_voice_reply_btn")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = accentBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Reply", color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onRepeatDestination,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("navigate_chainage_voice_repeat_btn")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Repeat destination", tint = secondaryText, modifier = Modifier.size(15.dp))
                    }

                    TextButton(
                        onClick = onChangeDestination,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("navigate_chainage_voice_change_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Change destination", tint = secondaryText, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // Permission / Service / Recognition Error explanation with non-intrusive retry
        AnimatedVisibility(
            visible = !assistantState.errorDetail.isNullOrBlank() &&
                stage != ChainageVoiceAssistantStage.AMBIGUOUS_CONFIRMATION,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(errorColor.copy(alpha = 0.09f))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("navigate_chainage_voice_error_banner")
            ) {
                Text(
                    text = assistantState.errorDetail.orEmpty(),
                    color = errorColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (stage == ChainageVoiceAssistantStage.PERMISSION_UNAVAILABLE) {
                    TextButton(
                        onClick = onRequestMicPermissionAgain,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("navigate_chainage_grant_mic_btn")
                    ) {
                        Text("Allow", color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (stage == ChainageVoiceAssistantStage.RECOGNITION_FAILED) {
                    TextButton(
                        onClick = onSpeakConfirmationReply,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry voice", tint = accentBlue, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

/**
 * Borderless minimalist route preview with soft fading background blend showing active alignment,
 * current position, and resolved destination chainage coordinate.
 */
@Composable
internal fun BorderlessChainageRouteMiniPreview(
    routeData: RouteData,
    currentPoint: GeoPoint?,
    destinationPoint: GeoPoint,
    currentLabel: String,
    destinationLabel: String,
    isDark: Boolean,
    accentBlue: Color,
    accentEmerald: Color,
    modifier: Modifier = Modifier
) {
    val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
    val alignPoints = remember(activeAlign?.id, routeData.markers.size) {
        val pts = activeAlign?.points.orEmpty()
        if (pts.size >= 2) pts else routeData.markers.map { it.point }
    }
    if (alignPoints.isEmpty()) return

    val surfaceTint = if (isDark) Color(0xFF080B12) else Color(0xFFF1F4F8)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        surfaceTint.copy(alpha = 0.30f),
                        surfaceTint,
                        surfaceTint.copy(alpha = 0.30f)
                    )
                )
            )
            .testTag("navigate_chainage_route_preview")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val allPts = buildList {
                addAll(alignPoints)
                if (currentPoint != null) add(currentPoint)
                add(destinationPoint)
            }
            val minLat = allPts.minOf { it.latitude }
            val maxLat = allPts.maxOf { it.latitude }
            val minLng = allPts.minOf { it.longitude }
            val maxLng = allPts.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)

            fun project(pt: GeoPoint): Offset {
                val x = (((pt.longitude - minLng) / lngSpan).toFloat().coerceIn(0f, 1f)) * size.width
                val y = (1f - ((pt.latitude - minLat) / latSpan).toFloat().coerceIn(0f, 1f)) * size.height
                return Offset(x, y)
            }

            if (alignPoints.size >= 2) {
                val path = Path()
                val step = (alignPoints.size / 100).coerceAtLeast(1)
                var started = false
                var idx = 0
                while (idx < alignPoints.size) {
                    val o = project(alignPoints[idx])
                    if (!started) {
                        path.moveTo(o.x, o.y)
                        started = true
                    } else {
                        path.lineTo(o.x, o.y)
                    }
                    idx += step
                }
                val lastO = project(alignPoints.last())
                path.lineTo(lastO.x, lastO.y)

                drawPath(
                    path = path,
                    color = accentBlue.copy(alpha = 0.42f),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            val destOffset = project(destinationPoint)
            if (currentPoint != null) {
                val curOffset = project(currentPoint)
                drawLine(
                    color = accentEmerald.copy(alpha = 0.72f),
                    start = curOffset,
                    end = destOffset,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = accentBlue.copy(alpha = 0.24f), radius = 7.dp.toPx(), center = curOffset)
                drawCircle(color = accentBlue, radius = 3.8.dp.toPx(), center = curOffset)
            }

            drawCircle(color = accentEmerald.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = destOffset)
            drawCircle(color = accentEmerald, radius = 4.4.dp.toPx(), center = destOffset)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentBlue))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentLabel,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentEmerald))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = destinationLabel,
                    color = accentEmerald,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
