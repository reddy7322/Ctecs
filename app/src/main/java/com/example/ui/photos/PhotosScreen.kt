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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var viewingPhoto by remember { mutableStateOf<PhotoRecordEntity?>(null) }
    var photoToDelete by remember { mutableStateOf<PhotoRecordEntity?>(null) }
    var photoForDetails by remember { mutableStateOf<PhotoRecordEntity?>(null) }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateListOf<Long>() }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Dropdown menu states
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    // Collapsed groups state
    val collapsedGroups = remember { mutableStateListOf<String>() }

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
    val filteredSortedPhotos = remember(photos, searchQuery, selectedFilter, gallerySortOption) {
        val filtered = photos.filter { p ->
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
                        TextButton(onClick = {
                            if (selectedPhotoIds.size == filteredSortedPhotos.size) {
                                selectedPhotoIds.clear()
                            } else {
                                selectedPhotoIds.clear()
                                selectedPhotoIds.addAll(filteredSortedPhotos.map { it.id })
                            }
                        }) {
                            Text(
                                text = if (selectedPhotoIds.size == filteredSortedPhotos.size) "Deselect All" else "Select All",
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
                                tint = if (selectedPhotoIds.isNotEmpty()) Color(0xFFEF4444) else Color.Gray
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
                                text = "Photos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${filteredSortedPhotos.size} of ${photos.size} items",
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
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (photos.isEmpty()) "No highway photos captured yet" else "No photos match filter criteria",
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
                                                                    viewingPhoto = photo
                                                                }
                                                            },
                                                            onLongClick = {
                                                                if (!isSelectionMode) {
                                                                    isSelectionMode = true
                                                                    selectedPhotoIds.add(photo.id)
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
                                                viewingPhoto = photo
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedPhotoIds.add(photo.id)
                                            }
                                        },
                                        onShare = { shareSinglePhoto(context, photo) },
                                        onDelete = { photoToDelete = photo },
                                        onShowDetails = { photoForDetails = photo }
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
            onDismiss = { viewingPhoto = null },
            onShare = { shareSinglePhoto(context, photo) },
            onDelete = {
                viewingPhoto = null
                photoToDelete = photo
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
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to permanently delete this photo (${photo.fileName})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePhoto(photo)
                        photoToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete Selected Photos") },
            text = { Text("Are you sure you want to permanently delete ${selectedPhotoIds.size} selected photos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedList = photos.filter { it.id in selectedPhotoIds }
                        viewModel.deletePhotos(selectedList)
                        selectedPhotoIds.clear()
                        isSelectionMode = false
                        showBulkDeleteDialog = false
                    }
                ) {
                    Text("Delete All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Grid Card Component:
 * Uses ContentScale.Fit inside a dark neutral background card so the original aspect ratio is displayed fully without cropping or stretching.
 */
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
            .clickable(onClick = onClick)
            .testTag("photo_card_${photo.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF0F172A))
        ) {
            val file = remember(photo.filePath) { File(photo.filePath) }

            // Display photo in complete original aspect ratio (ContentScale.Fit)
            AsyncImage(
                model = file,
                contentDescription = photo.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

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
            .clickable(onClick = onClick)
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
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
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
 * Full-Screen Photo Viewer:
 * Displays full photo in original aspect ratio with interactive pinch-to-zoom and pan gestures.
 */
@Composable
fun FullScreenPhotoViewer(
    photo: PhotoRecordEntity,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showInfoSheet by remember { mutableStateOf(true) }

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

                    Text(
                        text = photo.chainageLabel ?: photo.fileName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

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

                // Interactive Zoomable Image Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                        .pointerInput(Unit) {
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
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Highway Photo: ${photo.formattedOverlayText}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Highway Photo"))
    } catch (ignored: Exception) {}
}

/**
 * Share multiple photos
 */
private fun shareMultiplePhotos(context: Context, photos: List<PhotoRecordEntity>) {
    try {
        val uris = ArrayList<Uri>()
        photos.forEach { p ->
            val file = File(p.filePath)
            if (file.exists()) {
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            }
        }
        if (uris.isEmpty()) return

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, "Sharing ${uris.size} Highway Photos")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Selected Photos"))
    } catch (ignored: Exception) {}
}
