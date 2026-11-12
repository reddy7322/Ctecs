package com.example.ui.survey

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.ui.theme.isAppDarkTheme

enum class GlassButtonVariant {
    GOLD,
    SUCCESS,
    SKY,
    DESTRUCTIVE,
    NEUTRAL
}

/**
 * Unified theme colors model for the Survey module, delivering consistent,
 * high-contrast visual clarity across both Light and Dark theme modes.
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
    val isDark = isAppDarkTheme()
    return remember(isDark) {
        if (isDark) {
            SurveyThemeColors(
                isDark = true,
                background = Color(0xFF0F172A),
                cardBackground = Color(0xEB131B2E),
                cardBorder = Color(0x40FFD700),
                headerBackground = Color(0xEE131B2E),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFF94A3B8),
                textMuted = Color(0xFF64748B),
                divider = Color(0x26FFFFFF),
                gold = Color(0xFFFFD700),
                sky = Color(0xFF38BDF8),
                green = Color(0xFF22C55E),
                red = Color(0xFFEF4444),
                inputBackground = Color(0xFF1E293B),
                inputBorder = Color(0x40FFFFFF),
                inputFocusedBorder = Color(0xFFFFD700),
                surfaceHighlight = Color(0x28FFD700)
            )
        } else {
            SurveyThemeColors(
                isDark = false,
                background = Color(0xFFF8FAFC),
                cardBackground = Color(0xF5FFFFFF),
                cardBorder = Color(0xFFCBD5E1),
                headerBackground = Color(0xF2FFFFFF),
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                textMuted = Color(0xFF94A3B8),
                divider = Color(0x1F0F172A),
                gold = Color(0xFFB45309), // Warm readable amber in light mode
                sky = Color(0xFF0284C7),
                green = Color(0xFF16A34A),
                red = Color(0xFFDC2626),
                inputBackground = Color(0xFFFFFFFF),
                inputBorder = Color(0xFFCBD5E1),
                inputFocusedBorder = Color(0xFF0284C7),
                surfaceHighlight = Color(0x18B45309)
            )
        }
    }
}

/**
 * Premium minimalist glass button tailored for CTECS highway survey operations.
 * Automatically adapts between Light Theme (crisp borders, readable dark text/accents)
 * and Dark Theme (translucent dark glass, glowing gold/sky accents).
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
    val isDark = isAppDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val (bgBase, borderBase, contentBase) = if (isDark) {
        when (variant) {
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
    } else {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                Color(0x20F59E0B),
                Color(0x88B45309),
                Color(0xFFB45309)
            )
            GlassButtonVariant.SUCCESS -> Triple(
                Color(0x2016A34A),
                Color(0x8816A34A),
                Color(0xFF15803D)
            )
            GlassButtonVariant.SKY -> Triple(
                Color(0x200284C7),
                Color(0x880284C7),
                Color(0xFF0369A1)
            )
            GlassButtonVariant.DESTRUCTIVE -> Triple(
                Color(0x20DC2626),
                Color(0x88DC2626),
                Color(0xFFB91C1C)
            )
            GlassButtonVariant.NEUTRAL -> Triple(
                Color(0x120F172A),
                Color(0x330F172A),
                Color(0xFF1E293B)
            )
        }
    }

    val disabledBg = if (isDark) Color(0x101E293B) else Color(0x080F172A)
    val disabledBorder = if (isDark) Color(0x26475569) else Color(0x1F94A3B8)
    val disabledContent = if (isDark) Color(0x6694A3B8) else Color(0x66475569)

    val animatedBg by animateColorAsState(
        targetValue = if (enabled) bgBase else disabledBg,
        animationSpec = tween(180),
        label = "glass_bg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (enabled) borderBase else disabledBorder,
        animationSpec = tween(180),
        label = "glass_border"
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) contentBase else disabledContent,
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
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val isDark = isAppDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isHighlight = isActive || active

    val (bgBase, borderBase, contentBase) = if (isDark) {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                if (isHighlight) Color(0x38FFD700) else Color(0x28FFD700),
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
    } else {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                if (isHighlight) Color(0x30F59E0B) else Color(0x1AF59E0B),
                Color(0xFFB45309),
                Color(0xFFB45309)
            )
            GlassButtonVariant.SUCCESS -> Triple(
                Color(0x2016A34A),
                Color(0xFF16A34A),
                Color(0xFF15803D)
            )
            GlassButtonVariant.SKY -> Triple(
                Color(0x200284C7),
                Color(0xFF0284C7),
                Color(0xFF0369A1)
            )
            GlassButtonVariant.DESTRUCTIVE -> Triple(
                Color(0x20DC2626),
                Color(0xFFDC2626),
                Color(0xFFB91C1C)
            )
            GlassButtonVariant.NEUTRAL -> Triple(
                if (isHighlight) Color(0x250284C7) else Color(0xF0FFFFFF),
                if (isHighlight) Color(0xFF0284C7) else Color(0x330F172A),
                if (isHighlight) Color(0xFF0284C7) else Color(0xFF1E293B)
            )
        }
    }

    val actualIconSize = iconSize ?: (size.value * 0.48f).dp.coerceIn(16.dp, 22.dp)

    Surface(
        shape = shape,
        color = bgBase,
        border = BorderStroke(1.dp, borderBase.copy(alpha = if (isHighlight) 0.85f else 0.4f)),
        shadowElevation = if (isDark) 2.dp else 1.dp,
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
 * Supports both Light and Dark themes seamlessly.
 */
