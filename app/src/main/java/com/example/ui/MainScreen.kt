package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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

    // BackHandler to return to Home if on another tab
    if (currentTab != AppTab.HOME) {
        BackHandler {
            viewModel.setTab(AppTab.HOME)
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
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                targetValue = if (isSelected) HighlightYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(200),
                                label = "icon_tint"
                            )
                            val bgTint by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                                animationSpec = tween(200),
                                label = "icon_bg"
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(bgTint)
                                    .clickable {
                                        viewModel.setTab(tab)
                                    }
                                    .testTag("nav_item_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = contentDesc,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
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
                    onNavigateToSettings = { viewModel.setTab(AppTab.SETTINGS) }
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
}

@Composable
fun AnimatedGlassToast(
    toastMessage: ToastMessage?,
    onDismiss: () -> Unit
) {
    if (toastMessage == null) return

    LaunchedEffect(toastMessage.id) {
        kotlinx.coroutines.delay(3000L)
        onDismiss()
    }

    val (accentColor, iconSymbol) = when (toastMessage.type) {
        ToastType.SUCCESS -> Color(0xFF22C55E) to "✓"
        ToastType.ERROR -> Color(0xFFEF4444) to "✕"
        ToastType.WARNING -> HighlightYellow to "⚠"
        ToastType.SETTINGS_SAVED -> HighlightYellow to "✓"
        ToastType.MODE_CHANGE -> Color(0xFF38BDF8) to "⚡"
        ToastType.INFO -> Color(0xFF38BDF8) to "ℹ"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                        )
                    ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) +
                    androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF2111827),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = iconSymbol,
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = toastMessage.message,
                        color = PrimaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
