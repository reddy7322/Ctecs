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
                    title = "1. Project Settings",
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
                    Text("Timestamp Format Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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
                                    Text(sample, fontSize = 13.sp, color = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface)
                                    Text(pattern, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Watermark Placement Position", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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
            if (matchesSearch("custom logo size opacity position shape rotation stamp", searchQuery)) {
                val hasCustomLogo = !currentOverlay.logoUri.isNullOrBlank() && File(currentOverlay.logoUri ?: "").exists()

                SmartDropdownSection(
                    title = "10. Custom Logo",
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
                    }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text("App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
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
            title = { Text("Restore Default Settings?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("All custom project profile names, camera settings, timestamp formats, and logo preferences will be reset to factory defaults.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
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
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            color = if (isSelected) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(res) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF38BDF8),
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
