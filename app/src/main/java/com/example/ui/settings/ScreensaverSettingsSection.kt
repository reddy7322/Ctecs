package com.example.ui.settings

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.location.LocationState
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudAnimationSpeed
import com.example.model.HudColorPreset
import com.example.model.HudDirectionColorMode
import com.example.model.HudDirectionPosition
import com.example.model.HudDirectionStyle
import com.example.model.HudElementSize
import com.example.model.HudFontStyle
import com.example.model.HudLocationPosition
import com.example.model.HudMapMarkerStyle
import com.example.model.HudMapPosition
import com.example.model.HudPanelSize
import com.example.model.HudSpeedometerStyle
import com.example.model.HudVerticalPosition
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.ProjectProfile
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.ScreensaverChainageStateHolder
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.applyScreensaverImmersiveMode
import com.example.util.KeepScreenOn
import kotlin.math.roundToInt

/**
 * Complete `Settings -> Screensaver` configuration workspace housing:
 * - General Screensaver behavior (Master, Auto-Activate, Timeout presets/custom, Touch-to-Dismiss, Auto-Restart)
 * - Display Modes (Portrait / Landscape / Car Dashboard Mirror HUD & Brightness)
 * - Dashboard element customization (Chainage, LHS/RHS Direction, Speedometer, Location)
 * - Shared Mini Map preferences (synchronized with Home Page and In-App Screensaver)
 * - Real-time interactive Previews (Portrait, Landscape, Mirror HUD) & Reset Defaults
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreensaverSettingsSectionContent(
    settings: HomeScreensaverSettings,
    matchResult: ChainageMatchResult = ChainageMatchResult(),
    locationState: LocationState = LocationState(),
    activeRoute: RouteData? = null,
    projectProfile: ProjectProfile = ProjectProfile(),
    compassHeading: Float = 0f,
    currentLocationName: String = "",
    mapThemePreference: String = "system",
    mapTypePreference: String = "standard",
    accentColor: Color = Color(0xFF38BDF8),
    secondaryColor: Color = Color(0xFF2DD4BF),
    onUpdateSettings: ((HomeScreensaverSettings) -> HomeScreensaverSettings) -> Unit,
    onSelectDisplayMode: (ScreensaverDisplayMode) -> Unit,
    onToggleMirror: (Boolean) -> Unit,
    onHudBrightnessChange: (Float) -> Unit,
    onMiniMapOrientationChange: (MiniMapOrientationMode) -> Unit,
    onMiniMapZoomIn: () -> Unit,
    onMiniMapZoomOut: () -> Unit,
    onMiniMapRecenter: () -> Unit,
    onMiniMapStyleChange: (MiniMapStyle) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isDark = com.example.ui.theme.isAppDarkTheme()

    val subExpanded = remember {
        mutableStateMapOf(
            "general" to true,
            "display_mode" to false,
            "chainage" to false,
            "direction" to false,
            "speedometer" to false,
            "location" to false,
            "mini_map" to false,
            "previews_reset" to false
        )
    }

    var previewScreensaverMode by remember { mutableStateOf<ScreensaverDisplayMode?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var customTimeoutText by remember(settings.inactivityTimeoutSeconds) {
        mutableStateOf(settings.inactivityTimeoutSeconds.toString())
    }
    var customTimeoutError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ============================================================
        // 1. GENERAL SCREENSAVER SETTINGS
        // ============================================================
        ScreensaverSubDropdown(
            title = "General",
            subtitle = if (settings.masterEnabled) {
                "${settings.inactivityTimeoutSeconds}s timeout • " +
                    if (settings.autoActivateEnabled) "Auto ON" else "Auto OFF"
            } else {
                "Disabled"
            },
            icon = Icons.Default.Bedtime,
            accentColor = accentColor,
            isExpanded = subExpanded["general"] == true,
            onToggle = { subExpanded["general"] = !(subExpanded["general"] ?: false) },
            headerTag = "screensaver_section_general"
        ) {
            ScreensaverSwitchRow(
                title = "Enable Screensaver",
                subtitle = null,
                checked = settings.masterEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(masterEnabled = enabled) }
                },
                accentColor = accentColor,
                tag = "screensaver_master_switch"
            )

            ScreensaverSwitchRow(
                title = "Automatic Activation",
                subtitle = "Open after Home Page inactivity",
                checked = settings.autoActivateEnabled,
                enabled = settings.masterEnabled,
                onCheckedChange = { auto ->
                    onUpdateSettings { it.copy(autoActivateEnabled = auto) }
                },
                accentColor = accentColor,
                tag = "screensaver_auto_activate_switch"
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Inactivity Timeout (${settings.inactivityTimeoutSeconds}s)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeScreensaverSettings.TIMEOUT_PRESETS_SEC.forEach { presetSec ->
                    val selected = !settings.isCustomTimeout && settings.inactivityTimeoutSeconds == presetSec
                    FilterChip(
                        selected = selected,
                        enabled = settings.masterEnabled,
                        onClick = {
                            customTimeoutError = null
                            onUpdateSettings { it.withTimeoutSeconds(presetSec, custom = false) }
                        },
                        label = {
                            Text("${presetSec}s", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = if (isDark) Color.Black else Color.White
                        ),
                        modifier = Modifier.testTag("screensaver_timeout_${presetSec}s")
                    )
                }
                FilterChip(
                    selected = settings.isCustomTimeout,
                    enabled = settings.masterEnabled,
                    onClick = {
                        onUpdateSettings { it.copy(isCustomTimeout = true) }
                    },
                    label = {
                        Text("Custom", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = secondaryColor,
                        selectedLabelColor = if (isDark) Color.Black else Color.White
                    ),
                    modifier = Modifier.testTag("screensaver_timeout_custom_chip")
                )
            }

            if (settings.isCustomTimeout) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customTimeoutText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            customTimeoutText = digits
                            val parsed = digits.toIntOrNull()
                            customTimeoutError = when {
                                digits.isEmpty() -> "Enter seconds (${HomeScreensaverSettings.MIN_TIMEOUT_SEC}–${HomeScreensaverSettings.MAX_TIMEOUT_SEC})"
                                parsed == null || parsed < HomeScreensaverSettings.MIN_TIMEOUT_SEC ->
                                    "Minimum ${HomeScreensaverSettings.MIN_TIMEOUT_SEC}s"
                                parsed > HomeScreensaverSettings.MAX_TIMEOUT_SEC ->
                                    "Maximum ${HomeScreensaverSettings.MAX_TIMEOUT_SEC}s"
                                else -> null
                            }
                        },
                        label = { Text("Custom timeout (seconds)", fontSize = 11.5.sp) },
                        isError = customTimeoutError != null,
                        supportingText = customTimeoutError?.let { err -> { Text(err, fontSize = 10.sp) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val parsed = customTimeoutText.toIntOrNull()
                                if (parsed != null && parsed in HomeScreensaverSettings.MIN_TIMEOUT_SEC..HomeScreensaverSettings.MAX_TIMEOUT_SEC) {
                                    customTimeoutError = null
                                    onUpdateSettings { it.withTimeoutSeconds(parsed, custom = true) }
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("screensaver_timeout_custom_input")
                    )
                    Button(
                        onClick = {
                            val parsed = customTimeoutText.toIntOrNull()
                            if (parsed != null && parsed in HomeScreensaverSettings.MIN_TIMEOUT_SEC..HomeScreensaverSettings.MAX_TIMEOUT_SEC) {
                                customTimeoutError = null
                                onUpdateSettings { it.withTimeoutSeconds(parsed, custom = true) }
                                focusManager.clearFocus()
                            } else {
                                customTimeoutError = "Range ${HomeScreensaverSettings.MIN_TIMEOUT_SEC}–${HomeScreensaverSettings.MAX_TIMEOUT_SEC}s"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = if (isDark) Color.Black else Color.White
                        ),
                        modifier = Modifier.testTag("screensaver_timeout_custom_apply")
                    ) {
                        Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ScreensaverSwitchRow(
                title = "Touch anywhere to dismiss",
                subtitle = null,
                checked = settings.touchToDismiss,
                enabled = settings.masterEnabled,
                onCheckedChange = { touch ->
                    onUpdateSettings { it.copy(touchToDismiss = touch) }
                },
                accentColor = accentColor,
                tag = "screensaver_touch_dismiss_switch"
            )

            ScreensaverSwitchRow(
                title = "Automatic timer restart",
                subtitle = "Restart countdown after dismissal",
                checked = settings.autoRestartTimer,
                enabled = settings.masterEnabled,
                onCheckedChange = { restart ->
                    onUpdateSettings { it.copy(autoRestartTimer = restart) }
                },
                accentColor = accentColor,
                tag = "screensaver_auto_restart_switch"
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { previewScreensaverMode = settings.displayMode },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screensaver_quick_preview_btn"),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Active Mode", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                }
                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier.testTag("screensaver_quick_reset_btn"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ============================================================
        // 2. DISPLAY MODE, AUTO ORIENTATION & CAR DASHBOARD MIRROR HUD
        // ============================================================
        ScreensaverSubDropdown(
            title = "Display Mode & HUD Mirror",
            subtitle = "${if (settings.autoOrientationEnabled) "Auto Orient" else settings.displayMode.label} • ${if (settings.mirrorModePreferred || settings.isMirrorMode) "Mirror ON" else "Normal"} • ${(settings.hudBrightness * 100).roundToInt()}%",
            icon = Icons.Default.DisplaySettings,
            accentColor = accentColor,
            isExpanded = subExpanded["display_mode"] == true,
            onToggle = { subExpanded["display_mode"] = !(subExpanded["display_mode"] ?: false) },
            headerTag = "screensaver_section_display_mode"
        ) {
            ScreensaverSwitchRow(
                title = "Auto Orientation",
                subtitle = "Adapt Portrait & Landscape automatically from device rotation",
                checked = settings.autoOrientationEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.withAutoOrientation(enabled) }
                },
                accentColor = accentColor,
                tag = "screensaver_auto_orientation_switch"
            )

            Text(
                text = if (settings.autoOrientationEnabled) "Default / Fallback Layout Mode" else "Screensaver Layout Mode",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScreensaverDisplayMode.entries.forEach { mode ->
                    val selected = settings.displayMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDisplayMode(mode) },
                        label = { Text(mode.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = if (isDark) Color.Black else Color.White
                        ),
                        modifier = Modifier.testTag("screensaver_mode_${mode.name.lowercase()}")
                    )
                }
            }

            ScreensaverSwitchRow(
                title = "Car Dashboard Mirror Mode (HUD)",
                subtitle = "Mirrors Landscape HUD for windshield reflection (Portrait stays upright Normal)",
                checked = settings.mirrorModePreferred || settings.isMirrorMode,
                onCheckedChange = onToggleMirror,
                accentColor = secondaryColor,
                tag = "screensaver_mirror_switch"
            )

            ScreensaverSwitchRow(
                title = "Keep Screen Awake",
                subtitle = "Prevent display sleep while Screensaver is active",
                checked = settings.keepScreenAwake,
                onCheckedChange = { awake ->
                    onUpdateSettings { it.withKeepScreenAwake(awake) }
                },
                accentColor = accentColor,
                tag = "screensaver_keep_awake_switch"
            )

            ScreensaverSwitchRow(
                title = "Orientation Lock",
                subtitle = "Hold current Portrait or Landscape orientation",
                checked = settings.orientationLocked,
                onCheckedChange = { locked ->
                    onUpdateSettings { it.withOrientationLock(locked) }
                },
                accentColor = accentColor,
                tag = "screensaver_orientation_lock_switch"
            )

            OptionChipGroup(
                label = "Theme",
                options = ScreensaverThemeMode.entries,
                selected = settings.screensaverThemeMode,
                labelFor = { it.label },
                onSelect = { mode -> onUpdateSettings { it.withThemeMode(mode) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_theme"
            )

            ScreensaverSliderRow(
                title = "Windshield HUD Brightness",
                value = settings.hudBrightness,
                valueRange = HomeScreensaverSettings.MIN_HUD_BRIGHTNESS..HomeScreensaverSettings.MAX_HUD_BRIGHTNESS,
                valueText = "${(settings.hudBrightness * 100).roundToInt()}%",
                accentColor = accentColor,
                onValueChange = onHudBrightnessChange,
                tag = "screensaver_brightness_slider"
            )
        }

        // ============================================================
        // 3. DASHBOARD CUSTOMISATION — CHAINAGE
        // ============================================================
        ScreensaverSubDropdown(
            title = "Chainage Display",
            subtitle = "${if (settings.showChainage) "Visible" else "Hidden"} • ${settings.chainageFontSize.label} • ${settings.chainageFontStyle.label}",
            icon = Icons.Default.LinearScale,
            accentColor = accentColor,
            isExpanded = subExpanded["chainage"] == true,
            onToggle = { subExpanded["chainage"] = !(subExpanded["chainage"] ?: false) },
            headerTag = "screensaver_section_chainage"
        ) {
            ScreensaverSwitchRow(
                title = "Show Live Chainage",
                subtitle = "Display calibrated chainage hero readout in screensaver",
                checked = settings.showChainage,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showChainage = v) } },
                accentColor = accentColor,
                tag = "screensaver_show_chainage_switch"
            )
            OptionChipGroup(
                label = "Font Size",
                options = HudElementSize.entries,
                selected = settings.chainageFontSize,
                labelFor = { it.label },
                onSelect = { s -> onUpdateSettings { it.copy(chainageFontSize = s) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_chainage_size"
            )
            OptionChipGroup(
                label = "Font Style",
                options = HudFontStyle.entries,
                selected = settings.chainageFontStyle,
                labelFor = { it.label },
                onSelect = { st -> onUpdateSettings { it.copy(chainageFontStyle = st) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_chainage_font"
            )
            OptionChipGroup(
                label = "Text Color",
                options = HudColorPreset.entries,
                selected = settings.chainageColorPreset,
                labelFor = { it.label },
                onSelect = { c -> onUpdateSettings { it.copy(chainageColorPreset = c) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_chainage_color"
            )
            ScreensaverSwitchRow(
                title = "Odometer Rolling Animation",
                subtitle = "Smooth drum-roll transition between chainage digits",
                checked = settings.chainageOdometerAnimation,
                onCheckedChange = { v -> onUpdateSettings { it.copy(chainageOdometerAnimation = v) } },
                accentColor = accentColor,
                tag = "screensaver_chainage_odometer_switch"
            )
            OptionChipGroup(
                label = "Animation Speed",
                options = HudAnimationSpeed.entries,
                selected = settings.chainageAnimationSpeed,
                labelFor = { it.label },
                onSelect = { sp -> onUpdateSettings { it.copy(chainageAnimationSpeed = sp) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_chainage_speed"
            )
            OptionChipGroup(
                label = "Panel Size",
                options = HudPanelSize.entries,
                selected = settings.chainagePanelSize,
                labelFor = { it.label },
                onSelect = { p -> onUpdateSettings { it.copy(chainagePanelSize = p) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_chainage_panel_size"
            )
            OptionChipGroup(
                label = "Panel Alignment",
                options = HudVerticalPosition.entries,
                selected = settings.chainagePanelPosition,
                labelFor = { it.label },
                onSelect = { pos -> onUpdateSettings { it.copy(chainagePanelPosition = pos) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_chainage_pos"
            )
        }

        // ============================================================
        // 4. DASHBOARD CUSTOMISATION — LHS / RHS DIRECTION
        // ============================================================
        ScreensaverSubDropdown(
            title = "LHS / RHS Direction Indicator",
            subtitle = "${if (settings.showDirectionSide) "Visible" else "Hidden"} • ${settings.directionIndicatorStyle.label} • ${settings.directionPosition.label}",
            icon = Icons.Default.Explore,
            accentColor = secondaryColor,
            isExpanded = subExpanded["direction"] == true,
            onToggle = { subExpanded["direction"] = !(subExpanded["direction"] ?: false) },
            headerTag = "screensaver_section_direction"
        ) {
            ScreensaverSwitchRow(
                title = "Show LHS / RHS Indicator",
                subtitle = "Displays verified road side and travel arrow when alignment lock is reliable",
                checked = settings.showDirectionSide,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showDirectionSide = v) } },
                accentColor = secondaryColor,
                tag = "screensaver_show_direction_switch"
            )
            OptionChipGroup(
                label = "Badge Style",
                options = HudDirectionStyle.entries,
                selected = settings.directionIndicatorStyle,
                labelFor = { it.label },
                onSelect = { st -> onUpdateSettings { it.copy(directionIndicatorStyle = st) } },
                accentColor = secondaryColor,
                tagPrefix = "screensaver_direction_style"
            )
            ScreensaverSwitchRow(
                title = "Animated Directional Arrows",
                subtitle = "Subtle pulsing arrow motion indicating increasing/decreasing side",
                checked = settings.directionArrowsAnimated,
                onCheckedChange = { v -> onUpdateSettings { it.copy(directionArrowsAnimated = v) } },
                accentColor = secondaryColor,
                tag = "screensaver_direction_anim_switch"
            )
            OptionChipGroup(
                label = "Color Mode",
                options = HudDirectionColorMode.entries,
                selected = settings.directionColorPreset,
                labelFor = { it.label },
                onSelect = { cm -> onUpdateSettings { it.copy(directionColorPreset = cm) } },
                accentColor = secondaryColor,
                tagPrefix = "screensaver_direction_color"
            )
            OptionChipGroup(
                label = "Indicator Size",
                options = HudElementSize.entries,
                selected = settings.directionSize,
                labelFor = { it.label },
                onSelect = { sz -> onUpdateSettings { it.copy(directionSize = sz) } },
                accentColor = secondaryColor,
                tagPrefix = "screensaver_direction_size"
            )
            OptionChipGroup(
                label = "Indicator Position",
                options = HudDirectionPosition.entries,
                selected = settings.directionPosition,
                labelFor = { it.label },
                onSelect = { dp -> onUpdateSettings { it.copy(directionPosition = dp) } },
                accentColor = secondaryColor,
                tagPrefix = "screensaver_direction_pos"
            )
        }

        // ============================================================
        // 5. DASHBOARD CUSTOMISATION — SPEEDOMETER
        // ============================================================
        ScreensaverSubDropdown(
            title = "Speedometer Gauge",
            subtitle = "${if (settings.showSpeedometer) "Visible" else "Hidden"} • ${settings.speedometerStyle.label} • ${settings.speedometerSize.label}",
            icon = Icons.Default.Speed,
            accentColor = accentColor,
            isExpanded = subExpanded["speedometer"] == true,
            onToggle = { subExpanded["speedometer"] = !(subExpanded["speedometer"] ?: false) },
            headerTag = "screensaver_section_speedometer"
        ) {
            ScreensaverSwitchRow(
                title = "Show Live GPS Speedometer",
                subtitle = "Displays real GPS velocity in km/h (or -- km/h when unavailable)",
                checked = settings.showSpeedometer,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showSpeedometer = v) } },
                accentColor = accentColor,
                tag = "screensaver_show_speedometer_switch"
            )
            OptionChipGroup(
                label = "Speedometer Style",
                options = HudSpeedometerStyle.entries,
                selected = settings.speedometerStyle,
                labelFor = { it.label },
                onSelect = { st -> onUpdateSettings { it.copy(speedometerStyle = st) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_speed_style"
            )
            OptionChipGroup(
                label = "Display Size",
                options = HudElementSize.entries,
                selected = settings.speedometerSize,
                labelFor = { it.label },
                onSelect = { sz -> onUpdateSettings { it.copy(speedometerSize = sz) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_speed_size"
            )
            OptionChipGroup(
                label = "Accent Color",
                options = HudColorPreset.entries,
                selected = settings.speedometerColorPreset,
                labelFor = { it.label },
                onSelect = { cp -> onUpdateSettings { it.copy(speedometerColorPreset = cp) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_speed_color"
            )
            ScreensaverSwitchRow(
                title = "Animate Speedometer Needle",
                subtitle = "Smooth needle sweep on circular instrument gauge",
                checked = settings.speedometerAnimateNeedle,
                onCheckedChange = { v -> onUpdateSettings { it.copy(speedometerAnimateNeedle = v) } },
                accentColor = accentColor,
                tag = "screensaver_speed_needle_switch"
            )
            ScreensaverSwitchRow(
                title = "Animate Digital Numbers",
                subtitle = "Smooth numeric speed transitions",
                checked = settings.speedometerAnimateNumbers,
                onCheckedChange = { v -> onUpdateSettings { it.copy(speedometerAnimateNumbers = v) } },
                accentColor = accentColor,
                tag = "screensaver_speed_numbers_switch"
            )
        }

        // ============================================================
        // 6. DASHBOARD CUSTOMISATION — LOCATION NAME
        // ============================================================
        ScreensaverSubDropdown(
            title = "Location Name Display",
            subtitle = "${if (settings.showLocation) "Visible" else "Hidden"} • ${settings.locationPosition.label}",
            icon = Icons.Default.Place,
            accentColor = secondaryColor,
            isExpanded = subExpanded["location"] == true,
            onToggle = { subExpanded["location"] = !(subExpanded["location"] ?: false) },
            headerTag = "screensaver_section_location"
        ) {
            ScreensaverSwitchRow(
                title = "Show Current Location Name",
                subtitle = "Reverse-geocoded village, locality, or highway segment name",
                checked = settings.showLocation,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showLocation = v) } },
                accentColor = secondaryColor,
                tag = "screensaver_show_location_switch"
            )
            OptionChipGroup(
                label = "Font Size",
                options = HudElementSize.entries,
                selected = settings.locationFontSize,
                labelFor = { it.label },
                onSelect = { sz -> onUpdateSettings { it.copy(locationFontSize = sz) } },
                accentColor = secondaryColor,
                tagPrefix = "screensaver_location_size"
            )
            OptionChipGroup(
                label = "Position",
                options = HudLocationPosition.entries,
                selected = settings.locationPosition,
                labelFor = { it.label },
                onSelect = { lp -> onUpdateSettings { it.copy(locationPosition = lp) } },
                accentColor = secondaryColor,
                tagPrefix = "screensaver_location_pos"
            )
            ScreensaverSwitchRow(
                title = "Dynamic Reverse-Geocoding Updates",
                subtitle = "Automatically refresh place names as vehicle moves across locations",
                checked = settings.locationDynamicUpdates,
                onCheckedChange = { v -> onUpdateSettings { it.copy(locationDynamicUpdates = v) } },
                accentColor = secondaryColor,
                tag = "screensaver_location_dynamic_switch"
            )
        }

        // ============================================================
        // 7. SHARED MINI MAP SETTINGS (HOME + SCREENSAVER)
        // ============================================================
        ScreensaverSubDropdown(
            title = "Shared Mini Map",
            subtitle = "${if (settings.miniMapVisible) "Visible" else "Hidden"} • ${settings.miniMapOrientation.label} • ${settings.miniMapStyle.label}",
            icon = Icons.Default.Map,
            accentColor = accentColor,
            isExpanded = subExpanded["mini_map"] == true,
            onToggle = { subExpanded["mini_map"] = !(subExpanded["mini_map"] ?: false) },
            headerTag = "screensaver_section_minimap"
        ) {
            ScreensaverSwitchRow(
                title = "Show Shared Mini Map",
                subtitle = "Home Page & Screensaver",
                checked = settings.miniMapVisible,
                onCheckedChange = { v -> onUpdateSettings { it.withMiniMapVisible(v) } },
                accentColor = accentColor,
                tag = "screensaver_minimap_visible_switch"
            )

            OptionChipGroup(
                label = "Orientation",
                options = MiniMapOrientationMode.entries,
                selected = settings.miniMapOrientation,
                labelFor = { it.label },
                onSelect = onMiniMapOrientationChange,
                accentColor = accentColor,
                tagPrefix = "screensaver_map_orient"
            )

            // Zoom Level & Recenter Row
            Text(
                text = "Zoom Level (${String.format(java.util.Locale.US, "%.2f", settings.miniMapZoomLevel)}x) & Step",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMiniMapZoomOut,
                    modifier = Modifier.testTag("screensaver_map_zoom_out_btn")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Out", fontSize = 11.5.sp)
                }
                OutlinedButton(
                    onClick = onMiniMapZoomIn,
                    modifier = Modifier.testTag("screensaver_map_zoom_in_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("In", fontSize = 11.5.sp)
                }
                OutlinedButton(
                    onClick = onMiniMapRecenter,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screensaver_map_recenter_btn")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recenter", fontSize = 11.5.sp, color = accentColor)
                }
            }

            OptionChipGroup(
                label = "Zoom Step",
                options = listOf(0.50f, 0.75f, 1.25f),
                selected = listOf(0.50f, 0.75f, 1.25f).minBy { kotlin.math.abs(it - settings.miniMapZoomStep) },
                labelFor = { "${it}x" },
                onSelect = { step -> onUpdateSettings { it.copy(miniMapZoomStep = step) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_map_zoom_step"
            )

            OptionChipGroup(
                label = "Map Visual Style",
                options = MiniMapStyle.entries,
                selected = settings.miniMapStyle,
                labelFor = { it.label },
                onSelect = onMiniMapStyleChange,
                accentColor = accentColor,
                tagPrefix = "screensaver_map_style"
            )

            OptionChipGroup(
                label = "Map Panel Size",
                options = HudPanelSize.entries,
                selected = settings.miniMapSize,
                labelFor = { it.label },
                onSelect = { sz -> onUpdateSettings { it.copy(miniMapSize = sz) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_map_size"
            )

            OptionChipGroup(
                label = "Screensaver Map Position",
                options = HudMapPosition.entries,
                selected = settings.miniMapPosition,
                labelFor = { it.label },
                onSelect = { pos -> onUpdateSettings { it.copy(miniMapPosition = pos) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_map_pos"
            )

            ScreensaverSliderRow(
                title = "Map Panel Opacity",
                value = settings.miniMapOpacity,
                valueRange = HomeScreensaverSettings.MIN_MINI_MAP_OPACITY..1.0f,
                valueText = "${(settings.miniMapOpacity * 100).roundToInt()}%",
                accentColor = accentColor,
                onValueChange = { op -> onUpdateSettings { it.withMiniMapOpacity(op) } },
                tag = "screensaver_map_opacity_slider"
            )

            ScreensaverSwitchRow(
                title = "Show Map Frame Border",
                subtitle = null,
                checked = settings.miniMapShowBorder,
                onCheckedChange = { v -> onUpdateSettings { it.copy(miniMapShowBorder = v) } },
                accentColor = accentColor,
                tag = "screensaver_map_border_switch"
            )

            if (settings.miniMapShowBorder) {
                OptionChipGroup(
                    label = "Border Accent Color",
                    options = HudColorPreset.entries,
                    selected = settings.miniMapBorderColorPreset,
                    labelFor = { it.label },
                    onSelect = { cp -> onUpdateSettings { it.copy(miniMapBorderColorPreset = cp) } },
                    accentColor = accentColor,
                    tagPrefix = "screensaver_map_border_color"
                )
            }

            OptionChipGroup(
                label = "GPS Vehicle Marker",
                options = HudMapMarkerStyle.entries,
                selected = settings.miniMapMarkerStyle,
                labelFor = { it.label },
                onSelect = { ms -> onUpdateSettings { it.copy(miniMapMarkerStyle = ms) } },
                accentColor = accentColor,
                tagPrefix = "screensaver_map_marker_style"
            )

            ScreensaverSwitchRow(
                title = "Show KMZ/KML Route Alignment",
                subtitle = null,
                checked = settings.miniMapShowRouteAlignment,
                onCheckedChange = { v -> onUpdateSettings { it.copy(miniMapShowRouteAlignment = v) } },
                accentColor = accentColor,
                tag = "screensaver_map_alignment_switch"
            )
        }

        // ============================================================
        // 8. PREVIEWS & RESET SCREENSAVER SETTINGS
        // ============================================================
        ScreensaverSubDropdown(
            title = "Live Previews & Reset",
            subtitle = "Portrait, Landscape & Mirror HUD",
            icon = Icons.Default.Tune,
            accentColor = accentColor,
            isExpanded = subExpanded["previews_reset"] == true,
            onToggle = { subExpanded["previews_reset"] = !(subExpanded["previews_reset"] ?: false) },
            headerTag = "screensaver_section_previews_reset"
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { previewScreensaverMode = ScreensaverDisplayMode.PORTRAIT },
                    modifier = Modifier.testTag("screensaver_preview_portrait_btn")
                ) {
                    Text("Portrait Preview", fontSize = 11.5.sp)
                }
                OutlinedButton(
                    onClick = { previewScreensaverMode = ScreensaverDisplayMode.LANDSCAPE },
                    modifier = Modifier.testTag("screensaver_preview_landscape_btn")
                ) {
                    Text("Landscape Preview", fontSize = 11.5.sp)
                }
                OutlinedButton(
                    onClick = { previewScreensaverMode = ScreensaverDisplayMode.MIRROR },
                    modifier = Modifier.testTag("screensaver_preview_mirror_btn")
                ) {
                    Text("Mirror HUD Preview", fontSize = 11.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screensaver_reset_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset Screensaver Settings to Defaults",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ============================================================
    // FULL-SCREEN SCREENSAVER LIVE PREVIEW DIALOG
    // ============================================================
    val activePreviewMode = previewScreensaverMode
    if (activePreviewMode != null) {
        val hasRealData = activeRoute != null &&
            locationState.point != null &&
            matchResult.chainage != null

        val previewRoute = activeRoute ?: PREVIEW_SAMPLE_ROUTE
        val previewLocation = locationState.point ?: PREVIEW_SAMPLE_POINT
        val previewDisplayState = remember(
            hasRealData,
            matchResult,
            locationState,
            projectProfile.roadSide,
            currentLocationName,
            activeRoute?.id
        ) {
            if (hasRealData) {
                ScreensaverChainageStateHolder().resolve(
                    matchResult = matchResult,
                    route = activeRoute,
                    hasGpsFix = true,
                    projectRoadSide = projectProfile.roadSide,
                    locationState = locationState,
                    locationName = currentLocationName
                )
            } else {
                ScreensaverDisplayState(
                    chainageText = "204+320",
                    reliableSide = "LHS",
                    directionArrow = "↑",
                    isLiveGpsValid = true,
                    isIncreasingTrend = true,
                    locationName = "NH-58 Sample Alignment",
                    speedText = "64 km/h",
                    speedKmh = 64
                )
            }
        }

        val previewSettings = remember(settings, activePreviewMode) {
            when (activePreviewMode) {
                ScreensaverDisplayMode.PORTRAIT -> settings.selectPortrait()
                ScreensaverDisplayMode.LANDSCAPE -> settings.selectLandscape()
                ScreensaverDisplayMode.MIRROR -> settings.setMirrorEnabled(true)
            }.copy(
                touchToDismiss = true,
                autoOrientationEnabled = false
            )
        }

        Dialog(
            onDismissRequest = { previewScreensaverMode = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            val localView = LocalView.current
            val dialogWindow = (localView.parent as? DialogWindowProvider)?.window
            KeepScreenOn(
                enabled = previewSettings.keepScreenAwake,
                requesterId = "ScreensaverPreview"
            )
            DisposableEffect(dialogWindow, localView, previewSettings.keepScreenAwake) {
                if (dialogWindow != null) {
                    dialogWindow.setDimAmount(0f)
                    dialogWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    if (previewSettings.keepScreenAwake) {
                        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                applyScreensaverImmersiveMode(
                    dialogWindow = dialogWindow,
                    dialogView = localView,
                    hostActivityWindow = null,
                    enable = true
                )
                onDispose {
                    dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    applyScreensaverImmersiveMode(
                        dialogWindow = dialogWindow,
                        dialogView = localView,
                        hostActivityWindow = null,
                        enable = false
                    )
                }
            }

            ChainageScreensaverOverlay(
                displayState = previewDisplayState,
                location = previewLocation,
                bearing = locationState.bearing ?: 42f,
                compassHeading = compassHeading,
                routeData = previewRoute,
                mapThemePreference = mapThemePreference,
                mapTypePreference = mapTypePreference,
                isDarkAppTheme = isDark,
                screensaverSettings = previewSettings,
                isSamplePreview = !hasRealData,
                onToggleMirrorQuickMode = { enableMirror, currentlyLandscape ->
                    onUpdateSettings {
                        it.withMirrorQuickToggle(
                            enableMirror = enableMirror,
                            isCurrentlyLandscape = currentlyLandscape
                        )
                    }
                    previewScreensaverMode = when {
                        enableMirror -> ScreensaverDisplayMode.MIRROR
                        currentlyLandscape -> ScreensaverDisplayMode.LANDSCAPE
                        else -> ScreensaverDisplayMode.PORTRAIT
                    }
                },
                onToggleOrientationLock = { currentlyLandscape ->
                    onUpdateSettings {
                        it.withOrientationLock(!it.orientationLocked, currentlyLandscape)
                    }
                },
                onDismiss = { previewScreensaverMode = null }
            )
        }
    }

    // ============================================================
    // RESET CONFIRMATION DIALOG
    // ============================================================
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            modifier = Modifier.testTag("screensaver_reset_confirm_dialog"),
            title = {
                Text("Reset Screensaver Settings?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This restores General Screensaver, Display Mode, Mirror HUD, Dashboard Elements, and Shared Mini Map preferences to default values.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetDefaults()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("screensaver_reset_confirm_btn")
                ) {
                    Text("Reset Defaults", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("screensaver_reset_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScreensaverSubDropdown(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    headerTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 0.75.dp,
            color = if (isExpanded) accentColor.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(220))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag(headerTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.13f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                    tint = if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun ScreensaverSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    tag: String,
    enabled: Boolean = true
) {
    val isDark = com.example.ui.theme.isAppDarkTheme()
    val thumbColor = if (isDark) Color.Black else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.9f else 0.55f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = thumbColor,
                checkedTrackColor = accentColor,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun ScreensaverSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    accentColor: Color,
    tagPrefix: String
) {
    val isDark = com.example.ui.theme.isAppDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val sanitizedName = labelFor(option)
                    .lowercase()
                    .replace("[^a-z0-9]+".toRegex(), "_")
                    .trim('_')
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            text = labelFor(option),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = if (isDark) Color.Black else Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("${tagPrefix}_$sanitizedName")
                )
            }
        }
    }
}

private val PREVIEW_SAMPLE_POINT = GeoPoint(29.5050, 77.7050)

private val PREVIEW_SAMPLE_ROUTE = RouteData(
    id = "preview_sample_route",
    name = "NH-58 Sample Alignment",
    fileName = "sample_preview.kmz",
    markers = emptyList(),
    alignments = listOf(
        RouteAlignment(
            id = "sample_main",
            name = "Sample Highway Alignment",
            points = listOf(
                GeoPoint(29.4980, 77.7010),
                GeoPoint(29.5050, 77.7050),
                GeoPoint(29.5120, 77.7090)
            ),
            direction = "INCREASING"
        )
    ),
    bounds = GeoBounds(29.495, 29.515, 77.698, 77.712),
    startChainage = Chainage(200_000L),
    endChainage = Chainage(210_000L)
)
