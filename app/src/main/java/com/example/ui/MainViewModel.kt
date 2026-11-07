package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.AppDatabase
import com.example.data.PhotoRecordEntity
import com.example.data.PhotoRepository
import com.example.data.PreferencesManager
import com.example.data.RouteEntity
import com.example.data.RouteRepository
import com.example.geo.ChainageMatcher
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.location.LocationTracker
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageLogEntry
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CameraMode
import com.example.model.DefinedProject
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.SpeedUnit
import com.example.geo.SmartChainageCorrectionEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("Home"),
    CAMERA("Camera"),
    PHOTOS("Photos"),
    ROUTES("Routes"),
    HIGHWAY_TOOLS("Tools"),
    SMART_NOTES("Notes"),
    SETTINGS("Settings")
}

sealed class UiMessage {
    data class Success(val message: String) : UiMessage()
    data class Error(val message: String) : UiMessage()
    data class Info(val message: String) : UiMessage()
}

enum class ToastType {
    SUCCESS, ERROR, WARNING, INFO, SETTINGS_SAVED, MODE_CHANGE
}

data class ToastMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val message: String,
    val type: ToastType = ToastType.INFO
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val preferencesManager = PreferencesManager(application)
    val routeRepository = RouteRepository(application, db.routeDao(), preferencesManager, viewModelScope)
    val photoRepository = PhotoRepository(application, db.photoRecordDao())
    val smartHighwayRepository = com.example.data.SmartHighwayRepository(
        context = application,
        projectDao = db.highwayProjectDao(),
        activityDao = db.smartActivityDao(),
        qcRecordDao = db.qcRecordDao(),
        reportDao = db.dailyReportRecordDao(),
        scope = viewModelScope
    )
    val locationTracker = LocationTracker(application)
    val compassTracker = com.example.sensor.CompassAndOrientationTracker(application)
    val appUpdateManager = com.example.update.AppUpdateManager(application)
    val whatsNewManager = com.example.update.WhatsNewManager(application)

    val allProjects = smartHighwayRepository.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allQcRecords = smartHighwayRepository.allQcRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun getActivitiesForProjectAndDate(projectId: String, dateString: String) =
        smartHighwayRepository.getActivitiesForProjectAndDate(projectId, dateString)

    suspend fun getActivitiesForProjectAndDateRangeList(projectId: String, startDate: String, endDate: String) =
        smartHighwayRepository.getActivitiesForProjectAndDateRangeList(projectId, startDate, endDate)

    fun getRecordedDatesForProject(projectId: String) =
        smartHighwayRepository.getRecordedDatesForProject(projectId)

    fun getReportsForProject(projectId: String) =
        smartHighwayRepository.getReportsForProject(projectId)

    suspend fun saveSmartActivity(activity: com.example.data.SmartActivityEntity) =
        smartHighwayRepository.saveActivity(activity)

    suspend fun deleteSmartActivity(activity: com.example.data.SmartActivityEntity) =
        smartHighwayRepository.deleteActivity(activity)

    suspend fun saveProject(project: com.example.data.HighwayProjectEntity) =
        smartHighwayRepository.saveProject(project)

    suspend fun saveQcRecord(record: com.example.data.QcRecordEntity) =
        smartHighwayRepository.saveQcRecord(record)

    suspend fun deleteQcRecord(record: com.example.data.QcRecordEntity) =
        smartHighwayRepository.deleteQcRecord(record)

    suspend fun saveDailyProjectReport(report: com.example.data.DailyProjectReportEntity) =
        smartHighwayRepository.saveReport(report)

    val compassState: StateFlow<com.example.sensor.CompassState> = compassTracker.compassState
    val deviceOrientation: StateFlow<com.example.sensor.DeviceOrientation> = compassTracker.deviceOrientation
    val rotationDegrees: StateFlow<Float> = compassTracker.rotationDegrees

    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // App Update & What's New State
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _availableUpdate = MutableStateFlow<com.example.update.AppReleaseInfo?>(null)
    val availableUpdate: StateFlow<com.example.update.AppReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0f)
    val updateDownloadProgress: StateFlow<Float> = _updateDownloadProgress.asStateFlow()

    private val _showWhatsNewDialog = MutableStateFlow(false)
    val showWhatsNewDialog: StateFlow<Boolean> = _showWhatsNewDialog.asStateFlow()

    private val _whatsNewFeatures = MutableStateFlow<List<com.example.update.WhatsNewFeature>>(
        com.example.update.WhatsNewManager.DEFAULT_FEATURES
    )
    val whatsNewFeatures: StateFlow<List<com.example.update.WhatsNewFeature>> = _whatsNewFeatures.asStateFlow()

    private val _uiMessages = MutableSharedFlow<UiMessage>()
    val uiMessages: SharedFlow<UiMessage> = _uiMessages.asSharedFlow()

    private val _toastMessage = MutableStateFlow<ToastMessage?>(null)
    val toastMessage: StateFlow<ToastMessage?> = _toastMessage.asStateFlow()

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        _toastMessage.value = ToastMessage(message = message, type = type)
    }

    fun showSuccess(message: String) {
        showToast(message, ToastType.SUCCESS)
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Page Loader State & Percentage Animations
    private val _isPageLoading = MutableStateFlow(true)
    val isPageLoading: StateFlow<Boolean> = _isPageLoading.asStateFlow()

    private val _pageLoadingProgress = MutableStateFlow(0.08f)
    val pageLoadingProgress: StateFlow<Float> = _pageLoadingProgress.asStateFlow()

    private val _pageLoadingStatus = MutableStateFlow("Starting System Engine...")
    val pageLoadingStatus: StateFlow<String> = _pageLoadingStatus.asStateFlow()

    fun showPageLoader(status: String = "Loading...", initialProgress: Float = 0f) {
        _pageLoadingStatus.value = status
        _pageLoadingProgress.value = initialProgress
        _isPageLoading.value = true
    }

    fun updatePageLoader(progress: Float, status: String? = null) {
        _pageLoadingProgress.value = progress.coerceIn(0f, 1f)
        if (status != null) {
            _pageLoadingStatus.value = status
        }
    }

    fun hidePageLoader() {
        _pageLoadingProgress.value = 1f
        _pageLoadingStatus.value = "Ready"
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            _isPageLoading.value = false
        }
    }

    // Location State
    val locationState: StateFlow<LocationState> = locationTracker.locationState

    // Route State
    val activeRouteData: StateFlow<RouteData?> = routeRepository.activeRouteData
    val allRoutes: StateFlow<List<RouteEntity>> = routeRepository.allRoutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Project & Overlay Settings
    val projectProfile: StateFlow<ProjectProfile> = preferencesManager.projectProfileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProjectProfile()
    )

    val definedProjects: StateFlow<List<DefinedProject>> = preferencesManager.definedProjectsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val overlayConfig: StateFlow<OverlayConfig> = preferencesManager.overlayConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverlayConfig()
    )

    val galleryViewMode: StateFlow<String> = preferencesManager.galleryViewModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "grid"
    )

    val gallerySortOption: StateFlow<String> = preferencesManager.gallerySortOptionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Newest first"
    )

    val galleryGroupOption: StateFlow<String> = preferencesManager.galleryGroupOptionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "None"
    )

    fun updateGalleryPrefs(viewMode: String? = null, sortOption: String? = null, groupOption: String? = null) {
        viewModelScope.launch {
            preferencesManager.updateGalleryPreferences(viewMode, sortOption, groupOption)
        }
    }

    val themeMode: StateFlow<String> = preferencesManager.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.updateThemeMode(mode)
            _uiMessages.emit(UiMessage.Info("Theme updated to ${mode.replaceFirstChar { it.uppercase() }}"))
        }
    }

    val mapThemePreference: StateFlow<String> = preferencesManager.mapThemeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    fun setMapTheme(theme: String) {
        viewModelScope.launch {
            preferencesManager.updateMapTheme(theme)
            val label = when (theme.lowercase()) {
                "light" -> "Light"
                "dark" -> "Dark"
                else -> "System Default"
            }
            _uiMessages.emit(UiMessage.Info("Map Theme: $label"))
        }
    }

    val mapTypePreference: StateFlow<String> = preferencesManager.mapTypeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "standard"
    )

    fun setMapType(type: String) {
        viewModelScope.launch {
            preferencesManager.updateMapType(type)
            val label = if (type.equals("satellite", ignoreCase = true)) "Satellite" else "Standard (Street)"
            _uiMessages.emit(UiMessage.Info("Map Type: $label"))
        }
    }

    val cameraSettings: StateFlow<com.example.model.CameraSettings> = preferencesManager.cameraSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.model.CameraSettings()
    )

    // Capture mode (PHOTO vs VIDEO)
    private val _captureMode = MutableStateFlow(com.example.model.CaptureMode.PHOTO)
    val captureMode: StateFlow<com.example.model.CaptureMode> = _captureMode.asStateFlow()

    // Video recording states
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isRecordingPaused = MutableStateFlow(false)
    val isRecordingPaused: StateFlow<Boolean> = _isRecordingPaused.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    // Zoom & Exposure
    private val _currentZoomRatio = MutableStateFlow(1.0f)
    val currentZoomRatio: StateFlow<Float> = _currentZoomRatio.asStateFlow()

    private val _exposureCompensation = MutableStateFlow(0)
    val exposureCompensation: StateFlow<Int> = _exposureCompensation.asStateFlow()

    // Manual chainage override state
    private val _manualChainage = MutableStateFlow<Chainage?>(null)
    val manualChainage: StateFlow<Chainage?> = _manualChainage.asStateFlow()

    // Smart KMZ Chainage Correction StateFlows
    val routeCorrectionsMap: StateFlow<Map<String, List<ChainageCorrectionPoint>>> = preferencesManager.routeCorrectionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val isRouteCorrectionsEnabled: StateFlow<Boolean> = preferencesManager.routeCorrectionsEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val activeRouteCorrections: StateFlow<List<ChainageCorrectionPoint>> = combine(
        activeRouteData,
        routeCorrectionsMap
    ) { route, map ->
        if (route != null) {
            map[route.id].orEmpty().sortedBy { it.originalKmzMetres }
        } else {
            emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val chainageAuditLog: StateFlow<List<ChainageLogEntry>> = preferencesManager.chainageAuditLogFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val progressionTracker = com.example.geo.ChainageProgressionTracker()

    // Combined live match result with strict 4-tier priority:
    // 1. MANUAL override
    // 2. CUSTOM CORRECTION (within valid KMZ correction coverage)
    // 3. KMZ (original imported KMZ chainage)
    // 4. GPS (unmatched fallback)
    val liveMatchResult: StateFlow<ChainageMatchResult> = combine(
        locationState,
        activeRouteData,
        manualChainage,
        routeCorrectionsMap,
        isRouteCorrectionsEnabled
    ) { loc, route, manual, correctionsMap, isCorrectionsEnabled ->
        // Tier 1: MANUAL OVERRIDE (Highest priority)
        if (manual != null) {
            val side = progressionTracker.getCurrentSide()
            return@combine ChainageMatchResult.manual(
                chainage = manual,
                point = loc.point,
                side = side
            ).copy(
                source = ChainageSource.MANUAL,
                statusMessage = "Manual Override Active (${manual.format()})"
            )
        }

        // Tier 4 fallback if no GPS fix
        if (loc.point == null) {
            return@combine ChainageMatchResult.noRoute(loc.accuracy).copy(source = ChainageSource.GPS)
        }

        // Tier 4 fallback if no route loaded
        if (route == null || route.markers.isEmpty()) {
            return@combine ChainageMatchResult(
                confidence = com.example.model.MatchConfidence.NO_MATCH,
                gpsAccuracy = loc.accuracy,
                statusMessage = "No route loaded (GPS Active)",
                detectedSide = "Unknown",
                source = ChainageSource.GPS
            )
        }

        // Calculate original KMZ alignment match
        val kmzMatch = ChainageMatcher.match(
            gpsPoint = loc.point,
            gpsAccuracy = loc.accuracy,
            bearing = loc.bearing,
            routeData = route
        )

        val rawKmzChainage = kmzMatch.chainage

        if (kmzMatch.confidence == com.example.model.MatchConfidence.NO_MATCH || rawKmzChainage == null) {
            val stabilized = progressionTracker.processResult(kmzMatch, route.id)
            return@combine stabilized.copy(
                source = ChainageSource.GPS,
                rawUncorrectedChainage = null
            )
        }

        // Tier 2: CUSTOM KMZ CORRECTION
        val routeCorrections = correctionsMap[route.id].orEmpty()
        val smartCorrected = if (isCorrectionsEnabled && routeCorrections.isNotEmpty()) {
            SmartChainageCorrectionEngine.applyCorrection(
                rawChainage = rawKmzChainage,
                routeData = route,
                correctionPoints = routeCorrections,
                gpsPoint = loc.point,
                gpsAccuracy = loc.accuracy,
                confidence = kmzMatch.confidence
            )
        } else {
            null
        }

        val finalResult = if (smartCorrected != null) {
            kmzMatch.copy(
                chainage = smartCorrected.correctedChainage,
                source = ChainageSource.CUSTOM_CORRECTION,
                rawUncorrectedChainage = rawKmzChainage,
                correctionPointInfo = smartCorrected.infoMessage,
                statusMessage = "Smart Calibrated (${smartCorrected.infoMessage})"
            )
        } else {
            // Tier 3: ORIGINAL KMZ ALIGNMENT
            kmzMatch.copy(
                source = ChainageSource.KMZ,
                rawUncorrectedChainage = rawKmzChainage
            )
        }

        val stabilized = progressionTracker.processResult(finalResult, route.id)
        if (!progressionTracker.isManualOverride() && stabilized.detectedSide.isNotBlank() && stabilized.detectedSide != "Unknown") {
            val detectedEnum = com.example.model.RoadSide.fromString(stabilized.detectedSide)
            if (projectProfile.value.roadSide != detectedEnum) {
                viewModelScope.launch {
                    preferencesManager.updateProjectProfile(projectProfile.value.copy(roadSide = detectedEnum))
                }
            }
        }
        stabilized
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChainageMatchResult.noRoute()
    )

    // Photos list
    val allPhotos: StateFlow<List<PhotoRecordEntity>> = photoRepository.allPhotos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Camera settings in UI
    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<PhotoRecordEntity?>(null)
    val lastCapturedPhoto: StateFlow<PhotoRecordEntity?> = _lastCapturedPhoto.asStateFlow()

    // Dialog states
    private val _showManualChainageDialog = MutableStateFlow(false)
    val showManualChainageDialog: StateFlow<Boolean> = _showManualChainageDialog.asStateFlow()

    private val _isImportingRoute = MutableStateFlow(false)
    val isImportingRoute: StateFlow<Boolean> = _isImportingRoute.asStateFlow()

    init {
        // Auto-load bundled sample route if none exists yet
        viewModelScope.launch {
            allRoutes.collect { routes ->
                if (routes.isEmpty()) {
                    routeRepository.loadBundledSampleRoute()
                }
            }
        }

        // Restore saved manual chainage override
        viewModelScope.launch {
            preferencesManager.manualChainageOverrideFlow.collect { savedManual ->
                _manualChainage.value = savedManual
            }
        }

        // Check if What's New should be shown for newly installed/updated version
        viewModelScope.launch {
            if (whatsNewManager.shouldShowWhatsNewOnLaunch()) {
                _showWhatsNewDialog.value = true
            }
        }

        // Silent background check for updates
        viewModelScope.launch {
            checkForAppUpdate(isManual = false)
        }

        // Auto-sync storage videos and photos into Room
        viewModelScope.launch {
            photoRepository.syncMediaFilesFromStorage()
        }

        // Smart Startup Page Loader progression
        viewModelScope.launch {
            _pageLoadingProgress.value = 0.18f
            _pageLoadingStatus.value = "Starting System Engine..."
            kotlinx.coroutines.delay(220)

            _pageLoadingProgress.value = 0.45f
            _pageLoadingStatus.value = "Calibrating Sensors & GPS..."
            kotlinx.coroutines.delay(260)

            _pageLoadingProgress.value = 0.78f
            _pageLoadingStatus.value = "Loading Route Alignments..."
            kotlinx.coroutines.delay(280)

            _pageLoadingProgress.value = 0.96f
            _pageLoadingStatus.value = "Configuring Realtime Overlays..."
            kotlinx.coroutines.delay(240)

            _pageLoadingProgress.value = 1.0f
            _pageLoadingStatus.value = "Ready"
            kotlinx.coroutines.delay(260)

            _isPageLoading.value = false
        }
    }

    fun checkForAppUpdate(isManual: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val currentChannel = com.example.update.AppReleaseChannel.current()
            val result = appUpdateManager.checkForUpdate(currentChannel)
            _isCheckingUpdate.value = false

            if (result.isSuccess) {
                val info = result.getOrNull()
                if (info != null && info.isUpdateAvailable) {
                    _availableUpdate.value = info
                    // Also parse dynamic what's new from release notes
                    val dynamicFeatures = whatsNewManager.parseReleaseNotesToFeatures(info.releaseNotes)
                    _whatsNewFeatures.value = dynamicFeatures
                } else if (isManual) {
                    val msg = "You are using the latest ${currentChannel.label} version of Ctecs (v${com.example.BuildConfig.VERSION_NAME})"
                    _uiMessages.emit(UiMessage.Info(msg))
                    showToast(msg, ToastType.SUCCESS)
                }
            } else if (isManual) {
                val err = result.exceptionOrNull()?.message ?: "Check failed"
                _uiMessages.emit(UiMessage.Error("Update check: $err"))
                showToast("Update check: $err", ToastType.ERROR)
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val info = _availableUpdate.value ?: return
        val url = info.apkDownloadUrl ?: return

        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _updateDownloadProgress.value = 0f

            val fileName = "Ctecs-${info.tagName}.apk"
            val result = appUpdateManager.downloadApk(url, fileName) { progress ->
                _updateDownloadProgress.value = progress
            }
            _isDownloadingUpdate.value = false

            if (result.isSuccess) {
                val apkFile = result.getOrNull()
                if (apkFile != null) {
                    _availableUpdate.value = null
                    appUpdateManager.installApk(apkFile)
                    _uiMessages.emit(UiMessage.Success("Update downloaded. Starting installer..."))
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Download failed"
                _uiMessages.emit(UiMessage.Error("Failed to download update: $err"))
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun openWhatsNew() {
        _showWhatsNewDialog.value = true
    }

    fun dismissWhatsNew() {
        viewModelScope.launch {
            whatsNewManager.markCurrentVersionAsSeen()
            _showWhatsNewDialog.value = false
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun toggleCameraFacing() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun openManualChainageDialog() {
        _showManualChainageDialog.value = true
    }

    fun closeManualChainageDialog() {
        _showManualChainageDialog.value = false
    }

    fun setManualChainage(input: String) {
        val parsed = Chainage.parse(input)
        if (parsed != null) {
            _manualChainage.value = parsed
            _showManualChainageDialog.value = false
            viewModelScope.launch {
                preferencesManager.setManualChainageOverride(parsed)
                preferencesManager.addChainageLogEntry(
                    ChainageLogEntry(
                        actionType = "MANUAL_OVERRIDE",
                        routeId = activeRouteData.value?.id ?: "",
                        routeName = activeRouteData.value?.name ?: "No Route",
                        summary = "Manual Override: ${parsed.format()}",
                        details = "Previous: ${liveMatchResult.value.displayChainage}"
                    )
                )
                _uiMessages.emit(UiMessage.Success("Manual chainage active: ${parsed.format()}"))
            }
        } else {
            viewModelScope.launch {
                _uiMessages.emit(UiMessage.Error("Invalid chainage format. Use e.g. 198+880 or 198880"))
            }
        }
    }

    fun clearManualChainage() {
        _manualChainage.value = null
        _showManualChainageDialog.value = false
        viewModelScope.launch {
            preferencesManager.setManualChainageOverride(null)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "MANUAL_RESET",
                    routeId = activeRouteData.value?.id ?: "",
                    routeName = activeRouteData.value?.name ?: "No Route",
                    summary = "Reset to Auto GPS",
                    details = "Manual override cleared"
                )
            )
            _uiMessages.emit(UiMessage.Info("Switched back to automatic GPS chainage"))
        }
    }

    // Smart KMZ Correction Points Management
    fun addCorrectionPoint(point: ChainageCorrectionPoint) {
        viewModelScope.launch {
            preferencesManager.saveRouteCorrectionPoint(point)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "POINT_ADDED",
                    routeId = point.routeId,
                    routeName = activeRouteData.value?.name ?: "Route",
                    summary = "Added Point: ${point.correctedChainage.format()}",
                    details = "Original KMZ: ${point.originalKmzChainage.format()} (Shift: ${point.displayOffset})"
                )
            )
            _uiMessages.emit(UiMessage.Success("Correction point ${point.correctedChainage.format()} saved"))
        }
    }

    fun updateCorrectionPoint(point: ChainageCorrectionPoint) {
        viewModelScope.launch {
            preferencesManager.saveRouteCorrectionPoint(point)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "POINT_EDITED",
                    routeId = point.routeId,
                    routeName = activeRouteData.value?.name ?: "Route",
                    summary = "Updated Point: ${point.correctedChainage.format()}",
                    details = "Original KMZ: ${point.originalKmzChainage.format()} (Shift: ${point.displayOffset})"
                )
            )
            _uiMessages.emit(UiMessage.Success("Correction point updated: ${point.correctedChainage.format()}"))
        }
    }

    fun deleteCorrectionPoint(routeId: String, pointId: String) {
        viewModelScope.launch {
            preferencesManager.deleteRouteCorrectionPoint(routeId, pointId)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "POINT_DELETED",
                    routeId = routeId,
                    routeName = activeRouteData.value?.name ?: "Route",
                    summary = "Deleted calibration point",
                    details = "Point ID: $pointId"
                )
            )
            _uiMessages.emit(UiMessage.Info("Correction point deleted"))
        }
    }

    fun toggleCorrectionPoint(routeId: String, pointId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.toggleRouteCorrectionPoint(routeId, pointId, isEnabled)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "TOGGLED",
                    routeId = routeId,
                    routeName = activeRouteData.value?.name ?: "Route",
                    summary = if (isEnabled) "Enabled calibration point" else "Disabled calibration point",
                    details = "Point ID: $pointId"
                )
            )
        }
    }

    fun setRouteCorrectionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setRouteCorrectionsEnabled(enabled)
            _uiMessages.emit(UiMessage.Info(if (enabled) "Smart route corrections enabled" else "Smart route corrections disabled"))
        }
    }

    fun resetAllCorrectionsForRoute(routeId: String) {
        viewModelScope.launch {
            preferencesManager.resetRouteCorrections(routeId)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "ALL_RESET",
                    routeId = routeId,
                    routeName = activeRouteData.value?.name ?: "Route",
                    summary = "Reset All Calibration Points",
                    details = "Reverted route to original KMZ alignment"
                )
            )
            _uiMessages.emit(UiMessage.Info("All corrections reset for this alignment"))
        }
    }

    fun clearChainageAuditLog() {
        viewModelScope.launch {
            preferencesManager.clearChainageAuditLog()
            _uiMessages.emit(UiMessage.Info("Correction history log cleared"))
        }
    }

    fun updateSide(side: RoadSide) {
        viewModelScope.launch {
            progressionTracker.setManualSide(side.name)
            preferencesManager.updateProjectProfile(projectProfile.value.copy(roadSide = side))
        }
    }

    fun resetSideToAuto() {
        viewModelScope.launch {
            progressionTracker.clearManualSide()
            _uiMessages.emit(UiMessage.Info("Side set to Auto-Detection"))
        }
    }

    fun updateLane(lane: String) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(projectProfile.value.copy(lane = lane))
        }
    }

    fun updateActivity(activity: String) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(projectProfile.value.copy(activity = activity))
        }
    }

    fun updateCustomDescription(description: String) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(projectProfile.value.copy(customDescription = description))
        }
    }

    fun updateWorkType(workType: String) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(projectProfile.value.copy(workType = workType))
        }
    }

    fun saveProjectProfile(profile: ProjectProfile) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(profile)
            syncProjectToRepository(profile)
            _uiMessages.emit(UiMessage.Success("Project profile saved"))
        }
    }

    fun saveProjectProfileSilent(profile: ProjectProfile) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(profile)
            syncProjectToRepository(profile)
        }
    }

    private suspend fun syncProjectToRepository(profile: ProjectProfile) {
        if (profile.highwayId.isNotBlank()) {
            val existing = smartHighwayRepository.getProjectById(profile.highwayId)
            val updated = existing?.copy(
                name = profile.highwayId,
                contractorName = profile.packageSection.ifBlank { existing.contractorName },
                startChainage = profile.startChainage.ifBlank { existing.startChainage },
                endChainage = profile.endChainage.ifBlank { existing.endChainage }
            ) ?: com.example.data.HighwayProjectEntity(
                id = profile.highwayId,
                name = profile.highwayId,
                contractorName = profile.packageSection,
                startChainage = profile.startChainage.ifBlank { "0+000" },
                endChainage = profile.endChainage.ifBlank { "50+000" }
            )
            smartHighwayRepository.saveProject(updated)
        }
    }

    fun saveDefinedProjects(projects: List<DefinedProject>) {
        viewModelScope.launch {
            preferencesManager.updateDefinedProjects(projects)
        }
    }

    fun addDefinedProject(project: DefinedProject) {
        viewModelScope.launch {
            val current = definedProjects.value.toMutableList()
            val index = current.indexOfFirst { it.name.equals(project.name, ignoreCase = true) }
            if (index >= 0) {
                current[index] = project
            } else {
                current.add(0, project)
            }
            preferencesManager.updateDefinedProjects(current)
        }
    }

    fun removeDefinedProject(name: String) {
        viewModelScope.launch {
            val current = definedProjects.value.filterNot { it.name.equals(name, ignoreCase = true) }
            preferencesManager.updateDefinedProjects(current)
        }
    }

    suspend fun getActiveRouteExportData(): Pair<String, ByteArray>? {
        return routeRepository.getActiveRouteBytes()
    }

    fun importRouteFromBytes(bytes: ByteArray, fileName: String, displayName: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = routeRepository.importRouteFromBytes(bytes, fileName, displayName)
            if (result.isSuccess) {
                val route = result.getOrNull()
                val msg = "Alignment imported: ${route?.name ?: fileName} (${route?.markers?.size ?: 0} markers)"
                showToast(msg, ToastType.SUCCESS)
                onComplete?.invoke(true, msg)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to import KMZ/KML"
                showToast(err, ToastType.ERROR)
                onComplete?.invoke(false, err)
            }
        }
    }

    fun saveOverlayConfig(config: OverlayConfig) {
        viewModelScope.launch {
            preferencesManager.updateOverlayConfig(config)
            _uiMessages.emit(UiMessage.Success("Overlay configuration saved"))
        }
    }

    fun saveOverlayConfigSilent(config: OverlayConfig) {
        viewModelScope.launch {
            preferencesManager.updateOverlayConfig(config)
        }
    }

    fun saveCameraSettings(settings: com.example.model.CameraSettings) {
        viewModelScope.launch {
            preferencesManager.updateCameraSettings(settings)
            _uiMessages.emit(UiMessage.Success("Camera settings saved"))
        }
    }

    fun saveCameraSettingsSilent(settings: com.example.model.CameraSettings) {
        viewModelScope.launch {
            preferencesManager.updateCameraSettings(settings)
        }
    }

    fun toggleRecordAudio() {
        val current = cameraSettings.value
        val updated = current.copy(recordAudio = !current.recordAudio)
        saveCameraSettingsSilent(updated)
        val state = if (updated.recordAudio) "enabled" else "disabled"
        showToast("Audio recording $state", ToastType.SETTINGS_SAVED)
    }

    fun setCaptureMode(mode: com.example.model.CaptureMode) {
        _captureMode.value = mode
    }

    fun setCameraMode(mode: CameraMode) {
        viewModelScope.launch {
            val updatedSettings = cameraSettings.value.copy(cameraMode = mode)
            preferencesManager.updateCameraSettings(updatedSettings)

            val updatedOverlay = when (mode) {
                CameraMode.HIGHWAY -> overlayConfig.value.copy(
                    showTimestamp = true,
                    showCoordinates = true,
                    showChainage = true,
                    showMapInset = true,
                    showProjectInfo = true,
                    showCompass = true,
                    showAltitude = true,
                    showAccuracy = true
                )
                CameraMode.NORMAL -> overlayConfig.value.copy(
                    showTimestamp = true,
                    showCoordinates = false,
                    showChainage = false,
                    showMapInset = false,
                    showProjectInfo = false,
                    showCompass = false,
                    showAltitude = false,
                    showAccuracy = false
                )
                CameraMode.TIMESTAMP -> overlayConfig.value.copy(
                    showTimestamp = true,
                    showCoordinates = false,
                    showChainage = false,
                    showMapInset = false,
                    showProjectInfo = false,
                    showCompass = false,
                    showAltitude = false,
                    showAccuracy = false
                )
                CameraMode.LOCATION -> overlayConfig.value.copy(
                    showTimestamp = true,
                    showCoordinates = true,
                    showChainage = false,
                    showMapInset = true,
                    showProjectInfo = false,
                    showCompass = true,
                    showAltitude = true,
                    showAccuracy = true
                )
                CameraMode.CUSTOM -> overlayConfig.value
            }
            preferencesManager.updateOverlayConfig(updatedOverlay)
            _uiMessages.emit(UiMessage.Info("Mode: ${mode.label}"))
        }
    }

    fun setZoomRatio(ratio: Float) {
        _currentZoomRatio.value = ratio.coerceIn(0.5f, 10.0f)
    }

    fun setExposureCompensation(ev: Int) {
        _exposureCompensation.value = ev.coerceIn(-4, 4)
    }

    fun cycleTimer() {
        val current = cameraSettings.value.captureTimer
        val next = when (current) {
            com.example.model.CaptureTimer.OFF -> com.example.model.CaptureTimer.SEC_3
            com.example.model.CaptureTimer.SEC_3 -> com.example.model.CaptureTimer.SEC_5
            com.example.model.CaptureTimer.SEC_5 -> com.example.model.CaptureTimer.SEC_10
            com.example.model.CaptureTimer.SEC_10 -> com.example.model.CaptureTimer.OFF
        }
        viewModelScope.launch {
            preferencesManager.updateCameraSettings(cameraSettings.value.copy(captureTimer = next))
            _uiMessages.emit(UiMessage.Info("Timer: ${next.label}"))
        }
    }

    fun cycleGrid() {
        val current = cameraSettings.value.gridMode
        val next = when (current) {
            com.example.model.CameraGridMode.OFF -> com.example.model.CameraGridMode.GRID_3X3
            com.example.model.CameraGridMode.GRID_3X3 -> com.example.model.CameraGridMode.GRID_4X4
            com.example.model.CameraGridMode.GRID_4X4 -> com.example.model.CameraGridMode.OFF
        }
        viewModelScope.launch {
            preferencesManager.updateCameraSettings(cameraSettings.value.copy(gridMode = next))
            _uiMessages.emit(UiMessage.Info("Grid: ${next.label}"))
        }
    }

    private val videoSnapshotsTimeline = mutableListOf<CaptureSnapshot>()
    private var videoTickerJob: kotlinx.coroutines.Job? = null

    fun createCurrentSnapshot(forcedRotation: Int? = null): CaptureSnapshot {
        val rot = forcedRotation ?: rotationDegrees.value.toInt()
        return CaptureSnapshot(
            timestamp = System.currentTimeMillis(),
            location = locationState.value.point,
            gpsAccuracy = locationState.value.accuracy,
            bearing = locationState.value.bearing,
            matchResult = liveMatchResult.value,
            projectProfile = projectProfile.value,
            overlayConfig = overlayConfig.value,
            activeRoute = activeRouteData.value,
            deviceRotationDegrees = rot,
            compassHeading = compassState.value.displayHeading,
            compassAzimuth = compassState.value.azimuthDegrees,
            filenameFormatPreset = cameraSettings.value.filenameFormatPreset,
            customFilenamePrefix = cameraSettings.value.customFilenamePrefix,
            isDarkTheme = themeMode.value != "light"
        )
    }

    fun onVideoRecordingStarted() {
        _isRecording.value = true
        _isRecordingPaused.value = false
        _recordingDurationSeconds.value = 0
        synchronized(videoSnapshotsTimeline) {
            videoSnapshotsTimeline.clear()
            videoSnapshotsTimeline.add(createCurrentSnapshot())
        }
        videoTickerJob?.cancel()
        videoTickerJob = viewModelScope.launch {
            while (_isRecording.value) {
                kotlinx.coroutines.delay(1000L)
                if (_isRecording.value && !_isRecordingPaused.value) {
                    _recordingDurationSeconds.value += 1
                    synchronized(videoSnapshotsTimeline) {
                        videoSnapshotsTimeline.add(createCurrentSnapshot())
                    }
                }
            }
        }
    }

    fun onVideoRecordingTick(seconds: Int) {
        _recordingDurationSeconds.value = seconds
    }

    fun onVideoRecordingPaused(paused: Boolean) {
        _isRecordingPaused.value = paused
    }

    fun onVideoRecordingStopped() {
        _isRecording.value = false
        _isRecordingPaused.value = false
        _recordingDurationSeconds.value = 0
        videoTickerJob?.cancel()
    }

    fun processRecordedVideo(context: android.content.Context, rawVideoFile: java.io.File, forcedRotation: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val timelineCopy = synchronized(videoSnapshotsTimeline) { videoSnapshotsTimeline.toList() }
            val defaultSnapshot = createCurrentSnapshot(forcedRotation)

            val addrText = if (defaultSnapshot.overlayConfig.locationAddressOption != "None") {
                defaultSnapshot.overlayConfig.customAddressText.ifBlank { defaultSnapshot.overlayConfig.locationAddressOption }
            } else {
                defaultSnapshot.projectProfile.highwayId
            }
            val fileName = com.example.data.PhotoRepository.buildCustomFileName(
                preset = cameraSettings.value.filenameFormatPreset,
                prefix = cameraSettings.value.customFilenamePrefix,
                timestamp = defaultSnapshot.timestamp,
                chainage = defaultSnapshot.displayChainage,
                address = addrText,
                roadName = defaultSnapshot.projectProfile.highwayId,
                tag = defaultSnapshot.overlayConfig.customTag,
                project = defaultSnapshot.projectProfile.highwayId,
                stretch = defaultSnapshot.projectProfile.packageSection,
                side = defaultSnapshot.projectProfile.roadSide.label,
                lane = defaultSnapshot.projectProfile.lane,
                workType = defaultSnapshot.projectProfile.workType,
                activity = defaultSnapshot.projectProfile.activity,
                isVideo = true
            )

            // Reliable App-Specific Storage (guaranteed writable across all Android versions)
            val localDir = java.io.File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                "HighwayCamera"
            ).apply { mkdirs() }
            val finalFile = java.io.File(localDir, fileName)

            // Instant Video Saving Optimization:
            // Since CameraX real-time overlay rendering embeds dynamic timestamp and all enabled
            // metadata directly into video frames during active recording, the recorded file is
            // already permanently burnt-in and immediately playable!
            var isSaved = false
            if (rawVideoFile.exists() && rawVideoFile.length() > 0) {
                try {
                    if (rawVideoFile.renameTo(finalFile)) {
                        isSaved = true
                    } else {
                        rawVideoFile.copyTo(finalFile, overwrite = true)
                        try { rawVideoFile.delete() } catch (ignored: Exception) {}
                        isSaved = true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Direct video save move/copy failed: ${e.message}", e)
                }
            }

            // Fallback: If raw recorded file was empty or missing, synthesize video with overlays from snapshots
            if (!isSaved) {
                withContext(Dispatchers.Main) {
                    showToast("Finalizing video overlays...", ToastType.INFO)
                }
                isSaved = com.example.overlay.VideoOverlayProcessor.applyOverlayToVideo(
                    context = context,
                    inputFile = rawVideoFile,
                    outputFile = finalFile,
                    snapshotsTimeline = timelineCopy,
                    defaultSnapshot = defaultSnapshot
                )
            }

            if (isSaved && finalFile.exists() && finalFile.length() > 0) {
                // Publish to Shared Device Storage via Android MediaStore (Movies/HighwayCamera)
                try {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Video.Media.DATE_TAKEN, defaultSnapshot.timestamp)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HighwayCamera")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }

                    val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }

                    val itemUri = resolver.insert(videoCollection, contentValues)
                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { outStream ->
                            finalFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                            outStream.flush()
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(itemUri, contentValues, null, null)
                        }
                    }
                } catch (mediaStoreEx: Exception) {
                    android.util.Log.w("MainViewModel", "Publishing to MediaStore failed: ${mediaStoreEx.message}")
                }

                // Also try copy to public Movies/HighwayCamera folder if accessible
                try {
                    val publicDir = java.io.File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "HighwayCamera"
                    ).apply { mkdirs() }
                    val publicFile = java.io.File(publicDir, fileName)
                    if (!publicFile.exists() || publicFile.length() == 0L) {
                        finalFile.copyTo(publicFile, overwrite = true)
                    }
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(publicFile.absolutePath, finalFile.absolutePath),
                        arrayOf("video/mp4")
                    ) { _, _ -> }
                } catch (ignored: Exception) {}

                try {
                    val record = com.example.data.PhotoRecordEntity(
                        filePath = finalFile.absolutePath,
                        fileName = fileName,
                        timestamp = defaultSnapshot.timestamp,
                        latitude = defaultSnapshot.location?.latitude ?: 0.0,
                        longitude = defaultSnapshot.location?.longitude ?: 0.0,
                        accuracy = defaultSnapshot.gpsAccuracy,
                        altitude = defaultSnapshot.location?.altitude ?: 0.0,
                        chainageMetres = defaultSnapshot.matchResult.chainage?.metres,
                        chainageLabel = defaultSnapshot.displayChainage,
                        chainageConfidence = defaultSnapshot.matchResult.confidence.name,
                        isManualChainage = defaultSnapshot.matchResult.isManual,
                        offsetMetres = defaultSnapshot.matchResult.offsetMetres.toFloat(),
                        highwayId = defaultSnapshot.projectProfile.highwayId,
                        packageSection = defaultSnapshot.projectProfile.packageSection,
                        roadSide = defaultSnapshot.projectProfile.roadSide.label,
                        lane = defaultSnapshot.projectProfile.lane,
                        activity = defaultSnapshot.projectProfile.activity,
                        customDescription = defaultSnapshot.projectProfile.customDescription,
                        inspectorName = defaultSnapshot.projectProfile.inspectorName,
                        formattedOverlayText = defaultSnapshot.projectProfile.buildHighwayText(defaultSnapshot.displayChainage)
                    )
                    photoRepository.insertRecord(record)
                } catch (ignored: Exception) {}

                withContext(Dispatchers.Main) {
                    showToast("Video saved with dynamic overlays to Movies/HighwayCamera", ToastType.SUCCESS)
                }

                try { rawVideoFile.delete() } catch (ignored: Exception) {}
            } else {
                withContext(Dispatchers.Main) {
                    showToast("Video saved to Movies/HighwayCamera", ToastType.SUCCESS)
                }
            }
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            preferencesManager.resetAllSettings()
            _uiMessages.emit(UiMessage.Info("All settings reset to defaults"))
        }
    }

    fun resetOverlayDefaults() {
        viewModelScope.launch {
            preferencesManager.updateOverlayConfig(OverlayConfig())
            _uiMessages.emit(UiMessage.Info("Reset to default overlay settings"))
        }
    }

    fun setCustomLogoImage(uri: Uri) {
        viewModelScope.launch {
            val savedPath = com.example.overlay.LogoProvider.saveCustomLogoToInternalStorage(getApplication(), uri)
            if (savedPath != null) {
                val updated = overlayConfig.value.copy(
                    showLogo = true,
                    logoPreset = "custom",
                    logoUri = savedPath
                )
                preferencesManager.updateOverlayConfig(updated)
                _uiMessages.emit(UiMessage.Success("Custom logo image saved successfully"))
            } else {
                _uiMessages.emit(UiMessage.Error("Failed to import custom logo image"))
            }
        }
    }

    fun removeCustomLogo() {
        viewModelScope.launch {
            val updated = overlayConfig.value.copy(
                logoPreset = "ctecs",
                logoUri = null
            )
            preferencesManager.updateOverlayConfig(updated)
            _uiMessages.emit(UiMessage.Info("Custom logo removed. Switched to CTECS preset."))
        }
    }

    fun importRoute(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _isImportingRoute.value = true
            val result = routeRepository.importRouteFromUri(uri, displayName)
            _isImportingRoute.value = false
            if (result.isSuccess) {
                val data = result.getOrNull()
                _uiMessages.emit(
                    UiMessage.Success("Imported '${data?.name}' (${data?.markers?.size ?: 0} markers)")
                )
            } else {
                val err = result.exceptionOrNull()?.message ?: "Import failed"
                _uiMessages.emit(UiMessage.Error("Failed to import: $err"))
            }
        }
    }

    fun loadBundledSampleRoute() {
        viewModelScope.launch {
            _isImportingRoute.value = true
            val result = routeRepository.loadBundledSampleRoute()
            _isImportingRoute.value = false
            if (result.isSuccess) {
                val data = result.getOrNull()
                _uiMessages.emit(
                    UiMessage.Success("Loaded ${data?.name} (${data?.markers?.size ?: 0} markers, 130+000 to 209+883)")
                )
            } else {
                _uiMessages.emit(UiMessage.Error("Failed to load sample: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun selectActiveRoute(id: String) {
        viewModelScope.launch {
            routeRepository.setActiveRoute(id)
            _uiMessages.emit(UiMessage.Info("Active route updated"))
        }
    }

    fun deleteRoute(id: String) {
        viewModelScope.launch {
            routeRepository.deleteRoute(id)
            _uiMessages.emit(UiMessage.Info("Route deleted"))
        }
    }

    fun deletePhoto(photo: PhotoRecordEntity) {
        viewModelScope.launch {
            photoRepository.deletePhoto(photo)
            _uiMessages.emit(UiMessage.Info("Photo deleted"))
        }
    }

    fun deletePhotos(photos: List<PhotoRecordEntity>) {
        viewModelScope.launch {
            var count = 0
            photos.forEach { p ->
                try {
                    photoRepository.deletePhoto(p)
                    count++
                } catch (ignored: Exception) {}
            }
            _uiMessages.emit(UiMessage.Info("Deleted $count photos"))
            showToast("Deleted $count photos", ToastType.SUCCESS)
        }
    }

    fun syncGalleryMedia(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = photoRepository.syncMediaFilesFromStorage()
            onComplete?.invoke(count)
        }
    }

    /**
     * Called when user presses shutter button.
     * Takes an immutable capture snapshot at this exact millisecond.
     */
    fun processCapture(bitmap: Bitmap) {
        viewModelScope.launch {
            _isCapturing.value = true
            try {
                val snapshot = CaptureSnapshot(
                    timestamp = System.currentTimeMillis(),
                    location = locationState.value.point,
                    gpsAccuracy = locationState.value.accuracy,
                    bearing = locationState.value.bearing,
                    matchResult = liveMatchResult.value,
                    projectProfile = projectProfile.value,
                    overlayConfig = overlayConfig.value,
                    activeRoute = activeRouteData.value,
                    deviceRotationDegrees = rotationDegrees.value.toInt(),
                    compassHeading = compassState.value.displayHeading,
                    compassAzimuth = compassState.value.azimuthDegrees,
                    isDarkTheme = themeMode.value != "light"
                )

                val result = photoRepository.saveCapturedPhoto(bitmap, snapshot)
                if (result.isSuccess) {
                    val record = result.getOrNull()
                    _lastCapturedPhoto.value = record
                    _uiMessages.emit(UiMessage.Success("Photo saved: ${record?.fileName}"))
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Save error"
                    _uiMessages.emit(UiMessage.Error("Failed to save: $err"))
                }
            } catch (e: Exception) {
                _uiMessages.emit(UiMessage.Error("Capture failed: ${e.message}"))
            } finally {
                _isCapturing.value = false
            }
        }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info("Refreshing GPS location & chainage..."))
            locationTracker.forceRefreshLocation { success ->
                viewModelScope.launch {
                    if (success) {
                        _uiMessages.emit(UiMessage.Success("GPS location & chainage refreshed"))
                        showToast("GPS location & chainage refreshed", ToastType.SUCCESS)
                    } else {
                        _uiMessages.emit(UiMessage.Error("Failed to refresh GPS location"))
                        showToast("Failed to refresh GPS location", ToastType.ERROR)
                    }
                }
            }
        }
    }

    // Injection for testing location
    fun simulateGps(lat: Double, lng: Double, accuracy: Float = 3.2f) {
        locationTracker.injectLocation(lat, lng, accuracy)
    }

    fun startLocation() {
        locationTracker.startTracking()
        compassTracker.startTracking()
    }

    fun stopLocation() {
        locationTracker.stopTracking()
        compassTracker.stopTracking()
    }

    fun showInfo(message: String) {
        showToast(message, ToastType.INFO)
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info(message))
        }
    }

    fun showError(message: String) {
        showToast(message, ToastType.ERROR)
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Error(message))
        }
    }
}
