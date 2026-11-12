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
 * Unified theme colors model for the Survey module, delivering consistent,
 * high-contrast visual clarity across both Light and Dark theme modes.
 * Clean, solid Material Design surfaces without muddy transparencies.
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
                background = Color(0xFF0A0F1D),
                cardBackground = Color(0xFF131C2E),
                cardBorder = Color(0xFF1E293B),
                headerBackground = Color(0xFF131C2E),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFF94A3B8),
                textMuted = Color(0xFF64748B),
                divider = Color(0xFF1E293B),
                gold = Color(0xFFF59E0B),
                sky = Color(0xFF38BDF8),
                green = Color(0xFF22C55E),
                red = Color(0xFFEF4444),
                inputBackground = Color(0xFF0F172A),
                inputBorder = Color(0xFF334155),
                inputFocusedBorder = Color(0xFFF59E0B),
                surfaceHighlight = Color(0xFF1E293B)
            )
        } else {
            SurveyThemeColors(
                isDark = false,
                background = Color(0xFFF8FAFC),
                cardBackground = Color(0xFFFFFFFF),
                cardBorder = Color(0xFFE2E8F0),
                headerBackground = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                textMuted = Color(0xFF94A3B8),
                divider = Color(0xFFE2E8F0),
                gold = Color(0xFFD97706), // Crisp amber in light mode
                sky = Color(0xFF0284C7),
                green = Color(0xFF16A34A),
                red = Color(0xFFDC2626),
                inputBackground = Color(0xFFF8FAFC),
                inputBorder = Color(0xFFCBD5E1),
                inputFocusedBorder = Color(0xFF0284C7),
                surfaceHighlight = Color(0xFFF1F5F9)
            )
        }
    }
}

