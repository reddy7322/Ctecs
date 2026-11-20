package com.example.ui.survey

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider

/**
 * Material UI button color variants for CTECS survey controls.
 */
enum class GlassButtonVariant {
    GOLD,
    SUCCESS,
    SKY,
    DESTRUCTIVE,
    NEUTRAL
}

// Alias for modern naming
typealias SurveyButtonVariant = GlassButtonVariant

/**
 * Unified theme colors model for the Survey module, directly backed by
 * CtecsTheme (AMOLED Deep Black Dark Mode & Professional Minimalist Light Mode).
 */
data class SurveyThemeColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val headerBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val gold: Color,
    val sky: Color,
    val green: Color,
    val red: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val inputFocusedBorder: Color,
    val surfaceHighlight: Color
)

@Composable
fun rememberSurveyThemeColors(): SurveyThemeColors {
    val c = CtecsTheme.colors
    return remember(c) {
        SurveyThemeColors(
            isDark = c.isDark,
            background = c.background,
            cardBackground = c.surface,
            cardBorder = Color.Transparent,
            headerBackground = c.background,
            textPrimary = c.textPrimary,
            textSecondary = c.textSecondary,
            textMuted = c.textMuted,
            divider = c.subtleSeparator,
            gold = c.warning,
            sky = c.accent,
            green = c.success,
            red = c.error,
            inputBackground = c.secondarySurface,
            inputBorder = Color.Transparent,
            inputFocusedBorder = c.accent,
            surfaceHighlight = c.secondarySurface
        )
    }
}

/**
 * Flat Borderless Material Design Button for CTECS Survey operations.
 * Zero borders, zero shadows, clean tonal/solid fills with high-contrast text.
 */
@Composable
fun SurveyMaterialButton(
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
    val c = CtecsTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    val (bgBase, contentBase) = when (variant) {
        GlassButtonVariant.GOLD -> c.warning to Color.White
        GlassButtonVariant.SUCCESS -> c.success to Color.White
        GlassButtonVariant.SKY -> c.accent to Color.White
        GlassButtonVariant.DESTRUCTIVE -> c.error to Color.White
        GlassButtonVariant.NEUTRAL -> c.secondarySurface to c.textPrimary
    }

    val disabledBg = c.secondarySurface.copy(alpha = 0.55f)
    val disabledContent = c.textMuted

    val animatedBg by animateColorAsState(
        targetValue = if (enabled) bgBase else disabledBg,
        animationSpec = tween(150),
        label = "btn_bg"
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) contentBase else disabledContent,
        animationSpec = tween(150),
        label = "btn_content"
    )

    val actualPadding = contentPadding ?: PaddingValues(horizontal = 12.dp, vertical = 4.dp)

    Surface(
        shape = shape,
        color = animatedBg,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(height)
            .heightIn(min = 38.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = animatedContent.copy(alpha = 0.2f)),
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
                    modifier = Modifier.size(15.dp),
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
 * Backwards compatible alias for SurveyMaterialButton.
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
    SurveyMaterialButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        variant = variant,
        enabled = enabled,
        isLoading = isLoading,
        fontSize = fontSize,
        height = height,
        shape = shape,
        contentPadding = contentPadding
    )
}

/**
 * Flat Borderless Material Design Icon Button for map controls and toolbar actions.
 */
@Composable
fun SurveyMaterialIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.NEUTRAL,
    isActive: Boolean = false,
    active: Boolean = false,
    size: Dp = 38.dp,
    iconSize: Dp? = null,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val c = CtecsTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHighlight = isActive || active

    val (bgBase, contentBase) = when (variant) {
        GlassButtonVariant.GOLD ->
            if (isHighlight) c.warning to Color.White else c.secondarySurface to c.warning
        GlassButtonVariant.SUCCESS ->
            if (isHighlight) c.success to Color.White else c.secondarySurface to c.success
        GlassButtonVariant.SKY ->
            if (isHighlight) c.accent to Color.White else c.secondarySurface to c.accent
        GlassButtonVariant.DESTRUCTIVE ->
            if (isHighlight) c.error to Color.White else c.secondarySurface to c.error
        GlassButtonVariant.NEUTRAL ->
            if (isHighlight) c.accent to Color.White else c.secondarySurface to c.textPrimary
    }

    val actualIconSize = iconSize ?: (size.value * 0.48f).dp.coerceIn(16.dp, 22.dp)

    Surface(
        shape = shape,
        color = bgBase,
        shadowElevation = 0.dp,
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentBase.copy(alpha = 0.25f)),
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
 * Backwards compatible alias for SurveyMaterialIconButton.
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
    shape: Shape = RoundedCornerShape(12.dp)
) {
    SurveyMaterialIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        isActive = isActive,
        active = active,
        size = size,
        iconSize = iconSize,
        shape = shape
    )
}

/**
 * Flat Borderless Segmented Toggle Pill for Road Side, Lane selection, Auto vs Manual modes.
 */
@Composable
fun <T> SurveyGlassSegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color? = null
) {
    val c = CtecsTheme.colors
    val resolvedActiveColor = activeColor ?: c.accent

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = c.secondarySurface,
        shadowElevation = 0.dp,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (option, label) ->
                val isSelected = option == selected
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) resolvedActiveColor else Color.Transparent,
                    animationSpec = tween(120),
                    label = "pill_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else c.textSecondary,
                    animationSpec = tween(120),
                    label = "pill_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
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

/**
 * Clean Borderless Theme Selector Dialog for the Survey Module.
 */
@Composable
fun SurveyThemeSelectorDialog(
    currentTheme: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = c.elevatedSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "APPEARANCE MODE",
                        fontWeight = FontWeight.Bold,
                        color = c.accent,
                        fontSize = 13.sp,
                        letterSpacing = 0.8.sp
                    )
                    SurveyGlassIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Done",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SKY,
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                SmartFadeDivider()

                listOf(
                    Triple("dark", "Deep Black Dark Mode", Icons.Default.DarkMode),
                    Triple("light", "Professional Light Mode", Icons.Default.LightMode),
                    Triple("system", "System Default", Icons.Default.SettingsBrightness)
                ).forEach { (mode, label, icon) ->
                    val isSelected = currentTheme.equals(mode, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) c.accent.copy(alpha = 0.14f) else c.secondarySurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTheme(mode)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) c.accent else c.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) c.accent else c.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
