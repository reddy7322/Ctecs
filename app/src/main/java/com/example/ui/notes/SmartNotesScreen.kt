package com.example.ui.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Search
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// -------------------------------------------------------------
// SUB ACTIVITY DATA MODEL FOR MULTI SUB-ACTIVITIES
// -------------------------------------------------------------
data class SubActivityData(
    val name: String = "",
    val location: String = "",
    val fromChainage: String = "",
    val toChainage: String = "",
    val lengthM: Double = 0.0,
    val widthM: Double = 0.0,
    val depthM: Double = 0.0,
    val comments: String = "",
    var isExpanded: Boolean = true
) {
    val quantityM3: Double
        get() = if (depthM > 0 && widthM > 0 && lengthM > 0) lengthM * widthM * (depthM / 1000.0) else 0.0
}

// -------------------------------------------------------------
// HELPER: AUTOMATIC CHAINAGE FORMATTER
// -------------------------------------------------------------
fun formatHighwayChainageInput(input: String): String {
    if (input.isBlank()) return input
    val clean = input.replace("+", "").replace(" ", "").trim()
    if (input.contains("+") && !clean.contains(" ")) return input

    val parts = clean.split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) "." + parts[1] else ""

    val digits = integerPart.filter { it.isDigit() }
    if (digits.length >= 4) {
        val prefix = digits.substring(0, digits.length - 3)
        val suffix = digits.substring(digits.length - 3)
        return "$prefix+$suffix$decimalPart"
    }
    return input
}

fun parseChainageMetres(chainageStr: String): Double? {
    val clean = chainageStr.replace("+", "").replace(" ", "").trim()
    if (clean.isBlank()) return null
    if (clean.contains("+")) {
        val parts = clean.split("+")
        if (parts.size == 2) {
            val km = parts[0].toDoubleOrNull() ?: 0.0
            val m = parts[1].toDoubleOrNull() ?: 0.0
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
// SUB ACTIVITIES JSON SERIALIZATION
// -------------------------------------------------------------
fun parseSubActivitiesJson(
    jsonStr: String,
    defaultName: String,
    defaultFrom: String,
    defaultTo: String,
    defaultLen: Double,
    defaultWidth: Double,
    defaultDepth: Double,
    defaultComm: String
): List<SubActivityData> {
    if (jsonStr.startsWith("[")) {
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SubActivityData>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sName = obj.optString("name", "")
                if (sName.isNotBlank() && !sName.equals("None", true)) {
                    list.add(
                        SubActivityData(
                            name = sName,
                            location = obj.optString("location", ""),
                            fromChainage = obj.optString("fromChainage", ""),
                            toChainage = obj.optString("toChainage", ""),
                            lengthM = obj.optDouble("lengthM", 0.0),
                            widthM = obj.optDouble("widthM", 0.0),
                            depthM = if (obj.has("depthM")) {
                                obj.optDouble("depthM", 0.0)
                            } else if (obj.has("depth")) {
                                obj.optDouble("depth", 0.0)
                            } else if (obj.has("depthMm")) {
                                obj.optDouble("depthMm", 0.0)
                            } else 0.0,
                            comments = obj.optString("comments", ""),
                            isExpanded = false
                        )
                    )
                }
            }
            if (list.isNotEmpty()) return list
        } catch (e: Exception) {
            // Fallback
        }
    }

    if (defaultName.isNotBlank() && !defaultName.equals("None", true)) {
        return listOf(
            SubActivityData(
                name = defaultName,
                location = "",
                fromChainage = defaultFrom,
                toChainage = defaultTo,
                lengthM = defaultLen,
                widthM = defaultWidth,
                depthM = defaultDepth,
                comments = if (!defaultComm.startsWith("[")) defaultComm else "",
                isExpanded = false
            )
        )
    }

    return emptyList()
}

fun encodeSubActivitiesJson(subList: List<SubActivityData>): String {
    val array = JSONArray()
    subList.forEach { sub ->
        if (sub.name.isNotBlank() && !sub.name.equals("None", true)) {
            val obj = JSONObject()
            obj.put("name", sub.name)
            obj.put("location", sub.location)
            obj.put("fromChainage", sub.fromChainage)
            obj.put("toChainage", sub.toChainage)
            obj.put("lengthM", sub.lengthM)
            obj.put("widthM", sub.widthM)
            obj.put("depthM", sub.depthM)
            obj.put("comments", sub.comments)
            array.put(obj)
        }
    }
    return array.toString()
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
// HELPER: REAL-TIME ACTIVITY INPUT DRAFT MANAGER (AUTO-SAVE)
// -------------------------------------------------------------
object ActivityDraftManager {
    private const val PREFS_NAME = "ctecs_activity_draft_prefs"
    private const val KEY_HAS_DRAFT = "has_draft"
    private const val KEY_FROM_CH = "from_ch"
    private const val KEY_TO_CH = "to_ch"
    private const val KEY_ACT = "act"
    private const val KEY_CUSTOM_ACT = "custom_act"
    private const val KEY_WIDTH = "width"
    private const val KEY_DEPTH = "depth"
    private const val KEY_SIDE = "side"
    private const val KEY_LOC = "loc"
    private const val KEY_CUSTOM_LOC = "custom_loc"
    private const val KEY_COMMENTS = "comments"
    private const val KEY_PHOTO = "photo"
    private const val KEY_SUBS_JSON = "subs_json"

    fun saveDraft(
        context: Context,
        fromCh: String,
        toCh: String,
        act: String,
        customAct: String,
        width: String,
        depth: String,
        side: String,
        loc: String,
        customLoc: String,
        comments: String,
        photo: String,
        subsJson: String
    ) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_HAS_DRAFT, true)
            .putString(KEY_FROM_CH, fromCh)
            .putString(KEY_TO_CH, toCh)
            .putString(KEY_ACT, act)
            .putString(KEY_CUSTOM_ACT, customAct)
            .putString(KEY_WIDTH, width)
            .putString(KEY_DEPTH, depth)
            .putString(KEY_SIDE, side)
            .putString(KEY_LOC, loc)
            .putString(KEY_CUSTOM_LOC, customLoc)
            .putString(KEY_COMMENTS, comments)
            .putString(KEY_PHOTO, photo)
            .putString(KEY_SUBS_JSON, subsJson)
            .apply()
    }

    fun hasDraft(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_HAS_DRAFT, false)
    }

    data class DraftData(
        val fromCh: String,
        val toCh: String,
        val act: String,
        val customAct: String,
        val width: String,
        val depth: String,
        val side: String,
        val loc: String,
        val customLoc: String,
        val comments: String,
        val photo: String,
        val subsJson: String
    )

    fun loadDraft(context: Context): DraftData? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_HAS_DRAFT, false)) return null
        return DraftData(
            fromCh = sp.getString(KEY_FROM_CH, "") ?: "",
            toCh = sp.getString(KEY_TO_CH, "") ?: "",
            act = sp.getString(KEY_ACT, "BC") ?: "BC",
            customAct = sp.getString(KEY_CUSTOM_ACT, "") ?: "",
            width = sp.getString(KEY_WIDTH, "7.0") ?: "7.0",
            depth = sp.getString(KEY_DEPTH, "") ?: "",
            side = sp.getString(KEY_SIDE, "Auto") ?: "Auto",
            loc = sp.getString(KEY_LOC, "I/L") ?: "I/L",
            customLoc = sp.getString(KEY_CUSTOM_LOC, "") ?: "",
            comments = sp.getString(KEY_COMMENTS, "") ?: "",
            photo = sp.getString(KEY_PHOTO, "") ?: "",
            subsJson = sp.getString(KEY_SUBS_JSON, "[]") ?: "[]"
        )
    }

    fun clearDraft(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
    }
}

