package com.example.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.location.LocationState
import com.example.model.ChainageMatchResult
import com.example.model.NavigationNotificationSettings
import com.example.model.ProjectProfile
import com.example.model.RouteData
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.LockScreenNavUiState
import com.example.navigation.LockScreenNavigationService
import com.example.navigation.LockScreenNotificationFormatter

/**
 * Settings -> Navigation Notifications
 * Dedicated controls for lock-screen navigation notifications, status bar Live Updates,
 * and Samsung Now Bar compatibility.
 */
@Composable
fun NavigationNotificationSettingsSectionContent(
    notifSettings: NavigationNotificationSettings,
    isLockScreenNavActive: Boolean,
    matchResult: ChainageMatchResult? = null,
    locationState: LocationState? = null,
    activeRoute: RouteData? = null,
    projectProfile: ProjectProfile? = null,
    currentLocationName: String? = null,
    accentColor: Color,
    onToggleNotificationEnabled: (Boolean) -> Unit,
    onUpdateNotifSettings: ((NavigationNotificationSettings) -> NavigationNotificationSettings) -> Unit,
    onResetDefaults: () -> Unit
) {
    val context = LocalContext.current
    var hasPostNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.POST_NOTIFICATIONS] ?: hasPostNotifPermission
        } else {
            true
        }
        hasPostNotifPermission = notifOk
        val locOk = LockScreenNavigationService.hasLocationPermission(context)
        if (notifOk && locOk) {
            onToggleNotificationEnabled(true)
        }
    }

    val effectiveEnabled = notifSettings.enabled && isLockScreenNavActive

    val resolver = remember { LockScreenNavStateResolver() }
    val resolvedNavState = remember(
        matchResult,
        locationState,
        activeRoute?.id,
        projectProfile,
        currentLocationName
    ) {
        val candidate = if (activeRoute != null && locationState != null && matchResult != null && projectProfile != null) {
            resolver.resolve(
                locationState = locationState,
                matchResult = matchResult,
                routeData = activeRoute,
                projectProfile = projectProfile,
                locationName = currentLocationName
            )
        } else {
            LockScreenNavUiState(
                chainageText = "196+494",
                reliableSide = "RHS",
                directionArrow = "↓",
                isIncreasingTrend = false,
                directionInstruction = "Decreasing Chainage (RHS)",
                roadOrStretchName = projectProfile?.highwayId?.takeIf { it.isNotBlank() } ?: "NH Corridor",
                nextMarkerLabel = "Next KM 196+000",
                distanceToNextText = "494 m",
                distanceToNextMetres = 494L,
                speedKmh = 0,
                speedText = "0 km/h",
                locationName = currentLocationName?.takeIf { it.isNotBlank() } ?: "Sample Corridor",
                isLiveGpsValid = true,
                hasRouteData = true,
                isSamplePreview = true
            )
        }
        candidate
    }

    val previewSummary = remember(resolvedNavState, notifSettings) {
        LockScreenNotificationFormatter.format(resolvedNavState, notifSettings)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nav_notif_settings_root"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Master Toggle
        NavNotifSwitchRow(
            title = "Enable Navigation Notification",
            subtitle = if (effectiveEnabled) {
                "Active on notification shade & status bar"
            } else {
                "Off"
            },
            checked = effectiveEnabled,
            accentColor = accentColor,
            tag = "nav_notif_switch_enabled",
            onCheckedChange = { enable ->
                if (!enable) {
                    onToggleNotificationEnabled(false)
                } else {
                    val missing = buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (!LockScreenNavigationService.hasLocationPermission(context)) {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                    if (missing.isEmpty()) {
                        onToggleNotificationEnabled(true)
                    } else {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }
            }
        )

        if (!hasPostNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notification permission required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Real-time Notification Preview matching exact Android Status / Shade card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B111E),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nav_notif_live_preview_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFF0EA5E9).copy(alpha = 0.18f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "CTECS • Navigation",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (notifSettings.liveNotificationEnabled && !previewSummary.shortCriticalText.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0EA5E9).copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = previewSummary.shortCriticalText,
                                color = Color(0xFF7DD3FC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("nav_notif_preview_chip")
                            )
                        }
                    }
                }

                Text(
                    text = previewSummary.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("nav_notif_preview_title")
                )

                Text(
                    text = previewSummary.expandedText ?: previewSummary.contentText,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.testTag("nav_notif_preview_body")
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(vertical = 2.dp)
        )

        NavNotifSwitchRow(
            title = "Show Chainage",
            subtitle = null,
            checked = notifSettings.showChainage,
            accentColor = accentColor,
            tag = "nav_notif_switch_chainage",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showChainage = v) } }
        )

        NavNotifSwitchRow(
            title = "Show LHS / RHS",
            subtitle = null,
            checked = notifSettings.showRoadSide,
            accentColor = accentColor,
            tag = "nav_notif_switch_road_side",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showRoadSide = v) } }
        )

        NavNotifSwitchRow(
            title = "Show Next KM Marker",
            subtitle = null,
            checked = notifSettings.showNextKmMarker,
            accentColor = accentColor,
            tag = "nav_notif_switch_next_km",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showNextKmMarker = v) } }
        )

        NavNotifSwitchRow(
            title = "Show Distance Remaining",
            subtitle = null,
            checked = notifSettings.showDistanceRemaining,
            accentColor = accentColor,
            tag = "nav_notif_switch_distance",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showDistanceRemaining = v) } }
        )

        NavNotifSwitchRow(
            title = "Show Speed",
            subtitle = null,
            checked = notifSettings.showSpeed,
            accentColor = accentColor,
            tag = "nav_notif_switch_speed",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showSpeed = v) } }
        )

        NavNotifSwitchRow(
            title = "Show Location",
            subtitle = null,
            checked = notifSettings.showLocation,
            accentColor = accentColor,
            tag = "nav_notif_switch_location",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showLocation = v) } }
        )

        NavNotifSwitchRow(
            title = "Show on Lock Screen",
            subtitle = "Public visibility when device is locked",
            checked = notifSettings.showOnLockScreen,
            accentColor = accentColor,
            tag = "nav_notif_switch_lock_screen",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(showOnLockScreen = v) } }
        )

        NavNotifSwitchRow(
            title = "Enable Expanded Navigation Information",
            subtitle = "Includes location & GPS status on expand",
            checked = notifSettings.expandedInfoEnabled,
            accentColor = accentColor,
            tag = "nav_notif_switch_expanded",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(expandedInfoEnabled = v) } }
        )

        NavNotifSwitchRow(
            title = "Enable Live Notification / Now Bar",
            subtitle = "Status bar chip & Samsung Now Bar promotion",
            checked = notifSettings.liveNotificationEnabled,
            accentColor = accentColor,
            tag = "nav_notif_switch_live",
            onCheckedChange = { v -> onUpdateNotifSettings { it.copy(liveNotificationEnabled = v) } }
        )

        if (Build.VERSION.SDK_INT >= 36) {
            TextButton(
                onClick = {
                    try {
                        val intent = Intent("android.settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallback)
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.testTag("nav_notif_system_live_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("System Live Update Permission", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedButton(
            onClick = onResetDefaults,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nav_notif_reset_btn")
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("Reset Navigation Notification Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NavNotifSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    tag: String
) {
    val isDark = com.example.ui.theme.isAppDarkTheme()
    val thumbColor = if (isDark) Color.Black else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
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
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = thumbColor,
                checkedTrackColor = accentColor,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
