package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cohesive Flat Borderless Dialog, BottomSheet, and Popup Theme Tokens
 * powered directly by the centralized [CtecsTheme] design system.
 *
 * - Dark Mode: Pure AMOLED Deep Black / Graphite surfaces (#0D111A / #111827 / #171E2B) + Electric Blue (#1685FF).
 * - Light Mode: Clean White / Cool Grey surfaces (#FFFFFF / #F1F4F8 / #EAF0F7) + Professional Blue (#1677F2).
 * - Strictly borderless flat design (zero tonal elevation shadows, soft tonal separation).
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
    val shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    val primaryAccent: Color = DarkPrimaryAccent,
    val secondaryAccent: Color = DarkSecondaryAccent,
    val successColor: Color = DarkSuccess,
    val warningColor: Color = DarkWarning,
    val errorColor: Color = DarkError,
    val tonalElevation: Dp = 0.dp,
    val cornerRadius: Dp = 22.dp,
    val dividerColor: Color = Color.Transparent,
    val surfaceCardColor: Color = surfaceColor,
    val inputFocusedBorder: Color = primaryAccent,
    val inputUnfocusedBorder: Color = Color.Transparent
)

@Composable
fun rememberDialogColors(): DialogThemeTokens {
    val c = CtecsTheme.colors
    return if (c.isDark) {
        DialogThemeTokens(
            containerColor = c.surface,             // #0D111A
            surfaceColor = c.secondarySurface,      // #111827
            surfaceVariantColor = c.elevatedSurface,// #171E2B
            titleColor = c.textPrimary,             // #FFFFFF
            textColor = c.textPrimary,              // #FFFFFF
            textSecondaryColor = c.textSecondary,   // #A1AAB9
            borderColor = Color.Transparent,        // Strictly borderless
            inputBackground = c.secondarySurface,   // #111827 tonal fill
            inputBorder = Color.Transparent,
            isDark = true,
            shape = RoundedCornerShape(22.dp),
            primaryAccent = c.accent,               // #1685FF
            secondaryAccent = c.accentSecondary,    // #20B8F0
            successColor = c.success,               // #20C997
            warningColor = c.warning,               // #FFB52E
            errorColor = c.error,                   // #FF5263
            tonalElevation = 0.dp,
            cornerRadius = 22.dp,
            dividerColor = c.elevatedSurface.copy(alpha = 0.55f),
            surfaceCardColor = c.secondarySurface,
            inputFocusedBorder = c.accent,
            inputUnfocusedBorder = Color.Transparent
        )
    } else {
        DialogThemeTokens(
            containerColor = c.background,          // #FFFFFF
            surfaceColor = c.surface,               // #F1F4F8
            surfaceVariantColor = c.secondarySurface,// #EAF0F7
            titleColor = c.textPrimary,             // #101828
            textColor = c.textPrimary,              // #101828
            textSecondaryColor = c.textSecondary,   // #526174
            borderColor = Color.Transparent,        // Strictly borderless
            inputBackground = c.surface,            // #F1F4F8 tonal fill
            inputBorder = Color.Transparent,
            isDark = false,
            shape = RoundedCornerShape(22.dp),
            primaryAccent = c.accent,               // #1677F2
            secondaryAccent = c.accentSecondary,    // #159FD5
            successColor = c.success,               // #159C72
            warningColor = c.warning,               // #D99A20
            errorColor = c.error,                   // #DC4455
            tonalElevation = 0.dp,
            cornerRadius = 22.dp,
            dividerColor = c.secondarySurface,
            surfaceCardColor = c.surface,
            inputFocusedBorder = c.accent,
            inputUnfocusedBorder = Color.Transparent
        )
    }
}
