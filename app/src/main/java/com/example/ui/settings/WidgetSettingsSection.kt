package com.example.ui.settings

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.LocationState
import com.example.model.ChainageMatchResult
import com.example.model.CtecsWidgetInstanceConfig
import com.example.model.CtecsWidgetSettings
import com.example.model.CtecsWidgetType
import com.example.model.MiniMapOrientationMode
import com.example.model.ProjectProfile
import com.example.model.RouteData
import com.example.model.WidgetAccentColorPreset
import com.example.model.WidgetBackgroundStyle
import com.example.model.WidgetSizeTier
import com.example.model.WidgetThemeMode
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.LockScreenNavUiState
import com.example.widget.CtecsWidgetRenderer
import com.example.widget.CtecsWidgetUpdater

/**
 * Settings → Widgets
 * Minimalist Flat UI configuration for CTECS Home-Screen Widgets:
 * - Supports Default Widget Settings and per-widget instance (`appWidgetId`) overrides
 * - Live flat bitmap preview across Compact, Navigation, Mini Map, and Dashboard widgets
 * - Responsive size tier preview (Compact / Medium / Large)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetSettingsSectionContent(
    widgetSettings: CtecsWidgetSettings,
    matchResult: ChainageMatchResult? = null,
    locationState: LocationState? = null,
    activeRoute: RouteData? = null,
    projectProfile: ProjectProfile? = null,
    currentLocationName: String? = null,
    compassHeading: Float = 0f,
    isTrackingActive: Boolean = false,
    accentColor: Color,
    onUpdateSettings: ((CtecsWidgetSettings) -> CtecsWidgetSettings) -> Unit,
    onUpdateInstanceConfig: (CtecsWidgetInstanceConfig) -> Unit,
    onRemoveInstanceOverride: (Int) -> Unit,
    onRefreshWidgetsNow: () -> Unit,
    onResetDefaults: () -> Unit
) {
    val context = LocalContext.current

    var previewType by remember { mutableStateOf(CtecsWidgetType.NAVIGATION) }
    var previewSizeTier by remember { mutableStateOf(WidgetSizeTier.MEDIUM) }
    var selectedTargetWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val activePlacedWidgets = remember(widgetSettings) {
        val fromManager = CtecsWidgetUpdater.getActiveWidgetInstances(context)
        val combined = linkedMapOf<Int, CtecsWidgetType>()
        fromManager.forEach { (id, type) -> combined[id] = type }
        widgetSettings.instanceOverrides.forEach { (id, cfg) ->
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                combined.putIfAbsent(id, cfg.widgetType)
            }
        }
        combined.entries.toList()
    }

    val activeConfig: CtecsWidgetInstanceConfig = remember(
        widgetSettings,
        selectedTargetWidgetId,
        previewType
    ) {
        if (selectedTargetWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetSettings.resolveForInstance(AppWidgetManager.INVALID_APPWIDGET_ID, previewType)
        } else {
            val targetType = activePlacedWidgets.firstOrNull { it.key == selectedTargetWidgetId }?.value ?: previewType
            widgetSettings.resolveForInstance(selectedTargetWidgetId, targetType)
        }
    }

    fun applyConfigTransform(
        defaultTransform: (CtecsWidgetSettings) -> CtecsWidgetSettings,
        instanceTransform: (CtecsWidgetInstanceConfig) -> CtecsWidgetInstanceConfig
    ) {
        if (selectedTargetWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            onUpdateSettings(defaultTransform)
        } else {
            onUpdateInstanceConfig(instanceTransform(activeConfig.copy(useCustomOverride = true)))
        }
    }

    val resolver = remember { LockScreenNavStateResolver() }
    val resolvedNavState = remember(
        matchResult,
        locationState,
        activeRoute?.id,
        projectProfile,
        currentLocationName
    ) {
        if (activeRoute != null && locationState != null && matchResult != null && projectProfile != null) {
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
                locationName = currentLocationName?.takeIf { it.isNotBlank() } ?: "Highway Stretch",
                isLiveGpsValid = true,
                hasRouteData = true,
                isSamplePreview = true
            )
        }
    }

    val previewDimensionsDp = remember(previewType, previewSizeTier) {
        when (previewSizeTier) {
            WidgetSizeTier.COMPACT -> 185 to 78
            WidgetSizeTier.MEDIUM -> 265 to 125
            WidgetSizeTier.LARGE -> 340 to 190
        }
    }

    val previewRenderResult = remember(
        previewType,
        previewSizeTier,
        activeConfig,
        resolvedNavState,
        activeRoute?.id,
        locationState?.point,
        compassHeading,
        isTrackingActive
    ) {
        CtecsWidgetRenderer.renderWidgetBitmap(
            context = context,
            navState = resolvedNavState,
            instanceConfig = activeConfig.copy(widgetType = previewType),
            widthDp = previewDimensionsDp.first,
            heightDp = previewDimensionsDp.second,
            location = locationState?.point,
            bearing = locationState?.bearing ?: compassHeading,
            routeData = activeRoute,
            isTrackingActive = isTrackingActive
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("widget_settings_root"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Live Flat Widget Preview Header & Type Selector
        Text(
            text = "WIDGET TYPE PREVIEW",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CtecsWidgetType.entries.forEach { type ->
                val selected = previewType == type
                FilterChip(
                    selected = selected,
                    onClick = { previewType = type },
                    label = {
                        Text(
                            text = type.label.removeSuffix(" Widget"),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    ),
                    modifier = Modifier.testTag("widget_preview_type_${type.name.lowercase()}")
                )
            }
        }

        // Size tier selector (Compact / Medium / Large)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preview Size",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WidgetSizeTier.entries.forEach { tier ->
                    val isSel = previewSizeTier == tier
                    FilterChip(
                        selected = isSel,
                        onClick = { previewSizeTier = tier },
                        label = {
                            Text(
                                text = tier.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.16f),
                            selectedLabelColor = accentColor
                        ),
                        modifier = Modifier.testTag("widget_preview_size_${tier.name.lowercase()}")
                    )
                }
            }
        }

        // Rendered Native Flat UI Preview Box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("widget_live_preview_surface")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    bitmap = previewRenderResult.bitmap.asImageBitmap(),
                    contentDescription = "${previewType.label} Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            when (previewSizeTier) {
                                WidgetSizeTier.COMPACT -> 84.dp
                                WidgetSizeTier.MEDIUM -> 124.dp
                                WidgetSizeTier.LARGE -> 178.dp
                            }
                        )
                        .clip(RoundedCornerShape(12.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${previewType.label} • ${activeConfig.themeMode.label} • ${activeConfig.backgroundStyle.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRefreshWidgetsNow,
                        modifier = Modifier.testTag("widget_manual_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh widgets",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh Now", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Per-Widget Instance Target Selector (Default vs Individual Placed Widgets)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Text(
            text = "TARGET WIDGET CONFIGURATION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isDefaultSelected = selectedTargetWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID
            FilterChip(
                selected = isDefaultSelected,
                onClick = { selectedTargetWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID },
                label = {
                    Text(
                        text = "Default (All Widgets)",
                        fontSize = 11.sp,
                        fontWeight = if (isDefaultSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.18f),
                    selectedLabelColor = accentColor
                ),
                modifier = Modifier.testTag("widget_target_default")
            )
            activePlacedWidgets.forEach { (id, type) ->
                val isSelected = selectedTargetWidgetId == id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedTargetWidgetId = id
                        previewType = type
                    },
                    label = {
                        Text(
                            text = "${type.label.removeSuffix(" Widget")} #$id",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    ),
                    modifier = Modifier.testTag("widget_target_instance_$id")
                )
            }
        }

        if (selectedTargetWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            widgetSettings.instanceOverrides.containsKey(selectedTargetWidgetId)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom override active for Widget #$selectedTargetWidgetId",
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor
                )
                TextButton(
                    onClick = { onRemoveInstanceOverride(selectedTargetWidgetId) },
                    modifier = Modifier.testTag("widget_clear_instance_override_btn")
                ) {
                    Text("Use Default", fontSize = 11.sp)
                }
            }
        }

        // 3. Appearance: Theme, Flat Background Style & Restrained Accent Colour
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Text(
            text = "THEME, BACKGROUND & ACCENT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )

        Text(
            text = "Widget Theme",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WidgetThemeMode.entries.forEach { mode ->
                val selected = activeConfig.themeMode == mode
                FilterChip(
                    selected = selected,
                    onClick = {
                        applyConfigTransform(
                            defaultTransform = { it.copy(themeMode = mode) },
                            instanceTransform = { it.copy(themeMode = mode) }
                        )
                    },
                    label = {
                        Text(
                            text = mode.label,
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
                        .testTag("widget_theme_${mode.name.lowercase()}")
                )
            }
        }

        Text(
            text = "Background Style",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WidgetBackgroundStyle.entries.forEach { bg ->
                val selected = activeConfig.backgroundStyle == bg
                FilterChip(
                    selected = selected,
                    onClick = {
                        applyConfigTransform(
                            defaultTransform = { it.copy(backgroundStyle = bg) },
                            instanceTransform = { it.copy(backgroundStyle = bg) }
                        )
                    },
                    label = {
                        Text(
                            text = bg.label,
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
                        .testTag("widget_bg_${bg.name.lowercase()}")
                )
            }
        }

        Text(
            text = "Accent Colour",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetAccentColorPreset.entries.forEach { preset ->
                val selected = activeConfig.accentColor == preset
                val swatchColor = Color(preset.darkColorInt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) swatchColor.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .border(
                            width = if (selected) 1.5.dp else 0.75.dp,
                            color = if (selected) swatchColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            applyConfigTransform(
                                defaultTransform = { it.copy(accentColor = preset) },
                                instanceTransform = { it.copy(accentColor = preset) }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("widget_accent_${preset.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(swatchColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = preset.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 4. Data Elements Visibility Toggles
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Text(
            text = "VISIBLE WIDGET ELEMENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )

        WidgetFlatSwitchRow(
            title = "Show Chainage",
            checked = activeConfig.showChainage,
            accentColor = accentColor,
            tag = "widget_switch_show_chainage",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showChainage = enabled) },
                    instanceTransform = { it.copy(showChainage = enabled) }
                )
            }
        )

        WidgetFlatSwitchRow(
            title = "Show LHS / RHS Direction",
            checked = activeConfig.showRoadSide,
            accentColor = accentColor,
            tag = "widget_switch_show_direction",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showRoadSide = enabled) },
                    instanceTransform = { it.copy(showRoadSide = enabled) }
                )
            }
        )

        WidgetFlatSwitchRow(
            title = "Show GPS Speed",
            checked = activeConfig.showSpeed,
            accentColor = accentColor,
            tag = "widget_switch_show_speed",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showSpeed = enabled) },
                    instanceTransform = { it.copy(showSpeed = enabled) }
                )
            }
        )

        WidgetFlatSwitchRow(
            title = "Show Next KM Marker",
            checked = activeConfig.showNextKmMarker,
            accentColor = accentColor,
            tag = "widget_switch_show_next_km",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showNextKmMarker = enabled) },
                    instanceTransform = { it.copy(showNextKmMarker = enabled) }
                )
            }
        )

        WidgetFlatSwitchRow(
            title = "Show Remaining Distance",
            checked = activeConfig.showDistanceRemaining,
            accentColor = accentColor,
            tag = "widget_switch_show_remaining_distance",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showDistanceRemaining = enabled) },
                    instanceTransform = { it.copy(showDistanceRemaining = enabled) }
                )
            }
        )

        WidgetFlatSwitchRow(
            title = "Show Location Name",
            checked = activeConfig.showLocation,
            accentColor = accentColor,
            tag = "widget_switch_show_location",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showLocation = enabled) },
                    instanceTransform = { it.copy(showLocation = enabled) }
                )
            }
        )

        WidgetFlatSwitchRow(
            title = "Show Mini Map",
            checked = activeConfig.showMiniMap,
            accentColor = accentColor,
            tag = "widget_switch_show_minimap",
            onCheckedChange = { enabled ->
                applyConfigTransform(
                    defaultTransform = { it.copy(showMiniMap = enabled) },
                    instanceTransform = { it.copy(showMiniMap = enabled) }
                )
            }
        )

        // 5. Mini Map Orientation
        Text(
            text = "Mini Map Orientation",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMapOrientationMode.entries.forEach { mode ->
                val selected = activeConfig.mapOrientation == mode
                FilterChip(
                    selected = selected,
                    onClick = {
                        applyConfigTransform(
                            defaultTransform = { it.copy(mapOrientation = mode) },
                            instanceTransform = { it.copy(mapOrientation = mode) }
                        )
                    },
                    label = {
                        Text(
                            text = mode.label,
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
                        .testTag("widget_map_orientation_${mode.name.lowercase()}")
                )
            }
        }

        // 6. Reset Widget Settings
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        OutlinedButton(
            onClick = { showResetConfirmDialog = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("widget_reset_defaults_btn")
        ) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reset Widget Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset Widget Settings?") },
            text = {
                Text("This will restore default flat theme, colours, visibility switches, and clear per-widget overrides.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        selectedTargetWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                        onResetDefaults()
                    },
                    modifier = Modifier.testTag("widget_confirm_reset_btn")
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
private fun WidgetFlatSwitchRow(
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
