package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HomeScreensaverSettings
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.ScreensaverDisplayMode
import kotlin.math.roundToInt

/**
 * Compact, tactile M3 Control Dock rendered ONLY on the normal Home Page.
 * Lets users configure:
 * 1. Screensaver Display Mode: Portrait / Landscape / Car Dashboard Mirror Mode (coupled to Landscape)
 * 2. Adjustable HUD Reflection Brightness (in Mirror HUD Mode)
 * 3. Shared Mini Map Controls (Orientation: North Up / Direction Up / Auto, Zoom In/Out, Recenter,
 *    Show/Hide, and Style: Standard / Satellite / Dark / Light) used consistently by the Home Map
 *    and all 3 Screensaver Modes.
 */
@Composable
fun HomeScreensaverAndMapControls(
    settings: HomeScreensaverSettings,
    onSelectMode: (ScreensaverDisplayMode) -> Unit,
    onToggleMirror: (Boolean) -> Unit,
    onHudBrightnessChange: (Float) -> Unit,
    onOrientationChange: (MiniMapOrientationMode) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    onToggleMapVisible: () -> Unit,
    onStyleChange: (MiniMapStyle) -> Unit,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    var isControlsExpanded by rememberSaveable(isCompact) {
        mutableStateOf(!isCompact)
    }

    val accentBlue = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
    val accentEmerald = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val subtleChipBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val activeChipBg = if (isDark) Color(0xFF0F2942) else Color(0xFFDBEAFE)
    val activeChipBorder = accentBlue

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_screensaver_controls_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Row 1: Screensaver Mode (Portrait / Landscape) + Mirror Mode Switch + Map/HUD Expand
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val isPortrait = settings.displayMode == ScreensaverDisplayMode.PORTRAIT
                val isLandscapeGroup = settings.isLandscapeLayout
                val isMirror = settings.isMirrorMode

                ModeSelectChip(
                    label = "Portrait",
                    icon = Icons.Default.ScreenLockPortrait,
                    selected = isPortrait,
                    activeBg = activeChipBg,
                    inactiveBg = subtleChipBg,
                    activeColor = accentBlue,
                    inactiveColor = subTextColor,
                    borderColor = if (isPortrait) activeChipBorder else cardBorderColor,
                    onClick = { onSelectMode(ScreensaverDisplayMode.PORTRAIT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_mode_portrait_btn")
                )

                ModeSelectChip(
                    label = "Landscape",
                    icon = Icons.Default.ScreenRotation,
                    selected = isLandscapeGroup,
                    activeBg = activeChipBg,
                    inactiveBg = subtleChipBg,
                    activeColor = accentBlue,
                    inactiveColor = subTextColor,
                    borderColor = if (isLandscapeGroup) activeChipBorder else cardBorderColor,
                    onClick = { onSelectMode(ScreensaverDisplayMode.LANDSCAPE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_mode_landscape_btn")
                )

                // Mirror HUD ON/OFF Pill + Switch (enabling auto-selects Landscape Mode)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isMirror) accentEmerald.copy(alpha = if (isDark) 0.22f else 0.14f) else subtleChipBg,
                    border = BorderStroke(
                        1.dp,
                        if (isMirror) accentEmerald else cardBorderColor
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Switch) { onToggleMirror(!isMirror) }
                        .semantics { selected = isMirror }
                        .testTag("home_mode_mirror_switch")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = "HUD Mirror Mode",
                            tint = if (isMirror) accentEmerald else subTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isMirror) "HUD ON" else "Mirror",
                            color = if (isMirror) accentEmerald else mainTextColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Switch(
                            checked = isMirror,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentEmerald,
                                uncheckedThumbColor = subTextColor,
                                uncheckedTrackColor = cardBorderColor
                            ),
                            modifier = Modifier
                                .height(20.dp)
                                .width(34.dp)
                        )
                    }
                }

                // Expand/collapse shared Mini Map & HUD Brightness panel
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isControlsExpanded) activeChipBg else subtleChipBg,
                    border = BorderStroke(1.dp, if (isControlsExpanded) activeChipBorder else cardBorderColor),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { isControlsExpanded = !isControlsExpanded }
                        .testTag("home_hud_map_controls_toggle")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Map and HUD Controls",
                            tint = if (isControlsExpanded) accentBlue else subTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Icon(
                            imageVector = if (isControlsExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = if (isControlsExpanded) accentBlue else subTextColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Expandable Shared Mini Map + HUD Brightness Panel
            AnimatedVisibility(
                visible = isControlsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HorizontalDivider(color = cardBorderColor.copy(alpha = 0.6f))

                    // Shared Mini Map Controls Strip (Horizontally Scrollable — single row, fast 1-tap access)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Orientation Modes: North Up / Direction Up / Auto
                        MiniMapChip(
                            label = "North Up",
                            icon = Icons.Default.Explore,
                            selected = settings.miniMapOrientation == MiniMapOrientationMode.NORTH_UP,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onOrientationChange(MiniMapOrientationMode.NORTH_UP) },
                            modifier = Modifier.testTag("home_map_orient_north_up")
                        )
                        MiniMapChip(
                            label = "Dir Up",
                            icon = Icons.Default.Explore,
                            selected = settings.miniMapOrientation == MiniMapOrientationMode.DIRECTION_UP,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onOrientationChange(MiniMapOrientationMode.DIRECTION_UP) },
                            modifier = Modifier.testTag("home_map_orient_direction_up")
                        )
                        MiniMapChip(
                            label = "Auto",
                            icon = Icons.Default.Explore,
                            selected = settings.miniMapOrientation == MiniMapOrientationMode.AUTO,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onOrientationChange(MiniMapOrientationMode.AUTO) },
                            modifier = Modifier.testTag("home_map_orient_auto")
                        )

                        // Zoom Out / Zoom In / Recenter / Show-Hide
                        MiniMapChip(
                            label = "Zoom -",
                            icon = Icons.Default.Remove,
                            selected = false,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = onZoomOut,
                            modifier = Modifier.testTag("home_map_zoom_out_btn")
                        )
                        MiniMapChip(
                            label = "Zoom +",
                            icon = Icons.Default.Add,
                            selected = false,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = onZoomIn,
                            modifier = Modifier.testTag("home_map_zoom_in_btn")
                        )
                        MiniMapChip(
                            label = "Center",
                            icon = Icons.Default.MyLocation,
                            selected = false,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = onRecenter,
                            modifier = Modifier.testTag("home_map_recenter_btn")
                        )
                        MiniMapChip(
                            label = if (settings.miniMapVisible) "Map Shown" else "Map Hidden",
                            icon = if (settings.miniMapVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            selected = settings.miniMapVisible,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = subTextColor,
                            borderColor = cardBorderColor,
                            onClick = onToggleMapVisible,
                            modifier = Modifier.testTag("home_map_visibility_btn")
                        )

                        // Map Styles: Standard / Satellite / Dark / Light
                        MiniMapChip(
                            label = "Standard",
                            selected = settings.miniMapStyle == MiniMapStyle.STANDARD,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onStyleChange(MiniMapStyle.STANDARD) },
                            modifier = Modifier.testTag("home_map_style_standard")
                        )
                        MiniMapChip(
                            label = "Satellite",
                            selected = settings.miniMapStyle == MiniMapStyle.SATELLITE,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onStyleChange(MiniMapStyle.SATELLITE) },
                            modifier = Modifier.testTag("home_map_style_satellite")
                        )
                        MiniMapChip(
                            label = "Dark",
                            selected = settings.miniMapStyle == MiniMapStyle.DARK,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onStyleChange(MiniMapStyle.DARK) },
                            modifier = Modifier.testTag("home_map_style_dark")
                        )
                        MiniMapChip(
                            label = "Light",
                            selected = settings.miniMapStyle == MiniMapStyle.LIGHT,
                            activeBg = activeChipBg,
                            inactiveBg = subtleChipBg,
                            activeColor = accentBlue,
                            inactiveColor = mainTextColor,
                            borderColor = cardBorderColor,
                            onClick = { onStyleChange(MiniMapStyle.LIGHT) },
                            modifier = Modifier.testTag("home_map_style_light")
                        )
                    }

                    // Adjustable HUD Brightness Control (for Car Dashboard Windshield Mirror Mode)
                    AnimatedVisibility(visible = settings.isMirrorMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrightnessMedium,
                                contentDescription = "HUD Brightness",
                                tint = accentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "HUD Brightness",
                                color = mainTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Slider(
                                value = settings.hudBrightness,
                                onValueChange = onHudBrightnessChange,
                                valueRange = HomeScreensaverSettings.MIN_HUD_BRIGHTNESS..HomeScreensaverSettings.MAX_HUD_BRIGHTNESS,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentEmerald,
                                    activeTrackColor = accentEmerald,
                                    inactiveTrackColor = cardBorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .testTag("home_hud_brightness_slider")
                            )
                            Text(
                                text = "${(settings.hudBrightness * 100f).roundToInt()}%",
                                color = accentEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeBg: Color,
    inactiveBg: Color,
    activeColor: Color,
    inactiveColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeBg else inactiveBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (selected) activeColor else inactiveColor,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun MiniMapChip(
    label: String,
    icon: ImageVector? = null,
    selected: Boolean,
    activeBg: Color,
    inactiveBg: Color,
    activeColor: Color,
    inactiveColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) activeBg else inactiveBg,
        border = BorderStroke(1.dp, if (selected) activeColor else borderColor),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) activeColor else inactiveColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                color = if (selected) activeColor else inactiveColor,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
