package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Smart Fade Margin modifier: renders a completely borderless tonal wash that blends
 * softly at horizontal/vertical margins into the parent background without hard edges.
 */
fun Modifier.smartFadeSurface(
    surfaceColor: Color,
    accentGlowColor: Color = Color.Transparent,
    cornerRadius: Dp = 18.dp,
    edgeAlpha: Float = 0.35f,
    centerAlpha: Float = 0.96f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .drawBehind {
        drawRect(
            brush = Brush.horizontalGradient(
                0.0f to surfaceColor.copy(alpha = edgeAlpha),
                0.10f to surfaceColor.copy(alpha = centerAlpha),
                0.90f to surfaceColor.copy(alpha = centerAlpha),
                1.0f to surfaceColor.copy(alpha = edgeAlpha)
            )
        )
        if (accentGlowColor != Color.Transparent) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentGlowColor.copy(alpha = 0.14f),
                        accentGlowColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.35f),
                    radius = size.maxDimension * 0.72f
                )
            )
        }
    }

/**
 * Smart Edge Fade overlay for maps and visual panels: feathers all four edges softly
 * into [fadeToColor] (pure AMOLED black in Dark Mode, pure white in Light Mode)
 * to eliminate harsh rectangular boundaries while keeping inner map content crystal clear.
 */
fun Modifier.smartMapEdgeFade(
    fadeToColor: Color,
    edgeSize: Dp = 14.dp
): Modifier = this.drawWithContent {
    drawContent()
    val px = edgeSize.toPx().coerceAtMost(size.minDimension * 0.22f)
    if (px <= 0f) return@drawWithContent

    // Top fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(fadeToColor.copy(alpha = 0.85f), Color.Transparent),
            startY = 0f,
            endY = px
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, px)
    )
    // Bottom fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, fadeToColor.copy(alpha = 0.88f)),
            startY = size.height - px,
            endY = size.height
        ),
        topLeft = Offset(0f, size.height - px),
        size = Size(size.width, px)
    )
    // Left fade
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(fadeToColor.copy(alpha = 0.70f), Color.Transparent),
            startX = 0f,
            endX = px * 0.85f
        ),
        topLeft = Offset.Zero,
        size = Size(px * 0.85f, size.height)
    )
    // Right fade
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, fadeToColor.copy(alpha = 0.70f)),
            startX = size.width - px * 0.85f,
            endX = size.width
        ),
        topLeft = Offset(size.width - px * 0.85f, 0f),
        size = Size(px * 0.85f, size.height)
    )
}

/**
 * Borderless horizontal smart fade divider (soft tonal center fading out to edges).
 */
@Composable
fun SmartFadeDivider(
    modifier: Modifier = Modifier,
    color: Color = CtecsTheme.colors.subtleSeparator
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.15f to color.copy(alpha = 0.7f),
                    0.85f to color.copy(alpha = 0.7f),
                    1.0f to Color.Transparent
                )
            )
    )
}

/**
 * Unified flat borderless section container with tonal separation and smart fading margins.
 */
@Composable
fun CtecsFlatContainer(
    modifier: Modifier = Modifier,
    containerColor: Color = CtecsTheme.colors.surface,
    cornerRadius: Dp = 16.dp,
    useFadeEdges: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val bgModifier = if (useFadeEdges) {
        Modifier.smartFadeSurface(
            surfaceColor = containerColor,
            cornerRadius = cornerRadius,
            edgeAlpha = 0.78f,
            centerAlpha = 1f
        )
    } else {
        Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
    }
    Box(
        modifier = modifier
            .then(bgModifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Unified flat borderless top app bar used consistently across all secondary screens.
 */
@Composable
fun CtecsFlatTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onNavigateBack != null) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = c.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions
        )
    }
}

/**
 * Unified flat primary action button (electric blue, borderless, 48dp accessible touch target).
 */
@Composable
fun CtecsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = CtecsTheme.colors.accent,
    contentColor: Color = CtecsTheme.colors.onAccent,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Unified flat secondary tonal button (no borders, subtle tonal surface).
 */
@Composable
fun CtecsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = CtecsTheme.colors.secondarySurface,
    contentColor: Color = CtecsTheme.colors.textPrimary,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.45f)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Borderless tonal text field colors (no harsh rectangular outlines; subtle electric-blue focus indicator).
 */
@Composable
fun ctecsBorderlessInputColors(
    containerColor: Color = CtecsTheme.colors.surface,
    accentColor: Color = CtecsTheme.colors.accent
): TextFieldColors {
    val c = CtecsTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        disabledContainerColor = containerColor.copy(alpha = 0.5f),
        focusedBorderColor = accentColor.copy(alpha = 0.70f),
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = c.error,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedLabelColor = accentColor,
        unfocusedLabelColor = c.textSecondary,
        cursorColor = accentColor,
        focusedPlaceholderColor = c.textMuted,
        unfocusedPlaceholderColor = c.textMuted
    )
}

/**
 * Unified minimalist empty state for both AMOLED Dark and Professional Light themes.
 */
@Composable
fun CtecsEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(c.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = c.accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = title,
            color = c.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = c.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            CtecsPrimaryButton(
                text = actionLabel,
                onClick = onAction
            )
        }
    }
}

/**
 * Unified minimalist loading indicator (subtle blue progress, zero visual clutter).
 */
@Composable
fun CtecsLoadingState(
    message: String = "Loading…",
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            color = c.accent,
            trackColor = c.secondarySurface,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = message,
            color = c.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Unified minimalist error state with clear description and retry action.
 */
@Composable
fun CtecsErrorState(
    title: String,
    description: String,
    retryLabel: String? = "Retry",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.error.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = title,
                tint = c.error,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            color = c.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            color = c.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        if (retryLabel != null && onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = retryLabel,
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Compact flat pill selector chip (borderless, soft tonal fill, subtle electric-blue selected state).
 */
@Composable
fun CtecsPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CtecsTheme.colors.accent
) {
    val c = CtecsTheme.colors
    val bg = if (selected) accentColor.copy(alpha = if (c.isDark) 0.22f else 0.14f) else c.surface
    val fg = if (selected) accentColor else c.textSecondary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
