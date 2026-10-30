package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.model.CameraMode
import com.example.model.ChainageMatchResult
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.SpeedUnit
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
    val locationTracker = LocationTracker(application)
    val compassTracker = com.example.sensor.CompassAndOrientationTracker(application)
    val appUpdateManager = com.example.update.AppUpdateManager(application)
    val whatsNewManager = com.example.update.WhatsNewManager(application)

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

    private val progressionTracker = com.example.geo.ChainageProgressionTracker()

    // Combined live match result
    val liveMatchResult: StateFlow<ChainageMatchResult> = combine(
        locationState,
        activeRouteData,
        manualChainage
    ) { loc, route, manual ->
        val rawResult = if (manual != null) {
            ChainageMatchResult.manual(manual, loc.point)
        } else if (loc.point != null) {
            ChainageMatcher.match(
                gpsPoint = loc.point,
                gpsAccuracy = loc.accuracy,
                bearing = loc.bearing,
                routeData = route
            )
        } else {
            ChainageMatchResult.noRoute(loc.accuracy)
        }
        progressionTracker.processResult(rawResult)
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

        // Collect live telemetry snapshots during active video recording
        viewModelScope.launch {
            liveMatchResult.collect {
                if (_isRecording.value && !_isRecordingPaused.value) {
                    synchronized(videoSnapshotsTimeline) {
                        videoSnapshotsTimeline.add(createCurrentSnapshot())
                    }
                }
            }
        }
    }


    fun checkForAppUpdate(isManual: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val result = appUpdateManager.checkForUpdate()
            _isCheckingUpdate.value = false

            if (result.isSuccess) {
                val info = result.getOrNull()
                if (info != null && info.isUpdateAvailable) {
                    _availableUpdate.value = info
                    // Also parse dynamic what's new from release notes
                    val dynamicFeatures = whatsNewManager.parseReleaseNotesToFeatures(info.releaseNotes)
                    _whatsNewFeatures.value = dynamicFeatures
                } else if (isManual) {
                    val channelLabel = com.example.update.AppUpdateManager.INSTALLED_CHANNEL.replaceFirstChar { it.uppercase() }
                    _uiMessages.emit(UiMessage.Info("Latest version installed on $channelLabel channel (v${com.example.BuildConfig.VERSION_NAME})"))
                }
            } else if (isManual) {
                val err = result.exceptionOrNull()?.message ?: "Check failed"
                _uiMessages.emit(UiMessage.Error("Update check: $err"))
            }
        }
    }

    val updateChannel: String = com.example.update.AppUpdateManager.INSTALLED_CHANNEL

    fun downloadAndInstallUpdate() {
        val info = _availableUpdate.value ?: return
        val url = info.apkDownloadUrl ?: return

        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _updateDownloadProgress.value = 0f

            val fileName = "CTECS-${info.tagName}.apk"
            val result = appUpdateManager.downloadApk(url, fileName) { progress ->
                _updateDownloadProgress.value = progress
            }
            _isDownloadingUpdate.value = false

            if (result.isSuccess) {
                val apkFile = result.getOrNull()
                if (apkFile != null) {
                    _availableUpdate.value = null
                    val installResult = appUpdateManager.validateAndInstallApk(apkFile, info.channel)
                    if (installResult.isSuccess) {
                        _uiMessages.emit(UiMessage.Success("Update validated for ${info.channel} channel. Starting installer..."))
                    } else {
                        val reason = installResult.exceptionOrNull()?.message ?: "Security validation failed"
                        _uiMessages.emit(UiMessage.Error("Installation blocked: $reason"))
                    }
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
                _uiMessages.emit(UiMessage.Success("Manual chainage set to ${parsed.format()}"))
            }
        } else {
            viewModelScope.launch {
                _uiMessages.emit(UiMessage.Error("Invalid chainage format. Use e.g. 196+040"))
            }
        }
    }

    fun clearManualChainage() {
        _manualChainage.value = null
        viewModelScope.launch {
            _uiMessages.emit(UiMessage.Info("Switched back to automatic GPS chainage"))
        }
    }

    fun updateSide(side: RoadSide) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(projectProfile.value.copy(roadSide = side))
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

    fun saveProjectProfile(profile: ProjectProfile) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(profile)
            _uiMessages.emit(UiMessage.Success("Project profile saved"))
        }
    }

    fun saveProjectProfileSilent(profile: ProjectProfile) {
        viewModelScope.launch {
            preferencesManager.updateProjectProfile(profile)
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

    fun createCurrentSnapshot(): CaptureSnapshot {
        return CaptureSnapshot(
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
            speedMps = locationState.value.speed
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
            var msAccumulator = 0L
            while (_isRecording.value) {
                kotlinx.coroutines.delay(200L) // 5 Hz high-frequency sampling
                if (_isRecording.value && !_isRecordingPaused.value) {
                    msAccumulator += 200L
                    if (msAccumulator >= 1000L) {
                        _recordingDurationSeconds.value += 1
                        msAccumulator = 0L
                    }
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

    fun processRecordedVideo(context: android.content.Context, rawVideoFile: java.io.File) {
        viewModelScope.launch {
            showToast("Saving video with dynamic overlays...", ToastType.INFO)

            val timelineCopy = synchronized(videoSnapshotsTimeline) { videoSnapshotsTimeline.toList() }
            val defaultSnapshot = createCurrentSnapshot()

            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val fileName = "VID_${timeStamp}.mp4"

            val outputDir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "HighwayCamera"
            ).apply { mkdirs() }

            val finalFile = java.io.File(outputDir, fileName)

            val success = com.example.overlay.VideoOverlayProcessor.applyOverlayToVideo(
                context = context,
                inputFile = rawVideoFile,
                outputFile = finalFile,
                snapshotsTimeline = timelineCopy,
                defaultSnapshot = defaultSnapshot
            )

            if (success && finalFile.exists() && finalFile.length() > 0) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    arrayOf("video/mp4")
                ) { _, _ -> }

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

                showToast("Video saved with dynamic overlays to Movies/HighwayCamera", ToastType.SUCCESS)

                try { rawVideoFile.delete() } catch (ignored: Exception) {}
            } else {
                showToast("Video saved to Movies/HighwayCamera", ToastType.SUCCESS)
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
                    compassAzimuth = compassState.value.azimuthDegrees
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
