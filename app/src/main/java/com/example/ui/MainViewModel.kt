package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.backup.BackupCategory
import com.example.backup.BackupExportResult
import com.example.backup.BackupHistoryItem
import com.example.backup.BackupPreviewInfo
import com.example.backup.BackupRestoreReport
import com.example.backup.RestoreMode
import com.example.backup.SettingsBackupManager
import com.example.camera.CameraBrightnessEngine
import com.example.data.AppDatabase
import com.example.data.MediaDeleteResult
import com.example.data.PhotoRecordEntity
import com.example.data.PhotoRepository
import com.example.data.PreferencesManager
import com.example.data.RouteEntity
import com.example.data.RouteRepository
import com.example.geo.ChainageAccuracyRefreshCoordinator
import com.example.geo.ChainageMatcher
import com.example.geo.SmartChainageCorrectionEngine
import com.example.geo.StuckChainageMonitor
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.location.LocationTracker
import com.example.model.CalibrationValidationStatus
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageAccuracyReport
import com.example.model.ChainageAccuracyStatus
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageLogEntry
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CameraMode
import com.example.model.DefinedProject
import com.example.model.GeneratedChainageMarker
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.navigation.LockScreenNavigationService
import com.example.model.KmzReplacementPreview
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.OverlayConfig
import com.example.model.PermanentCalibrationProfile
import com.example.model.ProjectProfile
import com.example.model.RefreshMode
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.model.SpeedUnit
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("Home"),
    CAMERA("Camera"),
    PHOTOS("Photos"),
    ROUTES("Routes"),
    SURVEY("Survey"),
    HIGHWAY_TOOLS("Tools"),
    SMART_NOTES("Notes"),
    WEATHER("Weather"),
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

