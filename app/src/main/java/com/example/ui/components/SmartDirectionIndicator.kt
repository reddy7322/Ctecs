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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
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

    val tokens = if (isDark) {
        com.example.ui.theme.DarkCtecsColorTokens
    } else {
        com.example.ui.theme.LightCtecsColorTokens
    }

    val targetAccent = when {
        isLhs -> tokens.success
        isRhs -> tokens.accent
        isOtherConfirmed -> tokens.accentSecondary
        directionState.detectionState == DirectionDetectionState.DIRECTION_CHANGING -> tokens.warning
        directionState.detectionState == DirectionDetectionState.GPS_SIGNAL_WEAK -> tokens.error
        else -> tokens.textSecondary
    }

    val targetBg = when {
        isLhs -> tokens.success.copy(alpha = if (isDark) 0.16f else 0.12f)
        isRhs -> tokens.accent.copy(alpha = if (isDark) 0.18f else 0.12f)
        isOtherConfirmed -> tokens.accentSecondary.copy(alpha = if (isDark) 0.18f else 0.12f)
        directionState.detectionState == DirectionDetectionState.DIRECTION_CHANGING ->
            tokens.warning.copy(alpha = if (isDark) 0.16f else 0.14f)
        directionState.detectionState == DirectionDetectionState.GPS_SIGNAL_WEAK ->
            tokens.error.copy(alpha = if (isDark) 0.16f else 0.12f)
        else -> tokens.surface
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
        onDismissRequest = onDismiss,
        containerColor = CtecsTheme.colors.elevatedSurface
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

        SmartFadeDivider(modifier = Modifier.padding(vertical = 2.dp))

        val manualOptions = if (includeExtendedSides) {
            listOf(RoadSide.LHS, RoadSide.RHS, RoadSide.BOTH, RoadSide.MEDIAN, RoadSide.NONE)
        } else {
            listOf(RoadSide.LHS, RoadSide.RHS)
        }

        manualOptions.forEach { side ->
            val confirmedNorm = com.example.navigation.SmartDirectionManager.normalizeManualSide(directionState.confirmedSide)
            val sideNorm = com.example.navigation.SmartDirectionManager.normalizeManualSide(side.label)
            val isSelected = directionState.isManualOverride && confirmedNorm != null && confirmedNorm == sideNorm
            DropdownMenuItem(
                text = {
                    Text(
                        text = side.label,
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
