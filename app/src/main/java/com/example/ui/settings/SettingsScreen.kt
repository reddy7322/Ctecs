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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
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
import com.example.model.DefinedProject
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.VideoQualityOption
import com.example.geo.OfflineSyncStatus
import com.example.overlay.LogoProvider
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import com.example.ui.map.OfflineMapSyncStatusStrip
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.ctecsBorderlessInputColors
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val currentProject by viewModel.projectProfile.collectAsStateWithLifecycle()
    val definedProjects by viewModel.definedProjects.collectAsStateWithLifecycle()
    val currentOverlay by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val currentLocationName by viewModel.currentLocationName.collectAsStateWithLifecycle()
    val latestWeatherObservation by viewModel.latestWeatherObservation.collectAsStateWithLifecycle()
    val currentCameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val isImportingRoute by viewModel.isImportingRoute.collectAsStateWithLifecycle()
    val routeImportStatus by viewModel.routeImportStatus.collectAsStateWithLifecycle()
    val routeImportError by viewModel.routeImportError.collectAsStateWithLifecycle()
    val manualChainage by viewModel.manualChainage.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val lastValidLocation by viewModel.lastValidLocation.collectAsStateWithLifecycle()
    val liveMatchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val smartDirectionState by viewModel.smartDirectionState.collectAsStateWithLifecycle()
    val smartDirectionPrefs by viewModel.smartDirectionPreferences.collectAsStateWithLifecycle()
    val screensaverSettings by viewModel.homeScreensaverSettings.collectAsStateWithLifecycle()
    val navNotificationSettings by viewModel.navigationNotificationSettings.collectAsStateWithLifecycle()
    val widgetSettings by viewModel.widgetSettings.collectAsStateWithLifecycle()
    val kmStoneAlertSettings by viewModel.kmStoneAlertSettings.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()
    val mapThemePreference by viewModel.mapThemePreference.collectAsStateWithLifecycle()
    val mapTypePreference by viewModel.mapTypePreference.collectAsStateWithLifecycle()
    val offlineSyncState by viewModel.offlineMapSyncState.collectAsStateWithLifecycle()
    val offlineAutoPreload by viewModel.offlineMapAutoPreload.collectAsStateWithLifecycle()
    val offlineWifiOnly by viewModel.offlineMapWifiOnly.collectAsStateWithLifecycle()
    val miniMapViewDistance by viewModel.miniMapViewDistancePreference.collectAsStateWithLifecycle()

    // In-App Update States
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showKmlUrlImportDialog by remember { mutableStateOf(false) }
    var isPreviewLandscape by remember { mutableStateOf(false) }
    var paddingTargetMode by remember { mutableStateOf("photo") }
    var previewMediaMode by remember { mutableStateOf("photo") }
    var showTimestampPreviewPopup by remember { mutableStateOf(false) }

    val c = CtecsTheme.colors
    val isDark = c.isDark
    val primaryAccent = c.accent
    val primaryAccentContent = c.onAccent
    val secondaryAccent = c.accentSecondary
    val secondaryAccentContent = c.onAccent
    val timestampAccent = if (isDark) Color(0xFF7B9CFF) else Color(0xFF3B5BDB)
    val timestampAccentContent = Color.White
    val gpsAccent = c.success
    val gpsAccentContent = Color.White
    val videoAccent = c.error

    // Responsive local input states for zero-lag typing across all sections
    var projectNameInput by remember { mutableStateOf(currentProject.highwayId) }
    var projectStretchInput by remember { mutableStateOf(currentProject.packageSection) }
    var projectStartChainageInput by remember { mutableStateOf(currentProject.startChainage) }
    var projectEndChainageInput by remember { mutableStateOf(currentProject.endChainage) }
    var pdfPreparedByInput by remember { mutableStateOf(currentOverlay.pdfDefaultPreparedBy) }
    var customFilenamePrefixInput by remember { mutableStateOf(currentCameraSettings.customFilenamePrefix) }
    var customStoragePathInput by remember { mutableStateOf(currentCameraSettings.customStoragePath) }

    LaunchedEffect(currentProject.highwayId) {
        if (projectNameInput != currentProject.highwayId) {
            projectNameInput = currentProject.highwayId
        }
    }
    LaunchedEffect(currentProject.packageSection) {
        if (projectStretchInput != currentProject.packageSection) {
            projectStretchInput = currentProject.packageSection
        }
    }
    LaunchedEffect(currentProject.startChainage) {
        if (projectStartChainageInput != currentProject.startChainage) {
            projectStartChainageInput = currentProject.startChainage
        }
    }
    LaunchedEffect(currentProject.endChainage) {
        if (projectEndChainageInput != currentProject.endChainage) {
            projectEndChainageInput = currentProject.endChainage
        }
    }
    LaunchedEffect(currentOverlay.pdfDefaultPreparedBy) {
        if (pdfPreparedByInput != currentOverlay.pdfDefaultPreparedBy) {
            pdfPreparedByInput = currentOverlay.pdfDefaultPreparedBy
        }
    }
    LaunchedEffect(currentCameraSettings.customFilenamePrefix) {
        if (customFilenamePrefixInput != currentCameraSettings.customFilenamePrefix) {
            customFilenamePrefixInput = currentCameraSettings.customFilenamePrefix
        }
    }
    LaunchedEffect(currentCameraSettings.customStoragePath) {
        if (customStoragePathInput != currentCameraSettings.customStoragePath) {
            customStoragePathInput = currentCameraSettings.customStoragePath
        }
    }

    // Dialog states for File Name Format and Signature
    var showFileNameFormatDialog by remember { mutableStateOf(false) }
    var showSignatureDrawDialog by remember { mutableStateOf(false) }

    // Query real camera hardware capabilities
    val cameraHardwareCapabilities = remember(isFrontCamera) {
        com.example.camera.CameraQualityEngine.queryHardwareCapabilities(
            context = context,
            isFrontCamera = isFrontCamera
        )
    }

    val allSectionKeys = remember {
        listOf(
            "project", "camera", "timestamp", "mini_map", "gps", "chainage",
            "offline_maps", "logo", "storage", "display", "screensaver",
            "navigation_notifications", "widgets", "km_stone_alerts",
            "chainage_voice_assistant", "weather_intelligence", "backup_restore", "permissions_about"
        )
    }

    // ALL UNIFIED SECTIONS ARE COLLAPSED INITIALLY
    val expandedSections = remember {
        mutableStateMapOf<String, Boolean>().apply {
            allSectionKeys.forEach { put(it, false) }
        }
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

    val pdfLogoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val targetFile = File(context.filesDir, "pdf_default_logo.png")
                input?.use { inStream ->
                    targetFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                viewModel.saveOverlayConfigSilent(
                    currentOverlay.copy(pdfLogoUri = targetFile.absolutePath)
                )
                viewModel.showToast("PDF default logo saved successfully")
            } catch (e: Exception) {
                viewModel.showToast("Failed to load PDF logo")
            }
        }
    }

    val pdfSignaturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val targetFile = File(context.filesDir, "dpr_signature.png")
                input?.use { inStream ->
                    targetFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                viewModel.saveOverlayConfigSilent(
                    currentOverlay.copy(pdfSignatureUri = targetFile.absolutePath)
                )
                viewModel.showToast("PDF default signature saved successfully")
            } catch (e: Exception) {
                viewModel.showToast("Failed to load signature image")
            }
        }
    }

    // TTF / OTF Custom Font File Picker Launcher
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                var displayName = "custom_font.ttf"
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx) ?: "custom_font.ttf"
                    }
                }
                val safeName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val targetFile = File(context.filesDir, "font_${System.currentTimeMillis()}_$safeName")
                cr.openInputStream(uri)?.use { inStream ->
                    targetFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                val testTf = android.graphics.Typeface.createFromFile(targetFile)
                if (testTf != null) {
                    val fontLabel = displayName.substringBeforeLast(".")
                    viewModel.saveOverlayConfigSilent(
                        currentOverlay.copy(
                            fontFamily = fontLabel,
                            customFontPath = targetFile.absolutePath,
                            customFontName = displayName
                        )
                    )
                    viewModel.showToast("Custom font loaded: $displayName", ToastType.SUCCESS)
                } else {
                    viewModel.showToast("Invalid font file. Please select a valid TTF/OTF file.", ToastType.ERROR)
                }
            } catch (e: Exception) {
                viewModel.showToast("Failed to load font: ${e.message}", ToastType.ERROR)
            }
        }
    }

    // KMZ / KML File Picker Launcher
    val kmzPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}

            var fileName: String? = null
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) {
                                val n = cursor.getString(idx)
                                if (!n.isNullOrBlank()) fileName = n
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            } else if (uri.scheme == "file") {
                uri.path?.let { p ->
                    val f = java.io.File(p)
                    if (f.name.isNotBlank()) fileName = f.name
                }
            }
            val rawFallback = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_alignment.kmz"
            val decodedFallback = try { Uri.decode(rawFallback) } catch (e: Exception) { rawFallback }
            val resolvedName = (fileName ?: decodedFallback).substringAfterLast('/').substringAfterLast(':')
            val displayName = if (resolvedName.isNotBlank()) resolvedName else "imported_alignment.kmz"
            viewModel.importRoute(uri, displayName)
            viewModel.showToast("Importing $displayName...")
        }
    }

    // Export Project JSON (with user added KMZ/KML alignment) Launcher
    val exportProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val routeExportData = viewModel.getActiveRouteExportData()
                    val json = org.json.JSONObject().apply {
                        put("version", 2)
                        put("project", currentProject.highwayId)
                        put("highwayId", currentProject.highwayId)
                        put("stretch", currentProject.packageSection)
                        put("packageSection", currentProject.packageSection)
                        put("startChainage", currentProject.startChainage)
                        put("endChainage", currentProject.endChainage)
                        put("workType", currentProject.workType)
                        put("activity", currentProject.activity)
                        put("roadSide", currentProject.roadSide.label)
                        put("lane", currentProject.lane)
                        put("separator", currentProject.separator)
                        put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

                        // Include user's defined projects
                        val projectsArray = org.json.JSONArray()
                        definedProjects.forEach { dp ->
                            val pObj = org.json.JSONObject()
                            pObj.put("name", dp.name)
                            pObj.put("stretch", dp.stretch)
                            pObj.put("workType", dp.workType)
                            pObj.put("startChainage", dp.startChainage)
                            pObj.put("endChainage", dp.endChainage)
                            projectsArray.put(pObj)
                        }
                        put("definedProjects", projectsArray)

                        // Include active KMZ/KML file if available
                        if (routeExportData != null) {
                            put("hasRouteAlignment", true)
                            put("routeFileName", routeExportData.first)
                            put("routeDisplayName", activeRoute?.name ?: routeExportData.first.substringBeforeLast("."))
                            put("routeMarkerCount", activeRoute?.markers?.size ?: 0)
                            put("routeBase64", android.util.Base64.encodeToString(routeExportData.second, android.util.Base64.NO_WRAP))
                        } else {
                            put("hasRouteAlignment", false)
                        }
                    }
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        outStream.write(json.toString(2).toByteArray(Charsets.UTF_8))
                    }
                    if (routeExportData != null) {
                        viewModel.showSuccess("Project \"${currentProject.highwayId.ifBlank { "Config" }}\" with KMZ/KML exported successfully")
                    } else {
                        viewModel.showSuccess("Project \"${currentProject.highwayId.ifBlank { "Config" }}\" exported successfully")
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Export failed: ${e.message}", ToastType.ERROR)
                }
            }
        }
    }

    // Import Project JSON (with user added KMZ/KML alignment) Launcher
    val importProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}

            var fileName: String? = null
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) {
                                val n = cursor.getString(idx)
                                if (!n.isNullOrBlank()) fileName = n
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            } else if (uri.scheme == "file") {
                uri.path?.let { p ->
                    val f = java.io.File(p)
                    if (f.name.isNotBlank()) fileName = f.name
                }
            }
            val rawFallback = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val decodedFallback = try { Uri.decode(rawFallback) } catch (e: Exception) { rawFallback }
            val resolvedFileName = (fileName ?: decodedFallback).substringAfterLast('/').substringAfterLast(':')

            if (resolvedFileName.endsWith(".kmz", ignoreCase = true) || resolvedFileName.endsWith(".kml", ignoreCase = true)) {
                // Direct KMZ or KML imported via project import
                viewModel.importRoute(uri, resolvedFileName)
                if (currentProject.highwayId.isBlank()) {
                    val pName = resolvedFileName.substringBeforeLast(".")
                    viewModel.saveProjectProfileSilent(currentProject.copy(highwayId = pName))
                    viewModel.addDefinedProject(DefinedProject(name = pName, stretch = currentProject.packageSection, workType = currentProject.workType))
                }
                viewModel.showSuccess("Imported KMZ/KML alignment: $resolvedFileName")
                return@rememberLauncherForActivityResult
            }

            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) {
                    viewModel.showToast("Selected file is empty", ToastType.ERROR)
                    return@rememberLauncherForActivityResult
                }
                val json = org.json.JSONObject(content)
                val projectName = json.optString("project", json.optString("highwayId", "")).trim()
                val stretchName = json.optString("stretch", json.optString("packageSection", "")).trim()
                val startChainage = json.optString("startChainage", currentProject.startChainage).trim()
                val endChainage = json.optString("endChainage", currentProject.endChainage).trim()
                val workType = json.optString("workType", "").trim()
                val activityStr = json.optString("activity", currentProject.activity).trim()
                val roadSideStr = json.optString("roadSide", currentProject.roadSide.label)
                val laneStr = json.optString("lane", currentProject.lane)
                val separatorStr = json.optString("separator", currentProject.separator)

                if (projectName.isBlank() && stretchName.isBlank() && !json.has("routeBase64")) {
                    viewModel.showToast("No valid project information found in JSON", ToastType.WARNING)
                    return@rememberLauncherForActivityResult
                }

                val updated = currentProject.copy(
                    highwayId = projectName.ifBlank { currentProject.highwayId },
                    packageSection = stretchName.ifBlank { currentProject.packageSection },
                    startChainage = startChainage.ifBlank { currentProject.startChainage },
                    endChainage = endChainage.ifBlank { currentProject.endChainage },
                    workType = workType.ifBlank { currentProject.workType },
                    activity = activityStr.ifBlank { currentProject.activity },
                    roadSide = RoadSide.fromString(roadSideStr),
                    lane = laneStr.ifBlank { currentProject.lane },
                    separator = separatorStr.ifBlank { currentProject.separator }
                )
                viewModel.saveProjectProfile(updated)

                if (projectName.isNotBlank()) {
                    viewModel.addDefinedProject(
                        DefinedProject(
                            name = projectName,
                            stretch = stretchName,
                            workType = workType.ifBlank { currentProject.workType },
                            startChainage = startChainage.ifBlank { currentProject.startChainage },
                            endChainage = endChainage.ifBlank { currentProject.endChainage }
                        )
                    )
                }

                // Restore defined projects list if bundled
                if (json.has("definedProjects")) {
                    try {
                        val arr = json.getJSONArray("definedProjects")
                        for (i in 0 until arr.length()) {
                            val pObj = arr.getJSONObject(i)
                            val pName = pObj.optString("name", "").trim()
                            if (pName.isNotBlank()) {
                                viewModel.addDefinedProject(
                                    DefinedProject(
                                        name = pName,
                                        stretch = pObj.optString("stretch", "").trim(),
                                        workType = pObj.optString("workType", "MM").trim(),
                                        startChainage = pObj.optString("startChainage", "0+000").trim(),
                                        endChainage = pObj.optString("endChainage", "50+000").trim()
                                    )
                                )
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                // Restore user added KMZ/KML file if present in export
                val routeBase64 = json.optString("routeBase64", "").trim()
                if (routeBase64.isNotBlank()) {
                    try {
                        val routeBytes = android.util.Base64.decode(routeBase64, android.util.Base64.DEFAULT)
                        val rFileName = json.optString("routeFileName", "${projectName.ifBlank { "alignment" }}.kmz")
                        val rDispName = json.optString("routeDisplayName", projectName.ifBlank { "Route Alignment" })
                        viewModel.importRouteFromBytes(routeBytes, rFileName, rDispName)
                        viewModel.showSuccess("Imported project: $projectName with KMZ/KML alignment")
                    } catch (e: Exception) {
                        viewModel.showToast("Project imported, but failed to load alignment: ${e.message}", ToastType.WARNING)
                    }
                } else {
                    viewModel.showSuccess("Imported project: $projectName")
                }
            } catch (e: Exception) {
                viewModel.showToast("Failed to parse project file: ${e.message}", ToastType.ERROR)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTimestampPreviewPopup = true },
                        modifier = Modifier.testTag("top_timestamp_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrandingWatermark,
                            contentDescription = "Smart Timestamp Live Preview",
                            tint = timestampAccent
                        )
                    }
                    IconButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.testTag("restore_defaults_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore Defaults",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = c.secondaryBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SmartFadeDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preferences saved automatically",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent,
                                contentColor = primaryAccentContent
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_done_button")
                        ) {
                            Text("Done", color = primaryAccentContent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = c.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Instant Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_search_input"),
                placeholder = { Text("Search settings...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = primaryAccent, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ctecsBorderlessInputColors(
                    containerColor = c.surface,
                    accentColor = primaryAccent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Expand / Collapse All Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CATEGORIES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(
                        onClick = {
                            allSectionKeys.forEach { expandedSections[it] = true }
                        },
                        modifier = Modifier.testTag("settings_expand_all_button")
                    ) {
                        Text("Expand All", fontSize = 11.sp, color = primaryAccent, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            allSectionKeys.forEach { expandedSections[it] = false }
                        },
                        modifier = Modifier.testTag("settings_collapse_all_button")
                    ) {
                        Text("Collapse All", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ====================================================
            // 1. PROJECT SETTINGS
            // ====================================================
            if (matchesSearch("project stretch section work type mm rm lie highway export import", searchQuery)) {
                var projectDropdownExpanded by remember { mutableStateOf(false) }
                var workTypeDropdownExpanded by remember { mutableStateOf(false) }
                val workTypeOptions = remember { ProjectProfile.WORK_TYPE_PRESETS }
                val activeWorkType = workTypeOptions.firstOrNull {
                    it.code.equals(currentProject.workType.trim(), ignoreCase = true) ||
                    (currentProject.workType.isBlank() && it.code.equals("None", ignoreCase = true))
                }
                val workTypeDisplayText = activeWorkType?.let {
                    if (it.code.equals("None", ignoreCase = true)) "None (Empty)" else "${it.code} - ${it.title}"
                } ?: if (currentProject.workType.isBlank() || currentProject.workType.equals("None", ignoreCase = true)) "None (Empty)" else currentProject.workType

                SmartDropdownSection(
                    title = "Project Settings",
                    subtitle = "${currentProject.highwayId.ifBlank { "No Project Selected" }} • ${currentProject.packageSection.ifBlank { "No Stretch Set" }} (${currentProject.startChainage} - ${currentProject.endChainage}) • Work: ${if (currentProject.workType.isBlank() || currentProject.workType.equals("None", ignoreCase = true)) "None" else currentProject.workType}",
                    icon = Icons.Default.Engineering,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["project"] ?: false,
                    onToggleExpand = { expandedSections["project"] = !(expandedSections["project"] ?: false) }
                ) {
                    // 1. Project Selection Dropdown
                    ExposedDropdownMenuBox(
                        expanded = projectDropdownExpanded,
                        onExpandedChange = { projectDropdownExpanded = !projectDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = projectNameInput,
                            onValueChange = { newName ->
                                projectNameInput = newName
                                viewModel.saveProjectProfileSilent(currentProject.copy(highwayId = newName))
                            },
                            label = { Text("Project") },
                            placeholder = { Text("Select or enter Project name", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("settings_project_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ctecsBorderlessInputColors(
                                containerColor = c.secondarySurface,
                                accentColor = primaryAccent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = projectDropdownExpanded,
                            onDismissRequest = { projectDropdownExpanded = false },
                            modifier = Modifier.background(c.elevatedSurface)
                        ) {
                            Text(
                                text = "PROJECTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            if (definedProjects.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "No saved projects yet\nEnter project name above to add",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                            } else {
                                definedProjects.forEach { proj ->
                                    val isSelected = proj.name.equals(currentProject.highwayId, ignoreCase = true)
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = proj.name,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.onSurface
                                                )
                                                val details = listOfNotNull(
                                                    proj.stretch.takeIf { it.isNotBlank() },
                                                    if (proj.startChainage.isNotBlank() && proj.endChainage.isNotBlank()) "${proj.startChainage} - ${proj.endChainage}" else null,
                                                    proj.workType.takeIf { it.isNotBlank() }
                                                ).joinToString(" • ")
                                                if (details.isNotBlank()) {
                                                    Text(
                                                        text = details,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = primaryAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.removeDefinedProject(proj.name) },
                                                    modifier = Modifier.size(48.dp).testTag("delete_project_${proj.name}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            projectNameInput = proj.name
                                            projectStretchInput = if (proj.stretch.isNotBlank()) proj.stretch else currentProject.packageSection
                                            projectStartChainageInput = if (proj.startChainage.isNotBlank()) proj.startChainage else currentProject.startChainage
                                            projectEndChainageInput = if (proj.endChainage.isNotBlank()) proj.endChainage else currentProject.endChainage
                                            viewModel.saveProjectProfileSilent(
                                                currentProject.copy(
                                                    highwayId = proj.name,
                                                    packageSection = if (proj.stretch.isNotBlank()) proj.stretch else currentProject.packageSection,
                                                    workType = if (proj.workType.isNotBlank()) proj.workType else currentProject.workType,
                                                    startChainage = if (proj.startChainage.isNotBlank()) proj.startChainage else currentProject.startChainage,
                                                    endChainage = if (proj.endChainage.isNotBlank()) proj.endChainage else currentProject.endChainage
                                                )
                                            )
                                            projectDropdownExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }

                            if (currentProject.highwayId.isNotBlank() && definedProjects.none { it.name.equals(currentProject.highwayId, ignoreCase = true) }) {
                                SmartFadeDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "+ Save \"${currentProject.highwayId}\" to Projects",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primaryAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        viewModel.addDefinedProject(
                                            DefinedProject(
                                                name = currentProject.highwayId,
                                                stretch = currentProject.packageSection,
                                                workType = currentProject.workType,
                                                startChainage = currentProject.startChainage,
                                                endChainage = currentProject.endChainage
                                            )
                                        )
                                        viewModel.showSuccess("Saved \"${currentProject.highwayId}\" to projects")
                                        projectDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 2. Strech Input
                    OutlinedTextField(
                        value = projectStretchInput,
                        onValueChange = { newStretch ->
                            projectStretchInput = newStretch
                            viewModel.saveProjectProfileSilent(currentProject.copy(packageSection = newStretch))
                        },
                        label = { Text("Stretch") },
                        placeholder = { Text("Enter Stretch / Section", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("settings_stretch_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ctecsBorderlessInputColors(
                            containerColor = c.secondarySurface,
                            accentColor = primaryAccent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    // 3. Project Start & End Chainage Manual Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = projectStartChainageInput,
                            onValueChange = { newStart ->
                                projectStartChainageInput = newStart
                                viewModel.saveProjectProfileSilent(currentProject.copy(startChainage = newStart))
                            },
                            label = { Text("Start Chainage") },
                            placeholder = { Text("0+000", color = c.textMuted) },
                            modifier = Modifier.weight(1f).testTag("settings_start_chainage_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ctecsBorderlessInputColors(
                                containerColor = c.secondarySurface,
                                accentColor = primaryAccent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Right) }
                            )
                        )

                        OutlinedTextField(
                            value = projectEndChainageInput,
                            onValueChange = { newEnd ->
                                projectEndChainageInput = newEnd
                                viewModel.saveProjectProfileSilent(currentProject.copy(endChainage = newEnd))
                            },
                            label = { Text("End Chainage") },
                            placeholder = { Text("50+000", color = c.textMuted) },
                            modifier = Modifier.weight(1f).testTag("settings_end_chainage_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ctecsBorderlessInputColors(
                                containerColor = c.secondarySurface,
                                accentColor = primaryAccent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            )
                        )
                    }

                    // 3. Work Type Dropdown (MM - RM - LIE)
                    ExposedDropdownMenuBox(
                        expanded = workTypeDropdownExpanded,
                        onExpandedChange = { workTypeDropdownExpanded = !workTypeDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = workTypeDisplayText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Work Type") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = workTypeDropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("settings_work_type_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ctecsBorderlessInputColors(
                                containerColor = c.secondarySurface,
                                accentColor = primaryAccent
                            ),
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = workTypeDropdownExpanded,
                            onDismissRequest = { workTypeDropdownExpanded = false },
                            modifier = Modifier.background(c.elevatedSurface)
                        ) {
                            workTypeOptions.forEach { wt ->
                                val isSelected = wt.code.equals(currentProject.workType.trim(), ignoreCase = true) ||
                                    (currentProject.workType.isBlank() && wt.code.equals("None", ignoreCase = true))
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) primaryAccentContent else MaterialTheme.colorScheme.onSurfaceVariant
                                            ) {
                                                Text(
                                                    text = wt.code,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = if (wt.code.equals("None", ignoreCase = true)) "None (Empty Work Type)" else wt.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.saveProjectProfileSilent(currentProject.copy(workType = wt.code))
                                        workTypeDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    // 4. KMZ / KML Route Alignment Status & Export/Import
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.secondarySurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinearScale,
                                contentDescription = null,
                                tint = if (activeRoute != null) gpsAccent else c.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (activeRoute != null) {
                                    "Alignment: ${activeRoute?.name} (${activeRoute?.markers?.size ?: 0} KM marks) • Included in Export"
                                } else {
                                    "No KMZ/KML attached • Will export project settings only"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeRoute != null) gpsAccent else c.textSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sanitizedName = (currentProject.highwayId.ifBlank { "Project" })
                                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                exportProjectLauncher.launch("${sanitizedName}_alignment_bundle.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_project_button"),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent.copy(alpha = 0.14f),
                                contentColor = primaryAccent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeRoute != null) "Export (with KMZ)" else "Export Project",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                importProjectLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_project_button"),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = secondaryAccent,
                                contentColor = secondaryAccentContent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Project", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 2. CAMERA, PHOTO & VIDEO SETTINGS (AUTOMATIC, MINIMALIST & BORDERLESS)
            // ====================================================
            if (matchesSearch("camera lens zoom ultra-wide 0.6x shutter sound grid horizon level autofocus touch mirror battery save photo settings original exif video recording stabilization ois eis audio sound mic crop full semi rectangular square auto quality", searchQuery)) {
                SmartDropdownSection(
                    title = "Camera, Photo & Video Settings",
                    subtitle = "Auto Max Quality • Frame: ${currentCameraSettings.cropMode.label} • Grid: ${currentCameraSettings.gridMode.label}",
                    icon = Icons.Default.CameraAlt,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["camera"] ?: false,
                    onToggleExpand = { expandedSections["camera"] = !(expandedSections["camera"] ?: false) }
                ) {
                    // Borderless minimalist automatic quality notice (zero manual output/resolution/quality selectors)
                    Text(
                        text = "Automatic maximum native photo & video quality • Adapts to Portrait & Landscape",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = c.textSecondary,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("settings_auto_output_status")
                    )

                    CompactSwitchRow(
                        title = "Touch to take photo",
                        subtitle = "Tap viewfinder to focus and capture",
                        checked = currentCameraSettings.touchToCapture,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(touchToCapture = it)) },
                        tag = "switch_touch_to_capture"
                    )

                    CompactSwitchRow(
                        title = "Shutter sound",
                        subtitle = "Play camera shutter audio on photo capture",
                        checked = currentCameraSettings.captureSound,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(captureSound = it)) },
                        tag = "switch_shutter_sound"
                    )

                    CompactSwitchRow(
                        title = "Horizon level indicator",
                        subtitle = "Show alignment level line on viewfinder",
                        checked = currentCameraSettings.showHorizonLevel,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(showHorizonLevel = it)) },
                        tag = "switch_horizon_level"
                    )

                    CompactSwitchRow(
                        title = "Easy to toggle cameras",
                        subtitle = "Double-tap viewfinder to switch rear and front lenses",
                        checked = currentCameraSettings.easyToggleCameras,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(easyToggleCameras = it)) },
                        tag = "switch_easy_toggle_cameras"
                    )

                    CompactSwitchRow(
                        title = "Battery save mode",
                        subtitle = "Softly dim viewfinder display to conserve battery",
                        checked = currentCameraSettings.batterySaveMode,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(batterySaveMode = it)) },
                        tag = "switch_battery_save"
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Viewfinder Grid",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CameraGridMode.entries.forEach { gridMode ->
                            FilterChip(
                                selected = currentCameraSettings.gridMode == gridMode,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(gridMode = gridMode)) },
                                label = { Text(gridMode.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("grid_mode_${gridMode.name.lowercase()}"),
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent,
                                    selectedLabelColor = primaryAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Autofocus Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.example.model.CameraAutofocusMode.entries.forEach { afMode ->
                            FilterChip(
                                selected = currentCameraSettings.autofocusMode == afMode,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(autofocusMode = afMode)) },
                                label = { Text(afMode.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("autofocus_mode_${afMode.name.lowercase()}"),
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent,
                                    selectedLabelColor = primaryAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                )
                            )
                        }
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val saveOriginalActive = currentOverlay.saveOriginalImage || currentCameraSettings.saveOriginalAndStamped
                    CompactSwitchRow(
                        title = "Save original photo",
                        subtitle = "Keep un-watermarked copy alongside stamped image",
                        checked = saveOriginalActive,
                        onCheckedChange = { enabled ->
                            viewModel.saveOverlayConfigSilent(currentOverlay.copy(saveOriginalImage = enabled))
                            viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(saveOriginalAndStamped = enabled))
                        },
                        tag = "switch_keep_original"
                    )

                    CompactSwitchRow(
                        title = "Preserve EXIF metadata",
                        subtitle = "Embed GPS, timestamp, and project metadata in JPEG EXIF",
                        checked = currentCameraSettings.preserveExif,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(preserveExif = it)) },
                        tag = "switch_preserve_exif"
                    )

                    CompactSwitchRow(
                        title = "Front camera mirror",
                        subtitle = "Flip captured front-camera photos horizontally",
                        checked = currentCameraSettings.frontCameraMirror,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(frontCameraMirror = it)) },
                        tag = "switch_front_mirror"
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Video Stabilization",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.example.model.VideoStabilizationMode.entries.forEach { stabMode ->
                            FilterChip(
                                selected = currentCameraSettings.videoStabilizationMode == stabMode,
                                enabled = cameraHardwareCapabilities.supportsAnyStabilization || stabMode == com.example.model.VideoStabilizationMode.OFF,
                                onClick = {
                                    viewModel.saveCameraSettingsSilent(
                                        currentCameraSettings.copy(videoStabilizationMode = stabMode)
                                    )
                                },
                                label = { Text(stabMode.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("video_stabilization_${stabMode.name.lowercase()}"),
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = videoAccent,
                                    selectedLabelColor = Color.White,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                )
                            )
                        }
                    }

                    CompactSwitchRow(
                        title = "Record Audio",
                        subtitle = "Include microphone audio track in recorded videos",
                        checked = currentCameraSettings.recordAudio,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(recordAudio = it)) },
                        tag = "switch_record_audio"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 6. TIMESTAMP (MERGED WITH METADATA & WATERMARK)
            // ====================================================
            if (matchesSearch("photo video stamp mini map minimap map inset overlay orientation zoom top view distance smart auto 100m 250m 500m 1km 2km 5km 10km 20km custom distance alignment lhs rhs timestamp date time format precision 24-hour placement pattern metadata watermark font size color opacity shadow background spacing", searchQuery)) {
                var isFormatDropdownOpen by remember { mutableStateOf(false) }
                var isPositionDropdownOpen by remember { mutableStateOf(false) }
                var isFontDropdownOpen by remember { mutableStateOf(false) }
                var customFormatText by remember(currentOverlay.timestampFormat) { mutableStateOf(currentOverlay.timestampFormat) }
                var customHexText by remember(currentOverlay.textColorHex) { mutableStateOf(currentOverlay.textColorHex) }
                var showCustomFormatInput by remember { mutableStateOf(false) }

                SmartDropdownSection(
                    title = "Photo & Video Stamp",
                    subtitle = "Mini Map ${if (currentOverlay.showMapInset) "ON" else "OFF"} • ${currentOverlay.timestampFormat} • ${currentOverlay.watermarkPosition} • ${currentOverlay.fontSizeSp.toInt()}sp",
                    icon = Icons.Default.BrandingWatermark,
                    accentColor = timestampAccent,
                    isExpanded = expandedSections["timestamp"] ?: false,
                    onToggleExpand = { expandedSections["timestamp"] = !(expandedSections["timestamp"] ?: false) }
                ) {
                    // Smart Timestamp Popup Trigger Card (Replaces duplicate inline previews)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = timestampAccent.copy(alpha = if (isDark) 0.14f else 0.10f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable { showTimestampPreviewPopup = true }
                            .testTag("open_timestamp_preview_popup")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    color = timestampAccent.copy(alpha = 0.18f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.BrandingWatermark,
                                            contentDescription = null,
                                            tint = timestampAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Smart Timestamp Live Preview",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${currentOverlay.watermarkPosition} • ${currentOverlay.fontFamily} ${currentOverlay.fontSizeSp.toInt()}sp • Tap for live popup",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = timestampAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Button(
                                onClick = { showTimestampPreviewPopup = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = timestampAccent,
                                    contentColor = timestampAccentContent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Live Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // MINI MAP SETTINGS (PHOTO & VIDEO OVERLAYS)
                    // ----------------------------------------------------
                    MiniMapSettingsSection(
                        savedOverlay = currentOverlay,
                        liveLocation = locationState.point ?: lastValidLocation,
                        liveBearing = locationState.bearing,
                        activeRoute = activeRoute,
                        currentChainage = liveMatchResult.displayChainage.takeIf { it != "---" } ?: "",
                        currentSide = liveMatchResult.detectedSide,
                        accentColor = timestampAccent,
                        accentContentColor = timestampAccentContent,
                        isExpanded = expandedSections["mini_map"] ?: false,
                        onToggleExpand = { expandedSections["mini_map"] = !(expandedSections["mini_map"] ?: false) },
                        onSaveConfig = { updated -> viewModel.saveOverlayConfig(updated) },
                        onResetMiniMapDefaults = { viewModel.resetMiniMapDefaults() },
                        onShowMessage = { msg -> viewModel.showToast(msg, ToastType.INFO) },
                        viewDistancePreference = miniMapViewDistance,
                        onUpdateViewDistance = { pref -> viewModel.updateMiniMapViewDistance(pref) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SmartFadeDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 1: DISPLAY & OVERLAY TOGGLES
                    // ----------------------------------------------------
                    Text(
                        text = "DISPLAY & OVERLAY TOGGLES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )

                    CompactSwitchRow(
                        title = "Enable Timestamp Burn-In",
                        subtitle = "Render timestamp onto captured photos & videos",
                        checked = currentOverlay.burnInTimestamp,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(burnInTimestamp = it)) },
                        tag = "switch_burn_in_timestamp"
                    )

                    CompactSwitchRow(
                        title = "Current Location Name",
                        subtitle = "Auto-resolve locality via GPS",
                        checked = currentOverlay.showLocationName,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showLocationName = it)) },
                        tag = "switch_show_location_name"
                    )

                    if (currentOverlay.showLocationName) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.secondarySurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "DETECTED LOCATION",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = timestampAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val displayLoc = currentLocationName.ifBlank {
                                        currentOverlay.lastDetectedLocationName.ifBlank { "Detecting nearby area via GPS..." }
                                    }
                                    Text(
                                        text = displayLoc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (displayLoc.startsWith("Detecting")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.refreshLocationName()
                                        viewModel.showToast("Refreshing location name...", ToastType.INFO)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Location Name",
                                        tint = timestampAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    CompactSwitchRow(
                        title = "24-Hour Time Format",
                        checked = currentCameraSettings.is24HourFormat,
                        onCheckedChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(is24HourFormat = it)) },
                        tag = "switch_24_hour"
                    )

                    CompactSwitchRow(
                        title = "Show Project Info",
                        checked = currentOverlay.showProjectInfo,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showProjectInfo = it)) },
                        tag = "switch_show_project"
                    )

                    CompactSwitchRow(
                        title = "Show Compass Heading",
                        checked = currentOverlay.showCompass,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCompass = it)) },
                        tag = "switch_show_compass"
                    )

                    CompactSwitchRow(
                        title = "Show Altitude",
                        checked = currentOverlay.showAltitude,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showAltitude = it)) },
                        tag = "switch_show_altitude"
                    )

                    CompactSwitchRow(
                        title = "Show GPS Accuracy",
                        checked = currentOverlay.showAccuracy,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showAccuracy = it)) },
                        tag = "switch_show_accuracy"
                    )

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // WEATHER INFORMATION (DYNAMIC TIMESTAMP METADATA)
                    // ----------------------------------------------------
                    Text(
                        text = "WEATHER INFORMATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )

                    CompactSwitchRow(
                        title = "Show Weather Information",
                        subtitle = "Include dynamic location & capture-time weather in 4-line timestamp (Default OFF)",
                        checked = currentOverlay.showWeatherInfo,
                        onCheckedChange = {
                            viewModel.saveOverlayConfigSilent(currentOverlay.copy(showWeatherInfo = it))
                            if (it) viewModel.refreshWeather(force = false)
                        },
                        tag = "switch_weather_info"
                    )

                    if (currentOverlay.showWeatherInfo) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            CompactSwitchRow(
                                title = "Show Temperature",
                                subtitle = "Actual temperature (°C) at capture time",
                                checked = currentOverlay.weatherShowTemperature,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowTemperature = it))
                                },
                                tag = "switch_weather_temp"
                            )
                            CompactSwitchRow(
                                title = "Show Weather Condition",
                                subtitle = "Condition label (e.g. Sunny, Partly Cloudy, Rainy)",
                                checked = currentOverlay.weatherShowCondition,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowCondition = it))
                                },
                                tag = "switch_weather_condition"
                            )
                            CompactSwitchRow(
                                title = "Show Location Name",
                                subtitle = "Auto-resolved locality paired with weather",
                                checked = currentOverlay.weatherShowLocationName,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowLocationName = it))
                                },
                                tag = "switch_weather_location"
                            )
                            CompactSwitchRow(
                                title = "Show GPS Coordinates",
                                subtitle = "Latitude & longitude with N/S & E/W notation",
                                checked = currentOverlay.weatherShowCoordinates,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowCoordinates = it))
                                },
                                tag = "switch_weather_coords"
                            )
                            CompactSwitchRow(
                                title = "Show Humidity",
                                subtitle = "Relative humidity (%) at capture time",
                                checked = currentOverlay.weatherShowHumidity,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowHumidity = it))
                                },
                                tag = "switch_weather_humidity"
                            )
                            CompactSwitchRow(
                                title = "Show Wind Speed",
                                subtitle = "Wind speed (km/h) at capture time",
                                checked = currentOverlay.weatherShowWindSpeed,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowWindSpeed = it))
                                },
                                tag = "switch_weather_wind_speed"
                            )
                            CompactSwitchRow(
                                title = "Show Wind Direction",
                                subtitle = "Compass wind direction (N, NE, NW, etc.)",
                                checked = currentOverlay.weatherShowWindDirection,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherShowWindDirection = it))
                                },
                                tag = "switch_weather_wind_dir"
                            )
                            CompactSwitchRow(
                                title = "Omit Stale Weather (>45 min)",
                                subtitle = "Hide weather automatically when observation is too old",
                                checked = currentOverlay.weatherOmitIfStale,
                                onCheckedChange = {
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(weatherOmitIfStale = it))
                                },
                                tag = "switch_weather_omit_stale"
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Weather Display Format",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.example.model.WeatherDisplayFormat.entries.forEach { fmt ->
                                    val selected = currentOverlay.weatherDisplayFormat == fmt
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            viewModel.saveOverlayConfigSilent(
                                                currentOverlay.copy(weatherDisplayFormat = fmt)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = fmt.displayName,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = timestampAccent,
                                            selectedLabelColor = timestampAccentContent,
                                            containerColor = c.secondarySurface,
                                            labelColor = c.textSecondary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chip_weather_format_${fmt.name.lowercase(Locale.US)}")
                                    )
                                }
                            }

                            // Compact Live Timestamp Preview Card showing exact dynamic lines
                            val previewLines = remember(
                                currentOverlay,
                                currentProject,
                                locationState.point,
                                liveMatchResult.displayChainage,
                                currentCameraSettings.coordinateFormat,
                                currentCameraSettings.is24HourFormat,
                                currentLocationName,
                                latestWeatherObservation
                            ) {
                                com.example.overlay.SmartTimestampComposer.composeLines(
                                    config = currentOverlay,
                                    project = currentProject,
                                    location = locationState.point ?: lastValidLocation,
                                    chainageStr = liveMatchResult.displayChainage.takeIf { it != "---" } ?: "",
                                    coordinateFormat = currentCameraSettings.coordinateFormat,
                                    is24HourFormat = currentCameraSettings.is24HourFormat,
                                    timestampMs = System.currentTimeMillis(),
                                    locationName = currentLocationName.ifBlank { currentOverlay.lastDetectedLocationName },
                                    weatherObservation = latestWeatherObservation
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = c.secondarySurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("weather_timestamp_live_preview")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (latestWeatherObservation != null) {
                                            "LIVE TIMESTAMP PREVIEW (${latestWeatherObservation!!.source})"
                                        } else {
                                            "LIVE TIMESTAMP PREVIEW (WEATHER WAITING FOR GPS/NETWORK)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = timestampAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    previewLines.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 2: DROPDOWN - TIMESTAMP FORMAT PRESETS
                    // ----------------------------------------------------
                    Text(
                        text = "TIMESTAMP FORMAT PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val formatPresets = listOf(
                        "dd-MM-yyyy HH:mm:ss" to "DD-MM-YYYY HH:mm:ss (Standard 24H)",
                        "dd/MM/yyyy HH:mm:ss" to "DD/MM/YYYY HH:mm:ss (Slash 24H)",
                        "yyyy-MM-dd HH:mm:ss" to "YYYY-MM-DD HH:mm:ss (ISO-8601)",
                        "dd MMM yyyy, HH:mm:ss" to "DD MMM YYYY, HH:mm:ss (Short Month)",
                        "HH:mm:ss" to "HH:mm:ss (24-Hour Time Only)",
                        "hh:mm:ss a" to "hh:mm:ss a (12-Hour Time Only)",
                        "dd MMMM yyyy h:mm a" to "DD MMMM YYYY h:mm a (Full Month 12H)",
                        "MM/dd/yyyy h:mm:ss a" to "MM/DD/YYYY h:mm:ss a (US Format)",
                        "dd-MMM-yyyy HH:mm" to "DD-MMM-YYYY HH:mm (Compact)",
                        "EEE, dd MMM yyyy HH:mm" to "EEE, DD MMM YYYY HH:mm (With Day)"
                    )

                    val currentSampleDate = remember(currentOverlay.timestampFormat) {
                        try {
                            SimpleDateFormat(currentOverlay.timestampFormat, Locale.US).format(Date())
                        } catch (e: Exception) {
                            currentOverlay.timestampFormat
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = c.secondarySurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isFormatDropdownOpen = true }
                                .padding(vertical = 2.dp)
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
                                        text = currentSampleDate,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = timestampAccent
                                    )
                                    Text(
                                        text = "Pattern: ${currentOverlay.timestampFormat}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isFormatDropdownOpen) Icons.Default.ExpandLess else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Format",
                                    tint = timestampAccent
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isFormatDropdownOpen,
                            onDismissRequest = { isFormatDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 380.dp)
                                .background(c.elevatedSurface)
                        ) {
                            formatPresets.forEach { (pattern, label) ->
                                val sample = try {
                                    SimpleDateFormat(pattern, Locale.US).format(Date())
                                } catch (e: Exception) {
                                    pattern
                                }
                                val isSelected = currentOverlay.timestampFormat == pattern
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sample,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) timestampAccent else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = timestampAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.saveOverlayConfigSilent(currentOverlay.copy(timestampFormat = pattern))
                                        showCustomFormatInput = false
                                        isFormatDropdownOpen = false
                                    }
                                )
                            }
                            SmartFadeDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✍ Custom Format Pattern...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryAccent
                                        )
                                    }
                                },
                                onClick = {
                                    showCustomFormatInput = true
                                    isFormatDropdownOpen = false
                                }
                            )
                        }
                    }

                    if (showCustomFormatInput || !formatPresets.any { it.first == currentOverlay.timestampFormat }) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customFormatText,
                                onValueChange = { customFormatText = it },
                                label = { Text("Custom Pattern (e.g. yyyy/MM/dd HH:mm)") },
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                colors = ctecsBorderlessInputColors(
                                    containerColor = c.secondarySurface,
                                    accentColor = timestampAccent
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customFormatText.isNotBlank()) {
                                        try {
                                            SimpleDateFormat(customFormatText, Locale.US).format(Date())
                                            viewModel.saveOverlayConfigSilent(currentOverlay.copy(timestampFormat = customFormatText.trim()))
                                            viewModel.showToast("Custom timestamp format applied", ToastType.SUCCESS)
                                        } catch (e: Exception) {
                                            viewModel.showToast("Invalid date/time format pattern", ToastType.ERROR)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = timestampAccent,
                                    contentColor = timestampAccentContent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Apply", color = timestampAccentContent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 3: DROPDOWN - WATERMARK PLACEMENT POSITION
                    // ----------------------------------------------------
                    Text(
                        text = "WATERMARK PLACEMENT POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val placementPositions = listOf(
                        "Top Left",
                        "Top Center",
                        "Top Right",
                        "Center Left",
                        "Center",
                        "Center Right",
                        "Bottom Left",
                        "Bottom Center",
                        "Bottom Right"
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = c.secondarySurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPositionDropdownOpen = true }
                                .padding(vertical = 2.dp)
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
                                        text = currentOverlay.watermarkPosition,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = timestampAccent
                                    )
                                }
                                Icon(
                                    imageVector = if (isPositionDropdownOpen) Icons.Default.ExpandLess else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Placement",
                                    tint = timestampAccent
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isPositionDropdownOpen,
                            onDismissRequest = { isPositionDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 340.dp)
                                .background(c.elevatedSurface)
                        ) {
                            placementPositions.forEach { pos ->
                                val isSelected = currentOverlay.watermarkPosition.equals(pos, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = pos,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) timestampAccent else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = timestampAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.saveOverlayConfigSilent(currentOverlay.copy(watermarkPosition = pos))
                                        isPositionDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 4: DROPDOWN - FONT SELECTION & TYPOGRAPHY
                    // ----------------------------------------------------
                    Text(
                        text = "FONT SELECTION & TYPOGRAPHY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val systemFonts = listOf(
                        "Default" to ("System Default" to FontFamily.Default),
                        "Sans-Serif" to ("Roboto / Sans-Serif" to FontFamily.SansSerif),
                        "Serif" to ("Serif Typography" to FontFamily.Serif),
                        "Monospace" to ("Monospace / Terminal" to FontFamily.Monospace),
                        "Cursive" to ("Dancing Script / Cursive" to FontFamily.Cursive)
                    )

                    val activeFontDisplay = when {
                        !currentOverlay.customFontName.isNullOrBlank() -> "Custom: ${currentOverlay.customFontName}"
                        else -> systemFonts.find { it.first.equals(currentOverlay.fontFamily, ignoreCase = true) }?.second?.first ?: currentOverlay.fontFamily
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = c.secondarySurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isFontDropdownOpen = true }
                                .padding(vertical = 2.dp)
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
                                        text = activeFontDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = timestampAccent
                                    )
                                    Text(
                                        text = "Sample: 03 Nov 2026 • 29.8339°N 77.8893°E",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isFontDropdownOpen) Icons.Default.ExpandLess else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Font",
                                    tint = timestampAccent
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isFontDropdownOpen,
                            onDismissRequest = { isFontDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 360.dp)
                                .background(c.elevatedSurface)
                        ) {
                            systemFonts.forEach { (key, details) ->
                                val (label, family) = details
                                val isSelected = currentOverlay.customFontPath.isNullOrBlank() && currentOverlay.fontFamily.equals(key, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontFamily = family,
                                                    color = if (isSelected) timestampAccent else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "189+320 LHS • 23:45:10",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = family,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = timestampAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.saveOverlayConfigSilent(
                                            currentOverlay.copy(
                                                fontFamily = key,
                                                customFontPath = null,
                                                customFontName = null
                                            )
                                        )
                                        isFontDropdownOpen = false
                                    }
                                )
                            }

                            if (!currentOverlay.customFontPath.isNullOrBlank()) {
                                SmartFadeDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Custom: ${currentOverlay.customFontName ?: "Loaded Font"}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = gpsAccent
                                                )
                                                Text(
                                                    text = "Imported TTF/OTF File Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = gpsAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = { isFontDropdownOpen = false }
                                )
                            }

                            SmartFadeDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.UploadFile,
                                            contentDescription = null,
                                            tint = primaryAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Import Custom Font (TTF/OTF)...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryAccent
                                        )
                                    }
                                },
                                onClick = {
                                    isFontDropdownOpen = false
                                    fontPickerLauncher.launch(
                                        arrayOf(
                                            "font/*",
                                            "font/ttf",
                                            "font/otf",
                                            "application/x-font-ttf",
                                            "application/x-font-opentype",
                                            "application/font-sfnt",
                                            "*/*"
                                        )
                                    )
                                }
                            )

                            if (!currentOverlay.customFontPath.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = videoAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Revert to Default Font",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = videoAccent
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.saveOverlayConfigSilent(
                                            currentOverlay.copy(
                                                fontFamily = "Default",
                                                customFontPath = null,
                                                customFontName = null
                                            )
                                        )
                                        isFontDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 5: FONT & TEXT CUSTOMIZATION
                    // ----------------------------------------------------
                    Text(
                        text = "FONT & TEXT CUSTOMIZATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    SliderRow(
                        title = "Font Size (SP)",
                        value = currentOverlay.fontSizeSp,
                        valueRange = 14f..42f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(fontSizeSp = it)) },
                        valueText = "${currentOverlay.fontSizeSp.toInt()} sp"
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Font Weight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    val fontWeights = listOf("Regular", "Medium", "Bold", "Italic")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fontWeights.forEach { weight ->
                            val isSelected = currentOverlay.fontStyle.equals(weight, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(fontStyle = weight)) },
                                label = { Text(weight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = timestampAccent,
                                    selectedLabelColor = timestampAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Text Alignment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    val alignments = listOf("Left", "Center", "Right")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        alignments.forEach { align ->
                            val isSelected = currentOverlay.textAlignment.equals(align, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(textAlignment = align)) },
                                label = { Text(align, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = timestampAccent,
                                    selectedLabelColor = timestampAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    SliderRow(
                        title = "Line Spacing Factor",
                        value = currentOverlay.lineSpacingFactor,
                        valueRange = 0.15f..0.60f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(lineSpacingFactor = it)) },
                        valueText = String.format(Locale.US, "%.2f", currentOverlay.lineSpacingFactor)
                    )

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 6: COLOR, STROKE & SHADOW
                    // ----------------------------------------------------
                    Text(
                        text = "TEXT COLOR, OUTLINE & SHADOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val colorPresets = listOf(
                        "#FFFFFF" to "White",
                        "#38BDF8" to "Sky Blue",
                        "#6366F1" to "Indigo",
                        "#22C55E" to "Emerald",
                        "#14B8A6" to "Teal",
                        "#F97316" to "Orange",
                        "#EF4444" to "Crimson",
                        "#EC4899" to "Rose"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        colorPresets.forEach { (hex, _) ->
                            val isSelected = currentOverlay.textColorHex.equals(hex, ignoreCase = true)
                            val parsedColor = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.saveOverlayConfigSilent(currentOverlay.copy(textColorHex = hex))
                                        customHexText = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) timestampAccent.copy(alpha = 0.28f) else c.secondarySurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (hex.equals("#FFFFFF", ignoreCase = true)) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val parsedCustomColor = try {
                            Color(android.graphics.Color.parseColor(customHexText))
                        } catch (e: Exception) {
                            Color.White
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.secondarySurface)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(parsedCustomColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = customHexText,
                            onValueChange = { customHexText = it },
                            label = { Text("Custom Hex Color (e.g. #FFFFFF)") },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = ctecsBorderlessInputColors(
                                containerColor = c.secondarySurface,
                                accentColor = timestampAccent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    android.graphics.Color.parseColor(customHexText.trim())
                                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(textColorHex = customHexText.trim()))
                                    viewModel.showToast("Custom text color applied", ToastType.SUCCESS)
                                } catch (e: Exception) {
                                    viewModel.showToast("Invalid Hex color (must be #RRGGBB)", ToastType.ERROR)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = timestampAccent,
                                contentColor = timestampAccentContent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply", color = timestampAccentContent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    SliderRow(
                        title = "Text Opacity",
                        value = currentOverlay.textOpacity * 100f,
                        valueRange = 10f..100f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(textOpacity = it / 100f)) },
                        valueText = "${(currentOverlay.textOpacity * 100).toInt()}%"
                    )

                    SliderRow(
                        title = "Text Stroke / Outline Width",
                        value = currentOverlay.textStrokeWidthDp,
                        valueRange = 0.0f..4.0f,
                        onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(textStrokeWidthDp = it)) },
                        valueText = String.format(Locale.US, "%.1f dp", currentOverlay.textStrokeWidthDp)
                    )

                    CompactSwitchRow(
                        title = "Enable Text Shadow",
                        subtitle = "Render dark contrast outline behind text for high daylight readability",
                        checked = currentOverlay.hasTextShadow,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(hasTextShadow = it)) },
                        tag = "switch_has_text_shadow"
                    )

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 7: BACKGROUND PANEL STYLE
                    // ----------------------------------------------------
                    Text(
                        text = "TEXT BACKGROUND PANEL STYLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val bgStyles = listOf("Transparent", "Translucent", "Solid")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bgStyles.forEach { style ->
                            val isSelected = currentOverlay.backgroundStyle.equals(style, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val newOpacity = when (style) {
                                        "Transparent" -> 0.0f
                                        "Translucent" -> 0.55f
                                        "Solid" -> 0.90f
                                        else -> currentOverlay.backgroundOpacity
                                    }
                                    viewModel.saveOverlayConfigSilent(
                                        currentOverlay.copy(
                                            backgroundStyle = style,
                                            backgroundOpacity = newOpacity
                                        )
                                    )
                                },
                                label = { Text(style, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = timestampAccent,
                                    selectedLabelColor = timestampAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (currentOverlay.backgroundStyle != "Transparent") {
                        SliderRow(
                            title = "Background Panel Opacity",
                            value = currentOverlay.backgroundOpacity * 100f,
                            valueRange = 10f..100f,
                            onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(backgroundOpacity = it / 100f)) },
                            valueText = "${(currentOverlay.backgroundOpacity * 100).toInt()}%"
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Background Panel Color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        val bgColors = listOf(
                            "#000000" to "Black",
                            "#1E293B" to "Slate",
                            "#0F172A" to "Navy",
                            "#18181B" to "Charcoal"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bgColors.forEach { (hex, name) ->
                                val isSelected = currentOverlay.backgroundColorHex.equals(hex, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(backgroundColorHex = hex)) },
                                    label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = timestampAccent,
                                        selectedLabelColor = timestampAccentContent,
                                        containerColor = c.secondarySurface,
                                        labelColor = c.textSecondary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 8: MANUAL OVERLAY PADDING CONTROLS
                    // ----------------------------------------------------
                    Text(
                        text = "MANUAL OVERLAY PADDING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = timestampAccent,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    CompactSwitchRow(
                        title = "Same Padding for Photo & Video",
                        checked = currentOverlay.useSamePaddingForPhotoAndVideo,
                        onCheckedChange = { isSame ->
                            viewModel.saveOverlayConfigSilent(
                                currentOverlay.copy(
                                    useSamePaddingForPhotoAndVideo = isSame,
                                    videoPaddingTopDp = if (isSame) currentOverlay.photoPaddingTopDp else currentOverlay.videoPaddingTopDp,
                                    videoPaddingBottomDp = if (isSame) currentOverlay.photoPaddingBottomDp else currentOverlay.videoPaddingBottomDp,
                                    videoPaddingLeftDp = if (isSame) currentOverlay.photoPaddingLeftDp else currentOverlay.videoPaddingLeftDp,
                                    videoPaddingRightDp = if (isSame) currentOverlay.photoPaddingRightDp else currentOverlay.videoPaddingRightDp
                                )
                            )
                        },
                        tag = "switch_same_padding_photo_video"
                    )

                    if (!currentOverlay.useSamePaddingForPhotoAndVideo) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = paddingTargetMode == "photo",
                                onClick = { paddingTargetMode = "photo" },
                                label = { Text("Photo Padding", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = timestampAccent,
                                    selectedLabelColor = timestampAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paddingTargetMode == "video",
                                onClick = { paddingTargetMode = "video" },
                                label = { Text("Video Padding", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = timestampAccent,
                                    selectedLabelColor = timestampAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val isTargetVideo = !currentOverlay.useSamePaddingForPhotoAndVideo && paddingTargetMode == "video"
                    val currentTopPad = if (isTargetVideo) currentOverlay.videoPaddingTopDp else currentOverlay.photoPaddingTopDp
                    val currentBottomPad = if (isTargetVideo) currentOverlay.videoPaddingBottomDp else currentOverlay.photoPaddingBottomDp
                    val currentLeftPad = if (isTargetVideo) currentOverlay.videoPaddingLeftDp else currentOverlay.photoPaddingLeftDp
                    val currentRightPad = if (isTargetVideo) currentOverlay.videoPaddingRightDp else currentOverlay.photoPaddingRightDp

                    PaddingSliderWithDirectInput(
                        title = "Top Padding (0 - 120 dp)",
                        valueDp = currentTopPad,
                        onValueChange = { newVal ->
                            val v = newVal.coerceIn(0, 120)
                            if (currentOverlay.useSamePaddingForPhotoAndVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingTopDp = v, videoPaddingTopDp = v))
                            } else if (isTargetVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(videoPaddingTopDp = v))
                            } else {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingTopDp = v))
                            }
                        }
                    )

                    PaddingSliderWithDirectInput(
                        title = "Bottom Padding (0 - 120 dp)",
                        valueDp = currentBottomPad,
                        onValueChange = { newVal ->
                            val v = newVal.coerceIn(0, 120)
                            if (currentOverlay.useSamePaddingForPhotoAndVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingBottomDp = v, videoPaddingBottomDp = v))
                            } else if (isTargetVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(videoPaddingBottomDp = v))
                            } else {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingBottomDp = v))
                            }
                        }
                    )

                    PaddingSliderWithDirectInput(
                        title = "Left Padding (0 - 120 dp)",
                        valueDp = currentLeftPad,
                        onValueChange = { newVal ->
                            val v = newVal.coerceIn(0, 120)
                            if (currentOverlay.useSamePaddingForPhotoAndVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingLeftDp = v, videoPaddingLeftDp = v))
                            } else if (isTargetVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(videoPaddingLeftDp = v))
                            } else {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingLeftDp = v))
                            }
                        }
                    )

                    PaddingSliderWithDirectInput(
                        title = "Right Padding (0 - 120 dp)",
                        valueDp = currentRightPad,
                        onValueChange = { newVal ->
                            val v = newVal.coerceIn(0, 120)
                            if (currentOverlay.useSamePaddingForPhotoAndVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingRightDp = v, videoPaddingRightDp = v))
                            } else if (isTargetVideo) {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(videoPaddingRightDp = v))
                            } else {
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(photoPaddingRightDp = v))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentOverlay.useSamePaddingForPhotoAndVideo) {
                                    viewModel.saveOverlayConfigSilent(
                                        currentOverlay.copy(
                                            photoPaddingTopDp = 16,
                                            photoPaddingBottomDp = 20,
                                            photoPaddingLeftDp = 16,
                                            photoPaddingRightDp = 16,
                                            videoPaddingTopDp = 16,
                                            videoPaddingBottomDp = 20,
                                            videoPaddingLeftDp = 16,
                                            videoPaddingRightDp = 16
                                        )
                                    )
                                } else if (isTargetVideo) {
                                    viewModel.saveOverlayConfigSilent(
                                        currentOverlay.copy(
                                            videoPaddingTopDp = 16,
                                            videoPaddingBottomDp = 20,
                                            videoPaddingLeftDp = 16,
                                            videoPaddingRightDp = 16
                                        )
                                    )
                                } else {
                                    viewModel.saveOverlayConfigSilent(
                                        currentOverlay.copy(
                                            photoPaddingTopDp = 16,
                                            photoPaddingBottomDp = 20,
                                            photoPaddingLeftDp = 16,
                                            photoPaddingRightDp = 16
                                        )
                                    )
                                }
                                viewModel.showToast("Padding reset to default (Top 16, Bottom 20, Left 16, Right 16 dp)", ToastType.INFO)
                            },
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = timestampAccent.copy(alpha = 0.14f),
                                contentColor = timestampAccent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Padding", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.saveOverlayConfigSilent(
                                    currentOverlay.copy(watermarkPosition = "Bottom Right")
                                )
                                viewModel.showToast("Position reset to Bottom Right", ToastType.INFO)
                            },
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent.copy(alpha = 0.14f),
                                contentColor = primaryAccent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Position", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    title = "GPS and Location",
                    subtitle = "${currentCameraSettings.coordinateFormat.label} • Threshold ±${currentCameraSettings.gpsAccuracyThresholdMeters.toInt()}m",
                    icon = Icons.Default.MyLocation,
                    accentColor = gpsAccent,
                    isExpanded = expandedSections["gps"] ?: false,
                    onToggleExpand = { expandedSections["gps"] = !(expandedSections["gps"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show GPS Coordinates",
                        checked = currentOverlay.showCoordinates,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showCoordinates = it)) },
                        tag = "switch_show_coordinates"
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Coordinate Format", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CoordinateFormat.entries.forEach { format ->
                            FilterChip(
                                selected = currentCameraSettings.coordinateFormat == format,
                                onClick = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(coordinateFormat = format)) },
                                label = { Text(format.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = gpsAccent,
                                    selectedLabelColor = gpsAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
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

            Spacer(modifier = Modifier.height(8.dp))

            // ====================================================
            // 8. CHAINAGE, KMZ AND LHS/RHS
            // ====================================================
            if (matchesSearch("chainage kmz kml alignment radius lhs rhs route manual offset", searchQuery)) {
                SmartDropdownSection(
                    title = "Chainage, KMZ and LHS/RHS",
                    subtitle = "Matching Radius: ${currentCameraSettings.routeMatchingRadiusMeters.toInt()}m",
                    icon = Icons.Default.LinearScale,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["chainage"] ?: false,
                    onToggleExpand = { expandedSections["chainage"] = !(expandedSections["chainage"] ?: false) }
                ) {
                    CompactSwitchRow(
                        title = "Show Chainage Stamp",
                        checked = currentOverlay.showChainage,
                        onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showChainage = it)) },
                        tag = "switch_show_chainage"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Active KMZ Route Alignment", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Currently active: ${activeRoute?.name ?: "None (No KMZ loaded)"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { kmzPickerLauncher.launch(arrayOf("*/*")) },
                            enabled = !isImportingRoute,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = primaryAccentContent),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isImportingRoute) "Importing..." else "Import Custom KMZ/KML",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { showKmlUrlImportDialog = true },
                            enabled = !isImportingRoute,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent.copy(alpha = 0.14f),
                                contentColor = primaryAccent
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("settings_import_kml_url_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Import KML / My Maps URL", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import URL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isImportingRoute) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            color = primaryAccent,
                            modifier = Modifier.fillMaxWidth().height(3.dp)
                        )
                        routeImportStatus?.let { status ->
                            Text(
                                text = status,
                                fontSize = 11.sp,
                                color = primaryAccent,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else if (!routeImportError.isNullOrBlank()) {
                        Text(
                            text = routeImportError ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    SliderRow(
                        title = "Route Alignment Search Radius",
                        value = currentCameraSettings.routeMatchingRadiusMeters.toFloat(),
                        valueRange = 20f..300f,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(routeMatchingRadiusMeters = it.toDouble())) },
                        valueText = "${currentCameraSettings.routeMatchingRadiusMeters.toInt()}m"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SmartFadeDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    // Smart LHS/RHS Auto Direction Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart LHS/RHS Direction",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = smartDirectionState.detailReason,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        com.example.ui.components.SmartDirectionIndicatorBadge(
                            directionState = smartDirectionState,
                            isDark = isDark,
                            isCompact = true,
                            testTagName = "settings_smart_direction_status_badge"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Direction Control Mode",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isAutoMode = smartDirectionPrefs.controlMode == com.example.model.DirectionControlMode.AUTO
                        val isManualLhs = !isAutoMode && smartDirectionPrefs.normalizedManualSide == "LHS"
                        val isManualRhs = !isAutoMode && smartDirectionPrefs.normalizedManualSide == "RHS"

                        listOf(
                            Triple("Auto (Smart)", isAutoMode, "settings_dir_mode_auto") to { viewModel.resetSideToAuto() },
                            Triple("Manual LHS", isManualLhs, "settings_dir_mode_lhs") to { viewModel.updateSide(RoadSide.LHS) },
                            Triple("Manual RHS", isManualRhs, "settings_dir_mode_rhs") to { viewModel.updateSide(RoadSide.RHS) }
                        ).forEach { (meta, action) ->
                            val (label, selected, tag) = meta
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) primaryAccent.copy(alpha = 0.18f) else c.secondarySurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { action() }
                                    .testTag(tag)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Alignment Chainage Convention",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Defines which carriageway corresponds to increasing chainage when the active KMZ does not explicitly specify it.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Auto (KMZ)", com.example.model.AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT, "settings_dir_conv_auto"),
                            Triple("Inc = LHS", com.example.model.AlignmentDirectionConvention.INCREASING_IS_LHS, "settings_dir_conv_inc_lhs"),
                            Triple("Inc = RHS", com.example.model.AlignmentDirectionConvention.INCREASING_IS_RHS, "settings_dir_conv_inc_rhs")
                        ).forEach { (label, convention, tag) ->
                            val selected = smartDirectionPrefs.alignmentConvention == convention
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) primaryAccent.copy(alpha = 0.18f) else c.secondarySurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.updateAlignmentDirectionConvention(convention) }
                                    .testTag(tag)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    CompactSwitchRow(
                        title = "Announce Confirmed Direction Changes",
                        checked = smartDirectionPrefs.voiceDirectionAnnouncementsEnabled,
                        onCheckedChange = { viewModel.setVoiceDirectionAnnouncementsEnabled(it) },
                        tag = "settings_dir_voice_switch"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 8b. OFFLINE MAPS & CORRIDOR CACHING
            // ====================================================
            if (matchesSearch("offline maps preload kmz corridor cache tiles wifi sync mini map", searchQuery)) {
                val offlineSummary = when (offlineSyncState.status) {
                    OfflineSyncStatus.COMPLETE -> "Ready • ${offlineSyncState.totalTiles} tiles (${offlineSyncState.formattedBytes})"
                    OfflineSyncStatus.SYNCING, OfflineSyncStatus.PREPARING -> "Syncing ${offlineSyncState.cachedTiles}/${offlineSyncState.totalTiles} tiles"
                    OfflineSyncStatus.PAUSED -> "Paused • ${offlineSyncState.cachedTiles}/${offlineSyncState.totalTiles}"
                    OfflineSyncStatus.WAITING_FOR_WIFI -> "Waiting for Wi-Fi"
                    OfflineSyncStatus.WAITING_FOR_NETWORK -> "Offline • ${offlineSyncState.cachedTiles}/${offlineSyncState.totalTiles}"
                    OfflineSyncStatus.PARTIAL_ERROR -> "Partial • ${offlineSyncState.cachedTiles}/${offlineSyncState.totalTiles}"
                    OfflineSyncStatus.IDLE -> if (offlineAutoPreload) "Auto-Preload ON" else "Auto-Preload OFF"
                }
                SmartDropdownSection(
                    title = "Offline Maps & Caching",
                    subtitle = offlineSummary,
                    icon = Icons.Default.Map,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["offline_maps"] ?: false,
                    onToggleExpand = {
                        expandedSections["offline_maps"] = !(expandedSections["offline_maps"] ?: false)
                    }
                ) {
                    OfflineMapSyncStatusStrip(
                        syncState = offlineSyncState,
                        onPauseSync = { viewModel.pauseOfflineMapSync() },
                        onResumeSync = { viewModel.resumeOfflineMapSync() },
                        onRetrySync = { viewModel.retryOfflineMapSync() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CompactSwitchRow(
                        title = "Auto-Preload Active KMZ Route",
                        subtitle = "Cache corridor tiles and 250m viewing zoom when route activates",
                        checked = offlineAutoPreload,
                        onCheckedChange = { viewModel.setOfflineMapAutoPreload(it) },
                        tag = "switch_offline_map_auto_preload"
                    )

                    CompactSwitchRow(
                        title = "Download Over Wi-Fi Only",
                        subtitle = "Pause corridor downloads on metered cellular data",
                        checked = offlineWifiOnly,
                        onCheckedChange = { viewModel.setOfflineMapWifiOnly(it) },
                        tag = "switch_offline_map_wifi_only"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.clearInactiveOfflineMapCache() },
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.secondarySurface,
                            contentColor = c.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("button_clear_inactive_offline_cache")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Inactive Project Map Cache",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // (Section 9 Metadata and Watermark is merged into Section 6. Timestamp)


            // ====================================================
            // 10. CUSTOM LOGO
            // ====================================================
            if (matchesSearch("custom logo size opacity position shape rotation stamp", searchQuery)) {
                val hasCustomLogo = !currentOverlay.logoUri.isNullOrBlank() && File(currentOverlay.logoUri ?: "").exists()

                SmartDropdownSection(
                    title = "Custom Logo",
                    subtitle = if (hasCustomLogo) "Custom Logo Active • ${currentOverlay.logoSizeDp}dp" else "No Logo Uploaded",
                    icon = Icons.Default.AddPhotoAlternate,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["logo"] ?: false,
                    onToggleExpand = { expandedSections["logo"] = !(expandedSections["logo"] ?: false) }
                ) {
                    if (!hasCustomLogo) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.secondarySurface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Upload your company or project logo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = primaryAccentContent),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("upload_custom_logo_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload Custom Logo", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // User Uploaded Logo - Show full, simplified custom logo settings
                        val logoFile = File(currentOverlay.logoUri!!)
                        val logoBitmap = remember(currentOverlay.logoUri) {
                            com.example.overlay.LogoProvider.decodeSampledBitmapFromFile(logoFile.absolutePath, 256)
                        }

                        // Preview & Actions Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = c.secondarySurface),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(
                                                when (currentOverlay.logoShape) {
                                                    LogoShape.CIRCLE -> CircleShape
                                                    LogoShape.ROUNDED_CORNER -> RoundedCornerShape(currentOverlay.logoCornerRadiusDp.dp)
                                                    LogoShape.ORIGINAL -> RoundedCornerShape(4.dp)
                                                }
                                            )
                                            .background(Color.Black.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (logoBitmap != null) {
                                            Image(
                                                bitmap = logoBitmap.asImageBitmap(),
                                                contentDescription = "Custom Logo",
                                                modifier = Modifier.size(48.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = null,
                                                tint = primaryAccent
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Custom Logo",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = logoFile.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.testTag("replace_logo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Replace Logo",
                                            tint = primaryAccent
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            try { logoFile.delete() } catch (ignored: Exception) {}
                                            viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoUri = null, showLogo = false))
                                            viewModel.showToast("Custom logo removed")
                                        },
                                        modifier = Modifier.testTag("remove_logo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Logo",
                                            tint = videoAccent
                                        )
                                    }
                                }
                            }
                        }

                        CompactSwitchRow(
                            title = "Show Logo Stamp",
                            checked = currentOverlay.showLogo,
                            onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(showLogo = it)) },
                            tag = "switch_show_logo"
                        )

                        Spacer(modifier = Modifier.height(4.dp))

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

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Logo Position",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                LogoPosition.TOP_RIGHT to "Top Right",
                                LogoPosition.TOP_LEFT to "Top Left",
                                LogoPosition.BOTTOM_RIGHT to "Bottom Right",
                                LogoPosition.BOTTOM_LEFT to "Bottom Left",
                                LogoPosition.ABOVE_TEXT to "Above Text"
                            ).forEach { (pos, label) ->
                                FilterChip(
                                    selected = currentOverlay.logoPosition == pos,
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoPosition = pos)) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = primaryAccent,
                                        selectedLabelColor = primaryAccentContent,
                                        containerColor = c.secondarySurface,
                                        labelColor = c.textSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Logo Shape",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                LogoShape.ORIGINAL to "Original",
                                LogoShape.ROUNDED_CORNER to "Rounded",
                                LogoShape.CIRCLE to "Circular"
                            ).forEach { (shape, label) ->
                                FilterChip(
                                    selected = currentOverlay.logoShape == shape,
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoShape = shape)) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = primaryAccent,
                                        selectedLabelColor = primaryAccentContent,
                                        containerColor = c.secondarySurface,
                                        labelColor = c.textSecondary
                                    )
                                )
                            }
                        }

                        if (currentOverlay.logoShape == LogoShape.ROUNDED_CORNER) {
                            SliderRow(
                                title = "Corner Radius (DP)",
                                value = currentOverlay.logoCornerRadiusDp.toFloat(),
                                valueRange = 2f..32f,
                                onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(logoCornerRadiusDp = it.toInt())) },
                                valueText = "${currentOverlay.logoCornerRadiusDp} dp"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SmartFadeDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "PDF DEFAULT LOGO & SIGNATURE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // --- PDF HEADER LOGO ---
                        Text(
                            text = "Default PDF Header Logo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Logo Thumbnail Preview if present
                        if (!currentOverlay.pdfLogoUri.isNullOrBlank()) {
                            val pdfLogoFile = File(currentOverlay.pdfLogoUri ?: "")
                            if (pdfLogoFile.exists()) {
                                val pdfLogoBmp = remember(currentOverlay.pdfLogoUri) {
                                    com.example.overlay.LogoProvider.decodeSampledBitmapFromFile(pdfLogoFile.absolutePath, 256)
                                }
                                if (pdfLogoBmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(c.secondarySurface, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Image(
                                                bitmap = pdfLogoBmp.asImageBitmap(),
                                                contentDescription = "PDF Header Logo Preview",
                                                modifier = Modifier.height(36.dp)
                                            )
                                            Text(
                                                text = "Active Header Logo (${pdfLogoBmp.width}×${pdfLogoBmp.height}px)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = gpsAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { pdfLogoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.secondarySurface,
                                    contentColor = primaryAccent
                                )
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (currentOverlay.pdfLogoUri.isNullOrBlank()) "Upload Header Logo" else "Change Logo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (!currentOverlay.pdfLogoUri.isNullOrBlank()) {
                                Button(
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfLogoUri = null)) },
                                    shape = RoundedCornerShape(10.dp),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = c.error.copy(alpha = 0.14f),
                                        contentColor = c.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        CompactSwitchRow(
                            title = "Include PDF Logo by Default",
                            checked = currentOverlay.includePdfLogoDefault,
                            onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(includePdfLogoDefault = it)) },
                            tag = "switch_pdf_logo_default"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // --- PDF DEFAULT SIGNATURE ---
                        Text(
                            text = "Default PDF Signature",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Signature Thumbnail Preview if present
                        if (!currentOverlay.pdfSignatureUri.isNullOrBlank()) {
                            val pdfSigFile = File(currentOverlay.pdfSignatureUri ?: "")
                            if (pdfSigFile.exists()) {
                                val pdfSigBmp = remember(currentOverlay.pdfSignatureUri) {
                                    com.example.overlay.LogoProvider.decodeSampledBitmapFromFile(pdfSigFile.absolutePath, 256)
                                }
                                if (pdfSigBmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Image(
                                                bitmap = pdfSigBmp.asImageBitmap(),
                                                contentDescription = "PDF Signature Preview",
                                                modifier = Modifier.height(40.dp)
                                            )
                                            Text(
                                                text = "Active Signature Stamp",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = gpsAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { pdfSignaturePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.secondarySurface,
                                    contentColor = primaryAccent
                                )
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (currentOverlay.pdfSignatureUri.isNullOrBlank()) "Upload" else "Change", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { showSignatureDrawDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.secondarySurface,
                                    contentColor = primaryAccent
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Draw", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (!currentOverlay.pdfSignatureUri.isNullOrBlank()) {
                                Button(
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfSignatureUri = null)) },
                                    shape = RoundedCornerShape(10.dp),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = c.error.copy(alpha = 0.14f),
                                        contentColor = c.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        CompactSwitchRow(
                            title = "Include Signature by Default",
                            checked = currentOverlay.includePdfSignatureDefault,
                            onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(includePdfSignatureDefault = it)) },
                            tag = "switch_pdf_sig_default"
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = pdfPreparedByInput,
                            onValueChange = { newPreparedBy ->
                                pdfPreparedByInput = newPreparedBy
                                viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfDefaultPreparedBy = newPreparedBy))
                            },
                            label = { Text("Default Prepared By (Engineer Name)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = ctecsBorderlessInputColors(
                                containerColor = c.secondarySurface,
                                accentColor = primaryAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 12. STORAGE, FILENAMES AND GALLERY
            // ====================================================
            if (matchesSearch("storage filenames gallery prefix folder path dcim movies file name format save format photos videos", searchQuery)) {
                val currentPresetOption = remember(currentCameraSettings.filenameFormatPreset) {
                    com.example.model.FilenameFormats.findPreset(currentCameraSettings.filenameFormatPreset)
                }
                val dynamicPreview = remember(currentPresetOption, currentProject) {
                    currentPresetOption.formatDynamicPreview(
                        project = currentProject.highwayId,
                        stretch = currentProject.packageSection,
                        chainage = "189+320",
                        side = currentProject.roadSide.label,
                        lane = currentProject.lane,
                        workType = currentProject.workType,
                        activity = currentProject.activity
                    )
                }
                var showInlineFormatDropdown by remember { mutableStateOf(false) }

                SmartDropdownSection(
                    title = "Storage, Filenames and Gallery",
                    subtitle = "Format: $dynamicPreview",
                    icon = Icons.Default.Storage,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["storage"] ?: false,
                    onToggleExpand = { expandedSections["storage"] = !(expandedSections["storage"] ?: false) }
                ) {
                    // Smart Dynamic Option: File Name Save Format (Photos & Videos)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("file_name_format_card"),
                        shape = RoundedCornerShape(12.dp),
                        color = c.secondarySurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFileNameFormatDialog = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "File name format",
                                        tint = primaryAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "File Name Save Format",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$dynamicPreview (.jpg / .mp4)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showInlineFormatDropdown = !showInlineFormatDropdown },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown format selector",
                                        tint = primaryAccent
                                    )
                                }
                            }

                            // Smart Dropdown Menu for Quick Selection
                            DropdownMenu(
                                expanded = showInlineFormatDropdown,
                                onDismissRequest = { showInlineFormatDropdown = false },
                                modifier = Modifier.background(c.elevatedSurface)
                            ) {
                                com.example.model.FilenameFormats.PRESETS.forEach { opt ->
                                    val isSel = opt.id.equals(currentCameraSettings.filenameFormatPreset, ignoreCase = true)
                                    val preview = opt.formatDynamicPreview(
                                        project = currentProject.highwayId,
                                        stretch = currentProject.packageSection,
                                        chainage = "189+320",
                                        side = currentProject.roadSide.label,
                                        lane = currentProject.lane,
                                        workType = currentProject.workType,
                                        activity = currentProject.activity
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = preview,
                                                    color = if (isSel) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                                )
                                                RadioButton(
                                                    selected = isSel,
                                                    onClick = {
                                                        viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(filenameFormatPreset = opt.id))
                                                        showInlineFormatDropdown = false
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = primaryAccent
                                                    )
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(filenameFormatPreset = opt.id))
                                            showInlineFormatDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customFilenamePrefixInput,
                        onValueChange = { newPrefix ->
                            customFilenamePrefixInput = newPrefix
                            viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customFilenamePrefix = newPrefix))
                        },
                        label = { Text("Custom Filename Prefix (Optional e.g. NH58_)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ctecsBorderlessInputColors(
                            containerColor = c.secondarySurface,
                            accentColor = primaryAccent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    OutlinedTextField(
                        value = customStoragePathInput,
                        onValueChange = { newPath ->
                            customStoragePathInput = newPath
                            viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customStoragePath = newPath))
                        },
                        label = { Text("Storage Directory Path") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ctecsBorderlessInputColors(
                            containerColor = c.secondarySurface,
                            accentColor = primaryAccent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13. DISPLAY AND APPEARANCE
            // ====================================================
            if (matchesSearch("display appearance theme mode dark light amoled system", searchQuery)) {
                SmartDropdownSection(
                    title = "Display and Appearance",
                    subtitle = "Theme: ${viewModel.themeMode.collectAsStateWithLifecycle().value.uppercase()}",
                    icon = Icons.Default.Palette,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["display"] ?: false,
                    onToggleExpand = { expandedSections["display"] = !(expandedSections["display"] ?: false) }
                ) {
                    Text("App Theme Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "System", "light" to "Light", "dark" to "AMOLED Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryAccent,
                                    selectedLabelColor = primaryAccentContent,
                                    containerColor = c.secondarySurface,
                                    labelColor = c.textSecondary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13b. SCREENSAVER
            // ====================================================
            if (matchesSearch("screensaver hud mirror portrait landscape speedometer chainage direction mini map amoled always on display aod burn-in dim", searchQuery)) {
                val screensaverSummary = buildString {
                    append(if (screensaverSettings.masterEnabled) "ON" else "OFF")
                    append(" • ")
                    append(screensaverSettings.displayMode.label)
                    if (screensaverSettings.aodEnabled) append(" • AOD")
                    append(" • ")
                    append("${screensaverSettings.inactivityTimeoutSeconds}s")
                }
                SmartDropdownSection(
                    title = "Screensaver",
                    subtitle = screensaverSummary,
                    icon = Icons.Default.Bedtime,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["screensaver"] ?: false,
                    onToggleExpand = { expandedSections["screensaver"] = !(expandedSections["screensaver"] ?: false) }
                ) {
                    ScreensaverSettingsSectionContent(
                        settings = screensaverSettings,
                        matchResult = liveMatchResult,
                        locationState = locationState,
                        activeRoute = activeRoute,
                        projectProfile = currentProject,
                        compassHeading = compassState.azimuthDegrees,
                        currentLocationName = currentLocationName,
                        mapThemePreference = mapThemePreference,
                        mapTypePreference = mapTypePreference,
                        accentColor = primaryAccent,
                        secondaryColor = secondaryAccent,
                        onUpdateSettings = { transform -> viewModel.updateScreensaverSettings(transform) },
                        onSelectDisplayMode = { mode -> viewModel.setScreensaverDisplayMode(mode) },
                        onToggleMirror = { enabled -> viewModel.setScreensaverMirrorEnabled(enabled) },
                        onHudBrightnessChange = { brightness -> viewModel.setScreensaverHudBrightness(brightness) },
                        onMiniMapOrientationChange = { orientation -> viewModel.setHomeMiniMapOrientation(orientation) },
                        onMiniMapZoomIn = { viewModel.zoomInHomeMiniMap() },
                        onMiniMapZoomOut = { viewModel.zoomOutHomeMiniMap() },
                        onMiniMapRecenter = { viewModel.recenterHomeMiniMap() },
                        onMiniMapStyleChange = { style -> viewModel.setHomeMiniMapStyle(style) },
                        onResetDefaults = { viewModel.resetScreensaverSettings() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13c. NAVIGATION NOTIFICATIONS (STATUS BAR / NOW BAR / LOCK SCREEN)
            // ====================================================
            if (matchesSearch("navigation notifications lock screen status bar now bar live update chainage speed location", searchQuery)) {
                val navNotifActive = navNotificationSettings.enabled
                val navNotifSummary = if (navNotifActive) {
                    buildString {
                        append("ON")
                        if (navNotificationSettings.liveNotificationEnabled) append(" • Live Chip")
                        if (navNotificationSettings.showOnLockScreen) append(" • Lock Screen")
                    }
                } else {
                    "OFF • Status bar & lock-screen updates"
                }
                SmartDropdownSection(
                    title = "Navigation Notifications",
                    subtitle = navNotifSummary,
                    icon = Icons.Default.NotificationsActive,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["navigation_notifications"] ?: false,
                    onToggleExpand = {
                        expandedSections["navigation_notifications"] = !(expandedSections["navigation_notifications"] ?: false)
                    }
                ) {
                    NavigationNotificationSettingsSectionContent(
                        notifSettings = navNotificationSettings,
                        matchResult = liveMatchResult,
                        locationState = locationState,
                        activeRoute = activeRoute,
                        projectProfile = currentProject,
                        currentLocationName = currentLocationName,
                        accentColor = primaryAccent,
                        onToggleNotificationEnabled = { enabled ->
                            viewModel.setNavigationNotificationEnabled(enabled)
                        },
                        onUpdateNotifSettings = { transform ->
                            viewModel.updateNavigationNotificationSettings(transform)
                        },
                        onResetDefaults = {
                            viewModel.resetNavigationNotificationSettings()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13d. WIDGETS (NATIVE ANDROID HOME-SCREEN WIDGETS)
            // ====================================================
            if (matchesSearch("widgets home screen compact navigation mini map dashboard flat theme accent chainage speed", searchQuery)) {
                val widgetSummary = buildString {
                    append(widgetSettings.themeMode.label)
                    append(" • ")
                    append(widgetSettings.backgroundStyle.label)
                    append(" • ")
                    append(widgetSettings.accentColor.label)
                }
                SmartDropdownSection(
                    title = "Widgets",
                    subtitle = widgetSummary,
                    icon = Icons.Default.Widgets,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["widgets"] ?: false,
                    onToggleExpand = {
                        expandedSections["widgets"] = !(expandedSections["widgets"] ?: false)
                    }
                ) {
                    WidgetSettingsSectionContent(
                        widgetSettings = widgetSettings,
                        matchResult = liveMatchResult,
                        locationState = locationState,
                        activeRoute = activeRoute,
                        projectProfile = currentProject,
                        currentLocationName = currentLocationName,
                        compassHeading = compassState.azimuthDegrees,
                        isTrackingActive = navNotificationSettings.enabled,
                        accentColor = primaryAccent,
                        onUpdateSettings = { transform -> viewModel.updateWidgetSettings(transform) },
                        onUpdateInstanceConfig = { cfg -> viewModel.updateWidgetInstanceConfig(cfg) },
                        onRemoveInstanceOverride = { id -> viewModel.removeWidgetInstanceOverride(id) },
                        onRefreshWidgetsNow = { viewModel.refreshHomeScreenWidgetsNow() },
                        onResetDefaults = { viewModel.resetWidgetSettings() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13e. NAVIGATION ALERTS -> KM STONE MONITORING
            // ====================================================
            if (matchesSearch("navigation alerts km stone monitoring arrival foreground service background persistent notification lock screen haptic vibration sound beep chime advance warning proximity threshold voice announcement tts speech language speed style custom distance intervals", searchQuery)) {
                val isKmStoneServiceRunning by viewModel.isKmStoneServiceRunning.collectAsStateWithLifecycle()
                val kmStoneServiceStatusNote by viewModel.kmStoneServiceStatusNote.collectAsStateWithLifecycle()
                val kmAlertSummary = buildString {
                    append(if (kmStoneAlertSettings.enabled) "ON" else "OFF")
                    if (isKmStoneServiceRunning) append(" • Service Running")
                    append(" • ±${kmStoneAlertSettings.proximityThresholdMetres}m")
                    if (kmStoneAlertSettings.voiceAnnouncementEnabled) append(" • Voice (${kmStoneAlertSettings.voiceStyle.label})")
                    if (kmStoneAlertSettings.vibrationEnabled) append(" • ${kmStoneAlertSettings.vibrationPattern.label}")
                    if (kmStoneAlertSettings.soundEnabled) append(" • ${kmStoneAlertSettings.soundOption.label}")
                }
                SmartDropdownSection(
                    title = "Navigation Alerts • KM Stone Monitoring",
                    subtitle = kmAlertSummary,
                    icon = Icons.Default.Vibration,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["km_stone_alerts"] ?: false,
                    onToggleExpand = {
                        expandedSections["km_stone_alerts"] = !(expandedSections["km_stone_alerts"] ?: false)
                    }
                ) {
                    KmStoneAlertSettingsSectionContent(
                        settings = kmStoneAlertSettings,
                        accentColor = primaryAccent,
                        isMonitoringServiceRunning = isKmStoneServiceRunning,
                        serviceStatusNote = kmStoneServiceStatusNote,
                        hasActiveRoute = activeRoute != null,
                        onUpdateSettings = { transform -> viewModel.updateKmStoneAlertSettings(transform) },
                        onResetDefaults = { viewModel.resetKmStoneAlertSettings() },
                        onFeedbackMessage = { msg -> viewModel.showToast(msg, ToastType.INFO) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13f. NAVIGATION -> CHAINAGE VOICE ASSISTANT
            // ====================================================
            if (matchesSearch("navigation chainage voice assistant speech recognition microphone search tts language english hindi telugu speed volume confirmation auto listen", searchQuery)) {
                val voiceAssistantSettings by viewModel.chainageVoiceAssistantSettings.collectAsStateWithLifecycle()
                val voiceAssistantSummary = buildString {
                    append(if (voiceAssistantSettings.voiceAssistantEnabled) "ON" else "OFF")
                    append(" • ${voiceAssistantSettings.voiceLanguage.label}")
                    if (voiceAssistantSettings.voiceOutputEnabled) {
                        append(" • ${voiceAssistantSettings.speechSpeed.label}")
                    }
                    if (voiceAssistantSettings.voiceConfirmationBeforeNav) {
                        append(" • Confirm")
                    }
                }
                SmartDropdownSection(
                    title = "Navigation • Chainage Voice Assistant",
                    subtitle = voiceAssistantSummary,
                    icon = Icons.Default.Mic,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["chainage_voice_assistant"] ?: false,
                    onToggleExpand = {
                        expandedSections["chainage_voice_assistant"] = !(expandedSections["chainage_voice_assistant"] ?: false)
                    }
                ) {
                    ChainageVoiceAssistantSettingsSectionContent(
                        settings = voiceAssistantSettings,
                        accentColor = primaryAccent,
                        onUpdateSettings = { transform -> viewModel.updateChainageVoiceAssistantSettings(transform) },
                        onResetDefaults = { viewModel.resetChainageVoiceAssistantSettings() },
                        onFeedbackMessage = { msg -> viewModel.showToast(msg, ToastType.INFO) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 13g. WEATHER & HIGHWAY WORK INTELLIGENCE
            // ====================================================
            if (matchesSearch("weather forecast hourly daily alerts notifications highway work suitability bc dbm irc morth 501 105 target production rate binder", searchQuery)) {
                val weatherIntelSettings by viewModel.weatherIntelligenceSettings.collectAsStateWithLifecycle()
                val workTargetInput by viewModel.workTargetInput.collectAsStateWithLifecycle()
                val weatherIntelSummary = "${weatherIntelSettings.temperatureUnit.symbol} • ${weatherIntelSettings.contractSpec.morthRevision.shortLabel} • ${weatherIntelSettings.primaryActivity.shortName}"
                SmartDropdownSection(
                    title = "Weather & Highway Work Intelligence",
                    subtitle = weatherIntelSummary,
                    icon = Icons.Rounded.Cloud,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["weather_intelligence"] ?: false,
                    onToggleExpand = {
                        expandedSections["weather_intelligence"] = !(expandedSections["weather_intelligence"] ?: false)
                    }
                ) {
                    com.example.ui.weather.WeatherIntelligenceSettingsSectionContent(
                        settings = weatherIntelSettings,
                        workTargetInput = workTargetInput,
                        accentColor = primaryAccent,
                        onUpdateSettings = { transform -> viewModel.updateWeatherIntelligenceSettings(transform) },
                        onUpdateWorkTargetInput = { updatedInput -> viewModel.updateWorkTargetInput(updatedInput) },
                        onOpenWeatherDashboard = { viewModel.setTab(com.example.ui.AppTab.WEATHER) },
                        onResetDefaults = { viewModel.resetWeatherIntelligenceSettings() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // BACKUP & RESTORE
            // ====================================================
            if (matchesSearch("backup restore export import zip transfer settings history calibration", searchQuery)) {
                val backupHistoryCount by viewModel.backupHistory.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    viewModel.refreshBackupHistory()
                }
                SmartDropdownSection(
                    title = "Backup & Restore",
                    subtitle = if (backupHistoryCount.isNotEmpty()) {
                        "${backupHistoryCount.size} backup(s) • Export & restore all CTECS settings"
                    } else {
                        "Export, validate & restore all settings across devices"
                    },
                    icon = Icons.Default.Backup,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["backup_restore"] ?: false,
                    onToggleExpand = { expandedSections["backup_restore"] = !(expandedSections["backup_restore"] ?: false) }
                ) {
                    BackupRestoreContent(
                        viewModel = viewModel,
                        accentColor = primaryAccent,
                        accentContentColor = primaryAccentContent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 14. PERMISSIONS AND APP INFORMATION
            // ====================================================
            if (matchesSearch("permissions app information version build update restore default", searchQuery)) {
                SmartDropdownSection(
                    title = "Permissions and App Information",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    icon = Icons.Default.Info,
                    accentColor = primaryAccent,
                    isExpanded = expandedSections["permissions_about"] ?: false,
                    onToggleExpand = { expandedSections["permissions_about"] = !(expandedSections["permissions_about"] ?: false) }
                ) {
                    val currentChannel = com.example.update.AppReleaseChannel.current()
                    val isBetaApp = currentChannel == com.example.update.AppReleaseChannel.BETA
                    val channelColor = if (isBetaApp) (if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)) else primaryAccent
                    val channelBg = channelColor.copy(alpha = 0.15f)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

                    Text(
                        text = if (isBetaApp) "Update Channel: Beta" else "Update Channel: Stable",
                        fontSize = 11.sp,
                        color = channelColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Available Update or Downloading Status Card
                    availableUpdate?.let { updateInfo ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = c.secondarySurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = channelColor, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Update Available: ${updateInfo.tagName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (updateInfo.apkSize > 0) {
                                        Text(
                                            text = "%.1f MB".format(updateInfo.apkSize / (1024.0 * 1024.0)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isDownloadingUpdate) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Downloading update...", fontSize = 11.sp, color = channelColor)
                                        Text("${(updateDownloadProgress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { updateDownloadProgress },
                                        color = channelColor,
                                        trackColor = c.elevatedSurface,
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.downloadAndInstallUpdate() },
                                        colors = ButtonDefaults.buttonColors(containerColor = channelColor, contentColor = Color.White),
                                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download & Install Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isCheckingUpdate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = channelColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBetaApp) "Checking for Beta updates..." else "Checking for Stable updates...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.checkForAppUpdate(isManual = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isBetaApp) Color(0xFF7C3AED) else primaryAccent, contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            shape = RoundedCornerShape(12.dp),
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

                    Button(
                        onClick = { showRestoreDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.error.copy(alpha = 0.14f),
                            contentColor = c.error
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset All Settings to Defaults", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    SmartFadeDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.secondarySurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Made with ❤️ madhureddyainala",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
        }
    }

    // Restore Defaults Dialog
    if (showRestoreDialog) {
        val dialogColors = com.example.ui.theme.rememberDialogColors()
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = dialogColors.shape,
            containerColor = dialogColors.containerColor,
            tonalElevation = 0.dp,
            title = { Text("Restore Default Settings?", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
            text = { Text("All custom project profile names, camera settings, timestamp formats, and logo preferences will be reset to factory defaults.", color = dialogColors.textSecondaryColor) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettings()
                        showRestoreDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.error, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reset Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = dialogColors.textSecondaryColor)
                }
            }
        )
    }

    if (showKmlUrlImportDialog) {
        com.example.ui.routes.KmlUrlImportDialog(
            onDismiss = { showKmlUrlImportDialog = false },
            onImportUrl = { url -> viewModel.importRouteFromUrl(url) }
        )
    }

    // Modal File Name Format Selection Dialog (Matching reference screenshot)
    if (showFileNameFormatDialog) {
        FileNameFormatDialog(
            currentSelectionId = currentCameraSettings.filenameFormatPreset,
            currentProject = currentProject,
            onSelect = { presetId ->
                viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(filenameFormatPreset = presetId))
                viewModel.showToast("File name format updated")
                showFileNameFormatDialog = false
            },
            onDismiss = { showFileNameFormatDialog = false }
        )
    }

    if (showSignatureDrawDialog) {
        SignatureDrawPadDialog(
            onDismiss = { showSignatureDrawDialog = false },
            onSaveSignature = { drawnBitmap ->
                try {
                    val targetFile = File(context.filesDir, "dpr_signature.png")
                    targetFile.outputStream().use { outStream ->
                        drawnBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outStream)
                    }
                    viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfSignatureUri = targetFile.absolutePath))
                    viewModel.showToast("Signature saved successfully")
                } catch (e: Exception) {
                    viewModel.showToast("Failed to save drawn signature")
                }
                showSignatureDrawDialog = false
            }
        )
    }

    if (showTimestampPreviewPopup) {
        SmartTimestampPreviewDialog(
            overlay = currentOverlay,
            project = currentProject,
            cameraSettings = currentCameraSettings,
            locationName = currentLocationName,
            liveLocation = locationState.point ?: lastValidLocation,
            liveChainage = liveMatchResult.displayChainage.takeIf { it != "---" }.orEmpty(),
            weatherObservation = latestWeatherObservation,
            isLandscape = isPreviewLandscape,
            onToggleOrientation = { isPreviewLandscape = !isPreviewLandscape },
            mediaMode = previewMediaMode,
            onMediaModeChange = { previewMediaMode = it },
            onPositionSelect = { newPos ->
                viewModel.saveOverlayConfigSilent(currentOverlay.copy(watermarkPosition = newPos))
            },
            onDismiss = { showTimestampPreviewPopup = false }
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
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val accentColor = CtecsTheme.colors.accent

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                )
                Text(
                    text = "${resolutions.size} supported hardware outputs detected",
                    style = MaterialTheme.typography.labelSmall.copy(color = dialogColors.textSecondaryColor)
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
                            color = if (isSelected) accentColor else dialogColors.textColor
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(res) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = dialogColors.textSecondaryColor
                            )
                        )
                    }
                    SmartFadeDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = dialogColors.textColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Clean, scrollable modal selection dialog for File Name Formats matching reference screenshot.
 * Displays all 11 format presets dynamically evaluated with current timestamp and highway metadata.
 */
@Composable
private fun FileNameFormatDialog(
    currentSelectionId: String,
    currentProject: ProjectProfile,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val accentColor = CtecsTheme.colors.accent

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "File name format",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "File name format",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialogColors.titleColor
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                SmartFadeDivider(color = accentColor.copy(alpha = 0.55f))
            }
        },
        text = {
            val presets = remember { com.example.model.FilenameFormats.PRESETS }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                presets.forEach { option ->
                    val isSelected = option.id.equals(currentSelectionId, ignoreCase = true)
                    val previewText = option.formatDynamicPreview(
                        project = currentProject.highwayId,
                        stretch = currentProject.packageSection,
                        chainage = "189+320",
                        side = currentProject.roadSide.label,
                        lane = currentProject.lane,
                        workType = currentProject.workType,
                        activity = currentProject.activity
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.id) }
                            .padding(vertical = 11.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = previewText,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accentColor else dialogColors.textColor,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(option.id) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = dialogColors.textSecondaryColor
                            )
                        )
                    }
                    SmartFadeDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * On-screen finger/stylus signature drawing pad with live path rendering and smooth saving.
 */