@Composable
fun <T> SurveyGlassSegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color? = null
) {
    val isDark = isAppDarkTheme()
    val resolvedActiveColor = activeColor ?: if (isDark) Color(0xFFFFD700) else Color(0xFFB45309)
    val surfaceBg = if (isDark) Color(0x990F172A) else Color(0xF2FFFFFF)
    val surfaceBorder = if (isDark) Color(0x3864748B) else Color(0x330F172A)
    val unselectedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = surfaceBg,
        border = BorderStroke(1.dp, surfaceBorder),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (option, label) ->
                val isSelected = option == selected
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) resolvedActiveColor.copy(alpha = if (isDark) 0.22f else 0.18f) else Color.Transparent,
                    animationSpec = tween(150),
                    label = "pill_bg"
                )
                val pillBorder by animateColorAsState(
                    targetValue = if (isSelected) resolvedActiveColor.copy(alpha = 0.85f) else Color.Transparent,
                    animationSpec = tween(150),
                    label = "pill_border"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) resolvedActiveColor else unselectedTextColor,
                    animationSpec = tween(150),
                    label = "pill_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
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
 * Clean Minimalist Theme Selector Dialog for the Survey Module.
 * Allows instant, dynamic theme switching between Light, Dark, and System Default
 * while preserving survey tracking and map coordinates.
 */
@Composable
fun SurveyThemeSelectorDialog(
    currentTheme: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = rememberSurveyThemeColors()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SURVEY THEME MODE",
                        fontWeight = FontWeight.Bold,
                        color = colors.gold,
                        fontSize = 14.sp
                    )
                    SurveyGlassIconButton(
                        icon = androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = "Done",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.GOLD,
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                HorizontalDivider(color = colors.divider)

                listOf(
                    Triple("light", "Light Theme", Icons.Default.LightMode),
                    Triple("dark", "Dark Theme", Icons.Default.DarkMode),
                    Triple("system", "System Default", Icons.Default.SettingsBrightness)
                ).forEach { (mode, label, icon) ->
                    val isSelected = currentTheme.equals(mode, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) colors.surfaceHighlight else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, colors.gold.copy(alpha = 0.6f)) else BorderStroke(0.5.dp, colors.cardBorder.copy(alpha = 0.3f)),
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
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    tint = if (isSelected) colors.gold else colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.gold else colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = colors.gold,
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
