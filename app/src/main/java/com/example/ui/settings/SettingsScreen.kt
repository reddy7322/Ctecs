package com.example.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.CameraGridMode
import com.example.model.CameraSettings
import com.example.model.CaptureTimer
import com.example.model.CoordinateFormat
import com.example.model.LogoPosition
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.VideoQualityOption
import com.example.overlay.LogoProvider
import com.example.ui.MainViewModel
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DisabledGray
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SurfaceDark
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val currentProject by viewModel.projectProfile.collectAsStateWithLifecycle()
    val currentOverlay by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val currentCameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val manualChainage by viewModel.manualChainage.collectAsStateWithLifecycle()

    // In-App Update States
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var isPreviewLandscape by remember { mutableStateOf(false) }

    // Section Expand/Collapse State - Project Settings is open by default at the top
    val expandedSections = remember {
        mutableStateMapOf(
            "project" to true,
            "camera" to false,
            "timestamp" to false,
            "typography" to false,
            "logo" to false,
            "metadata" to false,
            "gps" to false,
            "filename" to false,
            "orientation" to false,
            "map_compass" to false,
            "update" to false,
            "about" to false
        )
    }

    // Photo picker launcher for custom logo
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val targetFile = File(context.filesDir, "custom_user_logo.png")
                input?.use { inStream ->
                    targetFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                viewModel.saveOverlayConfigSilent(
                    currentOverlay.copy(
                        showLogo = true,
                        logoPreset = "custom",
                        logoUri = targetFile.absolutePath
                    )
                )
                viewModel.showToast("Custom logo imported successfully")
            } catch (e: Exception) {
                viewModel.showToast("Failed to load logo image")
            }
        }
    }

    // KMZ / KML File Picker Launcher
    val kmzPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_alignment.kmz"
            viewModel.importRoute(uri, displayName)
            viewModel.showToast("Importing $displayName...")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        )
                        Text(
                            text = "Project, Timestamp & Camera Configuration",
                            style = MaterialTheme.typography.labelSmall.copy(color = SecondaryText)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.testTag("restore_defaults_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore Defaults",
                            tint = SecondaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = ElevatedSurface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings saved automatically",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("settings_done_button")
                    ) {
                        Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = PrimaryBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Instant Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_search_input"),
                placeholder = { Text("Search settings (e.g. project, font, color, gps, chainage)...", color = SecondaryText, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SecondaryText, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Expand / Collapse All Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SETTINGS SECTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )
                Row {
                    TextButton(
                        onClick = {
                            listOf(
                                "project", "camera", "timestamp", "typography", "logo",
                                "metadata", "gps", "filename", "orientation", "map_compass", "update", "about"
                            ).forEach { expandedSections[it] = true }
                        }
                    ) {
                        Text("Expand All", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                    TextButton(
                        onClick = {
                            listOf(
                                "project", "camera", "timestamp", "typography", "logo",
                                "metadata", "gps", "filename", "orientation", "map_compass", "update", "about"
                            ).forEach { expandedSections[it] = false }
                        }
                    ) {
                        Text("Collapse All", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ----------------------------------------------------
            // LIVE WATERMARK PREVIEW CARD (with Portrait / Landscape toggle)
            // ----------------------------------------------------
            WatermarkLivePreviewCard(
                overlay = currentOverlay,
                project = currentProject,
                cameraSettings = currentCameraSettings,
                isLandscape = isPreviewLandscape,
                onToggleOrientation = { isPreviewLandscape = !isPreviewLandscape }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ====================================================
            // 1. PROJECT SETTINGS (FIRST SECTION AT TOP)
            // ====================================================
            if (matchesSearch("project highway road lane activity inspector section separator kmz kml alignment route chainage", searchQuery)) {
                SmartDropdownSection(
                    title = "Project Settings",
                    subtitle = "${currentProject.highwayId.ifBlank { "NH-58" }} • ${currentProject.roadSide.label} • ${currentProject.lane}",
                    icon = Icons.Default.Engineering,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["project"] ?: true,
                    onToggleExpand = { expandedSections["project"] = !(expandedSections["project"] ?: true) }
                ) {
                    // Highway ID
                    OutlinedTextField(
                        value = currentProject.highwayId,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(highwayId = it)) },
                        label = { Text("Highway / Project Name (e.g. NH-58, MH)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    // Package / Section
                    OutlinedTextField(
                        value = currentProject.packageSection,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(packageSection = it)) },
                        label = { Text("Package / Section (e.g. MM, Pkg-1)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    // Road Side (LHS / RHS / MED / BHS)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Road Side (Carriageway)", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoadSide.entries.forEach { side ->
                            FilterChip(
                                selected = currentProject.roadSide == side,
                                onClick = { viewModel.saveProjectProfileSilent(currentProject.copy(roadSide = side)) },
                                label = { Text(side.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Lane
                    OutlinedTextField(
                        value = currentProject.lane,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(lane = it)) },
                        label = { Text("Lane (e.g. I/L, O/L, Shoulder, Median)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    // Activity
                    OutlinedTextField(
                        value = currentProject.activity,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(activity = it)) },
                        label = { Text("Pavement Layer / Activity (e.g. DBM, BC, WMM, Subgrade)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    // Inspector & Separator
                    OutlinedTextField(
                        value = currentProject.inspectorName,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(inspectorName = it)) },
                        label = { Text("Inspector / Engineer Name (Optional)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    OutlinedTextField(
                        value = currentProject.separator,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(separator = it)) },
                        label = { Text("Text Separator (Default: ||)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    // KMZ / KML Route Alignment Import & Selection
                    Text("KMZ / KML Road Alignment & Chainage", style = MaterialTheme.typography.labelMedium, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    Text(
                        text = "Active Route: ${activeRoute?.name ?: "Sample NH-58 Alignment"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { kmzPickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import KMZ/KML", fontSize = 12.sp)
                        }
                    }

                    // Routes Dropdown
                    if (allRoutes.isNotEmpty()) {
                        Text("Select Route Alignment:", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allRoutes.forEach { route ->
                                val isSelected = activeRoute?.name == route.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectActiveRoute(route.id) },
                                    label = { Text(route.name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF38BDF8),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    // Manual Chainage Override
                    if (manualChainage != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Manual Chainage: ${manualChainage!!.format()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD700))
                            TextButton(onClick = { viewModel.clearManualChainage() }) {
                                Text("Switch to Auto GPS", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 2. CAMERA & CAPTURE
            // ====================================================
            if (matchesSearch("camera capture timer grid sound resolution ratio zoom video white balance battery", searchQuery)) {
                SmartDropdownSection(
                    title = "Camera & Capture",
                    subtitle = "${currentOverlay.resolutionOption} • ${currentCameraSettings.previewRatio.label} • Timer ${currentCameraSettings.captureTimer.label}",
                    icon = Icons.Default.CameraAlt,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["camera"] ?: false,
                    onToggleExpand = { expandedSections["camera"] = !(expandedSections["camera"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Keep original photo",
                        subtitle = "Save pristine un-watermarked photo alongside stamped copy",
                        checked = currentOverlay.saveOriginalImage,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(saveOriginalImage = it)) },
                        tag = "switch_keep_original"
                    )

                    CompactSwitchRow(
                        title = "Touch to take photo",
                        subtitle = "Tap anywhere on preview frame to trigger capture",
                        checked = currentCameraSettings.touchToCapture,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(touchToCapture = it)) },
                        tag = "switch_touch_to_capture"
                    )

                    CompactSwitchRow(
                        title = "Open shutter sound",
                        subtitle = "Audible feedback upon photo or video capture",
                        checked = currentCameraSettings.captureSound,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(captureSound = it)) },
                        tag = "switch_shutter_sound"
                    )

                    CompactSwitchRow(
                        title = "Show Grid",
                        subtitle = "Display 3×3 rule-of-thirds composition overlay",
                        checked = currentCameraSettings.gridMode != CameraGridMode.OFF,
                        onCheckedChange = {
                            val newMode = if (it) CameraGridMode.GRID_3X3 else CameraGridMode.OFF
                            viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(gridMode = newMode))
                        },
                        tag = "switch_show_grid"
                    )

                    CompactSwitchRow(
                        title = "Always display zoom slider",
                        subtitle = "Keep manual zoom control visible on viewfinder",
                        checked = currentCameraSettings.alwaysDisplayZoomSlider,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(alwaysDisplayZoomSlider = it)) },
                        tag = "switch_always_zoom"
                    )

                    CompactSwitchRow(
                        title = "Easy to toggle cameras",
                        subtitle = "One-tap front/back camera switch button",
                        checked = currentCameraSettings.easyToggleCameras,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(easyToggleCameras = it)) },
                        tag = "switch_easy_toggle_cameras"
                    )

                    CompactSwitchRow(
                        title = "Double-tap switch battery save",
                        subtitle = "Dim viewfinder screen when battery save mode is active",
                        checked = currentCameraSettings.batterySaveMode,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(batterySaveMode = it)) },
                        tag = "switch_battery_save"
                    )

                    if (currentCameraSettings.batterySaveMode) {
                        SliderRow(
                            title = "Battery save brightness",
                            value = currentCameraSettings.batterySaveBrightness,
                            onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(batterySaveBrightness = it)) },
                            valueText = "${(currentCameraSettings.batterySaveBrightness * 100).toInt()}%"
                        )
                    }

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    // Capture Timer
                    Text("Capture Timer", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CaptureTimer.entries.forEach { timer ->
                            FilterChip(
                                selected = currentCameraSettings.captureTimer == timer,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(captureTimer = timer)) },
                                label = { Text(timer.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00BCD4),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Resolution Options (from Screenshot 1)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Photo Resolution", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val resolutionOptions = listOf(
                        "1920 x 864", "1440 x 1080", "1088 x 1088", "1280 x 720",
                        "960 x 720", "720 x 480", "640 x 480", "640 x 360", "352 x 288"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        resolutionOptions.forEach { res ->
                            FilterChip(
                                selected = currentOverlay.resolutionOption == res,
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(resolutionOption = res)) },
                                label = { Text(res, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00BCD4),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Preview Ratio
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Preview Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.model.PreviewRatio.entries.forEach { ratio ->
                            FilterChip(
                                selected = currentCameraSettings.previewRatio == ratio,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(previewRatio = ratio)) },
                                label = { Text(ratio.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Video Quality & White Balance
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Video Quality", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = currentCameraSettings.videoQuality == VideoQualityOption.HD_720P,
                                    onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(videoQuality = VideoQualityOption.HD_720P)) },
                                    label = { Text("Normal", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = currentCameraSettings.videoQuality == VideoQualityOption.FHD_1080P,
                                    onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(videoQuality = VideoQualityOption.FHD_1080P)) },
                                    label = { Text("High", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF38BDF8), selectedLabelColor = Color.Black)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("White Balance", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Text(
                                text = currentCameraSettings.whiteBalance.capitalize(Locale.ROOT),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 3. TIMESTAMP & WATERMARK
            // ====================================================
            if (matchesSearch("timestamp watermark time format date placement background opacity 24-hour", searchQuery)) {
                SmartDropdownSection(
                    title = "Timestamp & Watermark",
                    subtitle = "${currentOverlay.watermarkPosition} • ${if (currentOverlay.burnInTimestamp) "Enabled" else "Disabled"}",
                    icon = Icons.Default.BrandingWatermark,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["timestamp"] ?: false,
                    onToggleExpand = { expandedSections["timestamp"] = !(expandedSections["timestamp"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Enable Timestamp & Overlay",
                        subtitle = "Burn timestamp, chainage, coordinates onto captured media",
                        checked = currentOverlay.burnInTimestamp,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(burnInTimestamp = it)) },
                        tag = "switch_burn_in_timestamp"
                    )

                    CompactSwitchRow(
                        title = "24-Hour Time Format",
                        subtitle = "Display 23:45 instead of 11:45 PM",
                        checked = currentCameraSettings.is24HourFormat,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(is24HourFormat = it)) },
                        tag = "switch_24_hour"
                    )

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    // Timestamp Format Presets (from Screenshot 4)
                    Text("Timestamp Format Presets", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val timestampPresets = listOf(
                        "dd MMMM yyyy h:mm a" to "28 October 2026 10:57 pm",
                        "yyyy-MM-dd HH:mm:ss" to "2026-10-28 22:57:49",
                        "dd/MM/yyyy HH:mm:ss" to "28/10/2026 22:57:49",
                        "MM/dd/yyyy h:mm:ss a" to "10/28/2026 10:57:49 PM",
                        "dd-MMM-yyyy HH:mm" to "28-Oct-2026 22:57",
                        "EEE, dd MMM yyyy HH:mm" to "Wed, 28 Oct 2026 22:57",
                        "dd MMMM yyyy" to "28 October 2026",
                        "HH:mm:ss" to "22:57:49"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        timestampPresets.forEach { (pattern, sample) ->
                            val isSelected = currentOverlay.timestampFormat == pattern
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.saveOverlayConfigSilent(currentOverlay.copy(timestampFormat = pattern)) }
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(timestampFormat = pattern)) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD700))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(sample, fontSize = 13.sp, color = if (isSelected) Color(0xFFFFD700) else PrimaryText)
                                    Text(pattern, fontSize = 10.sp, color = SecondaryText)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    // Time Placement (above vs below)
                    Text("Time Placement", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentOverlay.timePosition.equals("Time in above", ignoreCase = true),
                            onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(timePosition = "Time in above")) },
                            label = { Text("Time in above", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD700),
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = currentOverlay.timePosition.equals("Time in below", ignoreCase = true),
                            onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(timePosition = "Time in below")) },
                            label = { Text("Time in below", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD700),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Watermark Position Presets
                    Text("Watermark Position", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val positions = listOf(
                        "Bottom Right", "Bottom Center", "Bottom Left",
                        "Top Right", "Top Center", "Top Left", "Center"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        positions.forEach { pos ->
                            FilterChip(
                                selected = currentOverlay.watermarkPosition.equals(pos, ignoreCase = true),
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(watermarkPosition = pos)) },
                                label = { Text(pos, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Background Style & Opacity
                    Text("Background Style", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Transparent", "Solid", "Translucent Glass").forEach { style ->
                            FilterChip(
                                selected = currentOverlay.backgroundStyle.equals(style, ignoreCase = true),
                                onClick = {
                                    val newAlpha = when (style) {
                                        "Solid" -> 1.0f
                                        "Translucent Glass" -> 0.5f
                                        else -> 0.0f
                                    }
                                    viewModel.saveOverlayConfigSilent(
                                        currentOverlay.copy(
                                            backgroundStyle = style,
                                            backgroundOpacity = newAlpha
                                        )
                                    )
                                },
                                label = { Text(style, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    SliderRow(
                        title = "Background Opacity",
                        value = currentOverlay.backgroundOpacity,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(backgroundOpacity = it)) },
                        valueText = "${(currentOverlay.backgroundOpacity * 100).toInt()}%"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 4. TYPOGRAPHY & COLOURS
            // ====================================================
            if (matchesSearch("typography font color size style shadow opacity palette", searchQuery)) {
                SmartDropdownSection(
                    title = "Typography & Colours",
                    subtitle = "${currentOverlay.fontFamily} • ${currentOverlay.fontSizePreset} (${currentOverlay.fontSizeSp.toInt()}sp)",
                    icon = Icons.Default.TextFields,
                    accentColor = Color(0xFF00BCD4),
                    isExpanded = expandedSections["typography"] ?: false,
                    onToggleExpand = { expandedSections["typography"] = !(expandedSections["typography"] ?: false) }
                ) {
                    // Font Family Selector (from Screenshot 10)
                    Text("Font Family", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val fontFamilies = listOf("Default", "Noto Sans", "Source Sans Pro", "Dancing Script", "Monospace", "Serif")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fontFamilies.forEach { font ->
                            FilterChip(
                                selected = currentOverlay.fontFamily.equals(font, ignoreCase = true),
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(fontFamily = font)) },
                                label = { Text(font, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00BCD4),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Font Style
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Font Style", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Regular", "Medium", "Bold", "Italic").forEach { style ->
                            FilterChip(
                                selected = currentOverlay.fontStyle.equals(style, ignoreCase = true),
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(fontStyle = style)) },
                                label = { Text(style, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00BCD4),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    // Font Size Presets (from Screenshot 12)
                    Text("Text Size Presets", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val sizePresets = listOf(
                        "Tiny" to 14f,
                        "Small" to 18f,
                        "Medium" to 22f,
                        "Big" to 26f,
                        "Huge" to 32f,
                        "Huger" to 40f
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sizePresets.forEach { (label, spVal) ->
                            FilterChip(
                                selected = currentOverlay.fontSizePreset.equals(label, ignoreCase = true),
                                onClick = {
                                    viewModel.saveOverlayConfigSilent(
                                        currentOverlay.copy(
                                            fontSizePreset = label,
                                            fontSizeSp = spVal
                                        )
                                    )
                                },
                                label = { Text("$label (${spVal.toInt()}sp)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00BCD4),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    SliderRow(
                        title = "Custom Font Size Slider",
                        value = (currentOverlay.fontSizeSp - 12f) / 36f,
                        onValueChange = { norm ->
                            val sp = 12f + (norm * 36f)
                            viewModel.saveOverlayConfigSilent(
                                currentOverlay.copy(
                                    fontSizeSp = sp,
                                    fontSizePreset = "Custom"
                                )
                            )
                        },
                        valueText = "${currentOverlay.fontSizeSp.toInt()} sp"
                    )

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    // Color Palette Swatches (from Screenshot 5)
                    Text("Font Color Palette", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val swatches = listOf(
                        "#FFFFFF" to "White",
                        "#FFEB3B" to "Yellow",
                        "#CDDC39" to "Lime",
                        "#4CAF50" to "Green",
                        "#1B5E20" to "Dark Green",
                        "#00BCD4" to "Cyan",
                        "#03A9F4" to "Sky Blue",
                        "#FF9800" to "Orange",
                        "#F44336" to "Red",
                        "#E91E63" to "Pink",
                        "#9C27B0" to "Purple",
                        "#000000" to "Black"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        swatches.forEach { (hex, name) ->
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                            val isSelected = currentOverlay.textColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF00BCD4) else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.saveOverlayConfigSilent(currentOverlay.copy(textColorHex = hex)) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = name,
                                        tint = if (hex == "#FFFFFF" || hex == "#FFEB3B" || hex == "#CDDC39") Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    SliderRow(
                        title = "Text Opacity",
                        value = currentOverlay.textOpacity,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(textOpacity = it)) },
                        valueText = "${(currentOverlay.textOpacity * 100).toInt()}%"
                    )

                    CompactSwitchRow(
                        title = "Text Outline / Shadow",
                        subtitle = "Render dark shadow behind text for outdoor legibility",
                        checked = currentOverlay.hasTextShadow,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(hasTextShadow = it)) },
                        tag = "switch_text_shadow"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 5. CUSTOM LOGO & BRANDING
            // ====================================================
            if (matchesSearch("logo custom branding image stamp png ctecs nhai survey", searchQuery)) {
                SmartDropdownSection(
                    title = "Custom Logo",
                    subtitle = if (currentOverlay.showLogo) "Active: ${currentOverlay.logoPreset.uppercase()}" else "Disabled",
                    icon = Icons.Default.AddPhotoAlternate,
                    accentColor = Color(0xFFE53935),
                    isExpanded = expandedSections["logo"] ?: false,
                    onToggleExpand = { expandedSections["logo"] = !(expandedSections["logo"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show Logo on Watermark",
                        subtitle = "Display company, project, or department seal on captured media",
                        checked = currentOverlay.showLogo,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showLogo = it)) },
                        tag = "switch_show_logo"
                    )

                    if (currentOverlay.showLogo) {
                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                        // Logo Presets: ctecs, nhai, survey_seal, custom
                        Text("Logo Preset", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                        val logoPresets = listOf("ctecs", "nhai", "survey_seal", "custom")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            logoPresets.forEach { preset ->
                                FilterChip(
                                    selected = currentOverlay.logoPreset.equals(preset, ignoreCase = true),
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoPreset = preset)) },
                                    label = { Text(preset.uppercase(Locale.ROOT), fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE53935),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Logo Import & Preview Box
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val previewBmp = LogoProvider.getProcessedLogo(
                                        context = context,
                                        config = currentOverlay,
                                        targetWidthPx = 64
                                    )
                                    if (previewBmp != null) {
                                        Image(
                                            bitmap = previewBmp.asImageBitmap(),
                                            contentDescription = "Logo Preview",
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.4f))
                                                .border(1.dp, Color(0xFFE53935), RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(ElevatedSurface, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = SecondaryText)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Active: ${currentOverlay.logoPreset.uppercase()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText
                                        )
                                        Text(
                                            text = if (currentOverlay.logoUri != null) "Custom PNG file attached" else "Built-in vector asset",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecondaryText
                                        )
                                    }
                                }

                                Row {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.testTag("import_logo_button")
                                    ) {
                                        Text("Import PNG", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                    }
                                    if (currentOverlay.logoUri != null) {
                                        IconButton(
                                            onClick = {
                                                viewModel.saveOverlayConfigSilent(
                                                    currentOverlay.copy(
                                                        logoPreset = "ctecs",
                                                        logoUri = null
                                                    )
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove Custom", tint = Color(0xFFE53935))
                                        }
                                    }
                                }
                            }
                        }

                        // Logo Position Presets
                        Text("Logo Placement", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                        val logoPositions = LogoPosition.entries
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            logoPositions.forEach { pos ->
                                FilterChip(
                                    selected = currentOverlay.logoPosition == pos,
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoPosition = pos)) },
                                    label = { Text(pos.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE53935),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        SliderRow(
                            title = "Logo Size",
                            value = (currentOverlay.logoSizeDp - 32f) / (140f - 32f),
                            onValueChange = { norm ->
                                val dp = (32f + (norm * (140f - 32f))).toInt()
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoSizeDp = dp))
                            },
                            valueText = "${currentOverlay.logoSizeDp} dp"
                        )

                        SliderRow(
                            title = "Logo Opacity",
                            value = currentOverlay.logoOpacity,
                            onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoOpacity = it)) },
                            valueText = "${(currentOverlay.logoOpacity * 100).toInt()}%"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 6. METADATA & FIELD SELECTORS
            // ====================================================
            if (matchesSearch("metadata fields selector date coordinates chainage map project info compass altitude accuracy speed direction", searchQuery)) {
                SmartDropdownSection(
                    title = "Metadata & Field Selectors",
                    subtitle = "Chainage, Coordinates, Telemetry & Tags",
                    icon = Icons.Default.Layers,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["metadata"] ?: false,
                    onToggleExpand = { expandedSections["metadata"] = !(expandedSections["metadata"] ?: false) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.saveOverlayConfigSilent(
                                    currentOverlay.copy(
                                        showTimestamp = true,
                                        showCoordinates = true,
                                        showChainage = true,
                                        showMapInset = true,
                                        showProjectInfo = true,
                                        showCompass = true,
                                        showAltitude = true,
                                        showAccuracy = true
                                    )
                                )
                            }
                        ) {
                            Text("Select All", fontSize = 12.sp, color = Color(0xFF38BDF8))
                        }
                        TextButton(
                            onClick = {
                                viewModel.saveOverlayConfigSilent(
                                    currentOverlay.copy(
                                        showTimestamp = false,
                                        showCoordinates = false,
                                        showChainage = false,
                                        showMapInset = false,
                                        showProjectInfo = false,
                                        showCompass = false,
                                        showAltitude = false,
                                        showAccuracy = false
                                    )
                                )
                            }
                        ) {
                            Text("Deselect All", fontSize = 12.sp, color = SecondaryText)
                        }
                    }

                    CompactSwitchRow(
                        title = "Date & Time",
                        subtitle = "Display live timestamp on overlay",
                        checked = currentOverlay.showTimestamp,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showTimestamp = it)) },
                        tag = "switch_field_timestamp"
                    )

                    CompactSwitchRow(
                        title = "GPS Coordinates (Lat / Long)",
                        subtitle = "Geographic coordinates from device GPS",
                        checked = currentOverlay.showCoordinates,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCoordinates = it)) },
                        tag = "switch_field_coordinates"
                    )

                    CompactSwitchRow(
                        title = "Highway Chainage (KM 198+450)",
                        subtitle = "Real-time KMZ calculated chainage along road alignment",
                        checked = currentOverlay.showChainage,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showChainage = it)) },
                        tag = "switch_field_chainage"
                    )

                    CompactSwitchRow(
                        title = "Lower-Left Mini-Map Inset",
                        subtitle = "Miniature route overview map with alignment indicator",
                        checked = currentOverlay.showMapInset,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showMapInset = it)) },
                        tag = "switch_field_map"
                    )

                    CompactSwitchRow(
                        title = "Project Profile Info",
                        subtitle = "Highway number, package, section, lane, activity",
                        checked = currentOverlay.showProjectInfo,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showProjectInfo = it)) },
                        tag = "switch_field_project"
                    )

                    CompactSwitchRow(
                        title = "Compass Heading",
                        subtitle = "Directional azimuth (e.g. 142° SE)",
                        checked = currentOverlay.showCompass,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCompass = it)) },
                        tag = "switch_field_compass"
                    )

                    CompactSwitchRow(
                        title = "GPS Accuracy & Altitude",
                        subtitle = "Estimated horizontal error in metres and elevation",
                        checked = currentOverlay.showAccuracy,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showAccuracy = it, showAltitude = it)) },
                        tag = "switch_field_accuracy"
                    )

                    // Speed & Direction: strictly governed by user settings; OFF by default
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Speed & Travel Direction (Optional Telemetry)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = "Hidden by default. Enable independently for live preview or captured photos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    CompactSwitchRow(
                        title = "Show Speed on Live Camera Preview",
                        subtitle = "Display km/h on active camera viewfinder",
                        checked = currentOverlay.showSpeedOnPreview,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showSpeedOnPreview = it)) },
                        tag = "switch_speed_preview"
                    )

                    CompactSwitchRow(
                        title = "Burn Speed onto Captured Photo/Video",
                        subtitle = "Permanently imprint speed watermark onto media",
                        checked = currentOverlay.includeSpeedInCapture,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(includeSpeedInCapture = it)) },
                        tag = "switch_speed_capture"
                    )

                    CompactSwitchRow(
                        title = "Show Direction on Live Camera Preview",
                        subtitle = "Display travel bearing on active viewfinder",
                        checked = currentOverlay.showDirectionOnPreview,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showDirectionOnPreview = it)) },
                        tag = "switch_dir_preview"
                    )

                    CompactSwitchRow(
                        title = "Burn Direction onto Captured Media",
                        subtitle = "Permanently imprint travel bearing onto photo",
                        checked = currentOverlay.includeDirectionInCapture,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(includeDirectionInCapture = it)) },
                        tag = "switch_dir_capture"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 7. GPS & LOCATION BREAKDOWN
            // ====================================================
            if (matchesSearch("gps location address coordinate precision dms utm format margin interval", searchQuery)) {
                SmartDropdownSection(
                    title = "GPS & Location",
                    subtitle = "${currentCameraSettings.coordinateFormat.label} • Margin ${currentOverlay.gpsAccuracyMarginPreset}",
                    icon = Icons.Default.MyLocation,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["gps"] ?: false,
                    onToggleExpand = { expandedSections["gps"] = !(expandedSections["gps"] ?: false) }
                ) {
                    Text("Coordinate Format", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoordinateFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = currentCameraSettings.coordinateFormat == fmt,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(coordinateFormat = fmt)) },
                                label = { Text(fmt.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("GPS Accuracy Margin Suffix", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val marginPresets = listOf("Off", "±3m", "±5m", "±10m", "Auto")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        marginPresets.forEach { m ->
                            FilterChip(
                                selected = currentOverlay.gpsAccuracyMarginPreset.equals(m, ignoreCase = true),
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(gpsAccuracyMarginPreset = m)) },
                                label = { Text(m, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    Text("Address / Location Option", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Text(
                        text = "Select geographical detail level or pick None to hide.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    val addressLevels = listOf(
                        "None",
                        "India",
                        "Uttarakhand",
                        "Garhwal Division",
                        "Rehmadpur",
                        "National Highway 58",
                        "Sant Niwas",
                        "Sant Niwas, National Highway 58, Rehm...",
                        "Sant Niwas \nNational Highway 58 \nRehmadpur \nUttarakhand 247667 \nIndia"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        addressLevels.forEach { opt ->
                            val isSelected = currentOverlay.locationAddressOption == opt
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.saveOverlayConfigSilent(currentOverlay.copy(locationAddressOption = opt)) }
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(locationAddressOption = opt)) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD700))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = opt,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFFFFD700) else PrimaryText,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = currentOverlay.customAddressText,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(customAddressText = it)) },
                        label = { Text("Custom Address / Site Location Text (Optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFFD700), unfocusedBorderColor = BorderColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 8. FILENAME & STORAGE
            // ====================================================
            if (matchesSearch("filename storage path folder format naming dcim pictures media tag", searchQuery)) {
                SmartDropdownSection(
                    title = "Filename & Storage",
                    subtitle = currentCameraSettings.filenameFormatPreset,
                    icon = Icons.Default.Folder,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["filename"] ?: false,
                    onToggleExpand = { expandedSections["filename"] = !(expandedSections["filename"] ?: false) }
                ) {
                    Text("Filename Format Presets", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val filenamePresets = listOf(
                        "TimePhoto_YYYYMMDD_HHMMSS",
                        "YYYYMMDD_HHMMSS",
                        "YYYYMMDDHHMMSS",
                        "YYYYMMDD_HHMMSS_address",
                        "YYYYMMDD_HHMMSS_chainage",
                        "IndexNumber_YYYYMMDD_HHMMSS_address",
                        "chainage_address_YYYYMMDD_HHMMSS",
                        "tag_chainage_YYYYMMDD_HHMMSS"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        filenamePresets.forEach { preset ->
                            val isSelected = currentCameraSettings.filenameFormatPreset == preset
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(filenameFormatPreset = preset)) }
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(filenameFormatPreset = preset)) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFF38BDF8) else PrimaryText
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = currentCameraSettings.customFilenamePrefix,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customFilenamePrefix = it)) },
                        label = { Text("Custom Filename Prefix (e.g. NH58_SURVEY)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    OutlinedTextField(
                        value = currentOverlay.customTag,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(customTag = it)) },
                        label = { Text("Survey Tag / Job Identifier") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Storage Destination", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Text(
                                text = "Path: ${currentCameraSettings.customStoragePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryText,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "Auto-indexed into Android Gallery & Pictures/HighwayCamera shared storage",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                            )
                            Button(
                                onClick = { viewModel.showToast("Default storage folder: Pictures/HighwayCamera") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Select Folder", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 9. ORIENTATION & ALIGNMENT
            // ====================================================
            if (matchesSearch("orientation sensor rotation portrait landscape tilt override exif", searchQuery)) {
                SmartDropdownSection(
                    title = "Orientation",
                    subtitle = if (currentCameraSettings.autoRotateUI) "Auto-Override Active" else "Locked",
                    icon = Icons.Default.RotateRight,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["orientation"] ?: false,
                    onToggleExpand = { expandedSections["orientation"] = !(expandedSections["orientation"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Automatic Orientation Override",
                        subtitle = "Switch portrait/landscape dynamically using device sensors even when Android Auto-Rotate is locked",
                        checked = currentCameraSettings.autoRotateUI,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(autoRotateUI = it)) },
                        tag = "switch_auto_rotate_ui"
                    )

                    CompactSwitchRow(
                        title = "Preserve Sensor EXIF Orientation",
                        subtitle = "Imprint sensor rotation tag without double-rotating picture",
                        checked = currentCameraSettings.preserveExif,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(preserveExif = it)) },
                        tag = "switch_preserve_exif"
                    )

                    Text(
                        text = "Supports Portrait (0°), Landscape Right (90°), Reverse Portrait (180°), and Landscape Left (270°). Watermark text rotates gracefully to remain readable in all orientations.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 10. SMART MAP & COMPASS
            // ====================================================
            if (matchesSearch("map compass horizon inset level telemetry navigation", searchQuery)) {
                SmartDropdownSection(
                    title = "Smart Map & Compass",
                    subtitle = "Mini-Map ${if (currentOverlay.showMapInset) "On" else "Off"} • Compass ${if (currentOverlay.showCompass) "On" else "Off"}",
                    icon = Icons.Default.Map,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["map_compass"] ?: false,
                    onToggleExpand = { expandedSections["map_compass"] = !(expandedSections["map_compass"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "In-Camera Vector Mini-Map Inset",
                        subtitle = "Display live road alignment, nearby chainage ticks, and location pin",
                        checked = currentOverlay.showMapInset,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showMapInset = it)) },
                        tag = "switch_map_inset"
                    )

                    CompactSwitchRow(
                        title = "Compass Sensor Heading",
                        subtitle = "Show live compass degrees and cardinal direction",
                        checked = currentOverlay.showCompass,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCompass = it)) },
                        tag = "switch_compass"
                    )

                    CompactSwitchRow(
                        title = "Electronic Horizon Level",
                        subtitle = "Visual horizon level guide in camera preview to prevent tilted photos",
                        checked = currentCameraSettings.showHorizonLevel,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(showHorizonLevel = it)) },
                        tag = "switch_horizon_level"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 11. APP UPDATES
            // ====================================================
            if (matchesSearch("update version release ota download install built-in", searchQuery)) {
                SmartDropdownSection(
                    title = "App Updates",
                    subtitle = "Current Version: v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.SystemUpdate,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["update"] ?: false,
                    onToggleExpand = { expandedSections["update"] = !(expandedSections["update"] ?: false) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Installed Version", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PrimaryText)
                            }

                            Button(
                                onClick = { viewModel.checkForAppUpdate(isManual = true) },
                                enabled = !isCheckingUpdate && !isDownloadingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Checking...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check for Updates", fontSize = 11.sp)
                                }
                            }
                        }

                        if (isDownloadingUpdate) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Downloading update: ${(updateDownloadProgress * 100).toInt()}%", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            LinearProgressIndicator(
                                progress = { updateDownloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).padding(top = 4.dp)
                            )
                        }

                        if (availableUpdate != null && availableUpdate!!.isUpdateAvailable) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0369A1).copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("New Version Available: ${availableUpdate!!.tagName}", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 13.sp)
                                    Text(availableUpdate!!.releaseNotes, fontSize = 11.sp, color = SecondaryText, modifier = Modifier.padding(vertical = 4.dp))
                                    Button(
                                        onClick = { viewModel.downloadAndInstallUpdate() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text("Download & Install Update", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 12. APP INFORMATION
            // ====================================================
            if (matchesSearch("about info application ctecs developer version build", searchQuery)) {
                SmartDropdownSection(
                    title = "App Information",
                    subtitle = "Ctecs Highway Camera • v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.Info,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["about"] ?: false,
                    onToggleExpand = { expandedSections["about"] = !(expandedSections["about"] ?: false) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Application Name", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text("Ctecs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Build Version", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = PrimaryText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Target Architecture", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text("ARM64 / Android 14+ Ready", style = MaterialTheme.typography.bodySmall, color = PrimaryText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Offline KMZ Engine", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text("High-Precision Geodesic Matcher", style = MaterialTheme.typography.bodySmall, color = Color(0xFF38BDF8))
                        }
                        Text(
                            text = "Professional timestamp and highway chainage documentation camera for highway engineers, quality control teams, and survey inspectors.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Restore Defaults Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Default Settings?", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will reset all watermark, camera, typography, and telemetry options to factory defaults. Your saved photos and KMZ routes will not be deleted.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettings()
                        showRestoreDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Reset All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = PrimaryText)
                }
            },
            containerColor = ElevatedSurface
        )
    }
}

// ----------------------------------------------------
// SMART DROPDOWN ACCORDION COMPONENT
// ----------------------------------------------------

@Composable
fun SmartDropdownSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .animateContentSize(animationSpec = tween(250)),
        colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(color = SecondaryText),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp))
                    content()
                }
            }
        }
    }
}

