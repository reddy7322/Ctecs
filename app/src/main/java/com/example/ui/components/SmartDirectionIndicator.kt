package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DirectionDetectionState
import com.example.model.RoadSide
import com.example.model.SmartDirectionState

/**
 * Compact, borderless animated LHS/RHS direction indicator synchronized with
 * [SmartDirectionState].
 *
 * - Confirmed LHS / RHS renders with crisp directional arrow and smooth animated transition.
 * - Changing, Uncertain, Weak GPS, Stationary, and No-Alignment states render distinctly
 *   without ever displaying a guessed LHS/RHS side.
 * - Manual override shows a subtle "MAN" tag to distinguish it from automatic detection.
 */
@Composable
fun SmartDirectionIndicatorBadge(
    directionState: SmartDirectionState,
    isDark: Boolean,
    isCompact: Boolean = false,
    showArrow: Boolean = true,
    onClick: (() -> Unit)? = null,
    testTagName: String = "smart_direction_badge",
    modifier: Modifier = Modifier
) {
    val confirmedSide = directionState.confirmedSide
    val isLhs = confirmedSide.equals("LHS", ignoreCase = true)
    val isRhs = confirmedSide.equals("RHS", ignoreCase = true)
    val isOtherConfirmed = directionState.hasConfirmedDirection && !isLhs && !isRhs

    val targetAccent = when {
        isLhs -> if (isDark) Color(0xFF34D399) else Color(0xFF047857)
        isRhs -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
        isOtherConfirmed -> if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
        directionState.detectionState == DirectionDetectionState.DIRECTION_CHANGING ->
            if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
        directionState.detectionState == DirectionDetectionState.GPS_SIGNAL_WEAK ->
            if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
        else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    }

    val targetBg = when {
        isLhs -> if (isDark) Color(0xFF064E3B).copy(alpha = 0.50f) else Color(0xFFECFDF5)
        isRhs -> if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.50f) else Color(0xFFEFF6FF)
        isOtherConfirmed -> if (isDark) Color(0xFF4C1D95).copy(alpha = 0.48f) else Color(0xFFF3E8FF)
        directionState.detectionState == DirectionDetectionState.DIRECTION_CHANGING ->
            if (isDark) Color(0xFF78350F).copy(alpha = 0.45f) else Color(0xFFFEF3C7)
        directionState.detectionState == DirectionDetectionState.GPS_SIGNAL_WEAK ->
            if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.45f) else Color(0xFFFEE2E2)
        else -> if (isDark) Color(0xFF1E293B).copy(alpha = 0.72f) else Color(0xFFF1F5F9)
    }

    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(220),
        label = "smart_dir_accent"
    )
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(220),
        label = "smart_dir_bg"
    )

    val labelText = when {
        directionState.hasConfirmedDirection && confirmedSide != null -> {
            val arrow = if (showArrow && directionState.directionArrow.isNotBlank()) " ${directionState.directionArrow}" else ""
            "$confirmedSide$arrow"
        }
        else -> directionState.detectionState.shortLabel
    }

    val accessibilityLabel = buildString {
        append("Travel direction: ")
        append(directionState.statusLabel)
        if (directionState.isManualOverride) append(", manual override") else append(", automatic")
    }

    val clickModifier = if (onClick != null) {
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = if (isCompact) 32.dp else 36.dp)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBg)
            .then(clickModifier)
            .padding(
                horizontal = if (isCompact) 7.dp else 9.dp,
                vertical = if (isCompact) 3.dp else 4.5.dp
            )
            .semantics { contentDescription = accessibilityLabel }
            .testTag(testTagName)
    ) {
        AnimatedContent(
            targetState = Triple(labelText, directionState.detectionState, directionState.isManualOverride),
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200))) togetherWith
                    (fadeOut(tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(140)))
            },
            label = "smart_dir_badge_content"
        ) { (text, detState, isManual) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    detState == DirectionDetectionState.GPS_SIGNAL_WEAK -> {
                        Icon(
                            imageVector = Icons.Default.GpsOff,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(if (isCompact) 11.dp else 12.5.dp)
                        )
                    }
                    detState == DirectionDetectionState.DIRECTION_CHANGING -> {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(if (isCompact) 12.dp else 13.5.dp)
                        )
                    }
                    detState == DirectionDetectionState.STATIONARY && confirmedSide == null -> {
                        Icon(
                            imageVector = Icons.Default.PauseCircleOutline,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(if (isCompact) 11.dp else 12.5.dp)
                        )
                    }
                    detState == DirectionDetectionState.ALIGNMENT_UNAVAILABLE -> {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(if (isCompact) 11.dp else 12.5.dp)
                        )
                    }
                    detState == DirectionDetectionState.DIRECTION_UNCERTAIN -> {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(if (isCompact) 11.dp else 12.5.dp)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(if (isCompact) 5.dp else 6.dp)
                                .clip(CircleShape)
                                .background(animatedAccent)
                        )
                    }
                }

                Text(
                    text = text,
                    color = animatedAccent,
                    fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isManual) {
                    Text(
                        text = "MAN",
                        color = animatedAccent.copy(alpha = 0.78f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Compact dropdown selector enabling one-tap return to Automatic LHS/RHS detection
 * or explicit manual override without mutating alignment calibration.
 */
@Composable
fun SmartDirectionModePickerMenu(
    expanded: Boolean,
    directionState: SmartDirectionState,
    onDismiss: () -> Unit,
    onSelectAuto: () -> Unit,
    onSelectManualSide: (RoadSide) -> Unit,
    includeExtendedSides: Boolean = false
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        val isAuto = !directionState.isManualOverride
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto (Smart Detection)",
                        fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal
                    )
                }
            },
            trailingIcon = if (isAuto) {
                { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp)) }
            } else null,
            onClick = {
                onSelectAuto()
                onDismiss()
            },
            modifier = Modifier.testTag("smart_direction_menu_auto")
        )

        HorizontalDivider()

        val manualOptions = if (includeExtendedSides) {
            listOf(RoadSide.LHS, RoadSide.RHS, RoadSide.BOTH, RoadSide.MEDIAN, RoadSide.NONE)
        } else {
            listOf(RoadSide.LHS, RoadSide.RHS)
        }

        manualOptions.forEach { side ->
            val isSelected = directionState.isManualOverride &&
                directionState.confirmedSide.equals(side.label, ignoreCase = true)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Manual: ${side.label}",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                trailingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp)) }
                } else null,
                onClick = {
                    onSelectManualSide(side)
                    onDismiss()
                },
                modifier = Modifier.testTag("smart_direction_menu_${side.name.lowercase()}")
            )
        }
    }
}
