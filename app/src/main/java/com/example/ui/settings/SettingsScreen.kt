package com.example.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CameraGridMode
import com.example.model.CameraSettings
import com.example.model.CaptureTimer
import com.example.model.CoordinateFormat
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.PreviewRatio
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.VideoQualityOption
import com.example.overlay.LogoProvider
import com.example.ui.MainViewModel
import com.example.ui.theme.AccentRed
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DarkYellowSurface
import com.example.ui.theme.DeepRed
import com.example.ui.theme.DisabledGray
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SurfaceDark
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentProject by viewModel.projectProfile.collectAsStateWithLifecycle()
    val currentOverlay by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val currentCameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Section 1: Project & Activity state
    var highwayId by remember(currentProject) { mutableStateOf(currentProject.highwayId) }
    var packageSection by remember(currentProject) { mutableStateOf(currentProject.packageSection) }
    var roadSide by remember(currentProject) { mutableStateOf(currentProject.roadSide) }
    var lane by remember(currentProject) { mutableStateOf(currentProject.lane) }
    var activity by remember(currentProject) { mutableStateOf(currentProject.activity) }
    var customDescription by remember(currentProject) { mutableStateOf(currentProject.customDescription) }
    var inspectorName by remember(currentProject) { mutableStateOf(currentProject.inspectorName) }
    var separator by remember(currentProject) { mutableStateOf(currentProject.separator) }

    // Section 2: Timestamp & Text Overlay
    var template by remember(currentOverlay) { mutableStateOf(currentOverlay.template) }
    var coordPrecision by remember(currentOverlay) { mutableIntStateOf(currentOverlay.coordinatePrecision) }
    var timestampFormat by remember(currentOverlay) { mutableStateOf(currentOverlay.timestampFormat) }
    var showTimestamp by remember(currentOverlay) { mutableStateOf(currentOverlay.showTimestamp) }
    var showCoordinates by remember(currentOverlay) { mutableStateOf(currentOverlay.showCoordinates) }
    var showChainage by remember(currentOverlay) { mutableStateOf(currentOverlay.showChainage) }
    var showMapInset by remember(currentOverlay) { mutableStateOf(currentOverlay.showMapInset) }
    var showProjectInfo by remember(currentOverlay) { mutableStateOf(currentOverlay.showProjectInfo) }
    var showCompass by remember(currentOverlay) { mutableStateOf(currentOverlay.showCompass) }
    var showAltitude by remember(currentOverlay) { mutableStateOf(currentOverlay.showAltitude) }
    var showAccuracy by remember(currentOverlay) { mutableStateOf(currentOverlay.showAccuracy) }
    var burnInTimestamp by remember(currentOverlay) { mutableStateOf(currentOverlay.burnInTimestamp) }
    var hasTextShadow by remember(currentOverlay) { mutableStateOf(currentOverlay.hasTextShadow) }
    var fontSizeSp by remember(currentOverlay) { mutableFloatStateOf(currentOverlay.fontSizeSp) }
    var saveOriginal by remember(currentOverlay) { mutableStateOf(currentOverlay.saveOriginalImage) }

    // Custom Logo State
    var showLogo by remember(currentOverlay) { mutableStateOf(currentOverlay.showLogo) }
    var logoPreset by remember(currentOverlay) { mutableStateOf(currentOverlay.logoPreset) }
    var logoPosition by remember(currentOverlay) { mutableStateOf(currentOverlay.logoPosition) }
    var logoSizeDp by remember(currentOverlay) { mutableIntStateOf(currentOverlay.logoSizeDp) }
    var logoOpacity by remember(currentOverlay) { mutableFloatStateOf(currentOverlay.logoOpacity) }
    var logoShape by remember(currentOverlay) { mutableStateOf(currentOverlay.logoShape) }
    var logoCornerRadiusDp by remember(currentOverlay) { mutableIntStateOf(currentOverlay.logoCornerRadiusDp) }
    var logoMarginXDp by remember(currentOverlay) { mutableIntStateOf(currentOverlay.logoMarginXDp) }
    var logoMarginYDp by remember(currentOverlay) { mutableIntStateOf(currentOverlay.logoMarginYDp) }

    // Camera Hardware Settings
    var captureTimer by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.captureTimer) }
    var gridMode by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.gridMode) }
    var showHorizonLevel by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.showHorizonLevel) }
    var previewRatio by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.previewRatio) }
    var captureSound by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.captureSound) }
    var volumeButtonShutter by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.volumeButtonShutter) }
    var frontCameraMirror by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.frontCameraMirror) }
    var jpegQuality by remember(currentCameraSettings) { mutableIntStateOf(currentCameraSettings.jpegQuality) }
    var saveOriginalAndStamped by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.saveOriginalAndStamped) }
    var preserveExif by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.preserveExif) }
    var videoQuality by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.videoQuality) }
    var videoFps by remember(currentCameraSettings) { mutableIntStateOf(currentCameraSettings.videoFps) }
    var recordAudio by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.recordAudio) }
    var gpsIntervalSec by remember(currentCameraSettings) { mutableIntStateOf(currentCameraSettings.gpsUpdateIntervalSec) }
    var gpsAccuracyThreshold by remember(currentCameraSettings) { mutableFloatStateOf(currentCameraSettings.gpsAccuracyThresholdMeters) }
    var routeRadiusMeters by remember(currentCameraSettings) { mutableFloatStateOf(currentCameraSettings.routeMatchingRadiusMeters.toFloat()) }
    var coordFormat by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.coordinateFormat) }
    var is24HourFormat by remember(currentCameraSettings) { mutableStateOf(currentCameraSettings.is24HourFormat) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomLogoImage(uri)
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }

    // Filter helper
    fun matchesSearch(vararg terms: String): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim().lowercase()
        return terms.any { it.lowercase().contains(q) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Camera",
                            tint = PrimaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBackground,
                    titleContentColor = PrimaryText
                ),
                actions = {
                    TextButton(
                        onClick = {
                            // Save Project Profile
                            viewModel.saveProjectProfile(
                                currentProject.copy(
                                    highwayId = highwayId,
                                    packageSection = packageSection,
                                    roadSide = roadSide,
                                    lane = lane,
                                    activity = activity,
                                    customDescription = customDescription,
                                    inspectorName = inspectorName,
                                    separator = separator
                                )
                            )

                            // Save Overlay Config
                            viewModel.saveOverlayConfig(
                                currentOverlay.copy(
                                    template = template,
                                    timestampFormat = timestampFormat,
                                    coordinatePrecision = coordPrecision,
                                    showTimestamp = showTimestamp,
                                    showCoordinates = showCoordinates,
                                    showChainage = showChainage,
                                    showMapInset = showMapInset,
                                    showProjectInfo = showProjectInfo,
                                    showCompass = showCompass,
                                    showAltitude = showAltitude,
                                    showAccuracy = showAccuracy,
                                    burnInTimestamp = burnInTimestamp,
                                    hasTextShadow = hasTextShadow,
                                    fontSizeSp = fontSizeSp,
                                    saveOriginalImage = saveOriginal,
                                    showLogo = showLogo,
                                    logoPreset = logoPreset,
                                    logoPosition = logoPosition,
                                    logoSizeDp = logoSizeDp,
                                    logoOpacity = logoOpacity,
                                    logoShape = logoShape,
                                    logoCornerRadiusDp = logoCornerRadiusDp,
                                    logoMarginXDp = logoMarginXDp,
                                    logoMarginYDp = logoMarginYDp
                                )
                            )

                            // Save Camera Settings
                            viewModel.saveCameraSettings(
                                currentCameraSettings.copy(
                                    captureTimer = captureTimer,
                                    gridMode = gridMode,
                                    showHorizonLevel = showHorizonLevel,
                                    previewRatio = previewRatio,
                                    captureSound = captureSound,
                                    volumeButtonShutter = volumeButtonShutter,
                                    frontCameraMirror = frontCameraMirror,
                                    jpegQuality = jpegQuality,
                                    saveOriginalAndStamped = saveOriginalAndStamped,
                                    preserveExif = preserveExif,
                                    videoQuality = videoQuality,
                                    videoFps = videoFps,
                                    recordAudio = recordAudio,
                                    gpsUpdateIntervalSec = gpsIntervalSec,
                                    gpsAccuracyThresholdMeters = gpsAccuracyThreshold,
                                    routeMatchingRadiusMeters = routeRadiusMeters.toDouble(),
                                    coordinateFormat = coordFormat,
                                    is24HourFormat = is24HourFormat
                                )
                            )
                        },
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text("Save All", color = HighlightYellow, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = PrimaryBackground,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search settings (e.g. video, grid, chainage, logo)...", color = SecondaryText, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = HighlightYellow) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = SecondaryText)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HighlightYellow,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    cursorColor = HighlightYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_search_input")
            )

            // LIVE OVERLAY PREVIEW CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE OVERLAY PREVIEW",
                            color = HighlightYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Matches Reference Photo",
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Simulated Camera HUD box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181C24))
                            .padding(10.dp)
                    ) {
                        // Logo if enabled and top
                        if (showLogo && logoPosition == LogoPosition.TOP_RIGHT) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkYellowSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LOGO", color = HighlightYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Bottom-Left Mini Map representation
                        if (showMapInset) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .size(width = 65.dp, height = 75.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2C3E50))
                                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("MAP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Bottom-Right text
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(start = 75.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            if (showTimestamp) {
                                Text(
                                    text = if (is24HourFormat) "26 October 2026 17:32" else "26 October 2026 5:32 pm",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (showCoordinates) {
                                Text(
                                    text = "29.9183°N 78.1064°E",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (showProjectInfo || showChainage) {
                                val s = separator
                                Text(
                                    text = "$highwayId $s $packageSection $s 196+040 ${roadSide.name} $lane $activity",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 1: CAMERA & LENS
            if (matchesSearch("camera", "lens", "shutter", "mirror", "sound", "volume", "ratio")) {
                SettingsSectionCard(title = "1. Camera & Lens", icon = Icons.Default.CameraAlt) {
                    SettingDropdownRow(
                        label = "Preview Aspect Ratio",
                        current = previewRatio.label,
                        options = PreviewRatio.entries.map { it.label },
                        onSelect = { label -> previewRatio = PreviewRatio.entries.first { it.label == label } }
                    )
                    SettingSwitchRow(
                        label = "Volume Button Shutter",
                        subtitle = "Press volume up/down to take photo or start recording",
                        checked = volumeButtonShutter,
                        onCheckedChange = { volumeButtonShutter = it }
                    )
                    SettingSwitchRow(
                        label = "Front Camera Mirroring",
                        subtitle = "Mirror selfie image to match preview viewfinder",
                        checked = frontCameraMirror,
                        onCheckedChange = { frontCameraMirror = it }
                    )
                    SettingSwitchRow(
                        label = "Shutter Sound",
                        subtitle = "Play audio shutter click feedback upon capture",
                        checked = captureSound,
                        onCheckedChange = { captureSound = it }
                    )
                }
            }

            // SECTION 2: RESOLUTION & IMAGE QUALITY
            if (matchesSearch("resolution", "image", "quality", "jpeg", "raw", "save", "original")) {
                SettingsSectionCard(title = "2. Resolution & Image Quality", icon = Icons.Default.HighQuality) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("JPEG Quality", color = PrimaryText, fontSize = 13.sp)
                            Text("$jpegQuality%", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = jpegQuality.toFloat(),
                            onValueChange = { jpegQuality = it.toInt() },
                            valueRange = 60f..100f,
                            steps = 7,
                            colors = SliderDefaults.colors(thumbColor = HighlightYellow, activeTrackColor = HighlightYellow)
                        )
                    }
                    SettingSwitchRow(
                        label = "Save Both Original & Stamped",
                        subtitle = "Saves clean RAW frame alongside final stamped photo",
                        checked = saveOriginalAndStamped,
                        onCheckedChange = { saveOriginalAndStamped = it }
                    )
                    SettingSwitchRow(
                        label = "Preserve EXIF Metadata",
                        subtitle = "Embed GPS coordinates, altitude, timestamp and camera model into EXIF",
                        checked = preserveExif,
                        onCheckedChange = { preserveExif = it }
                    )
                }
            }

            // SECTION 3: EXPOSURE & FOCUS
            if (matchesSearch("exposure", "focus", "ev", "iso", "white balance")) {
                SettingsSectionCard(title = "3. Exposure & Focus", icon = Icons.Default.Exposure) {
                    Text(
                        text = "Hardware exposure compensation (-4 to +4 EV) can be adjusted on-screen or via quick sliders. Auto-exposure locks upon touch-to-focus.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Focus Mode", color = PrimaryText, fontSize = 13.sp)
                        Text("Continuous Auto Focus (AF)", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Touch-to-Focus", color = PrimaryText, fontSize = 13.sp)
                        Text("Enabled (Tap Viewfinder)", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // SECTION 4: FLASH, ZOOM & GRID
            if (matchesSearch("flash", "zoom", "grid", "horizon", "level", "timer")) {
                SettingsSectionCard(title = "4. Flash, Zoom & Grid", icon = Icons.Default.GridOn) {
                    SettingDropdownRow(
                        label = "Default Capture Timer",
                        current = captureTimer.label,
                        options = CaptureTimer.entries.map { it.label },
                        onSelect = { label -> captureTimer = CaptureTimer.entries.first { it.label == label } }
                    )
                    SettingDropdownRow(
                        label = "Composition Grid",
                        current = gridMode.label,
                        options = CameraGridMode.entries.map { it.label },
                        onSelect = { label -> gridMode = CameraGridMode.entries.first { it.label == label } }
                    )
                    SettingSwitchRow(
                        label = "Electronic Horizon Level",
                        subtitle = "Displays pitch/roll alignment bar to keep road photos straight",
                        checked = showHorizonLevel,
                        onCheckedChange = { showHorizonLevel = it }
                    )
                }
            }

            // SECTION 5: VIDEO RECORDING
            if (matchesSearch("video", "recording", "fps", "audio", "microphone")) {
                SettingsSectionCard(title = "5. Video Recording", icon = Icons.Default.Videocam) {
                    SettingDropdownRow(
                        label = "Video Quality",
                        current = videoQuality.label,
                        options = VideoQualityOption.entries.map { it.label },
                        onSelect = { label -> videoQuality = VideoQualityOption.entries.first { it.label == label } }
                    )
                    SettingDropdownRow(
                        label = "Framerate (FPS)",
                        current = "$videoFps FPS",
                        options = listOf("24 FPS", "30 FPS", "60 FPS"),
                        onSelect = { fpsStr -> videoFps = fpsStr.take(2).toIntOrNull() ?: 30 }
                    )
                    SettingSwitchRow(
                        label = "Record Microphone Audio",
                        subtitle = "Includes ambient audio/voice commentary during video capture",
                        checked = recordAudio,
                        onCheckedChange = { recordAudio = it }
                    )
                }
            }

            // SECTION 6: TIMESTAMP & TEXT
            if (matchesSearch("timestamp", "date", "time", "font", "shadow", "24h", "text", "format", "burn")) {
                SettingsSectionCard(title = "6. Timestamp & Text", icon = Icons.Default.TextFields) {
                    SettingSwitchRow(
                        label = "Show Date & Time",
                        subtitle = "Prints formatted timestamp on photo",
                        checked = showTimestamp,
                        onCheckedChange = { showTimestamp = it }
                    )
                    SettingSwitchRow(
                        label = "Burn-In Timestamp On Media",
                        subtitle = "When disabled, keeps pristine image with metadata-only timestamps in EXIF",
                        checked = burnInTimestamp,
                        onCheckedChange = { burnInTimestamp = it }
                    )
                    SettingDropdownRow(
                        label = "Timestamp Format",
                        current = timestampFormat,
                        options = listOf(
                            "dd-MM-yyyy HH:mm:ss",
                            "dd/MM/yyyy HH:mm:ss",
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "dd MMMM yyyy h:mm a"
                        ),
                        onSelect = { timestampFormat = it }
                    )
                    SettingSwitchRow(
                        label = "24-Hour Time Format",
                        subtitle = "Display 17:32 instead of 5:32 pm",
                        checked = is24HourFormat,
                        onCheckedChange = { is24HourFormat = it }
                    )
                    SettingSwitchRow(
                        label = "High Contrast Text Shadow",
                        subtitle = "Black drop shadow ensures readability against concrete/bright pavement",
                        checked = hasTextShadow,
                        onCheckedChange = { hasTextShadow = it }
                    )
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Font Size", color = PrimaryText, fontSize = 13.sp)
                            Text("${fontSizeSp.toInt()} sp", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = fontSizeSp,
                            onValueChange = { fontSizeSp = it },
                            valueRange = 16f..36f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = HighlightYellow, activeTrackColor = HighlightYellow)
                        )
                    }
                }
            }

            // SECTION 7: GPS & CHAINAGE
            if (matchesSearch("gps", "chainage", "dms", "accuracy", "radius", "precision", "utm", "compass", "altitude")) {
                SettingsSectionCard(title = "7. GPS, Compass & Chainage", icon = Icons.Default.MyLocation) {
                    SettingDropdownRow(
                        label = "Coordinate Format",
                        current = coordFormat.label,
                        options = CoordinateFormat.entries.map { it.label },
                        onSelect = { label -> coordFormat = CoordinateFormat.entries.first { it.label == label } }
                    )
                    SettingSwitchRow(
                        label = "Show Compass Heading",
                        subtitle = "Displays device direction in degrees and cardinal heading (e.g. 184° S)",
                        checked = showCompass,
                        onCheckedChange = { showCompass = it }
                    )
                    SettingSwitchRow(
                        label = "Show Altitude",
                        subtitle = "Displays elevation in meters above sea level",
                        checked = showAltitude,
                        onCheckedChange = { showAltitude = it }
                    )
                    SettingSwitchRow(
                        label = "Show GPS Accuracy",
                        subtitle = "Displays accuracy margin indicator (e.g. ±3m)",
                        checked = showAccuracy,
                        onCheckedChange = { showAccuracy = it }
                    )
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Coordinate Decimal Precision", color = PrimaryText, fontSize = 13.sp)
                            Text("$coordPrecision Decimals", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = coordPrecision.toFloat(),
                            onValueChange = { coordPrecision = it.toInt() },
                            valueRange = 4f..7f,
                            steps = 2,
                            colors = SliderDefaults.colors(thumbColor = HighlightYellow, activeTrackColor = HighlightYellow)
                        )
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Route Matching Search Radius", color = PrimaryText, fontSize = 13.sp)
                            Text("${routeRadiusMeters.toInt()} meters", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = routeRadiusMeters,
                            onValueChange = { routeRadiusMeters = it },
                            valueRange = 30f..250f,
                            steps = 10,
                            colors = SliderDefaults.colors(thumbColor = HighlightYellow, activeTrackColor = HighlightYellow)
                        )
                    }
                }
            }

            // SECTION 8: MINI-MAP
            if (matchesSearch("mini-map", "map", "inset", "satellite")) {
                SettingsSectionCard(title = "8. Mini-map Inset", icon = Icons.Default.Map) {
                    SettingSwitchRow(
                        label = "Show Mini-map Inset",
                        subtitle = "Renders highway alignment route and current GPS position pin",
                        checked = showMapInset,
                        onCheckedChange = { showMapInset = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Map Appearance", color = PrimaryText, fontSize = 13.sp)
                        Text("Google Satellite (Offline Cached)", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // SECTION 9: PROJECT & ACTIVITY
            if (matchesSearch("project", "activity", "highway", "package", "lane", "side", "inspector", "separator")) {
                SettingsSectionCard(title = "9. Project & Activity Parameters", icon = Icons.Default.Engineering) {
                    OutlinedTextField(
                        value = highwayId,
                        onValueChange = { highwayId = it },
                        label = { Text("Highway ID (e.g. MH, NH-58)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightYellow, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = packageSection,
                        onValueChange = { packageSection = it },
                        label = { Text("Package / Section (e.g. MM, Pkg-01)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightYellow, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingDropdownRow(
                        label = "Road Carriageway / Side",
                        current = roadSide.name,
                        options = RoadSide.entries.map { it.name },
                        onSelect = { name -> roadSide = RoadSide.valueOf(name) }
                    )
                    SettingDropdownRow(
                        label = "Lane Specification",
                        current = lane,
                        options = ProjectProfile.LANE_PRESETS,
                        onSelect = { lane = it }
                    )
                    SettingDropdownRow(
                        label = "Pavement Layer / Activity",
                        current = activity,
                        options = ProjectProfile.ACTIVITY_PRESETS,
                        onSelect = { activity = it }
                    )
                    OutlinedTextField(
                        value = inspectorName,
                        onValueChange = { inspectorName = it },
                        label = { Text("Inspector / Engineer Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightYellow, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = separator,
                        onValueChange = { separator = it },
                        label = { Text("Field Separator (e.g. || or |)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightYellow, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // SECTION 10: STORAGE & FILE NAMING
            if (matchesSearch("storage", "file", "naming", "folder", "exif", "memory")) {
                SettingsSectionCard(title = "10. Storage & File Naming", icon = Icons.Default.Folder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Output Directory", color = PrimaryText, fontSize = 13.sp)
                        Text("Pictures/HighwayCamera", color = HighlightYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Naming Pattern: {Highway}--{Package}--{Chainage}-{Side}-{Lane}-{Activity}_{timestamp}.jpg",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }

            // SECTION 11: PRIVACY & PERMISSIONS
            if (matchesSearch("privacy", "permission", "security", "offline")) {
                SettingsSectionCard(title = "11. Privacy & Hardware Permissions", icon = Icons.Default.Security) {
                    Text(
                        text = "This application is 100% offline-capable. No photos, GPS positions, or telemetry leave your device.",
                        color = PrimaryText,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Camera Hardware", color = PrimaryText, fontSize = 13.sp)
                        Text("GRANTED", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Precise GPS Location", color = PrimaryText, fontSize = 13.sp)
                        Text("GRANTED", color = HighlightYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // SECTION 12: ADVANCED SETTINGS & LOGO CUSTOMIZER
            if (matchesSearch("advanced", "logo", "preset", "custom", "update", "whatsnew")) {
                SettingsSectionCard(title = "12. Advanced Settings & Logo Customizer", icon = Icons.Default.Tune) {
                    SettingSwitchRow(
                        label = "Show Watermark Logo",
                        subtitle = "Display company/project logo on photo overlay",
                        checked = showLogo,
                        onCheckedChange = { showLogo = it }
                    )
                    if (showLogo) {
                        SettingDropdownRow(
                            label = "Logo Preset",
                            current = logoPreset.uppercase(),
                            options = listOf("CUBETECH", "CTECS", "NHAI", "SURVEY_SEAL", "CUSTOM"),
                            onSelect = { logoPreset = it.lowercase() }
                        )
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = HighlightYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Custom Logo from Device", color = PrimaryText)
                        }
                    }

                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("App Version", color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Ctecs v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})", color = SecondaryText, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.checkForAppUpdate(isManual = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = HighlightYellow)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Update", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Made with ❤️ madhureddyainala",
                            color = SecondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.openWhatsNew() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = HighlightYellow)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View What's New Release Notes", color = PrimaryText)
                    }
                }
            }

            // SECTION 13: RESET SETTINGS
            if (matchesSearch("reset", "restore", "defaults", "clear")) {
                SettingsSectionCard(title = "13. Reset Settings", icon = Icons.Default.Restore) {
                    Text(
                        text = "Reset all camera hardware parameters, overlay positions and project profiles back to default settings.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRed, contentColor = PrimaryText),
                        modifier = Modifier.fillMaxWidth().testTag("reset_all_settings_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset All Settings to Defaults", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Reset All Settings?", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will restore all 13 settings sections back to initial factory values. Saved route files and photos will NOT be deleted.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettings()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = PrimaryText)
                ) {
                    Text("Confirm Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = PrimaryText)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkYellowSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = HighlightYellow, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    color = PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Divider(color = BorderColor)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(text = subtitle, color = SecondaryText, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryBackground,
                checkedTrackColor = HighlightYellow,
                uncheckedThumbColor = SecondaryText,
                uncheckedTrackColor = ElevatedSurface
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdownRow(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = PrimaryText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ElevatedSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Text(
                    text = current,
                    color = HighlightYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = PrimaryText) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
