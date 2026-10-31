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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
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
import com.example.camera.CameraResolution
import com.example.camera.CameraResolutionHelper
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
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
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

    // Dialog states for Photo & Video Resolution selection
    var showPhotoResolutionDialog by remember { mutableStateOf(false) }
    var showVideoResolutionDialog by remember { mutableStateOf(false) }

    // Query real camera capability lists
    val photoResolutions = remember(isFrontCamera) {
        CameraResolutionHelper.getSupportedPhotoResolutions(context, isFrontCamera)
    }
    val videoResolutions = remember(isFrontCamera) {
        CameraResolutionHelper.getSupportedVideoResolutions(context, isFrontCamera)
    }

    // ALL 14 SECTIONS ARE COLLAPSED INITIALLY
    val expandedSections = remember {
        mutableStateMapOf(
            "project" to false,
            "camera" to false,
            "photo" to false,
            "video" to false,
            "resolution" to false,
            "timestamp" to false,
            "gps" to false,
            "chainage" to false,
            "metadata" to false,
            "logo" to false,
            "quality" to false,
            "storage" to false,
            "display" to false,
            "permissions_about" to false
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
                            text = "14 Categories • Complete Camera & Overlay Config",
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
                        text = "Preferences saved persistently",
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
                placeholder = { Text("Search settings (e.g. project, resolution, logo, gps, logo)...", color = SecondaryText, fontSize = 13.sp) },
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
                    text = "CATEGORIES (ALL COLLAPSED)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )
                Row {
                    TextButton(
                        onClick = {
                            listOf(
                                "project", "camera", "photo", "video", "resolution",
                                "timestamp", "gps", "chainage", "metadata", "logo",
                                "quality", "storage", "display", "permissions_about"
                            ).forEach { expandedSections[it] = true }
                        }
                    ) {
                        Text("Expand All", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                    TextButton(
                        onClick = {
                            listOf(
                                "project", "camera", "photo", "video", "resolution",
                                "timestamp", "gps", "chainage", "metadata", "logo",
                                "quality", "storage", "display", "permissions_about"
                            ).forEach { expandedSections[it] = false }
                        }
                    ) {
                        Text("Collapse All", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // LIVE WATERMARK PREVIEW CARD
            WatermarkLivePreviewCard(
                overlay = currentOverlay,
                project = currentProject,
                cameraSettings = currentCameraSettings,
                isLandscape = isPreviewLandscape,
                onToggleOrientation = { isPreviewLandscape = !isPreviewLandscape }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ====================================================
            // 1. PROJECT SETTINGS
            // ====================================================
            if (matchesSearch("project highway road lane activity inspector section separator kmz kml alignment route chainage", searchQuery)) {
                SmartDropdownSection(
                    title = "1. Project Settings",
                    subtitle = "${currentProject.highwayId.ifBlank { "NH-58" }} • ${currentProject.roadSide.label} • ${currentProject.lane}",
                    icon = Icons.Default.Engineering,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["project"] ?: false,
                    onToggleExpand = { expandedSections["project"] = !(expandedSections["project"] ?: false) }
                ) {
                    OutlinedTextField(
                        value = currentProject.highwayId,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(highwayId = it)) },
                        label = { Text("Highway / Project Name (e.g. NH-58, MH)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    OutlinedTextField(
                        value = currentProject.packageSection,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(packageSection = it)) },
                        label = { Text("Package / Section (e.g. MM, Pkg-1)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

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

                    OutlinedTextField(
                        value = currentProject.lane,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(lane = it)) },
                        label = { Text("Lane (e.g. I/L, O/L, Shoulder, Median)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    OutlinedTextField(
                        value = currentProject.activity,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(activity = it)) },
                        label = { Text("Pavement Layer / Activity (e.g. DBM, BC, WMM, Subgrade)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

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
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 2. CAMERA AND LENS
            // ====================================================
            if (matchesSearch("camera lens zoom shutter sound grid touch mirror battery save white balance", searchQuery)) {
                SmartDropdownSection(
                    title = "2. Camera and Lens",
                    subtitle = "Grid ${currentCameraSettings.gridMode.label} • Shutter Sound • Lens Controls",
                    icon = Icons.Default.CameraAlt,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["camera"] ?: false,
                    onToggleExpand = { expandedSections["camera"] = !(expandedSections["camera"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Touch to take photo",
                        subtitle = "Tap anywhere on preview frame to trigger photo capture",
                        checked = currentCameraSettings.touchToCapture,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(touchToCapture = it)) },
                        tag = "switch_touch_to_capture"
                    )

                    CompactSwitchRow(
                        title = "Shutter sound",
                        subtitle = "Audible feedback upon photo or video capture",
                        checked = currentCameraSettings.captureSound,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(captureSound = it)) },
                        tag = "switch_shutter_sound"
                    )

                    CompactSwitchRow(
                        title = "Show Grid",
                        subtitle = "Display 3×3 or 4×4 rule-of-thirds alignment grid",
                        checked = currentCameraSettings.gridMode != CameraGridMode.OFF,
                        onCheckedChange = {
                            val newMode = if (it) CameraGridMode.GRID_3X3 else CameraGridMode.OFF
                            viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(gridMode = newMode))
                        },
                        tag = "switch_show_grid"
                    )

                    CompactSwitchRow(
                        title = "Easy to toggle cameras",
                        subtitle = "One-tap front/back camera switch button on viewfinder",
                        checked = currentCameraSettings.easyToggleCameras,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(easyToggleCameras = it)) },
                        tag = "switch_easy_toggle_cameras"
                    )

                    CompactSwitchRow(
                        title = "Front camera mirror",
                        subtitle = "Save front camera photos mirrored as seen in viewfinder",
                        checked = currentCameraSettings.frontCameraMirror,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(frontCameraMirror = it)) },
                        tag = "switch_front_mirror"
                    )

                    CompactSwitchRow(
                        title = "Battery save mode",
                        subtitle = "Dim viewfinder preview brightness when idle to conserve power",
                        checked = currentCameraSettings.batterySaveMode,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(batterySaveMode = it)) },
                        tag = "switch_battery_save"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 3. PHOTO SETTINGS
            // ====================================================
            if (matchesSearch("photo settings original picture ratio jpeg quality timer ratio exif", searchQuery)) {
                SmartDropdownSection(
                    title = "3. Photo Settings",
                    subtitle = "Resolution: ${currentCameraSettings.photoResolution} • Ratio: ${currentCameraSettings.previewRatio.label}",
                    icon = Icons.Default.PhotoCamera,
                    accentColor = Color(0xFF00BCD4),
                    isExpanded = expandedSections["photo"] ?: false,
                    onToggleExpand = { expandedSections["photo"] = !(expandedSections["photo"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Save original photo",
                        subtitle = "Save pristine un-watermarked photo alongside stamped copy",
                        checked = currentOverlay.saveOriginalImage,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(saveOriginalImage = it)) },
                        tag = "switch_keep_original"
                    )

                    CompactSwitchRow(
                        title = "Preserve EXIF metadata",
                        subtitle = "Embed GPS & timestamp inside standard JPEG EXIF headers",
                        checked = currentCameraSettings.preserveExif,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(preserveExif = it)) },
                        tag = "switch_preserve_exif"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Photo Resolution (Real Hardware Capabilities)", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    OutlinedButton(
                        onClick = { showPhotoResolutionDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00BCD4)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BCD4))
                    ) {
                        Text("Photo Resolution: ${currentCameraSettings.photoResolution}", fontWeight = FontWeight.Bold)
                    }

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
                                    selectedContainerColor = Color(0xFF00BCD4),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    SliderRow(
                        title = "JPEG Compression Quality",
                        value = currentCameraSettings.jpegQuality.toFloat(),
                        valueRange = 80f..100f,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(jpegQuality = it.toInt())) },
                        valueText = "${currentCameraSettings.jpegQuality}%"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 4. VIDEO RECORDING SETTINGS
            // ====================================================
            if (matchesSearch("video recording settings fps audio sound mic segmentation quality", searchQuery)) {
                SmartDropdownSection(
                    title = "4. Video Recording Settings",
                    subtitle = "Resolution: ${currentCameraSettings.videoResolution} • Audio: ${if (currentCameraSettings.recordAudio) "ON" else "OFF"}",
                    icon = Icons.Default.Videocam,
                    accentColor = Color(0xFFEF4444),
                    isExpanded = expandedSections["video"] ?: false,
                    onToggleExpand = { expandedSections["video"] = !(expandedSections["video"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Record Audio",
                        subtitle = "Include microphone audio track in recorded video clips",
                        checked = currentCameraSettings.recordAudio,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(recordAudio = it)) },
                        tag = "switch_record_audio"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Video Recording Resolution", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    OutlinedButton(
                        onClick = { showVideoResolutionDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text("Video Resolution: ${currentCameraSettings.videoResolution}", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Target Video Frame Rate", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60).forEach { fps ->
                            FilterChip(
                                selected = currentCameraSettings.videoFps == fps,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(videoFps = fps)) },
                                label = { Text("$fps FPS", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEF4444),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 5. RESOLUTION AND FRAME RATE (DEDICATED DUAL CAPABILITY CARD)
            // ====================================================
            if (matchesSearch("resolution frame rate photo video size dimensions megapixels 4k 1080p 720p", searchQuery)) {
                SmartDropdownSection(
                    title = "5. Resolution and Frame Rate",
                    subtitle = "Photo: ${currentCameraSettings.photoResolution} | Video: ${currentCameraSettings.videoResolution}",
                    icon = Icons.Default.AspectRatio,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["resolution"] ?: false,
                    onToggleExpand = { expandedSections["resolution"] = !(expandedSections["resolution"] ?: false) }
                ) {
                    Text(
                        text = "Separate hardware resolution lists derived from active sensor output capabilities. Orientation flips are normalized and deduplicated.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Photo Resolution Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Photo Resolution Capabilities", fontWeight = FontWeight.Bold, color = Color(0xFF00BCD4), fontSize = 13.sp)
                                Text("${photoResolutions.size} Options Detected", fontSize = 10.sp, color = SecondaryText)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Current Selection: ${currentCameraSettings.photoResolution}", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showPhotoResolutionDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Still Photo Resolution...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Video Resolution Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Video Recording Capabilities", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 13.sp)
                                Text("${videoResolutions.size} Options Detected", fontSize = 10.sp, color = SecondaryText)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Current Selection: ${currentCameraSettings.videoResolution}", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showVideoResolutionDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Video Recording Resolution...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 6. TIMESTAMP AND DATE/TIME FORMAT
            // ====================================================
            if (matchesSearch("timestamp date time format precision 24-hour placement pattern", searchQuery)) {
                SmartDropdownSection(
                    title = "6. Timestamp and Date/Time Format",
                    subtitle = "${currentOverlay.timestampFormat} • ${if (currentCameraSettings.is24HourFormat) "24H" else "12H"}",
                    icon = Icons.Default.BrandingWatermark,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["timestamp"] ?: false,
                    onToggleExpand = { expandedSections["timestamp"] = !(expandedSections["timestamp"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Enable Timestamp Burn-In",
                        subtitle = "Permanently render timestamp onto captured photos & video frames",
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Timestamp Format Presets", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val timestampPresets = listOf(
                        "dd MMMM yyyy h:mm a" to "28 October 2026 10:57 pm",
                        "yyyy-MM-dd HH:mm:ss" to "2026-10-28 22:57:49",
                        "dd/MM/yyyy HH:mm:ss" to "28/10/2026 22:57:49",
                        "MM/dd/yyyy h:mm:ss a" to "10/28/2026 10:57:49 PM",
                        "dd-MMM-yyyy HH:mm" to "28-Oct-2026 22:57",
                        "EEE, dd MMM yyyy HH:mm" to "Wed, 28 Oct 2026 22:57"
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
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 7. GPS AND LOCATION
            // ====================================================
            if (matchesSearch("gps location coordinates dms utm decimal accuracy threshold interval address", searchQuery)) {
                SmartDropdownSection(
                    title = "7. GPS and Location",
                    subtitle = "${currentCameraSettings.coordinateFormat.label} • Threshold ±${currentCameraSettings.gpsAccuracyThresholdMeters.toInt()}m",
                    icon = Icons.Default.MyLocation,
                    accentColor = Color(0xFF22C55E),
                    isExpanded = expandedSections["gps"] ?: false,
                    onToggleExpand = { expandedSections["gps"] = !(expandedSections["gps"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show GPS Coordinates",
                        subtitle = "Render live latitude & longitude on captured media",
                        checked = currentOverlay.showCoordinates,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCoordinates = it)) },
                        tag = "switch_show_coordinates"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Coordinate Format", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CoordinateFormat.entries.forEach { format ->
                            FilterChip(
                                selected = currentCameraSettings.coordinateFormat == format,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(coordinateFormat = format)) },
                                label = { Text(format.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF22C55E),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    SliderRow(
                        title = "Coordinate Decimal Precision",
                        value = currentOverlay.coordinatePrecision.toFloat(),
                        valueRange = 4f..7f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(coordinatePrecision = it.toInt())) },
                        valueText = "${currentOverlay.coordinatePrecision} Decimals"
                    )

                    SliderRow(
                        title = "GPS Accuracy Threshold",
                        value = currentCameraSettings.gpsAccuracyThresholdMeters,
                        valueRange = 5f..50f,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(gpsAccuracyThresholdMeters = it)) },
                        valueText = "±${currentCameraSettings.gpsAccuracyThresholdMeters.toInt()}m"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 8. CHAINAGE, KMZ AND LHS/RHS
            // ====================================================
            if (matchesSearch("chainage kmz kml alignment radius lhs rhs route manual offset", searchQuery)) {
                SmartDropdownSection(
                    title = "8. Chainage, KMZ and LHS/RHS",
                    subtitle = "Matching Radius: ${currentCameraSettings.routeMatchingRadiusMeters.toInt()}m",
                    icon = Icons.Default.LinearScale,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["chainage"] ?: false,
                    onToggleExpand = { expandedSections["chainage"] = !(expandedSections["chainage"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show Chainage Stamp",
                        subtitle = "Burn KMZ-derived chainage onto captured media",
                        checked = currentOverlay.showChainage,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showChainage = it)) },
                        tag = "switch_show_chainage"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Active KMZ Route Alignment", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Text("Currently active: ${activeRoute?.name ?: "Sample NH-58 Route"}", fontSize = 12.sp, color = SecondaryText)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                            Text("Import Custom KMZ/KML", fontSize = 11.sp)
                        }
                    }

                    SliderRow(
                        title = "Route Alignment Search Radius",
                        value = currentCameraSettings.routeMatchingRadiusMeters.toFloat(),
                        valueRange = 20f..300f,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(routeMatchingRadiusMeters = it.toDouble())) },
                        valueText = "${currentCameraSettings.routeMatchingRadiusMeters.toInt()}m"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 9. METADATA AND WATERMARK
            // ====================================================
            if (matchesSearch("metadata watermark position font size color opacity shadow background", searchQuery)) {
                SmartDropdownSection(
                    title = "9. Metadata and Watermark",
                    subtitle = "Position: ${currentOverlay.watermarkPosition} • Size: ${currentOverlay.fontSizePreset}",
                    icon = Icons.Default.BrandingWatermark,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["metadata"] ?: false,
                    onToggleExpand = { expandedSections["metadata"] = !(expandedSections["metadata"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show Project Info",
                        subtitle = "Include Highway ID, Section & Lane details",
                        checked = currentOverlay.showProjectInfo,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showProjectInfo = it)) },
                        tag = "switch_show_project"
                    )

                    CompactSwitchRow(
                        title = "Show Compass Heading",
                        subtitle = "Include live compass direction (e.g. 359° N)",
                        checked = currentOverlay.showCompass,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCompass = it)) },
                        tag = "switch_show_compass"
                    )

                    CompactSwitchRow(
                        title = "Show Altitude",
                        subtitle = "Include elevation in meters above sea level",
                        checked = currentOverlay.showAltitude,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showAltitude = it)) },
                        tag = "switch_show_altitude"
                    )

                    CompactSwitchRow(
                        title = "Show GPS Accuracy",
                        subtitle = "Include GPS error margin (e.g. ±4m)",
                        checked = currentOverlay.showAccuracy,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showAccuracy = it)) },
                        tag = "switch_show_accuracy"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Watermark Placement Position", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val positions = listOf("Bottom Right", "Bottom Left", "Top Right", "Top Left", "Bottom Center", "Top Center", "Center")
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

                    SliderRow(
                        title = "Font Size (SP)",
                        value = currentOverlay.fontSizeSp,
                        valueRange = 14f..42f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(fontSizeSp = it)) },
                        valueText = "${currentOverlay.fontSizeSp.toInt()} sp"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 10. CUSTOM LOGO
            // ====================================================
            if (matchesSearch("custom logo preset nhai survey seal size opacity position shape rotation", searchQuery)) {
                SmartDropdownSection(
                    title = "10. Custom Logo",
                    subtitle = "Preset: ${currentOverlay.logoPreset.uppercase()} • Size: ${currentOverlay.logoSizeDp}dp",
                    icon = Icons.Default.AddPhotoAlternate,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["logo"] ?: false,
                    onToggleExpand = { expandedSections["logo"] = !(expandedSections["logo"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show Logo Stamp",
                        subtitle = "Display company seal / project logo on watermarked media",
                        checked = currentOverlay.showLogo,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showLogo = it)) },
                        tag = "switch_show_logo"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Logo Presets", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ctecs" to "CTECS", "nhai" to "NHAI", "survey_seal" to "Seal").forEach { (key, label) ->
                            FilterChip(
                                selected = currentOverlay.logoPreset == key,
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoPreset = key)) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Custom Logo Image...", fontSize = 12.sp)
                    }

                    SliderRow(
                        title = "Logo Size (DP)",
                        value = currentOverlay.logoSizeDp.toFloat(),
                        valueRange = 32f..120f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoSizeDp = it.toInt())) },
                        valueText = "${currentOverlay.logoSizeDp} dp"
                    )

                    SliderRow(
                        title = "Logo Opacity",
                        value = currentOverlay.logoOpacity,
                        valueRange = 0.2f..1.0f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoOpacity = it)) },
                        valueText = "${(currentOverlay.logoOpacity * 100).toInt()}%"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 11. IMAGE AND VIDEO QUALITY
            // ====================================================
            if (matchesSearch("image video quality jpeg compression bitrate hardware encoder", searchQuery)) {
                SmartDropdownSection(
                    title = "11. Image and Video Quality",
                    subtitle = "JPEG Quality: ${currentCameraSettings.jpegQuality}% • HW Accelerated",
                    icon = Icons.Default.HighQuality,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["quality"] ?: false,
                    onToggleExpand = { expandedSections["quality"] = !(expandedSections["quality"] ?: false) }
                ) {
                    SliderRow(
                        title = "JPEG Quality Level",
                        value = currentCameraSettings.jpegQuality.toFloat(),
                        valueRange = 80f..100f,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(jpegQuality = it.toInt())) },
                        valueText = "${currentCameraSettings.jpegQuality}%"
                    )

                    Text(
                        text = "Videos use hardware-accelerated MediaCodec & MediaMuxer frame pipeline to burn-in dynamic metadata without video quality loss.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 12. STORAGE, FILENAMES AND GALLERY
            // ====================================================
            if (matchesSearch("storage filenames gallery prefix folder path dcim movies", searchQuery)) {
                SmartDropdownSection(
                    title = "12. Storage, Filenames and Gallery",
                    subtitle = "Folder: DCIM/HighwayCamera",
                    icon = Icons.Default.Storage,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["storage"] ?: false,
                    onToggleExpand = { expandedSections["storage"] = !(expandedSections["storage"] ?: false) }
                ) {
                    OutlinedTextField(
                        value = currentCameraSettings.customFilenamePrefix,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customFilenamePrefix = it)) },
                        label = { Text("Custom Filename Prefix (Optional e.g. NH58_)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )

                    OutlinedTextField(
                        value = currentCameraSettings.customStoragePath,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customStoragePath = it)) },
                        label = { Text("Storage Directory Path") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = BorderColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13. DISPLAY AND APPEARANCE
            // ====================================================
            if (matchesSearch("display appearance theme mode horizon level auto-rotate screen on", searchQuery)) {
                SmartDropdownSection(
                    title = "13. Display and Appearance",
                    subtitle = "Theme: ${viewModel.themeMode.collectAsStateWithLifecycle().value.uppercase()}",
                    icon = Icons.Default.Palette,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["display"] ?: false,
                    onToggleExpand = { expandedSections["display"] = !(expandedSections["display"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Horizon level indicator",
                        subtitle = "Display electronic horizon line on camera viewfinder",
                        checked = currentCameraSettings.showHorizonLevel,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(showHorizonLevel = it)) },
                        tag = "switch_horizon_level"
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("App Theme Mode", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 14. PERMISSIONS AND APP INFORMATION
            // ====================================================
            if (matchesSearch("permissions app information version build update restore default", searchQuery)) {
                SmartDropdownSection(
                    title = "14. Permissions and App Information",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    icon = Icons.Default.Info,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["permissions_about"] ?: false,
                    onToggleExpand = { expandedSections["permissions_about"] = !(expandedSections["permissions_about"] ?: false) }
                ) {
                    val currentChannel = com.example.update.AppReleaseChannel.current()
                    val isBetaApp = currentChannel == com.example.update.AppReleaseChannel.BETA
                    val channelColor = if (isBetaApp) Color(0xFFFBBF24) else Color(0xFF38BDF8)
                    val channelBg = if (isBetaApp) Color(0xFFB45309).copy(alpha = 0.25f) else Color(0xFF0369A1).copy(alpha = 0.25f)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp, color = PrimaryText)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = channelBg
                        ) {
                            Text(
                                text = if (isBetaApp) "BETA" else "STABLE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = channelColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text("Package: ${context.packageName}", fontSize = 11.sp, color = SecondaryText)
                    Text(
                        text = if (isBetaApp) "Update Channel: Beta (Receives Beta releases only)" else "Update Channel: Stable (Receives Stable releases only)",
                        fontSize = 11.sp,
                        color = channelColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isCheckingUpdate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = channelColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBetaApp) "Checking for Beta updates..." else "Checking for Stable updates...",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.checkForAppUpdate(isManual = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isBetaApp) Color(0xFFB45309) else Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBetaApp) "Check for Beta Updates" else "Check for Stable Updates",
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset All Settings to Defaults", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Modal Photo Resolution Selection Dialog
    if (showPhotoResolutionDialog) {
        ResolutionSelectionDialog(
            title = "Select Photo Resolution",
            resolutions = photoResolutions,
            currentSelection = currentCameraSettings.photoResolution,
            onSelect = { res ->
                viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(photoResolution = res.label))
                viewModel.saveOverlayConfigSilent(currentOverlay.copy(resolutionOption = res.label))
                viewModel.showToast("Photo Resolution: ${res.label}")
                showPhotoResolutionDialog = false
            },
            onDismiss = { showPhotoResolutionDialog = false }
        )
    }

    // Modal Video Resolution Selection Dialog
    if (showVideoResolutionDialog) {
        ResolutionSelectionDialog(
            title = "Select Video Recording Resolution",
            resolutions = videoResolutions,
            currentSelection = currentCameraSettings.videoResolution,
            onSelect = { res ->
                viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(videoResolution = res.label))
                viewModel.showToast("Video Resolution: ${res.label}")
                showVideoResolutionDialog = false
            },
            onDismiss = { showVideoResolutionDialog = false }
        )
    }

    // Restore Defaults Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Default Settings?", fontWeight = FontWeight.Bold) },
            text = { Text("All custom project profile names, camera settings, timestamp formats, and logo preferences will be reset to factory defaults.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettings()
                        showRestoreDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = PrimaryText)
                }
            }
        )
    }
}

/**
 * Clean, scrollable modal selection dialog for Photo or Video resolutions matching reference screenshot.
 */
@Composable
private fun ResolutionSelectionDialog(
    title: String,
    resolutions: List<CameraResolution>,
    currentSelection: String,
    onSelect: (CameraResolution) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                )
                Text(
                    text = "${resolutions.size} supported hardware outputs detected",
                    style = MaterialTheme.typography.labelSmall.copy(color = SecondaryText)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                resolutions.forEach { res ->
                    val isSelected = currentSelection.equals(res.label, ignoreCase = true) ||
                            currentSelection.contains(res.key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(res) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = res.displayTitle,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF38BDF8) else PrimaryText
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(res) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF38BDF8),
                                unselectedColor = SecondaryText
                            )
                        )
                    }
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SmartDropdownSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isExpanded) accentColor.copy(alpha = 0.8f) else BorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(250))
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
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isExpanded) accentColor else SecondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(bottom = 10.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactSwitchRow(
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = PrimaryText, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF38BDF8)
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    onValueChange: (Float) -> Unit,
    valueText: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontWeight = FontWeight.Medium)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = BorderColor
            )
        )
    }
}

@Composable
private fun WatermarkLivePreviewCard(
    overlay: OverlayConfig,
    project: ProjectProfile,
    cameraSettings: CameraSettings,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE WATERMARK STAMP PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
                IconButton(onClick = onToggleOrientation) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate Preview",
                        tint = PrimaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 130.dp else 160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(
                        text = "${project.highwayId.ifBlank { "MH" }} | 168+400 ${project.roadSide.name} ${project.activity}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "28 October 2026 10:57 PM",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "29°50'1\"N 77°53'21\"E • GPS ±4m",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun matchesSearch(text: String, query: String): Boolean {
    if (query.isBlank()) return true
    return text.contains(query.trim(), ignoreCase = true)
}
