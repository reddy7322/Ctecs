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
    }

    // True lifecycle-aware GPS & sensor management: starts on ON_START and cleanly stops
    // hardware sensors + idle GPS on ON_STOP when app enters background or screen locks.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.startLocation()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.stopLocationIfIdle()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            viewModel.startLocation()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationIfIdle()
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
            when {
                currentTab == AppTab.HIGHWAY_TOOLS && previousTabBeforeTools != null ->
                    viewModel.setTab(previousTabBeforeTools ?: AppTab.HOME)
                currentTab == AppTab.WORK_PLANNING ->
                    viewModel.setTab(AppTab.WEATHER)
                else ->
                    viewModel.setTab(AppTab.HOME)
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentTab != AppTab.CAMERA && currentTab != AppTab.SURVEY && currentTab != AppTab.WEATHER && currentTab != AppTab.WORK_PLANNING) {
                val c = com.example.ui.theme.CtecsTheme.colors
                val selectedPillBg = c.accent.copy(alpha = if (c.isDark) 0.16f else 0.12f)
                val selectedIconColor = c.accent
                val unselectedIconColor = c.textMuted

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.background)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                ) {
                    // Subtle smart fade top transition into seamless bottom navigation
                    com.example.ui.theme.SmartFadeDivider(
                        color = if (c.isDark) c.elevatedSurface.copy(alpha = 0.65f) else c.secondarySurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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

                        tabs.forEach { (tab, icon, label) ->
                            val isSelected = currentTab == tab
                            val iconTint by animateColorAsState(
                                targetValue = if (isSelected) selectedIconColor else unselectedIconColor,
                                animationSpec = tween(200),
                                label = "icon_tint"
                            )
                            val bgTint by animateColorAsState(
                                targetValue = if (isSelected) selectedPillBg else Color.Transparent,
                                animationSpec = tween(200),
                                label = "icon_bg"
                            )

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (tab == AppTab.HIGHWAY_TOOLS) {
                                            previousTabBeforeTools = currentTab
                                        }
                                        viewModel.setTab(tab)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("nav_item_${tab.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(if (isSelected) 48.dp else 40.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(bgTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    color = iconTint,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1
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
                    onNavigateToNotes = { viewModel.setTab(AppTab.SMART_NOTES) },
                    onNavigateToSurvey = { viewModel.setTab(AppTab.SURVEY) },
                    onNavigateToWeather = { viewModel.setTab(AppTab.WEATHER) }
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
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) },
                    onNavigateToSurvey = { viewModel.setTab(AppTab.SURVEY) }
                )
                AppTab.HIGHWAY_TOOLS -> SmartHighwayToolsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(previousTabBeforeTools ?: AppTab.HOME) }
                )
                AppTab.SMART_NOTES -> SmartNotesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) }
                )
                AppTab.SURVEY -> com.example.ui.survey.SurveyScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setTab(AppTab.HOME) }
                )
                AppTab.WEATHER -> {
                    val weatherUiState by viewModel.weatherUiState.collectAsStateWithLifecycle()
                    val forecastBundle by viewModel.weatherForecastBundle.collectAsStateWithLifecycle()
                    val locName by viewModel.currentLocationName.collectAsStateWithLifecycle()
                    val camSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()
                    val weatherIntelSettings by viewModel.weatherIntelligenceSettings.collectAsStateWithLifecycle()
                    val assessments by viewModel.highwayWorkAssessments.collectAsStateWithLifecycle()
                    val selectedActivity by viewModel.selectedWeatherWorkActivity.collectAsStateWithLifecycle()
                    val hourlyTimeline by viewModel.hourlyWorkSuitabilityTimeline.collectAsStateWithLifecycle()
                    val siteMeasurements by viewModel.actualSiteMeasurements.collectAsStateWithLifecycle()
                    val workTargetInput by viewModel.workTargetInput.collectAsStateWithLifecycle()
                    val workTargetPrediction by viewModel.workTargetPrediction.collectAsStateWithLifecycle()
                    val delayRiskSummary by viewModel.weatherDelayRiskSummary.collectAsStateWithLifecycle()
                    val actionableAlerts by viewModel.actionableWeatherAlerts.collectAsStateWithLifecycle()
                    val liveMatch by viewModel.liveMatchResult.collectAsStateWithLifecycle()
                    val locState by viewModel.locationState.collectAsStateWithLifecycle()
                    val projProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
                    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
                    val weatherExpandedSections by viewModel.weatherExpandedSections.collectAsStateWithLifecycle()
                    val activeHighwayWorkPlan by viewModel.activeHighwayWorkPlan.collectAsStateWithLifecycle()
                    val savedHighwayWorkPlans by viewModel.savedHighwayWorkPlans.collectAsStateWithLifecycle()
                    val machineryCatalog by viewModel.machineryCatalog.collectAsStateWithLifecycle()
                    val isRefreshingMachineryCatalog by viewModel.isRefreshingMachineryCatalog.collectAsStateWithLifecycle()
                    val machineryCatalogLastRefreshedMs by viewModel.machineryCatalogLastRefreshedMs.collectAsStateWithLifecycle()

                    var lastValidHighwaySnapshot by remember {
                        mutableStateOf<com.example.ui.weather.LiveHighwayLocationSnapshot?>(null)
                    }
                    val liveHighwayLocation = remember(
                        liveMatch,
                        locState,
                        projProfile,
                        activeRoute,
                        locName,
                        lastValidHighwaySnapshot
                    ) {
                        com.example.ui.weather.buildLiveHighwayLocationSnapshot(
                            matchResult = liveMatch,
                            locationState = locState,
                            projectProfile = projProfile,
                            activeRoute = activeRoute,
                            resolvedLocationName = locName,
                            lastKnownValid = lastValidHighwaySnapshot
                        )
                    }
                    LaunchedEffect(
                        liveHighwayLocation.currentChainageText,
                        liveHighwayLocation.isChainageStale,
                        liveHighwayLocation.sideOfRoad,
                        liveHighwayLocation.roadOrProjectName
                    ) {
                        if (liveHighwayLocation.currentChainageText.isNotBlank() && !liveHighwayLocation.isChainageStale) {
                            lastValidHighwaySnapshot = liveHighwayLocation
                        }
                    }

                    com.example.ui.weather.WeatherDashboardScreen(
                        weatherUiState = weatherUiState,
                        forecastBundle = forecastBundle,
                        resolvedLocationName = locName,
                        is24HourFormat = camSettings.is24HourFormat,
                        settings = weatherIntelSettings,
                        assessments = assessments,
                        selectedActivity = selectedActivity,
                        hourlyWorkTimeline = hourlyTimeline,
                        siteMeasurements = siteMeasurements,
                        workTargetInput = workTargetInput,
                        workTargetPrediction = workTargetPrediction,
                        delayRiskSummary = delayRiskSummary,
                        actionableAlerts = actionableAlerts,
                        onSelectActivity = { viewModel.selectWeatherWorkActivity(it) },
                        onUpdateContractSpec = { viewModel.updateContractSpecificationConfig(it) },
                        onUpdateSiteMeasurements = { viewModel.updateActualSiteMeasurements(it) },
                        onUpdateWorkTargetInput = { viewModel.updateWorkTargetInput(it) },
                        onRefreshWeather = { viewModel.refreshWeather(force = true) },
                        onNavigateBack = { viewModel.setTab(AppTab.HOME) },
                        liveHighwayLocation = liveHighwayLocation,
                        expandedSections = weatherExpandedSections,
                        onToggleSection = { viewModel.toggleWeatherSection(it) },
                        highwayWorkPlan = activeHighwayWorkPlan,
                        savedHighwayWorkPlans = savedHighwayWorkPlans,
                        machineryCatalog = machineryCatalog,
                        isRefreshingMachineryCatalog = isRefreshingMachineryCatalog,
                        machineryCatalogLastRefreshedMs = machineryCatalogLastRefreshedMs,
                        onUpdateHighwayWorkPlan = { viewModel.updateHighwayWorkPlan(it) },
                        onSaveHighwayWorkPlan = { viewModel.saveCurrentHighwayWorkPlan() },
                        onLoadHighwayWorkPlan = { viewModel.loadSavedHighwayWorkPlan(it) },
                        onDeleteSavedHighwayWorkPlan = { viewModel.deleteSavedHighwayWorkPlan(it) },
                        onClearHighwayWorkPlan = { viewModel.clearHighwayWorkPlan() },
                        onRefreshMachineryCatalog = { viewModel.refreshMachineryCatalog() },
                        onUpdateWeatherSettings = { viewModel.updateWeatherIntelligenceSettings(it) },
                        onNavigateToWorkPlanning = { viewModel.setTab(AppTab.WORK_PLANNING) }
                    )
                }
                AppTab.WORK_PLANNING -> {
                    val activeHighwayWorkPlan by viewModel.activeHighwayWorkPlan.collectAsStateWithLifecycle()
                    val savedHighwayWorkPlans by viewModel.savedHighwayWorkPlans.collectAsStateWithLifecycle()
                    val machineryCatalog by viewModel.machineryCatalog.collectAsStateWithLifecycle()
                    val isRefreshingMachineryCatalog by viewModel.isRefreshingMachineryCatalog.collectAsStateWithLifecycle()
                    val workPlanningSettings by viewModel.workPlanningSettings.collectAsStateWithLifecycle()
                    val workPlanSaveStatus by viewModel.workPlanSaveStatus.collectAsStateWithLifecycle()
                    val localWorkPlanBackups by viewModel.localWorkPlanBackups.collectAsStateWithLifecycle()
                    val forecastBundle by viewModel.weatherForecastBundle.collectAsStateWithLifecycle()
                    val weatherIntelSettings by viewModel.weatherIntelligenceSettings.collectAsStateWithLifecycle()
                    val siteMeasurements by viewModel.actualSiteMeasurements.collectAsStateWithLifecycle()

                    com.example.ui.workplan.WorkPlanningScreen(
                        plan = activeHighwayWorkPlan,
                        savedPlans = savedHighwayWorkPlans,
                        machineryCatalog = machineryCatalog,
                        isRefreshingCatalog = isRefreshingMachineryCatalog,
                        workPlanningSettings = workPlanningSettings,
                        saveStatus = workPlanSaveStatus,
                        localBackups = localWorkPlanBackups,
                        forecastBundle = forecastBundle,
                        contractSpec = weatherIntelSettings.contractSpec,
                        siteMeasurements = siteMeasurements,
                        animationsEnabled = weatherIntelSettings.animationsEnabled,
                        staleThresholdMs = weatherIntelSettings.staleDataThresholdMinutes.toLong() * 60_000L,
                        onUpdatePlan = { viewModel.updateHighwayWorkPlan(it) },
                        onNewPlan = { viewModel.createNewHighwayWorkPlan() },
                        onSavePlan = { viewModel.saveCurrentHighwayWorkPlan() },
                        onRetrySave = { viewModel.retrySaveHighwayWorkPlan() },
                        onOpenPlan = { viewModel.loadSavedHighwayWorkPlan(it) },
                        onDuplicatePlan = { viewModel.duplicateHighwayWorkPlan(it) },
                        onRenamePlan = { id, title -> viewModel.renameHighwayWorkPlan(id, title) },
                        onDeletePlan = { viewModel.deleteSavedHighwayWorkPlan(it) },
                        onUpdateWorkPlanningSettings = { viewModel.updateWorkPlanningSettings(it) },
                        onRefreshMachineryCatalog = { viewModel.refreshMachineryCatalog() },
                        onBuildBackupJson = { viewModel.createWorkPlansBackupJson() },
                        onInspectBackupJson = { viewModel.inspectWorkPlansBackup(it) },
                        onApplyBackupImport = { insp, res -> viewModel.applyWorkPlansImport(insp, res) },
                        onSaveLocalSnapshot = { viewModel.saveLocalWorkPlanSnapshot() },
                        onSafeRestoreLocalBackup = { viewModel.safeRestoreLocalWorkPlanBackup(it) },
                        onNavigateToWeather = { viewModel.setTab(AppTab.WEATHER) },
                        onNavigateBack = { viewModel.setTab(AppTab.HOME) },
                        onNotifyUser = { msg -> viewModel.showToast(msg, ToastType.INFO) }
                    )
                }
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

    // Launch-time Interrupted Survey Resume Dialog
    val interruptedSession by viewModel.interruptedSurveySession.collectAsStateWithLifecycle()
    interruptedSession?.let { session ->
        com.example.ui.survey.ResumeSessionDialog(
            session = session,
            onResume = { viewModel.resumeInterruptedSurvey(session) },
            onDiscard = { viewModel.discardInterruptedSurvey(session) },
            onDismiss = { viewModel.dismissInterruptedSurveyPrompt() }
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

    val c = com.example.ui.theme.CtecsTheme.colors
    val accentColor = when (displayMessage?.type) {
        ToastType.SUCCESS -> c.success
        ToastType.ERROR -> c.error
        ToastType.WARNING -> c.warning
        ToastType.SETTINGS_SAVED -> c.accentSecondary
        ToastType.MODE_CHANGE -> c.accent
        ToastType.INFO, null -> c.accent
    }

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
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(160)),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = tween(150, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(130))
        ) {
            displayMessage?.let { msg ->
                val smartText = remember(msg.message, msg.type) {
                    formatSmartTinyFeedback(msg.message, msg.type)
                }

                Surface(
                    shape = CircleShape,
                    color = if (c.isDark) c.elevatedSurface else c.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .testTag("tiny_glass_toast")
                        .widthIn(max = 260.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Text(
                            text = smartText,
                            color = c.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.15.sp,
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