// ----------------------------------------------------
// UI COMPONENTS & LIVE PREVIEW
// ----------------------------------------------------

@Composable
fun WatermarkLivePreviewCard(
    overlay: OverlayConfig,
    project: ProjectProfile,
    cameraSettings: CameraSettings,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("watermark_preview_card"),
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BrandingWatermark,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE WATERMARK PREVIEW",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    )
                }

                FilterChip(
                    selected = isLandscape,
                    onClick = onToggleOrientation,
                    label = { Text(if (isLandscape) "Landscape 16:9" else "Portrait 9:16", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF38BDF8),
                        selectedLabelColor = Color.Black
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simulated Viewfinder Frame
            val previewHeight = if (isLandscape) 150.dp else 220.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 1.dp.toPx()
                    val c = Color.White.copy(alpha = 0.12f)
                    drawLine(c, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeW)
                    drawLine(c, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeW)
                    drawLine(c, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeW)
                    drawLine(c, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeW)
                }

                val textColor = try {
                    Color(android.graphics.Color.parseColor(overlay.textColorHex)).copy(alpha = overlay.textOpacity)
                } catch (e: Exception) {
                    Color.White.copy(alpha = overlay.textOpacity)
                }

                val bgColor = try {
                    Color(android.graphics.Color.parseColor(overlay.backgroundColorHex)).copy(alpha = overlay.backgroundOpacity)
                } catch (e: Exception) {
                    Color.Black.copy(alpha = overlay.backgroundOpacity)
                }

                val alignment = when (overlay.watermarkPosition.lowercase()) {
                    "top left" -> Alignment.TopStart
                    "top center" -> Alignment.TopCenter
                    "top right" -> Alignment.TopEnd
                    "bottom left" -> Alignment.BottomStart
                    "bottom center" -> Alignment.BottomCenter
                    "center" -> Alignment.Center
                    else -> Alignment.BottomEnd
                }

                val sampleDate = SimpleDateFormat(
                    if (cameraSettings.is24HourFormat) "dd MMMM yyyy HH:mm" else overlay.timestampFormat,
                    Locale.US
                ).format(Date())

                val sampleFontFamily = when (overlay.fontFamily.lowercase()) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    else -> FontFamily.SansSerif
                }

                val sampleFontStyle = when (overlay.fontStyle.lowercase()) {
                    "italic" -> FontStyle.Italic
                    else -> FontStyle.Normal
                }

                val sampleFontWeight = when (overlay.fontStyle.lowercase()) {
                    "bold" -> FontWeight.Bold
                    "medium" -> FontWeight.Medium
                    else -> FontWeight.Normal
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = alignment
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = if (alignment == Alignment.BottomStart || alignment == Alignment.TopStart) Alignment.Start else Alignment.End
                    ) {
                        if (overlay.timePosition.equals("Time in above", ignoreCase = true) && overlay.showTimestamp) {
                            Text(
                                text = sampleDate,
                                color = textColor,
                                fontSize = (overlay.fontSizeSp * 0.45f).coerceIn(9f, 16f).sp,
                                fontFamily = sampleFontFamily,
                                fontStyle = sampleFontStyle,
                                fontWeight = sampleFontWeight
                            )
                        }

                        if (overlay.showCoordinates) {
                            Text(
                                text = "29.8919°N 77.9587°E Alt: 268m (${overlay.gpsAccuracyMarginPreset})",
                                color = textColor,
                                fontSize = (overlay.fontSizeSp * 0.42f).coerceIn(8.5f, 15f).sp,
                                fontFamily = sampleFontFamily,
                                fontStyle = sampleFontStyle,
                                fontWeight = sampleFontWeight
                            )
                        }

                        if (overlay.locationAddressOption.isNotBlank() && overlay.locationAddressOption != "None") {
                            val addr = if (overlay.customAddressText.isNotBlank()) overlay.customAddressText else overlay.locationAddressOption
                            Text(
                                text = addr,
                                color = textColor,
                                fontSize = (overlay.fontSizeSp * 0.40f).coerceIn(8f, 14f).sp,
                                fontFamily = sampleFontFamily,
                                fontStyle = sampleFontStyle,
                                fontWeight = sampleFontWeight,
                                maxLines = 2
                            )
                        }

                        if (overlay.showCompass) {
                            Text(
                                text = "Compass: 142° SE",
                                color = textColor,
                                fontSize = (overlay.fontSizeSp * 0.40f).coerceIn(8f, 14f).sp,
                                fontFamily = sampleFontFamily,
                                fontStyle = sampleFontStyle,
                                fontWeight = sampleFontWeight
                            )
                        }

                        if (overlay.showSpeedOnPreview || overlay.includeSpeedInCapture) {
                            Text(
                                text = "Speed: 42.5 km/h",
                                color = Color(0xFFFFD700),
                                fontSize = (overlay.fontSizeSp * 0.40f).coerceIn(8f, 14f).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (overlay.showProjectInfo || overlay.showChainage) {
                            val chainText = if (overlay.showChainage) "KM 198+450" else ""
                            Text(
                                text = project.buildHighwayText(chainText),
                                color = textColor,
                                fontSize = (overlay.fontSizeSp * 0.45f).coerceIn(9f, 16f).sp,
                                fontFamily = sampleFontFamily,
                                fontStyle = sampleFontStyle,
                                fontWeight = sampleFontWeight
                            )
                        }

                        if (!overlay.timePosition.equals("Time in above", ignoreCase = true) && overlay.showTimestamp) {
                            Text(
                                text = sampleDate,
                                color = textColor,
                                fontSize = (overlay.fontSizeSp * 0.45f).coerceIn(9f, 16f).sp,
                                fontFamily = sampleFontFamily,
                                fontStyle = sampleFontStyle,
                                fontWeight = sampleFontWeight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF00BCD4),
                uncheckedThumbColor = DisabledGray,
                uncheckedTrackColor = SurfaceDark
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = PrimaryText)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF00BCD4),
                inactiveTrackColor = BorderColor
            )
        )
    }
}

fun matchesSearch(haystack: String, query: String): Boolean {
    if (query.isBlank()) return true
    val words = query.trim().lowercase().split("\\s+".toRegex())
    val text = haystack.lowercase()
    return words.any { text.contains(it) }
}
