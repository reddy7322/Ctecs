package com.example.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.update.AppReleaseChannel
import com.example.update.AppReleaseInfo

/**
 * Sleek, compact, borderless Dark / Light theme selector placed directly at the top
 * of the Settings Page for instant AMOLED True-Black Dark Mode & Clean White Light Mode switching.
 */
@Composable
fun MinimalistThemeSelectorBar(
    themeMode: String,
    isCurrentlyDark: Boolean,
    onSelectThemeMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val normalized = themeMode.trim().lowercase()
    val isDarkSelected = normalized == "dark" || normalized == "amoled" || (normalized == "system" && isCurrentlyDark)
    val isLightSelected = !isDarkSelected

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("settings_appearance_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Appearance",
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    ScopedSettingSaveBadge(settingKey = "theme_mode")
                }
                Text(
                    text = if (isDarkSelected) "True Black AMOLED Dark" else "Clean Minimalist Light",
                    color = c.textSecondary,
                    fontSize = 11.5.sp
                )
            }
            // Compact Dark / Light toggle pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.secondarySurface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeModePillButton(
                    label = "Dark",
                    icon = Icons.Rounded.DarkMode,
                    selected = isDarkSelected,
                    onClick = { onSelectThemeMode("dark") },
                    modifier = Modifier.testTag("theme_mode_dark")
                )
                ThemeModePillButton(
                    label = "Light",
                    icon = Icons.Rounded.LightMode,
                    selected = isLightSelected,
                    onClick = { onSelectThemeMode("light") },
                    modifier = Modifier.testTag("theme_mode_light")
                )
            }
        }
    }
}

@Composable
private fun ThemeModePillButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val containerColor by animateColorAsState(
        targetValue = if (selected) c.accent else Color.Transparent,
        animationSpec = tween(180),
        label = "theme_pill_bg_$label"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) c.onAccent else c.textSecondary,
        animationSpec = tween(180),
        label = "theme_pill_fg_$label"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "theme_pill_scale_$label"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minWidth = 70.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(containerColor)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$label theme",
            tint = contentColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Permanently visible App Information section directly on the main Settings Page.
 *
 * STRICT RULES:
 * - NO dropdown, NO expand/collapse, NO chevron, NO hidden content, NO separate page.
 * - Preserves all existing App Information details (app icon, CTECS name, version & build,
 *   release channel, update checker, reset defaults, exact developer credit).
 * - Hosts ONE dedicated, sleek animated "Buy Me a Coffee ☕" button directly inside the section.
 */
@Composable
fun AlwaysVisibleAppInfoSection(
    isCheckingUpdate: Boolean,
    availableUpdate: AppReleaseInfo?,
    isDownloadingUpdate: Boolean,
    updateDownloadProgress: Float,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstallUpdate: () -> Unit,
    onRequestResetDefaults: () -> Unit,
    onOpenBuyMeACoffee: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val currentChannel = remember { AppReleaseChannel.current() }
    val isBetaApp = currentChannel == AppReleaseChannel.BETA
    val channelColor = if (isBetaApp) {
        if (c.isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    } else {
        c.accent
    }

    val appName = remember(context) {
        try {
            context.getString(R.string.app_name)
        } catch (_: Exception) {
            "CTECS"
        }
    }

    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val width = drawable.intrinsicWidth.coerceIn(64, 144)
            val height = drawable.intrinsicHeight.coerceIn(64, 144)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("settings_app_info_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section label (static, borderless — NEVER expandable or collapsible)
        Text(
            text = "APP INFORMATION",
            color = c.textMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )

        // App Icon + CTECS Name + Version & Build + Release Channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "$appName app icon",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CT",
                        color = c.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (appName.equals("Ctecs", ignoreCase = true)) "CTECS" else appName,
                        color = c.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("app_info_name")
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = channelColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBetaApp) "BETA" else "STABLE",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = channelColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("app_info_version")
                )
                Text(
                    text = if (isBetaApp) "Update Channel: Beta" else "Update Channel: Stable",
                    color = c.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Available Update Status (preserved exactly from existing implementation)
        availableUpdate?.let { updateInfo ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.secondarySurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = channelColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Update Available: ${updateInfo.tagName}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = c.textPrimary
                        )
                    }
                    if (updateInfo.apkSize > 0) {
                        Text(
                            text = "%.1f MB".format(updateInfo.apkSize / (1024.0 * 1024.0)),
                            fontSize = 11.sp,
                            color = c.textSecondary
                        )
                    }
                }

                if (isDownloadingUpdate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Downloading update...", fontSize = 11.sp, color = channelColor)
                        Text(
                            "${(updateDownloadProgress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { updateDownloadProgress },
                        color = channelColor,
                        trackColor = c.elevatedSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Button(
                        onClick = onDownloadAndInstallUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = channelColor, contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download & Install Update", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Sleek Action Row: Check for Updates + Reset All Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isCheckingUpdate) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.secondarySurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = channelColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking...", fontSize = 12.sp, color = c.textSecondary)
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.secondarySurface)
                        .clickable(onClick = onCheckForUpdate)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("app_info_check_updates_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = "Check for updates",
                        tint = channelColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBetaApp) "Beta Updates" else "Check Updates",
                        color = c.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.error.copy(alpha = if (c.isDark) 0.14f else 0.09f))
                    .clickable(onClick = onRequestResetDefaults)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("app_info_reset_defaults_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = "Reset all settings to defaults",
                    tint = c.error,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reset Defaults",
                    color = c.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SmartFadeDivider(color = c.subtleSeparator)

        // Preserved exact Developer Credit
        Text(
            text = "Made with ❤️ madhureddyainala",
            color = c.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_info_developer_credit")
        )

        SmartFadeDivider(color = c.subtleSeparator)

        // One dedicated, sleek animated Buy Me a Coffee button directly inside App Information
        BuyMeACoffeeActionButton(
            onClick = onOpenBuyMeACoffee,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BuyMeACoffeeActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "buy_coffee_press_scale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.coffeeSurface)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = c.coffeeAccent),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("buy_me_a_coffee_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Coffee,
            contentDescription = "Coffee icon",
            tint = c.coffeeAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Buy Me a Coffee ☕",
            color = c.coffeeAccent,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
