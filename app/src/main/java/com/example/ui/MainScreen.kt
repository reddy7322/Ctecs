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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.camera.CameraScreen
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

    // BackHandler to return to Camera if on another tab
    if (currentTab != AppTab.CAMERA) {
        BackHandler {
            viewModel.setTab(AppTab.CAMERA)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = PrimaryText
            ) {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HighlightYellow,
                    selectedTextColor = HighlightYellow,
                    indicatorColor = DarkYellowSurface,
                    unselectedIconColor = SecondaryText,
                    unselectedTextColor = SecondaryText
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.CAMERA,
                    onClick = { viewModel.setTab(AppTab.CAMERA) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Camera") },
                    label = { Text("Camera") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_camera")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.PHOTOS,
                    onClick = { viewModel.setTab(AppTab.PHOTOS) },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Photos") },
                    label = { Text("Photos") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_photos")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.ROUTES,
                    onClick = { viewModel.setTab(AppTab.ROUTES) },
                    icon = { Icon(Icons.Default.AltRoute, contentDescription = "Routes") },
                    label = { Text("Routes") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_routes")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { viewModel.setTab(AppTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        },
        containerColor = PrimaryBackground,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.CAMERA -> CameraScreen(
                    viewModel = viewModel,
                    onNavigateToPhotos = { viewModel.setTab(AppTab.PHOTOS) }
                )
                AppTab.PHOTOS -> PhotosScreen(viewModel = viewModel)
                AppTab.ROUTES -> RoutesScreen(viewModel = viewModel)
                AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

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