// -------------------------------------------------------------
// FULL-PAGE RENDERED PDF PREVIEW DIALOG (PdfRenderer)
// -------------------------------------------------------------
@Composable
fun PdfDocumentPreviewDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPageCount by remember { mutableStateOf(1) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }

    LaunchedEffect(pdfFile, currentPageIndex) {
        withContext(Dispatchers.IO) {
            try {
                isRendering = true
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                totalPageCount = renderer.pageCount.coerceAtLeast(1)
                val safeIndex = currentPageIndex.coerceIn(0, totalPageCount - 1)
                val page = renderer.openPage(safeIndex)

                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    pageBitmap = bmp
                    isRendering = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRendering = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(Color(0xFF0F172A)),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Column {
                                Text("Document Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Page ${currentPageIndex + 1} of $totalPageCount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Page")
                            }
                            IconButton(
                                onClick = { if (currentPageIndex < totalPageCount - 1) currentPageIndex++ },
                                enabled = currentPageIndex < totalPageCount - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRendering) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                            Text("Rendering PDF page preview...", color = Color.White, fontSize = 12.sp)
                        }
                    } else if (pageBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Image(
                                bitmap = pageBitmap!!.asImageBitmap(),
                                contentDescription = "PDF Rendered Page ${currentPageIndex + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text("Could not render page preview", color = Color.White)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }

                        Button(
                            onClick = onShare,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share / Save PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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

    // 1. Projects State
    val appProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()

    var selectedProjectId by remember { mutableStateOf(projectProfile.highwayId.ifBlank { "NH-48" }) }

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

    // 2. Active Date State
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedDateString by remember { mutableStateOf(todayStr) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // 3. Database Activities for active project & date
    val activitiesForDate by viewModel.getActivitiesForProjectAndDate(selectedProjectId, selectedDateString)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 4. Live GPS / Route for Dynamic LHS / RHS
    val liveMatchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()

    val detectedSideFromRoute = remember(liveMatchResult, activeRoute) {
        if (activeRoute != null && liveMatchResult.confidence != MatchConfidence.NO_MATCH) {
            if (liveMatchResult.detectedSide.equals("LHS", true)) "LHS"
            else if (liveMatchResult.detectedSide.equals("RHS", true)) "RHS"
            else "LHS"
        } else {
            null
        }
    }

    // 5. Selected Export Checkboxes
    val selectedActivityIds = remember { mutableStateListOf<Long>() }

    // 6. Dialog States
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showProjectSwitcherDialog by remember { mutableStateOf(false) }
    var showDprExportDialog by remember { mutableStateOf(false) }
    var showWeeklyExportDialog by remember { mutableStateOf(false) }

    var activitySearchQuery by remember { mutableStateOf("") }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var showPdfPreviewModal by remember { mutableStateOf(false) }
    var isAutoSaved by remember { mutableStateOf(false) }

    var dprExportMode by remember { mutableStateOf("ALL") } // "SINGLE", "ALL", "SELECTED"
    var singleActivityToExport by remember { mutableStateOf<SmartActivityEntity?>(null) }
    var activityToDelete by remember { mutableStateOf<SmartActivityEntity?>(null) }

    // Form state variables for Add/Edit Popup
    var isEditingActivity by remember { mutableStateOf(false) }
    var editingActivityId by remember { mutableStateOf<Long?>(null) }
    var fromChainage by remember { mutableStateOf("198+500") }
    var toChainage by remember { mutableStateOf("198+880") }
    var selectedActivity by remember { mutableStateOf("BC") }
    var customActivityText by remember { mutableStateOf("") }
    var activityWidthM by remember { mutableStateOf("7.0") }
    var activityDepthM by remember { mutableStateOf("") }

    // Multi Sub Activity state list
    val subActivitiesList = remember { mutableStateListOf<SubActivityData>() }
    var showSubActivitySection by remember { mutableStateOf(false) }

    // Main Details
    var selectedSide by remember { mutableStateOf("Auto") } // Auto, LHS, RHS, BHS
    var selectedRoadLocation by remember { mutableStateOf("I/L") }
    var customRoadLocationText by remember { mutableStateOf("") }
    var mainComments by remember { mutableStateOf("") }

    // Signature and Logo
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
    var dprCustomLogoUri by remember(overlayConfig) { mutableStateOf<String?>(overlayConfig.pdfLogoUri?.takeIf { it.isNotBlank() } ?: overlayConfig.logoUri) }
    var dprPreparedByName by remember(overlayConfig, projectProfile) { mutableStateOf(overlayConfig.pdfDefaultPreparedBy.ifBlank { projectProfile.inspectorName.ifBlank { "Site Engineer" } }) }
    var signatureBitmapState by remember { mutableStateOf<Bitmap?>(null) }

    // Site Register Photo
    var siteRegisterPhotoPath by remember { mutableStateOf("") }
    var showSmartScanDialog by remember { mutableStateOf(false) }
    var showFullPhotoViewer by remember { mutableStateOf(false) }
    var viewingPhotoPath by remember { mutableStateOf("") }
    var scanOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tempCameraCaptureFile by remember { mutableStateOf<File?>(null) }

    // Load saved signature on startup
    LaunchedEffect(overlayConfig) {
        withContext(Dispatchers.IO) {
            val sigPath = overlayConfig.pdfSignatureUri
            val sigFile = if (!sigPath.isNullOrBlank() && File(sigPath).exists()) File(sigPath) else File(context.filesDir, "dpr_signature.png")
            if (sigFile.exists()) {
                val b = BitmapFactory.decodeFile(sigFile.absolutePath)
                if (b != null) signatureBitmapState = b
            }
        }
    }

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

    fun resetEntryForm() {
        isEditingActivity = false
        editingActivityId = null
        fromChainage = "198+500"
        toChainage = "198+880"
        selectedActivity = "BC"
        customActivityText = ""
        activityWidthM = "7.0"
        activityDepthM = ""
        subActivitiesList.clear()
        showSubActivitySection = false
        selectedSide = "Auto"
        selectedRoadLocation = "I/L"
        customRoadLocationText = ""
        mainComments = ""
        siteRegisterPhotoPath = ""
    }

    fun restoreDraftIfAvailable(): Boolean {
        val draft = ActivityDraftManager.loadDraft(context) ?: return false
        isEditingActivity = false
        editingActivityId = null
        fromChainage = draft.fromCh.ifBlank { "198+500" }
        toChainage = draft.toCh.ifBlank { "198+880" }
        selectedActivity = draft.act.ifBlank { "BC" }
        customActivityText = draft.customAct
        activityWidthM = draft.width.ifBlank { "7.0" }
        activityDepthM = draft.depth
        selectedSide = draft.side.ifBlank { "Auto" }
        selectedRoadLocation = draft.loc.ifBlank { "I/L" }
        customRoadLocationText = draft.customLoc
        mainComments = draft.comments
        siteRegisterPhotoPath = draft.photo

        subActivitiesList.clear()
        val parsedSubs = parseSubActivitiesJson(
            jsonStr = draft.subsJson,
            defaultName = "",
            defaultFrom = "",
            defaultTo = "",
            defaultLen = 0.0,
            defaultWidth = 0.0,
            defaultDepth = 0.0,
            defaultComm = ""
        )
        subActivitiesList.addAll(parsedSubs)
        showSubActivitySection = subActivitiesList.isNotEmpty()
        return true
    }

    LaunchedEffect(
        showAddEditDialog,
        fromChainage, toChainage, selectedActivity, customActivityText,
        activityWidthM, activityDepthM, selectedSide, selectedRoadLocation,
        customRoadLocationText, mainComments, siteRegisterPhotoPath,
        subActivitiesList.toList()
    ) {
        if (showAddEditDialog && !isEditingActivity) {
            val encoded = encodeSubActivitiesJson(subActivitiesList.toList())
            ActivityDraftManager.saveDraft(
                context = context,
                fromCh = fromChainage,
                toCh = toChainage,
                act = selectedActivity,
                customAct = customActivityText,
                width = activityWidthM,
                depth = activityDepthM,
                side = selectedSide,
                loc = selectedRoadLocation,
                customLoc = customRoadLocationText,
                comments = mainComments,
                photo = siteRegisterPhotoPath,
                subsJson = encoded
            )
            isAutoSaved = true
        }
    }

    fun populateFormForEdit(act: SmartActivityEntity) {
        isEditingActivity = true
        editingActivityId = act.id
        fromChainage = act.fromChainage
        toChainage = act.toChainage

        val stdActivities = listOf("EMB", "SG", "GSB", "WMM", "DBM", "BC", "DLC", "PQC", "Milling", "None")
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

        activityWidthM = if (act.widthM > 0) "${if (act.widthM % 1.0 == 0.0) act.widthM.toInt() else act.widthM}" else ""
        val dVal = if (act.depthM > 0) act.depthM else if (act.thicknessMm > 0) act.thicknessMm else 0.0
        activityDepthM = if (dVal > 0) "${if (dVal % 1.0 == 0.0) dVal.toInt() else dVal}" else ""

        // Sub activities
        subActivitiesList.clear()
        val parsedSubs = parseSubActivitiesJson(
            jsonStr = act.subComments,
            defaultName = act.subActivityName,
            defaultFrom = act.subFromChainage,
            defaultTo = act.subToChainage,
            defaultLen = act.subLengthM,
            defaultWidth = act.subWidthM,
            defaultDepth = act.subDepthM,
            defaultComm = act.subComments
        )
        subActivitiesList.addAll(parsedSubs)
        showSubActivitySection = subActivitiesList.isNotEmpty()

        selectedSide = act.sideMode.ifBlank { act.side }

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
        if (showAddEditDialog) {
            showAddEditDialog = false
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
                    IconButton(onClick = { showProjectSwitcherDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Projects")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!restoreDraftIfAvailable()) {
                        resetEntryForm()
                    }
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Activity")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ---------------------------------------------------------
            // 1. DATE TOOLBAR & 4 CLEAR EXPORT BUTTONS
            // ---------------------------------------------------------
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Date Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        // Total Completed Length for Date
                        val dayTotalLengthM = remember(activitiesForDate) {
                            activitiesForDate.sumOf { it.completedLengthM.takeIf { len -> len > 0 } ?: it.quantity }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Total: ${dayTotalLengthM.toInt()}m",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Row 2: Four Export Buttons (Single, All Day, Selected, Weekly)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Single Export Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            onClick = {
                                if (activitiesForDate.isNotEmpty()) {
                                    singleActivityToExport = activitiesForDate.first()
                                    dprExportMode = "SINGLE"
                                    showDprExportDialog = true
                                } else {
                                    Toast.makeText(context, "No activity available to export", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Single", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 2. All Day Export Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (activitiesForDate.isNotEmpty()) {
                                    dprExportMode = "ALL"
                                    showDprExportDialog = true
                                } else {
                                    Toast.makeText(context, "No activities to export for $selectedDateString", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("All Day", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        // 3. Selected Export Button
                        val selCount = selectedActivityIds.size
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selCount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selCount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.outlineVariant),
                            onClick = {
                                if (selCount > 0) {
                                    dprExportMode = "SELECTED"
                                    showDprExportDialog = true
                                } else {
                                    Toast.makeText(context, "Check activities in list to export selected", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (selCount > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (selCount > 0) "Selected ($selCount)" else "Selected",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selCount > 0) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 4. Weekly / Custom Date Export Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2563EB),
                            onClick = { showWeeklyExportDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Weekly", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // 2. SAVED ACTIVITIES LIST ONLY (MAIN SCREEN)
            // ---------------------------------------------------------
            if (activitiesForDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No activities logged for $selectedDateString",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap the + floating button below to log a new highway site activity.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (!restoreDraftIfAvailable()) {
                                    resetEntryForm()
                                }
                                showAddEditDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Site Activity")
                        }
                    }
                }
            } else {
                val filteredActivities = remember(activitiesForDate, activitySearchQuery) {
                    if (activitySearchQuery.isBlank()) activitiesForDate
                    else {
                        val q = activitySearchQuery.trim().lowercase(Locale.ROOT)
                        activitiesForDate.filter { act ->
                            act.activityLayer.lowercase(Locale.ROOT).contains(q) ||
                            act.fromChainage.lowercase(Locale.ROOT).contains(q) ||
                            act.toChainage.lowercase(Locale.ROOT).contains(q) ||
                            act.roadSection.lowercase(Locale.ROOT).contains(q) ||
                            act.side.lowercase(Locale.ROOT).contains(q) ||
                            act.siteObservations.lowercase(Locale.ROOT).contains(q) ||
                            act.remarks.lowercase(Locale.ROOT).contains(q) ||
                            act.subActivityName.lowercase(Locale.ROOT).contains(q) ||
                            act.subComments.lowercase(Locale.ROOT).contains(q)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = activitySearchQuery,
                        onValueChange = { activitySearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Search activities by name or chainage...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (activitySearchQuery.isNotBlank()) {
                                IconButton(onClick = { activitySearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                if (filteredActivities.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No activities matching \"$activitySearchQuery\"",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { activitySearchQuery = "" }) {
                                Text("Clear Search Filter")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredActivities, key = { it.id }) { act ->
                        val isChecked = selectedActivityIds.contains(act.id)
                        val parsedSubs = remember(act) {
                            parseSubActivitiesJson(act.subComments, act.subActivityName, act.subFromChainage, act.subToChainage, act.subLengthM, act.subWidthM, act.subDepthM, "")
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Top Row: Checkbox, Name, Chainage, Edit & Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) selectedActivityIds.add(act.id)
                                                else selectedActivityIds.remove(act.id)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )

                                        val actLabel = act.activityLayer.ifBlank { "BC" }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = actLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        val chText = if (act.fromChainage.isNotBlank() && act.toChainage.isNotBlank()) {
                                            "Ch. ${act.fromChainage} → ${act.toChainage}"
                                        } else "Ch. ${act.fromChainage}"
                                        Text(
                                            text = chText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        // Edit Icon Button
                                        IconButton(
                                            onClick = {
                                                populateFormForEdit(act)
                                                showAddEditDialog = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Activity", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }

                                        // Delete Icon Button
                                        IconButton(
                                            onClick = { activityToDelete = act },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Activity", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                // Details Grid Row: Length, Width, Side, Location
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        val lenM = act.completedLengthM.takeIf { it > 0 } ?: act.quantity
                                        if (lenM > 0) {
                                            Text("Length: ${lenM.toInt()}m", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (act.widthM > 0) {
                                            Text("Width: ${"%.1f".format(Locale.US, act.widthM)}m", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (act.side.isNotBlank() && !act.side.equals("None", true)) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFB45309).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = act.side,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (act.roadSection.isNotBlank() && !act.roadSection.equals("None", true)) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = act.roadSection,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Sub-Activities Summary if any
                                if (parsedSubs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Sub Activities (${parsedSubs.size}): " + parsedSubs.joinToString(", ") { "${it.name} (${it.fromChainage}→${it.toChainage})" },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Comments
                                val obs = act.siteObservations.ifBlank { act.remarks }
                                if (obs.isNotBlank() && !obs.equals("None", true)) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Comments: $obs",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Site Register Photo Scanned Badge
                                if (act.photoUris.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF16A34A).copy(alpha = 0.12f),
                                        modifier = Modifier.clickable {
                                            viewingPhotoPath = act.photoUris
                                            showFullPhotoViewer = true
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "📷 Site Register Scanned (Tap to View)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF16A34A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // FULL-SCREEN ADD / EDIT ACTIVITY POPUP DIALOG
    // -------------------------------------------------------------
    if (showAddEditDialog) {
        var showUnsavedConfirmation by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = {
                if (mainComments.isNotBlank() || subActivitiesList.isNotEmpty()) {
                    showUnsavedConfirmation = true
                } else {
                    showAddEditDialog = false
                    resetEntryForm()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Header Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val availableWidth = maxWidth
                            val isUltraCompact = availableWidth < 360.dp
                            val isCompact = availableWidth < 430.dp
                            val btnHeight = 36.dp
                            val btnSpacing = if (isUltraCompact) 4.dp else 6.dp
                            val btnPaddingHorizontal = if (isUltraCompact) 6.dp else if (isCompact) 8.dp else 12.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (isUltraCompact) 8.dp else 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left side: Back button + Title + Auto-saved badge
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(if (isUltraCompact) 6.dp else 8.dp)
                                ) {
                                    // Materialistic Flat Back Button
                                    Surface(
                                        onClick = {
                                            if (mainComments.isNotBlank() || subActivitiesList.isNotEmpty()) {
                                                showUnsavedConfirmation = true
                                            } else {
                                                showAddEditDialog = false
                                                resetEntryForm()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.size(btnHeight)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Close",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (isEditingActivity) "Edit Activity" else "Add Activity",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isUltraCompact) 15.sp else 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!isEditingActivity) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF16A34A).copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Auto-saved",
                                                    tint = Color(0xFF16A34A),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                if (!isCompact) {
                                                    Text(
                                                        text = "Auto-saved",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF16A34A)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Right side: Materialistic Flat Action Buttons with Smart Dynamic Fit
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(btnSpacing),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isEditingActivity && ActivityDraftManager.hasDraft(context)) {
                                        Button(
                                            onClick = {
                                                ActivityDraftManager.clearDraft(context)
                                                resetEntryForm()
                                                Toast.makeText(context, "Draft cleared", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                            contentPadding = PaddingValues(horizontal = btnPaddingHorizontal, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(btnHeight)
                                                .defaultMinSize(minHeight = btnHeight, minWidth = 1.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isCompact) "Clear" else "Clear Draft",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            showAddEditDialog = false
                                            resetEntryForm()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                        contentPadding = PaddingValues(horizontal = btnPaddingHorizontal, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(btnHeight)
                                            .defaultMinSize(minHeight = btnHeight, minWidth = 1.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Cancel",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val effectiveActivityName = when (selectedActivity) {
                                                "None" -> ""
                                                "Manually Enter", "Other" -> customActivityText.trim()
                                                else -> selectedActivity.trim()
                                            }

                                            val effectiveLocation = when (selectedRoadLocation) {
                                                "None" -> ""
                                                "Manually Enter" -> customRoadLocationText.trim()
                                                else -> selectedRoadLocation.trim()
                                            }

                                            val finalSide = if (selectedSide == "Auto") (detectedSideFromRoute ?: "LHS") else selectedSide
                                            val isAuto = selectedSide == "Auto" && detectedSideFromRoute != null

                                            val formattedFromCh = formatHighwayChainageInput(fromChainage.trim())
                                            val formattedToCh = formatHighwayChainageInput(toChainage.trim())
                                            fromChainage = formattedFromCh
                                            toChainage = formattedToCh

                                            val formattedSubList = subActivitiesList.map { sub ->
                                                val sFrom = formatHighwayChainageInput(sub.fromChainage.trim())
                                                val sTo = formatHighwayChainageInput(sub.toChainage.trim())
                                                sub.copy(
                                                    fromChainage = sFrom,
                                                    toChainage = sTo,
                                                    lengthM = calculateChainageDiffMetres(sFrom, sTo)
                                                )
                                            }
                                            subActivitiesList.clear()
                                            subActivitiesList.addAll(formattedSubList)

                                            val firstSub = formattedSubList.firstOrNull()
                                            val encodedSubComments = encodeSubActivitiesJson(formattedSubList)

                                            val mainLen = calculateChainageDiffMetres(formattedFromCh, formattedToCh)
                                            val mainWid = activityWidthM.toDoubleOrNull() ?: 0.0
                                            val mainDepth = activityDepthM.toDoubleOrNull() ?: 0.0
                                            val mainVol = if (mainDepth > 0 && mainWid > 0 && mainLen > 0) mainLen * mainWid * (mainDepth / 1000.0) else 0.0

                                            val finalAct = SmartActivityEntity(
                                                id = editingActivityId ?: 0,
                                                projectId = currentProject.id,
                                                projectName = currentProject.name,
                                                dateString = selectedDateString,
                                                timestamp = System.currentTimeMillis(),
                                                activityLayer = effectiveActivityName,
                                                fromChainage = formattedFromCh,
                                                toChainage = formattedToCh,
                                                completedLengthM = mainLen,
                                                widthM = mainWid,
                                                depthM = mainDepth,
                                                thicknessMm = mainDepth,
                                                quantity = mainVol,
                                                unit = if (mainDepth > 0) "Cum" else if (mainWid > 0) "Sqm" else "Rm",
                                                side = finalSide,
                                                sideMode = selectedSide,
                                                isSideAutoDetected = isAuto,
                                                roadSection = effectiveLocation,
                                                siteObservations = mainComments.trim(),
                                                subActivityName = firstSub?.name ?: "",
                                                subFromChainage = firstSub?.fromChainage ?: "",
                                                subToChainage = firstSub?.toChainage ?: "",
                                                subLengthM = firstSub?.lengthM ?: 0.0,
                                                subWidthM = firstSub?.widthM ?: 0.0,
                                                subDepthM = firstSub?.depthM ?: 0.0,
                                                subComments = encodedSubComments,
                                                photoUris = siteRegisterPhotoPath.trim()
                                            )

                                            scope.launch {
                                                viewModel.saveSmartActivity(finalAct)
                                                ActivityDraftManager.clearDraft(context)
                                                Toast.makeText(context, if (isEditingActivity) "Activity Updated" else "Activity Saved", Toast.LENGTH_SHORT).show()
                                                showAddEditDialog = false
                                                resetEntryForm()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = if (isUltraCompact) 8.dp else if (isCompact) 10.dp else 14.dp,
                                            vertical = 0.dp
                                        ),
                                        modifier = Modifier
                                            .height(btnHeight)
                                            .defaultMinSize(minHeight = btnHeight, minWidth = 1.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isEditingActivity) Icons.Default.Check else Icons.Default.Save,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isCompact) {
                                                if (isEditingActivity) "Update" else "Save"
                                            } else {
                                                if (isEditingActivity) "Update Activity" else "Save Activity"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Scrollable Form Body with Clean Minimalist Flat UI
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("MAIN ACTIVITY DETAILS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)

                        // 1. From Chainage
                        OutlinedTextField(
                            value = fromChainage,
                            onValueChange = { fromChainage = it },
                            label = { Text("From Chainage") },
                            placeholder = { Text("e.g. 198000 or 198+000") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { fromChainage = formatHighwayChainageInput(fromChainage) }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. To Chainage
                        OutlinedTextField(
                            value = toChainage,
                            onValueChange = { toChainage = it },
                            label = { Text("To Chainage") },
                            placeholder = { Text("e.g. 198880 or 198+880") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { toChainage = formatHighwayChainageInput(toChainage) }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 3. Auto Length (m) - Read Only Pill Container
                        val calculatedMainLen = calculateChainageDiffMetres(fromChainage, toChainage)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto Calculated Length", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("${calculatedMainLen.toInt()} m", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        // 4. Activity Dropdown
                        var expandedAct by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (selectedActivity == "Manually Enter") customActivityText else selectedActivity,
                                onValueChange = { customActivityText = it },
                                label = { Text("Activity Layer") },
                                readOnly = selectedActivity != "Manually Enter",
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(onClick = { expandedAct = true }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Activity")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedAct = true }
                            )
                            DropdownMenu(
                                expanded = expandedAct,
                                onDismissRequest = { expandedAct = false }
                            ) {
                                val activitiesList = listOf("EMB", "SG", "GSB", "WMM", "DBM", "BC", "DLC", "PQC", "Milling", "None", "Manually Enter")
                                activitiesList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, fontWeight = if (selectedActivity == item) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedActivity = item
                                            expandedAct = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedActivity == "Manually Enter" || selectedActivity == "Other") {
                            OutlinedTextField(
                                value = customActivityText,
                                onValueChange = { customActivityText = it },
                                label = { Text("Custom Activity Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 5. Width & Depth (m) & Smart Quantity Calculation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = activityWidthM,
                                onValueChange = { activityWidthM = it },
                                label = { Text("Width (m)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = activityDepthM,
                                onValueChange = { activityDepthM = it },
                                label = { Text("Depth (mm)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val mainLen = calculateChainageDiffMetres(fromChainage, toChainage)
                        val mainWid = activityWidthM.toDoubleOrNull() ?: 0.0
                        val mainDepth = activityDepthM.toDoubleOrNull() ?: 0.0
                        val mainQty = if (mainDepth > 0 && mainWid > 0) mainLen * mainWid * (mainDepth / 1000.0) else if (mainWid > 0) mainLen * mainWid else mainLen
                        val mainQtyUnit = if (mainDepth > 0 && mainWid > 0) "Cum (m³)" else if (mainWid > 0) "Sqm (m²)" else "Metres (m)"

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Smart Quantity: Length × Width × Depth", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("${"%.2f".format(Locale.US, mainQty)} $mainQtyUnit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // 6. Side Dropdown
                        var expandedSide by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedSide,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Carriageway Side") },
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(onClick = { expandedSide = true }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSide = true }
                            )
                            DropdownMenu(
                                expanded = expandedSide,
                                onDismissRequest = { expandedSide = false }
                            ) {
                                listOf("Auto", "LHS", "RHS", "BHS").forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s, fontWeight = if (selectedSide == s) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedSide = s
                                            expandedSide = false
                                        }
                                    )
                                }
                            }
                        }

                        // 7. Location Dropdown
                        var expandedLoc by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (selectedRoadLocation == "Manually Enter") customRoadLocationText else selectedRoadLocation,
                                onValueChange = { customRoadLocationText = it },
                                label = { Text("Road Location") },
                                readOnly = selectedRoadLocation != "Manually Enter",
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(onClick = { expandedLoc = true }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedLoc = true }
                            )
                            DropdownMenu(
                                expanded = expandedLoc,
                                onDismissRequest = { expandedLoc = false }
                            ) {
                                listOf("O/L", "I/L", "Shoulder", "Median", "MCW", "None", "Manually Enter").forEach { l ->
                                    DropdownMenuItem(
                                        text = { Text(l, fontWeight = if (selectedRoadLocation == l) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedRoadLocation = l
                                            expandedLoc = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedRoadLocation == "Manually Enter") {
                            OutlinedTextField(
                                value = customRoadLocationText,
                                onValueChange = { customRoadLocationText = it },
                                label = { Text("Enter Custom Location") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 8. Main Comments
                        OutlinedTextField(
                            value = mainComments,
                            onValueChange = { mainComments = it },
                            label = { Text("Main Comments") },
                            maxLines = 3,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // -------------------------------------------------------------
                        // SUB ACTIVITY SECTION
                        // -------------------------------------------------------------
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SUB ACTIVITIES (${subActivitiesList.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )

                            OutlinedButton(
                                onClick = {
                                    showSubActivitySection = true
                                    subActivitiesList.add(
                                        SubActivityData(
                                            name = "Glass Grid",
                                            fromChainage = fromChainage,
                                            toChainage = toChainage,
                                            lengthM = calculatedMainLen,
                                            widthM = activityWidthM.toDoubleOrNull() ?: 0.0,
                                            comments = "",
                                            isExpanded = true
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Sub Activity", fontSize = 11.sp)
                            }
                        }

                        if (showSubActivitySection && subActivitiesList.isNotEmpty()) {
                            subActivitiesList.forEachIndexed { subIndex, subItem ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Sub Activity #${subIndex + 1}: ${subItem.name}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        subActivitiesList[subIndex] = subItem.copy(isExpanded = !subItem.isExpanded)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (subItem.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Expand Sub",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { subActivitiesList.removeAt(subIndex) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove Sub", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        if (subItem.isExpanded) {
                                            var expandedSubName by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                OutlinedTextField(
                                                    value = subItem.name,
                                                    onValueChange = { newName ->
                                                        subActivitiesList[subIndex] = subItem.copy(name = newName)
                                                    },
                                                    label = { Text("Sub Activity Name", fontSize = 11.sp) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    trailingIcon = {
                                                        IconButton(onClick = { expandedSubName = true }) {
                                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                DropdownMenu(
                                                    expanded = expandedSubName,
                                                    onDismissRequest = { expandedSubName = false }
                                                ) {
                                                    listOf("Glass Grid", "Geotextile", "Crack Sealing", "Milling", "Kerb / Drain", "None", "Manually Enter").forEach { s ->
                                                        DropdownMenuItem(
                                                            text = { Text(s) },
                                                            onClick = {
                                                                subActivitiesList[subIndex] = subItem.copy(name = s)
                                                                expandedSubName = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            var expandedSubLoc by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                OutlinedTextField(
                                                    value = subItem.location,
                                                    onValueChange = { newLoc ->
                                                        subActivitiesList[subIndex] = subItem.copy(location = newLoc)
                                                    },
                                                    label = { Text("Sub Activity Location", fontSize = 11.sp) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    trailingIcon = {
                                                        IconButton(onClick = { expandedSubLoc = true }) {
                                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                DropdownMenu(
                                                    expanded = expandedSubLoc,
                                                    onDismissRequest = { expandedSubLoc = false }
                                                ) {
                                                    listOf("I/L", "O/L", "MCW", "Shoulder", "Service Road", "None", "Enter Manually").forEach { locOpt ->
                                                        DropdownMenuItem(
                                                            text = { Text(locOpt) },
                                                            onClick = {
                                                                val selectedLoc = if (locOpt == "None" || locOpt == "Enter Manually") "" else locOpt
                                                                subActivitiesList[subIndex] = subItem.copy(location = selectedLoc)
                                                                expandedSubLoc = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = subItem.fromChainage,
                                                    onValueChange = { newFrom ->
                                                        val len = calculateChainageDiffMetres(newFrom, subItem.toChainage)
                                                        subActivitiesList[subIndex] = subItem.copy(fromChainage = newFrom, lengthM = len)
                                                    },
                                                    label = { Text("Sub From Ch", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                                    keyboardActions = KeyboardActions(onNext = {
                                                        val fmt = formatHighwayChainageInput(subItem.fromChainage)
                                                        val len = calculateChainageDiffMetres(fmt, subItem.toChainage)
                                                        subActivitiesList[subIndex] = subItem.copy(fromChainage = fmt, lengthM = len)
                                                    }),
                                                    modifier = Modifier.weight(1f)
                                                )

                                                OutlinedTextField(
                                                    value = subItem.toChainage,
                                                    onValueChange = { newTo ->
                                                        val len = calculateChainageDiffMetres(subItem.fromChainage, newTo)
                                                        subActivitiesList[subIndex] = subItem.copy(toChainage = newTo, lengthM = len)
                                                    },
                                                    label = { Text("Sub To Ch", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = {
                                                        val fmt = formatHighwayChainageInput(subItem.toChainage)
                                                        val len = calculateChainageDiffMetres(subItem.fromChainage, fmt)
                                                        subActivitiesList[subIndex] = subItem.copy(toChainage = fmt, lengthM = len)
                                                    }),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "Length: ${subItem.lengthM.toInt()} m",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }

                                                 OutlinedTextField(
                                                     value = if (subItem.widthM > 0) "${subItem.widthM}" else "",
                                                     onValueChange = { newW ->
                                                         subActivitiesList[subIndex] = subItem.copy(widthM = newW.toDoubleOrNull() ?: 0.0)
                                                     },
                                                     label = { Text("Width (m)", fontSize = 11.sp) },
                                                     singleLine = true,
                                                     shape = RoundedCornerShape(8.dp),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                     modifier = Modifier.weight(1f)
                                                 )

                                                 OutlinedTextField(
                                                     value = if (subItem.depthM > 0) "${subItem.depthM}" else "",
                                                     onValueChange = { newD ->
                                                         subActivitiesList[subIndex] = subItem.copy(depthM = newD.toDoubleOrNull() ?: 0.0)
                                                     },
                                                     label = { Text("Depth (mm)", fontSize = 11.sp) },
                                                     singleLine = true,
                                                     shape = RoundedCornerShape(8.dp),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                     modifier = Modifier.weight(1f)
                                                 )
                                             }

                                             val subQty = if (subItem.depthM > 0 && subItem.widthM > 0) subItem.lengthM * subItem.widthM * (subItem.depthM / 1000.0) else if (subItem.widthM > 0) subItem.lengthM * subItem.widthM else subItem.lengthM
                                             val subUnit = if (subItem.depthM > 0 && subItem.widthM > 0) "m³" else if (subItem.widthM > 0) "m²" else "m"
                                             Surface(
                                                 shape = RoundedCornerShape(6.dp),
                                                 color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 Text(
                                                     text = "Sub Qty: ${"%.2f".format(Locale.US, subQty)} $subUnit",
                                                     fontSize = 11.sp,
                                                     fontWeight = FontWeight.Bold,
                                                     modifier = Modifier.padding(8.dp)
                                                 )
                                             }

                                            OutlinedTextField(
                                                value = subItem.comments,
                                                onValueChange = { newComm ->
                                                    subActivitiesList[subIndex] = subItem.copy(comments = newComm)
                                                },
                                                label = { Text("Sub Comments", fontSize = 11.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // 10. Site Register Photo
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("SITE REGISTER PHOTO (OPTIONAL • NO METADATA)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)

                                if (siteRegisterPhotoPath.isBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { triggerRegisterCameraCapture() },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Take Photo", fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                pickRegisterPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pick Image", fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("✓ Photo Scanned & Attached", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    viewingPhotoPath = siteRegisterPhotoPath
                                                    showFullPhotoViewer = true
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Visibility, contentDescription = "View", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { siteRegisterPhotoPath = "" },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // Unsaved Changes Confirmation
        if (showUnsavedConfirmation) {
            AlertDialog(
                onDismissRequest = { showUnsavedConfirmation = false },
                title = { Text("Discard Unsaved Changes?") },
                text = { Text("You have un-saved activity details. Are you sure you want to close without saving?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsavedConfirmation = false
                            showAddEditDialog = false
                            resetEntryForm()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Discard & Close")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnsavedConfirmation = false }) {
                        Text("Keep Editing")
                    }
                }
            )
        }
    }

    // -------------------------------------------------------------
    // WEEKLY / CUSTOM DATE RANGE EXPORT DIALOG
    // -------------------------------------------------------------
    if (showWeeklyExportDialog) {
        var startDateInput by remember {
            mutableStateOf(
                run {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                }
            )
        }
        var endDateInput by remember { mutableStateOf(todayStr) }
        var selectedPreset by remember { mutableStateOf("THIS_WEEK") } // "THIS_WEEK", "LAST_WEEK", "CUSTOM"
        var exportProjectId by remember { mutableStateOf(currentProject.id) }
        var isGeneratingRangePdf by remember { mutableStateOf(false) }
        var includeLogo by remember(overlayConfig.includePdfLogoDefault, dprCustomLogoUri) { mutableStateOf(overlayConfig.includePdfLogoDefault && !dprCustomLogoUri.isNullOrBlank()) }
        var includeSig by remember(overlayConfig.includePdfSignatureDefault, signatureBitmapState) { mutableStateOf(overlayConfig.includePdfSignatureDefault && signatureBitmapState != null) }

        AlertDialog(
            onDismissRequest = { showWeeklyExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF2563EB))
                    Text("Weekly / Custom Date Export", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Quick Range Presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedPreset == "THIS_WEEK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                selectedPreset = "THIS_WEEK"
                                val cal = Calendar.getInstance()
                                endDateInput = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                                cal.add(Calendar.DAY_OF_YEAR, -6)
                                startDateInput = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "This Week",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPreset == "THIS_WEEK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedPreset == "LAST_WEEK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                selectedPreset = "LAST_WEEK"
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, -7)
                                endDateInput = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                                cal.add(Calendar.DAY_OF_YEAR, -6)
                                startDateInput = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Last Week",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPreset == "LAST_WEEK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedPreset == "CUSTOM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedPreset = "CUSTOM" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Custom",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPreset == "CUSTOM") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startDateInput,
                            onValueChange = {
                                startDateInput = it
                                selectedPreset = "CUSTOM"
                            },
                            label = { Text("From Date (YYYY-MM-DD)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = endDateInput,
                            onValueChange = {
                                endDateInput = it
                                selectedPreset = "CUSTOM"
                            },
                            label = { Text("To Date (YYYY-MM-DD)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Project:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    var expandedPrj by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = exportProjectId,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedPrj = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedPrj = true }
                        )
                        DropdownMenu(
                            expanded = expandedPrj,
                            onDismissRequest = { expandedPrj = false }
                        ) {
                            appProjects.forEach { prj ->
                                DropdownMenuItem(
                                    text = { Text("${prj.id} - ${prj.name}") },
                                    onClick = {
                                        exportProjectId = prj.id
                                        expandedPrj = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text("Export Options:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    // Checkbox: Include Custom Logo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeLogo && !dprCustomLogoUri.isNullOrBlank(),
                            onCheckedChange = { includeLogo = it },
                            enabled = !dprCustomLogoUri.isNullOrBlank()
                        )
                        Text(
                            text = "Include Custom Logo" + if (dprCustomLogoUri.isNullOrBlank()) " (No logo set in Settings)" else "",
                            fontSize = 12.sp,
                            color = if (!dprCustomLogoUri.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Checkbox: Include Saved Signature
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeSig && signatureBitmapState != null,
                            onCheckedChange = { includeSig = it },
                            enabled = signatureBitmapState != null
                        )
                        Text(
                            text = "Include Saved Signature" + if (signatureBitmapState == null) " (No signature saved)" else "",
                            fontSize = 12.sp,
                            color = if (signatureBitmapState != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            isGeneratingRangePdf = true
                            scope.launch {
                                val targetProject = appProjects.find { it.id == exportProjectId } ?: currentProject
                                val rangeActs = viewModel.getActivitiesForProjectAndDateRangeList(exportProjectId, startDateInput, endDateInput)
                                val logoBmp = if (includeLogo) DprPdfGenerator.loadBitmapFromUri(context, dprCustomLogoUri) else null
                                val title = if (selectedPreset == "THIS_WEEK" || selectedPreset == "LAST_WEEK") "WEEKLY HIGHWAY SITE ACTIVITY REPORT" else "CUSTOM PERIOD SITE ACTIVITY REPORT"

                                val pdfFile = DprPdfGenerator.generateRangeDprPdf(
                                    context = context,
                                    project = targetProject,
                                    startDateString = startDateInput,
                                    endDateString = endDateInput,
                                    reportTitle = title,
                                    activities = rangeActs,
                                    preparedBy = dprPreparedByName,
                                    customLogoBitmap = logoBmp,
                                    signatureBitmap = if (includeSig) signatureBitmapState else null,
                                    includeLogo = includeLogo,
                                    includeSignature = includeSig
                                )
                                isGeneratingRangePdf = false
                                previewPdfFile = pdfFile
                                showPdfPreviewModal = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview")
                    }

                    Button(
                        onClick = {
                            isGeneratingRangePdf = true
                            scope.launch {
                                val targetProject = appProjects.find { it.id == exportProjectId } ?: currentProject
                                val rangeActs = viewModel.getActivitiesForProjectAndDateRangeList(exportProjectId, startDateInput, endDateInput)
                                val logoBmp = if (includeLogo) DprPdfGenerator.loadBitmapFromUri(context, dprCustomLogoUri) else null
                                val title = if (selectedPreset == "THIS_WEEK" || selectedPreset == "LAST_WEEK") "WEEKLY HIGHWAY SITE ACTIVITY REPORT" else "CUSTOM PERIOD SITE ACTIVITY REPORT"

                                val pdfFile = DprPdfGenerator.generateRangeDprPdf(
                                    context = context,
                                    project = targetProject,
                                    startDateString = startDateInput,
                                    endDateString = endDateInput,
                                    reportTitle = title,
                                    activities = rangeActs,
                                    preparedBy = dprPreparedByName,
                                    customLogoBitmap = logoBmp,
                                    signatureBitmap = if (includeSig) signatureBitmapState else null,
                                    includeLogo = includeLogo,
                                    includeSignature = includeSig
                                )
                                isGeneratingRangePdf = false
                                showWeeklyExportDialog = false
                                DprPdfGenerator.shareDprPdf(context, pdfFile)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share PDF")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeeklyExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // -------------------------------------------------------------
    // DAILY DPR EXPORT DIALOG (SINGLE, ALL DAY, SELECTED)
    // -------------------------------------------------------------
    if (showDprExportDialog) {
        var includeLogo by remember(overlayConfig.includePdfLogoDefault, dprCustomLogoUri) { mutableStateOf(overlayConfig.includePdfLogoDefault && !dprCustomLogoUri.isNullOrBlank()) }
        var includeSig by remember(overlayConfig.includePdfSignatureDefault, signatureBitmapState) { mutableStateOf(overlayConfig.includePdfSignatureDefault && signatureBitmapState != null) }

        AlertDialog(
            onDismissRequest = { showDprExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Export PDF Confirmation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val exportList = when (dprExportMode) {
                        "SINGLE" -> listOfNotNull(singleActivityToExport)
                        "SELECTED" -> activitiesForDate.filter { selectedActivityIds.contains(it.id) }
                        else -> activitiesForDate
                    }

                    Text("Selected Activities: ${exportList.size} item(s)", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = dprPreparedByName,
                        onValueChange = { dprPreparedByName = it },
                        label = { Text("Prepared By (Site Engineer Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text("Export Options:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    // Checkbox: Include Custom Logo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeLogo && !dprCustomLogoUri.isNullOrBlank(),
                            onCheckedChange = { includeLogo = it },
                            enabled = !dprCustomLogoUri.isNullOrBlank()
                        )
                        Text(
                            text = "Include Custom Logo" + if (dprCustomLogoUri.isNullOrBlank()) " (No logo set in Settings)" else "",
                            fontSize = 12.sp,
                            color = if (!dprCustomLogoUri.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Checkbox: Include Saved Signature
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeSig && signatureBitmapState != null,
                            onCheckedChange = { includeSig = it },
                            enabled = signatureBitmapState != null
                        )
                        Text(
                            text = "Include Saved Signature" + if (signatureBitmapState == null) " (No signature saved)" else "",
                            fontSize = 12.sp,
                            color = if (signatureBitmapState != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val exportList = when (dprExportMode) {
                                "SINGLE" -> listOfNotNull(singleActivityToExport)
                                "SELECTED" -> activitiesForDate.filter { selectedActivityIds.contains(it.id) }
                                else -> activitiesForDate
                            }
                            val logoBmp = if (includeLogo) DprPdfGenerator.loadBitmapFromUri(context, dprCustomLogoUri) else null
                            val pdfFile = DprPdfGenerator.generateDprPdf(
                                context = context,
                                project = currentProject,
                                dateString = selectedDateString,
                                activities = exportList,
                                preparedBy = dprPreparedByName,
                                customLogoBitmap = logoBmp,
                                signatureBitmap = if (includeSig) signatureBitmapState else null,
                                includeLogo = includeLogo,
                                includeSignature = includeSig
                            )
                            previewPdfFile = pdfFile
                            showPdfPreviewModal = true
                        }
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview")
                    }

                    Button(
                        onClick = {
                            val exportList = when (dprExportMode) {
                                "SINGLE" -> listOfNotNull(singleActivityToExport)
                                "SELECTED" -> activitiesForDate.filter { selectedActivityIds.contains(it.id) }
                                else -> activitiesForDate
                            }
                            val logoBmp = if (includeLogo) DprPdfGenerator.loadBitmapFromUri(context, dprCustomLogoUri) else null
                            val pdfFile = DprPdfGenerator.generateDprPdf(
                                context = context,
                                project = currentProject,
                                dateString = selectedDateString,
                                activities = exportList,
                                preparedBy = dprPreparedByName,
                                customLogoBitmap = logoBmp,
                                signatureBitmap = if (includeSig) signatureBitmapState else null,
                                includeLogo = includeLogo,
                                includeSignature = includeSig
                            )
                            showDprExportDialog = false
                            DprPdfGenerator.shareDprPdf(context, pdfFile)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share PDF")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDprExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Full Page Rendered Document Preview Dialog
    if (showPdfPreviewModal && previewPdfFile != null) {
        PdfDocumentPreviewDialog(
            pdfFile = previewPdfFile!!,
            onDismiss = { showPdfPreviewModal = false },
            onShare = {
                DprPdfGenerator.shareDprPdf(context, previewPdfFile!!)
            }
        )
    }

    // Delete Confirmation Dialog
    if (activityToDelete != null) {
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = { Text("Delete Site Activity?") },
            text = { Text("Are you sure you want to delete this activity entry (${activityToDelete?.activityLayer} Ch. ${activityToDelete?.fromChainage})?") },
            confirmButton = {
                Button(
                    onClick = {
                        activityToDelete?.let { act ->
                            scope.launch {
                                viewModel.deleteSmartActivity(act)
                                Toast.makeText(context, "Activity Deleted", Toast.LENGTH_SHORT).show()
                                activityToDelete = null
                            }
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
