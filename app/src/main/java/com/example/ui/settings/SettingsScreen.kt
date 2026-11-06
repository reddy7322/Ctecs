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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.example.overlay.LogoProvider
import com.example.ui.MainViewModel
import com.example.ui.ToastType
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

    // Dialog states for Photo & Video Resolution selection and File Name Format
    var showPhotoResolutionDialog by remember { mutableStateOf(false) }
    var showVideoResolutionDialog by remember { mutableStateOf(false) }
    var showFileNameFormatDialog by remember { mutableStateOf(false) }
    var showSignatureDrawDialog by remember { mutableStateOf(false) }

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
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_alignment.kmz"
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
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            if (fileName.endsWith(".kmz", ignoreCase = true) || fileName.endsWith(".kml", ignoreCase = true)) {
                // Direct KMZ or KML imported via project import
                viewModel.importRoute(uri, fileName)
                if (currentProject.highwayId.isBlank()) {
                    val pName = fileName.substringBeforeLast(".")
                    viewModel.saveProjectProfileSilent(currentProject.copy(highwayId = pName))
                    viewModel.addDefinedProject(DefinedProject(name = pName, stretch = currentProject.packageSection, workType = currentProject.workType))
                }
                viewModel.showSuccess("Imported KMZ/KML alignment: $fileName")
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
                            workType = workType.ifBlank { currentProject.workType }
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
                                        workType = pObj.optString("workType", "MM").trim()
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
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            tint = MaterialTheme.colorScheme.onSurface
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
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
                placeholder = { Text("Search settings (e.g. project, stretch, work type, resolution, logo)...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                    text = "CATEGORIES (ALL COLLAPSED)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Collapse All", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    subtitle = "${currentProject.highwayId.ifBlank { "No Project Selected" }} • ${currentProject.packageSection.ifBlank { "No Strech Set" }} • Work: ${if (currentProject.workType.isBlank() || currentProject.workType.equals("None", ignoreCase = true)) "None" else currentProject.workType}",
                    icon = Icons.Default.Engineering,
                    accentColor = Color(0xFF38BDF8),
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
                            value = currentProject.highwayId,
                            onValueChange = { newName ->
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "PROJECTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
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
                                                    color = if (isSelected) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (proj.stretch.isNotBlank() || proj.workType.isNotBlank()) {
                                                    Text(
                                                        text = listOfNotNull(
                                                            proj.stretch.takeIf { it.isNotBlank() },
                                                            proj.workType.takeIf { it.isNotBlank() }
                                                        ).joinToString(" • "),
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
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.removeDefinedProject(proj.name) },
                                                    modifier = Modifier.size(28.dp).testTag("delete_project_${proj.name}")
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
                                            viewModel.saveProjectProfileSilent(
                                                currentProject.copy(
                                                    highwayId = proj.name,
                                                    packageSection = if (proj.stretch.isNotBlank()) proj.stretch else currentProject.packageSection,
                                                    workType = if (proj.workType.isNotBlank()) proj.workType else currentProject.workType
                                                )
                                            )
                                            projectDropdownExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }

                            if (currentProject.highwayId.isNotBlank() && definedProjects.none { it.name.equals(currentProject.highwayId, ignoreCase = true) }) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "+ Save \"${currentProject.highwayId}\" to Projects",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        viewModel.addDefinedProject(
                                            DefinedProject(
                                                name = currentProject.highwayId,
                                                stretch = currentProject.packageSection,
                                                workType = currentProject.workType
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
                        value = currentProject.packageSection,
                        onValueChange = { viewModel.saveProjectProfileSilent(currentProject.copy(packageSection = it)) },
                        label = { Text("Strech") },
                        placeholder = { Text("Enter Strech / Section", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("settings_stretch_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        )
                    )

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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = workTypeDropdownExpanded,
                            onDismissRequest = { workTypeDropdownExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
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
                                                color = if (isSelected) Color(0xFF38BDF8) else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
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
                                                color = if (isSelected) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF38BDF8),
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
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                                tint = if (activeRoute != null) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (activeRoute != null) {
                                    "Alignment: ${activeRoute?.name} (${activeRoute?.markers?.size ?: 0} KM marks) • Included in Export"
                                } else {
                                    "No KMZ/KML attached • Will export project settings only"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeRoute != null) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val sanitizedName = (currentProject.highwayId.ifBlank { "Project" })
                                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                exportProjectLauncher.launch("${sanitizedName}_alignment_bundle.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_project_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF38BDF8)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
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
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00BCD4),
                                contentColor = Color.Black
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
            // 2. CAMERA AND LENS
            // ====================================================
            if (matchesSearch("camera lens zoom shutter sound grid touch mirror battery save white balance", searchQuery)) {
                SmartDropdownSection(
                    title = "Camera and Lens",
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
                    title = "Photo Settings",
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
                    Text("Photo Resolution (Real Hardware Capabilities)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedButton(
                        onClick = { showPhotoResolutionDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00BCD4)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BCD4))
                    ) {
                        Text("Photo Resolution: ${currentCameraSettings.photoResolution}", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Preview Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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
                                    selectedLabelColor = Color.Black,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    title = "Video Recording Settings",
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
                    Text("Video Recording Resolution", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedButton(
                        onClick = { showVideoResolutionDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text("Video Resolution: ${currentCameraSettings.videoResolution}", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Target Video Frame Rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    title = "Resolution and Frame Rate",
                    subtitle = "Photo: ${currentCameraSettings.photoResolution} | Video: ${currentCameraSettings.videoResolution}",
                    icon = Icons.Default.AspectRatio,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["resolution"] ?: false,
                    onToggleExpand = { expandedSections["resolution"] = !(expandedSections["resolution"] ?: false) }
                ) {
                    Text(
                        text = "Separate hardware resolution lists derived from active sensor output capabilities. Orientation flips are normalized and deduplicated.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Photo Resolution Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Photo Resolution Capabilities", fontWeight = FontWeight.Bold, color = Color(0xFF00BCD4), fontSize = 13.sp)
                                Text("${photoResolutions.size} Options Detected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Current Selection: ${currentCameraSettings.photoResolution}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Video Recording Capabilities", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 13.sp)
                                Text("${videoResolutions.size} Options Detected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Current Selection: ${currentCameraSettings.videoResolution}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            // 6. TIMESTAMP (MERGED WITH METADATA & WATERMARK)
            // ====================================================
            if (matchesSearch("timestamp date time format precision 24-hour placement pattern metadata watermark font size color opacity shadow background alignment spacing", searchQuery)) {
                var isFormatDropdownOpen by remember { mutableStateOf(false) }
                var isPositionDropdownOpen by remember { mutableStateOf(false) }
                var isFontDropdownOpen by remember { mutableStateOf(false) }
                var customFormatText by remember(currentOverlay.timestampFormat) { mutableStateOf(currentOverlay.timestampFormat) }
                var customHexText by remember(currentOverlay.textColorHex) { mutableStateOf(currentOverlay.textColorHex) }
                var showCustomFormatInput by remember { mutableStateOf(false) }

                SmartDropdownSection(
                    title = "Timestamp",
                    subtitle = "${currentOverlay.timestampFormat} • ${currentOverlay.watermarkPosition} • ${currentOverlay.fontFamily} • ${currentOverlay.fontSizeSp.toInt()}sp",
                    icon = Icons.Default.BrandingWatermark,
                    accentColor = Color(0xFFFFD700),
                    isExpanded = expandedSections["timestamp"] ?: false,
                    onToggleExpand = { expandedSections["timestamp"] = !(expandedSections["timestamp"] ?: false) }
                ) {
                    // ----------------------------------------------------
                    // SUBSECTION 1: DISPLAY & OVERLAY TOGGLES
                    // ----------------------------------------------------
                    Text(
                        text = "DISPLAY & OVERLAY TOGGLES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )

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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 2: DROPDOWN - TIMESTAMP FORMAT PRESETS
                    // ----------------------------------------------------
                    Text(
                        text = "TIMESTAMP FORMAT PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
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
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
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
                                        color = Color(0xFFFFD700)
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
                                    tint = Color(0xFFFFD700)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isFormatDropdownOpen,
                            onDismissRequest = { isFormatDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 380.dp)
                                .background(MaterialTheme.colorScheme.surface)
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
                                                    color = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFD700),
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                                            color = Color(0xFF38BDF8)
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
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 3: DROPDOWN - WATERMARK PLACEMENT POSITION
                    // ----------------------------------------------------
                    Text(
                        text = "WATERMARK PLACEMENT POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
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
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
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
                                        color = Color(0xFFFFD700)
                                    )
                                    Text(
                                        text = "Anchors timestamp & metadata to this frame position",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isPositionDropdownOpen) Icons.Default.ExpandLess else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Placement",
                                    tint = Color(0xFFFFD700)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isPositionDropdownOpen,
                            onDismissRequest = { isPositionDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 340.dp)
                                .background(MaterialTheme.colorScheme.surface)
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
                                                color = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFD700),
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 4: DROPDOWN - FONT SELECTION & TYPOGRAPHY
                    // ----------------------------------------------------
                    Text(
                        text = "FONT SELECTION & TYPOGRAPHY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
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
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
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
                                        color = Color(0xFFFFD700)
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
                                    tint = Color(0xFFFFD700)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isFontDropdownOpen,
                            onDismissRequest = { isFontDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 360.dp)
                                .background(MaterialTheme.colorScheme.surface)
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
                                                    color = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
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
                                                    tint = Color(0xFFFFD700),
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                                                    color = Color(0xFF22C55E)
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
                                                tint = Color(0xFF22C55E),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = { isFontDropdownOpen = false }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.UploadFile,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Import Custom Font (TTF/OTF)...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF38BDF8)
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
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Revert to Default Font",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFEF4444)
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 5: FONT & TEXT CUSTOMIZATION
                    // ----------------------------------------------------
                    Text(
                        text = "FONT & TEXT CUSTOMIZATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
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
                                label = { Text(weight, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
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
                                label = { Text(align, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 6: COLOR, STROKE & SHADOW
                    // ----------------------------------------------------
                    Text(
                        text = "TEXT COLOR, OUTLINE & SHADOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val colorPresets = listOf(
                        "#FFFFFF" to "White",
                        "#FFD700" to "Gold",
                        "#38BDF8" to "Cyan",
                        "#22C55E" to "Green",
                        "#F97316" to "Orange",
                        "#EF4444" to "Red",
                        "#FACC15" to "Yellow",
                        "#E2E8F0" to "Gray"
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFFD700) else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.saveOverlayConfigSilent(currentOverlay.copy(textColorHex = hex))
                                        customHexText = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (hex == "#FFFFFF" || hex == "#FFD700" || hex == "#FACC15") Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(parsedCustomColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = customHexText,
                            onValueChange = { customHexText = it },
                            label = { Text("Custom Hex Color (e.g. #FFFFFF)") },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 7: BACKGROUND PANEL STYLE
                    // ----------------------------------------------------
                    Text(
                        text = "TEXT BACKGROUND PANEL STYLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
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
                                label = { Text(style, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color.Black
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
                                    label = { Text(name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFFD700),
                                        selectedLabelColor = Color.Black
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                    // ----------------------------------------------------
                    // SUBSECTION 8: LIVE INTERACTIVE PREVIEW
                    // ----------------------------------------------------
                    Text(
                        text = "LIVE INTERACTIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val previewParsedColor = try {
                        Color(android.graphics.Color.parseColor(currentOverlay.textColorHex)).copy(alpha = currentOverlay.textOpacity.coerceIn(0.2f, 1.0f))
                    } catch (e: Exception) {
                        Color.White
                    }

                    val previewBgParsed = if (currentOverlay.backgroundStyle != "Transparent") {
                        try {
                            Color(android.graphics.Color.parseColor(currentOverlay.backgroundColorHex)).copy(alpha = currentOverlay.backgroundOpacity.coerceIn(0.1f, 1f))
                        } catch (e: Exception) {
                            Color.Black.copy(alpha = 0.6f)
                        }
                    } else {
                        Color.Transparent
                    }

                    val previewFontFamily = when (currentOverlay.fontFamily.lowercase()) {
                        "monospace" -> FontFamily.Monospace
                        "serif" -> FontFamily.Serif
                        "cursive" -> FontFamily.Cursive
                        else -> FontFamily.SansSerif
                    }

                    val previewFontWeight = when (currentOverlay.fontStyle.lowercase()) {
                        "bold" -> FontWeight.Bold
                        "medium" -> FontWeight.Medium
                        else -> FontWeight.Normal
                    }

                    val previewFontStyle = if (currentOverlay.fontStyle.equals("italic", ignoreCase = true)) FontStyle.Italic else FontStyle.Normal

                    val previewTextAlign = when (currentOverlay.textAlignment.lowercase()) {
                        "left" -> TextAlign.Start
                        "center" -> TextAlign.Center
                        else -> TextAlign.End
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFF0F172A))
                                .padding(12.dp),
                            contentAlignment = when (currentOverlay.watermarkPosition.lowercase()) {
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
                                horizontalAlignment = when (currentOverlay.textAlignment.lowercase()) {
                                    "left" -> Alignment.Start
                                    "center" -> Alignment.CenterHorizontally
                                    else -> Alignment.End
                                }
                            ) {
                                if (currentOverlay.showProjectInfo) {
                                    Text(
                                        text = "MH | MM | 189+320 LHS",
                                        color = previewParsedColor,
                                        fontSize = 11.sp,
                                        fontFamily = previewFontFamily,
                                        fontWeight = previewFontWeight,
                                        fontStyle = previewFontStyle,
                                        textAlign = previewTextAlign
                                    )
                                }
                                Text(
                                    text = currentSampleDate,
                                    color = previewParsedColor,
                                    fontSize = 11.sp,
                                    fontFamily = previewFontFamily,
                                    fontWeight = previewFontWeight,
                                    fontStyle = previewFontStyle,
                                    textAlign = previewTextAlign
                                )
                                if (currentOverlay.showAccuracy) {
                                    Text(
                                        text = "29.8339°N 77.8893°E • ±4m",
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
                    Text("Coordinate Format", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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
                                    selectedLabelColor = Color.Black,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    title = "Chainage, KMZ and LHS/RHS",
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
                    Text("Active KMZ Route Alignment", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Currently active: ${activeRoute?.name ?: "Sample NH-58 Route"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["logo"] ?: false,
                    onToggleExpand = { expandedSections["logo"] = !(expandedSections["logo"] ?: false) }
                ) {
                    if (!hasCustomLogo) {
                        // Empty State - No prefilled logos or dummy presets
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Custom Logo Uploaded",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Upload your project or company logo to watermark captured photos and videos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("upload_custom_logo_button")
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
                            try {
                                android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        // Preview & Actions Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
                                                tint = Color(0xFF38BDF8)
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
                                            tint = Color(0xFF38BDF8)
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
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }

                        CompactSwitchRow(
                            title = "Show Logo Stamp",
                            subtitle = "Burn uploaded logo onto captured photos & videos",
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
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF38BDF8),
                                        selectedLabelColor = Color.Black,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF38BDF8),
                                        selectedLabelColor = Color.Black,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "PDF Default Logo & Signature (DPR Reports)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "Configure default header logo, signature, and engineer name for exported PDF reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                                    try {
                                        android.graphics.BitmapFactory.decodeFile(pdfLogoFile.absolutePath)
                                    } catch (e: Exception) { null }
                                }
                                if (pdfLogoBmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                                                color = Color(0xFF16A34A)
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
                            OutlinedButton(
                                onClick = { pdfLogoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (currentOverlay.pdfLogoUri.isNullOrBlank()) "Upload Header Logo" else "Change Logo", fontSize = 11.sp)
                            }
                            if (!currentOverlay.pdfLogoUri.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfLogoUri = null)) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        CompactSwitchRow(
                            title = "Include PDF Logo by Default",
                            subtitle = "Automatically include header logo in exported DPR reports",
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
                                    try {
                                        android.graphics.BitmapFactory.decodeFile(pdfSigFile.absolutePath)
                                    } catch (e: Exception) { null }
                                }
                                if (pdfSigBmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                                                color = Color(0xFF16A34A)
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
                            OutlinedButton(
                                onClick = { pdfSignaturePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (currentOverlay.pdfSignatureUri.isNullOrBlank()) "Upload" else "Change", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { showSignatureDrawDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Draw", fontSize = 11.sp)
                            }
                            if (!currentOverlay.pdfSignatureUri.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfSignatureUri = null)) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        CompactSwitchRow(
                            title = "Include Signature by Default",
                            subtitle = "Automatically include saved signature in exported DPR reports",
                            checked = currentOverlay.includePdfSignatureDefault,
                            onCheckedChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(includePdfSignatureDefault = it)) },
                            tag = "switch_pdf_sig_default"
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = currentOverlay.pdfDefaultPreparedBy,
                            onValueChange = { viewModel.saveOverlayConfigSilent(currentOverlay.copy(pdfDefaultPreparedBy = it)) },
                            label = { Text("Default Prepared By (Engineer Name)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================
            // 11. IMAGE AND VIDEO QUALITY
            // ====================================================
            if (matchesSearch("image video quality jpeg compression bitrate hardware encoder", searchQuery)) {
                SmartDropdownSection(
                    title = "Image and Video Quality",
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["storage"] ?: false,
                    onToggleExpand = { expandedSections["storage"] = !(expandedSections["storage"] ?: false) }
                ) {
                    // Smart Dynamic Option: File Name Save Format (Photos & Videos)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("file_name_format_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "File name format",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "File Name Save Format",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Photos & Videos (Tap to choose format)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showInlineFormatDropdown = !showInlineFormatDropdown }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown format selector",
                                            tint = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                            }

                            // Dynamic Live Preview Box
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clickable { showFileNameFormatDialog = true }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Current dynamic filename:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$dynamicPreview.jpg",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = "$dynamicPreview.mp4 (Video)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8).copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Smart Dropdown Menu for Quick Selection
                            DropdownMenu(
                                expanded = showInlineFormatDropdown,
                                onDismissRequest = { showInlineFormatDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
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
                                                    color = if (isSel) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface,
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
                                                        selectedColor = Color(0xFF38BDF8)
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
                        value = currentCameraSettings.customFilenamePrefix,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customFilenamePrefix = it)) },
                        label = { Text("Custom Filename Prefix (Optional e.g. NH58_)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                        value = currentCameraSettings.customStoragePath,
                        onValueChange = { viewModel.saveCameraSettingsSilent(currentCameraSettings.copy(customStoragePath = it)) },
                        label = { Text("Storage Directory Path") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
            if (matchesSearch("display appearance theme mode horizon level auto-rotate screen on", searchQuery)) {
                SmartDropdownSection(
                    title = "Display and Appearance",
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
                    Text("App Theme Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
                                    selectedLabelColor = Color.Black,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    title = "Permissions and App Information",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    icon = Icons.Default.Info,
                    accentColor = Color(0xFF38BDF8),
                    isExpanded = expandedSections["permissions_about"] ?: false,
                    onToggleExpand = { expandedSections["permissions_about"] = !(expandedSections["permissions_about"] ?: false) }
                ) {
                    val isDark = com.example.ui.theme.isAppDarkTheme()
                    val currentChannel = com.example.update.AppReleaseChannel.current()
                    val isBetaApp = currentChannel == com.example.update.AppReleaseChannel.BETA
                    val channelColor = if (isBetaApp) Color(0xFFF59E0B) else Color(0xFF0284C7)
                    val channelBg = if (isBetaApp) Color(0xFFB45309).copy(alpha = 0.2f) else Color(0xFF0284C7).copy(alpha = 0.15f)

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

                    Text("Package: ${context.packageName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (isBetaApp) "Update Channel: Beta (Receives Beta releases only)" else "Update Channel: Stable (Receives Stable releases only)",
                        fontSize = 11.sp,
                        color = channelColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Available Update or Downloading Status Card
                    availableUpdate?.let { updateInfo ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFF0F9FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, channelColor.copy(alpha = 0.4f)),
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
                                        trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.downloadAndInstallUpdate() },
                                        colors = ButtonDefaults.buttonColors(containerColor = channelColor, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
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
                            colors = ButtonDefaults.buttonColors(containerColor = if (isBetaApp) Color(0xFFB45309) else Color(0xFF0284C7), contentColor = Color.White),
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

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
        val dialogColors = com.example.ui.theme.rememberDialogColors()
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = dialogColors.shape,
            containerColor = dialogColors.containerColor,
            title = { Text("Restore Default Settings?", fontWeight = FontWeight.Bold, color = dialogColors.titleColor) },
            text = { Text("All custom project profile names, camera settings, timestamp formats, and logo preferences will be reset to factory defaults.", color = dialogColors.textSecondaryColor) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettings()
                        showRestoreDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
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
    val accentColor = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
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
                    HorizontalDivider(color = dialogColors.borderColor)
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
    val accentColor = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
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
                HorizontalDivider(
                    thickness = 1.5.dp,
                    color = accentColor
                )
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
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = dialogColors.borderColor
                    )
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
    val accentColor = if (dialogColors.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
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
                        .height(170.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .border(1.5.dp, accentColor, RoundedCornerShape(10.dp))
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
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isExpanded) accentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 10.dp))
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
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF38BDF8),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
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
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        tint = MaterialTheme.colorScheme.onSurface,
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
                    val highwayPreview = project.buildHighwayText("181+000")
                    if (highwayPreview.isNotBlank()) {
                        Text(
                            text = highwayPreview,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    }
                    val commentLine = project.getCustomCommentLine()
                    if (commentLine != null) {
                        Text(
                            text = commentLine,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
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