@Composable
private fun SignatureDrawPadDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (android.graphics.Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val accentColor = CtecsTheme.colors.accent

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = accentColor)
                Text("Draw Digital Signature", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = dialogColors.titleColor)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign inside the box using your finger or stylus:",
                    fontSize = 12.sp,
                    color = dialogColors.textSecondaryColor
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(174.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White)
                            .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 4f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                        for (stroke in strokes) {
                            if (stroke.size > 1) {
                                val path = androidx.compose.ui.graphics.Path()
                                path.moveTo(stroke.first().x, stroke.first().y)
                                for (i in 1 until stroke.size) {
                                    path.lineTo(stroke[i].x, stroke[i].y)
                                }
                                drawPath(path, Color.Black, style = strokeStyle)
                            }
                        }
                        if (currentStroke.size > 1) {
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo(currentStroke.first().x, currentStroke.first().y)
                            for (i in 1 until currentStroke.size) {
                                path.lineTo(currentStroke[i].x, currentStroke[i].y)
                            }
                            drawPath(path, Color.Black, style = strokeStyle)
                        }
                    }
                }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            strokes.clear()
                            currentStroke = emptyList()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Drawing", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        val bmp = android.graphics.Bitmap.createBitmap(480, 180, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            strokeWidth = 6f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        for (stroke in strokes) {
                            if (stroke.size > 1) {
                                val path = android.graphics.Path()
                                path.moveTo(stroke.first().x, stroke.first().y)
                                for (i in 1 until stroke.size) {
                                    path.lineTo(stroke[i].x, stroke[i].y)
                                }
                                canvas.drawPath(path, paint)
                            }
                        }
                        onSaveSignature(bmp)
                    }
                    onDismiss()
                },
                enabled = strokes.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
            ) {
                Text("Save Signature", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = dialogColors.textSecondaryColor)
            }
        }
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
    val c = CtecsTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(220))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
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
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isExpanded) accentColor else c.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    SmartFadeDivider(modifier = Modifier.padding(bottom = 10.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    val c = CtecsTheme.colors
    val switchTrackColor = c.accent
    val switchThumbColor = c.onAccent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = switchThumbColor,
                checkedTrackColor = switchTrackColor,
                uncheckedTrackColor = c.secondarySurface,
                uncheckedBorderColor = Color.Transparent
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
    val c = CtecsTheme.colors
    val sliderAccent = c.accent

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, fontWeight = FontWeight.Medium)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = sliderAccent, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = sliderAccent,
                activeTrackColor = sliderAccent,
                inactiveTrackColor = c.secondarySurface
            )
        )
    }
}

