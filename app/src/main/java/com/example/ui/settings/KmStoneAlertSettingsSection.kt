package com.example.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.KmAdvanceWarningOption
import com.example.model.KmAlertSoundOption
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVibrationIntensity
import com.example.model.KmVibrationPattern
import com.example.navigation.KmStoneAlertFeedbackManager
import com.example.navigation.KmStoneMonitoringForegroundService
import com.example.navigation.LockScreenNavigationService
import kotlin.math.roundToInt

/**
 * Settings → Navigation Alerts → KM Stone Monitoring
 * Clean minimalist Flat UI section with expandable subsections:
 * - Monitoring & General (Foreground Service, Background Monitoring, Persistent Notification, Lock-Screen Visibility, Arrival Alerts)
 * - Vibration Customisation
 * - Sound Customisation
 * - Arrival Detection
 * - Smart Advance Warning
 * - Reset to Default
 */
@Composable
fun KmStoneAlertSettingsSectionContent(
    settings: KmStoneAlertSettings,
    accentColor: Color,
    isMonitoringServiceRunning: Boolean = false,
    serviceStatusNote: String? = null,
    hasActiveRoute: Boolean = true,
    onUpdateSettings: ((KmStoneAlertSettings) -> KmStoneAlertSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onFeedbackMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val expandedSubsections = remember {
        mutableStateMapOf(
            "general" to true,
            "vibration" to true,
            "sound" to true,
            "detection" to false,
            "advance" to false,
            "voice" to true
        )
    }

    var customVibrationInput by remember { mutableStateOf(settings.customVibrationPatternCsv) }
    var customAdvanceMetresInput by remember { mutableStateOf(settings.customAdvanceWarningMetres.toString()) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var showVibPatternMenu by remember { mutableStateOf(false) }
    var showSoundMenu by remember { mutableStateOf(false) }
    var showAdvWarningMenu by remember { mutableStateOf(false) }
    var showAdvVibPatternMenu by remember { mutableStateOf(false) }
    var showAdvSoundMenu by remember { mutableStateOf(false) }

    LaunchedEffect(settings.customVibrationPatternCsv) {
        if (customVibrationInput != settings.customVibrationPatternCsv) {
            customVibrationInput = settings.customVibrationPatternCsv
        }
    }
    LaunchedEffect(settings.customAdvanceWarningMetres) {
        val str = settings.customAdvanceWarningMetres.toString()
        if (customAdvanceMetresInput != str) {
            customAdvanceMetresInput = str
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri != null) {
                val title = try {
                    RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context)
                        ?: pickedUri.lastPathSegment
                        ?: "Custom Tone"
                } catch (_: Exception) {
                    "Custom Tone"
                }
                onUpdateSettings {
                    it.copy(
                        soundOption = KmAlertSoundOption.CUSTOM_SOUND,
                        customSoundUri = pickedUri.toString(),
                        customSoundName = title
                    )
                }
                onFeedbackMessage("Selected custom sound: $title")
            }
        }
    }

    var pendingTestNotificationAfterPerm by remember { mutableStateOf(false) }
    var pendingPersistentNotifAfterPerm by remember { mutableStateOf(false) }
    var hasBgLocationPerm by remember {
        mutableStateOf(KmStoneMonitoringForegroundService.hasBackgroundLocationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when {
                pendingTestNotificationAfterPerm -> {
                    pendingTestNotificationAfterPerm = false
                    KmStoneAlertFeedbackManager.testArrivalNotification(context, settings)
                    onFeedbackMessage("Sent test KM Stone arrival notification")
                }
                pendingPersistentNotifAfterPerm -> {
                    pendingPersistentNotifAfterPerm = false
                    onUpdateSettings { it.copy(persistentNotificationEnabled = true) }
                    onFeedbackMessage("Persistent KM Stone monitoring notification enabled")
                }
                else -> {
                    onUpdateSettings { it.copy(lockScreenNotificationsEnabled = true) }
                    onFeedbackMessage("Lock-screen KM Stone notifications enabled")
                }
            }
        } else {
            pendingTestNotificationAfterPerm = false
            pendingPersistentNotifAfterPerm = false
            onFeedbackMessage("Notification permission denied by Android")
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBgLocationPerm = granted || KmStoneMonitoringForegroundService.hasBackgroundLocationPermission(context)
        if (granted) {
            onFeedbackMessage("Background location permission granted for continuous monitoring")
        } else {
            onFeedbackMessage("Background location not granted; monitoring will rely on active foreground session")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("km_stone_alert_settings_root"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ------------------------------------------------------------
        // 1. MONITORING & GENERAL SUBSECTION
        // ------------------------------------------------------------
        val monitoringSubtitle = when {
            !settings.enabled -> "Monitoring disabled"
            isMonitoringServiceRunning -> "Foreground Service active"
            settings.backgroundMonitoringEnabled && settings.persistentNotificationEnabled && !hasActiveRoute ->
                "Standby • Load a route to start service"
            settings.backgroundMonitoringEnabled -> "Background monitoring enabled"
            else -> "In-app monitoring only"
        }
        KmFlatExpandableHeader(
            title = "KM Stone Monitoring & Alerts",
            subtitle = monitoringSubtitle,
            expanded = expandedSubsections["general"] == true,
            accentColor = accentColor,
            tag = "km_alert_sub_general",
            onToggle = { expandedSubsections["general"] = !(expandedSubsections["general"] ?: true) }
        )

        AnimatedVisibility(visible = expandedSubsections["general"] == true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Live Foreground Service Status Strip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMonitoringServiceRunning) {
                        Color(0xFF10B981).copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        0.75.dp,
                        if (isMonitoringServiceRunning) {
                            Color(0xFF10B981).copy(alpha = 0.40f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_alert_service_status_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = when {
                                isMonitoringServiceRunning -> "Dedicated KM Stone Foreground Service: RUNNING"
                                settings.shouldRunBackgroundForegroundService(hasActiveRoute) ->
                                    "Dedicated KM Stone Foreground Service: STARTING"
                                settings.enabled && settings.backgroundMonitoringEnabled && !hasActiveRoute ->
                                    "Foreground Service: STANDBY (load a KMZ/KML route)"
                                else -> "Dedicated KM Stone Foreground Service: STOPPED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonitoringServiceRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                        )
                        if (!serviceStatusNote.isNullOrBlank()) {
                            Text(
                                text = serviceStatusNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                KmFlatSwitchRow(
                    title = "Enable KM Stone Monitoring",
                    subtitle = "Continuously track verified KM Stone progress using CTECS GPS & calibrated chainage",
                    checked = settings.enabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_enabled",
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(enabled = enabled) } }
                )

                KmFlatSwitchRow(
                    title = "Background Monitoring (Foreground Service)",
                    subtitle = "Keep monitoring active in background and while screen is locked (subject to Android permissions)",
                    checked = settings.backgroundMonitoringEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_background_monitoring",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(backgroundMonitoringEnabled = enabled) }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    settings.enabled &&
                    settings.backgroundMonitoringEnabled &&
                    !hasBgLocationPerm
                ) {
                    OutlinedButton(
                        onClick = {
                            if (KmStoneMonitoringForegroundService.hasLocationPermission(context)) {
                                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            } else {
                                onFeedbackMessage("Grant Foreground Location permission first before enabling All-the-Time access")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("km_alert_request_bg_location_btn")
                    ) {
                        Text(
                            text = "Allow Background Location (Optional for Locked Screen)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                KmFlatSwitchRow(
                    title = "Persistent Monitoring Notification",
                    subtitle = "Show live chainage, next KM Stone, distance, GPS & speed while foreground service runs",
                    checked = settings.persistentNotificationEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_persistent_notif",
                    onCheckedChange = { enabled ->
                        if (enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !KmStoneMonitoringForegroundService.hasNotificationPermission(context)
                        ) {
                            pendingTestNotificationAfterPerm = false
                            pendingPersistentNotifAfterPerm = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onUpdateSettings { it.copy(persistentNotificationEnabled = enabled) }
                        }
                    }
                )

                KmFlatSwitchRow(
                    title = "Lock-Screen Notification Visibility",
                    subtitle = "Display chainage & KM Stone details on the lock screen (respects Android lock-screen privacy)",
                    checked = settings.lockScreenNotificationsEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_lock_screen_notif",
                    onCheckedChange = { enabled ->
                        if (enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !LockScreenNavigationService.hasNotificationPermission(context)
                        ) {
                            pendingTestNotificationAfterPerm = false
                            pendingPersistentNotifAfterPerm = false
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onUpdateSettings { it.copy(lockScreenNotificationsEnabled = enabled) }
                        }
                    }
                )

                KmFlatSwitchRow(
                    title = "KM Stone Arrival Alerts",
                    subtitle = "Deliver immediate arrival notification, sound & vibration upon confirmed KM Stone arrival",
                    checked = settings.arrivalAlertsEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_arrival_alerts",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(arrivalAlertsEnabled = enabled) }
                    }
                )

                KmFlatSwitchRow(
                    title = "Enable Haptic Feedback",
                    subtitle = "Crisp tactile confirmation upon KM Stone arrival",
                    checked = settings.hapticFeedbackEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_haptic",
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(hapticFeedbackEnabled = enabled) } }
                )

                KmFlatSwitchRow(
                    title = "Enable Vibration",
                    subtitle = "Patterned motor vibration for eyes-free field awareness",
                    checked = settings.vibrationEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_vibration",
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(vibrationEnabled = enabled) } }
                )

                KmFlatSwitchRow(
                    title = "Enable Sound",
                    subtitle = "Audio alert tone (respects Silent / Do Not Disturb & active recording)",
                    checked = settings.soundEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_sound",
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(soundEnabled = enabled) } }
                )

                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !LockScreenNavigationService.hasNotificationPermission(context)
                        ) {
                            pendingTestNotificationAfterPerm = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val record = KmStoneAlertFeedbackManager.testArrivalNotification(context, settings)
                            if (record.notificationPosted) {
                                onFeedbackMessage("Sent test KM Stone arrival notification")
                            } else {
                                onFeedbackMessage("Grant notification permission to preview arrival banners")
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_alert_test_notification_btn")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Arrival Notification", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // ------------------------------------------------------------
        // 2. VIBRATION CUSTOMISATION SUBSECTION
        // ------------------------------------------------------------
        KmFlatExpandableHeader(
            title = "Vibration Customisation",
            subtitle = "${settings.vibrationPattern.label} • ${settings.vibrationIntensity.label}",
            expanded = expandedSubsections["vibration"] == true,
            accentColor = accentColor,
            tag = "km_alert_sub_vibration",
            onToggle = { expandedSubsections["vibration"] = !(expandedSubsections["vibration"] ?: true) }
        )

        AnimatedVisibility(visible = expandedSubsections["vibration"] == true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    KmFlatDropdownRow(
                        label = "Vibration Pattern",
                        valueText = settings.vibrationPattern.label,
                        accentColor = accentColor,
                        tag = "km_alert_dropdown_vibration_pattern",
                        onClick = { showVibPatternMenu = true }
                    )
                    DropdownMenu(
                        expanded = showVibPatternMenu,
                        onDismissRequest = { showVibPatternMenu = false }
                    ) {
                        KmVibrationPattern.entries.forEach { pattern ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = pattern.label,
                                        fontWeight = if (settings.vibrationPattern == pattern) FontWeight.Bold else FontWeight.Normal,
                                        color = if (settings.vibrationPattern == pattern) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showVibPatternMenu = false
                                    onUpdateSettings { it.copy(vibrationPattern = pattern) }
                                },
                                modifier = Modifier.testTag("km_alert_vib_pattern_${pattern.name.lowercase()}")
                            )
                        }
                    }
                }

                if (settings.vibrationPattern == KmVibrationPattern.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customVibrationInput,
                            onValueChange = { customVibrationInput = it },
                            label = { Text("Custom Pattern (ms, e.g. 0,140,90,200)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("km_alert_custom_vib_input")
                        )
                        Button(
                            onClick = {
                                onUpdateSettings { it.copy(customVibrationPatternCsv = customVibrationInput.trim()) }
                                onFeedbackMessage("Custom vibration pattern saved")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("km_alert_custom_vib_apply_btn")
                        ) {
                            Text("Apply", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Vibration Intensity",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KmVibrationIntensity.entries.forEach { intensity ->
                        val selected = settings.vibrationIntensity == intensity
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateSettings { it.copy(vibrationIntensity = intensity) } },
                            label = {
                                Text(
                                    text = intensity.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                selectedLabelColor = accentColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("km_alert_vib_intensity_${intensity.name.lowercase()}")
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        KmStoneAlertFeedbackManager.testVibration(context, settings)
                        onFeedbackMessage("Testing vibration (${settings.vibrationPattern.label})")
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_alert_test_vibration_btn")
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Vibration", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // ------------------------------------------------------------
        // 3. SOUND CUSTOMISATION SUBSECTION
        // ------------------------------------------------------------
        KmFlatExpandableHeader(
            title = "Sound Customisation",
            subtitle = "${settings.soundOption.label} • ${settings.alertVolumePercent}%",
            expanded = expandedSubsections["sound"] == true,
            accentColor = accentColor,
            tag = "km_alert_sub_sound",
            onToggle = { expandedSubsections["sound"] = !(expandedSubsections["sound"] ?: true) }
        )

        AnimatedVisibility(visible = expandedSubsections["sound"] == true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val soundDisplay = if (settings.soundOption == KmAlertSoundOption.CUSTOM_SOUND &&
                        settings.customSoundName.isNotBlank()
                    ) {
                        "Custom: ${settings.customSoundName}"
                    } else {
                        settings.soundOption.label
                    }
                    KmFlatDropdownRow(
                        label = "Arrival Alert Sound",
                        valueText = soundDisplay,
                        accentColor = accentColor,
                        tag = "km_alert_dropdown_sound",
                        onClick = { showSoundMenu = true }
                    )
                    DropdownMenu(
                        expanded = showSoundMenu,
                        onDismissRequest = { showSoundMenu = false }
                    ) {
                        KmAlertSoundOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt.label,
                                        fontWeight = if (settings.soundOption == opt) FontWeight.Bold else FontWeight.Normal,
                                        color = if (settings.soundOption == opt) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showSoundMenu = false
                                    onUpdateSettings { it.copy(soundOption = opt) }
                                },
                                modifier = Modifier.testTag("km_alert_sound_opt_${opt.name.lowercase()}")
                            )
                        }
                    }
                }

                if (settings.soundOption == KmAlertSoundOption.CUSTOM_SOUND) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.customSoundName.isNotBlank()) {
                                "Selected: ${settings.customSoundName}"
                            } else {
                                "No custom device tone selected (using Digital Chime fallback)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                        if (settings.customSoundUri.isNotBlank()) {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(settings.customSoundUri))
                                        }
                                    }
                                    ringtonePickerLauncher.launch(intent)
                                } catch (_: Exception) {
                                    onFeedbackMessage("Device ringtone picker unavailable")
                                }
                            },
                            modifier = Modifier.testTag("km_alert_pick_custom_sound_btn")
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Choose Tone", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Alert Volume",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "${settings.alertVolumePercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.alertVolumePercent.toFloat(),
                    onValueChange = { vol ->
                        onUpdateSettings { it.copy(alertVolumePercent = vol.roundToInt().coerceIn(0, 100)) }
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_alert_volume_slider")
                )

                OutlinedButton(
                    onClick = {
                        KmStoneAlertFeedbackManager.testSound(context, settings)
                        onFeedbackMessage("Playing sound preview (${settings.soundOption.label})")
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_alert_test_sound_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Sound", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // ------------------------------------------------------------
        // 4. ARRIVAL DETECTION SUBSECTION
        // ------------------------------------------------------------
        KmFlatExpandableHeader(
            title = "Arrival Detection",
            subtitle = "Threshold ±${settings.proximityThresholdMetres}m • GPS ≤${settings.maxGpsAccuracyMetres}m",
            expanded = expandedSubsections["detection"] == true,
            accentColor = accentColor,
            tag = "km_alert_sub_detection",
            onToggle = { expandedSubsections["detection"] = !(expandedSubsections["detection"] ?: false) }
        )

        AnimatedVisibility(visible = expandedSubsections["detection"] == true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Proximity Threshold (±${settings.proximityThresholdMetres} m)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KmStoneAlertSettings.PROXIMITY_THRESHOLD_OPTIONS.forEach { threshold ->
                        val selected = settings.proximityThresholdMetres == threshold
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateSettings { it.copy(proximityThresholdMetres = threshold) } },
                            label = {
                                Text(
                                    text = "${threshold}m",
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                selectedLabelColor = accentColor
                            ),
                            modifier = Modifier.testTag("km_alert_proximity_${threshold}m")
                        )
                    }
                }

                Text(
                    text = "Maximum GPS Accuracy Requirement (≤ ${settings.maxGpsAccuracyMetres} m)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.maxGpsAccuracyMetres.toFloat(),
                    onValueChange = { acc ->
                        onUpdateSettings { it.copy(maxGpsAccuracyMetres = acc.roundToInt().coerceIn(10, 50)) }
                    },
                    valueRange = 10f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_alert_gps_accuracy_slider")
                )

                KmFlatSwitchRow(
                    title = "Repeat Alert Prevention",
                    subtitle = "Trigger only once per KM Stone until moving ≥${settings.repeatAlertDistanceMetres}m away",
                    checked = settings.repeatAlertPreventionEnabled,
                    accentColor = accentColor,
                    tag = "km_alert_switch_repeat_prevention",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(repeatAlertPreventionEnabled = enabled) }
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // ------------------------------------------------------------
        // 5. SMART ADVANCE WARNING SUBSECTION
        // ------------------------------------------------------------
        val advanceSummary = if (settings.advanceWarningOption == KmAdvanceWarningOption.DISABLED) {
            "Disabled"
        } else {
            "${settings.resolveAdvanceWarningDistanceMetres()}m before KM Stone"
        }
        KmFlatExpandableHeader(
            title = "Smart Advance Warning",
            subtitle = advanceSummary,
            expanded = expandedSubsections["advance"] == true,
            accentColor = accentColor,
            tag = "km_alert_sub_advance",
            onToggle = { expandedSubsections["advance"] = !(expandedSubsections["advance"] ?: false) }
        )

        AnimatedVisibility(visible = expandedSubsections["advance"] == true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    KmFlatDropdownRow(
                        label = "Advance Warning Distance",
                        valueText = settings.advanceWarningOption.label,
                        accentColor = accentColor,
                        tag = "km_alert_dropdown_advance_warning",
                        onClick = { showAdvWarningMenu = true }
                    )
                    DropdownMenu(
                        expanded = showAdvWarningMenu,
                        onDismissRequest = { showAdvWarningMenu = false }
                    ) {
                        KmAdvanceWarningOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt.label,
                                        fontWeight = if (settings.advanceWarningOption == opt) FontWeight.Bold else FontWeight.Normal,
                                        color = if (settings.advanceWarningOption == opt) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showAdvWarningMenu = false
                                    onUpdateSettings { it.copy(advanceWarningOption = opt) }
                                },
                                modifier = Modifier.testTag("km_alert_adv_opt_${opt.name.lowercase()}")
                            )
                        }
                    }
                }

                if (settings.advanceWarningOption == KmAdvanceWarningOption.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customAdvanceMetresInput,
                            onValueChange = { customAdvanceMetresInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Custom Advance Distance (25 - 2000 m)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("km_alert_custom_advance_input")
                        )
                        Button(
                            onClick = {
                                val parsed = (customAdvanceMetresInput.toIntOrNull() ?: 150).coerceIn(25, 2000)
                                customAdvanceMetresInput = parsed.toString()
                                onUpdateSettings { it.copy(customAdvanceWarningMetres = parsed) }
                                onFeedbackMessage("Advance warning set to ${parsed}m")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("km_alert_custom_advance_apply_btn")
                        ) {
                            Text("Apply", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (settings.advanceWarningOption != KmAdvanceWarningOption.DISABLED) {
                    KmFlatSwitchRow(
                        title = "Advance Warning Vibration",
                        checked = settings.advanceWarningVibrationEnabled,
                        accentColor = accentColor,
                        tag = "km_alert_switch_advance_vib",
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(advanceWarningVibrationEnabled = enabled) }
                        }
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        KmFlatDropdownRow(
                            label = "Advance Vibration Pattern",
                            valueText = settings.advanceWarningVibrationPattern.label,
                            accentColor = accentColor,
                            tag = "km_alert_dropdown_advance_vib_pattern",
                            onClick = { showAdvVibPatternMenu = true }
                        )
                        DropdownMenu(
                            expanded = showAdvVibPatternMenu,
                            onDismissRequest = { showAdvVibPatternMenu = false }
                        ) {
                            KmVibrationPattern.entries.forEach { pat ->
                                DropdownMenuItem(
                                    text = { Text(pat.label) },
                                    onClick = {
                                        showAdvVibPatternMenu = false
                                        onUpdateSettings { it.copy(advanceWarningVibrationPattern = pat) }
                                    }
                                )
                            }
                        }
                    }

                    KmFlatSwitchRow(
                        title = "Advance Warning Sound",
                        checked = settings.advanceWarningSoundEnabled,
                        accentColor = accentColor,
                        tag = "km_alert_switch_advance_sound",
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(advanceWarningSoundEnabled = enabled) }
                        }
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        KmFlatDropdownRow(
                            label = "Advance Sound Tone",
                            valueText = settings.advanceWarningSoundOption.label,
                            accentColor = accentColor,
                            tag = "km_alert_dropdown_advance_sound",
                            onClick = { showAdvSoundMenu = true }
                        )
                        DropdownMenu(
                            expanded = showAdvSoundMenu,
                            onDismissRequest = { showAdvSoundMenu = false }
                        ) {
                            KmAlertSoundOption.entries.forEach { snd ->
                                DropdownMenuItem(
                                    text = { Text(snd.label) },
                                    onClick = {
                                        showAdvSoundMenu = false
                                        onUpdateSettings { it.copy(advanceWarningSoundOption = snd) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // ------------------------------------------------------------
        // 6. SMART NEXT KM STONE VOICE ANNOUNCEMENT SUBSECTION
        // ------------------------------------------------------------
        KmStoneVoiceAnnouncementSettingsSubsection(
            settings = settings,
            expanded = expandedSubsections["voice"] == true,
            accentColor = accentColor,
            onToggleExpanded = {
                expandedSubsections["voice"] = !(expandedSubsections["voice"] ?: false)
            },
            onUpdateSettings = onUpdateSettings,
            onFeedbackMessage = onFeedbackMessage
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // ------------------------------------------------------------
        // 7. RESET TO DEFAULT
        // ------------------------------------------------------------
        OutlinedButton(
            onClick = { showResetConfirmDialog = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("km_alert_reset_defaults_btn")
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reset KM Stone Alert Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset KM Stone Arrival Settings?") },
            text = {
                Text("This will restore default KM Stone arrival detection thresholds, vibration pattern, sound tone, voice announcements, and advance warning settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetDefaults()
                    },
                    modifier = Modifier.testTag("km_alert_confirm_reset_btn")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun KmFlatExpandableHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    accentColor: Color,
    tag: String,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = accentColor
            )
        }
    }
}

@Composable
internal fun KmFlatDropdownRow(
    label: String,
    valueText: String,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select $label",
                tint = accentColor
            )
        }
    }
}

@Composable
internal fun KmFlatSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    accentColor: Color,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
