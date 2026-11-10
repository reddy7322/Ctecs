package com.example.ui.survey

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GlassButtonVariant {
    GOLD,
    SUCCESS,
    SKY,
    DESTRUCTIVE,
    NEUTRAL
}

/**
 * Premium minimalist glass button tailored for CTECS highway survey operations.
 * Features translucent glass panels, subtle border outlines, clean icons, and responsive state animations.
 */
@Composable
fun SurveyGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: GlassButtonVariant = GlassButtonVariant.NEUTRAL,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fontSize: TextUnit = 12.sp,
    height: Dp = 42.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val (bgBase, borderBase, contentBase) = when (variant) {
        GlassButtonVariant.GOLD -> Triple(
            Color(0x28FFD700),
            Color(0x99FFD700),
            Color(0xFFFFD700)
        )
        GlassButtonVariant.SUCCESS -> Triple(
            Color(0x2822C55E),
            Color(0x8822C55E),
            Color(0xFF22C55E)
        )
        GlassButtonVariant.SKY -> Triple(
            Color(0x2438BDF8),
            Color(0x8838BDF8),
            Color(0xFF38BDF8)
        )
        GlassButtonVariant.DESTRUCTIVE -> Triple(
            Color(0x28EF4444),
            Color(0x88EF4444),
            Color(0xFFEF4444)
        )
        GlassButtonVariant.NEUTRAL -> Triple(
            Color(0x1F334155),
            Color(0x4D64748B),
            Color(0xFFF8FAFC)
        )
    }

    val animatedBg by animateColorAsState(
        targetValue = if (enabled) bgBase else Color(0x101E293B),
        animationSpec = tween(180),
        label = "glass_bg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (enabled) borderBase else Color(0x26475569),
        animationSpec = tween(180),
        label = "glass_border"
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) contentBase else Color(0x6694A3B8),
        animationSpec = tween(180),
        label = "glass_content"
    )

    val actualPadding = contentPadding ?: PaddingValues(horizontal = 10.dp)

    Surface(
        shape = shape,
        color = animatedBg,
        border = BorderStroke(1.dp, animatedBorder),
        shadowElevation = 0.dp,
        modifier = modifier
            .height(height)
            .heightIn(min = 32.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = animatedContent.copy(alpha = 0.25f)),
                enabled = enabled && !isLoading,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(actualPadding)
                .defaultMinSize(minHeight = height),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = animatedContent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedContent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = text,
                color = animatedContent,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact circular or rounded-square glass icon button for map controls and toolbar triggers.
 */
@Composable
fun SurveyGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.NEUTRAL,
    isActive: Boolean = false,
    active: Boolean = false,
    size: Dp = 38.dp,
    iconSize: Dp? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }

    val isHighlight = isActive || active
    val (bgBase, borderBase, contentBase) = when (variant) {
        GlassButtonVariant.GOLD -> Triple(
            if (isHighlight) Color(0x3DFD700) else Color(0x28FFD700),
            Color(0xFFFFD700),
            Color(0xFFFFD700)
        )
        GlassButtonVariant.SUCCESS -> Triple(
            Color(0x2822C55E),
            Color(0xFF22C55E),
            Color(0xFF22C55E)
        )
        GlassButtonVariant.SKY -> Triple(
            Color(0x2438BDF8),
            Color(0xFF38BDF8),
            Color(0xFF38BDF8)
        )
        GlassButtonVariant.DESTRUCTIVE -> Triple(
            Color(0x28EF4444),
            Color(0xFFEF4444),
            Color(0xFFEF4444)
        )
        GlassButtonVariant.NEUTRAL -> Triple(
            if (isHighlight) Color(0x3838BDF8) else Color(0xB30F172A),
            if (isHighlight) Color(0xFF38BDF8) else Color(0x3864748B),
            if (isHighlight) Color(0xFF38BDF8) else Color(0xFFF1F5F9)
        )
    }

    val actualIconSize = iconSize ?: (size.value * 0.48f).dp.coerceIn(16.dp, 22.dp)

    Surface(
        shape = shape,
        color = bgBase,
        border = BorderStroke(1.dp, borderBase.copy(alpha = if (isHighlight) 0.85f else 0.4f)),
        shadowElevation = 2.dp,
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentBase.copy(alpha = 0.3f)),
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentBase,
                modifier = Modifier.size(actualIconSize)
            )
        }
    }
}

/**
 * Ultra-sleek segmented toggle pill for Auto vs Manual modes, Offset sides, etc.
 */
@Composable
fun <T> SurveyGlassSegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFFFD700)
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0x990F172A),
        border = BorderStroke(1.dp, Color(0x3864748B)),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (option, label) ->
                val isSelected = option == selected
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) activeColor.copy(alpha = 0.22f) else Color.Transparent,
                    animationSpec = tween(150),
                    label = "pill_bg"
                )
                val pillBorder by animateColorAsState(
                    targetValue = if (isSelected) activeColor.copy(alpha = 0.85f) else Color.Transparent,
                    animationSpec = tween(150),
                    label = "pill_border"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else Color(0xFF94A3B8),
                    animationSpec = tween(150),
                    label = "pill_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .then(
                            if (isSelected) Modifier.background(pillBg, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
