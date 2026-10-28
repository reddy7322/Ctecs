package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

    // Collect UI messages
    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            when (msg) {
                is UiMessage.Success -> snackbarHostState.showSnackbar("✓ ${msg.message}")
                is UiMessage.Error -> snackbarHostState.showSnackbar("✕ ${msg.message}")
                is UiMessage.Info -> snackbarHostState.showSnackbar(msg.message)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = PrimaryBackground,
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
        kotlinx.coroutines.delay(2500L)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -40 }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -40 })
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x901A1A1A),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, HighlightYellow.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HighlightYellow)
                    )
                    Text(
                        text = toastMessage.message,
                        color = PrimaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