@Composable
private fun PaddingSliderWithDirectInput(
    title: String,
    valueDp: Int,
    range: ClosedFloatingPointRange<Float> = 0f..120f,
    onValueChange: (Int) -> Unit
) {
    val c = CtecsTheme.colors
    val padAccent = if (c.isDark) Color(0xFF7B9CFF) else Color(0xFF3B5BDB)
    var textValue by remember(valueDp) { mutableStateOf(valueDp.toString()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        textValue = digits
                        val parsed = digits.toIntOrNull()
                        if (parsed != null) {
                            onValueChange(parsed.coerceIn(0, 120))
                        } else if (digits.isEmpty()) {
                            onValueChange(0)
                        }
                    },
                    textStyle = MaterialTheme.typography.labelMedium.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = padAccent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(68.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ctecsBorderlessInputColors(
                        containerColor = c.secondarySurface,
                        accentColor = padAccent
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary
                )
            }
        }
        Slider(
            value = valueDp.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = padAccent,
                activeTrackColor = padAccent,
                inactiveTrackColor = c.secondarySurface
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartTimestampPreviewDialog(
    overlay: OverlayConfig,
    project: ProjectProfile,
    cameraSettings: CameraSettings,
    locationName: String,
    liveLocation: com.example.model.GeoPoint? = null,
    liveChainage: String = "",
    weatherObservation: com.example.model.WeatherObservation? = null,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
    mediaMode: String,
    onMediaModeChange: (String) -> Unit,
    onPositionSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val accentColor = if (c.isDark) Color(0xFF7B9CFF) else Color(0xFF3B5BDB)
    val secondaryColor = c.accent

    var liveNow by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            liveNow = Date()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val formattedLiveTimestamp = remember(overlay.timestampFormat, cameraSettings.is24HourFormat, liveNow) {
        try {
            SimpleDateFormat(overlay.timestampFormat, Locale.US).format(liveNow)
        } catch (e: Exception) {
            overlay.timestampFormat
        }
    }

    val previewParsedColor = try {
        Color(android.graphics.Color.parseColor(overlay.textColorHex)).copy(alpha = overlay.textOpacity.coerceIn(0.2f, 1.0f))
    } catch (e: Exception) {
        Color.White
    }

    val previewBgParsed = if (overlay.backgroundStyle != "Transparent") {
        try {
            Color(android.graphics.Color.parseColor(overlay.backgroundColorHex)).copy(alpha = overlay.backgroundOpacity.coerceIn(0.1f, 1f))
        } catch (e: Exception) {
            Color.Black.copy(alpha = 0.6f)
        }
    } else {
        Color.Transparent
    }

    val previewFontFamily = when (overlay.fontFamily.lowercase()) {
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }

    val previewFontWeight = when (overlay.fontStyle.lowercase()) {
        "bold" -> FontWeight.Bold
        "medium" -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    val previewFontStyle = if (overlay.fontStyle.equals("italic", ignoreCase = true)) FontStyle.Italic else FontStyle.Normal

    val previewTextAlign = when (overlay.textAlignment.lowercase()) {
        "left" -> TextAlign.Start
        "center" -> TextAlign.Center
        else -> TextAlign.End
    }

    val activePreviewPadding = overlay.getEffectivePadding(isVideo = mediaMode == "video")
    val scaledPadLeft = (activePreviewPadding.left * 0.45f).coerceIn(4f, 40f).dp
    val scaledPadRight = (activePreviewPadding.right * 0.45f).coerceIn(4f, 40f).dp
    val scaledPadTop = (activePreviewPadding.top * 0.45f).coerceIn(4f, 40f).dp
    val scaledPadBottom = (activePreviewPadding.bottom * 0.45f).coerceIn(4f, 40f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BrandingWatermark,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Smart Timestamp Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = dialogColors.titleColor
                        )
                        Text(
                            text = "Real-time live watermark & layout simulation",
                            style = MaterialTheme.typography.labelSmall,
                            color = dialogColors.textSecondaryColor
                        )
                    }
                }
                IconButton(onClick = onToggleOrientation) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Toggle Portrait/Landscape",
                        tint = secondaryColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mode & Orientation Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mediaMode == "photo",
                        onClick = { onMediaModeChange("photo") },
                        label = { Text("Photo (4:3)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = Color.White,
                            containerColor = c.secondarySurface,
                            labelColor = c.textSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mediaMode == "video",
                        onClick = { onMediaModeChange("video") },
                        label = { Text("Video (16:9)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = Color.White,
                            containerColor = c.secondarySurface,
                            labelColor = c.textSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live Preview Canvas Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF080B12)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                when {
                                    isLandscape -> 140.dp
                                    mediaMode == "video" -> 165.dp
                                    else -> 205.dp
                                }
                            )
                            .background(Color(0xFF080B12))
                            .padding(
                                start = scaledPadLeft,
                                end = scaledPadRight,
                                top = scaledPadTop,
                                bottom = scaledPadBottom
                            ),
                        contentAlignment = when (overlay.watermarkPosition.lowercase()) {
                            "top left" -> Alignment.TopStart
                            "top center" -> Alignment.TopCenter
                            "top right" -> Alignment.TopEnd
                            "center left" -> Alignment.CenterStart
                            "center" -> Alignment.Center
                            "center right" -> Alignment.CenterEnd
                            "bottom left" -> Alignment.BottomStart
                            "bottom center" -> Alignment.BottomCenter
                            else -> Alignment.BottomEnd
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(previewBgParsed)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = when (overlay.textAlignment.lowercase()) {
                                "left" -> Alignment.Start
                                "center" -> Alignment.CenterHorizontally
                                else -> Alignment.End
                            }
                        ) {
                            if (overlay.showWeatherInfo) {
                                val dynamicWeatherPreviewLines = com.example.overlay.SmartTimestampComposer.composeLines(
                                    config = overlay,
                                    project = project,
                                    location = liveLocation,
                                    chainageStr = liveChainage.ifBlank { "198+450" },
                                    coordinateFormat = cameraSettings.coordinateFormat,
                                    is24HourFormat = cameraSettings.is24HourFormat,
                                    timestampMs = liveNow.time,
                                    locationName = locationName.ifBlank { overlay.lastDetectedLocationName },
                                    weatherObservation = weatherObservation
                                )
                                dynamicWeatherPreviewLines.forEach { line ->
                                    Text(
                                        text = line,
                                        color = previewParsedColor,
                                        fontSize = 11.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = previewFontWeight,
                                        fontStyle = previewFontStyle,
                                        textAlign = previewTextAlign
                                    )
                                }
                            } else {
                                if (overlay.showProjectInfo) {
                                    val highwayPreview = project.buildHighwayText("198+450").ifBlank {
                                        "NH-44 Expansion | 198+450 RHS • DBM"
                                    }
                                    Text(
                                        text = highwayPreview,
                                        color = previewParsedColor,
                                        fontSize = 11.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = previewFontWeight,
                                        fontStyle = previewFontStyle,
                                        textAlign = previewTextAlign
                                    )
                                }
                                val commentLine = project.getCustomCommentLine()
                                if (!commentLine.isNullOrBlank()) {
                                    Text(
                                        text = commentLine,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = previewTextAlign
                                    )
                                }
                                if (overlay.showLocationName) {
                                    val locNameDisplay = locationName.ifBlank {
                                        overlay.lastDetectedLocationName.ifBlank { "Dehradun, Uttarakhand" }
                                    }
                                    Text(
                                        text = locNameDisplay,
                                        color = previewParsedColor,
                                        fontSize = 11.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = previewFontWeight,
                                        fontStyle = previewFontStyle,
                                        textAlign = previewTextAlign
                                    )
                                }
                                if (overlay.burnInTimestamp) {
                                    Text(
                                        text = formattedLiveTimestamp,
                                        color = previewParsedColor,
                                        fontSize = 11.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = previewFontWeight,
                                        fontStyle = previewFontStyle,
                                        textAlign = previewTextAlign
                                    )
                                }
                                if (overlay.showCoordinates || overlay.showAccuracy) {
                                    val coordText = if (overlay.showCoordinates && overlay.showAccuracy) {
                                        "30.3165°N 78.0322°E • ±4m"
                                    } else if (overlay.showCoordinates) {
                                        "30.3165°N 78.0322°E"
                                    } else {
                                        "GPS ±4m"
                                    }
                                    Text(
                                        text = coordText,
                                        color = previewParsedColor,
                                        fontSize = 10.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = previewFontWeight,
                                        fontStyle = previewFontStyle,
                                        textAlign = previewTextAlign
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Position Selector inside Popup
                Text(
                    text = "QUICK POSITION SELECTOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Top Left", "Top Center", "Top Right",
                        "Center Left", "Center", "Center Right",
                        "Bottom Left", "Bottom Center", "Bottom Right"
                    ).forEach { pos ->
                        val isSel = overlay.watermarkPosition.equals(pos, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = { onPositionSelect(pos) },
                            label = { Text(pos, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.White,
                                containerColor = dialogColors.surfaceVariantColor,
                                labelColor = dialogColors.textColor
                            )
                        )
                    }
                }

                // Active Summary Row
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = dialogColors.surfaceVariantColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Font: ${overlay.fontFamily} (${overlay.fontSizeSp.toInt()}sp) • Align: ${overlay.textAlignment} • Margins: T${activePreviewPadding.top} B${activePreviewPadding.bottom} L${activePreviewPadding.left} R${activePreviewPadding.right}dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = dialogColors.textSecondaryColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun matchesSearch(text: String, query: String): Boolean {
    if (query.isBlank()) return true
    return text.contains(query.trim(), ignoreCase = true)
}
