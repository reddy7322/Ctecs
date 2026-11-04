package com.example.ui.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DailyProjectReportEntity
import com.example.data.HighwayProjectEntity
import com.example.data.SmartActivityEntity
import com.example.model.MatchConfidence
import com.example.ui.MainViewModel
import com.example.util.DprPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// -------------------------------------------------------------
// HELPER: CHAINAGE PARSING & DYNAMIC FORMATTING
// -------------------------------------------------------------
fun formatHighwayChainageInput(raw: String): String {
    val clean = raw.trim()
    if (clean.isBlank()) return ""
    // If user typed plus, keep as is for free cursor/editing
    if (clean.contains("+")) {
        return clean
    }
    // Pure digits formatting: "198880" -> "198+880", "200000" -> "200+000"
    val digits = clean.filter { it.isDigit() }
    if (digits.length >= 4) {
        val kmPart = digits.substring(0, digits.length - 3)
        val mPart = digits.substring(digits.length - 3)
        return "$kmPart+$mPart"
    }
    return clean
}

fun parseChainageMetres(s: String): Double? {
    val clean = s.trim()
    if (clean.isBlank()) return null
    if (clean.contains("+")) {
        val parts = clean.split("+")
        if (parts.size == 2) {
            val km = parts[0].trim().toDoubleOrNull() ?: return null
            val m = parts[1].trim().toDoubleOrNull() ?: return null
            return (km * 1000.0) + m
        }
    }
    val d = clean.toDoubleOrNull() ?: return null
    return if (d >= 1000 && !clean.contains(".")) d else d
}

fun calculateChainageDiffMetres(from: String, to: String): Double {
    val f = parseChainageMetres(from) ?: return 0.0
    val t = parseChainageMetres(to) ?: return 0.0
    return abs(t - f)
}

