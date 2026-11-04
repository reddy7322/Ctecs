package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutLinearInEasing
import com.example.ui.tools.SmartHighwayToolsScreen
import com.example.ui.notes.SmartNotesScreen
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.camera.CameraScreen
import com.example.ui.home.HomeScreen
import com.example.ui.photos.PhotosScreen
import com.example.ui.routes.RoutesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.DarkYellowSurface
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SurfaceDark

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Request permissions on startup
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (fineLocationGranted) {
            viewModel.startLocation()
        }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        viewModel.startLocation()
    }

    // Lifecycle-aware location management
    DisposableEffect(lifecycleOwner) {
        viewModel.startLocation()
        onDispose {
            viewModel.stopLocation()
        }
    }

    // Collect UI messages and route them to top glass notification
    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            when (msg) {
                is UiMessage.Success -> viewModel.showToast("✓ ${msg.message}", ToastType.SUCCESS)
                is UiMessage.Error -> viewModel.showToast("✕ ${msg.message}", ToastType.ERROR)
                is UiMessage.Info -> viewModel.showToast(msg.message, ToastType.INFO)
            }
        }
    }

    var previousTabBeforeTools by remember { mutableStateOf<AppTab?>(null) }

    // BackHandler to return to Home or previous tab if on another tab
    if (currentTab != AppTab.HOME) {
        BackHandler {
            if (currentTab == AppTab.HIGHWAY_TOOLS && previousTabBeforeTools != null) {
                viewModel.setTab(previousTabBeforeTools ?: AppTab.HOME)
            } else {
                viewModel.setTab(AppTab.HOME)
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentTab != AppTab.CAMERA) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Triple(AppTab.HOME, Icons.Default.Home, "Home"),
                            Triple(AppTab.CAMERA, Icons.Default.CameraAlt, "Camera"),
                            Triple(AppTab.PHOTOS, Icons.Default.PhotoLibrary, "Gallery"),
                            Triple(AppTab.ROUTES, Icons.Default.AltRoute, "Routes"),
                            Triple(AppTab.SETTINGS, Icons.Default.Settings, "Settings")
                        )

                        tabs.forEach { (tab, icon, contentDesc) ->
                            val isSelected = currentTab == tab
                            val iconTint by animateColorAsState(
                                targetValue = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(200),
                                label = "icon_tint"
                            )
                            val bgTint by animateColorAsState(
                                targetValue = if (isSelected) HighlightYellow else Color.Transparent,
                                animationSpec = tween(200),
                                label = "icon_bg"
                            )

                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .width(if (isSelected) 46.dp else 34.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bgTint)
                                    .clickable {
                                        if (tab == AppTab.HIGHWAY_TOOLS) {
                                            previousTabBeforeTools = currentTab
                                        }
                                        viewModel.setTab(tab)
                                    }
                                    .testTag("nav_item_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = contentDesc,
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToCamera = { viewModel.setTab(AppTab.CAMERA) },
                    onNavigateToPhotos = { viewModel.setTab(AppTab.PHOTOS) },
                    onNavigateToRoutes = { viewModel.setTab(AppTab.ROUTES) },
                    onNavigateToSettings = { viewModel.setTab(AppTab.SETTINGS) },
                    onNavigateToTools = {
                        previousTabBeforeTools = AppTab.HOME
                        viewModel.setTab(AppTab.HIGHWAY_TOOLS)
                    },
                    onNavigateToNotes = { viewModel.setTab(AppTab.SMART_NOTES) }
                )
                AppTab.CAMERA -> CameraScreen(
                    viewModel = viewModel,
                    onNavigateToHome = { viewModel.setTab(AppTab.HOME) },
                    onNavigateToPhotos = { viewModel.setTab(AppTab.PHOTOS) },
                    onNavigateToRoutes = { viewModel.setTab(AppTab.ROUTES) },
                    onNavigateToSettings = { viewModel.setTab(AppTab.SETTINGS) }
                )
                AppTab.PHOTOS -> PhotosScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) }
                )
                AppTab.ROUTES -> RoutesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) }
                )
                AppTab.HIGHWAY_TOOLS -> SmartHighwayToolsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(previousTabBeforeTools ?: AppTab.HOME) }
                )
                AppTab.SMART_NOTES -> SmartNotesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) }
                )
            }


        }
    }

    // Top In-App Glass Toast & Status Notification
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    AnimatedGlassToast(
        toastMessage = toastMessage,
        onDismiss = { viewModel.clearToast() }
    )

    // App Update Dialog
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()

    availableUpdate?.let { updateInfo ->
        com.example.ui.update.UpdateAvailableDialog(
            releaseInfo = updateInfo,
            isDownloading = isDownloadingUpdate,
            downloadProgress = updateDownloadProgress,
            onDownloadAndInstall = { viewModel.downloadAndInstallUpdate() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    // Dynamic What's New Dialog
    val showWhatsNewDialog by viewModel.showWhatsNewDialog.collectAsStateWithLifecycle()
    val whatsNewFeatures by viewModel.whatsNewFeatures.collectAsStateWithLifecycle()

    if (showWhatsNewDialog) {
        com.example.ui.update.WhatsNewDialog(
            versionName = com.example.BuildConfig.VERSION_NAME,
            features = whatsNewFeatures,
            onDismiss = { viewModel.dismissWhatsNew() }
        )
    }

    // Smart Minimalist Glass Page Loader UI with percentage animations
    val isPageLoading by viewModel.isPageLoading.collectAsStateWithLifecycle()
    val pageLoadingProgress by viewModel.pageLoadingProgress.collectAsStateWithLifecycle()
    val pageLoadingStatus by viewModel.pageLoadingStatus.collectAsStateWithLifecycle()

    com.example.ui.loader.SmartGlassPageLoader(
        isLoading = isPageLoading,
        progress = pageLoadingProgress,
        status = pageLoadingStatus,
        onDismissRequest = { viewModel.hidePageLoader() }
    )
}

@Composable
fun AnimatedGlassToast(
    toastMessage: ToastMessage?,
    onDismiss: () -> Unit
) {
    var activeMessage by remember { mutableStateOf(toastMessage) }

    LaunchedEffect(toastMessage?.id) {
        if (toastMessage != null) {
            activeMessage = toastMessage
            kotlinx.coroutines.delay(2200L)
            onDismiss()
        }
    }

    val displayMessage = toastMessage ?: activeMessage

    val accentColor = when (displayMessage?.type) {
        ToastType.SUCCESS -> Color(0xFF34D399) // Emerald
        ToastType.ERROR -> Color(0xFFF87171)   // Soft Rose
        ToastType.WARNING -> Color(0xFFFBBF24) // Amber
        ToastType.SETTINGS_SAVED -> Color(0xFF2DD4BF) // Teal
        ToastType.MODE_CHANGE -> Color(0xFF38BDF8)    // Sky
        ToastType.INFO, null -> Color(0xFF818CF8)     // Indigo
    }

    // Micro pulsing breathing animation for the status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "toast_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "toast_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = toastMessage != null && displayMessage != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = androidx.compose.animation.core.tween(160, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) + scaleOut(
                targetScale = 0.90f,
                animationSpec = androidx.compose.animation.core.tween(140)
            )
        ) {
            displayMessage?.let { msg ->
                val smartText = remember(msg.message, msg.type) {
                    formatSmartTinyFeedback(msg.message, msg.type)
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xB8090D14), // Ultra sleek minimalist frosted glass
                    border = BorderStroke(0.75.dp, Color(0x38FFFFFF)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .testTag("tiny_glass_toast")
                        .widthIn(max = 240.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Smart tiny status indicator with gentle glowing aura
                        Box(
                            modifier = Modifier.size(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = pulseAlpha * 0.35f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(5.5.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                        }

                        // Smart tiny feedback text: minimal, single-line, no clutter
                        Text(
                            text = smartText,
                            color = Color(0xFFF8FAFC),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.25.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Transforms verbose toast messages and file paths into concise, smart, tiny feedback labels.
 */
private fun formatSmartTinyFeedback(message: String, type: ToastType): String {
    val clean = message
        .removePrefix("✓ ")
        .removePrefix("✕ ")
        .removePrefix("⚠ ")
        .removePrefix("⚡ ")
        .removePrefix("ℹ ")
        .trim()

    val lower = clean.lowercase()

    return when {
        lower.startsWith("video saved") || lower.contains("dynamic overlays") -> "Video Saved"
        lower.startsWith("photo saved") -> "Photo Saved"
        lower.contains("gps location & chainage refreshed") || lower.contains("gps refreshed") -> "GPS Synced"
        lower.contains("failed to refresh gps") -> "GPS Offline"
        lower.contains("file name format") -> "Format Saved"
        lower.contains("custom logo imported") -> "Logo Added"
        lower.contains("custom logo removed") -> "Logo Removed"
        lower.contains("failed to load logo") -> "Logo Failed"
        lower.contains("export failed") -> "Export Failed"
        lower.contains("project imported") -> "Imported"
        lower.contains("project & strech updated") || lower.contains("project updated") -> "Project Saved"
        lower.startsWith("deleted ") && lower.contains("photo") -> {
            val count = clean.filter { it.isDigit() }
            if (count.isNotEmpty()) "$count Deleted" else "Deleted"
        }
        lower.startsWith("activity updated:") -> "Activity Set"
        lower.startsWith("comment set:") -> "Comment Set"
        lower.contains("comment cleared") -> "Comment Cleared"
        lower.contains("audio recording") -> {
            if (lower.contains("enabled") || lower.contains("on")) "Audio On" else "Audio Off"
        }
        lower.startsWith("theme:") -> {
            val mode = clean.substringAfter(":").trim()
            "Theme $mode"
        }
        lower.startsWith("road alignment:") -> "Road Linked"
        lower.contains("hdr mode") -> {
            if (lower.contains("active") || lower.contains("on")) "HDR On" else "HDR Off"
        }
        lower.contains("horizon level") -> {
            if (lower.contains("on")) "Level On" else "Level Off"
        }
        lower.startsWith("photo resolution:") -> {
            "Photo " + clean.substringAfter(":").trim()
        }
        lower.startsWith("video resolution:") -> {
            "Video " + clean.substringAfter(":").trim()
        }
        lower.contains("finalizing") -> "Saving..."
        lower.contains("update check") -> "Up to date"
        clean.contains(":") -> {
            val prefix = clean.substringBefore(":").trim()
            if (prefix.length in 3..15) prefix else clean.take(15)
        }
        clean.length > 18 -> {
            val words = clean.split(" ")
            if (words.size >= 2) "${words[0]} ${words[1]}" else clean.take(16)
        }
        else -> clean
    }
}
