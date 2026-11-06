package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Cohesive Material Design 3 Dialog & Popup Color Tokens
 * strictly enforcing Light Theme (soft white/off-white, dark charcoal text, muted grey, subtle light-grey borders)
 * and Dark Theme (deep charcoal, light primary text, muted grey, subtle dark borders, no white popup backgrounds).
 */
@Immutable
data class DialogThemeTokens(
    val containerColor: Color,
    val surfaceColor: Color,
    val surfaceVariantColor: Color,
    val titleColor: Color,
    val textColor: Color,
    val textSecondaryColor: Color,
    val borderColor: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val isDark: Boolean,
    val shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    val primaryAccent: Color = Color(0xFF2563EB),
    val errorColor: Color = Color(0xFFEF4444),
    val tonalElevation: androidx.compose.ui.unit.Dp = 6.dp,
    val cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    val dividerColor: Color = borderColor,
    val surfaceCardColor: Color = surfaceColor,
    val inputFocusedBorder: Color = Color(0xFF2563EB),
    val inputUnfocusedBorder: Color = inputBorder
)

@Composable
fun rememberDialogColors(): DialogThemeTokens {
    val isDark = isAppDarkTheme()
    return if (isDark) {
        DialogThemeTokens(
            containerColor = Color(0xFF18181B),       // Deep charcoal (Zinc 900)
            surfaceColor = Color(0xFF27272A),         // Zinc 800
            surfaceVariantColor = Color(0xFF202024),  // Charcoal variant
            titleColor = Color(0xFFF4F4F5),          // Light primary text
            textColor = Color(0xFFE4E4E7),           // Crisp light text
            textSecondaryColor = Color(0xFFA1A1AA),   // Muted grey secondary text
            borderColor = Color(0xFF3F3F46),          // Subtle dark border (Zinc 700)
            inputBackground = Color(0xFF27272A),      // Visible input fill
            inputBorder = Color(0xFF3F3F46),          // Subtle dark input border
            isDark = true,
            shape = RoundedCornerShape(20.dp),
            primaryAccent = Color(0xFF3B82F6),
            errorColor = Color(0xFFF87171),
            tonalElevation = 6.dp,
            cornerRadius = 20.dp,
            dividerColor = Color(0xFF3F3F46),
            surfaceCardColor = Color(0xFF27272A),
            inputFocusedBorder = Color(0xFF3B82F6),
            inputUnfocusedBorder = Color(0xFF3F3F46)
        )
    } else {
        DialogThemeTokens(
            containerColor = Color(0xFFFFFFFF),       // Soft white background
            surfaceColor = Color(0xFFF4F4F5),         // Off-white surface
            surfaceVariantColor = Color(0xFFE4E4E7),  // Subtle secondary container
            titleColor = Color(0xFF0F172A),          // Dark charcoal title
            textColor = Color(0xFF1E293B),           // Dark charcoal body text
            textSecondaryColor = Color(0xFF64748B),   // Muted grey secondary text
            borderColor = Color(0xFFE2E8F0),          // Subtle light-grey border
            inputBackground = Color(0xFFFFFFFF),      // Clean white input fill
            inputBorder = Color(0xFFCBD5E1),          // Light grey input border
            isDark = false,
            shape = RoundedCornerShape(20.dp),
            primaryAccent = Color(0xFF2563EB),
            errorColor = Color(0xFFEF4444),
            tonalElevation = 6.dp,
            cornerRadius = 20.dp,
            dividerColor = Color(0xFFE2E8F0),
            surfaceCardColor = Color(0xFFF4F4F5),
            inputFocusedBorder = Color(0xFF2563EB),
            inputUnfocusedBorder = Color(0xFFCBD5E1)
        )
    }
}
