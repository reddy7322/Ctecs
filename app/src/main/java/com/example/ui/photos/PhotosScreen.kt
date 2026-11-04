package com.example.ui.photos

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PhotoRecordEntity
import com.example.model.GeoPoint
import com.example.ui.MainViewModel
import com.example.ui.camera.MiniMapPreview
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryMediaTab {
    PHOTOS,
    VIDEOS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val photos by viewModel.allPhotos.collectAsStateWithLifecycle()
    val galleryViewMode by viewModel.galleryViewMode.collectAsStateWithLifecycle()
    val gallerySortOption by viewModel.gallerySortOption.collectAsStateWithLifecycle()
    val galleryGroupOption by viewModel.galleryGroupOption.collectAsStateWithLifecycle()

    var selectedMediaTab by remember { mutableStateOf(GalleryMediaTab.PHOTOS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val haptic = LocalHapticFeedback.current
    var viewingPhoto by remember { mutableStateOf<PhotoRecordEntity?>(null) }
    var viewingVideo by remember { mutableStateOf<PhotoRecordEntity?>(null) }
    var photoToDelete by remember { mutableStateOf<PhotoRecordEntity?>(null) }
    var photoForDetails by remember { mutableStateOf<PhotoRecordEntity?>(null) }
    var photoForOptions by remember { mutableStateOf<PhotoRecordEntity?>(null) }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateListOf<Long>() }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Dropdown menu states
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    // Collapsed groups state
    val collapsedGroups = remember { mutableStateListOf<String>() }

    val photoCount = remember(photos) { photos.count { !it.fileName.endsWith(".mp4", ignoreCase = true) } }
    val videoCount = remember(photos) { photos.count { it.fileName.endsWith(".mp4", ignoreCase = true) } }

    // Auto-sync storage videos and photos when gallery screen opens
    LaunchedEffect(Unit) {
        viewModel.syncGalleryMedia()
    }

    val sortOptions = listOf(
        "Newest first" to "NEWEST_FIRST",
        "Oldest first" to "OLDEST_FIRST",
        "Filename A–Z" to "NAME_ASC",
        "Filename Z–A" to "NAME_DESC",
        "Largest file first" to "SIZE_DESC",
        "Smallest file first" to "SIZE_ASC",
        "Highest resolution first" to "RES_DESC",
        "Lowest resolution first" to "RES_ASC"
    )

    val groupOptions = listOf(
        "None (All Photos)" to "None",
        "By Capture Date" to "Date",
        "By Month & Year" to "Month_Year",
        "By Highway / Project" to "Highway",
        "By Activity" to "Activity"
    )

    // Filter & Sort photos
    val filteredSortedPhotos = remember(photos, searchQuery, selectedFilter, gallerySortOption, selectedMediaTab) {
        val filtered = photos.filter { p ->
            val isVideo = p.fileName.endsWith(".mp4", ignoreCase = true) || p.filePath.endsWith(".mp4", ignoreCase = true)
            val matchesTab = when (selectedMediaTab) {
                GalleryMediaTab.PHOTOS -> !isVideo
                GalleryMediaTab.VIDEOS -> isVideo
            }
            if (!matchesTab) return@filter false

            val matchesQuery = if (searchQuery.isBlank()) true else {
                p.chainageLabel?.contains(searchQuery, ignoreCase = true) == true ||
                        p.activity.contains(searchQuery, ignoreCase = true) ||
                        p.fileName.contains(searchQuery, ignoreCase = true) ||
                        p.highwayId.contains(searchQuery, ignoreCase = true)
            }
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "DBM" -> p.activity.equals("DBM", ignoreCase = true)
                "BC" -> p.activity.equals("BC", ignoreCase = true)
                "GSB" -> p.activity.equals("GSB", ignoreCase = true)
                "WMM" -> p.activity.equals("WMM", ignoreCase = true)
                "Milling" -> p.activity.equals("Milling", ignoreCase = true)
                "Subgrade" -> p.activity.equals("Subgrade", ignoreCase = true)
                "PQC" -> p.activity.equals("PQC", ignoreCase = true)
                "None" -> p.activity.equals("None", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesFilter
        }

        when (gallerySortOption) {
            "OLDEST_FIRST" -> filtered.sortedBy { it.timestamp }
            "NAME_ASC" -> filtered.sortedBy { it.fileName.lowercase(Locale.US) }
            "NAME_DESC" -> filtered.sortedByDescending { it.fileName.lowercase(Locale.US) }
            "SIZE_DESC" -> filtered.sortedByDescending { File(it.filePath).length() }
            "SIZE_ASC" -> filtered.sortedBy { File(it.filePath).length() }
            "RES_DESC" -> filtered.sortedByDescending {
                val res = getImageResolution(it.filePath)
                res.first * res.second
            }
            "RES_ASC" -> filtered.sortedBy {
                val res = getImageResolution(it.filePath)
                res.first * res.second
            }
            else -> filtered.sortedByDescending { it.timestamp } // NEWEST_FIRST default
        }
    }

    // Group photos
    val groupedPhotos = remember(filteredSortedPhotos, galleryGroupOption) {
        when (galleryGroupOption) {
            "Date" -> {
                val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US)
                filteredSortedPhotos.groupBy { sdf.format(Date(it.timestamp)) }.toList()
            }
            "Month_Year" -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
                filteredSortedPhotos.groupBy { sdf.format(Date(it.timestamp)) }.toList()
            }
            "Highway" -> {
                filteredSortedPhotos.groupBy { it.highwayId.ifBlank { "Unassigned Highway" } }.toList()
            }
            "Activity" -> {
                filteredSortedPhotos.groupBy { it.activity.ifBlank { "Unassigned Activity" } }.toList()
            }
            else -> {
                listOf("All Photos" to filteredSortedPhotos)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Multi-select Contextual Bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedPhotoIds.size} Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedPhotoIds.clear()
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Selection Mode")
                        }
                    },
                    actions = {
                        // Select All / Deselect All
                        OutlinedButton(
                            onClick = {
                                if (selectedPhotoIds.size == filteredSortedPhotos.size) {
                                    selectedPhotoIds.clear()
                                } else {
                                    selectedPhotoIds.clear()
                                    selectedPhotoIds.addAll(filteredSortedPhotos.map { it.id })
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedPhotoIds.size == filteredSortedPhotos.size) Icons.Default.Close else Icons.Default.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedPhotoIds.size == filteredSortedPhotos.size) "Deselect" else "Select All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Share selected
                        IconButton(
                            enabled = selectedPhotoIds.isNotEmpty(),
                            onClick = {
                                val selectedList = photos.filter { it.id in selectedPhotoIds }
                                shareMultiplePhotos(context, selectedList)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share Selected")
                        }

                        // Delete selected
                        IconButton(
                            enabled = selectedPhotoIds.isNotEmpty(),
                            onClick = { showBulkDeleteDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFFDC2626) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // Standard Top AppBar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (selectedMediaTab == GalleryMediaTab.PHOTOS) "Photos" else "Videos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${filteredSortedPhotos.size} ${if (selectedMediaTab == GalleryMediaTab.PHOTOS) "photos" else "videos"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Camera"
                            )
                        }
                    },
                    actions = {
                        // View mode toggle
                        IconButton(
                            onClick = {
                                val nextMode = if (galleryViewMode == "GRID") "LIST" else "GRID"
                                viewModel.updateGalleryPrefs(viewMode = nextMode)
                            }
                        ) {
                            Icon(
                                imageVector = if (galleryViewMode == "GRID") Icons.Default.FormatListBulleted else Icons.Default.GridView,
                                contentDescription = if (galleryViewMode == "GRID") "Switch to List View" else "Switch to Grid View"
                            )
                        }

                        // Sort Menu Toggle
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort Options")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                Text(
                                    text = "  Sort By",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                sortOptions.forEach { (label, code) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(label, fontSize = 13.sp)
                                                if (gallerySortOption == code) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateGalleryPrefs(sortOption = code)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Group Menu Toggle
                        Box {
                            IconButton(onClick = { showGroupMenu = true }) {
                                Icon(imageVector = Icons.Default.FilterList, contentDescription = "Group Options")
                            }
                            DropdownMenu(
                                expanded = showGroupMenu,
                                onDismissRequest = { showGroupMenu = false }
                            ) {
                                Text(
                                    text = "  Group By",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                groupOptions.forEach { (label, code) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(label, fontSize = 13.sp)
                                                if (galleryGroupOption == code) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateGalleryPrefs(groupOption = code)
                                            showGroupMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Multi-Select Toggle Button
                        IconButton(onClick = {
                            isSelectionMode = true
                            selectedPhotoIds.clear()
                        }) {
                            Icon(imageVector = Icons.Default.Checklist, contentDescription = "Select Photos")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Share Item
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = selectedPhotoIds.isNotEmpty()) {
                                        val selectedList = photos.filter { it.id in selectedPhotoIds }
                                        shareMultiplePhotos(context, selectedList)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFF2563EB) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Share",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedPhotoIds.isNotEmpty()) Color(0xFF1E293B) else Color.Gray
                                )
                            }

                            // Divider 1
                            Box(modifier = Modifier.height(22.dp).width(1.dp).background(Color(0xFFE2E8F0)))

                            // 2. Delete Item
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = selectedPhotoIds.isNotEmpty()) {
                                        showBulkDeleteDialog = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFFDC2626) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Delete",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedPhotoIds.isNotEmpty()) Color(0xFFDC2626) else Color.Gray
                                )
                            }

                            // Divider 2
                            Box(modifier = Modifier.height(22.dp).width(1.dp).background(Color(0xFFE2E8F0)))

                            // 3. Move Item
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = selectedPhotoIds.isNotEmpty()) {
                                        viewModel.showToast("Moved ${selectedPhotoIds.size} files")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Move",
                                    tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFF2563EB) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Move",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedPhotoIds.isNotEmpty()) Color(0xFF1E293B) else Color.Gray
                                )
                            }

                            // Divider 3
                            Box(modifier = Modifier.height(22.dp).width(1.dp).background(Color(0xFFE2E8F0)))

                            // 4. Group Item
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = selectedPhotoIds.isNotEmpty()) {
                                        viewModel.showToast("Grouped ${selectedPhotoIds.size} photos")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Group",
                                    tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFF2563EB) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Group",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedPhotoIds.isNotEmpty()) Color(0xFF1E293B) else Color.Gray
                                )
                            }

                            // Divider 4
                            Box(modifier = Modifier.height(22.dp).width(1.dp).background(Color(0xFFE2E8F0)))

                            // 5. Export Item
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = selectedPhotoIds.isNotEmpty()) {
                                        val selectedList = photos.filter { it.id in selectedPhotoIds }
                                        shareMultiplePhotos(context, selectedList)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Export",
                                    tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFF2563EB) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Export",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedPhotoIds.isNotEmpty()) Color(0xFF1E293B) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        BackHandler(enabled = isSelectionMode) {
            isSelectionMode = false
            selectedPhotoIds.clear()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Smart Switch Tab: Photos / Videos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Photos Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selectedMediaTab == GalleryMediaTab.PHOTOS) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { selectedMediaTab = GalleryMediaTab.PHOTOS }
                        .padding(vertical = 8.dp)
                        .testTag("tab_photos"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = if (selectedMediaTab == GalleryMediaTab.PHOTOS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Photos ($photoCount)",
                            color = if (selectedMediaTab == GalleryMediaTab.PHOTOS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selectedMediaTab == GalleryMediaTab.PHOTOS) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                // Videos Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selectedMediaTab == GalleryMediaTab.VIDEOS) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { selectedMediaTab = GalleryMediaTab.VIDEOS }
                        .padding(vertical = 8.dp)
                        .testTag("tab_videos"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (selectedMediaTab == GalleryMediaTab.VIDEOS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Videos ($videoCount)",
                            color = if (selectedMediaTab == GalleryMediaTab.VIDEOS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selectedMediaTab == GalleryMediaTab.VIDEOS) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Search Input Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Search by chainage, activity, filename...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("photo_search_input")
            )

            // Activity Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterChips = listOf("All", "DBM", "BC", "GSB", "WMM", "Milling", "Subgrade", "PQC", "None")
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterChips.take(5).forEach { chip ->
                            FilterChip(
                                selected = selectedFilter == chip,
                                onClick = { selectedFilter = chip },
                                label = { Text(chip, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Photos Content View
            if (filteredSortedPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedMediaTab == GalleryMediaTab.PHOTOS) Icons.Default.PhotoLibrary else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedMediaTab == GalleryMediaTab.PHOTOS) {
                                if (photoCount == 0) "No highway photos captured yet" else "No photos match filter criteria"
                            } else {
                                if (videoCount == 0) "No recorded highway videos yet" else "No videos match filter criteria"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    groupedPhotos.forEach { (groupTitle, photoGroup) ->
                        val isCollapsed = collapsedGroups.contains(groupTitle)

                        // Group Header (show if grouping enabled or multiple groups exist)
                        if (galleryGroupOption != "None" || groupedPhotos.size > 1) {
                            item(key = "header_$groupTitle") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            if (isCollapsed) collapsedGroups.remove(groupTitle) else collapsedGroups.add(groupTitle)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = groupTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${photoGroup.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!isCollapsed) {
                            if (galleryViewMode == "GRID") {
                                item(key = "grid_$groupTitle") {
                                    // Proportional 2-Column Grid Item Container
                                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            photoGroup.chunked(2).forEach { pair ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    pair.forEach { photo ->
                                                        val isSelected = selectedPhotoIds.contains(photo.id)
                                                        PhotoUncroppedGridCard(
                                                            photo = photo,
                                                            isSelectionMode = isSelectionMode,
                                                            isSelected = isSelected,
                                                            onClick = {
                                                                if (isSelectionMode) {
                                                                    if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                                                } else {
                                                                    val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
                                                                    if (isVideo) {
                                                                        viewingVideo = photo
                                                                    } else {
                                                                        viewingPhoto = photo
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                if (isSelectionMode) {
                                                                    if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                                                } else {
                                                                    photoForOptions = photo
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    if (pair.size == 1) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // List View Items
                                items(photoGroup, key = { it.id }) { photo ->
                                    val isSelected = selectedPhotoIds.contains(photo.id)
                                    PhotoUncroppedListItem(
                                        photo = photo,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                            } else {
                                                val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
                                                if (isVideo) {
                                                    viewingVideo = photo
                                                } else {
                                                    viewingPhoto = photo
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSelectionMode) {
                                                if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                            } else {
                                                photoForOptions = photo
                                            }
                                        },
                                        onShare = { shareSinglePhoto(context, photo) },
                                        onDelete = { photoToDelete = photo },
                                        onShowDetails = { photoForDetails = photo },
                                        onSelect = {
                                            isSelectionMode = true
                                            if (!selectedPhotoIds.contains(photo.id)) {
                                                selectedPhotoIds.add(photo.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Interactive Photo Viewer Dialog
    viewingPhoto?.let { photo ->
        FullScreenPhotoViewer(
            photo = photo,
            photosList = filteredSortedPhotos,
            onPhotoSelect = { viewingPhoto = it },
            onDismiss = { viewingPhoto = null },
            onShare = { shareSinglePhoto(context, photo) },
            onDelete = {
                viewingPhoto = null
                photoToDelete = photo
            }
        )
    }

    // Full-Screen Interactive Video Player Dialog
    viewingVideo?.let { video ->
        FullScreenVideoPlayer(
            video = video,
            onDismiss = { viewingVideo = null },
            onShare = { shareSinglePhoto(context, video) },
            onDelete = {
                viewingVideo = null
                photoToDelete = video
            }
        )
    }

    // Photo Details Info Modal
    photoForDetails?.let { photo ->
        PhotoDetailsDialog(
            photo = photo,
            onDismiss = { photoForDetails = null }
        )
    }

    // Single Photo Delete Confirmation Dialog
    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Photo File", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${photo.fileName}' from both the gallery and device storage?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePhoto(photo)
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { photoToDelete = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete ${selectedPhotoIds.size} Selected Items", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete these ${selectedPhotoIds.size} media files from device storage?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedList = photos.filter { it.id in selectedPhotoIds }
                        viewModel.deletePhotos(selectedList)
                        selectedPhotoIds.clear()
                        isSelectionMode = false
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete All (${selectedPhotoIds.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBulkDeleteDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // Long-Press Quick Options Dialog
    photoForOptions?.let { photo ->
        PhotoLongPressOptionsDialog(
            photo = photo,
            isSelected = selectedPhotoIds.contains(photo.id),
            selectedCount = selectedPhotoIds.size,
            onDismiss = { photoForOptions = null },
            onShare = {
                photoForOptions = null
                shareSinglePhoto(context, photo)
            },
            onDelete = {
                photoForOptions = null
                photoToDelete = photo
            },
            onSelect = {
                photoForOptions = null
                isSelectionMode = true
                if (!selectedPhotoIds.contains(photo.id)) {
                    selectedPhotoIds.add(photo.id)
                }
            },
            onShareSelected = {
                val selectedList = photos.filter { it.id in selectedPhotoIds }
                photoForOptions = null
                shareMultiplePhotos(context, selectedList)
            },
            onDeleteSelected = {
                photoForOptions = null
                showBulkDeleteDialog = true
            },
            onViewFullscreen = {
                val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
                photoForOptions = null
                if (isVideo) {
                    viewingVideo = photo
                } else {
                    viewingPhoto = photo
                }
            },
            onShowDetails = {
                photoForOptions = null
                photoForDetails = photo
            },
            onCopyText = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Survey Details", text))
                android.widget.Toast.makeText(context, "Copied survey details to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                photoForOptions = null
            }
        )
    }
}

/**
 * Grid Card Component:
 * Uses ContentScale.Fit inside a dark neutral background card so the original aspect ratio is displayed fully without cropping or stretching.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoUncroppedGridCard(
    photo: PhotoRecordEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF334155),
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_card_${photo.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF0F172A))
        ) {
            val file = remember(photo.filePath) { File(photo.filePath) }
            val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
            val videoThumbnail = remember(photo.filePath) {
                if (isVideo) {
                    try {
                        ThumbnailUtils.createVideoThumbnail(photo.filePath, MediaStore.Images.Thumbnails.MINI_KIND)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            // Display media thumbnail in complete original aspect ratio (ContentScale.Fit)
            if (isVideo && videoThumbnail != null) {
                Image(
                    bitmap = videoThumbnail.asImageBitmap(),
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            }

            // Center Play Icon for Video
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xA0000000), CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Video Badge in Top-Start
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .background(Color(0xDD0F172A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "VIDEO",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Selection Checkmark Overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color(0x99000000),
                            CircleShape
                        )
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom Gradient Overlay Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xEE090D16))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = photo.chainageLabel?.ifBlank { "CH: ---" } ?: "CH: ---",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${photo.highwayId} ${photo.roadSide} ${photo.activity}".trim(),
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * List Item Component:
 * Displays original aspect ratio thumbnail (ContentScale.Fit) alongside complete file details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoUncroppedListItem(
    photo: PhotoRecordEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val file = remember(photo.filePath) { File(photo.filePath) }
    val fileSizeMb = remember(file) { "%.1f MB".format(Locale.US, file.length() / (1024f * 1024f)) }
    val resolution = remember(photo.filePath) {
        val res = getImageResolution(photo.filePath)
        val mp = "%.1f MP".format(Locale.US, (res.first * res.second) / 1_000_000f)
        "${res.first}x${res.second} ($mp)"
    }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Selection Checkbox
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Proportional Thumbnail Container (ContentScale.Fit)
            val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
            val videoThumbnail = remember(photo.filePath) {
                if (isVideo) {
                    try {
                        ThumbnailUtils.createVideoThumbnail(photo.filePath, MediaStore.Images.Thumbnails.MINI_KIND)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (isVideo && videoThumbnail != null) {
                    Image(
                        bitmap = videoThumbnail.asImageBitmap(),
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = file,
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xA0000000), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Photo Metadata Summary
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = photo.chainageLabel?.ifBlank { photo.fileName } ?: photo.fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${photo.highwayId} ${photo.roadSide} ${photo.activity}".trim(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = sdf.format(Date(photo.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "$resolution • $fileSizeMb",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Overflow Options Menu
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select / Multi-Select", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onSelect()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Details & Info", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShowDetails()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", fontSize = 13.sp, color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Long-Press Quick Options Dialog:
 * Shows rich action bottom dialog with Share, Delete, Multi-selection, Fullscreen View, Survey Details, and Copy metadata.
 */
@Composable
fun PhotoLongPressOptionsDialog(
    photo: PhotoRecordEntity,
    isSelected: Boolean,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onViewFullscreen: () -> Unit,
    onShowDetails: () -> Unit,
    onCopyText: (String) -> Unit
) {
    val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
    val file = remember(photo.filePath) { File(photo.filePath) }
    val fileSizeMb = remember(file) { "%.1f MB".format(Locale.US, file.length() / (1024f * 1024f)) }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val resolution = remember(photo.filePath) {
        val res = getImageResolution(photo.filePath)
        "${res.first}x${res.second}"
    }
    val videoThumbnail = remember(photo.filePath) {
        if (isVideo) {
            try {
                ThumbnailUtils.createVideoThumbnail(photo.filePath, MediaStore.Images.Thumbnails.MINI_KIND)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    // Drag Handle Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }

                    // Header with Thumbnail, Title, and Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Media Thumbnail Box
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo && videoThumbnail != null) {
                                Image(
                                    bitmap = videoThumbnail.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = photo.chainageLabel?.ifBlank { photo.fileName } ?: photo.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${photo.highwayId} ${photo.roadSide} ${photo.activity}".trim(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${sdf.format(Date(photo.timestamp))} • $fileSizeMb • $resolution",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Options List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // 1. Share Option
                        LongPressActionRow(
                            icon = Icons.Default.Share,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = if (isVideo) "Share Video" else "Share Photo",
                            subtitle = "Share highway survey media with overlay metadata",
                            onClick = onShare
                        )

                        // 2. Delete Option
                        LongPressActionRow(
                            icon = Icons.Default.Delete,
                            iconTint = Color(0xFFEF4444),
                            title = if (isVideo) "Delete Video" else "Delete Photo",
                            subtitle = "Permanently remove from device storage",
                            titleColor = Color(0xFFEF4444),
                            onClick = onDelete
                        )

                        // 3. Multi Selection Mode Option
                        LongPressActionRow(
                            icon = Icons.Default.Checklist,
                            iconTint = Color(0xFF0284C7),
                            title = "Multi-Selection Mode",
                            subtitle = "Select this and other items to batch share or delete",
                            onClick = onSelect
                        )

                        // If multiple items are already selected, provide quick batch actions right here:
                        if (selectedCount > 1) {
                            LongPressActionRow(
                                icon = Icons.Default.Share,
                                iconTint = Color(0xFF10B981),
                                title = "Share All $selectedCount Selected",
                                subtitle = "Send all selected photos and videos together",
                                onClick = onShareSelected
                            )
                            LongPressActionRow(
                                icon = Icons.Default.Delete,
                                iconTint = Color(0xFFDC2626),
                                title = "Delete All $selectedCount Selected",
                                subtitle = "Delete all selected photos and videos",
                                titleColor = Color(0xFFDC2626),
                                onClick = onDeleteSelected
                            )
                        }

                        // 4. View / Play Fullscreen
                        LongPressActionRow(
                            icon = if (isVideo) Icons.Default.PlayArrow else Icons.Default.OpenInFull,
                            iconTint = Color(0xFF8B5CF6),
                            title = if (isVideo) "Play Video" else "View Fullscreen",
                            subtitle = if (isVideo) "Play with scrubber, seek and duration controls" else "Pinch to zoom and inspect full resolution",
                            onClick = onViewFullscreen
                        )

                        // 5. Details & GPS Map
                        LongPressActionRow(
                            icon = Icons.Default.Info,
                            iconTint = Color(0xFFF59E0B),
                            title = "Details & Survey Metadata",
                            subtitle = "Inspect chainage, offset, GPS coordinates & map",
                            onClick = onShowDetails
                        )

                        // 6. Copy Survey Information
                        LongPressActionRow(
                            icon = Icons.Default.ContentCopy,
                            iconTint = Color(0xFF059669),
                            title = "Copy Survey Details",
                            subtitle = "Copy chainage, road and GPS to clipboard",
                            onClick = {
                                val text = photo.formattedOverlayText.ifBlank {
                                    "${photo.highwayId} | ${photo.chainageLabel ?: ""} | ${photo.roadSide} | ${photo.activity}".trim()
                                }
                                onCopyText(text)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LongPressActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Full-Screen Photo Viewer:
 * Displays full photo in original aspect ratio with interactive pinch-to-zoom and pan gestures.
 */
@Composable
fun FullScreenPhotoViewer(
    photo: PhotoRecordEntity,
    photosList: List<PhotoRecordEntity> = emptyList(),
    onPhotoSelect: (PhotoRecordEntity) -> Unit = {},
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showInfoSheet by remember { mutableStateOf(true) }

    LaunchedEffect(photo.id) {
        scale = 1f
        offset = Offset.Zero
    }

    val currentIndex = remember(photo.id, photosList) {
        photosList.indexOfFirst { it.id == photo.id }
    }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex >= 0 && currentIndex < photosList.size - 1

    val goToPrev = {
        if (hasPrev) onPhotoSelect(photosList[currentIndex - 1])
    }
    val goToNext = {
        if (hasNext) onPhotoSelect(photosList[currentIndex + 1])
    }

    val file = remember(photo.filePath) { File(photo.filePath) }
    val fileSizeMb = remember(file) { "%.2f MB".format(Locale.US, file.length() / (1024f * 1024f)) }
    val resolution = remember(photo.filePath) {
        val res = getImageResolution(photo.filePath)
        val mp = "%.1f MP".format(Locale.US, (res.first * res.second) / 1_000_000f)
        "${res.first} x ${res.second} ($mp)"
    }
    val sdf = remember { SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", Locale.US) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D16)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xDD090D16))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = photo.chainageLabel ?: photo.fileName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (photosList.size > 1 && currentIndex >= 0) {
                            Text(
                                text = "Photo ${currentIndex + 1} of ${photosList.size}",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { showInfoSheet = !showInfoSheet }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Toggle Metadata Info",
                                tint = if (showInfoSheet) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                }

                // Interactive Zoomable & Swipable Image Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                        .pointerInput(photo.id) {
                            var dragTotalX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotalX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (scale <= 1.05f) {
                                        change.consume()
                                        dragTotalX += dragAmount
                                    }
                                },
                                onDragEnd = {
                                    if (scale <= 1.05f) {
                                        if (dragTotalX < -40f && hasNext) {
                                            goToNext()
                                        } else if (dragTotalX > 40f && hasPrev) {
                                            goToPrev()
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(photo.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) {
                                    Offset(offset.x + pan.x, offset.y + pan.y)
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = file,
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )

                    // Side Navigation Arrows (Intimate Side Scrolling)
                    if (hasPrev && scale <= 1.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x80000000))
                                .clickable { goToPrev() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Photo",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    if (hasNext && scale <= 1.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x80000000))
                                .clickable { goToNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Photo",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    if (scale > 1f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(Color(0x99000000), RoundedCornerShape(20.dp))
                        ) {
                            Text("Reset Zoom", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                // Bottom Metadata Details Sheet
                AnimatedVisibility(
                    visible = showInfoSheet,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = photo.formattedOverlayText.ifBlank { "${photo.highwayId} ${photo.chainageLabel}" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Captured Date:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(sdf.format(Date(photo.timestamp)), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Resolution:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(resolution, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("File Size:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(fileSizeMb, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("GPS Position:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(
                                    "%.5f, %.5f (±%.1fm)".format(Locale.US, photo.latitude, photo.longitude, photo.accuracy),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Match Status:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(
                                    "Offset %.1fm • %s %s".format(
                                        Locale.US,
                                        photo.offsetMetres,
                                        photo.chainageConfidence,
                                        if (photo.isManualChainage) "(Manual)" else "(Auto GPS)"
                                    ),
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (photo.inspectorName.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Inspector:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Text(photo.inspectorName, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-Screen Video Player with playback controls, seeking slider, time formatting,
 * and highway survey metadata overlay.
 */
@Composable
fun FullScreenVideoPlayer(
    video: PhotoRecordEntity,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isCompleted by remember { mutableStateOf(false) }

    val file = remember(video.filePath) { File(video.filePath) }
    val fileSizeMb = remember(file) { "%.2f MB".format(Locale.US, file.length() / (1024f * 1024f)) }
    val sdf = remember { SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", Locale.US) }

    fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%02d:%02d".format(Locale.US, minutes, seconds)
    }

    // Playback ticker
    LaunchedEffect(video.filePath, isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                if (!isDraggingSlider) {
                    val pos = vv.currentPosition
                    val dur = vv.duration
                    currentPositionMs = pos
                    if (dur > 0) durationMs = dur
                }
            }
            delay(250L)
        }
    }

    // Auto-dismiss controls after 3.5s of playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500L)
            showControls = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showControls = !showControls }
            ) {
                // Video View Surface
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(video.filePath)
                            setOnPreparedListener { mp ->
                                durationMs = mp.duration
                                mp.isLooping = false
                                start()
                                isPlaying = true
                                isCompleted = false
                            }
                            setOnCompletionListener {
                                isPlaying = false
                                isCompleted = true
                                showControls = true
                            }
                            setOnErrorListener { _, what, extra ->
                                android.util.Log.e("FullScreenVideoPlayer", "Video error: $what, extra: $extra")
                                true
                            }
                        }.also { videoViewRef = it }
                    },
                    update = { vv ->
                        videoViewRef = vv
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Center Play/Pause/Replay Button Overlay
                AnimatedVisibility(
                    visible = showControls || !isPlaying,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0x99000000), CircleShape)
                            .clickable {
                                videoViewRef?.let { vv ->
                                    if (isCompleted) {
                                        vv.seekTo(0)
                                        vv.start()
                                        isPlaying = true
                                        isCompleted = false
                                    } else if (isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isCompleted -> Icons.Default.Replay
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Header Controls
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xD0000000))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }

                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                text = video.fileName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${video.highwayId} ${video.chainageLabel ?: ""}".trim(),
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row {
                            IconButton(onClick = { showInfoSheet = !showInfoSheet }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Toggle Video Info",
                                    tint = if (showInfoSheet) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                            IconButton(onClick = onShare) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share Video", tint = Color.White)
                            }
                            IconButton(onClick = onDelete) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Video", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }

                // Bottom Controls Bar with Scrubber & Time
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xD0000000))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Time & Scrubber Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(if (isDraggingSlider) sliderValue.toInt() else currentPositionMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Slider(
                                value = if (isDraggingSlider) sliderValue else currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                                onValueChange = {
                                    isDraggingSlider = true
                                    sliderValue = it
                                },
                                onValueChangeFinished = {
                                    isDraggingSlider = false
                                    videoViewRef?.seekTo(sliderValue.toInt())
                                    currentPositionMs = sliderValue.toInt()
                                },
                                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            Text(
                                text = formatTime(durationMs),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Playback Action Buttons Row
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Replay 5 seconds
                            IconButton(onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition - 5000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            }) {
                                Icon(Icons.Default.Replay5, contentDescription = "Replay 5s", tint = Color.White)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Play / Pause toggle
                            IconButton(
                                onClick = {
                                    videoViewRef?.let { vv ->
                                        if (isCompleted) {
                                            vv.seekTo(0)
                                            vv.start()
                                            isPlaying = true
                                            isCompleted = false
                                        } else if (isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Forward 5 seconds
                            IconButton(onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition + 5000).coerceAtMost(durationMs)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            }) {
                                Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = Color.White)
                            }
                        }

                        // Metadata Sheet Toggle
                        if (showInfoSheet) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = video.formattedOverlayText.ifBlank { "${video.highwayId} ${video.chainageLabel ?: ""}" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text("Recorded: ${sdf.format(Date(video.timestamp))}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    Text("File Size: $fileSizeMb", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    if (video.latitude != 0.0 || video.longitude != 0.0) {
                                        Text(
                                            "GPS: %.5f, %.5f (±%.1fm)".format(Locale.US, video.latitude, video.longitude, video.accuracy),
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp
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

/**
 * Photo Details Dialog
 */
@Composable
fun PhotoDetailsDialog(
    photo: PhotoRecordEntity,
    onDismiss: () -> Unit
) {
    val file = remember(photo.filePath) { File(photo.filePath) }
    val fileSizeMb = remember(file) { "%.2f MB (%d bytes)".format(Locale.US, file.length() / (1024f * 1024f), file.length()) }
    val resolution = remember(photo.filePath) {
        val res = getImageResolution(photo.filePath)
        val mp = "%.1f MP".format(Locale.US, (res.first * res.second) / 1_000_000f)
        "${res.first} x ${res.second} pixels ($mp)"
    }
    val sdf = remember { SimpleDateFormat("EEEE, dd MMMM yyyy 'at' hh:mm:ss a", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DetailRow("Filename", photo.fileName)
                DetailRow("Chainage", photo.chainageLabel ?: "N/A")
                DetailRow("Overlay Text", photo.formattedOverlayText)
                DetailRow("Captured Time", sdf.format(Date(photo.timestamp)))
                DetailRow("Resolution", resolution)
                DetailRow("File Size", fileSizeMb)
                DetailRow("Highway / Project", photo.highwayId)
                DetailRow("Road Side / Lane", "${photo.roadSide} / ${photo.lane}")
                DetailRow("Activity / Desc", "${photo.activity}${if (photo.customDescription.isNotBlank()) " / " + photo.customDescription else ""}")
                DetailRow("GPS Coordinates", "%.6f, %.6f".format(Locale.US, photo.latitude, photo.longitude))
                DetailRow("GPS Accuracy", "±%.1f meters".format(Locale.US, photo.accuracy))
                DetailRow("Match Offset", "%.1f meters".format(Locale.US, photo.offsetMetres))
                DetailRow("Match Type", if (photo.isManualChainage) "Manual Input" else "Auto KMZ Match")
                DetailRow("File Path", photo.filePath)

                if (photo.latitude != 0.0 || photo.longitude != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Capture Location Map",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    MiniMapPreview(
                        location = GeoPoint(photo.latitude, photo.longitude),
                        bearing = null,
                        routeData = null,
                        highwayTag = photo.highwayId,
                        gpsAccuracy = photo.accuracy,
                        showGpsBadge = true,
                        showProjectTag = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(text = value.ifBlank { "N/A" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Reads image dimensions without decoding full bitmap into memory
 */
private fun getImageResolution(filePath: String): Pair<Int, Int> {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        Pair(options.outWidth, options.outHeight)
    } catch (e: Exception) {
        Pair(0, 0)
    }
}

/**
 * Share a single photo
 */
private fun shareSinglePhoto(context: Context, photo: PhotoRecordEntity) {
    try {
        val file = File(photo.filePath)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "Media file does not exist on device", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) || photo.filePath.endsWith(".mp4", ignoreCase = true)
        val dynamicHighwayText = if (photo.formattedOverlayText.isNotBlank()) {
            photo.formattedOverlayText
        } else {
            val parts = listOfNotNull(
                photo.highwayId.ifBlank { null },
                photo.packageSection.ifBlank { null },
                photo.chainageLabel?.ifBlank { null },
                photo.roadSide.ifBlank { null },
                photo.lane.ifBlank { null },
                photo.activity.ifBlank { null }
            )
            parts.joinToString(" ")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, dynamicHighwayText)
            clipData = android.content.ClipData.newUri(context.contentResolver, photo.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, if (isVideo) "Share Highway Video" else "Share Highway Photo")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error sharing media: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * Share multiple photos or videos
 */
private fun shareMultiplePhotos(context: Context, photos: List<PhotoRecordEntity>) {
    try {
        val uris = ArrayList<Uri>()
        val textLines = mutableListOf<String>()
        photos.forEach { p ->
            val file = File(p.filePath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                uris.add(uri)
                val t = if (p.formattedOverlayText.isNotBlank()) p.formattedOverlayText else p.fileName
                textLines.add(t)
            }
        }
        if (uris.isEmpty()) {
            android.widget.Toast.makeText(context, "No existing media files found to share", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val hasVideos = photos.any { it.fileName.endsWith(".mp4", ignoreCase = true) || it.filePath.endsWith(".mp4", ignoreCase = true) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (hasVideos) "*/*" else "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, textLines.joinToString("\n\n"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (uris.isNotEmpty()) {
                val clipData = android.content.ClipData.newUri(context.contentResolver, "Highway Media", uris[0])
                for (i in 1 until uris.size) {
                    clipData.addItem(android.content.ClipData.Item(uris[i]))
                }
                this.clipData = clipData
            }
        }
        val chooser = Intent.createChooser(intent, "Share Selected Media (${uris.size})")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error sharing media: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
