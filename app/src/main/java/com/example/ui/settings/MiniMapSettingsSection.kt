package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoPoint
import com.example.model.MiniMapCornerShape
import com.example.model.MiniMapDirectionStyle
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapPosition
import com.example.model.MiniMapSizePreset
import com.example.model.MiniMapStyle
import com.example.model.MiniMapStyleConfig
import com.example.model.OverlayConfig
import com.example.model.RouteData
import com.example.ui.camera.MiniMapPreview
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniMapSettingsSection(
    savedOverlay: OverlayConfig,
    liveLocation: GeoPoint?,
    liveBearing: Float?,
    activeRoute: RouteData?,
    currentChainage: String,
    currentSide: String,
    accentColor: Color,
    accentContentColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSaveConfig: (OverlayConfig) -> Unit,
    onResetMiniMapDefaults: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var draftOverlay by remember { mutableStateOf(savedOverlay) }
    var editingVideoMode by remember { mutableStateOf(false) }
    var previewLandscape by remember { mutableStateOf(false) }

    val hasUnsavedChanges = draftOverlay.showMapInset != savedOverlay.showMapInset ||
        draftOverlay.photoMiniMap != savedOverlay.photoMiniMap ||
        draftOverlay.videoMiniMap != savedOverlay.videoMiniMap

    // Keep draft synchronized with external saves when no local unsaved edits exist
    LaunchedEffect(savedOverlay) {
        if (!hasUnsavedChanges) {
            draftOverlay = savedOverlay
        }
    }

    val activeStyleConfig = if (editingVideoMode) draftOverlay.videoMiniMap else draftOverlay.photoMiniMap

    fun updateActiveStyle(transform: (MiniMapStyleConfig) -> MiniMapStyleConfig) {
        draftOverlay = if (editingVideoMode) {
            draftOverlay.copy(videoMiniMap = transform(draftOverlay.videoMiniMap))
        } else {
            draftOverlay.copy(photoMiniMap = transform(draftOverlay.photoMiniMap))
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (isExpanded) 0.7f else 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mini_map_settings_section")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Expandable Header Row: Photo & Video Stamp -> Mini Map Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("mini_map_settings_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Mini Map Settings",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Mini Map Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val modeLabel = if (draftOverlay.showMapInset) {
                            "Photo: ${if (draftOverlay.photoMiniMap.enabled) draftOverlay.photoMiniMap.sizePreset.label else "OFF"} • Video: ${if (draftOverlay.videoMiniMap.enabled) draftOverlay.videoMiniMap.sizePreset.label else "OFF"}"
                        } else {
                            "Master Disabled"
                        }
                        Text(
                            text = if (hasUnsavedChanges) "$modeLabel • Unsaved changes" else modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnsavedChanges) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = accentColor
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // A. ENABLE / DISABLE
                    MiniMapGroupTitle("A. ENABLE / DISABLE", accentColor)
                    MiniMapSwitchRow(
                        title = "Master Mini Map ON/OFF",
                        subtitle = "Global toggle for Mini Map on camera preview, photos, and videos",
                        checked = draftOverlay.showMapInset,
                        onCheckedChange = { draftOverlay = draftOverlay.copy(showMapInset = it) },
                        accentColor = accentColor,
                        tag = "switch_master_minimap"
                    )

                    // Independent Photo vs Video Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !editingVideoMode,
                            onClick = { editingVideoMode = false },
                            label = { Text("Photo Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = accentContentColor
                            ),
                            modifier = Modifier.weight(1f).testTag("minimap_mode_photo")
                        )
                        FilterChip(
                            selected = editingVideoMode,
                            onClick = { editingVideoMode = true },
                            label = { Text("Video Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = accentContentColor
                            ),
                            modifier = Modifier.weight(1f).testTag("minimap_mode_video")
                        )
                    }

                    MiniMapSwitchRow(
                        title = if (editingVideoMode) "Video Mini Map ON/OFF" else "Photo Mini Map ON/OFF",
                        subtitle = if (editingVideoMode) {
                            "Independent enable/disable for recorded videos"
                        } else {
                            "Independent enable/disable for captured photos"
                        },
                        checked = activeStyleConfig.enabled,
                        onCheckedChange = { enabled -> updateActiveStyle { it.copy(enabled = enabled) } },
                        accentColor = accentColor,
                        tag = "switch_mode_minimap"
                    )

                    // Copy Photo -> Video and Copy Video -> Photo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                draftOverlay = draftOverlay.copy(videoMiniMap = draftOverlay.photoMiniMap)
                                onShowMessage("Copied Photo Mini Map settings to Video")
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("minimap_copy_photo_to_video")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Photo → Video", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                draftOverlay = draftOverlay.copy(photoMiniMap = draftOverlay.videoMiniMap)
                                onShowMessage("Copied Video Mini Map settings to Photo")
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("minimap_copy_video_to_photo")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Video → Photo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // LIVE INTERACTIVE PREVIEW CARD
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF090F1C)),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("minimap_settings_live_preview")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE ${if (editingVideoMode) "VIDEO (16:9)" else "PHOTO (4:3)"} PREVIEW",
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { previewLandscape = !previewLandscape },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = "Toggle Preview Orientation",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            val previewRatio = if (editingVideoMode) {
                                if (previewLandscape) 16f / 9f else 9f / 16f
                            } else {
                                if (previewLandscape) 4f / 3f else 3f / 4f
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (previewLandscape) 150.dp else 195.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF131C2E))
                                    .padding(10.dp)
                            ) {
                                // Simulated frame box proportional to chosen aspect
                                val frameModifier = Modifier
                                    .align(Alignment.Center)
                                    .aspectRatio(previewRatio, matchHeightConstraintsFirst = true)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))

                                BoxWithConstraints(modifier = frameModifier) {
                                    val frameW = maxWidth
                                    val frameH = maxHeight
                                    val previewCenterGeo = liveLocation ?: activeRoute?.bounds?.center

                                    // Timestamp watermark hint at configured corner
                                    Text(
                                        text = "NH-58 • ${currentChainage.ifBlank { "200+150" }}",
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                    )

                                    if (draftOverlay.showMapInset && activeStyleConfig.enabled) {
                                        val scaleToPreview = (minOf(frameW, frameH) / 300.dp).coerceIn(0.38f, 0.75f)
                                        val pW = (activeStyleConfig.resolveWidthDp().dp * scaleToPreview)
                                            .coerceIn(28.dp, frameW * 0.48f)
                                        val pH = (activeStyleConfig.resolveHeightDp().dp * scaleToPreview)
                                            .coerceIn(28.dp, frameH * 0.48f)
                                        val margin = 6.dp
                                        val maxX = (frameW - margin * 2 - pW).coerceAtLeast(0.dp)
                                        val maxY = (frameH - margin * 2 - pH).coerceAtLeast(0.dp)

                                        val (ox, oy) = when (activeStyleConfig.position) {
                                            MiniMapPosition.TOP_LEFT -> Pair(margin, margin)
                                            MiniMapPosition.TOP_RIGHT -> Pair(margin + maxX, margin)
                                            MiniMapPosition.BOTTOM_LEFT -> Pair(margin, margin + maxY)
                                            MiniMapPosition.BOTTOM_RIGHT -> Pair(margin + maxX, margin + maxY)
                                            MiniMapPosition.CUSTOM -> Pair(
                                                margin + maxX * activeStyleConfig.customPosXFraction.coerceIn(0f, 1f),
                                                margin + maxY * activeStyleConfig.customPosYFraction.coerceIn(0f, 1f)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .offset(x = ox, y = oy)
                                                .size(width = pW, height = pH)
                                        ) {
                                            MiniMapPreview(
                                                location = previewCenterGeo,
                                                bearing = liveBearing,
                                                routeData = activeRoute,
                                                highwayTag = "",
                                                miniMapConfig = activeStyleConfig,
                                                chainageLabel = currentChainage,
                                                sideLabel = currentSide,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Mini Map Hidden",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // B. SIZE
                    MiniMapGroupTitle("B. MINI MAP SIZE", accentColor)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMapSizePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = activeStyleConfig.sizePreset == preset,
                                onClick = { updateActiveStyle { it.copy(sizePreset = preset) } },
                                label = { Text(preset.label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = accentContentColor
                                )
                            )
                        }
                    }
                    if (activeStyleConfig.sizePreset == MiniMapSizePreset.CUSTOM) {
                        MiniMapSliderRow(
                            title = "Custom Width",
                            value = activeStyleConfig.customWidthDp.toFloat(),
                            valueRange = 44f..220f,
                            valueText = "${activeStyleConfig.customWidthDp} dp",
                            accentColor = accentColor,
                            onValueChange = { w -> updateActiveStyle { it.copy(customWidthDp = w.toInt()) } }
                        )
                        MiniMapSliderRow(
                            title = "Custom Height",
                            value = activeStyleConfig.customHeightDp.toFloat(),
                            valueRange = 44f..220f,
                            valueText = "${activeStyleConfig.customHeightDp} dp",
                            accentColor = accentColor,
                            onValueChange = { h -> updateActiveStyle { it.copy(customHeightDp = h.toInt()) } }
                        )
                    }

                    // C. OPACITY
                    MiniMapGroupTitle("C. MINI MAP OPACITY", accentColor)
                    MiniMapSliderRow(
                        title = "Overall Mini Map Opacity",
                        value = activeStyleConfig.opacity * 100f,
                        valueRange = 0f..100f,
                        valueText = "${(activeStyleConfig.opacity * 100).toInt()}%",
                        accentColor = accentColor,
                        onValueChange = { pct -> updateActiveStyle { it.copy(opacity = (pct / 100f).coerceIn(0f, 1f)) } }
                    )

                    // D. POSITION
                    MiniMapGroupTitle("D. MINI MAP POSITION", accentColor)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMapPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = activeStyleConfig.position == pos,
                                onClick = { updateActiveStyle { it.copy(position = pos) } },
                                label = { Text(pos.label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = accentContentColor
                                )
                            )
                        }
                    }
                    if (activeStyleConfig.position == MiniMapPosition.CUSTOM) {
                        MiniMapSliderRow(
                            title = "Horizontal Position (Left → Right)",
                            value = activeStyleConfig.customPosXFraction * 100f,
                            valueRange = 0f..100f,
                            valueText = "${(activeStyleConfig.customPosXFraction * 100).toInt()}%",
                            accentColor = accentColor,
                            onValueChange = { x -> updateActiveStyle { it.copy(customPosXFraction = (x / 100f).coerceIn(0f, 1f)) } }
                        )
                        MiniMapSliderRow(
                            title = "Vertical Position (Top → Bottom)",
                            value = activeStyleConfig.customPosYFraction * 100f,
                            valueRange = 0f..100f,
                            valueText = "${(activeStyleConfig.customPosYFraction * 100).toInt()}%",
                            accentColor = accentColor,
                            onValueChange = { y -> updateActiveStyle { it.copy(customPosYFraction = (y / 100f).coerceIn(0f, 1f)) } }
                        )
                    }

                    // E. MAP APPEARANCE
                    MiniMapGroupTitle("E. MAP APPEARANCE & STYLING", accentColor)
                    Text("Corner Shape", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniMapCornerShape.entries.forEach { shape ->
                            FilterChip(
                                selected = activeStyleConfig.cornerShape == shape,
                                onClick = { updateActiveStyle { it.copy(cornerShape = shape) } },
                                label = { Text(shape.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = accentContentColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (activeStyleConfig.cornerShape == MiniMapCornerShape.ROUNDED) {
                        MiniMapSliderRow(
                            title = "Corner Radius",
                            value = activeStyleConfig.cornerRadiusDp.toFloat(),
                            valueRange = 0f..32f,
                            valueText = "${activeStyleConfig.cornerRadiusDp} dp",
                            accentColor = accentColor,
                            onValueChange = { r -> updateActiveStyle { it.copy(cornerRadiusDp = r.toInt()) } }
                        )
                    }

                    MiniMapSwitchRow(
                        title = "Border Outline",
                        subtitle = "Draw border frame around mini map container",
                        checked = activeStyleConfig.borderEnabled,
                        onCheckedChange = { b -> updateActiveStyle { it.copy(borderEnabled = b) } },
                        accentColor = accentColor
                    )
                    if (activeStyleConfig.borderEnabled) {
                        MiniMapSliderRow(
                            title = "Border Thickness",
                            value = activeStyleConfig.borderThicknessDp,
                            valueRange = 0.5f..5.0f,
                            valueText = String.format(Locale.US, "%.1f dp", activeStyleConfig.borderThicknessDp),
                            accentColor = accentColor,
                            onValueChange = { t -> updateActiveStyle { it.copy(borderThicknessDp = t) } }
                        )
                        MiniMapColorRow(
                            title = "Border Color",
                            selectedHex = activeStyleConfig.borderColorHex,
                            colors = listOf("#94A3B8", "#FFFFFF", "#38BDF8", "#FACC15", "#22C55E", "#0F172A"),
                            accentColor = accentColor,
                            onSelect = { hex -> updateActiveStyle { it.copy(borderColorHex = hex) } }
                        )
                    }

                    MiniMapSliderRow(
                        title = "Background Transparency",
                        value = activeStyleConfig.backgroundAlpha * 100f,
                        valueRange = 0f..100f,
                        valueText = "${(activeStyleConfig.backgroundAlpha * 100).toInt()}%",
                        accentColor = accentColor,
                        onValueChange = { bg -> updateActiveStyle { it.copy(backgroundAlpha = (bg / 100f).coerceIn(0f, 1f)) } }
                    )

                    MiniMapSliderRow(
                        title = "Map Brightness",
                        value = activeStyleConfig.brightness * 100f,
                        valueRange = 50f..150f,
                        valueText = "${(activeStyleConfig.brightness * 100).toInt()}%",
                        accentColor = accentColor,
                        onValueChange = { br -> updateActiveStyle { it.copy(brightness = (br / 100f).coerceIn(0.4f, 1.6f)) } }
                    )

                    MiniMapSliderRow(
                        title = "Map Contrast",
                        value = activeStyleConfig.contrast * 100f,
                        valueRange = 60f..160f,
                        valueText = "${(activeStyleConfig.contrast * 100).toInt()}%",
                        accentColor = accentColor,
                        onValueChange = { ct -> updateActiveStyle { it.copy(contrast = (ct / 100f).coerceIn(0.5f, 1.8f)) } }
                    )

                    Text("Map Style", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMapStyle.entries.forEach { style ->
                            FilterChip(
                                selected = activeStyleConfig.mapStyle == style,
                                onClick = { updateActiveStyle { it.copy(mapStyle = style) } },
                                label = { Text(style.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = accentContentColor
                                )
                            )
                        }
                    }

                    MiniMapColorRow(
                        title = "Road Alignment Color",
                        selectedHex = activeStyleConfig.routeColorHex,
                        colors = listOf("#38BDF8", "#FACC15", "#22C55E", "#F97316", "#EC4899", "#FFFFFF"),
                        accentColor = accentColor,
                        onSelect = { hex -> updateActiveStyle { it.copy(routeColorHex = hex) } }
                    )

                    MiniMapColorRow(
                        title = "GPS Position Marker Color",
                        selectedHex = activeStyleConfig.gpsMarkerColorHex,
                        colors = listOf("#EF4444", "#22C55E", "#38BDF8", "#FACC15", "#A855F7", "#FFFFFF"),
                        accentColor = accentColor,
                        onSelect = { hex -> updateActiveStyle { it.copy(gpsMarkerColorHex = hex) } }
                    )

                    Text("Direction Marker Style", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMapDirectionStyle.entries.forEach { dirStyle ->
                            FilterChip(
                                selected = activeStyleConfig.directionMarkerStyle == dirStyle,
                                onClick = { updateActiveStyle { it.copy(directionMarkerStyle = dirStyle) } },
                                label = { Text(dirStyle.label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = accentContentColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    MiniMapSwitchRow(
                        title = "Subtle Drop Shadow",
                        subtitle = "Soft outer elevation shadow behind mini map container",
                        checked = activeStyleConfig.shadowEnabled,
                        onCheckedChange = { s -> updateActiveStyle { it.copy(shadowEnabled = s) } },
                        accentColor = accentColor
                    )

                    // F. MAP ZOOM & ORIENTATION
                    MiniMapGroupTitle("F. MAP ZOOM & ORIENTATION", accentColor)
                    MiniMapSwitchRow(
                        title = "Automatic Zoom",
                        subtitle = "Dynamically fit current GPS position & nearby KMZ alignment",
                        checked = activeStyleConfig.autoZoom,
                        onCheckedChange = { az -> updateActiveStyle { it.copy(autoZoom = az) } },
                        accentColor = accentColor
                    )
                    if (!activeStyleConfig.autoZoom) {
                        MiniMapSliderRow(
                            title = "Manual Zoom Level",
                            value = activeStyleConfig.zoomLevel,
                            valueRange = 12.0f..18.0f,
                            valueText = String.format(Locale.US, "Z %.1f", activeStyleConfig.zoomLevel),
                            accentColor = accentColor,
                            onValueChange = { z -> updateActiveStyle { it.copy(zoomLevel = z) } }
                        )
                    }

                    Text("Map Orientation", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMapOrientationMode.entries.forEach { orient ->
                            FilterChip(
                                selected = activeStyleConfig.orientationMode == orient,
                                onClick = { updateActiveStyle { it.copy(orientationMode = orient) } },
                                label = { Text(orient.label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = accentContentColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // G. MAP INFORMATION TOGGLES
                    MiniMapGroupTitle("G. MAP INFORMATION TOGGLES", accentColor)
                    MiniMapSwitchRow(
                        title = "Current Position Marker",
                        subtitle = "Display live GPS pin inside mini map",
                        checked = activeStyleConfig.showCurrentPositionMarker,
                        onCheckedChange = { v -> updateActiveStyle { it.copy(showCurrentPositionMarker = v) } },
                        accentColor = accentColor
                    )
                    MiniMapSwitchRow(
                        title = "Road Alignment",
                        subtitle = "Display imported KMZ/KML road corridor & pathway",
                        checked = activeStyleConfig.showRoadAlignment,
                        onCheckedChange = { v -> updateActiveStyle { it.copy(showRoadAlignment = v) } },
                        accentColor = accentColor
                    )
                    MiniMapSwitchRow(
                        title = "Current Chainage Label",
                        subtitle = "Show reliable calibrated chainage inside mini map",
                        checked = activeStyleConfig.showCurrentChainageLabel,
                        onCheckedChange = { v -> updateActiveStyle { it.copy(showCurrentChainageLabel = v) } },
                        accentColor = accentColor
                    )
                    MiniMapSwitchRow(
                        title = "LHS / RHS Indicator",
                        subtitle = "Display reliable road side badge inside mini map",
                        checked = activeStyleConfig.showSideIndicator,
                        onCheckedChange = { v -> updateActiveStyle { it.copy(showSideIndicator = v) } },
                        accentColor = accentColor
                    )
                    MiniMapSwitchRow(
                        title = "Direction Arrow",
                        subtitle = "Show travel/road heading indicator",
                        checked = activeStyleConfig.showDirectionArrow,
                        onCheckedChange = { v -> updateActiveStyle { it.copy(showDirectionArrow = v) } },
                        accentColor = accentColor
                    )
                    MiniMapSwitchRow(
                        title = "Nearby Chainage Markers",
                        subtitle = "Display KM stone / chainage dots along nearby alignment",
                        checked = activeStyleConfig.showNearbyChainageMarkers,
                        onCheckedChange = { v -> updateActiveStyle { it.copy(showNearbyChainageMarkers = v) } },
                        accentColor = accentColor
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // SAVE / CANCEL / RESET ACTIONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSaveConfig(draftOverlay) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = accentContentColor
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("minimap_save_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                draftOverlay = savedOverlay
                                onShowMessage("Mini Map changes discarded")
                            },
                            enabled = hasUnsavedChanges,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("minimap_cancel_button")
                        ) {
                            Text("Cancel Changes", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            draftOverlay = draftOverlay.copy(
                                showMapInset = true,
                                photoMiniMap = MiniMapStyleConfig(),
                                videoMiniMap = MiniMapStyleConfig()
                            )
                            onResetMiniMapDefaults()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("minimap_reset_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Mini Map to Default", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMapGroupTitle(title: String, accentColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MiniMapSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    tag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            ),
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier
        )
    }
}

@Composable
private fun MiniMapSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
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
                activeTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun MiniMapColorRow(
    title: String,
    selectedHex: String,
    colors: List<String>,
    accentColor: Color,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { hex ->
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                val parsed = try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (_: Exception) {
                    Color.White
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parsed)
                        .border(
                            width = if (isSelected) 2.8.dp else 1.dp,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable { onSelect(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (hex.equals("#FFFFFF", true) || hex.equals("#FACC15", true)) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
