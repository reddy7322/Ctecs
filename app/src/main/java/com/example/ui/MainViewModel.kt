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
import com.example.model.CalibrationValidationStatus
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageLogEntry
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CameraMode
import com.example.model.DefinedProject
import com.example.model.GeneratedChainageMarker
import com.example.model.KmzReplacementPreview
import com.example.model.OverlayConfig
import com.example.model.PermanentCalibrationProfile
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.SpeedUnit
import com.example.geo.SmartChainageCorrectionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("Home"),
    CAMERA("Camera"),
    PHOTOS("Photos"),
    ROUTES("Routes"),
    SURVEY("Survey"),
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
    val surveyRepository = com.example.survey.data.SurveyRepository(db.surveyDao(), viewModelScope)
    val surveyManager = com.example.survey.engine.SurveyManager(application, surveyRepository, viewModelScope)

    // Interrupted Survey Launch Check
    private val _interruptedSurveySession = MutableStateFlow<com.example.survey.model.SurveySession?>(null)
    val interruptedSurveySession: StateFlow<com.example.survey.model.SurveySession?> = _interruptedSurveySession.asStateFlow()

    fun dismissInterruptedSurveyPrompt() {
        _interruptedSurveySession.value = null
    }

    fun resumeInterruptedSurvey(session: com.example.survey.model.SurveySession) {
        viewModelScope.launch {
            surveyManager.resumeExistingSession(session)
            setTab(AppTab.SURVEY)
            _interruptedSurveySession.value = null
            showToast("Survey resumed at ${session.displayCurrentChainage}", ToastType.SUCCESS)
        }
    }

    fun discardInterruptedSurvey(session: com.example.survey.model.SurveySession) {
        viewModelScope.launch {
            surveyRepository.updateSession(session.copy(status = com.example.survey.model.SurveyStatus.COMPLETED))
            _interruptedSurveySession.value = null
            showToast("Interrupted survey discarded", ToastType.INFO)
        }
    }

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

    // Dynamic Location Name Detection & Resolution
    private val _currentLocationName = MutableStateFlow("")
    val currentLocationName: StateFlow<String> = _currentLocationName.asStateFlow()

    fun refreshLocationName() {
        viewModelScope.launch {
            val pt = locationTracker.locationState.value.point
            if (pt != null) {
                com.example.location.LocationNameResolver.invalidateCache()
                val name = com.example.location.LocationNameResolver.resolveLocationName(
                    context = getApplication(),
                    latitude = pt.latitude,
                    longitude = pt.longitude
                )
                if (name.isNotBlank()) {
                    _currentLocationName.value = name
                    saveOverlayConfigSilent(overlayConfig.value.copy(lastDetectedLocationName = name))
                    showToast("Location updated: $name", ToastType.SUCCESS)
                } else {
                    showToast("Unable to resolve area name. Check network.", ToastType.WARNING)
                }
            } else {
                showToast("Acquiring GPS fix for location...", ToastType.INFO)
            }
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

    // Manual chainage override state (Camera Screen simple custom chainage, independent of Chainage Control System)
    private val _manualChainage = MutableStateFlow<Chainage?>(null)
    val manualChainage: StateFlow<Chainage?> = _manualChainage.asStateFlow()

    private val _manualChainageText = MutableStateFlow<String?>(null)
    val manualChainageText: StateFlow<String?> = _manualChainageText.asStateFlow()

    val savedCustomChainages: StateFlow<List<String>> = preferencesManager.savedCustomChainagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
            SmartChainageCorrectionEngine.sortPointsInRouteOrder(map[route.id].orEmpty(), route)
        } else {
            emptyList()
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val calibrationProfiles: StateFlow<List<PermanentCalibrationProfile>> = preferencesManager.calibrationProfilesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _activeProfileCompatibility = MutableStateFlow<SmartChainageCorrectionEngine.ProfileCompatibilityResult?>(null)
    val activeProfileCompatibility: StateFlow<SmartChainageCorrectionEngine.ProfileCompatibilityResult?> = _activeProfileCompatibility.asStateFlow()

    val chainageAuditLog: StateFlow<List<ChainageLogEntry>> = preferencesManager.chainageAuditLogFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val progressionTracker = com.example.geo.ChainageProgressionTracker()

    val selectedAlignmentId = MutableStateFlow<String?>(null)
    @Volatile
    private var lastCalculatedMatchResult: ChainageMatchResult? = null

    fun selectAlignment(alignmentId: String?) {
        selectedAlignmentId.value = alignmentId
    }

    private val effectiveRouteFlow: Flow<RouteData?> = combine(activeRouteData, selectedAlignmentId) { route, selectedAlignId ->
        if (selectedAlignId != null && route != null) {
            route.copy(selectedAlignmentId = selectedAlignId)
        } else {
            route
        }
    }.distinctUntilChanged()

    // Automatic KMZ / Custom Correction / GPS calculation (Tiers 2, 3, 4) running on background Default dispatcher
    private val autoMatchResultFlow: Flow<ChainageMatchResult> = combine(
        locationState,
        effectiveRouteFlow,
        routeCorrectionsMap,
        isRouteCorrectionsEnabled
    ) { loc: LocationState, route: RouteData?, correctionsMap: Map<String, List<ChainageCorrectionPoint>>, isCorrectionsEnabled: Boolean ->
        // Tier 4 fallback if no GPS fix
        if (loc.point == null) {
            return@combine ChainageMatchResult.noRoute(loc.accuracy).copy(
                detectedSide = progressionTracker.getCurrentSide(),
                source = ChainageSource.GPS
            )
        }

        // Tier 4 fallback if no route loaded
        if (route == null || (route.markers.isEmpty() && route.alignments.isEmpty())) {
            return@combine ChainageMatchResult(
                confidence = com.example.model.MatchConfidence.NO_MATCH,
                gpsAccuracy = loc.accuracy,
                statusMessage = "No route loaded (GPS Active)",
                detectedSide = progressionTracker.getCurrentSide(),
                source = ChainageSource.GPS
            )
        }

        // Calculate original KMZ alignment match with precision nearest-segment & continuity
        val kmzMatch = ChainageMatcher.match(
            gpsPoint = loc.point,
            gpsAccuracy = loc.accuracy,
            bearing = loc.bearing,
            routeData = route,
            previousResult = lastCalculatedMatchResult
        )

        val rawKmzChainage = kmzMatch.chainage

        if (kmzMatch.confidence == com.example.model.MatchConfidence.NO_MATCH || rawKmzChainage == null) {
            val stabilized = progressionTracker.processResult(kmzMatch, route.id)
            lastCalculatedMatchResult = stabilized
            return@combine stabilized.copy(
                source = ChainageSource.GPS,
                rawUncorrectedChainage = null
            )
        }

        // Tier 2: CUSTOM KMZ CORRECTION (interpolated along route distance within valid coverage)
        val routeCorrections = correctionsMap[route.id].orEmpty()
        val smartCorrected = if (isCorrectionsEnabled && routeCorrections.isNotEmpty()) {
            SmartChainageCorrectionEngine.applyCorrection(
                rawChainage = rawKmzChainage,
                routeData = route,
                correctionPoints = routeCorrections,
                gpsPoint = loc.point,
                gpsAccuracy = loc.accuracy,
                confidence = kmzMatch.confidence,
                distanceAlongAlignmentMetres = kmzMatch.distanceAlongAlignmentMetres,
                isUncertainJump = kmzMatch.isUncertain
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
                statusMessage = "Custom Correction (${smartCorrected.infoMessage})"
            )
        } else {
            // Tier 3: ORIGINAL KMZ ALIGNMENT
            kmzMatch.copy(
                source = ChainageSource.KMZ,
                rawUncorrectedChainage = rawKmzChainage
            )
        }

        val stabilized = progressionTracker.processResult(finalResult, route.id)
        lastCalculatedMatchResult = stabilized
        if (!progressionTracker.isManualOverride() && stabilized.detectedSide.isNotBlank() && stabilized.detectedSide != "Unknown") {
            val detectedEnum = com.example.model.RoadSide.fromString(stabilized.detectedSide)
            if (projectProfile.value.roadSide != detectedEnum) {
                viewModelScope.launch(Dispatchers.IO) {
                    preferencesManager.updateProjectProfile(projectProfile.value.copy(roadSide = detectedEnum))
                }
            }
        }
        stabilized
    }.flowOn(Dispatchers.Default)

    // Unified live match result with strict 4-tier priority across Home Page, Camera, and Media Watermarks:
    // 1. MANUAL override (highest priority, preserving LHS/RHS detection & underlying KMZ reference)
    // 2. CUSTOM CORRECTION (within valid KMZ correction coverage)
    // 3. KMZ (original imported KMZ chainage)
    // 4. GPS (unmatched fallback)
    val liveMatchResult: StateFlow<ChainageMatchResult> = combine(
        autoMatchResultFlow,
        manualChainage,
        manualChainageText
    ) { autoResult, manual, manualText ->
        if (manual != null || !manualText.isNullOrBlank()) {
            val formattedManual = manual?.format() ?: manualText.orEmpty()
            val preservedSide = if (autoResult.detectedSide.isNotBlank() && autoResult.detectedSide != "Unknown") {
                autoResult.detectedSide
            } else {
                progressionTracker.getCurrentSide()
            }
            autoResult.copy(
                chainage = manual ?: autoResult.chainage,
                customChainageText = formattedManual,
                confidence = com.example.model.MatchConfidence.HIGH,
                isManual = true,
                detectedSide = preservedSide,
                source = ChainageSource.MANUAL,
                rawUncorrectedChainage = autoResult.rawUncorrectedChainage ?: autoResult.chainage,
                statusMessage = "Manual Chainage Active ($formattedManual)"
            )
        } else {
            autoResult
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChainageMatchResult.noRoute()
    )

    val cameraMatchResult: StateFlow<ChainageMatchResult> = liveMatchResult

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

        // Restore saved manual chainage override
        viewModelScope.launch {
            preferencesManager.manualChainageTextFlow.collect { savedText ->
                _manualChainageText.value = savedText
                _manualChainage.value = if (!savedText.isNullOrBlank()) Chainage.parse(savedText) else null
            }
        }

        // Initialize and resolve dynamic current location name
        viewModelScope.launch {
            overlayConfig.collect { cfg ->
                if (_currentLocationName.value.isBlank() && cfg.lastDetectedLocationName.isNotBlank()) {
                    _currentLocationName.value = cfg.lastDetectedLocationName
                }
            }
        }

        // Fast non-blocking location update collector for Survey & Chainage
        viewModelScope.launch {
            locationTracker.locationState.collect { state ->
                val pt = state.point
                if (pt != null) {
                    surveyManager.processLocationUpdate(pt, state.accuracy, state.speed ?: 0f, state.bearing ?: 0f)
                }
            }
        }

        // Isolated, throttled reverse-geocoding collector on Dispatchers.IO so network I/O never stalls GPS/chainage
        viewModelScope.launch(Dispatchers.IO) {
            var lastGeocodedPt: com.example.model.GeoPoint? = null
            var lastGeocodedTimeMs = 0L
            locationTracker.locationState.collect { state ->
                val pt = state.point ?: return@collect
                if (!overlayConfig.value.showLocationName) return@collect
                val now = System.currentTimeMillis()
                val moved = lastGeocodedPt?.let { com.example.geo.GeoUtils.distanceMetres(it, pt) } ?: Double.MAX_VALUE
                if (moved < 45.0 && (now - lastGeocodedTimeMs) < 15_000L && _currentLocationName.value.isNotBlank()) {
                    return@collect
                }
                lastGeocodedPt = pt
                lastGeocodedTimeMs = now
                val name = com.example.location.LocationNameResolver.resolveLocationName(
                    context = getApplication(),
                    latitude = pt.latitude,
                    longitude = pt.longitude
                )
                if (name.isNotBlank() && name != _currentLocationName.value) {
                    _currentLocationName.value = name
                    saveOverlayConfigSilent(overlayConfig.value.copy(lastDetectedLocationName = name))
                }
            }
        }

        // Automatic Permanent Calibration Profile Recognition & Reuse when alignment is loaded/imported
        viewModelScope.launch(Dispatchers.Default) {
            combine(activeRouteData, calibrationProfiles) { route, profiles ->
                Pair(route, profiles)
            }.collect { (route, profiles) ->
                if (route == null || profiles.isEmpty()) {
                    _activeProfileCompatibility.value = null
                    return@collect
                }
                val fingerprint = SmartChainageCorrectionEngine.computeAlignmentFingerprint(route)
                val exactMatch = profiles.firstOrNull { it.alignmentFingerprint == fingerprint }
                val sameNameOrIdMatch = profiles.firstOrNull {
                    it.originalFileName.equals(route.fileName, ignoreCase = true) || it.routeId == route.id
                }
                val candidate = exactMatch ?: sameNameOrIdMatch
                if (candidate == null) {
                    _activeProfileCompatibility.value = null
                    return@collect
                }
                val compat = SmartChainageCorrectionEngine.evaluateProfileCompatibility(candidate, route)
                _activeProfileCompatibility.value = compat

                val currentPoints = routeCorrectionsMap.value[route.id].orEmpty()
                if (compat.isExactGeometryMatch && currentPoints.isEmpty() && compat.revalidatedPoints.isNotEmpty()) {
                    progressionTracker.resetChainageFilter()
                    preferencesManager.saveRouteCorrectionPointsList(route.id, compat.revalidatedPoints)
                    preferencesManager.setRouteCorrectionsEnabled(true)
                } else if (!compat.isExactGeometryMatch && compat.isCompatible && currentPoints.isNotEmpty()) {
                    // Mark existing points as Needs Review if geometry changed
                    val anyNeedsUpdate = currentPoints.any { it.verificationStatus == CalibrationValidationStatus.VERIFIED }
                    if (anyNeedsUpdate && compat.revalidatedPoints.isNotEmpty()) {
                        preferencesManager.saveRouteCorrectionPointsList(route.id, compat.revalidatedPoints)
                    }
                }
            }
        }

        // Periodic Location & Chainage Stream Health Watchdog (recovers stalled GPS callbacks without app restart)
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(5_000L)
                locationTracker.checkAndRecoverIfStale(8_000L)
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

        // Launch-time check: detect if a survey was interrupted and prompt user to resume
        viewModelScope.launch {
            kotlinx.coroutines.delay(450)
            val interrupted = surveyRepository.getInterruptedSessionOnStartup()
            if (interrupted != null) {
                _interruptedSurveySession.value = interrupted
            }
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
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            clearManualChainage()
            return
        }
        val parsed = Chainage.parse(trimmed)
        if (parsed == null) {
            viewModelScope.launch {
                _uiMessages.emit(UiMessage.Error("Invalid chainage format. Use e.g. 198+880, 198880, or 200+000"))
                showToast("Invalid chainage format (e.g. 198+880 or 198880)", ToastType.ERROR)
            }
            return
        }
        val formatted = parsed.format()
        _manualChainage.value = parsed
        _manualChainageText.value = formatted
        _showManualChainageDialog.value = false
        viewModelScope.launch {
            preferencesManager.setManualChainageOverride(parsed, formatted)
            val currentList = savedCustomChainages.value.toMutableList()
            if (currentList.none { it.equals(formatted, ignoreCase = true) }) {
                currentList.add(0, formatted)
                preferencesManager.saveCustomChainagesList(currentList)
            }
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "MANUAL_OVERRIDE",
                    routeId = activeRouteData.value?.id ?: "",
                    routeName = activeRouteData.value?.name ?: "No Route",
                    summary = "Manual Chainage: $formatted",
                    details = "Manual override active"
                )
            )
            _uiMessages.emit(UiMessage.Success("Manual chainage active: $formatted"))
            showToast("Manual chainage active: $formatted", ToastType.SUCCESS)
        }
    }

    fun clearManualChainage() {
        _manualChainage.value = null
        _manualChainageText.value = null
        _showManualChainageDialog.value = false
        viewModelScope.launch {
            preferencesManager.setManualChainageOverride(null, null)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "MANUAL_RESET",
                    routeId = activeRouteData.value?.id ?: "",
                    routeName = activeRouteData.value?.name ?: "No Route",
                    summary = "Reset to Automatic Chainage",
                    details = "Manual override cleared"
                )
            )
            _uiMessages.emit(UiMessage.Info("Reset to automatic chainage"))
            showToast("Reset to automatic chainage", ToastType.INFO)
        }
    }

    fun addCustomChainagePreset(input: String) {
        val parsed = Chainage.parse(input.trim()) ?: return
        val displayStr = parsed.format()
        viewModelScope.launch {
            val currentList = savedCustomChainages.value.toMutableList()
            currentList.removeAll { it.equals(displayStr, ignoreCase = true) }
            currentList.add(0, displayStr)
            preferencesManager.saveCustomChainagesList(currentList)
            showToast("Saved chainage preset: $displayStr", ToastType.SUCCESS)
        }
    }

    fun updateCustomChainagePreset(oldValue: String, newValue: String) {
        val parsed = Chainage.parse(newValue.trim()) ?: return
        val displayStr = parsed.format()
        viewModelScope.launch {
            val currentList = savedCustomChainages.value.toMutableList()
            val idx = currentList.indexOfFirst { it.equals(oldValue, ignoreCase = true) }
            if (idx >= 0) {
                currentList[idx] = displayStr
            } else {
                currentList.add(0, displayStr)
            }
            preferencesManager.saveCustomChainagesList(currentList)
            if (_manualChainageText.value.equals(oldValue, ignoreCase = true)) {
                _manualChainage.value = parsed
                _manualChainageText.value = displayStr
                preferencesManager.setManualChainageOverride(parsed, displayStr)
            }
            showToast("Updated chainage: $displayStr", ToastType.SUCCESS)
        }
    }

    fun deleteCustomChainagePreset(value: String) {
        viewModelScope.launch {
            val currentList = savedCustomChainages.value.filterNot { it.equals(value, ignoreCase = true) }
            preferencesManager.saveCustomChainagesList(currentList)
        }
    }

    // Smart KMZ Correction Points Management
    fun addCorrectionPoint(point: ChainageCorrectionPoint) {
        val route = activeRouteData.value
        val resolvedPoint = if (route != null) {
            val resolved = SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
                routeData = route,
                targetKmzMetres = point.originalKmzMetres,
                fallbackGps = if (point.latitude != 0.0 || point.longitude != 0.0) point.point else null
            )
            point.copy(
                projectId = point.projectId.ifBlank { projectProfile.value.highwayId },
                latitude = if (point.latitude != 0.0) point.latitude else resolved.point.latitude,
                longitude = if (point.longitude != 0.0) point.longitude else resolved.point.longitude,
                routeDistanceMetres = if (point.routeDistanceMetres > 0.01) point.routeDistanceMetres else resolved.routeDistanceMetres
            )
        } else {
            point.copy(projectId = point.projectId.ifBlank { projectProfile.value.highwayId })
        }
        progressionTracker.resetChainageFilter()
        viewModelScope.launch {
            preferencesManager.setRouteCorrectionsEnabled(true)
            preferencesManager.saveRouteCorrectionPoint(resolvedPoint)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "POINT_ADDED",
                    routeId = resolvedPoint.routeId,
                    routeName = route?.name ?: "Route",
                    summary = "Added Point: ${resolvedPoint.correctedChainage.format()}",
                    details = "Original KMZ: ${resolvedPoint.originalKmzChainage.format()} (Shift: ${resolvedPoint.displayOffset})"
                )
            )
            _uiMessages.emit(UiMessage.Success("Correction point ${resolvedPoint.correctedChainage.format()} saved"))
            showToast("Correction point ${resolvedPoint.correctedChainage.format()} saved", ToastType.SUCCESS)
        }
    }

    fun updateCorrectionPoint(point: ChainageCorrectionPoint) {
        val route = activeRouteData.value
        val resolvedPoint = if (route != null) {
            val resolved = SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
                routeData = route,
                targetKmzMetres = point.originalKmzMetres,
                fallbackGps = if (point.latitude != 0.0 || point.longitude != 0.0) point.point else null
            )
            point.copy(
                projectId = point.projectId.ifBlank { projectProfile.value.highwayId },
                latitude = if (point.latitude != 0.0) point.latitude else resolved.point.latitude,
                longitude = if (point.longitude != 0.0) point.longitude else resolved.point.longitude,
                routeDistanceMetres = resolved.routeDistanceMetres
            )
        } else {
            point.copy(projectId = point.projectId.ifBlank { projectProfile.value.highwayId })
        }
        progressionTracker.resetChainageFilter()
        viewModelScope.launch {
            preferencesManager.setRouteCorrectionsEnabled(true)
            preferencesManager.saveRouteCorrectionPoint(resolvedPoint)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "POINT_EDITED",
                    routeId = resolvedPoint.routeId,
                    routeName = route?.name ?: "Route",
                    summary = "Updated Point: ${resolvedPoint.correctedChainage.format()}",
                    details = "Original KMZ: ${resolvedPoint.originalKmzChainage.format()} (Shift: ${resolvedPoint.displayOffset})"
                )
            )
            _uiMessages.emit(UiMessage.Success("Correction point updated: ${resolvedPoint.correctedChainage.format()}"))
            showToast("Correction point updated: ${resolvedPoint.correctedChainage.format()}", ToastType.SUCCESS)
        }
    }

    fun deleteCorrectionPoint(routeId: String, pointId: String) {
        progressionTracker.resetChainageFilter()
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

    /**
     * Imports verified permanent reference points from the active KMZ/KML alignment markers.
     */
    fun importReferencePointsFromActiveRoute(stepMetres: Long = 2000L) {
        val route = activeRouteData.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val extracted = SmartChainageCorrectionEngine.extractReferencePointsFromRoute(
                routeData = route,
                projectId = projectProfile.value.highwayId,
                stepMetres = stepMetres
            )
            if (extracted.isEmpty()) {
                showToast("No reference markers found in active alignment", ToastType.WARNING)
                return@launch
            }
            val existing = routeCorrectionsMap.value[route.id].orEmpty().toMutableList()
            var addedCount = 0
            for (pt in extracted) {
                val isDup = existing.any {
                    kotlin.math.abs(it.originalKmzMetres - pt.originalKmzMetres) <= 1L ||
                        (it.routeDistanceMetres > 0.01 && pt.routeDistanceMetres > 0.01 &&
                            kotlin.math.abs(it.routeDistanceMetres - pt.routeDistanceMetres) < 1.0)
                }
                if (!isDup) {
                    existing.add(pt)
                    addedCount++
                }
            }
            progressionTracker.resetChainageFilter()
            preferencesManager.saveRouteCorrectionPointsList(route.id, existing)
            preferencesManager.setRouteCorrectionsEnabled(true)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "KMZ_REFS_IMPORTED",
                    routeId = route.id,
                    routeName = route.name,
                    summary = "Imported $addedCount KMZ reference points",
                    details = "Interval: ${stepMetres}m"
                )
            )
            showToast("Imported $addedCount verified reference points from KMZ", ToastType.SUCCESS)
        }
    }

    /**
     * Saves or updates a Permanent Calibration Profile for the active route and project.
     */
    fun saveCurrentCalibrationProfile(profileNameInput: String = "") {
        val route = activeRouteData.value ?: return
        val points = activeRouteCorrections.value
        if (points.isEmpty()) {
            showToast("Add at least 1 verified reference point before saving a profile", ToastType.WARNING)
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val fingerprint = SmartChainageCorrectionEngine.computeAlignmentFingerprint(route)
            val conflictReport = SmartChainageCorrectionEngine.detectReferenceConflicts(points, route)
            val cleanName = profileNameInput.trim().ifBlank { "${route.name} Profile" }
            val profileId = java.util.UUID.randomUUID().toString()
            val linkedPoints = points.map {
                it.copy(
                    calibrationProfileId = profileId,
                    verificationStatus = if (conflictReport.hasConflict) {
                        CalibrationValidationStatus.INVALID
                    } else {
                        CalibrationValidationStatus.VERIFIED
                    }
                )
            }
            val firstOff = linkedPoints.first().displayOffset
            val lastOff = linkedPoints.last().displayOffset
            val offsetSummary = if (linkedPoints.size == 1 || firstOff == lastOff) {
                "Shift $firstOff (${linkedPoints.size} pts)"
            } else {
                "Shift $firstOff .. $lastOff (${linkedPoints.size} pts)"
            }

            val profile = PermanentCalibrationProfile(
                profileId = profileId,
                profileName = cleanName,
                projectId = projectProfile.value.highwayId,
                routeId = route.id,
                originalFileName = route.fileName,
                alignmentFingerprint = fingerprint,
                controlPoints = linkedPoints,
                chainageDirection = if (route.diagnostics?.isChainageIncreasing == false) "DECREASING" else "INCREASING",
                chainageEquations = route.diagnostics?.chainageEquations.orEmpty(),
                markerIntervalMetres = SmartChainageCorrectionEngine.DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES,
                calibrationOffsetSummary = offsetSummary,
                lastValidatedAt = System.currentTimeMillis(),
                validationStatus = conflictReport.validationStatus,
                statusDetailMessage = if (conflictReport.hasConflict) {
                    conflictReport.conflicts.first()
                } else {
                    "Validated Permanent Profile (${linkedPoints.size} Verified Refs)"
                }
            )
            preferencesManager.saveRouteCorrectionPointsList(route.id, linkedPoints)
            preferencesManager.saveCalibrationProfile(profile)
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "PROFILE_SAVED",
                    routeId = route.id,
                    routeName = route.name,
                    summary = "Saved Profile: $cleanName",
                    details = offsetSummary
                )
            )
            showToast("Saved permanent profile '$cleanName'", ToastType.SUCCESS)
        }
    }

    /**
     * Loads and applies a saved Permanent Calibration Profile to the active route.
     */
    fun applySavedCalibrationProfile(profile: PermanentCalibrationProfile) {
        val route = activeRouteData.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val compat = SmartChainageCorrectionEngine.evaluateProfileCompatibility(profile, route)
            if (!compat.isCompatible) {
                showToast(compat.statusMessage, ToastType.ERROR)
                return@launch
            }
            val validatedPts = compat.revalidatedPoints.map {
                it.copy(verificationStatus = CalibrationValidationStatus.VERIFIED)
            }
            progressionTracker.resetChainageFilter()
            preferencesManager.saveRouteCorrectionPointsList(route.id, validatedPts)
            preferencesManager.setRouteCorrectionsEnabled(true)
            // Update profile fingerprint if revalidated by user on modified alignment
            val newFingerprint = SmartChainageCorrectionEngine.computeAlignmentFingerprint(route)
            preferencesManager.saveCalibrationProfile(
                profile.copy(
                    routeId = route.id,
                    alignmentFingerprint = newFingerprint,
                    controlPoints = validatedPts,
                    lastValidatedAt = System.currentTimeMillis(),
                    validationStatus = CalibrationValidationStatus.VERIFIED,
                    statusDetailMessage = "Revalidated & Active on ${route.fileName}"
                )
            )
            preferencesManager.addChainageLogEntry(
                ChainageLogEntry(
                    actionType = "PROFILE_APPLIED",
                    routeId = route.id,
                    routeName = route.name,
                    summary = "Applied Profile: ${profile.profileName}",
                    details = "${validatedPts.size} verified control points loaded"
                )
            )
            showToast("Applied calibration profile '${profile.profileName}'", ToastType.SUCCESS)
        }
    }

    fun deleteCalibrationProfile(profileId: String) {
        viewModelScope.launch {
            preferencesManager.deleteCalibrationProfile(profileId)
            showToast("Calibration profile removed", ToastType.INFO)
        }
    }

    /**
     * Previews the minimal-change KMZ/KML replacement with 2 km calibration markers.
     */
    fun previewKmzCalibrationReplacement(
        rangeStartMetres: Long? = null,
        rangeEndMetres: Long? = null,
        intervalMetres: Long = SmartChainageCorrectionEngine.DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES,
        onResult: (Result<KmzReplacementPreview>) -> Unit
    ) {
        val route = activeRouteData.value
        if (route == null) {
            onResult(Result.failure(Exception("No active KMZ/KML alignment loaded")))
            return
        }
        val points = activeRouteCorrections.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportData = routeRepository.getActiveRouteBytes()
                val (fileName, sourceBytes) = if (exportData != null) {
                    exportData
                } else {
                    val synth = SmartChainageCorrectionEngine.generateCorrectedKmlContent(route, emptyList())
                    Pair(route.fileName.ifBlank { "${route.name}.kml" }, synth.toByteArray(Charsets.UTF_8))
                }
                val preview = SmartChainageCorrectionEngine.previewKmzReplacement(
                    sourceBytes = sourceBytes,
                    sourceFileName = fileName,
                    routeData = route,
                    correctionPoints = points,
                    rangeStartMetres = rangeStartMetres,
                    rangeEndMetres = rangeEndMetres,
                    intervalMetres = intervalMetres
                )
                withContext(Dispatchers.Main) {
                    onResult(Result.success(preview))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            }
        }
    }

    /**
     * Executes Final Apply — replaces old chainage-only markers within the selected range with
     * calibrated 2 km markers while preserving 100% of unrelated KMZ/KML data, exports to targetUri
     * (if provided), and optionally activates the calibrated alignment in the app.
     */
    fun applyCalibrationToKmzAndExport(
        targetUri: Uri? = null,
        activateInApp: Boolean = false,
        rangeStartMetres: Long? = null,
        rangeEndMetres: Long? = null,
        intervalMetres: Long = SmartChainageCorrectionEngine.DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val route = activeRouteData.value
        if (route == null) {
            onComplete?.invoke(false, "No active KMZ/KML alignment loaded")
            return
        }
        val points = activeRouteCorrections.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportData = routeRepository.getActiveRouteBytes()
                val (fileName, sourceBytes) = if (exportData != null) {
                    exportData
                } else {
                    val synth = SmartChainageCorrectionEngine.generateCorrectedKmlContent(route, emptyList())
                    Pair(route.fileName.ifBlank { "${route.name}.kml" }, synth.toByteArray(Charsets.UTF_8))
                }

                val (calibratedBytes, preview) = SmartChainageCorrectionEngine.applyCalibrationToExistingKmzOrKml(
                    sourceBytes = sourceBytes,
                    sourceFileName = fileName,
                    routeData = route,
                    correctionPoints = points,
                    rangeStartMetres = rangeStartMetres,
                    rangeEndMetres = rangeEndMetres,
                    intervalMetres = intervalMetres,
                    profileName = "${route.name} Calibrated"
                )

                if (targetUri != null) {
                    getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                        out.write(calibratedBytes)
                        out.flush()
                    } ?: throw Exception("Could not open output destination for writing")
                }

                if (activateInApp) {
                    val importRes = routeRepository.importRouteFromBytes(
                        bytes = calibratedBytes,
                        fileName = preview.suggestedOutputFileName,
                        displayName = "${route.name} (Calibrated 2km)"
                    )
                    if (importRes.isFailure) {
                        throw importRes.exceptionOrNull() ?: Exception("Failed to activate calibrated alignment")
                    }
                    progressionTracker.resetChainageFilter()
                }

                preferencesManager.addChainageLogEntry(
                    ChainageLogEntry(
                        actionType = "KMZ_FINAL_APPLIED",
                        routeId = route.id,
                        routeName = route.name,
                        summary = "Applied 2km Calibration (${preview.rangeStart.format()}..${preview.rangeEnd.format()})",
                        details = "Removed ${preview.removedOldMarkersCount} old markers, inserted ${preview.inserted2KmMarkers.size} 2km markers"
                    )
                )

                val summaryMsg = "Replaced ${preview.removedOldMarkersCount} old markers with ${preview.inserted2KmMarkers.size} calibrated 2 km markers"
                withContext(Dispatchers.Main) {
                    showToast(summaryMsg, ToastType.SUCCESS)
                    onComplete?.invoke(true, summaryMsg)
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Failed to apply KMZ calibration"
                withContext(Dispatchers.Main) {
                    showToast(errMsg, ToastType.ERROR)
                    onComplete?.invoke(false, errMsg)
                }
            }
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
        val locName = if (overlayConfig.value.showLocationName) {
            currentLocationName.value.ifBlank { overlayConfig.value.lastDetectedLocationName }.trim()
        } else ""
        return CaptureSnapshot(
            timestamp = System.currentTimeMillis(),
            location = locationState.value.point,
            locationName = locName,
            gpsAccuracy = locationState.value.accuracy,
            bearing = locationState.value.bearing,
            matchResult = cameraMatchResult.value,
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

            val addrText = if (defaultSnapshot.locationName.isNotBlank() && !defaultSnapshot.locationName.equals("None", ignoreCase = true)) {
                defaultSnapshot.locationName
            } else if (defaultSnapshot.overlayConfig.locationAddressOption != "None") {
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

    private val _routeImportStatus = MutableStateFlow<String?>(null)
    val routeImportStatus: StateFlow<String?> = _routeImportStatus.asStateFlow()

    private val _routeImportError = MutableStateFlow<String?>(null)
    val routeImportError: StateFlow<String?> = _routeImportError.asStateFlow()

    fun clearRouteImportMessages() {
        _routeImportStatus.value = null
        _routeImportError.value = null
    }

    fun importRoute(
        uri: Uri,
        displayName: String,
        onComplete: ((Result<RouteData>) -> Unit)? = null
    ) {
        if (_isImportingRoute.value) {
            showToast("Import already in progress...", ToastType.INFO)
            return
        }
        viewModelScope.launch {
            _isImportingRoute.value = true
            _routeImportError.value = null
            _routeImportStatus.value = "Importing & parsing $displayName..."
            try {
                val result = routeRepository.importRouteFromUri(uri, displayName)
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    selectedAlignmentId.value = null
                    lastCalculatedMatchResult = null
                    progressionTracker.resetChainageFilter()
                    if (_manualChainage.value != null || !_manualChainageText.value.isNullOrBlank()) {
                        _manualChainage.value = null
                        _manualChainageText.value = null
                        preferencesManager.setManualChainageOverride(null, null)
                    }
                    val rangeStr = if (data?.startChainage != null && data.endChainage != null) {
                        " • ${data.startChainage.format()} → ${data.endChainage.format()}"
                    } else ""
                    val msg = "Imported '${data?.fileName ?: displayName}' (${data?.markers?.size ?: 0} markers$rangeStr)"
                    _routeImportStatus.value = msg
                    _routeImportError.value = null
                    _uiMessages.emit(UiMessage.Success(msg))
                    showToast(msg, ToastType.SUCCESS)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Import failed"
                    _routeImportStatus.value = null
                    _routeImportError.value = err
                    _uiMessages.emit(UiMessage.Error("Failed to import: $err"))
                    showToast("Import failed: $err", ToastType.ERROR)
                }
                onComplete?.invoke(result)
            } finally {
                _isImportingRoute.value = false
            }
        }
    }

    fun selectActiveRoute(id: String) {
        viewModelScope.launch {
            selectedAlignmentId.value = null
            lastCalculatedMatchResult = null
            progressionTracker.resetChainageFilter()
            routeRepository.setActiveRoute(id)
            _uiMessages.emit(UiMessage.Info("Active route updated"))
        }
    }

    fun deleteRoute(id: String) {
        viewModelScope.launch {
            selectedAlignmentId.value = null
            lastCalculatedMatchResult = null
            progressionTracker.resetChainageFilter()
            routeRepository.deleteRoute(id)
            _routeImportStatus.value = null
            _routeImportError.value = null
            _uiMessages.emit(UiMessage.Info("Alignment removed"))
            showToast("Alignment removed", ToastType.INFO)
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
                    matchResult = cameraMatchResult.value,
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