// -------------------------------------------------------------
// HELPER: SMART SCAN BITMAP ENHANCEMENT & PROCESSING
// -------------------------------------------------------------
fun applySmartScanFilter(src: Bitmap, mode: String): Bitmap {
    val w = src.width
    val h = src.height
    val dest = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(dest)
    val paint = Paint().apply { isAntiAlias = true }

    when (mode) {
        "DOCUMENT" -> {
            // Enhanced Document: Boost ink contrast and whiten background
            val cm = ColorMatrix(floatArrayOf(
                1.45f, 0f, 0f, 0f, -32f,
                0f, 1.45f, 0f, 0f, -32f,
                0f, 0f, 1.45f, 0f, -32f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
        }
        "BW" -> {
            // Black & White Scanner Filter
            val cm = ColorMatrix()
            cm.setSaturation(0f)
            val contrast = 1.7f
            val scale = contrast
            val translate = (-0.5f * scale + 0.5f) * 255f
            val cmContrast = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(cmContrast)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
        }
        "MAGIC" -> {
            // Magic color: rich ink contrast & white background
            val cm = ColorMatrix(floatArrayOf(
                1.25f, 0f, 0f, 0f, -15f,
                0f, 1.25f, 0f, 0f, -15f,
                0f, 0f, 1.25f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
        }
        else -> {
            // Original raw photo without any filter
            canvas.drawBitmap(src, 0f, 0f, paint)
        }
    }
    return dest
}

fun rotateScanBitmap(src: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

fun saveScanBitmapToFile(context: Context, bmp: Bitmap): String {
    val dir = File(context.filesDir, "register_scans")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "site_register_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    return file.absolutePath
}

// -------------------------------------------------------------
// MAIN COMPOSABLE: SMART NOTES (SITE ACTIVITY - DPR)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartNotesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Projects State (Sync with CTECS Project Settings & Room)
    val appProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val definedProjects by viewModel.definedProjects.collectAsStateWithLifecycle()

    // Resolve Active Project: Preference from ProjectProfile or allProjects
    var selectedProjectId by remember { mutableStateOf(projectProfile.highwayId.ifBlank { "NH-48" }) }

    // Keep selectedProjectId valid
    val currentProject = remember(selectedProjectId, appProjects, projectProfile) {
        appProjects.find { it.id.equals(selectedProjectId, true) || it.name.equals(selectedProjectId, true) }
            ?: HighwayProjectEntity(
                id = selectedProjectId,
                name = if (projectProfile.highwayId.isNotBlank()) projectProfile.highwayId else "Highway Project $selectedProjectId",
                clientName = "National Highways Authority",
                contractorName = projectProfile.packageSection.ifBlank { "Highway Infra" },
                startChainage = "0+000",
                endChainage = "50+000",
                totalLengthKm = 50.0
            )
    }

    // 2. Active Date State (Default Today: YYYY-MM-DD)
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedDateString by remember { mutableStateOf(todayStr) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // 3. Database Activities for active project & date
    val activitiesForDate by viewModel.getActivitiesForProjectAndDate(selectedProjectId, selectedDateString)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 4. Live GPS / Route for Dynamic LHS / RHS
    val liveMatchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()

    // Resolved Auto Side
    val detectedSideFromRoute = remember(liveMatchResult, activeRoute) {
        if (activeRoute != null && liveMatchResult.confidence != MatchConfidence.NO_MATCH) {
            if (liveMatchResult.detectedSide.equals("LHS", true)) "LHS"
            else if (liveMatchResult.detectedSide.equals("RHS", true)) "RHS"
            else "LHS"
        } else {
            null // Route or GPS not available
        }
    }

    // 5. Activity Entry Form State
    var isEditingActivity by remember { mutableStateOf(false) }
    var editingActivityId by remember { mutableStateOf<Long?>(null) }

    // Main Chainage & Activity
    var fromChainage by remember { mutableStateOf("198+500") }
    var toChainage by remember { mutableStateOf("198+880") }
    var selectedActivity by remember { mutableStateOf("BC") }
    var customActivityText by remember { mutableStateOf("") }
    var activityWidthM by remember { mutableStateOf("7.0") }

    // Sub Activity
    var selectedSubActivity by remember { mutableStateOf("None") }
    var customSubActivityText by remember { mutableStateOf("") }
    var subFromChainage by remember { mutableStateOf("") }
    var subToChainage by remember { mutableStateOf("") }
    var subWidthM by remember { mutableStateOf("") }
    var subComments by remember { mutableStateOf("") }

    // Main Details
    var selectedSide by remember { mutableStateOf("Auto") } // Auto, LHS, RHS, BHS
    var selectedRoadLocation by remember { mutableStateOf("I/L") } // O/L, I/L, Shoulder, Median, MCW, None, Manually Enter
    var customRoadLocationText by remember { mutableStateOf("") }
    var mainComments by remember { mutableStateOf("") }

    // Auto calculate lengths
    val calculatedMainLengthM = remember(fromChainage, toChainage) {
        calculateChainageDiffMetres(fromChainage, toChainage)
    }
    val calculatedSubLengthM = remember(subFromChainage, subToChainage) {
        calculateChainageDiffMetres(subFromChainage, subToChainage)
    }

    // 6. Selected Export Checkboxes
    val selectedActivityIds = remember { mutableStateListOf<Long>() }

    // 7. Dialog States
    var showProjectSwitcherDialog by remember { mutableStateOf(false) }
    var showDprExportDialog by remember { mutableStateOf(false) }
    var dprExportMode by remember { mutableStateOf("ALL") } // "SINGLE", "ALL", "SELECTED"
    var singleActivityToExport by remember { mutableStateOf<SmartActivityEntity?>(null) }
    var activityToDelete by remember { mutableStateOf<SmartActivityEntity?>(null) }

    // 8. Custom Logo & Signature State
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
    var dprCustomLogoUri by remember { mutableStateOf<String?>(overlayConfig.logoUri) }
    var dprPreparedByName by remember { mutableStateOf(projectProfile.inspectorName.ifBlank { "Site Engineer" }) }
    var signatureBitmapState by remember { mutableStateOf<Bitmap?>(null) }

    // Load saved signature on startup
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val sigFile = File(context.filesDir, "dpr_signature.png")
            if (sigFile.exists()) {
                val b = BitmapFactory.decodeFile(sigFile.absolutePath)
                if (b != null) signatureBitmapState = b
            }
        }
    }

    // Image Picker for DPR Custom Logo
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            dprCustomLogoUri = uri.toString()
            Toast.makeText(context, "DPR Logo Updated", Toast.LENGTH_SHORT).show()
        }
    }

    // Site Register Photo State (Optional without timestamp or metadata, with Smart Scan)
    var siteRegisterPhotoPath by remember { mutableStateOf("") }
    var showSmartScanDialog by remember { mutableStateOf(false) }
    var showFullPhotoViewer by remember { mutableStateOf(false) }
    var viewingPhotoPath by remember { mutableStateOf("") }
    var scanOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tempCameraCaptureFile by remember { mutableStateOf<File?>(null) }

    val takeRegisterPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraCaptureFile != null && tempCameraCaptureFile!!.exists()) {
            val path = tempCameraCaptureFile!!.absolutePath
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) {
                scanOriginalBitmap = bmp
                showSmartScanDialog = true
            }
        }
    }

    val pickRegisterPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        scanOriginalBitmap = bmp
                        showSmartScanDialog = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun triggerRegisterCameraCapture() {
        try {
            val cacheFile = File(context.cacheDir, "site_register_raw_${System.currentTimeMillis()}.jpg")
            tempCameraCaptureFile = cacheFile
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
            takeRegisterPhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Reset entry form function
    fun resetEntryForm() {
        isEditingActivity = false
        editingActivityId = null
        selectedActivity = "BC"
        customActivityText = ""
        activityWidthM = "7.0"
        selectedSubActivity = "None"
        customSubActivityText = ""
        subFromChainage = ""
        subToChainage = ""
        subWidthM = ""
        subComments = ""
        selectedSide = "Auto"
        selectedRoadLocation = "I/L"
        customRoadLocationText = ""
        mainComments = ""
        siteRegisterPhotoPath = ""
    }

    // Populate form for editing
    fun populateFormForEdit(act: SmartActivityEntity) {
        isEditingActivity = true
        editingActivityId = act.id
        fromChainage = act.fromChainage
        toChainage = act.toChainage

        // Activity dropdown
        val stdActivities = listOf("EMB", "SG", "GSB", "WMM", "DBM", "BC", "DLC", "PQC", "Tack Coat", "Prime Coat", "Milling", "Other", "None")
        if (act.activityLayer.isBlank()) {
            selectedActivity = "None"
            customActivityText = ""
        } else if (stdActivities.contains(act.activityLayer)) {
            selectedActivity = act.activityLayer
            customActivityText = ""
        } else {
            selectedActivity = "Manually Enter"
            customActivityText = act.activityLayer
        }

        activityWidthM = if (act.widthM > 0) "${act.widthM}" else ""

        // Sub activity
        val stdSub = listOf("Glass Grid", "Geotextile", "Crack Sealing", "Tack Coat", "Prime Coat", "Milling", "Kerb / Drain", "Other", "None")
        if (act.subActivityName.isBlank()) {
            selectedSubActivity = "None"
            customSubActivityText = ""
        } else if (stdSub.contains(act.subActivityName)) {
            selectedSubActivity = act.subActivityName
            customSubActivityText = ""
        } else {
            selectedSubActivity = "Manually Enter"
            customSubActivityText = act.subActivityName
        }

        subFromChainage = act.subFromChainage
        subToChainage = act.subToChainage
        subWidthM = if (act.subWidthM > 0) "${act.subWidthM}" else ""
        subComments = act.subComments

        // Side
        selectedSide = act.sideMode.ifBlank { act.side }

        // Road Location
        val stdLocs = listOf("O/L", "I/L", "Shoulder", "Median", "MCW", "None")
        if (act.roadSection.isBlank()) {
            selectedRoadLocation = "None"
            customRoadLocationText = ""
        } else if (stdLocs.contains(act.roadSection)) {
            selectedRoadLocation = act.roadSection
            customRoadLocationText = ""
        } else {
            selectedRoadLocation = "Manually Enter"
            customRoadLocationText = act.roadSection
        }

        mainComments = act.siteObservations.ifBlank { act.remarks }
        siteRegisterPhotoPath = act.photoUris
    }

    BackHandler {
        if (isEditingActivity) {
            resetEntryForm()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { showProjectSwitcherDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SITE ACTIVITY – DPR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = currentProject.id,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = currentProject.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Switch Project
                    IconButton(onClick = { showProjectSwitcherDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Projects")
                    }
                    // All Day DPR Export
                    IconButton(onClick = {
                        dprExportMode = "ALL"
                        showDprExportDialog = true
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "DPR Export", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ---------------------------------------------------------
            // 1. MINIMALIST EXPORT TOOLBAR (Single, All Day, Selected)
            // ---------------------------------------------------------
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date Navigation
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                cal.time = sdf.parse(selectedDateString) ?: Date()
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                                selectedDateString = sdf.format(cal.time)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Day", modifier = Modifier.size(20.dp))
                        }

                        val formattedHeaderDate = remember(selectedDateString) {
                            try {
                                val inSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val outSdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
                                outSdf.format(inSdf.parse(selectedDateString) ?: Date())
                            } catch (e: Exception) {
                                selectedDateString
                            }
                        }
                        Text(
                            text = formattedHeaderDate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { showDatePickerDialog = true }
                        )

                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                cal.time = sdf.parse(selectedDateString) ?: Date()
                                cal.add(Calendar.DAY_OF_YEAR, 1)
                                selectedDateString = sdf.format(cal.time)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Export Toolbar Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Selected Export
                        OutlinedButton(
                            onClick = {
                                if (selectedActivityIds.isEmpty()) {
                                    Toast.makeText(context, "Select at least 1 activity using checkboxes below", Toast.LENGTH_SHORT).show()
                                } else {
                                    dprExportMode = "SELECTED"
                                    showDprExportDialog = true
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Selected (${selectedActivityIds.size})", fontSize = 11.sp)
                        }

                        // All Day Export
                        Button(
                            onClick = {
                                dprExportMode = "ALL"
                                showDprExportDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All Day DPR", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // 2. SIMPLE ACTIVITY ENTRY UI (Fast, Minimalist)
            // ---------------------------------------------------------
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header of form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditingActivity) "EDIT SITE ACTIVITY" else "NEW SITE ACTIVITY ENTRY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isEditingActivity) {
                            TextButton(
                                onClick = { resetEntryForm() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }

                    // A. MAIN CHAINAGE: From, To, Auto-calculation & Length
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = fromChainage,
                            onValueChange = { fromChainage = formatHighwayChainageInput(it) },
                            label = { Text("From Ch. (e.g. 198+500)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toChainage,
                            onValueChange = { toChainage = formatHighwayChainageInput(it) },
                            label = { Text("To Ch. (e.g. 198+880)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Length Display
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Calculated Length:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${calculatedMainLengthM.toInt()} metres",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // B. ACTIVITY DROPDOWN & WIDTH
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Activity Dropdown
                        Box(modifier = Modifier.weight(1.3f)) {
                            var expandedAct by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = selectedActivity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Activity") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (expandedAct) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedAct = !expandedAct }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedAct = true }
                            )
                            DropdownMenu(
                                expanded = expandedAct,
                                onDismissRequest = { expandedAct = false }
                            ) {
                                val actList = listOf(
                                    "EMB", "SG", "GSB", "WMM", "DBM", "BC",
                                    "DLC", "PQC", "Tack Coat", "Prime Coat", "Milling", "Other", "None", "Manually Enter"
                                )
                                actList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            selectedActivity = item
                                            expandedAct = false
                                        }
                                    )
                                }
                            }
                        }

                        // Width (m)
                        OutlinedTextField(
                            value = activityWidthM,
                            onValueChange = { activityWidthM = it },
                            label = { Text("Width (m)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    // Custom Activity input if "Manually Enter" or "Other"
                    if (selectedActivity == "Manually Enter" || selectedActivity == "Other") {
                        OutlinedTextField(
                            value = customActivityText,
                            onValueChange = { customActivityText = it },
                            label = { Text("Enter Custom Activity Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // C. SUB ACTIVITY (Collapsible / Optional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.3f)) {
                            var expandedSub by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = selectedSubActivity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sub Activity") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (expandedSub) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedSub = !expandedSub }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSub = true }
                            )
                            DropdownMenu(
                                expanded = expandedSub,
                                onDismissRequest = { expandedSub = false }
                            ) {
                                val subList = listOf(
                                    "Glass Grid", "Geotextile", "Crack Sealing",
                                    "Tack Coat", "Prime Coat", "Milling", "Kerb / Drain", "Other", "None", "Manually Enter"
                                )
                                subList.forEach { sItem ->
                                    DropdownMenuItem(
                                        text = { Text(sItem) },
                                        onClick = {
                                            selectedSubActivity = sItem
                                            expandedSub = false
                                        }
                                    )
                                }
                            }
                        }

                        // Sub Width
                        OutlinedTextField(
                            value = subWidthM,
                            onValueChange = { subWidthM = it },
                            label = { Text("Sub W (m)") },
                            enabled = selectedSubActivity != "None",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    if (selectedSubActivity == "Manually Enter" || selectedSubActivity == "Other") {
                        OutlinedTextField(
                            value = customSubActivityText,
                            onValueChange = { customSubActivityText = it },
                            label = { Text("Enter Custom Sub-Activity Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Sub Activity From/To & Length (if not None)
                    if (selectedSubActivity != "None") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = subFromChainage,
                                onValueChange = { subFromChainage = formatHighwayChainageInput(it) },
                                label = { Text("Sub From Ch.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = subToChainage,
                                onValueChange = { subToChainage = formatHighwayChainageInput(it) },
                                label = { Text("Sub To Ch.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (calculatedSubLengthM > 0) {
                            Text(
                                text = "Sub-Activity Length: ${calculatedSubLengthM.toInt()} m",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedTextField(
                            value = subComments,
                            onValueChange = { subComments = it },
                            label = { Text("Sub Activity Comments") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // D. MAIN ACTIVITY DETAILS: Side & Road Location
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Side dropdown: Auto, LHS, RHS, BHS
                        Box(modifier = Modifier.weight(1f)) {
                            var expandedSide by remember { mutableStateOf(false) }
                            val effectiveSideDisplay = if (selectedSide == "Auto") {
                                if (detectedSideFromRoute != null) "Auto ($detectedSideFromRoute)" else "Auto (Select Side)"
                            } else selectedSide

                            OutlinedTextField(
                                value = effectiveSideDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Side") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedSide = !expandedSide }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSide = true }
                            )
                            DropdownMenu(
                                expanded = expandedSide,
                                onDismissRequest = { expandedSide = false }
                            ) {
                                listOf("Auto", "LHS", "RHS", "BHS").forEach { sideOption ->
                                    DropdownMenuItem(
                                        text = {
                                            val label = if (sideOption == "Auto" && detectedSideFromRoute != null) {
                                                "Auto (Detected: $detectedSideFromRoute)"
                                            } else sideOption
                                            Text(label)
                                        },
                                        onClick = {
                                            selectedSide = sideOption
                                            expandedSide = false
                                        }
                                    )
                                }
                            }
                        }

                        // Road Location dropdown: O/L, I/L, Shoulder, Median, MCW, None, Manually Enter
                        Box(modifier = Modifier.weight(1f)) {
                            var expandedLoc by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = selectedRoadLocation,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Road Location") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedLoc = !expandedLoc }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedLoc = true }
                            )
                            DropdownMenu(
                                expanded = expandedLoc,
                                onDismissRequest = { expandedLoc = false }
                            ) {
                                listOf("O/L", "I/L", "Shoulder", "Median", "MCW", "None", "Manually Enter").forEach { locOpt ->
                                    DropdownMenuItem(
                                        text = { Text(locOpt) },
                                        onClick = {
                                            selectedRoadLocation = locOpt
                                            expandedLoc = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedRoadLocation == "Manually Enter") {
                        OutlinedTextField(
                            value = customRoadLocationText,
                            onValueChange = { customRoadLocationText = it },
                            label = { Text("Enter Custom Road Location") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Main Comments
                    OutlinedTextField(
                        value = mainComments,
                        onValueChange = { mainComments = it },
                        label = { Text("Main Comments (Observations & Remarks)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // -------------------------------------------------------------
                    // SITE REGISTER PHOTO (OPTIONAL - WITHOUT ANY TIMESTAMP OR METADATA)
                    // WITH SMART SCAN FEATURE
                    // -------------------------------------------------------------
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = "Site Register Photo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "Optional • No Metadata",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (siteRegisterPhotoPath.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF16A34A).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "✓ Scanned & Attached",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Capture a clean document photo of the site inspection book, level sheet, or material register. Saved in original quality without any timestamp or watermark overlays.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (siteRegisterPhotoPath.isBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { triggerRegisterCameraCapture() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1.1f)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Take Register Photo", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            pickRegisterPhotoLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(0.9f)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pick Image", fontSize = 11.sp)
                                    }
                                }
                            } else {
                                // Thumbnail and Smart Scan Tools Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.DarkGray)
                                            .clickable {
                                                viewingPhotoPath = siteRegisterPhotoPath
                                                showFullPhotoViewer = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val thumb = remember(siteRegisterPhotoPath) {
                                            BitmapFactory.decodeFile(siteRegisterPhotoPath)
                                        }
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb.asImageBitmap(),
                                                contentDescription = "Site Register Thumbnail",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Site Register Photo",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Clean Document • Tap to View",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        // Smart Scan Filter & Enhance
                                        IconButton(
                                            onClick = {
                                                val bmp = BitmapFactory.decodeFile(siteRegisterPhotoPath)
                                                if (bmp != null) {
                                                    scanOriginalBitmap = bmp
                                                    showSmartScanDialog = true
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = "Smart Scan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                        }

                                        // View Fullscreen
                                        IconButton(
                                            onClick = {
                                                viewingPhotoPath = siteRegisterPhotoPath
                                                showFullPhotoViewer = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = "View Photo", modifier = Modifier.size(17.dp))
                                        }

                                        // Retake
                                        IconButton(
                                            onClick = { triggerRegisterCameraCapture() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Retake", modifier = Modifier.size(17.dp))
                                        }

                                        // Delete / Remove
                                        IconButton(
                                            onClick = { siteRegisterPhotoPath = "" },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons: Save Activity & Add Another
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Save Activity logic
                                val effectiveActivityName = when (selectedActivity) {
                                    "None" -> ""
                                    "Manually Enter", "Other" -> customActivityText.trim()
                                    else -> selectedActivity.trim()
                                }

                                val effectiveSubName = when (selectedSubActivity) {
                                    "None" -> ""
                                    "Manually Enter", "Other" -> customSubActivityText.trim()
                                    else -> selectedSubActivity.trim()
                                }

                                val effectiveLocation = when (selectedRoadLocation) {
                                    "None" -> ""
                                    "Manually Enter" -> customRoadLocationText.trim()
                                    else -> selectedRoadLocation.trim()
                                }

                                val finalSide: String
                                val isAuto: Boolean
                                if (selectedSide == "Auto") {
                                    finalSide = detectedSideFromRoute ?: "LHS"
                                    isAuto = detectedSideFromRoute != null
                                } else {
                                    finalSide = selectedSide
                                    isAuto = false
                                }

                                val finalAct = SmartActivityEntity(
                                    id = editingActivityId ?: 0,
                                    projectId = currentProject.id,
                                    projectName = currentProject.name,
                                    dateString = selectedDateString,
                                    timestamp = System.currentTimeMillis(),
                                    activityLayer = effectiveActivityName,
                                    fromChainage = fromChainage.trim(),
                                    toChainage = toChainage.trim(),
                                    completedLengthM = calculatedMainLengthM,
                                    widthM = activityWidthM.toDoubleOrNull() ?: 0.0,
                                    side = finalSide,
                                    sideMode = selectedSide,
                                    isSideAutoDetected = isAuto,
                                    roadSection = effectiveLocation,
                                    siteObservations = mainComments.trim(),
                                    subActivityName = effectiveSubName,
                                    subFromChainage = subFromChainage.trim(),
                                    subToChainage = subToChainage.trim(),
                                    subLengthM = calculatedSubLengthM,
                                    subWidthM = subWidthM.toDoubleOrNull() ?: 0.0,
                                    subComments = subComments.trim(),
                                    photoUris = siteRegisterPhotoPath.trim()
                                )

                                scope.launch {
                                    viewModel.saveSmartActivity(finalAct)
                                    Toast.makeText(context, if (isEditingActivity) "Activity Updated" else "Activity Saved", Toast.LENGTH_SHORT).show()
                                    resetEntryForm()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEditingActivity) "Update Activity" else "Save Activity")
                        }

                        if (!isEditingActivity) {
                            OutlinedButton(
                                onClick = {
                                    resetEntryForm()
                                    Toast.makeText(context, "Form Cleared for Next Entry", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Another")
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // 3. DAILY SAVED ACTIVITIES LIST (Chronological Order)
            // ---------------------------------------------------------
            Text(
                text = "SAVED ACTIVITIES FOR ${selectedDateString} (${activitiesForDate.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (activitiesForDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No activities logged for $selectedDateString yet.\nEnter details above and tap Save Activity.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                activitiesForDate.forEachIndexed { index, act ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top Row: Checkbox, Name, Chainage, and Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedActivityIds.contains(act.id),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedActivityIds.add(act.id)
                                            else selectedActivityIds.remove(act.id)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )

                                    val actTitle = act.activityLayer.ifBlank { "Activity #${index + 1}" }
                                    Text(
                                        text = actTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val chStr = if (act.fromChainage.isNotBlank() && act.toChainage.isNotBlank()) {
                                        "${act.fromChainage} → ${act.toChainage}"
                                    } else act.fromChainage.ifBlank { act.toChainage }
                                    if (chStr.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                text = chStr,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick Actions: Single Export, Edit, Delete
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            singleActivityToExport = act
                                            dprExportMode = "SINGLE"
                                            showDprExportDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Single", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                    }

                                    IconButton(
                                        onClick = { populateFormForEdit(act) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Activity", modifier = Modifier.size(17.dp))
                                    }

                                    IconButton(
                                        onClick = { activityToDelete = act },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Activity", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }

                            // Details Row: Length, Width, Side, Road Location
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (act.completedLengthM > 0) {
                                    Text("L: ${act.completedLengthM.toInt()}m", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                if (act.widthM > 0) {
                                    Text("W: ${act.widthM}m", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                val sideLabel = when {
                                    act.isSideAutoDetected -> "${act.side} (Auto)"
                                    act.side.isNotBlank() && !act.side.equals("None", true) -> "${act.side} (Manual)"
                                    else -> ""
                                }
                                if (sideLabel.isNotBlank()) {
                                    Text("Side: $sideLabel", fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.SemiBold)
                                }
                                if (act.roadSection.isNotBlank() && !act.roadSection.equals("None", true)) {
                                    Text("Loc: ${act.roadSection}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Sub Activity Details if any
                            if (act.subActivityName.isNotBlank() && !act.subActivityName.equals("None", true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Sub: ${act.subActivityName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    if (act.subFromChainage.isNotBlank() && act.subToChainage.isNotBlank()) {
                                        Text("${act.subFromChainage} → ${act.subToChainage}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (act.subLengthM > 0) {
                                        Text("${act.subLengthM.toInt()}m", fontSize = 10.sp)
                                    }
                                }
                            }

                            // Observations / Comments
                            val obs = act.siteObservations.ifBlank { act.remarks }
                            if (obs.isNotBlank() && !obs.equals("None", true)) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Obs: $obs",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Site Register Photo Attached Badge
                            if (act.photoUris.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable {
                                        viewingPhotoPath = act.photoUris
                                        showFullPhotoViewer = true
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("Site Register Scanned (Tap to View)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // -------------------------------------------------------------
    // DIALOG: PROJECT SWITCHER (CTECS Project Settings Integration)
    // -------------------------------------------------------------
    if (showProjectSwitcherDialog) {
        AlertDialog(
            onDismissRequest = { showProjectSwitcherDialog = false },
            title = { Text("Select Highway Project", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val combinedProjects = remember(appProjects, definedProjects, projectProfile) {
                        val set = mutableSetOf<String>()
                        val list = mutableListOf<HighwayProjectEntity>()
                        if (projectProfile.highwayId.isNotBlank()) {
                            set.add(projectProfile.highwayId)
                            list.add(HighwayProjectEntity(id = projectProfile.highwayId, name = projectProfile.highwayId, clientName = "NHAI", contractorName = projectProfile.packageSection))
                        }
                        definedProjects.forEach { dp ->
                            if (!set.contains(dp.name)) {
                                set.add(dp.name)
                                list.add(HighwayProjectEntity(id = dp.name, name = dp.name, clientName = "NHAI", contractorName = dp.stretch))
                            }
                        }
                        appProjects.forEach { p ->
                            if (!set.contains(p.id)) {
                                set.add(p.id)
                                list.add(p)
                            }
                        }
                        list
                    }

                    combinedProjects.forEach { prj ->
                        val isSel = prj.id.equals(selectedProjectId, true) || prj.name.equals(selectedProjectId, true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProjectId = prj.id
                                    resetEntryForm()
                                    showProjectSwitcherDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(prj.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("ID: ${prj.id} • ${prj.contractorName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isSel) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectSwitcherDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: DATE PICKER POPUP
    // -------------------------------------------------------------
    if (showDatePickerDialog) {
        val today = Calendar.getInstance()
        var tempMonth by remember { mutableStateOf(today.clone() as Calendar) }

        AlertDialog(
            onDismissRequest = { showDatePickerDialog = false },
            title = { Text("Select Date", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val c = tempMonth.clone() as Calendar
                            c.add(Calendar.MONTH, -1)
                            tempMonth = c
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                        }
                        Text(monthFormat.format(tempMonth.time), fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            val c = tempMonth.clone() as Calendar
                            c.add(Calendar.MONTH, 1)
                            tempMonth = c
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }

                    // 7 days header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                            Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val daysGrid = remember(tempMonth) {
                        val cal = tempMonth.clone() as Calendar
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        val maxD = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
                        val list = mutableListOf<String?>()
                        for (i in 1 until firstDow) list.add(null)
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        for (d in 1..maxD) {
                            cal.set(Calendar.DAY_OF_MONTH, d)
                            list.add(sdf.format(cal.time))
                        }
                        list
                    }

                    daysGrid.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            week.forEach { dStr ->
                                if (dStr == null) {
                                    Box(modifier = Modifier.size(32.dp))
                                } else {
                                    val isSel = dStr == selectedDateString
                                    val dayNum = dStr.substringAfterLast("-").toInt()
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable {
                                                selectedDateString = dStr
                                                showDatePickerDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Close") }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: CONFIRM DELETE ACTIVITY
    // -------------------------------------------------------------
    activityToDelete?.let { act ->
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = { Text("Delete Activity Entry?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete activity ${act.activityLayer} (${act.fromChainage} → ${act.toChainage})? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.deleteSmartActivity(act)
                            selectedActivityIds.remove(act.id)
                            activityToDelete = null
                            Toast.makeText(context, "Activity Deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { activityToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: DPR EXPORT, CUSTOM LOGO & SIGNATURE PAD
    // -------------------------------------------------------------
    if (showDprExportDialog) {
        val exportList = remember(dprExportMode, singleActivityToExport, activitiesForDate, selectedActivityIds) {
            when (dprExportMode) {
                "SINGLE" -> listOfNotNull(singleActivityToExport)
                "SELECTED" -> activitiesForDate.filter { selectedActivityIds.contains(it.id) }
                else -> activitiesForDate
            }
        }

        var showSignaturePad by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDprExportDialog = false },
            title = {
                Text(
                    text = when (dprExportMode) {
                        "SINGLE" -> "Single Activity DPR Export"
                        "SELECTED" -> "Selected Activities DPR Export (${exportList.size})"
                        else -> "All Day DPR Export (${exportList.size})"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Project: ${currentProject.name} • Date: $selectedDateString", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

                    // User Name (Prepared By)
                    OutlinedTextField(
                        value = dprPreparedByName,
                        onValueChange = { dprPreparedByName = it },
                        label = { Text("Prepared By (Display Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Custom Logo Section
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DPR Custom Logo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (dprCustomLogoUri != null) "Custom Logo Active" else "No logo configured",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (dprCustomLogoUri != null) {
                                    OutlinedButton(
                                        onClick = { dprCustomLogoUri = null },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Remove", fontSize = 10.sp)
                                    }
                                }
                                Button(
                                    onClick = { logoPickerLauncher.launch("image/*") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(if (dprCustomLogoUri != null) "Change" else "Select Logo", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Signature Pad Section
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Inspector Signature", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (signatureBitmapState != null) {
                                    Text("✓ Signature Saved", fontSize = 10.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Optional (Leave Blank if None)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showSignaturePad = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (signatureBitmapState != null) "Re-draw Signature" else "Draw Signature", fontSize = 11.sp)
                                }
                                if (signatureBitmapState != null) {
                                    OutlinedButton(
                                        onClick = {
                                            signatureBitmapState = null
                                            File(context.filesDir, "dpr_signature.png").delete()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Activities Preview Summary
                    Text(
                        text = "Activities to Export (${exportList.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    exportList.forEachIndexed { i, eAct ->
                        Text(
                            text = "${i + 1}. ${eAct.activityLayer.ifBlank { "Activity" }} (${eAct.fromChainage} → ${eAct.toChainage}) • ${eAct.completedLengthM.toInt()}m",
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val logoBitmap = withContext(Dispatchers.IO) {
                                    DprPdfGenerator.loadBitmapFromUri(context, dprCustomLogoUri)
                                }

                                val pdfFile = withContext(Dispatchers.IO) {
                                    DprPdfGenerator.generateDprPdf(
                                        context = context,
                                        project = currentProject,
                                        dateString = selectedDateString,
                                        activities = exportList,
                                        preparedBy = dprPreparedByName,
                                        customLogoBitmap = logoBitmap,
                                        signatureBitmap = signatureBitmapState
                                    )
                                }

                                showDprExportDialog = false
                                Toast.makeText(context, "DPR PDF Generated: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                                DprPdfGenerator.shareDprPdf(context, pdfFile)
                            } catch (e: Exception) {
                                Toast.makeText(context, "PDF Generation error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate & Share PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDprExportDialog = false }) { Text("Cancel") }
            }
        )

        // Signature Drawing Modal
        if (showSignaturePad) {
            SignatureDrawingDialog(
                onDismiss = { showSignaturePad = false },
                onSaveSignature = { drawnBitmap ->
                    signatureBitmapState = drawnBitmap
                    showSignaturePad = false
                    // Save to persistent file
                    scope.launch(Dispatchers.IO) {
                        try {
                            val f = File(context.filesDir, "dpr_signature.png")
                            FileOutputStream(f).use { out ->
                                drawnBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        } catch (e: Exception) {
                            // Ignore save error
                        }
                    }
                    Toast.makeText(context, "Signature Saved", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Smart Scan Document Editor Dialog
        if (showSmartScanDialog && scanOriginalBitmap != null) {
            SmartScanEditorDialog(
                originalBitmap = scanOriginalBitmap!!,
                onDismiss = { showSmartScanDialog = false },
                onSaveScanned = { processedBmp ->
                    val savedPath = saveScanBitmapToFile(context, processedBmp)
                    siteRegisterPhotoPath = savedPath
                    showSmartScanDialog = false
                    Toast.makeText(context, "Site Register Photo Attached", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Fullscreen Register Document Viewer
        if (showFullPhotoViewer && viewingPhotoPath.isNotBlank()) {
            FullPhotoViewerDialog(
                photoPath = viewingPhotoPath,
                onDismiss = { showFullPhotoViewer = false }
            )
        }
    }
}

// -------------------------------------------------------------
// SIGNATURE DRAWING PAD DIALOG
// -------------------------------------------------------------
@Composable
fun SignatureDrawingDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (Bitmap) -> Unit
) {
    var strokeLines by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentStroke by remember { mutableStateOf(listOf<Offset>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Signature", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sign with your finger or stylus in the box below:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // White canvas area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    currentStroke = listOf(startOffset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokeLines = strokeLines + listOf(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw existing completed strokes
                        strokeLines.forEach { stroke ->
                            for (i in 0 until stroke.size - 1) {
                                drawLine(
                                    color = Color.Black,
                                    start = stroke[i],
                                    end = stroke[i + 1],
                                    strokeWidth = 3.5f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        // Draw active dragging stroke
                        for (i in 0 until currentStroke.size - 1) {
                            drawLine(
                                color = Color.Black,
                                start = currentStroke[i],
                                end = currentStroke[i + 1],
                                strokeWidth = 3.5f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        strokeLines = emptyList()
                        currentStroke = emptyList()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Canvas", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (strokeLines.isEmpty()) {
                        onDismiss()
                    } else {
                        // Render strokes onto Bitmap
                        val w = 400
                        val h = 180
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)

                        val p = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            strokeWidth = 4f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        strokeLines.forEach { stroke ->
                            if (stroke.isNotEmpty()) {
                                val path = android.graphics.Path()
                                path.moveTo(stroke.first().x, stroke.first().y)
                                for (i in 1 until stroke.size) {
                                    path.lineTo(stroke[i].x, stroke[i].y)
                                }
                                canvas.drawPath(path, p)
                            }
                        }

                        onSaveSignature(bmp)
                    }
                }
            ) {
                Text("Save Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -------------------------------------------------------------
// SMART SCAN DOCUMENT EDITOR DIALOG
// -------------------------------------------------------------
@Composable
fun SmartScanEditorDialog(
    originalBitmap: Bitmap,
    onDismiss: () -> Unit,
    onSaveScanned: (Bitmap) -> Unit
) {
    var selectedFilterMode by remember { mutableStateOf("DOCUMENT") }
    var rotationDegrees by remember { mutableStateOf(0f) }
    var currentProcessedBmp by remember(selectedFilterMode, rotationDegrees) {
        mutableStateOf(
            run {
                val rotated = if (rotationDegrees % 360f != 0f) rotateScanBitmap(originalBitmap, rotationDegrees) else originalBitmap
                applySmartScanFilter(rotated, selectedFilterMode)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Smart Document Scanner", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enhance readability for site register, level sheet, or inspection logs without watermarks or timestamps.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // High resolution preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = currentProcessedBmp.asImageBitmap(),
                        contentDescription = "Scanned Register Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Filter preset selector pills
                Text("Scan Filter Presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf(
                        "DOCUMENT" to "Document",
                        "BW" to "B&W Scan",
                        "MAGIC" to "Magic Color",
                        "ORIGINAL" to "Original"
                    )
                    filters.forEach { (modeKey, label) ->
                        val isSelected = selectedFilterMode == modeKey
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedFilterMode = modeKey },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                            )
                        }
                    }
                }

                // Tools row (Rotate 90°)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate 90°", fontSize = 11.sp)
                    }

                    Text(
                        text = "Clean scan • No metadata",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveScanned(currentProcessedBmp) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Attach Scanned Photo", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}

// -------------------------------------------------------------
// FULLSCREEN REGISTER DOCUMENT VIEWER
// -------------------------------------------------------------
@Composable
fun FullPhotoViewerDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(photoPath) {
        try {
            BitmapFactory.decodeFile(photoPath)
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Site Register Photo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Full Site Register Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Text(
                        text = "Unable to load image file.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "Clean Document Photo (No Timestamp / Watermark Overlay)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