/**
 * Modern Material Design Flat Button for CTECS Survey operations.
 * Eliminates glass effects, thick borders, heavy shadows, and gradients.
 * Uses flat solid/tonal Material UI fills with high-contrast text and clean ripple effects.
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
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues? = null
) {
    val isDark = isAppDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    // Flat Material color pairs (Background, Text/Icon content, subtle border)
    val (bgBase, borderBase, contentBase) = if (isDark) {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                Color(0xFFD97706),
                Color(0xFFF59E0B),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.SUCCESS -> Triple(
                Color(0xFF16A34A),
                Color(0xFF22C55E),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.SKY -> Triple(
                Color(0xFF0284C7),
                Color(0xFF38BDF8),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.DESTRUCTIVE -> Triple(
                Color(0xFFDC2626),
                Color(0xFFEF4444),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.NEUTRAL -> Triple(
                Color(0xFF1E293B),
                Color(0xFF334155),
                Color(0xFFF8FAFC)
            )
        }
    } else {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                Color(0xFFD97706),
                Color(0xFFB45309),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.SUCCESS -> Triple(
                Color(0xFF16A34A),
                Color(0xFF15803D),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.SKY -> Triple(
                Color(0xFF0284C7),
                Color(0xFF0369A1),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.DESTRUCTIVE -> Triple(
                Color(0xFFDC2626),
                Color(0xFFB91C1C),
                Color(0xFFFFFFFF)
            )
            GlassButtonVariant.NEUTRAL -> Triple(
                Color(0xFFF1F5F9),
                Color(0xFFCBD5E1),
                Color(0xFF0F172A)
            )
        }
    }

    val disabledBg = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val disabledBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val disabledContent = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)

    val animatedBg by animateColorAsState(
        targetValue = if (enabled) bgBase else disabledBg,
        animationSpec = tween(150),
        label = "btn_bg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (enabled) borderBase else disabledBorder,
        animationSpec = tween(150),
        label = "btn_border"
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) contentBase else disabledContent,
        animationSpec = tween(150),
        label = "btn_content"
    )

    val actualPadding = contentPadding ?: PaddingValues(horizontal = 10.dp, vertical = 4.dp)

    Surface(
        shape = shape,
        color = animatedBg,
        border = BorderStroke(0.75.dp, animatedBorder.copy(alpha = 0.5f)),
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
 * Replaces glass styling with flat Material UI design seamlessly.
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
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
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
 * Modern Material Design Flat Icon Button for map controls and toolbar actions.
 * Flat background, clean icon tint, minimum 48dp interactive touch target.
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
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val isDark = isAppDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isHighlight = isActive || active

    val (bgBase, borderBase, contentBase) = if (isDark) {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                if (isHighlight) Color(0xFFD97706) else Color(0xFF1E293B),
                if (isHighlight) Color(0xFFF59E0B) else Color(0xFF334155),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFFF59E0B)
            )
            GlassButtonVariant.SUCCESS -> Triple(
                if (isHighlight) Color(0xFF16A34A) else Color(0xFF1E293B),
                if (isHighlight) Color(0xFF22C55E) else Color(0xFF334155),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFF22C55E)
            )
            GlassButtonVariant.SKY -> Triple(
                if (isHighlight) Color(0xFF0284C7) else Color(0xFF1E293B),
                if (isHighlight) Color(0xFF38BDF8) else Color(0xFF334155),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFF38BDF8)
            )
            GlassButtonVariant.DESTRUCTIVE -> Triple(
                if (isHighlight) Color(0xFFDC2626) else Color(0xFF1E293B),
                if (isHighlight) Color(0xFFEF4444) else Color(0xFF334155),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFFEF4444)
            )
            GlassButtonVariant.NEUTRAL -> Triple(
                if (isHighlight) Color(0xFF0284C7) else Color(0xFF1E293B),
                if (isHighlight) Color(0xFF38BDF8) else Color(0xFF334155),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFFF8FAFC)
            )
        }
    } else {
        when (variant) {
            GlassButtonVariant.GOLD -> Triple(
                if (isHighlight) Color(0xFFD97706) else Color(0xFFF1F5F9),
                if (isHighlight) Color(0xFFB45309) else Color(0xFFCBD5E1),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFFD97706)
            )
            GlassButtonVariant.SUCCESS -> Triple(
                if (isHighlight) Color(0xFF16A34A) else Color(0xFFF1F5F9),
                if (isHighlight) Color(0xFF15803D) else Color(0xFFCBD5E1),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFF16A34A)
            )
            GlassButtonVariant.SKY -> Triple(
                if (isHighlight) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                if (isHighlight) Color(0xFF0369A1) else Color(0xFFCBD5E1),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFF0284C7)
            )
            GlassButtonVariant.DESTRUCTIVE -> Triple(
                if (isHighlight) Color(0xFFDC2626) else Color(0xFFF1F5F9),
                if (isHighlight) Color(0xFFB91C1C) else Color(0xFFCBD5E1),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFFDC2626)
            )
            GlassButtonVariant.NEUTRAL -> Triple(
                if (isHighlight) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                if (isHighlight) Color(0xFF0284C7) else Color(0xFFCBD5E1),
                if (isHighlight) Color(0xFFFFFFFF) else Color(0xFF0F172A)
            )
        }
    }

    val actualIconSize = iconSize ?: (size.value * 0.48f).dp.coerceIn(16.dp, 22.dp)

    Surface(
        shape = shape,
        color = bgBase,
        border = BorderStroke(0.75.dp, borderBase),
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
    shape: Shape = RoundedCornerShape(10.dp)
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
 * Modern Material 3 Segmented Toggle Pill for Road Side, Lane selection, Auto vs Manual modes, etc.
 * High contrast, clean solid indicator pill, zero glass blurring.
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
    val resolvedActiveColor = activeColor ?: if (isDark) Color(0xFFD97706) else Color(0xFFD97706)
    val surfaceBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val surfaceBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val unselectedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = surfaceBg,
        border = BorderStroke(0.75.dp, surfaceBorder),
        shadowElevation = 0.dp,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
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
                    targetValue = if (isSelected) Color.White else unselectedTextColor,
                    animationSpec = tween(120),
                    label = "pill_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
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
            shape = RoundedCornerShape(14.dp),
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
                        icon = Icons.Default.Check,
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
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) colors.surfaceHighlight else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, colors.gold) else BorderStroke(0.5.dp, colors.cardBorder),
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