data class PendingMediaDeleteRequest(
    val intentSender: IntentSender,
    val targetPhotos: List<PhotoRecordEntity>,
    val pendingUris: List<Uri>,
    val alreadyDeletedPhotos: List<PhotoRecordEntity> = emptyList(),
    val alreadyDeletedPaths: List<String> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val preferencesManager = PreferencesManager(application)
    val settingsBackupManager = SettingsBackupManager(application, preferencesManager)
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

    val locationTracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(application)
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

    // Single authoritative centralized Map Heading engine (strictly separate from LHS/RHS road-direction engine)
    val mapHeadingManager = com.example.geo.MapHeadingManager()
    val mapHeadingState: StateFlow<com.example.geo.MapHeadingState> = mapHeadingManager.headingState

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

    // Automatic Settings Persistence & Animated Confirmation Coordinator
    val settingsAutoSaveCoordinator = com.example.settings.SettingsAutoSaveCoordinator(viewModelScope)

    // Minimalist Application Startup Loader Controller (runs ONCE per process/ViewModel lifecycle)
    val startupInitializationController = com.example.ui.loader.StartupInitializationController(viewModelScope)
    val startupLoaderState: StateFlow<com.example.ui.loader.StartupLoaderUiState> =
        startupInitializationController.uiState

    // Preserved compatibility StateFlow for HomeScreen screensaver gating
    val isPageLoading: StateFlow<Boolean> = startupLoaderState
        .map { it.isVisible }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    @Volatile
    private var isAppForegrounded = false

    fun retryStartupInitialization() {
        startupInitializationController.retry()
    }

    // Location State
    val locationState: StateFlow<LocationState> = locationTracker.locationState

    private val _lastValidLocation = MutableStateFlow<GeoPoint?>(null)
    val lastValidLocation: StateFlow<GeoPoint?> = _lastValidLocation.asStateFlow()

    @Volatile
    private var lastValidGpsBearing: Float? = null

    // Route State
    val activeRouteData: StateFlow<RouteData?> = routeRepository.activeRouteData
    val allRoutes: StateFlow<List<RouteEntity>> = routeRepository.allRoutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Project & Overlay Settings (Authoritative in-memory StateFlow synced with DataStore)
    private val _projectProfile = MutableStateFlow(preferencesManager.getInitialProjectProfile())
    val projectProfile: StateFlow<ProjectProfile> = _projectProfile.asStateFlow()

    val definedProjects: StateFlow<List<DefinedProject>> = preferencesManager.definedProjectsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialDefinedProjects()
    )

    val overlayConfig: StateFlow<OverlayConfig> = preferencesManager.overlayConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialOverlayConfig()
    )

    val galleryViewMode: StateFlow<String> = preferencesManager.galleryViewModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialGalleryViewMode()
    )

    val gallerySortOption: StateFlow<String> = preferencesManager.gallerySortOptionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialGallerySortOption()
    )

    val galleryGroupOption: StateFlow<String> = preferencesManager.galleryGroupOptionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialGalleryGroupOption()
    )

    fun updateGalleryPrefs(viewMode: String? = null, sortOption: String? = null, groupOption: String? = null) {
        val signature = listOf(
            viewMode ?: galleryViewMode.value,
            sortOption ?: gallerySortOption.value,
            groupOption ?: galleryGroupOption.value
        ).hashCode()
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "gallery_prefs",
            sectionKey = "storage",
            valueSignature = signature
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateGalleryPreferences(viewMode, sortOption, groupOption)
            }
        }
    }

    private val _themeMode = MutableStateFlow(preferencesManager.getInitialThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        settingsAutoSaveCoordinator.seedPersistedSignature("theme_mode", _themeMode.value.trim().lowercase().hashCode())
        viewModelScope.launch {
            preferencesManager.themeModeFlow.collect { savedMode ->
                _themeMode.value = savedMode
            }
        }
    }

    fun setThemeMode(mode: String) {
        val normalized = mode.trim().lowercase()
        _themeMode.value = normalized
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "theme_mode",
            sectionKey = "appearance",
            valueSignature = normalized.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateThemeMode(normalized)
            }
        }
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info("Theme updated to ${normalized.replaceFirstChar { it.uppercase() }}"))
        }
    }

    // Dynamic Location Name Detection & Resolution
    private val _currentLocationName = MutableStateFlow(
        preferencesManager.getInitialOverlayConfig().lastDetectedLocationName
    )
    val currentLocationName: StateFlow<String> = _currentLocationName.asStateFlow()

    // Single Shared Live Weather Repository (reused across Home, Weather Dashboard, Camera, Video, Settings)
    private val weatherRepository: com.example.weather.SharedWeatherRepository =
        com.example.weather.SharedWeatherRepository.instance
    private val weatherAlertNotifier = com.example.weather.WeatherAlertNotifier()
    val weatherUiState: StateFlow<com.example.model.WeatherUiState> = weatherRepository.weatherUiState
    val latestWeatherObservation: StateFlow<com.example.model.WeatherObservation?> =
        weatherRepository.latestObservation
    val weatherForecastBundle: StateFlow<com.example.model.WeatherForecastBundle?> =
        weatherRepository.forecastBundle

    val weatherIntelligenceSettings: StateFlow<com.example.model.WeatherIntelligenceSettings> =
        preferencesManager.weatherIntelligenceSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesManager.getInitialWeatherIntelligenceSettings()
        )

    val actualSiteMeasurements: StateFlow<com.example.model.ActualSiteMeasurements> =
        preferencesManager.actualSiteMeasurementsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesManager.getInitialActualSiteMeasurements()
        )

    val workTargetInput: StateFlow<com.example.model.WorkTargetInput> =
        preferencesManager.workTargetInputFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesManager.getInitialWorkTargetInput()
        )

    private val _selectedWeatherWorkActivity = MutableStateFlow(
        preferencesManager.getInitialWeatherIntelligenceSettings().primaryActivity
    )
    val selectedWeatherWorkActivity: StateFlow<com.example.model.HighwayWorkActivity> =
        _selectedWeatherWorkActivity.asStateFlow()

    val weatherExpandedSections: StateFlow<Set<String>> =
        preferencesManager.weatherExpandedSectionsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesManager.getInitialWeatherExpandedSections()
        )

    val highwayWorkAssessments: StateFlow<List<com.example.model.ActivitySuitabilityAssessment>> =
        combine(
            latestWeatherObservation,
            weatherIntelligenceSettings,
            actualSiteMeasurements
        ) { obs, settings, site ->
            com.example.weather.IrcMorthWeatherRuleEngine.evaluateAllActivities(
                observation = obs,
                contractSpec = settings.contractSpec,
                siteMeasurements = site,
                staleThresholdMs = settings.staleDataThresholdMinutes.toLong() * 60_000L,
                bituminousConfig = settings.bituminousConfig
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.example.weather.IrcMorthWeatherRuleEngine.evaluateAllActivities(
                observation = weatherRepository.latestObservation.value,
                contractSpec = preferencesManager.getInitialWeatherIntelligenceSettings().contractSpec,
                siteMeasurements = preferencesManager.getInitialActualSiteMeasurements(),
                bituminousConfig = preferencesManager.getInitialWeatherIntelligenceSettings().bituminousConfig
            )
        )

    val hourlyWorkSuitabilityTimeline: StateFlow<List<com.example.model.HourlyWorkWindow>> =
        combine(
            weatherForecastBundle,
            selectedWeatherWorkActivity,
            weatherIntelligenceSettings,
            actualSiteMeasurements
        ) { bundle, activity, settings, site ->
            val hours = bundle?.next24Hours().orEmpty()
            com.example.weather.IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
                activity = activity,
                hourlyPoints = hours,
                contractSpec = settings.contractSpec,
                siteMeasurements = site,
                bituminousConfig = settings.bituminousConfig
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val workTargetPrediction: StateFlow<com.example.model.WorkTargetPrediction> =
        combine(
            workTargetInput,
            hourlyWorkSuitabilityTimeline,
            weatherForecastBundle,
            weatherIntelligenceSettings,
            actualSiteMeasurements
        ) { input, selectedTimeline, bundle, settings, site ->
            val timelineForTarget = if (selectedTimeline.firstOrNull()?.activity == input.activity) {
                selectedTimeline
            } else {
                com.example.weather.IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
                    activity = input.activity,
                    hourlyPoints = bundle?.next24Hours().orEmpty(),
                    contractSpec = settings.contractSpec,
                    siteMeasurements = site,
                    bituminousConfig = settings.bituminousConfig
                )
            }
            com.example.weather.WorkTargetPredictionEngine.predictWorkTarget(
                input = input,
                hourlyTimeline = timelineForTarget
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.example.weather.WorkTargetPredictionEngine.predictWorkTarget(
                input = preferencesManager.getInitialWorkTargetInput(),
                hourlyTimeline = emptyList()
            )
        )

    val weatherDelayRiskSummary: StateFlow<com.example.model.DelayRiskSummary> =
        combine(
            selectedWeatherWorkActivity,
            hourlyWorkSuitabilityTimeline,
            workTargetPrediction
        ) { activity, timeline, prediction ->
            com.example.weather.WorkTargetPredictionEngine.analyzeDelayRisks(
                activity = activity,
                hourlyTimeline = timeline,
                targetPrediction = prediction.takeIf { it.activity == activity }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.example.weather.WorkTargetPredictionEngine.analyzeDelayRisks(
                activity = preferencesManager.getInitialWeatherIntelligenceSettings().primaryActivity,
                hourlyTimeline = emptyList()
            )
        )

    val actionableWeatherAlerts: StateFlow<List<com.example.model.WeatherAlertItem>> =
        combine(
            weatherForecastBundle,
            weatherIntelligenceSettings,
            highwayWorkAssessments,
            workTargetPrediction
        ) { bundle, settings, assessments, prediction ->
            weatherAlertNotifier.evaluateActionableAlerts(
                bundle = bundle,
                settings = settings,
                assessments = assessments,
                targetPrediction = prediction
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun selectWeatherWorkActivity(activity: com.example.model.HighwayWorkActivity) {
        val current = weatherIntelligenceSettings.value
        if (_selectedWeatherWorkActivity.value == activity &&
            current.primaryActivity == activity &&
            !current.bituminousConfig.usesExtendedBituminousProfile
        ) return
        _selectedWeatherWorkActivity.value = activity
        val syncedBituminous = when (activity) {
            com.example.model.HighwayWorkActivity.BC_LAYING ->
                current.bituminousConfig.withCategoryChanged(com.example.model.BituminousWorkCategory.BC)
            com.example.model.HighwayWorkActivity.DBM_LAYING ->
                current.bituminousConfig.withCategoryChanged(com.example.model.BituminousWorkCategory.DBM)
            com.example.model.HighwayWorkActivity.TACK_COAT_APPLICATION ->
                current.bituminousConfig.withOtherWorkTypeChanged(com.example.model.OtherBituminousWorkType.TACK_COAT)
            com.example.model.HighwayWorkActivity.PRIME_COAT_APPLICATION ->
                current.bituminousConfig.withOtherWorkTypeChanged(com.example.model.OtherBituminousWorkType.PRIME_COAT)
            else -> current.bituminousConfig.copy(isCustomCategoryActive = false)
        }
        val updated = current.copy(
            primaryActivity = activity,
            bituminousConfig = syncedBituminous
        )
        preferencesManager.launchSafeEdit {
            preferencesManager.updateWeatherIntelligenceSettings(updated)
        }
    }

    fun updateWeatherIntelligenceSettings(
        transform: (com.example.model.WeatherIntelligenceSettings) -> com.example.model.WeatherIntelligenceSettings
    ) {
        val updated = transform(weatherIntelligenceSettings.value)
        _selectedWeatherWorkActivity.value = updated.primaryActivity
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "weather_intelligence",
            sectionKey = "weather",
            valueSignature = updated.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateWeatherIntelligenceSettings(updated)
            }
        }
    }

    fun resetWeatherIntelligenceSettings() {
        val defaults = com.example.model.WeatherIntelligenceSettings()
        _selectedWeatherWorkActivity.value = defaults.primaryActivity
        preferencesManager.launchSafeEdit {
            preferencesManager.updateWeatherIntelligenceSettings(defaults)
        }
        showToast("Weather & Highway Intelligence defaults restored", ToastType.SETTINGS_SAVED)
    }

    fun updateContractSpecificationConfig(spec: com.example.model.ContractSpecificationConfig) {
        updateWeatherIntelligenceSettings { it.copy(contractSpec = spec) }
    }

    fun updateActualSiteMeasurements(measurements: com.example.model.ActualSiteMeasurements) {
        preferencesManager.launchSafeEdit {
            preferencesManager.updateActualSiteMeasurements(measurements)
        }
    }

    fun updateWorkTargetInput(input: com.example.model.WorkTargetInput) {
        preferencesManager.launchSafeEdit {
            preferencesManager.updateWorkTargetInput(input)
        }
    }

    fun toggleWeatherSection(sectionKey: String) {
        val current = weatherExpandedSections.value
        val updated = if (sectionKey in current) current - sectionKey else current + sectionKey
        preferencesManager.launchSafeEdit {
            preferencesManager.updateWeatherExpandedSections(updated)
        }
    }

    fun refreshWeather(force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val pt = locationTracker.locationState.value.point ?: _lastValidLocation.value
            val locName = _currentLocationName.value.ifBlank { overlayConfig.value.lastDetectedLocationName }
            val ttlMs = weatherIntelligenceSettings.value.autoRefreshIntervalMinutes.toLong() * 60_000L
            val updated = weatherRepository.refreshForLocation(
                point = pt,
                resolvedLocationName = locName,
                forceRefresh = force,
                cacheTtlMs = ttlMs
            )
            val bundle = weatherRepository.forecastBundle.value
            if (bundle != null) {
                preferencesManager.saveCachedWeatherForecastBundle(bundle)
                val alerts = weatherAlertNotifier.evaluateActionableAlerts(
                    bundle = bundle,
                    settings = weatherIntelligenceSettings.value,
                    assessments = highwayWorkAssessments.value,
                    targetPrediction = workTargetPrediction.value
                )
                weatherAlertNotifier.dispatchSystemNotifications(getApplication(), alerts)
            } else if (updated != null) {
                preferencesManager.saveCachedWeatherObservation(updated)
            }
        }
    }

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
                    preferencesManager.updateLastDetectedLocationName(name)
                    weatherRepository.attachResolvedLocationName(name)
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
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialMapTheme()
    )

    fun setMapTheme(theme: String) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "map_theme",
            sectionKey = "offline_maps",
            valueSignature = theme.lowercase().hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateMapTheme(theme)
            }
        }
        viewModelScope.launch {
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
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialMapType()
    )

    fun setMapType(type: String) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "map_type",
            sectionKey = "offline_maps",
            valueSignature = type.lowercase().hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateMapType(type)
            }
        }
        viewModelScope.launch {
            val label = if (type.equals("satellite", ignoreCase = true)) "Satellite" else "Standard (Street)"
            _uiMessages.emit(UiMessage.Info("Map Type: $label"))
        }
    }

    val offlineMapSyncManager: com.example.geo.OfflineMapSyncManager =
        com.example.geo.OfflineMapSyncManager.shared
    val offlineMapSyncState: StateFlow<com.example.geo.OfflineMapSyncState> =
        offlineMapSyncManager.syncState

    val offlineMapAutoPreload: StateFlow<Boolean> = preferencesManager.offlineMapAutoPreloadFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialOfflineMapAutoPreload()
    )

    val offlineMapWifiOnly: StateFlow<Boolean> = preferencesManager.offlineMapWifiOnlyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialOfflineMapWifiOnly()
    )

    fun setOfflineMapAutoPreload(enabled: Boolean) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "offline_auto_preload",
            sectionKey = "offline_maps",
            valueSignature = enabled.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateOfflineMapPreferences(autoPreload = enabled)
            }
        }
        offlineMapSyncManager.updatePreferences(
            context = getApplication(),
            autoPreload = enabled,
            wifiOnly = offlineMapWifiOnly.value
        )
    }

    fun setOfflineMapWifiOnly(enabled: Boolean) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "offline_wifi_only",
            sectionKey = "offline_maps",
            valueSignature = enabled.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateOfflineMapPreferences(wifiOnly = enabled)
            }
        }
        offlineMapSyncManager.updatePreferences(
            context = getApplication(),
            autoPreload = offlineMapAutoPreload.value,
            wifiOnly = enabled
        )
    }

    fun pauseOfflineMapSync() {
        offlineMapSyncManager.pauseSync(getApplication())
    }

    fun resumeOfflineMapSync() {
        offlineMapSyncManager.resumeSync(getApplication())
    }

    fun retryOfflineMapSync() {
        offlineMapSyncManager.retrySync(getApplication())
    }

    fun clearInactiveOfflineMapCache() {
        viewModelScope.launch(Dispatchers.IO) {
            offlineMapSyncManager.clearInactiveProjectCaches(
                context = getApplication(),
                activeRouteId = activeRouteData.value?.id
            )
            showToast("Cleared inactive project offline map cache", ToastType.SUCCESS)
        }
    }

    val preferredExternalNavAppId: StateFlow<String?> = preferencesManager.preferredExternalNavAppIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialPreferredExternalNavAppId()
    )

    fun setPreferredExternalNavAppId(appId: String?) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "preferred_external_nav",
            sectionKey = "chainage",
            valueSignature = (appId ?: "").hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updatePreferredExternalNavAppId(appId)
            }
        }
    }

    private val _chainageVoiceAssistantSettings = MutableStateFlow(
        preferencesManager.getInitialChainageVoiceAssistantSettings()
    )
    val chainageVoiceAssistantSettings: StateFlow<com.example.model.ChainageVoiceAssistantSettings> =
        _chainageVoiceAssistantSettings.asStateFlow()

    fun updateChainageVoiceAssistantSettings(
        transform: (com.example.model.ChainageVoiceAssistantSettings) -> com.example.model.ChainageVoiceAssistantSettings
    ) {
        val updated = transform(_chainageVoiceAssistantSettings.value)
        _chainageVoiceAssistantSettings.value = updated
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "chainage_voice_assistant",
            sectionKey = "chainage_voice_assistant",
            valueSignature = updated.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateChainageVoiceAssistantSettings(updated)
            }
        }
    }

    fun resetChainageVoiceAssistantSettings() {
        val defaults = com.example.model.ChainageVoiceAssistantSettings.defaultSettings()
        _chainageVoiceAssistantSettings.value = defaults
        preferencesManager.launchSafeEdit {
            preferencesManager.updateChainageVoiceAssistantSettings(defaults)
        }
        showToast("Chainage Voice Assistant settings reset to default", ToastType.SUCCESS)
    }

    val surveyShow200mLabels: StateFlow<Boolean> = preferencesManager.surveyShow200mLabelsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSurveyShow200mLabels()
    )

    val surveyShowCentreline: StateFlow<Boolean> = preferencesManager.surveyShowCentrelineFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSurveyShowCentreline()
    )

    val surveyShowGpsTrack: StateFlow<Boolean> = preferencesManager.surveyShowGpsTrackFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSurveyShowGpsTrack()
    )

    val surveyShowLaneTrack: StateFlow<Boolean> = preferencesManager.surveyShowLaneTrackFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSurveyShowLaneTrack()
    )

    val surveyShowMarkers: StateFlow<Boolean> = preferencesManager.surveyShowMarkersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSurveyShowMarkers()
    )

    val surveyMapType: StateFlow<String> = preferencesManager.surveyMapTypeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSurveyMapType()
    )

    fun updateSurveyMapPreferences(
        show200mLabels: Boolean? = null,
        showCentreline: Boolean? = null,
        showGpsTrack: Boolean? = null,
        showLaneTrack: Boolean? = null,
        showMarkers: Boolean? = null,
        mapType: String? = null
    ) {
        val signature = listOf(
            show200mLabels ?: surveyShow200mLabels.value,
            showCentreline ?: surveyShowCentreline.value,
            showGpsTrack ?: surveyShowGpsTrack.value,
            showLaneTrack ?: surveyShowLaneTrack.value,
            showMarkers ?: surveyShowMarkers.value,
            mapType ?: surveyMapType.value
        ).hashCode()
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "survey_map_prefs",
            sectionKey = "offline_maps",
            valueSignature = signature
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateSurveyMapPreferences(
                    show200mLabels = show200mLabels,
                    showCentreline = showCentreline,
                    showGpsTrack = showGpsTrack,
                    showLaneTrack = showLaneTrack,
                    showMarkers = showMarkers,
                    mapType = mapType
                )
            }
        }
    }

    private val _homeScreensaverSettings = MutableStateFlow(preferencesManager.getInitialHomeScreensaverSettings())
    val homeScreensaverSettings: StateFlow<HomeScreensaverSettings> = _homeScreensaverSettings.asStateFlow()
    private val _isScreensaverActive = MutableStateFlow(false)
    val isScreensaverActive: StateFlow<Boolean> = _isScreensaverActive.asStateFlow()
    private val _isScreensaverScreenOff = MutableStateFlow(false)
    val isScreensaverScreenOff: StateFlow<Boolean> = _isScreensaverScreenOff.asStateFlow()
    private val _isScreensaverAodActive = MutableStateFlow(false)
    val isScreensaverAodActive: StateFlow<Boolean> = _isScreensaverAodActive.asStateFlow()
    private val _screensaverSensorPosture = MutableStateFlow<com.example.ui.home.ScreensaverSensorPosture?>(null)
    val screensaverSensorPosture: StateFlow<com.example.ui.home.ScreensaverSensorPosture?> = _screensaverSensorPosture.asStateFlow()
    val isKmStoneServiceRunning: StateFlow<Boolean> = CtecsNavigationTelemetryHub.isKmStoneServiceRunning
    val kmStoneServiceStatusNote: StateFlow<String?> = CtecsNavigationTelemetryHub.kmStoneServiceStatusNote
    val lastKmStoneHubEvent: StateFlow<com.example.navigation.KmStoneAlertEvent?> = CtecsNavigationTelemetryHub.lastKmStoneEvent
    private val homeScreensaverMutationLock = Any()
    private var homeScreensaverWriteVersion = 0L
    private var homeScreensaverFlushedVersion = 0L
    private var homeScreensaverWriteJob: Job? = null

    private val _navigationNotificationSettings =
        MutableStateFlow(preferencesManager.getInitialNavigationNotificationSettings())
    val navigationNotificationSettings: StateFlow<com.example.model.NavigationNotificationSettings> =
        _navigationNotificationSettings.asStateFlow()
    private val navNotifMutationLock = Any()
    private var navNotifWriteVersion = 0L
    private var navNotifFlushedVersion = 0L
    private var navNotifWriteJob: Job? = null

    private val _widgetSettings = MutableStateFlow(preferencesManager.getInitialWidgetSettings())
    val widgetSettings: StateFlow<com.example.model.CtecsWidgetSettings> = _widgetSettings.asStateFlow()
    private val widgetSettingsMutationLock = Any()
    private var widgetSettingsWriteVersion = 0L
    private var widgetSettingsFlushedVersion = 0L
    private var widgetSettingsWriteJob: Job? = null

    private val _kmStoneAlertSettings = MutableStateFlow(preferencesManager.getInitialKmStoneAlertSettings())
    val kmStoneAlertSettings: StateFlow<com.example.model.KmStoneAlertSettings> = _kmStoneAlertSettings.asStateFlow()
    private val kmStoneAlertMutationLock = Any()
    private var kmStoneAlertWriteVersion = 0L
    private var kmStoneAlertFlushedVersion = 0L
    private var kmStoneAlertWriteJob: Job? = null

    private val _lastKmStoneAlertEvent = MutableStateFlow<com.example.navigation.KmStoneAlertEvent?>(null)
    val lastKmStoneAlertEvent: StateFlow<com.example.navigation.KmStoneAlertEvent?> = _lastKmStoneAlertEvent.asStateFlow()

    private fun updateWidgetSettingsState(
        transform: (com.example.model.CtecsWidgetSettings) -> com.example.model.CtecsWidgetSettings
    ) {
        val (myVersion, snapshot) = synchronized(widgetSettingsMutationLock) {
            widgetSettingsWriteVersion += 1L
            val updated = transform(_widgetSettings.value)
            _widgetSettings.value = updated
            widgetSettingsWriteVersion to updated
        }
        CtecsNavigationTelemetryHub.updateTelemetry(widgetSettings = snapshot)
        com.example.widget.CtecsWidgetUpdater.cacheWidgetSettings(getApplication(), snapshot)
        com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "widget_settings",
            sectionKey = "widgets",
            valueSignature = snapshot.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                val (targetVersion, targetSnapshot) = synchronized(widgetSettingsMutationLock) {
                    if (widgetSettingsWriteVersion <= widgetSettingsFlushedVersion) return@executeSafeEdit
                    widgetSettingsWriteVersion to _widgetSettings.value
                }
                preferencesManager.updateWidgetSettings(targetSnapshot)
                synchronized(widgetSettingsMutationLock) {
                    if (targetVersion > widgetSettingsFlushedVersion) {
                        widgetSettingsFlushedVersion = targetVersion
                    }
                }
            }
        }
    }

    fun updateWidgetSettings(
        transform: (com.example.model.CtecsWidgetSettings) -> com.example.model.CtecsWidgetSettings
    ) {
        updateWidgetSettingsState(transform)
    }

    fun updateWidgetInstanceConfig(config: com.example.model.CtecsWidgetInstanceConfig) {
        updateWidgetSettingsState { current ->
            current.withInstanceConfig(config)
        }
    }

    fun removeWidgetInstanceOverride(appWidgetId: Int) {
        updateWidgetSettingsState { current ->
            current.withoutInstanceConfig(appWidgetId)
        }
    }

    fun resetWidgetSettings() {
        val defaults = com.example.model.CtecsWidgetSettings.defaultSettings()
        updateWidgetSettingsState { defaults }
        showToast("Widget settings reset to default", ToastType.SUCCESS)
    }

    fun refreshHomeScreenWidgetsNow() {
        refreshLocation()
        com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
        showToast("Home-screen widgets refreshed", ToastType.SUCCESS)
    }

    private fun updateKmStoneAlertState(
        transform: (com.example.model.KmStoneAlertSettings) -> com.example.model.KmStoneAlertSettings
    ) {
        val (myVersion, snapshot) = synchronized(kmStoneAlertMutationLock) {
            kmStoneAlertWriteVersion += 1L
            val updated = transform(_kmStoneAlertSettings.value)
            _kmStoneAlertSettings.value = updated
            kmStoneAlertWriteVersion to updated
        }
        CtecsNavigationTelemetryHub.updateTelemetry(kmStoneAlertSettings = snapshot)
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "km_stone_alerts",
            sectionKey = "km_stone_alerts",
            valueSignature = snapshot.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                val (targetVersion, targetSnapshot) = synchronized(kmStoneAlertMutationLock) {
                    if (kmStoneAlertWriteVersion <= kmStoneAlertFlushedVersion) return@executeSafeEdit
                    kmStoneAlertWriteVersion to _kmStoneAlertSettings.value
                }
                preferencesManager.updateKmStoneAlertSettings(targetSnapshot)
                synchronized(kmStoneAlertMutationLock) {
                    if (targetVersion > kmStoneAlertFlushedVersion) {
                        kmStoneAlertFlushedVersion = targetVersion
                    }
                }
            }
        }
    }

    fun updateKmStoneAlertSettings(
        transform: (com.example.model.KmStoneAlertSettings) -> com.example.model.KmStoneAlertSettings
    ) {
        updateKmStoneAlertState(transform)
        val updated = _kmStoneAlertSettings.value
        if (updated.enabled && updated.lockScreenNotificationsEnabled && activeRouteData.value != null) {
            startLocation()
        }
        syncBackgroundNavigationService()
    }

    fun resetKmStoneAlertSettings() {
        val defaults = com.example.model.KmStoneAlertSettings.defaultSettings()
        updateKmStoneAlertState { defaults }
        CtecsNavigationTelemetryHub.sharedKmStoneDetector.reset()
        syncBackgroundNavigationService()
        showToast("KM Stone Arrival alert settings reset to default", ToastType.SUCCESS)
    }

    private fun updateHomeScreensaverState(transform: (HomeScreensaverSettings) -> HomeScreensaverSettings) {
        val (myVersion, snapshot) = synchronized(homeScreensaverMutationLock) {
            homeScreensaverWriteVersion += 1L
            val updated = transform(_homeScreensaverSettings.value)
            _homeScreensaverSettings.value = updated
            homeScreensaverWriteVersion to updated
        }
        CtecsNavigationTelemetryHub.updateTelemetry(screensaverSettings = snapshot)
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "screensaver_settings",
            sectionKey = "screensaver",
            valueSignature = snapshot.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                val (targetVersion, targetSnapshot) = synchronized(homeScreensaverMutationLock) {
                    if (homeScreensaverWriteVersion <= homeScreensaverFlushedVersion) return@executeSafeEdit
                    homeScreensaverWriteVersion to _homeScreensaverSettings.value
                }
                preferencesManager.updateHomeScreensaverSettings(targetSnapshot)
                synchronized(homeScreensaverMutationLock) {
                    if (targetVersion > homeScreensaverFlushedVersion) {
                        homeScreensaverFlushedVersion = targetVersion
                    }
                }
            }
        }
    }

    fun updateScreensaverSettings(transform: (HomeScreensaverSettings) -> HomeScreensaverSettings) {
        updateHomeScreensaverState(transform)
    }

    fun setScreensaverDisplayMode(mode: ScreensaverDisplayMode) {
        updateHomeScreensaverState { current ->
            when (mode) {
                ScreensaverDisplayMode.PORTRAIT -> current.selectPortrait()
                ScreensaverDisplayMode.LANDSCAPE -> current.selectLandscape()
                ScreensaverDisplayMode.MIRROR -> current.setMirrorEnabled(true)
            }
        }
    }

    fun setScreensaverMirrorEnabled(enabled: Boolean) {
        updateHomeScreensaverState { current ->
            current.setMirrorEnabled(enabled)
        }
    }

    fun setScreensaverAutoOrientationEnabled(enabled: Boolean) {
        updateHomeScreensaverState { current ->
            current.withAutoOrientation(enabled)
        }
    }

    fun setScreensaverActive(active: Boolean) {
        _isScreensaverActive.value = active
        if (!active) {
            val wasLowPower = _isScreensaverScreenOff.value || _isScreensaverAodActive.value
            _isScreensaverScreenOff.value = false
            _isScreensaverAodActive.value = false
            _screensaverSensorPosture.value = null
            if (wasLowPower && isAppForegrounded) {
                compassTracker.startTracking()
            }
        }
    }

    fun setScreensaverScreenOff(screenOff: Boolean, forcePureBlack: Boolean = false) {
        if (screenOff && _isScreensaverActive.value) {
            val cfg = _homeScreensaverSettings.value
            if (!forcePureBlack && cfg.aodEnabled && cfg.aodAutoActivateOnScreenOff) {
                _isScreensaverScreenOff.value = false
                _isScreensaverAodActive.value = true
                compassTracker.stopTracking()
                return
            }
            _isScreensaverAodActive.value = false
            _isScreensaverScreenOff.value = true
            // Pause compass/orientation hardware sensors during black Screen OFF mode to save battery
            // while keeping live GPS, chainage, speed, and KM Stone monitoring fully active.
            compassTracker.stopTracking()
        } else {
            _isScreensaverScreenOff.value = false
            if (!_isScreensaverAodActive.value && isAppForegrounded) {
                compassTracker.startTracking()
            }
        }
    }

    fun setScreensaverAodActive(active: Boolean) {
        val next = active && _isScreensaverActive.value && _homeScreensaverSettings.value.aodEnabled
        _isScreensaverAodActive.value = next
        if (next) {
            _isScreensaverScreenOff.value = false
            // Pause compass sensor during AMOLED AOD while keeping GPS & KM Stone monitoring live
            compassTracker.stopTracking()
        } else if (!_isScreensaverScreenOff.value && isAppForegrounded) {
            compassTracker.startTracking()
        }
    }

    fun toggleScreensaverAod() {
        setScreensaverAodActive(!_isScreensaverAodActive.value)
    }

    fun updateScreensaverSensorPosture(posture: com.example.ui.home.ScreensaverSensorPosture?) {
        _screensaverSensorPosture.value = posture
    }

    fun setScreensaverKeepScreenAwake(enabled: Boolean) {
        updateHomeScreensaverState { current ->
            current.withKeepScreenAwake(enabled)
        }
    }

    fun setScreensaverOrientationLocked(
        locked: Boolean,
        currentIsLandscape: Boolean? = null
    ) {
        updateHomeScreensaverState { current ->
            current.withOrientationLock(locked, currentIsLandscape)
        }
    }

    fun setScreensaverThemeMode(mode: com.example.model.ScreensaverThemeMode) {
        updateHomeScreensaverState { current ->
            current.withThemeMode(mode)
        }
    }

    fun toggleScreensaverMirrorQuickMode(
        enableMirror: Boolean,
        isCurrentlyLandscape: Boolean
    ) {
        updateHomeScreensaverState { current ->
            current.withMirrorQuickToggle(
                enableMirror = enableMirror,
                isCurrentlyLandscape = isCurrentlyLandscape
            )
        }
    }

    fun setScreensaverHudBrightness(brightness: Float) {
        updateHomeScreensaverState { current ->
            current.withHudBrightness(brightness)
        }
    }

    fun setHomeMiniMapOrientation(orientation: MiniMapOrientationMode) {
        updateHomeScreensaverState { current ->
            current.withOrientation(orientation)
        }
    }

    fun zoomInHomeMiniMap() {
        updateHomeScreensaverState { current ->
            current.withZoomIn()
        }
    }

    fun zoomOutHomeMiniMap() {
        updateHomeScreensaverState { current ->
            current.withZoomOut()
        }
    }

    fun setHomeMiniMapZoomLevel(zoom: Float) {
        updateHomeScreensaverState { current ->
            current.withZoomLevel(zoom)
        }
    }

    fun recenterHomeMiniMap() {
        updateHomeScreensaverState { current ->
            current.withRecenter()
        }
    }

    val miniMapViewDistancePreference: StateFlow<com.example.ui.map.MiniMapViewDistancePreference> =
        preferencesManager.miniMapViewDistanceFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            preferencesManager.getInitialMiniMapViewDistance()
        )

    fun updateMiniMapViewDistance(preference: com.example.ui.map.MiniMapViewDistancePreference) {
        val sanitized = preference.copy(customDistanceMetres = preference.validatedCustomMetres)
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "mini_map_view_distance",
            sectionKey = "timestamp",
            valueSignature = sanitized.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateMiniMapViewDistance(sanitized)
            }
        }
        // Recenter Home/Screensaver Mini Map so the new distance applies smoothly right away
        recenterHomeMiniMap()
    }

    fun setHomeMiniMapVisible(visible: Boolean) {
        updateHomeScreensaverState { current ->
            current.withMiniMapVisible(visible)
        }
    }

    fun setHomeMiniMapStyle(style: MiniMapStyle) {
        updateHomeScreensaverState { current ->
            current.withMiniMapStyle(style)
        }
        preferencesManager.launchSafeEdit {
            when (style) {
                MiniMapStyle.STANDARD -> {
                    preferencesManager.updateMapType("standard")
                    preferencesManager.updateMapTheme("system")
                }
                MiniMapStyle.SATELLITE -> {
                    preferencesManager.updateMapType("satellite")
                }
                MiniMapStyle.DARK -> {
                    preferencesManager.updateMapType("standard")
                    preferencesManager.updateMapTheme("dark")
                }
                MiniMapStyle.LIGHT -> {
                    preferencesManager.updateMapType("standard")
                    preferencesManager.updateMapTheme("light")
                }
            }
        }
    }

    private fun updateNavNotificationState(
        transform: (com.example.model.NavigationNotificationSettings) -> com.example.model.NavigationNotificationSettings
    ) {
        val (myVersion, snapshot) = synchronized(navNotifMutationLock) {
            navNotifWriteVersion += 1L
            val updated = transform(_navigationNotificationSettings.value)
            _navigationNotificationSettings.value = updated
            navNotifWriteVersion to updated
        }
        CtecsNavigationTelemetryHub.updateTelemetry(navigationNotificationSettings = snapshot)
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "navigation_notifications",
            sectionKey = "navigation_notifications",
            valueSignature = snapshot.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                val (targetVersion, targetSnapshot) = synchronized(navNotifMutationLock) {
                    if (navNotifWriteVersion <= navNotifFlushedVersion) return@executeSafeEdit
                    navNotifWriteVersion to _navigationNotificationSettings.value
                }
                preferencesManager.updateNavigationNotificationSettings(targetSnapshot)
                synchronized(navNotifMutationLock) {
                    if (targetVersion > navNotifFlushedVersion) {
                        navNotifFlushedVersion = targetVersion
                    }
                }
            }
        }
    }

    fun updateNavigationNotificationSettings(
        transform: (com.example.model.NavigationNotificationSettings) -> com.example.model.NavigationNotificationSettings
    ) {
        updateNavNotificationState(transform)
    }

    private fun syncBackgroundNavigationService() {
        val hasRoute = activeRouteData.value != null
        val alertCfg = _kmStoneAlertSettings.value
        val navCfg = _navigationNotificationSettings.value

        // Dedicated KM Stone Monitoring Foreground Service
        val kmStoneServiceActive = com.example.navigation.KmStoneMonitoringForegroundService
            .shouldServiceStayActive(alertCfg, hasRoute)
        com.example.navigation.KmStoneMonitoringForegroundService.syncServiceState(
            getApplication(),
            kmStoneServiceActive
        )

        // Navigation Notification Foreground Service (only runs when Navigation Notification itself is enabled,
        // preventing redundant dual monitoring services when KM Stone Monitoring service is active or disabled)
        LockScreenNavigationService.syncServiceState(getApplication(), navCfg.enabled)
    }

    fun setNavigationNotificationEnabled(enabled: Boolean) {
        updateNavNotificationState { current -> current.copy(enabled = enabled) }
        if (enabled) {
            startLocation()
        }
        syncBackgroundNavigationService()
    }

    fun resetNavigationNotificationSettings() {
        val defaults = com.example.model.NavigationNotificationSettings.defaultSettings()
        updateNavNotificationState { defaults }
        syncBackgroundNavigationService()
        showToast("Navigation notification settings reset", ToastType.SUCCESS)
    }

    fun resetScreensaverSettings() {
        val defaults = HomeScreensaverSettings.defaultSettings()
        updateHomeScreensaverState { defaults }
        preferencesManager.launchSafeEdit {
            preferencesManager.updateMapType("standard")
            preferencesManager.updateMapTheme("system")
        }
        showToast("Screensaver settings reset to defaults", ToastType.SUCCESS)
    }

    val cameraSettings: StateFlow<com.example.model.CameraSettings> = preferencesManager.cameraSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialCameraSettings()
    )

    // Capture mode (PHOTO vs VIDEO)
    private val _captureMode = MutableStateFlow(preferencesManager.getInitialCaptureMode())
    val captureMode: StateFlow<com.example.model.CaptureMode> = _captureMode.asStateFlow()

    // Video recording states
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isRecordingPaused = MutableStateFlow(false)
    val isRecordingPaused: StateFlow<Boolean> = _isRecordingPaused.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    val videoExportState: StateFlow<com.example.video.VideoExportState> =
        com.example.video.VideoExportManager.exportState

    private val _isExportingVideo = MutableStateFlow(false)
    val isExportingVideo: StateFlow<Boolean> = _isExportingVideo.asStateFlow()

    // Zoom & Exposure
    private val _currentZoomRatio = MutableStateFlow(preferencesManager.getInitialCameraSettings().defaultLensZoom)
    val currentZoomRatio: StateFlow<Float> = _currentZoomRatio.asStateFlow()

    private val _exposureCompensation = MutableStateFlow(preferencesManager.getInitialCameraSettings().exposureCompensationStep)
    val exposureCompensation: StateFlow<Int> = _exposureCompensation.asStateFlow()

    // Live Camera Capture Exposure Telemetry (tracks real sensor exposure for photo/video & UI readout)
    private val _captureExposureEvStops = MutableStateFlow(0f)
    val captureExposureEvStops: StateFlow<Float> = _captureExposureEvStops.asStateFlow()

    private val _captureExposureNormalized = MutableStateFlow(
        preferencesManager.getInitialCameraSettings().let { s ->
            if (s.autoBrightnessEnabled) CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL else s.manualPreviewBrightness
        }
    )
    val captureExposureNormalized: StateFlow<Float> = _captureExposureNormalized.asStateFlow()

    private val _captureExposureHardwareSupported = MutableStateFlow(true)
    val captureExposureHardwareSupported: StateFlow<Boolean> = _captureExposureHardwareSupported.asStateFlow()

    // Manual chainage override & calibration state (Single Centralized Engine: Stable & Dynamic modes)
    private val initialManualCalibration = preferencesManager.getInitialManualChainageCalibration()

    private val _manualCalibrationCommand = MutableStateFlow(initialManualCalibration)
    private val _liveManualCalibration = MutableStateFlow(initialManualCalibration)
    val manualChainageCalibration: StateFlow<com.example.model.ManualChainageCalibrationState> =
        _liveManualCalibration.asStateFlow()

    private val _manualChainage = MutableStateFlow<Chainage?>(
        initialManualCalibration.effectiveCurrentMetres?.takeIf { initialManualCalibration.isActive }?.let { Chainage(it) }
            ?: preferencesManager.getInitialManualChainageOverride()
    )
    val manualChainage: StateFlow<Chainage?> = _manualChainage.asStateFlow()

    private val _manualChainageText = MutableStateFlow<String?>(
        initialManualCalibration.effectiveDisplayChainage?.takeIf { initialManualCalibration.isActive }
            ?: preferencesManager.getInitialManualChainageText()
    )
    val manualChainageText: StateFlow<String?> = _manualChainageText.asStateFlow()

    @Volatile
    private var lastPersistedManualMetres: Long? = initialManualCalibration.currentChainageMetres
    @Volatile
    private var lastPersistedManualCalibrated: Boolean = initialManualCalibration.isDynamicAnchorCalibrated

    val savedCustomChainages: StateFlow<List<String>> = preferencesManager.savedCustomChainagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialSavedCustomChainages()
    )

    // Smart KMZ Chainage Correction StateFlows
    val routeCorrectionsMap: StateFlow<Map<String, List<ChainageCorrectionPoint>>> = preferencesManager.routeCorrectionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialRouteCorrections()
    )

    val isRouteCorrectionsEnabled: StateFlow<Boolean> = preferencesManager.routeCorrectionsEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialRouteCorrectionsEnabled()
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
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val calibrationProfiles: StateFlow<List<PermanentCalibrationProfile>> = preferencesManager.calibrationProfilesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialCalibrationProfiles()
    )

    private val _activeProfileCompatibility = MutableStateFlow<SmartChainageCorrectionEngine.ProfileCompatibilityResult?>(null)
    val activeProfileCompatibility: StateFlow<SmartChainageCorrectionEngine.ProfileCompatibilityResult?> = _activeProfileCompatibility.asStateFlow()

    val chainageAuditLog: StateFlow<List<ChainageLogEntry>> = preferencesManager.chainageAuditLogFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesManager.getInitialChainageAuditLog()
    )

    private val smartDirectionMutationLock = Any()
    private var smartDirectionWriteVersion: Long = 0L
    private var smartDirectionFlushedVersion: Long = 0L

    private val _smartDirectionPreferences = MutableStateFlow(preferencesManager.getInitialSmartDirectionPreferences())
    val smartDirectionPreferences: StateFlow<com.example.model.SmartDirectionPreferences> =
        _smartDirectionPreferences.asStateFlow()

    val directionLockMode: StateFlow<String> = _smartDirectionPreferences
        .map { it.internalDirectionMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _smartDirectionPreferences.value.internalDirectionMode
        )

    val smartDirectionState: StateFlow<com.example.model.SmartDirectionState> =
        CtecsNavigationTelemetryHub.directionState

    private val progressionTracker = com.example.geo.ChainageProgressionTracker().apply {
        val dirPrefs = _smartDirectionPreferences.value
        if (dirPrefs.controlMode == com.example.model.DirectionControlMode.MANUAL_OVERRIDE &&
            !dirPrefs.normalizedManualSide.isNullOrBlank()
        ) {
            setManualSide(dirPrefs.normalizedManualSide!!)
        }
    }
    private val refreshCoordinator = ChainageAccuracyRefreshCoordinator()
    private val stuckChainageMonitor = StuckChainageMonitor()
    private var manualDirectionInactivityJob: Job? = null

    val selectedAlignmentId = MutableStateFlow<String?>(preferencesManager.getInitialSelectedAlignmentId())
    @Volatile
    private var lastCalculatedMatchResult: ChainageMatchResult? = null

    fun selectAlignment(alignmentId: String?) {
        selectedAlignmentId.value = alignmentId
        CtecsNavigationTelemetryHub.sharedDirectionManager.onAlignmentChanged(
            routeId = activeRouteData.value?.id,
            alignmentId = alignmentId
        )
        preferencesManager.launchSafeEdit {
            preferencesManager.updateSelectedAlignmentId(alignmentId)
        }
    }

    private val effectiveRouteFlow: Flow<RouteData?> = combine(activeRouteData, selectedAlignmentId) { route, selectedAlignId ->
        if (selectedAlignId != null && route != null) {
            route.copy(selectedAlignmentId = selectedAlignId)
        } else {
            route
        }
    }.distinctUntilChanged()

    // Dedicated CoroutineScope for conflated GPS & alignment matching + Heartbeat Monitor
    private val calibrationMatchingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeMatchingSubTaskJob: Job? = null
    private val _autoMatchStateFlow = MutableStateFlow(ChainageMatchResult.noRoute())

    private val _isRefreshingChainageAccuracy = MutableStateFlow(false)
    val isRefreshingChainageAccuracy: StateFlow<Boolean> = _isRefreshingChainageAccuracy.asStateFlow()

    private val _chainageAccuracyReport = MutableStateFlow<ChainageAccuracyReport?>(null)
    val chainageAccuracyReport: StateFlow<ChainageAccuracyReport?> = _chainageAccuracyReport.asStateFlow()

    val calibrationDiagnostics: StateFlow<SmartChainageCorrectionEngine.CalibrationEngineDiagnostics> =
        SmartChainageCorrectionEngine.diagnosticsFlow

    private fun computeAutoMatchForState(
        loc: LocationState,
        route: RouteData?,
        correctionsMap: Map<String, List<ChainageCorrectionPoint>>,
        isCorrectionsEnabled: Boolean
    ): ChainageMatchResult {
        // Tier 4 fallback if no GPS fix
        if (loc.point == null) {
            val baseNoGps = ChainageMatchResult.noRoute(loc.accuracy).copy(
                source = ChainageSource.GPS,
                timestamp = if (loc.lastFixTime > 0L) loc.lastFixTime else System.currentTimeMillis()
            )
            val dirState = evaluateAndPublishSmartDirection(loc, baseNoGps, route)
            return baseNoGps.copy(
                detectedSide = dirState.confirmedSide ?: "Unknown",
                smartDirectionState = dirState
            )
        }

        // Tier 4 fallback if no route loaded
        if (route == null || (route.markers.isEmpty() && route.alignments.isEmpty())) {
            val baseNoRoute = ChainageMatchResult(
                projectedPoint = loc.point,
                confidence = com.example.model.MatchConfidence.NO_MATCH,
                gpsAccuracy = loc.accuracy,
                statusMessage = "No route loaded (GPS Active)",
                timestamp = if (loc.lastFixTime > 0L) loc.lastFixTime else System.currentTimeMillis(),
                source = ChainageSource.GPS
            )
            val dirState = evaluateAndPublishSmartDirection(loc, baseNoRoute, null)
            return baseNoRoute.copy(
                detectedSide = dirState.confirmedSide ?: "Unknown",
                smartDirectionState = dirState
            )
        }

        val startNs = System.nanoTime()
        SmartChainageCorrectionEngine.notifyTaskStarted()

        val activeAlign = route.activeAlignment ?: route.alignments.firstOrNull()
        val cachedGeom = if (activeAlign != null && activeAlign.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(activeAlign, route.markers)
        } else null

        // Calculate original KMZ alignment match with precision nearest-segment & continuity
        val kmzMatch = ChainageMatcher.match(
            gpsPoint = loc.point,
            gpsAccuracy = loc.accuracy,
            bearing = loc.bearing,
            routeData = route,
            previousResult = lastCalculatedMatchResult,
            fixTimeMs = loc.lastFixTime
        )

        val rawKmzChainage = kmzMatch.chainage

        if (kmzMatch.confidence == com.example.model.MatchConfidence.NO_MATCH || rawKmzChainage == null) {
            val stabilized = progressionTracker.processResult(kmzMatch, route.id)
            val dirState = evaluateAndPublishSmartDirection(loc, stabilized, route)
            val enrichedNoMatch = stabilized.copy(
                detectedSide = dirState.confirmedSide ?: "Unknown",
                smartDirectionState = dirState,
                source = ChainageSource.GPS,
                rawUncorrectedChainage = null
            )
            lastCalculatedMatchResult = enrichedNoMatch
            val latencyUs = (System.nanoTime() - startNs) / 1_000L
            SmartChainageCorrectionEngine.notifyTaskCompleted(
                latencyMicros = latencyUs,
                alignmentName = activeAlign?.name ?: route.name,
                segmentsCount = cachedGeom?.segmentCount ?: 0,
                gridCellsCount = cachedGeom?.mappedMarkers?.size ?: 0,
                matchedChainage = enrichedNoMatch.displayChainage,
                offsetMetres = enrichedNoMatch.offsetMetres,
                confidence = enrichedNoMatch.confidence.name,
                hierarchyTier = SmartChainageCorrectionEngine.CalibrationHierarchyTier.TIER_5_GPS_FALLBACK.label
            )
            return enrichedNoMatch
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

        // Smart stuck-chainage detection: detect valid displaced GPS updates >= 12m with unchanged chainage
        // without triggering false positives when device is legitimately stationary (< 4.5m).
        val isManualActive = _manualChainage.value != null || !_manualChainageText.value.isNullOrBlank()
        val stuckDecision = stuckChainageMonitor.evaluate(
            gpsPoint = loc.point,
            fixTimeMs = loc.lastFixTime,
            matchResult = stabilized,
            routeId = route.id,
            isManualOverride = isManualActive
        )
        if (stuckDecision.isStuck) {
            progressionTracker.resetChainageFilter()
            lastCalculatedMatchResult = null
            val recoveredKmz = ChainageMatcher.match(
                gpsPoint = loc.point,
                gpsAccuracy = loc.accuracy,
                bearing = loc.bearing,
                routeData = route,
                previousResult = null,
                fixTimeMs = loc.lastFixTime
            )
            val recoveredRaw = recoveredKmz.chainage
            val recoveredSmart = if (isCorrectionsEnabled && routeCorrections.isNotEmpty() &&
                recoveredKmz.confidence != com.example.model.MatchConfidence.NO_MATCH && recoveredRaw != null
            ) {
                SmartChainageCorrectionEngine.applyCorrection(
                    rawChainage = recoveredRaw,
                    routeData = route,
                    correctionPoints = routeCorrections,
                    gpsPoint = loc.point,
                    gpsAccuracy = loc.accuracy,
                    confidence = recoveredKmz.confidence,
                    distanceAlongAlignmentMetres = recoveredKmz.distanceAlongAlignmentMetres,
                    isUncertainJump = recoveredKmz.isUncertain
                )
            } else {
                null
            }
            val preRecovered = when {
                recoveredSmart != null -> recoveredKmz.copy(
                    chainage = recoveredSmart.correctedChainage,
                    source = ChainageSource.CUSTOM_CORRECTION,
                    rawUncorrectedChainage = recoveredRaw,
                    correctionPointInfo = recoveredSmart.infoMessage,
                    statusMessage = "Auto-Recovered (${recoveredSmart.infoMessage})"
                )
                recoveredRaw != null -> recoveredKmz.copy(
                    source = ChainageSource.KMZ,
                    rawUncorrectedChainage = recoveredRaw,
                    statusMessage = "Auto-Recovered KMZ Match"
                )
                else -> recoveredKmz.copy(
                    source = ChainageSource.GPS,
                    rawUncorrectedChainage = null
                )
            }
            val recoveredStabilized = progressionTracker.processResult(preRecovered, route.id)
            val recoveredDir = evaluateAndPublishSmartDirection(loc, recoveredStabilized, route)
            val enrichedRecovered = recoveredStabilized.copy(
                detectedSide = recoveredDir.confirmedSide ?: "Unknown",
                smartDirectionState = recoveredDir
            )
            lastCalculatedMatchResult = enrichedRecovered
            _autoMatchStateFlow.value = enrichedRecovered
            SmartChainageCorrectionEngine.recordStuckChainageRecovery(
                component = stuckDecision.affectedComponent,
                reason = stuckDecision.reason
            )
            calibrationMatchingScope.launch {
                restartCalibrationMatchingSubTask("Auto-recovery: ${stuckDecision.affectedComponent}")
            }
            return enrichedRecovered
        }

        val dirState = evaluateAndPublishSmartDirection(loc, stabilized, route)
        val enrichedResult = stabilized.copy(
            detectedSide = dirState.confirmedSide ?: "Unknown",
            smartDirectionState = dirState
        )
        lastCalculatedMatchResult = enrichedResult

        val latencyUs = (System.nanoTime() - startNs) / 1_000L
        val tierLabel = smartCorrected?.hierarchyTier?.label
            ?: SmartChainageCorrectionEngine.CalibrationHierarchyTier.TIER_2_VALIDATED_ALIGNMENT.label

        SmartChainageCorrectionEngine.notifyTaskCompleted(
            latencyMicros = latencyUs,
            alignmentName = activeAlign?.name ?: route.name,
            segmentsCount = cachedGeom?.segmentCount ?: 0,
            gridCellsCount = cachedGeom?.mappedMarkers?.size ?: 0,
            matchedChainage = enrichedResult.displayChainage,
            offsetMetres = enrichedResult.offsetMetres,
            confidence = enrichedResult.confidence.name,
            hierarchyTier = tierLabel
        )
        return enrichedResult
    }

    /**
     * Runs the single shared [com.example.navigation.SmartDirectionManager], publishes the result
     * to [CtecsNavigationTelemetryHub], updates in-memory profile & active survey road side ONLY
     * when explicit AUTO mode is active (never overwriting a locked manual LHS/RHS override),
     * and announces confirmed AUTO direction changes via TTS.
     */
    private fun evaluateAndPublishSmartDirection(
        loc: LocationState,
        matchResult: ChainageMatchResult,
        route: RouteData?
    ): com.example.model.SmartDirectionState {
        val prefs = _smartDirectionPreferences.value
        val dirState = CtecsNavigationTelemetryHub.sharedDirectionManager.evaluate(
            locationState = loc,
            matchResult = matchResult,
            routeData = route,
            preferences = prefs
        )
        CtecsNavigationTelemetryHub.updateTelemetry(directionState = dirState)

        // Strict Manual Override Lock: NEVER auto-switch manual LHS/RHS back to AUTO and
        // NEVER overwrite ProjectProfile roadSide while manual override is locked.
        val isManualLocked = prefs.controlMode == com.example.model.DirectionControlMode.MANUAL_OVERRIDE ||
            dirState.isManualOverride

        val confirmedBinary = dirState.confirmedBinarySide
        if (!isManualLocked && confirmedBinary != null) {
            val detectedEnum = RoadSide.fromString(confirmedBinary)
            if (_projectProfile.value.roadSide != detectedEnum) {
                _projectProfile.update { it.copy(roadSide = detectedEnum) }
            }
            // Keep active survey session synchronized with confirmed auto direction
            val activeSurvey = surveyManager.activeSession.value
            if (activeSurvey != null && activeSurvey.status == com.example.survey.model.SurveyStatus.RECORDING) {
                val surveySide = if (confirmedBinary == "LHS") {
                    com.example.survey.model.RoadSide.LHS
                } else {
                    com.example.survey.model.RoadSide.RHS
                }
                if (activeSurvey.roadSide != surveySide) {
                    viewModelScope.launch {
                        surveyManager.updateRoadSide(surveySide)
                    }
                }
            }
        }

        // Announce ONLY genuinely confirmed LHS <-> RHS direction transitions in explicit AUTO mode
        if (dirState.directionChangedFrom != null && !isManualLocked) {
            com.example.navigation.KmStoneVoiceAnnouncementManager.announceDirectionChange(
                context = getApplication(),
                directionState = dirState,
                settings = _kmStoneAlertSettings.value,
                voiceDirectionEnabled = prefs.voiceDirectionAnnouncementsEnabled,
                isRecordingActive = CtecsNavigationTelemetryHub.isCameraOrSurveyRecording.value
            )
        }

        return dirState
    }

    /**
     * Manual direction lock never expires automatically; ensure no legacy inactivity job runs.
     */
    private fun scheduleManualDirectionInactivityWatcher() {
        manualDirectionInactivityJob?.cancel()
        manualDirectionInactivityJob = null
    }

    /**
     * Starts or restarts the dedicated conflated GPS alignment matching sub-task without requiring
     * a full application refresh or screen navigation.
     */
    fun restartCalibrationMatchingSubTask(reason: String = "Manual diagnostic restart") {
        activeMatchingSubTaskJob?.cancel()
        if (reason != "Initial startup") {
            SmartChainageCorrectionEngine.recordSubTaskRestarted(reason)
        }
        activeMatchingSubTaskJob = calibrationMatchingScope.launch {
            val conflatedGpsFlow = locationState
                .onEach { SmartChainageCorrectionEngine.recordGpsUpdateReceived() }
                .conflate()

            combine(
                conflatedGpsFlow,
                effectiveRouteFlow,
                routeCorrectionsMap,
                isRouteCorrectionsEnabled,
                _smartDirectionPreferences
            ) { loc, route, correctionsMap, isCorrectionsEnabled, _ ->
                computeAutoMatchForState(loc, route, correctionsMap, isCorrectionsEnabled)
            }.conflate().collect { result ->
                _autoMatchStateFlow.value = result
            }
        }
    }

    // Unified live match result with strict 4-tier priority across Home Page, Camera, and Media Watermarks:
    // 1. MANUAL (Direction-aware Continue Stable Chainage OR Dynamic Chainage Correction)
    // 2. CUSTOM CORRECTION (within valid KMZ correction coverage)
    // 3. KMZ (original imported KMZ chainage)
    // 4. GPS (unmatched fallback)
    val liveMatchResult: StateFlow<ChainageMatchResult> = combine(
        _autoMatchStateFlow,
        _manualCalibrationCommand
    ) { autoResult, calCommand ->
        if (calCommand.isActive && calCommand.effectiveCurrentMetres != null) {
            val currentLive = _liveManualCalibration.value
            val sameAnchorSession = currentLive.isActive &&
                currentLive.mode == calCommand.mode &&
                currentLive.anchorChainageMetres == calCommand.anchorChainageMetres &&
                currentLive.anchorTimestampMs == calCommand.anchorTimestampMs
            val workingCal = if (sameAnchorSession) currentLive else calCommand
            val nowMs = System.currentTimeMillis()
            val resolution = progressionTracker.processWithManualCalibration(
                autoResult = autoResult,
                locationState = locationTracker.locationState.value,
                routeData = activeRouteData.value,
                calibration = workingCal,
                nowMs = nowMs
            )
            _liveManualCalibration.value = resolution.updatedCalibration
            val activeChainage = resolution.matchResult.chainage
            if (activeChainage != null) {
                _manualChainage.value = activeChainage
                _manualChainageText.value = activeChainage.format()
            }
            val newMetres = resolution.updatedCalibration.currentChainageMetres
            val newCalibrated = resolution.updatedCalibration.isDynamicAnchorCalibrated
            val shouldPersist = resolution.calibrationChangedSignificantly && (
                newCalibrated != lastPersistedManualCalibrated ||
                    lastPersistedManualMetres == null ||
                    (newMetres != null && kotlin.math.abs(newMetres - (lastPersistedManualMetres ?: newMetres)) >= 5L)
                )
            if (shouldPersist) {
                lastPersistedManualMetres = newMetres
                lastPersistedManualCalibrated = newCalibrated
                val snapshotToPersist = resolution.updatedCalibration
                preferencesManager.launchSafeEdit {
                    preferencesManager.updateManualChainageCalibration(snapshotToPersist)
                }
            }
            resolution.matchResult
        } else {
            autoResult
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = if (initialManualCalibration.isActive && initialManualCalibration.effectiveCurrentMetres != null) {
            val initCh = Chainage(initialManualCalibration.effectiveCurrentMetres!!)
            ChainageMatchResult.manual(
                chainage = initCh,
                customText = initCh.format()
            ).copy(
                manualChainageMode = initialManualCalibration.mode,
                travelDirection = initialManualCalibration.travelDirection,
                manualCalibrationAnchorMetres = initialManualCalibration.anchorChainageMetres,
                manualCalibrationOffsetMetres = initialManualCalibration.computedOffsetMetres,
                isManualCalibrationPendingGps = initialManualCalibration.mode == com.example.model.ManualChainageMode.DYNAMIC_CORRECTION &&
                    !initialManualCalibration.isDynamicAnchorCalibrated
            )
        } else {
            ChainageMatchResult.noRoute()
        }
    )

    val cameraMatchResult: StateFlow<ChainageMatchResult> = liveMatchResult

    // Photos list
    val allPhotos: StateFlow<List<PhotoRecordEntity>> = photoRepository.allPhotos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Camera settings in UI
    private val _isFlashOn = MutableStateFlow(preferencesManager.getInitialIsFlashOn())
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(preferencesManager.getInitialIsFrontCamera())
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

    private val _pendingMediaDeleteRequest = MutableStateFlow<PendingMediaDeleteRequest?>(null)
    val pendingMediaDeleteRequest: StateFlow<PendingMediaDeleteRequest?> = _pendingMediaDeleteRequest.asStateFlow()

    private val _isDeletingMedia = MutableStateFlow(false)
    val isDeletingMedia: StateFlow<Boolean> = _isDeletingMedia.asStateFlow()

    private val inFlightDeleteKeys = mutableSetOf<String>()

    init {
        // Synchronize authoritative in-memory ProjectProfile with persisted DataStore preferences
        viewModelScope.launch {
            preferencesManager.projectProfileFlow.collect { savedProfile ->
                _projectProfile.value = savedProfile
            }
        }

        // Synchronize Home Screensaver & Shared Mini Map preferences with DataStore
        viewModelScope.launch {
            preferencesManager.homeScreensaverSettingsFlow.collect { savedSettings ->
                var resolvedSnapshot: HomeScreensaverSettings? = null
                synchronized(homeScreensaverMutationLock) {
                    if (homeScreensaverFlushedVersion == homeScreensaverWriteVersion) {
                        val currentEpoch = _homeScreensaverSettings.value.recenterEpoch
                        val updated = savedSettings.copy(recenterEpoch = currentEpoch)
                        _homeScreensaverSettings.value = updated
                        resolvedSnapshot = updated
                    }
                }
                resolvedSnapshot?.let { snap ->
                    CtecsNavigationTelemetryHub.updateTelemetry(screensaverSettings = snap)
                }
            }
        }

        // Synchronize Navigation Notification preferences with DataStore & Hub
        viewModelScope.launch {
            preferencesManager.navigationNotificationSettingsFlow.collect { savedNotif ->
                var resolvedNotif: com.example.model.NavigationNotificationSettings? = null
                synchronized(navNotifMutationLock) {
                    if (navNotifFlushedVersion == navNotifWriteVersion) {
                        _navigationNotificationSettings.value = savedNotif
                        resolvedNotif = savedNotif
                    }
                }
                resolvedNotif?.let { snap ->
                    CtecsNavigationTelemetryHub.updateTelemetry(navigationNotificationSettings = snap)
                    syncBackgroundNavigationService()
                }
            }
        }

        // Synchronize Widget settings with DataStore, Hub, and Native AppWidgets
        viewModelScope.launch {
            preferencesManager.widgetSettingsFlow.collect { savedWidgets ->
                var resolvedWidgets: com.example.model.CtecsWidgetSettings? = null
                synchronized(widgetSettingsMutationLock) {
                    if (widgetSettingsFlushedVersion == widgetSettingsWriteVersion) {
                        _widgetSettings.value = savedWidgets
                        resolvedWidgets = savedWidgets
                    }
                }
                resolvedWidgets?.let { snap ->
                    CtecsNavigationTelemetryHub.updateTelemetry(widgetSettings = snap)
                    com.example.widget.CtecsWidgetUpdater.cacheWidgetSettings(getApplication(), snap)
                    com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
                }
            }
        }

        // Synchronize KM Stone Arrival Alert settings with DataStore & Hub
        viewModelScope.launch {
            preferencesManager.kmStoneAlertSettingsFlow.collect { savedAlerts ->
                var resolvedAlerts: com.example.model.KmStoneAlertSettings? = null
                synchronized(kmStoneAlertMutationLock) {
                    if (kmStoneAlertFlushedVersion == kmStoneAlertWriteVersion) {
                        _kmStoneAlertSettings.value = savedAlerts
                        resolvedAlerts = savedAlerts
                    }
                }
                resolvedAlerts?.let { snap ->
                    CtecsNavigationTelemetryHub.updateTelemetry(kmStoneAlertSettings = snap)
                    syncBackgroundNavigationService()
                }
            }
        }

        // Track active Camera or Survey recording so KM Stone alerts never interrupt recording
        viewModelScope.launch {
            combine(_isRecording, surveyManager.activeSession) { isRec, surveySession ->
                val isSurveyActive = surveySession != null &&
                    surveySession.status == com.example.survey.model.SurveyStatus.RECORDING
                isRec || isSurveyActive
            }.distinctUntilChanged().collect { recordingActive ->
                CtecsNavigationTelemetryHub.updateTelemetry(isCameraOrSurveyRecording = recordingActive)
            }
        }

        // Mirror live chainage, route, project, compass, and clock format into shared Navigation Hub
        viewModelScope.launch {
            liveMatchResult.collect { match ->
                CtecsNavigationTelemetryHub.updateTelemetry(matchResult = match)
            }
        }
        viewModelScope.launch {
            activeRouteData.collect { route ->
                lastCalculatedMatchResult = null
                CtecsNavigationTelemetryHub.sharedDirectionManager.onAlignmentChanged(
                    routeId = route?.id,
                    alignmentId = selectedAlignmentId.value ?: route?.selectedAlignmentId ?: route?.activeAlignment?.id
                )
                val refreshedDir = evaluateAndPublishSmartDirection(
                    loc = locationTracker.locationState.value,
                    matchResult = _autoMatchStateFlow.value,
                    route = route
                )
                _autoMatchStateFlow.update { current ->
                    current.copy(
                        detectedSide = refreshedDir.confirmedSide ?: "Unknown",
                        smartDirectionState = refreshedDir
                    )
                }
                CtecsNavigationTelemetryHub.updateTelemetry(routeData = route)
                syncBackgroundNavigationService()
                com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
            }
        }
        // Immediately synchronize persisted manual direction lock (or AUTO) on ViewModel startup
        // so reopening the Camera or recreating Activity restores the exact mode before any GPS fix arrives.
        run {
            val initialDirPrefs = _smartDirectionPreferences.value
            CtecsNavigationTelemetryHub.updateTelemetry(directionPreferences = initialDirPrefs)
            if (initialDirPrefs.controlMode == com.example.model.DirectionControlMode.MANUAL_OVERRIDE &&
                !initialDirPrefs.normalizedManualSide.isNullOrBlank()
            ) {
                val restoredSide = initialDirPrefs.normalizedManualSide!!
                CtecsNavigationTelemetryHub.sharedDirectionManager.onManualDirectionSelected(
                    restoredSide,
                    initialDirPrefs.manualSelectedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
                progressionTracker.setManualSide(restoredSide)
                val restoredEnum = RoadSide.fromString(restoredSide)
                if (_projectProfile.value.roadSide != restoredEnum) {
                    _projectProfile.update { it.copy(roadSide = restoredEnum) }
                }
            } else {
                CtecsNavigationTelemetryHub.sharedDirectionManager.onAutoModeSelected()
                progressionTracker.clearManualSide()
            }
            val initialDirState = evaluateAndPublishSmartDirection(
                loc = locationTracker.locationState.value,
                matchResult = _autoMatchStateFlow.value,
                route = activeRouteData.value
            )
            _autoMatchStateFlow.update { current ->
                current.copy(
                    detectedSide = initialDirState.confirmedSide ?: current.detectedSide,
                    smartDirectionState = initialDirState
                )
            }
        }

        viewModelScope.launch {
            preferencesManager.smartDirectionPreferencesFlow.collect { prefs ->
                var applied: com.example.model.SmartDirectionPreferences? = null
                synchronized(smartDirectionMutationLock) {
                    if (smartDirectionFlushedVersion == smartDirectionWriteVersion) {
                        _smartDirectionPreferences.value = prefs
                        applied = prefs
                    }
                }
                applied?.let { resolved ->
                    CtecsNavigationTelemetryHub.updateTelemetry(directionPreferences = resolved)
                    if (resolved.controlMode == com.example.model.DirectionControlMode.MANUAL_OVERRIDE &&
                        !resolved.normalizedManualSide.isNullOrBlank()
                    ) {
                        val sideLabel = resolved.normalizedManualSide!!
                        CtecsNavigationTelemetryHub.sharedDirectionManager.onManualDirectionSelected(
                            sideLabel,
                            resolved.manualSelectedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
                        )
                        progressionTracker.setManualSide(sideLabel)
                    } else {
                        CtecsNavigationTelemetryHub.sharedDirectionManager.onAutoModeSelected()
                        progressionTracker.clearManualSide()
                    }
                    manualDirectionInactivityJob?.cancel()
                    manualDirectionInactivityJob = null
                }
            }
        }
        viewModelScope.launch {
            _projectProfile.collect { profile ->
                CtecsNavigationTelemetryHub.updateTelemetry(projectProfile = profile)
                com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
            }
        }
        viewModelScope.launch {
            _currentLocationName.collect { locName ->
                CtecsNavigationTelemetryHub.updateTelemetry(locationName = locName)
                com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
            }
        }
        viewModelScope.launch {
            compassState.collect { compass ->
                CtecsNavigationTelemetryHub.updateTelemetry(compassState = compass)
            }
        }
        viewModelScope.launch {
            cameraSettings.collect { cam ->
                CtecsNavigationTelemetryHub.updateTelemetry(is24HourFormat = cam.is24HourFormat)
            }
        }

        // Evaluate KM Stone Arrival Alerts & update Home-Screen Widgets on GPS/Chainage changes
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                locationTracker.locationState,
                liveMatchResult,
                activeRouteData,
                _projectProfile,
                _kmStoneAlertSettings
            ) { loc, match, route, profile, alertSettings ->
                if (!CtecsNavigationTelemetryHub.isServiceRunning.value &&
                    !CtecsNavigationTelemetryHub.isKmStoneServiceRunning.value
                ) {
                    val events = CtecsNavigationTelemetryHub.sharedKmStoneDetector.evaluate(
                        locationState = loc,
                        matchResult = match,
                        routeData = route,
                        projectRoadSide = profile.roadSide,
                        settings = alertSettings,
                        smartDirectionState = match.smartDirectionState
                    )
                    if (events.isNotEmpty()) {
                        events.forEach { evt ->
                            if (evt.kind != com.example.navigation.KmStoneAlertKind.VOICE_DISTANCE) {
                                _lastKmStoneAlertEvent.value = evt
                            }
                            com.example.navigation.KmStoneAlertFeedbackManager.dispatchAlert(
                                context = getApplication(),
                                event = evt,
                                settings = alertSettings,
                                isRecordingActive = CtecsNavigationTelemetryHub.isCameraOrSurveyRecording.value
                            )
                        }
                    }
                }
                Unit
            }.collect {
                com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
            }
        }

        // Retain last valid GPS coordinate/bearing & feed centralized Map Heading engine seamlessly
        viewModelScope.launch {
            locationTracker.locationState.collect { loc ->
                if (loc.point != null) {
                    _lastValidLocation.value = loc.point
                }
                if (loc.bearing != null) {
                    lastValidGpsBearing = loc.bearing
                }
                mapHeadingManager.processGpsSample(
                    point = loc.point,
                    speedMps = loc.speed,
                    gpsBearingDeg = loc.bearing,
                    accuracyMetres = loc.accuracy.takeIf { it > 0f },
                    routeData = activeRouteData.value,
                    timestampMs = loc.lastFixTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            }
        }

        // Shared KMZ/KML offline Mini Map synchronization across all screens
        viewModelScope.launch {
            combine(
                offlineMapAutoPreload,
                offlineMapWifiOnly
            ) { autoPreload, wifiOnly -> autoPreload to wifiOnly }
                .distinctUntilChanged()
                .collect { (autoPreload, wifiOnly) ->
                    offlineMapSyncManager.updatePreferences(
                        context = getApplication(),
                        autoPreload = autoPreload,
                        wifiOnly = wifiOnly
                    )
                }
        }
        viewModelScope.launch {
            combine(
                activeRouteData,
                mapTypePreference,
                _homeScreensaverSettings
            ) { route, mapType, hudSettings ->
                val effectiveType = if (hudSettings.miniMapStyle == MiniMapStyle.SATELLITE ||
                    mapType.equals("satellite", ignoreCase = true)
                ) {
                    "satellite"
                } else {
                    "standard"
                }
                route to effectiveType
            }.distinctUntilChanged().collect { (route, effectiveType) ->
                offlineMapSyncManager.syncRoute(
                    context = getApplication(),
                    routeData = route,
                    mapType = effectiveType
                )
            }
        }

        // Start dedicated conflated GPS + Alignment matching sub-task
        restartCalibrationMatchingSubTask("Initial startup")

        // Heartbeat-based monitor that detects stalls in chainage calculation tasks and automatically restarts affected sub-tasks
        calibrationMatchingScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(SmartChainageCorrectionEngine.DEFAULT_HEARTBEAT_INTERVAL_MS)
                val stallDetected = SmartChainageCorrectionEngine.checkHeartbeatAndDetectStall(
                    stallThresholdMs = SmartChainageCorrectionEngine.DEFAULT_STALL_TIMEOUT_MS
                )
                val subTaskDead = activeMatchingSubTaskJob?.isActive != true
                if (stallDetected || subTaskDead) {
                    val reason = if (stallDetected) {
                        "Heartbeat watchdog detected calculation stall (>2.5s)"
                    } else {
                        "Heartbeat watchdog restarted inactive matching sub-task"
                    }
                    restartCalibrationMatchingSubTask(reason)
                }
            }
        }

        // Restore saved manual chainage calibration state cleanly on startup / backup restore
        viewModelScope.launch {
            preferencesManager.manualChainageCalibrationFlow.collect { savedCal ->
                val live = _liveManualCalibration.value
                val sameLiveSession = savedCal.isActive == live.isActive &&
                    savedCal.mode == live.mode &&
                    savedCal.anchorChainageMetres == live.anchorChainageMetres &&
                    savedCal.anchorTimestampMs == live.anchorTimestampMs
                if (!sameLiveSession) {
                    _liveManualCalibration.value = savedCal
                    _manualCalibrationCommand.value = savedCal
                    lastPersistedManualMetres = savedCal.currentChainageMetres
                    lastPersistedManualCalibrated = savedCal.isDynamicAnchorCalibrated
                    val ch = savedCal.effectiveCurrentMetres?.takeIf { savedCal.isActive }?.let { Chainage(it) }
                    _manualChainage.value = ch
                    _manualChainageText.value = ch?.format()
                }
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

        // Hydrate cached weather forecast & observation on startup (preserving original observation timestamp)
        weatherRepository.seedFromPersistedForecast(preferencesManager.getInitialCachedWeatherForecastBundle())
        weatherRepository.seedFromPersistedCache(preferencesManager.getInitialCachedWeatherObservation())

        // Isolated, throttled reverse-geocoding & shared live weather collector on Dispatchers.IO
        // Reuses the existing LocationTracker — zero duplicate GPS listeners or network storms
        viewModelScope.launch(Dispatchers.IO) {
            var lastGeocodedPt: com.example.model.GeoPoint? = null
            var lastGeocodedTimeMs = 0L
            locationTracker.locationState.collect { state ->
                val pt = state.point ?: return@collect
                val now = System.currentTimeMillis()
                val moved = lastGeocodedPt?.let { com.example.geo.GeoUtils.distanceMetres(it, pt) } ?: Double.MAX_VALUE
                if (moved >= 45.0 || (now - lastGeocodedTimeMs) >= 15_000L || _currentLocationName.value.isBlank()) {
                    lastGeocodedPt = pt
                    lastGeocodedTimeMs = now
                    val name = com.example.location.LocationNameResolver.resolveLocationName(
                        context = getApplication(),
                        latitude = pt.latitude,
                        longitude = pt.longitude
                    )
                    if (name.isNotBlank() && name != _currentLocationName.value) {
                        _currentLocationName.value = name
                        preferencesManager.updateLastDetectedLocationName(name)
                    }
                }
                val resolvedName = _currentLocationName.value.ifBlank { overlayConfig.value.lastDetectedLocationName }
                if (resolvedName.isNotBlank()) {
                    weatherRepository.attachResolvedLocationName(resolvedName)
                }
                val ttlMs = weatherIntelligenceSettings.value.autoRefreshIntervalMinutes.toLong() * 60_000L
                val fetchedWeather = weatherRepository.refreshForLocation(
                    point = pt,
                    resolvedLocationName = resolvedName,
                    forceRefresh = false,
                    cacheTtlMs = ttlMs
                )
                if (fetchedWeather != null && fetchedWeather.fetchedAtMs >= now - 2_000L) {
                    val bundle = weatherRepository.forecastBundle.value
                    if (bundle != null) {
                        preferencesManager.saveCachedWeatherForecastBundle(bundle)
                        val alerts = weatherAlertNotifier.evaluateActionableAlerts(
                            bundle = bundle,
                            settings = weatherIntelligenceSettings.value,
                            assessments = highwayWorkAssessments.value,
                            targetPrediction = workTargetPrediction.value
                        )
                        weatherAlertNotifier.dispatchSystemNotifications(getApplication(), alerts)
                    } else {
                        preferencesManager.saveCachedWeatherObservation(fetchedWeather)
                    }
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

        // Silent background check for updates (deferred after startup & throttled to once every 12 hours)
        viewModelScope.launch {
            kotlinx.coroutines.delay(4_000L)
            if (appUpdateManager.shouldRunAutomaticCheck()) {
                checkForAppUpdate(isManual = false)
            }
        }

        // Launch-time check: detect if a survey was interrupted and prompt user to resume
        viewModelScope.launch {
            kotlinx.coroutines.delay(450)
            val interrupted = surveyRepository.getInterruptedSessionOnStartup()
            if (interrupted != null) {
                _interruptedSurveySession.value = interrupted
            }
        }

        // Auto-sync storage videos and photos into Room (deferred on IO so first frame stays snappy)
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1_500L)
            photoRepository.syncMediaFilesFromStorage()
        }

        // Seed initial persisted signatures so initial load/recomposition never triggers spurious save animations
        settingsAutoSaveCoordinator.seedPersistedSignature("project_profile", _projectProfile.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("camera_settings", cameraSettings.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("overlay_config", overlayConfig.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("screensaver_settings", _homeScreensaverSettings.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("navigation_notifications", _navigationNotificationSettings.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("widget_settings", _widgetSettings.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("km_stone_alerts", _kmStoneAlertSettings.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("chainage_voice_assistant", _chainageVoiceAssistantSettings.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("offline_auto_preload", offlineMapAutoPreload.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("offline_wifi_only", offlineMapWifiOnly.value.hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("map_theme", mapThemePreference.value.lowercase().hashCode())
        settingsAutoSaveCoordinator.seedPersistedSignature("map_type", mapTypePreference.value.lowercase().hashCode())

        // Execute genuine weighted startup initialisation ONCE per process/ViewModel lifecycle
        startupInitializationController.startIfNeeded(
            listOf(
                com.example.ui.loader.StartupTask(
                    id = "preferences",
                    statusLabel = "Loading preferences...",
                    weight = 0.25f,
                    isEssential = true
                ) {
                    preferencesManager.getInitialPreferences()
                },
                com.example.ui.loader.StartupTask(
                    id = "database",
                    statusLabel = "Initialising CTECS...",
                    weight = 0.25f,
                    isEssential = true
                ) {
                    db.openHelper.readableDatabase
                },
                com.example.ui.loader.StartupTask(
                    id = "configuration",
                    statusLabel = "Restoring application...",
                    weight = 0.25f,
                    isEssential = true
                ) {
                    _projectProfile.value
                    overlayConfig.value
                    cameraSettings.value
                },
                com.example.ui.loader.StartupTask(
                    id = "services",
                    statusLabel = "Preparing services...",
                    weight = 0.25f,
                    isEssential = true
                ) {
                    CtecsNavigationTelemetryHub.updateTelemetry(
                        projectProfile = _projectProfile.value,
                        screensaverSettings = _homeScreensaverSettings.value,
                        navigationNotificationSettings = _navigationNotificationSettings.value,
                        widgetSettings = _widgetSettings.value,
                        kmStoneAlertSettings = _kmStoneAlertSettings.value
                    )
                }
            )
        )
    }

    fun checkForAppUpdate(isManual: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val currentChannel = com.example.update.AppReleaseChannel.current()
            val result = appUpdateManager.checkForUpdate(currentChannel, isManual = isManual)
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
        locationTracker.setHighFrequencyMode(tab == AppTab.CAMERA || tab == AppTab.SURVEY)
    }

    fun toggleFlash() {
        val next = !_isFlashOn.value
        _isFlashOn.value = next
        preferencesManager.launchSafeEdit {
            preferencesManager.updateIsFlashOn(next)
        }
    }

    fun toggleCameraFacing() {
        val next = !_isFrontCamera.value
        _isFrontCamera.value = next
        preferencesManager.launchSafeEdit {
            preferencesManager.updateIsFrontCamera(next)
        }
    }

    fun openManualChainageDialog() {
        _showManualChainageDialog.value = true
    }

    fun closeManualChainageDialog() {
        _showManualChainageDialog.value = false
    }

    fun setManualChainage(
        input: String,
        mode: com.example.model.ManualChainageMode = _liveManualCalibration.value.mode,
        closeDialog: Boolean = true
    ) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            clearManualChainage()
            return
        }
        val parsed = Chainage.parse(trimmed)
        if (parsed == null) {
            viewModelScope.launch {
                _uiMessages.emit(UiMessage.Error("Invalid chainage format. Use e.g. 174+250, 198+880, or 200000"))
                showToast("Invalid chainage format (e.g. 174+250 or 198+880)", ToastType.ERROR)
            }
            return
        }
        val formatted = parsed.format()
        val nowMs = System.currentTimeMillis()
        val newCal = progressionTracker.establishManualCalibration(
            enteredChainage = parsed,
            mode = mode,
            currentAutoResult = _autoMatchStateFlow.value,
            locationState = locationTracker.locationState.value,
            routeId = activeRouteData.value?.id,
            nowMs = nowMs
        )
        _manualChainage.value = parsed
        _manualChainageText.value = formatted
        _liveManualCalibration.value = newCal
        _manualCalibrationCommand.value = newCal
        lastPersistedManualMetres = newCal.currentChainageMetres
        lastPersistedManualCalibrated = newCal.isDynamicAnchorCalibrated
        if (closeDialog) {
            _showManualChainageDialog.value = false
        }
        val modeDetail = if (mode == com.example.model.ManualChainageMode.DYNAMIC_CORRECTION) {
            val off = newCal.formattedOffsetBadge
            if (newCal.isDynamicAnchorCalibrated && off != null) {
                "Dynamic calibrated ($off)"
            } else {
                "Dynamic mode (awaiting reliable GPS)"
            }
        } else {
            "Continue Stable Chainage"
        }
        preferencesManager.launchSafeEdit {
            preferencesManager.updateManualChainageCalibration(newCal)
        }
        viewModelScope.launch {
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
                    summary = "Manual Chainage: $formatted (${mode.shortLabel})",
                    details = modeDetail
                )
            )
            _uiMessages.emit(UiMessage.Success("Manual chainage ($modeDetail): $formatted"))
            showToast("$formatted • ${mode.shortLabel}", ToastType.SUCCESS)
        }
    }

    /**
     * Switches cleanly between [com.example.model.ManualChainageMode.CONTINUE_STABLE] and
     * [com.example.model.ManualChainageMode.DYNAMIC_CORRECTION] while strictly preserving
     * the current displayed chainage without unexpected jumps.
     */
    fun setManualChainageMode(targetMode: com.example.model.ManualChainageMode) {
        val currentLive = _liveManualCalibration.value
        val nowMs = System.currentTimeMillis()
        if (!currentLive.isActive) {
            val updatedInactive = currentLive.copy(mode = targetMode, lastUpdatedTimestampMs = nowMs)
            _liveManualCalibration.value = updatedInactive
            _manualCalibrationCommand.value = updatedInactive
            preferencesManager.launchSafeEdit {
                preferencesManager.updateManualChainageCalibration(updatedInactive)
            }
            return
        }
        if (currentLive.mode == targetMode) return

        val switched = progressionTracker.switchManualMode(
            currentCalibration = currentLive,
            targetMode = targetMode,
            currentAutoResult = _autoMatchStateFlow.value,
            locationState = locationTracker.locationState.value,
            routeId = activeRouteData.value?.id,
            nowMs = nowMs
        )
        _liveManualCalibration.value = switched
        _manualCalibrationCommand.value = switched
        switched.effectiveCurrentMetres?.let { metres ->
            val ch = Chainage(metres)
            _manualChainage.value = ch
            _manualChainageText.value = ch.format()
        }
        lastPersistedManualMetres = switched.currentChainageMetres
        lastPersistedManualCalibrated = switched.isDynamicAnchorCalibrated
        preferencesManager.launchSafeEdit {
            preferencesManager.updateManualChainageCalibration(switched)
        }
        val msg = if (targetMode == com.example.model.ManualChainageMode.DYNAMIC_CORRECTION) {
            val off = switched.formattedOffsetBadge
            if (switched.isDynamicAnchorCalibrated && off != null) {
                "Dynamic Correction active ($off)"
            } else {
                "Dynamic Correction active (waiting GPS lock)"
            }
        } else {
            "Continue Stable Chainage active"
        }
        showToast(msg, ToastType.INFO)
    }

    /**
     * Explicit user-controlled recalibration at current GPS position (preserves active mode or uses target mode).
     */
    fun recalibrateManualChainage(
        input: String? = null,
        mode: com.example.model.ManualChainageMode = _liveManualCalibration.value.mode
    ) {
        val effectiveInput = input?.trim()?.takeIf { it.isNotBlank() }
            ?: _liveManualCalibration.value.effectiveDisplayChainage
            ?: liveMatchResult.value.chainage?.format()
            ?: return
        setManualChainage(input = effectiveInput, mode = mode, closeDialog = false)
    }

    fun clearManualChainage() {
        val preservedMode = _liveManualCalibration.value.mode
        val cleared = com.example.model.ManualChainageCalibrationState.inactive(preservedMode)
        _manualChainage.value = null
        _manualChainageText.value = null
        _liveManualCalibration.value = cleared
        _manualCalibrationCommand.value = cleared
        lastPersistedManualMetres = null
        lastPersistedManualCalibrated = false
        progressionTracker.resetManualProgressionSession()
        _showManualChainageDialog.value = false
        preferencesManager.launchSafeEdit {
            preferencesManager.updateManualChainageCalibration(cleared)
        }
        viewModelScope.launch {
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
                setManualChainage(displayStr, _liveManualCalibration.value.mode, closeDialog = false)
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
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "route_corrections_enabled",
            sectionKey = "chainage",
            valueSignature = enabled.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.setRouteCorrectionsEnabled(enabled)
            }
        }
        viewModelScope.launch {
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
        val nowMs = System.currentTimeMillis()
        val writeVersion: Long
        val updatedPrefs: com.example.model.SmartDirectionPreferences
        synchronized(smartDirectionMutationLock) {
            updatedPrefs = _smartDirectionPreferences.value.copy(
                controlMode = com.example.model.DirectionControlMode.MANUAL_OVERRIDE,
                manualOverrideSide = side.label,
                manualSelectedAtMs = nowMs
            )
            smartDirectionWriteVersion += 1L
            writeVersion = smartDirectionWriteVersion
            _smartDirectionPreferences.value = updatedPrefs
        }
        val normalizedLabel = updatedPrefs.normalizedManualSide ?: side.label
        manualDirectionInactivityJob?.cancel()
        manualDirectionInactivityJob = null
        CtecsNavigationTelemetryHub.sharedDirectionManager.onManualDirectionSelected(normalizedLabel, nowMs)
        progressionTracker.setManualSide(normalizedLabel)
        CtecsNavigationTelemetryHub.updateTelemetry(directionPreferences = updatedPrefs)
        _projectProfile.update { it.copy(roadSide = side) }

        // Synchronously publish authoritative locked manual direction state so Camera preview,
        // immediate photo/video captures, Home, Screensaver, Survey, and Notifications stay 100% consistent.
        val loc = locationTracker.locationState.value
        val currentMatch = _autoMatchStateFlow.value
        val route = activeRouteData.value
        val dirState = evaluateAndPublishSmartDirection(loc, currentMatch, route)
        _autoMatchStateFlow.value = currentMatch.copy(
            detectedSide = dirState.confirmedSide ?: normalizedLabel,
            smartDirectionState = dirState
        )

        // Sync active recording survey road side when a binary/BHS manual option is chosen
        val activeSurvey = surveyManager.activeSession.value
        if (activeSurvey != null && activeSurvey.status == com.example.survey.model.SurveyStatus.RECORDING) {
            val surveySide = when (side) {
                RoadSide.LHS -> com.example.survey.model.RoadSide.LHS
                RoadSide.RHS -> com.example.survey.model.RoadSide.RHS
                RoadSide.BOTH -> com.example.survey.model.RoadSide.BHS
                else -> null
            }
            if (surveySide != null && activeSurvey.roadSide != surveySide) {
                viewModelScope.launch { surveyManager.updateRoadSide(surveySide) }
            }
        }

        preferencesManager.launchSafeEdit {
            try {
                preferencesManager.updateSmartDirectionPreferences(updatedPrefs)
                preferencesManager.editProjectProfile { it.copy(roadSide = side) }
            } finally {
                synchronized(smartDirectionMutationLock) {
                    if (writeVersion > smartDirectionFlushedVersion) {
                        smartDirectionFlushedVersion = writeVersion
                    }
                }
            }
        }
    }

    fun resetSideToAuto() {
        val writeVersion: Long
        val updatedPrefs: com.example.model.SmartDirectionPreferences
        synchronized(smartDirectionMutationLock) {
            updatedPrefs = _smartDirectionPreferences.value.copy(
                controlMode = com.example.model.DirectionControlMode.AUTO,
                manualOverrideSide = null,
                manualSelectedAtMs = 0L
            )
            smartDirectionWriteVersion += 1L
            writeVersion = smartDirectionWriteVersion
            _smartDirectionPreferences.value = updatedPrefs
        }
        manualDirectionInactivityJob?.cancel()
        manualDirectionInactivityJob = null
        CtecsNavigationTelemetryHub.sharedDirectionManager.onAutoModeSelected()
        progressionTracker.clearManualSide()
        CtecsNavigationTelemetryHub.updateTelemetry(directionPreferences = updatedPrefs)

        // Synchronously hand control back to automatic GPS + KMZ/KML road-alignment detection
        val loc = locationTracker.locationState.value
        val currentMatch = _autoMatchStateFlow.value
        val route = activeRouteData.value
        val dirState = evaluateAndPublishSmartDirection(loc, currentMatch, route)
        _autoMatchStateFlow.value = currentMatch.copy(
            detectedSide = dirState.confirmedSide ?: "Unknown",
            smartDirectionState = dirState
        )

        preferencesManager.launchSafeEdit {
            try {
                preferencesManager.updateSmartDirectionPreferences(updatedPrefs)
            } finally {
                synchronized(smartDirectionMutationLock) {
                    if (writeVersion > smartDirectionFlushedVersion) {
                        smartDirectionFlushedVersion = writeVersion
                    }
                }
            }
        }
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info("Direction set to Auto (KMZ/KML Offset)"))
        }
    }

    fun updateAlignmentDirectionConvention(convention: com.example.model.AlignmentDirectionConvention) {
        val updatedPrefs = _smartDirectionPreferences.value.copy(alignmentConvention = convention)
        _smartDirectionPreferences.value = updatedPrefs
        CtecsNavigationTelemetryHub.updateTelemetry(directionPreferences = updatedPrefs)
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "alignment_direction_convention",
            sectionKey = "chainage",
            valueSignature = updatedPrefs.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateSmartDirectionPreferences(updatedPrefs)
            }
        }
    }

    fun setVoiceDirectionAnnouncementsEnabled(enabled: Boolean) {
        val updatedPrefs = _smartDirectionPreferences.value.copy(voiceDirectionAnnouncementsEnabled = enabled)
        _smartDirectionPreferences.value = updatedPrefs
        CtecsNavigationTelemetryHub.updateTelemetry(directionPreferences = updatedPrefs)
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "voice_direction_announcements",
            sectionKey = "chainage",
            valueSignature = updatedPrefs.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateSmartDirectionPreferences(updatedPrefs)
            }
        }
    }

    fun updateLane(lane: String) {
        val clean = lane.trim()
        _projectProfile.update { it.copy(lane = clean) }
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = _projectProfile.value.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.editProjectProfile { it.copy(lane = clean) }
            }
        }
    }

    fun updateActivity(activity: String) {
        val clean = activity.trim()
        _projectProfile.update { it.copy(activity = clean) }
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = _projectProfile.value.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.editProjectProfile { it.copy(activity = clean) }
            }
        }
    }

    fun updateSubActivity(subActivity: String) {
        val clean = subActivity.trim()
        _projectProfile.update { it.copy(subActivity = clean) }
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = _projectProfile.value.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.editProjectProfile { it.copy(subActivity = clean) }
            }
        }
    }

    fun updateCustomDescription(description: String) {
        val clean = description.trim()
        _projectProfile.update { it.copy(customDescription = clean) }
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = _projectProfile.value.hashCode(),
            debounceMs = com.example.settings.SettingsAutoSaveCoordinator.CONTINUOUS_INPUT_DEBOUNCE_MS
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.editProjectProfile { it.copy(customDescription = clean) }
            }
        }
    }

    fun updateWorkType(workType: String) {
        val clean = workType.trim()
        _projectProfile.update { it.copy(workType = clean) }
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = _projectProfile.value.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.editProjectProfile { it.copy(workType = clean) }
            }
        }
    }

    fun saveProjectProfile(profile: ProjectProfile) {
        _projectProfile.value = profile
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = profile.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateProjectProfile(profile)
                syncProjectToRepository(profile)
            }
        }
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Success("Project profile saved"))
        }
    }

    fun saveProjectProfileSilent(profile: ProjectProfile) {
        _projectProfile.value = profile
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "project_profile",
            sectionKey = "project",
            valueSignature = profile.hashCode(),
            debounceMs = com.example.settings.SettingsAutoSaveCoordinator.CONTINUOUS_INPUT_DEBOUNCE_MS
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateProjectProfile(profile)
                syncProjectToRepository(profile)
            }
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
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "defined_projects",
            sectionKey = "project",
            valueSignature = projects.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateDefinedProjects(projects)
            }
        }
    }

    fun addDefinedProject(project: DefinedProject) {
        preferencesManager.launchSafeEdit {
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
        preferencesManager.launchSafeEdit {
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
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "overlay_config",
            sectionKey = "timestamp",
            valueSignature = config.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateOverlayConfig(config)
            }
        }
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Success("Overlay configuration saved"))
        }
    }

    fun saveOverlayConfigSilent(config: OverlayConfig) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "overlay_config",
            sectionKey = "timestamp",
            valueSignature = config.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateOverlayConfig(config)
            }
        }
    }

    fun resetMiniMapDefaults() {
        preferencesManager.launchSafeEdit {
            preferencesManager.editOverlayConfig { current ->
                current.copy(
                    showMapInset = true,
                    photoMiniMap = com.example.model.MiniMapStyleConfig(),
                    videoMiniMap = com.example.model.MiniMapStyleConfig()
                )
            }
            preferencesManager.updateMiniMapViewDistance(
                com.example.ui.map.MiniMapViewDistancePreference()
            )
        }
        recenterHomeMiniMap()
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info("Mini Map settings reset to default"))
        }
    }

    fun saveCameraSettings(settings: com.example.model.CameraSettings) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "camera_settings",
            sectionKey = "camera",
            valueSignature = settings.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateCameraSettings(settings)
            }
        }
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Success("Camera settings saved"))
        }
    }

    fun saveCameraSettingsSilent(settings: com.example.model.CameraSettings) {
        settingsAutoSaveCoordinator.saveSetting(
            settingKey = "camera_settings",
            sectionKey = "camera",
            valueSignature = settings.hashCode()
        ) {
            preferencesManager.executeSafeEdit {
                preferencesManager.updateCameraSettings(settings)
            }
        }
    }

    fun toggleRecordAudio() {
        viewModelScope.launch {
            val updated = preferencesManager.editCameraSettings { current ->
                current.copy(recordAudio = !current.recordAudio)
            }
            val state = if (updated.recordAudio) "enabled" else "disabled"
            showToast("Audio recording $state", ToastType.SETTINGS_SAVED)
        }
    }

    fun setCaptureMode(mode: com.example.model.CaptureMode) {
        _captureMode.value = mode
        preferencesManager.launchSafeEdit {
            preferencesManager.updateCaptureMode(mode)
        }
    }

    fun setCameraMode(mode: CameraMode) {
        preferencesManager.launchSafeEdit {
            preferencesManager.editCameraSettings { current ->
                current.copy(cameraMode = mode)
            }
            preferencesManager.editOverlayConfig { current ->
                when (mode) {
                    CameraMode.HIGHWAY -> current.copy(
                        showTimestamp = true,
                        showCoordinates = true,
                        showChainage = true,
                        showMapInset = true,
                        showProjectInfo = true,
                        showCompass = true,
                        showAltitude = true,
                        showAccuracy = true
                    )
                    CameraMode.NORMAL -> current.copy(
                        showTimestamp = true,
                        showCoordinates = false,
                        showChainage = false,
                        showMapInset = false,
                        showProjectInfo = false,
                        showCompass = false,
                        showAltitude = false,
                        showAccuracy = false
                    )
                    CameraMode.TIMESTAMP -> current.copy(
                        showTimestamp = true,
                        showCoordinates = false,
                        showChainage = false,
                        showMapInset = false,
                        showProjectInfo = false,
                        showCompass = false,
                        showAltitude = false,
                        showAccuracy = false
                    )
                    CameraMode.LOCATION -> current.copy(
                        showTimestamp = true,
                        showCoordinates = true,
                        showChainage = false,
                        showMapInset = true,
                        showProjectInfo = false,
                        showCompass = true,
                        showAltitude = true,
                        showAccuracy = true
                    )
                    CameraMode.CUSTOM -> current
                }
            }
        }
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info("Mode: ${mode.label}"))
        }
    }

    fun setZoomRatio(ratio: Float) {
        if (!ratio.isFinite()) return
        _currentZoomRatio.value = ratio.coerceIn(0.25f, 200.0f)
    }

    fun setExposureCompensation(ev: Int) {
        val clamped = ev.coerceIn(-4, 4)
        _exposureCompensation.value = clamped
        preferencesManager.launchSafeEdit {
            preferencesManager.editCameraSettings { current ->
                current.copy(exposureCompensationStep = clamped)
            }
        }
    }

    fun setCameraAutoBrightness(enabled: Boolean) {
        if (enabled) {
            _exposureCompensation.value = 0
            _captureExposureNormalized.value = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL
            _captureExposureEvStops.value = 0f
        }
        preferencesManager.launchSafeEdit {
            preferencesManager.editCameraSettings { current ->
                current.copy(
                    autoBrightnessEnabled = enabled,
                    exposureCompensationStep = if (enabled) 0 else current.exposureCompensationStep
                )
            }
        }
    }

    fun setCameraManualBrightness(normalizedLevel: Float) {
        val clamped = CameraBrightnessEngine.clampNormalizedExposure(normalizedLevel)
        _captureExposureNormalized.value = clamped
        preferencesManager.launchSafeEdit {
            preferencesManager.editCameraSettings { current ->
                current.copy(
                    autoBrightnessEnabled = false,
                    manualPreviewBrightness = clamped
                )
            }
        }
    }

    fun updateCaptureExposureTelemetry(
        normalizedLevel: Float,
        evStops: Float,
        hardwareEvSupported: Boolean
    ) {
        _captureExposureNormalized.value = CameraBrightnessEngine.clampNormalizedExposure(normalizedLevel)
        _captureExposureEvStops.value = evStops
        _captureExposureHardwareSupported.value = hardwareEvSupported
    }

    fun cycleTimer() {
        viewModelScope.launch {
            val updated = preferencesManager.editCameraSettings { current ->
                val next = when (current.captureTimer) {
                    com.example.model.CaptureTimer.OFF -> com.example.model.CaptureTimer.SEC_3
                    com.example.model.CaptureTimer.SEC_3 -> com.example.model.CaptureTimer.SEC_5
                    com.example.model.CaptureTimer.SEC_5 -> com.example.model.CaptureTimer.SEC_10
                    com.example.model.CaptureTimer.SEC_10 -> com.example.model.CaptureTimer.OFF
                }
                current.copy(captureTimer = next)
            }
            _uiMessages.emit(UiMessage.Info("Timer: ${updated.captureTimer.label}"))
        }
    }

    fun cycleGrid() {
        viewModelScope.launch {
            val updated = preferencesManager.editCameraSettings { current ->
                val next = when (current.gridMode) {
                    com.example.model.CameraGridMode.OFF -> com.example.model.CameraGridMode.GRID_3X3
                    com.example.model.CameraGridMode.GRID_3X3 -> com.example.model.CameraGridMode.GRID_4X4
                    com.example.model.CameraGridMode.GRID_4X4 -> com.example.model.CameraGridMode.OFF
                }
                current.copy(gridMode = next)
            }
            _uiMessages.emit(UiMessage.Info("Grid: ${updated.gridMode.label}"))
        }
    }

    fun cycleCameraCropMode() {
        viewModelScope.launch {
            val updated = preferencesManager.editCameraSettings { current ->
                current.copy(cropMode = current.cropMode.next())
            }
            showToast("Frame: ${updated.cropMode.label}", ToastType.INFO)
        }
    }

    fun setCameraCropMode(mode: com.example.model.CameraCropMode) {
        viewModelScope.launch {
            val updated = preferencesManager.editCameraSettings { current ->
                current.copy(cropMode = mode)
            }
            showToast("Frame: ${updated.cropMode.label}", ToastType.INFO)
        }
    }

    private val videoSnapshotsTimeline = mutableListOf<CaptureSnapshot>()
    private val videoChainageHistory = mutableListOf<com.example.model.VideoChainageHistorySample>()
    private var videoTickerJob: kotlinx.coroutines.Job? = null
    private var videoChainageCollectorJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var videoRecordingActiveElapsedMs: Long = 0L
    @Volatile
    private var videoSegmentStartWallMs: Long = 0L

    fun getVideoChainageHistorySnapshot(): List<com.example.model.VideoChainageHistorySample> =
        synchronized(videoChainageHistory) { videoChainageHistory.toList() }

    fun getVideoSnapshotsTimelineCopy(): List<CaptureSnapshot> =
        synchronized(videoSnapshotsTimeline) { videoSnapshotsTimeline.toList() }

    private fun computeCurrentVideoElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        return if (_isRecording.value && !_isRecordingPaused.value && videoSegmentStartWallMs > 0L) {
            videoRecordingActiveElapsedMs + (nowMs - videoSegmentStartWallMs).coerceAtLeast(0L)
        } else {
            videoRecordingActiveElapsedMs
        }
    }

    private fun recordVideoTimelineSample(nowMs: Long = System.currentTimeMillis()) {
        val elapsedMs = computeCurrentVideoElapsedMs(nowMs)
        val snap = createCurrentSnapshot().copy(
            timestamp = nowMs,
            videoElapsedMs = elapsedMs
        )
        synchronized(videoSnapshotsTimeline) {
            videoSnapshotsTimeline.add(snap)
        }
        synchronized(videoChainageHistory) {
            val match = snap.matchResult
            videoChainageHistory.add(
                com.example.model.VideoChainageHistorySample(
                    wallTimestampMs = nowMs,
                    recordingElapsedMs = elapsedMs,
                    chainageMetres = match.chainage?.metres,
                    chainageLabel = snap.displayChainage,
                    source = match.source,
                    manualMode = match.manualChainageMode,
                    travelDirection = match.travelDirection,
                    roadSide = match.detectedSide,
                    latitude = snap.location?.latitude,
                    longitude = snap.location?.longitude,
                    gpsAccuracy = snap.gpsAccuracy,
                    isGpsRetained = snap.isGpsRetained || match.isUncertain
                )
            )
        }
    }

    fun createCurrentSnapshot(forcedRotation: Int? = null): CaptureSnapshot {
        val nowMs = System.currentTimeMillis()
        val rot = forcedRotation ?: rotationDegrees.value.toInt()
        val currentOverlayCfg = overlayConfig.value
        val currentCamCfg = cameraSettings.value
        val shouldIncludeLoc = currentOverlayCfg.showLocationName ||
            (currentOverlayCfg.showWeatherInfo && currentOverlayCfg.weatherShowLocationName)
        val locName = if (shouldIncludeLoc) {
            currentLocationName.value
                .ifBlank { currentOverlayCfg.lastDetectedLocationName }
                .trim()
        } else ""
        val liveLoc = locationState.value.point
        val liveBearing = locationState.value.bearing
        val liveSpeed = locationState.value.speed
        if (liveLoc != null) {
            _lastValidLocation.value = liveLoc
        }
        if (liveBearing != null) {
            lastValidGpsBearing = liveBearing
        }
        val retainedLoc = liveLoc ?: _lastValidLocation.value
        val retainedBearing = mapHeadingState.value.headingDegrees ?: liveBearing ?: lastValidGpsBearing
        // Non-blocking synchronous read of capture-time weather observation (never delays shutter)
        val (weatherObs, isWeatherFresh) = weatherRepository.getObservationForCapture(
            captureTimeMs = nowMs,
            omitIfStale = currentOverlayCfg.weatherOmitIfStale
        )
        val latestDir = smartDirectionState.value
        val synchronizedMatch = cameraMatchResult.value.let { current ->
            if (current.smartDirectionState == latestDir) {
                current
            } else {
                current.copy(
                    detectedSide = latestDir.confirmedSide ?: current.detectedSide,
                    smartDirectionState = latestDir
                )
            }
        }
        return CaptureSnapshot(
            timestamp = nowMs,
            location = liveLoc,
            locationName = locName,
            gpsAccuracy = locationState.value.accuracy,
            bearing = liveBearing,
            matchResult = synchronizedMatch,
            projectProfile = projectProfile.value,
            overlayConfig = currentOverlayCfg,
            activeRoute = activeRouteData.value,
            deviceRotationDegrees = rot,
            compassHeading = compassState.value.displayHeading,
            compassAzimuth = compassState.value.azimuthDegrees,
            speedMps = liveSpeed,
            filenameFormatPreset = currentCamCfg.filenameFormatPreset,
            customFilenamePrefix = currentCamCfg.customFilenamePrefix,
            isDarkTheme = themeMode.value != "light",
            lastValidLocation = retainedLoc,
            lastValidBearing = retainedBearing,
            isGpsRetained = (liveLoc == null && retainedLoc != null),
            isVideoCapture = (_captureMode.value == com.example.model.CaptureMode.VIDEO || _isRecording.value),
            weatherObservation = weatherObs,
            weatherIsFreshAtCapture = isWeatherFresh,
            is24HourFormat = currentCamCfg.is24HourFormat,
            coordinateFormat = currentCamCfg.coordinateFormat,
            photoJpegQuality = 98,
            preserveExif = currentCamCfg.preserveExif,
            videoQualityPreset = com.example.model.OutputQualityPreset.MAXIMUM,
            videoOutputSizeOptimization = false,
            targetVideoFps = currentCamCfg.videoFps,
            cropMode = currentCamCfg.cropMode,
            videoElapsedMs = if (_isRecording.value) computeCurrentVideoElapsedMs(nowMs) else null
        )
    }

    fun onVideoRecordingStarted(nowMs: Long = System.currentTimeMillis()) {
        _isRecording.value = true
        _isRecordingPaused.value = false
        _recordingDurationSeconds.value = 0
        videoRecordingActiveElapsedMs = 0L
        videoSegmentStartWallMs = nowMs
        CtecsNavigationTelemetryHub.sharedDirectionManager.onVideoRecordingStateChanged(true)
        synchronized(videoSnapshotsTimeline) {
            videoSnapshotsTimeline.clear()
        }
        synchronized(videoChainageHistory) {
            videoChainageHistory.clear()
        }
        recordVideoTimelineSample(nowMs)
        videoTickerJob?.cancel()
        videoTickerJob = viewModelScope.launch {
            while (_isRecording.value) {
                kotlinx.coroutines.delay(1000L)
                if (_isRecording.value && !_isRecordingPaused.value) {
                    _recordingDurationSeconds.value += 1
                    recordVideoTimelineSample()
                }
            }
        }
        videoChainageCollectorJob?.cancel()
        videoChainageCollectorJob = viewModelScope.launch {
            var lastRecordedChainage: Long? = cameraMatchResult.value.chainage?.metres
            var lastRecordedMs = nowMs
            cameraMatchResult.collect { match ->
                if (_isRecording.value && !_isRecordingPaused.value) {
                    val m = match.chainage?.metres
                    val t = System.currentTimeMillis()
                    if (m != lastRecordedChainage && (t - lastRecordedMs) >= 300L) {
                        lastRecordedChainage = m
                        lastRecordedMs = t
                        recordVideoTimelineSample(t)
                    }
                }
            }
        }
    }

    fun onVideoRecordingTick(seconds: Int) {
        _recordingDurationSeconds.value = seconds
    }

    fun onVideoRecordingPaused(paused: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val wasPaused = _isRecordingPaused.value
        if (paused == wasPaused) return
        if (paused) {
            if (_isRecording.value && videoSegmentStartWallMs > 0L) {
                videoRecordingActiveElapsedMs += (nowMs - videoSegmentStartWallMs).coerceAtLeast(0L)
                recordVideoTimelineSample(nowMs)
            }
            _isRecordingPaused.value = true
        } else {
            _isRecordingPaused.value = false
            if (_isRecording.value) {
                videoSegmentStartWallMs = nowMs
                recordVideoTimelineSample(nowMs)
            }
        }
    }

    fun onVideoRecordingStopped(nowMs: Long = System.currentTimeMillis()) {
        if (_isRecording.value && !_isRecordingPaused.value && videoSegmentStartWallMs > 0L) {
            videoRecordingActiveElapsedMs += (nowMs - videoSegmentStartWallMs).coerceAtLeast(0L)
            recordVideoTimelineSample(nowMs)
        }
        _isRecording.value = false
        _isRecordingPaused.value = false
        _recordingDurationSeconds.value = 0
        videoTickerJob?.cancel()
        videoChainageCollectorJob?.cancel()
        CtecsNavigationTelemetryHub.sharedDirectionManager.onVideoRecordingStateChanged(false)
    }

    fun processRecordedVideo(
        context: android.content.Context,
        rawVideoFile: java.io.File,
        forcedRotation: Int? = null,
        hasRealtimeOverlay: Boolean = true
    ) {
        val appContext = context.applicationContext
        val timelineCopy = synchronized(videoSnapshotsTimeline) { videoSnapshotsTimeline.toList() }
        val historyCopy = synchronized(videoChainageHistory) { videoChainageHistory.toList() }
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
            subActivity = defaultSnapshot.projectProfile.subActivity,
            isVideo = true
        )

        val localDir = java.io.File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir,
            "HighwayCamera"
        ).apply { mkdirs() }
        val finalFile = java.io.File(localDir, fileName)

        val request = com.example.video.VideoExportRequest(
            sourceFile = rawVideoFile,
            destinationFile = finalFile,
            fileName = fileName,
            cropMode = defaultSnapshot.cropMode,
            hasRealtimeOverlay = hasRealtimeOverlay,
            requireOverlayBurnIn = !hasRealtimeOverlay,
            snapshotsTimeline = timelineCopy,
            defaultSnapshot = defaultSnapshot,
            deleteSourceAfterSuccess = true,
            publishToMediaStore = true
        )

        val enqueued = com.example.video.VideoExportManager.enqueueExport(
            context = appContext,
            request = request
        ) { success, finalState ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (success && finalFile.exists() && finalFile.length() > 0L) {
                        try {
                            val startSample = historyCopy.firstOrNull()
                            val endSample = historyCopy.lastOrNull()
                            val chainageRangeSummary = if (startSample != null && endSample != null &&
                                startSample.chainageLabel != endSample.chainageLabel
                            ) {
                                "Video Chainage: ${startSample.chainageLabel} → ${endSample.chainageLabel} (${historyCopy.size} pts)"
                            } else if (startSample != null) {
                                "Video Chainage: ${startSample.chainageLabel} (${historyCopy.size} pts)"
                            } else null
                            val calDesc = _liveManualCalibration.value.formatExifSummary()
                            val videoDesc = listOfNotNull(
                                defaultSnapshot.projectProfile.customDescription.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) },
                                chainageRangeSummary,
                                calDesc
                            ).joinToString(" | ")
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
                                subActivity = defaultSnapshot.projectProfile.subActivity,
                                customDescription = videoDesc,
                                inspectorName = defaultSnapshot.projectProfile.inspectorName,
                                formattedOverlayText = defaultSnapshot.projectProfile.buildHighwayText(defaultSnapshot.displayChainage)
                            )
                            photoRepository.insertRecord(record)
                        } catch (ignored: Exception) {}

                        val secText = String.format(java.util.Locale.US, "%.1fs", (finalState.elapsedMs / 1000.0).coerceAtLeast(0.1))
                        withContext(Dispatchers.Main) {
                            showToast("Video saved (${finalState.method.displayLabel}, $secText)", ToastType.SUCCESS)
                        }
                    } else {
                        try {
                            if (rawVideoFile.exists() && rawVideoFile.absolutePath != finalFile.absolutePath) {
                                rawVideoFile.delete()
                            }
                        } catch (ignored: Exception) {}
                        withContext(Dispatchers.Main) {
                            if (finalState.status == com.example.video.VideoExportStatus.CANCELLED) {
                                showToast("Video export cancelled.", ToastType.INFO)
                            } else {
                                showError(finalState.errorMessage ?: "Failed to save video: valid camera footage was not captured.")
                            }
                        }
                    }
                } finally {
                    _isExportingVideo.value = false
                }
            }
        }

        if (enqueued) {
            _isExportingVideo.value = true
        } else {
            showToast("Another video export is already in progress.", ToastType.INFO)
        }
    }

    /**
     * Exports an existing Gallery video losslessly to shared Movies/HighwayCamera using
     * Direct Stream Copy (zero re-encoding) without ever modifying or deleting the original file.
     */
    fun exportGalleryVideo(
        context: android.content.Context,
        videoRecord: com.example.data.PhotoRecordEntity
    ) {
        val appContext = context.applicationContext
        val sourceFile = java.io.File(videoRecord.filePath)
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            showError("Original video file is missing or unavailable.")
            return
        }

        val exportStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val baseName = sourceFile.nameWithoutExtension.ifBlank { "CTECS_Video" }
        val exportFileName = "${baseName}_export_${exportStamp}.mp4"
        val exportDir = java.io.File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir,
            "HighwayCamera/Exports"
        ).apply { mkdirs() }
        val destFile = java.io.File(exportDir, exportFileName)

        val request = com.example.video.VideoExportRequest(
            sourceFile = sourceFile,
            destinationFile = destFile,
            fileName = exportFileName,
            cropMode = com.example.model.CameraCropMode.FULL,
            hasRealtimeOverlay = true,
            requireOverlayBurnIn = false,
            deleteSourceAfterSuccess = false, // NEVER delete or modify gallery original!
            publishToMediaStore = true
        )

        val enqueued = com.example.video.VideoExportManager.enqueueExport(
            context = appContext,
            request = request
        ) { success, finalState ->
            viewModelScope.launch(Dispatchers.Main) {
                _isExportingVideo.value = false
                if (success) {
                    val secText = String.format(java.util.Locale.US, "%.1fs", (finalState.elapsedMs / 1000.0).coerceAtLeast(0.1))
                    showToast("Exported to Movies/HighwayCamera (${finalState.method.displayLabel}, $secText)", ToastType.SUCCESS)
                } else if (finalState.status == com.example.video.VideoExportStatus.CANCELLED) {
                    showToast("Video export cancelled.", ToastType.INFO)
                } else {
                    showError(finalState.errorMessage ?: "Video export failed.")
                }
            }
        }

        if (enqueued) {
            _isExportingVideo.value = true
            showToast("Exporting video in background...", ToastType.INFO)
        } else {
            showToast("A video export is already in progress.", ToastType.INFO)
        }
    }

    fun cancelVideoExport(context: android.content.Context) {
        com.example.video.VideoExportManager.cancelActiveExport(context.applicationContext)
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            preferencesManager.resetAllSettings()
            _projectProfile.value = ProjectProfile()
            _homeScreensaverSettings.value = HomeScreensaverSettings()
            _navigationNotificationSettings.value = com.example.model.NavigationNotificationSettings()
            _widgetSettings.value = com.example.model.CtecsWidgetSettings()
            _kmStoneAlertSettings.value = com.example.model.KmStoneAlertSettings()
            _manualChainage.value = null
            _manualChainageText.value = null
            _liveManualCalibration.value = com.example.model.ManualChainageCalibrationState()
            _manualCalibrationCommand.value = com.example.model.ManualChainageCalibrationState()
            lastPersistedManualMetres = null
            lastPersistedManualCalibrated = false
            progressionTracker.resetManualProgressionSession()
            selectedAlignmentId.value = null
            _isFlashOn.value = false
            _isFrontCamera.value = false
            _captureMode.value = com.example.model.CaptureMode.PHOTO
            _currentZoomRatio.value = 1.0f
            _exposureCompensation.value = 0
            progressionTracker.clearManualSide()
            CtecsNavigationTelemetryHub.updateTelemetry(
                projectProfile = ProjectProfile(),
                screensaverSettings = HomeScreensaverSettings(),
                navigationNotificationSettings = com.example.model.NavigationNotificationSettings(),
                widgetSettings = com.example.model.CtecsWidgetSettings(),
                kmStoneAlertSettings = com.example.model.KmStoneAlertSettings(),
                is24HourFormat = false
            )
            com.example.widget.CtecsWidgetUpdater.cacheWidgetSettings(
                getApplication(),
                com.example.model.CtecsWidgetSettings()
            )
            com.example.widget.CtecsWidgetUpdater.updateAllWidgets(getApplication())
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
                preferencesManager.editOverlayConfig { current ->
                    current.copy(
                        showLogo = true,
                        logoPreset = "custom",
                        logoUri = savedPath
                    )
                }
                _uiMessages.emit(UiMessage.Success("Custom logo image saved successfully"))
            } else {
                _uiMessages.emit(UiMessage.Error("Failed to import custom logo image"))
            }
        }
    }

    fun removeCustomLogo() {
        viewModelScope.launch {
            preferencesManager.editOverlayConfig { current ->
                current.copy(
                    logoPreset = "ctecs",
                    logoUri = null
                )
            }
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
                    preferencesManager.updateSelectedAlignmentId(null)
                    lastCalculatedMatchResult = null
                    progressionTracker.resetChainageFilter()
                    if (_manualChainage.value != null || !_manualChainageText.value.isNullOrBlank() || _liveManualCalibration.value.isActive) {
                        val cleared = com.example.model.ManualChainageCalibrationState.inactive(_liveManualCalibration.value.mode)
                        _manualChainage.value = null
                        _manualChainageText.value = null
                        _liveManualCalibration.value = cleared
                        _manualCalibrationCommand.value = cleared
                        lastPersistedManualMetres = null
                        lastPersistedManualCalibrated = false
                        preferencesManager.updateManualChainageCalibration(cleared)
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

    fun importRouteFromUrl(
        rawUrl: String,
        onComplete: ((Result<RouteData>) -> Unit)? = null
    ) {
        val trimmedUrl = rawUrl.trim()
        if (trimmedUrl.isEmpty()) {
            _routeImportError.value = "Please enter a Google My Maps or direct KML/KMZ URL."
            showToast("Please enter a valid KML or My Maps URL", ToastType.ERROR)
            return
        }
        if (_isImportingRoute.value) {
            showToast("Import already in progress...", ToastType.INFO)
            return
        }
        viewModelScope.launch {
            _isImportingRoute.value = true
            _routeImportError.value = null
            val isMyMaps = com.example.parser.KmlUrlImporter.isGoogleMyMapsUrl(trimmedUrl)
            _routeImportStatus.value = if (isMyMaps) {
                "Fetching Google My Maps KML/KMZ..."
            } else {
                "Downloading & parsing KML/KMZ URL..."
            }
            try {
                val result = routeRepository.importRouteFromUrl(trimmedUrl)
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    selectedAlignmentId.value = null
                    lastCalculatedMatchResult = null
                    progressionTracker.resetChainageFilter()
                    if (_manualChainage.value != null || !_manualChainageText.value.isNullOrBlank() || _liveManualCalibration.value.isActive) {
                        val cleared = com.example.model.ManualChainageCalibrationState.inactive(_liveManualCalibration.value.mode)
                        _manualChainage.value = null
                        _manualChainageText.value = null
                        _liveManualCalibration.value = cleared
                        _manualCalibrationCommand.value = cleared
                        lastPersistedManualMetres = null
                        lastPersistedManualCalibrated = false
                        preferencesManager.updateManualChainageCalibration(cleared)
                    }
                    val rangeStr = if (data?.startChainage != null && data.endChainage != null) {
                        " • ${data.startChainage.format()} → ${data.endChainage.format()}"
                    } else ""
                    val msg = "Imported '${data?.name ?: data?.fileName ?: "URL Alignment"}' (${data?.markers?.size ?: 0} markers$rangeStr)"
                    _routeImportStatus.value = msg
                    _routeImportError.value = null
                    _uiMessages.emit(UiMessage.Success(msg))
                    showToast(msg, ToastType.SUCCESS)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to import from URL"
                    _routeImportStatus.value = null
                    _routeImportError.value = err
                    _uiMessages.emit(UiMessage.Error("URL import failed: $err"))
                    showToast("URL import failed: $err", ToastType.ERROR)
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
            preferencesManager.updateSelectedAlignmentId(null)
            lastCalculatedMatchResult = null
            progressionTracker.resetChainageFilter()
            routeRepository.setActiveRoute(id)
            _uiMessages.emit(UiMessage.Info("Active route updated"))
        }
    }

    fun deleteRoute(id: String) {
        viewModelScope.launch {
            selectedAlignmentId.value = null
            preferencesManager.updateSelectedAlignmentId(null)
            lastCalculatedMatchResult = null
            progressionTracker.resetChainageFilter()
            routeRepository.deleteRoute(id)
            offlineMapSyncManager.deleteProjectOfflineData(getApplication(), id)
            _routeImportStatus.value = null
            _routeImportError.value = null
            _uiMessages.emit(UiMessage.Info("Alignment removed"))
            showToast("Alignment removed", ToastType.INFO)
        }
    }

    fun deletePhoto(photo: PhotoRecordEntity) {
        deletePhotos(listOf(photo))
    }

    fun deletePhotos(photos: List<PhotoRecordEntity>) {
        val uniqueTargets = photos.distinctBy { if (it.id != 0L) "id:${it.id}" else "path:${it.filePath}" }
        val keys = uniqueTargets.map { if (it.id != 0L) "id:${it.id}" else "path:${it.filePath}" }
        val newTargets = uniqueTargets.filterIndexed { index, _ -> !inFlightDeleteKeys.contains(keys[index]) }
        if (newTargets.isEmpty()) return

        val newKeys = newTargets.map { if (it.id != 0L) "id:${it.id}" else "path:${it.filePath}" }
        inFlightDeleteKeys.addAll(newKeys)
        _isDeletingMedia.value = true

        viewModelScope.launch {
            try {
                val result = photoRepository.deleteMediaItems(newTargets)
                handleMediaDeleteResult(result)
            } catch (e: Exception) {
                val err = e.message ?: "Failed to delete media file"
                _uiMessages.emit(UiMessage.Error(err))
                showToast(err, ToastType.ERROR)
            } finally {
                if (_pendingMediaDeleteRequest.value == null) {
                    inFlightDeleteKeys.removeAll(newKeys.toSet())
                }
                _isDeletingMedia.value = false
            }
        }
    }

    fun onSystemDeleteApprovalResult(approved: Boolean) {
        val pending = _pendingMediaDeleteRequest.value ?: return
        _pendingMediaDeleteRequest.value = null
        val keys = pending.targetPhotos.map { if (it.id != 0L) "id:${it.id}" else "path:${it.filePath}" }.toSet()

        viewModelScope.launch {
            _isDeletingMedia.value = true
            try {
                if (!approved) {
                    photoRepository.cancelPendingDeletion(pending.targetPhotos)
                    clearLastCapturedIfDeleted(pending.alreadyDeletedPhotos)
                    val msg = if (pending.alreadyDeletedPhotos.isNotEmpty()) {
                        "Deleted ${pending.alreadyDeletedPhotos.size} item(s); ${pending.targetPhotos.size} cancelled (permission denied)"
                    } else {
                        "Deletion cancelled — device storage permission denied"
                    }
                    _uiMessages.emit(UiMessage.Error(msg))
                    showToast(msg, ToastType.ERROR)
                    if (pending.alreadyDeletedPhotos.isNotEmpty()) {
                        photoRepository.syncMediaFilesFromStorage()
                    }
                    return@launch
                }

                val result = photoRepository.completeApprovedDeletion(
                    targetPhotos = pending.targetPhotos,
                    pendingUris = pending.pendingUris,
                    alreadyDeletedPhotos = pending.alreadyDeletedPhotos,
                    alreadyDeletedPaths = pending.alreadyDeletedPaths
                )
                handleMediaDeleteResult(result)
            } catch (e: Exception) {
                val err = e.message ?: "Failed to complete media deletion"
                _uiMessages.emit(UiMessage.Error(err))
                showToast(err, ToastType.ERROR)
            } finally {
                inFlightDeleteKeys.removeAll(keys)
                _isDeletingMedia.value = false
            }
        }
    }

    private suspend fun handleMediaDeleteResult(result: MediaDeleteResult) {
        when (result) {
            is MediaDeleteResult.Success -> {
                clearLastCapturedIfDeleted(result.deletedPhotos)
                photoRepository.syncMediaFilesFromStorage()
                val msg = if (result.deletedPhotos.size == 1) {
                    val isVideo = result.deletedPhotos.first().fileName.endsWith(".mp4", ignoreCase = true)
                    if (isVideo) "Video deleted permanently" else "Photo deleted permanently"
                } else {
                    "Deleted ${result.deletedPhotos.size} media files permanently"
                }
                _uiMessages.emit(UiMessage.Success(msg))
                showToast(msg, ToastType.SUCCESS)
            }
            is MediaDeleteResult.RequiresSystemApproval -> {
                clearLastCapturedIfDeleted(result.alreadyDeletedPhotos)
                _pendingMediaDeleteRequest.value = PendingMediaDeleteRequest(
                    intentSender = result.intentSender,
                    targetPhotos = result.targetPhotos,
                    pendingUris = result.pendingUris,
                    alreadyDeletedPhotos = result.alreadyDeletedPhotos,
                    alreadyDeletedPaths = result.alreadyDeletedPaths
                )
            }
            is MediaDeleteResult.PartialFailure -> {
                clearLastCapturedIfDeleted(result.deletedPhotos)
                photoRepository.syncMediaFilesFromStorage()
                val firstErr = result.failedItems.firstOrNull()?.second ?: "permission denied"
                val msg = "Deleted ${result.deletedPhotos.size} item(s); ${result.failedItems.size} failed ($firstErr)"
                _uiMessages.emit(UiMessage.Error(msg))
                showToast(msg, ToastType.ERROR)
            }
            is MediaDeleteResult.Failure -> {
                _uiMessages.emit(UiMessage.Error(result.reason))
                showToast(result.reason, ToastType.ERROR)
            }
        }
    }

    private fun clearLastCapturedIfDeleted(deletedPhotos: List<PhotoRecordEntity>) {
        val current = _lastCapturedPhoto.value ?: return
        if (deletedPhotos.any { it.id == current.id || (it.filePath.isNotBlank() && it.filePath == current.filePath) }) {
            _lastCapturedPhoto.value = null
        }
    }

    fun syncGalleryMedia(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = photoRepository.syncMediaFilesFromStorage()
            onComplete?.invoke(count)
        }
    }

    private val photoSaveMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Ultra-fast non-blocking photo processing pipeline.
     *
     * - Uses the immutable [capturedSnapshot] taken at the exact shutter millisecond (or captures one immediately).
     * - Applies ONLY the active [com.example.model.CameraCropMode] (in FULL mode: ZERO pixels cropped and ZERO
     *   extra bitmap copies) strictly BEFORE existing [OverlayRenderer] timestamp/GPS/chainage/weather/EXIF burn-in.
     * - Releases shutter state rapidly while serializing background disk + MediaStore writes via [photoSaveMutex]
     *   so rapid back-to-back shots never freeze the preview or drop/corrupt photos.
     */
    fun setCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
    }

    fun processCapture(bitmap: Bitmap, capturedSnapshot: CaptureSnapshot? = null) {
        val snapshot = capturedSnapshot ?: createCurrentSnapshot()
        val settingsNow = cameraSettings.value
        val activeLevel = _captureExposureNormalized.value
        val activeEvStops = _captureExposureEvStops.value
        val hwEvApplied = _captureExposureHardwareSupported.value

        // Keep shutter responsive for rapid consecutive shots while background encoding/saving finishes asynchronously
        _isCapturing.value = false

        viewModelScope.launch(Dispatchers.Default) {
            val intermediateBitmaps = mutableListOf<Bitmap>()
            try {
                // 1. Hardware-fallback exposure compensation (no-op when Auto or hardware EV already applied)
                val exposureProcessed = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
                    source = bitmap,
                    normalizedLevel = activeLevel,
                    evStops = activeEvStops,
                    isAutoExposure = settingsNow.autoBrightnessEnabled,
                    hardwareEvApplied = hwEvApplied
                )
                if (exposureProcessed !== bitmap) intermediateBitmaps.add(exposureProcessed)

                // 2. Unified dynamic crop mode strictly BEFORE overlay rendering so stamps/EXIF stay 100% intact.
                //    In FULL mode this returns exposureProcessed directly with ZERO cropping and ZERO downscaling.
                val isLandscapeShot = if (exposureProcessed.width != exposureProcessed.height) {
                    exposureProcessed.width > exposureProcessed.height
                } else {
                    snapshot.deviceRotationDegrees == 90 || snapshot.deviceRotationDegrees == 270
                }
                val captureBitmap = com.example.camera.CameraOutputFormatEngine.applyCropModeToBitmap(
                    source = exposureProcessed,
                    cropMode = snapshot.cropMode,
                    isLandscape = isLandscapeShot
                )
                if (captureBitmap !== exposureProcessed && captureBitmap !== bitmap) {
                    intermediateBitmaps.add(captureBitmap)
                }

                // 3. Serialize disk + MediaStore persistence off the UI thread while preserving 100% of
                //    existing timestamp, GPS, chainage, LHS/RHS, weather, watermark, logo, minimap & EXIF logic.
                val result = photoSaveMutex.withLock {
                    photoRepository.saveCapturedPhoto(captureBitmap, snapshot)
                }
                if (result.isSuccess) {
                    val record = result.getOrNull()
                    _lastCapturedPhoto.value = record
                    // Reset 10-minute manual direction inactivity timer ONLY on successful photograph capture
                    CtecsNavigationTelemetryHub.sharedDirectionManager.onPhotoCaptured()
                    _uiMessages.emit(UiMessage.Success("Photo saved: ${record?.fileName}"))
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Save error"
                    _uiMessages.emit(UiMessage.Error("Failed to save: $err"))
                }
            } catch (e: Exception) {
                _uiMessages.emit(UiMessage.Error("Capture failed: ${e.message}"))
            } finally {
                intermediateBitmaps.forEach { bmp ->
                    if (bmp !== bitmap && !bmp.isRecycled) {
                        try { bmp.recycle() } catch (ignored: Throwable) {}
                    }
                }
                if (!bitmap.isRecycled) {
                    try { bitmap.recycle() } catch (ignored: Throwable) {}
                }
            }
        }
    }

    /**
     * Unified Force Refresh All / Smart Refresh action for Chainage Accuracy.
     * Synchronizes GPS, validates/rebuilds KMZ alignment caches without losing files,
     * preserves permanent control points & profiles, preserves manual chainage overrides,
     * recalculates KMZ + calibration + LHS/RHS side, and updates Home/Camera/Chainage Control immediately.
     */
    fun forceRefreshAllChainageAccuracy(
        mode: RefreshMode = RefreshMode.FORCE_ALL,
        onComplete: ((ChainageAccuracyReport) -> Unit)? = null
    ) {
        val generation = refreshCoordinator.tryBeginRefresh() ?: return
        _isRefreshingChainageAccuracy.value = true
        val previousMatched = lastCalculatedMatchResult ?: _autoMatchStateFlow.value.takeIf { it.chainage != null }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Step A: Request latest GPS location & validate coordinates/freshness/accuracy
                val gpsOk = locationTracker.refreshGpsAsync(forceNewFix = (mode == RefreshMode.FORCE_ALL))
                val locState = locationTracker.locationState.value
                val gpsValidation = locationTracker.validateLocationState(locState)

                // Step B: Validate active KMZ/KML alignment & rebuild spatial index when needed
                val routeOutcome = routeRepository.validateAndRefreshActiveRoute(
                    forceRebuildCache = (mode == RefreshMode.FORCE_ALL)
                )
                val selAlignId = selectedAlignmentId.value
                val effectiveRoute = routeOutcome.route?.let { route ->
                    if (selAlignId != null && route.alignments.any { it.id == selAlignId }) {
                        route.copy(selectedAlignmentId = selAlignId)
                    } else {
                        route
                    }
                }

                // Step C: Reload & revalidate permanent calibration control points and profiles cleanly
                val currentRawPoints = if (effectiveRoute != null) {
                    routeCorrectionsMap.value[effectiveRoute.id].orEmpty()
                } else {
                    emptyList()
                }
                val calibrationSync = refreshCoordinator.synchronizeCalibrationData(
                    route = effectiveRoute,
                    rawPoints = currentRawPoints,
                    profiles = calibrationProfiles.value,
                    forceRevalidate = (mode == RefreshMode.FORCE_ALL)
                )
                _activeProfileCompatibility.value = calibrationSync.compatibility

                if (effectiveRoute != null &&
                    calibrationSync.syncedPoints.isNotEmpty() &&
                    calibrationSync.syncedPoints != currentRawPoints
                ) {
                    preferencesManager.saveRouteCorrectionPointsList(effectiveRoute.id, calibrationSync.syncedPoints)
                }

                // Steps D, E, F: Recalculate chainage, calibrated offset, LHS/RHS side & run 9 accuracy checks
                val output = refreshCoordinator.recalculateAndValidate(
                    generation = generation,
                    mode = mode,
                    locationState = locState,
                    gpsValidation = gpsValidation,
                    route = effectiveRoute,
                    routeValidGeometry = routeOutcome.isValidGeometry,
                    calibrationSync = calibrationSync,
                    isCorrectionsEnabled = isRouteCorrectionsEnabled.value,
                    previousResult = previousMatched,
                    progressionTracker = progressionTracker,
                    manualChainage = _manualChainage.value,
                    manualChainageText = _manualChainageText.value,
                    gpsRefreshSucceeded = gpsOk
                )

                val latestLocTime = locationTracker.locationState.value.lastFixTime
                val refreshedDirState = evaluateAndPublishSmartDirection(locState, output.matchResult, effectiveRoute)
                val enrichedRefreshMatch = output.matchResult.copy(
                    detectedSide = refreshedDirState.confirmedSide ?: "Unknown",
                    smartDirectionState = refreshedDirState
                )
                if (generation == refreshCoordinator.currentGeneration() && latestLocTime <= locState.lastFixTime) {
                    if (mode == RefreshMode.FORCE_ALL) {
                        stuckChainageMonitor.reset()
                    }
                    lastCalculatedMatchResult = enrichedRefreshMatch
                    _autoMatchStateFlow.value = enrichedRefreshMatch
                }

                SmartChainageCorrectionEngine.recordRefreshExecuted(
                    isForceAll = (mode == RefreshMode.FORCE_ALL),
                    accuracyStatusLabel = output.report.status.label,
                    summaryMessage = output.report.summaryMessage
                )
                _chainageAccuracyReport.value = output.report

                if (effectiveRoute != null && mode == RefreshMode.FORCE_ALL) {
                    preferencesManager.addChainageLogEntry(
                        ChainageLogEntry(
                            actionType = "FORCE_REFRESH_ALL",
                            routeId = effectiveRoute.id,
                            routeName = effectiveRoute.name,
                            summary = "${output.report.status.label} (${output.matchResult.displayChainage})",
                            details = output.report.summaryMessage
                        )
                    )
                }

                val toastType = when (output.report.status) {
                    ChainageAccuracyStatus.VERIFIED, ChainageAccuracyStatus.UPDATED -> ToastType.SUCCESS
                    ChainageAccuracyStatus.NEEDS_GPS, ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW -> ToastType.WARNING
                    ChainageAccuracyStatus.ALIGNMENT_MISMATCH -> ToastType.ERROR
                }
                _uiMessages.emit(
                    if (toastType == ToastType.ERROR) UiMessage.Error(output.report.summaryMessage)
                    else UiMessage.Success(output.report.summaryMessage)
                )
                showToast(output.report.summaryMessage, toastType)

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(output.report)
                }
            } catch (e: Exception) {
                val hasPrevious = previousMatched?.chainage != null
                val fallbackReport = ChainageAccuracyReport(
                    status = if (hasPrevious) ChainageAccuracyStatus.NEEDS_GPS else ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW,
                    mode = mode,
                    isRefreshing = false,
                    isStaleFallback = hasPrevious,
                    summaryMessage = if (hasPrevious) {
                        "Refresh interrupted • Retained ${previousMatched?.displayChainage} (Stale)"
                    } else {
                        "Refresh failed: ${e.message ?: "Unknown error"}"
                    },
                    lastRefreshedEpochMs = System.currentTimeMillis(),
                    isManualOverrideActive = _manualChainage.value != null || !_manualChainageText.value.isNullOrBlank()
                )
                _chainageAccuracyReport.value = fallbackReport
                showToast(fallbackReport.summaryMessage, ToastType.WARNING)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(fallbackReport)
                }
            } finally {
                refreshCoordinator.endRefresh()
                _isRefreshingChainageAccuracy.value = false
            }
        }
    }

    fun refreshLocation() {
        forceRefreshAllChainageAccuracy(RefreshMode.SMART)
    }

    // Injection for testing location
    fun simulateGps(lat: Double, lng: Double, accuracy: Float = 3.2f) {
        locationTracker.injectLocation(lat, lng, accuracy)
    }

    fun startLocation() {
        isAppForegrounded = true
        locationTracker.startTracking()
        if (!_isScreensaverScreenOff.value && !_isScreensaverAodActive.value) {
            compassTracker.startTracking()
        }
        syncBackgroundNavigationService()
    }

    fun stopLocation() {
        isAppForegrounded = false
        locationTracker.stopTracking()
        compassTracker.stopTracking()
    }

    fun stopLocationIfIdle() {
        isAppForegrounded = false
        val hasRoute = activeRouteData.value != null
        val bgServiceActive = CtecsNavigationTelemetryHub.isServiceRunning.value ||
            CtecsNavigationTelemetryHub.isKmStoneServiceRunning.value ||
            _navigationNotificationSettings.value.enabled ||
            com.example.navigation.KmStoneMonitoringForegroundService.shouldServiceStayActive(
                settings = _kmStoneAlertSettings.value,
                hasActiveRoute = hasRoute
            )
        if (!bgServiceActive) {
            locationTracker.stopTracking()
        }
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

    // ====================================================
    // SETTINGS BACKUP & RESTORE
    // ====================================================
    private val _backupHistory = MutableStateFlow<List<BackupHistoryItem>>(emptyList())
    val backupHistory: StateFlow<List<BackupHistoryItem>> = _backupHistory.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _backupProgress = MutableStateFlow(0f)
    val backupProgress: StateFlow<Float> = _backupProgress.asStateFlow()

    private val _backupProgressMessage = MutableStateFlow<String?>(null)
    val backupProgressMessage: StateFlow<String?> = _backupProgressMessage.asStateFlow()

    private val _lastBackupExportResult = MutableStateFlow<BackupExportResult?>(null)
    val lastBackupExportResult: StateFlow<BackupExportResult?> = _lastBackupExportResult.asStateFlow()

    private val _pendingBackupPreview = MutableStateFlow<BackupPreviewInfo?>(null)
    val pendingBackupPreview: StateFlow<BackupPreviewInfo?> = _pendingBackupPreview.asStateFlow()

    private val _lastRestoreReport = MutableStateFlow<BackupRestoreReport?>(null)
    val lastRestoreReport: StateFlow<BackupRestoreReport?> = _lastRestoreReport.asStateFlow()

    fun refreshBackupHistory() {
        viewModelScope.launch {
            _backupHistory.value = settingsBackupManager.listBackupHistory()
        }
    }

    fun exportSettingsBackup(destinationUri: Uri? = null) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgress.value = 0.05f
            _backupProgressMessage.value = "Preparing backup..."
            try {
                val result = settingsBackupManager.exportBackup(
                    destinationUri = destinationUri,
                    onProgress = { fraction, msg ->
                        _backupProgress.value = fraction
                        _backupProgressMessage.value = msg
                    }
                )
                _lastBackupExportResult.value = result
                _backupHistory.value = settingsBackupManager.listBackupHistory()
                if (result.success) {
                    val msg = "Backup exported: ${result.fileName} (${result.totalSettingsCount} settings)"
                    showToast(msg, ToastType.SUCCESS)
                    _uiMessages.emit(UiMessage.Success(msg))
                } else {
                    val err = result.errorMessage ?: "Backup export failed"
                    showToast(err, ToastType.ERROR)
                    _uiMessages.emit(UiMessage.Error(err))
                }
            } finally {
                _isBackupBusy.value = false
                _backupProgress.value = 0f
                _backupProgressMessage.value = null
            }
        }
    }

    fun inspectBackupUriForRestore(uri: Uri) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgress.value = 0.4f
            _backupProgressMessage.value = "Validating backup checksums & manifest..."
            try {
                val preview = settingsBackupManager.inspectBackupUri(uri)
                _pendingBackupPreview.value = preview
                if (!preview.isRestorable) {
                    showToast(preview.validationMessage, ToastType.ERROR)
                }
            } finally {
                _isBackupBusy.value = false
                _backupProgress.value = 0f
                _backupProgressMessage.value = null
            }
        }
    }

    fun inspectHistoryItemForRestore(item: BackupHistoryItem) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgress.value = 0.4f
            _backupProgressMessage.value = "Validating backup archive..."
            try {
                val preview = settingsBackupManager.inspectBackupFile(java.io.File(item.filePath))
                _pendingBackupPreview.value = preview
                if (!preview.isRestorable) {
                    showToast(preview.validationMessage, ToastType.ERROR)
                }
            } finally {
                _isBackupBusy.value = false
                _backupProgress.value = 0f
                _backupProgressMessage.value = null
            }
        }
    }

    fun dismissBackupPreview() {
        _pendingBackupPreview.value = null
    }

    fun dismissRestoreReport() {
        _lastRestoreReport.value = null
    }

    fun confirmRestoreFromPreview(
        mode: RestoreMode,
        selectedCategories: Set<BackupCategory>
    ) {
        val preview = _pendingBackupPreview.value ?: return
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgress.value = 0.2f
            _backupProgressMessage.value = "Creating safety snapshot & restoring settings..."
            try {
                val report = settingsBackupManager.restoreFromPreview(
                    preview = preview,
                    mode = mode,
                    selectedCategories = selectedCategories
                )
                _pendingBackupPreview.value = null
                _lastRestoreReport.value = report
                if (report.success) {
                    val refreshedLoc = overlayConfig.value.lastDetectedLocationName
                    if (refreshedLoc.isNotBlank()) {
                        _currentLocationName.value = refreshedLoc
                    }
                    _backupHistory.value = settingsBackupManager.listBackupHistory()
                    val msg = "Restored ${report.restoredSettingsCount} settings across ${report.restoredCategories.size} categories"
                    showToast(msg, ToastType.SUCCESS)
                    _uiMessages.emit(UiMessage.Success(msg))
                } else {
                    val msg = report.errorMessage ?: "Restore failed"
                    showToast(msg, ToastType.ERROR)
                    _uiMessages.emit(UiMessage.Error(msg))
                }
            } finally {
                _isBackupBusy.value = false
                _backupProgress.value = 0f
                _backupProgressMessage.value = null
            }
        }
    }

    fun deleteBackupHistoryItem(item: BackupHistoryItem) {
        viewModelScope.launch {
            val deleted = settingsBackupManager.deleteBackupFile(item.filePath)
            _backupHistory.value = settingsBackupManager.listBackupHistory()
            if (deleted) {
                showToast("Deleted backup ${item.fileName}", ToastType.INFO)
            } else {
                showToast("Unable to delete backup file", ToastType.WARNING)
            }
        }
    }

    fun getShareBackupUri(item: BackupHistoryItem): Uri? {
        return settingsBackupManager.getShareUriForBackup(item.filePath)
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationIfIdle()
        calibrationMatchingScope.cancel()
    }
}
