package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupCategory
import com.example.backup.PrefValueType
import com.example.backup.TypedPrefEntry
import com.example.data.PreferencesManager
import com.example.model.CameraGridMode
import com.example.model.CameraMode
import com.example.model.CameraSettings
import com.example.model.CaptureMode
import com.example.model.CaptureTimer
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.CoordinateFormat
import com.example.model.CtecsWidgetInstanceConfig
import com.example.model.CtecsWidgetSettings
import com.example.model.CtecsWidgetType
import com.example.model.DefinedProject
import com.example.model.HomeScreensaverSettings
import com.example.model.KmAdvanceWarningOption
import com.example.model.KmAlertSoundOption
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVibrationIntensity
import com.example.model.KmVibrationPattern
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapSizePreset
import com.example.model.MiniMapStyle
import com.example.model.MiniMapStyleConfig
import com.example.model.NavigationNotificationSettings
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.PermanentCalibrationProfile
import com.example.model.PreviewRatio
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.model.SpeedUnit
import com.example.model.VideoQualityOption
import com.example.model.WidgetAccentColorPreset
import com.example.model.WidgetBackgroundStyle
import com.example.model.WidgetThemeMode
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.ui.MainViewModel
import com.example.widget.CtecsCompactWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlobalSettingsPersistenceTest {

    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        preferencesManager = PreferencesManager(context)
        runBlocking {
            preferencesManager.resetAllSettings()
            delay(50)
        }
        PreferencesManager.clearMemoryCacheForProcessRestart()
        CtecsNavigationTelemetryHub.ensureInitialized(context, forceReload = true)
    }

    @After
    fun tearDown() {
        runBlocking {
            preferencesManager.resetAllSettings()
            delay(50)
        }
        PreferencesManager.clearMemoryCacheForProcessRestart()
    }

    private suspend fun awaitPersistenceDrain() {
        PreferencesManager.awaitPendingWrites()
        delay(40)
    }

    @Test
    fun firstInstallDefaultsAreConsistentAcrossViewModelAndPreferences() {
        val vm = MainViewModel(application)
        assertEquals("system", vm.themeMode.value)
        assertEquals("system", vm.mapThemePreference.value)
        assertEquals("standard", vm.mapTypePreference.value)
        assertEquals("grid", vm.galleryViewMode.value)
        assertEquals("Newest first", vm.gallerySortOption.value)
        assertEquals("None", vm.galleryGroupOption.value)
        assertEquals(CaptureMode.PHOTO, vm.captureMode.value)
        assertFalse(vm.isFlashOn.value)
        assertFalse(vm.isFrontCamera.value)
        assertEquals(CameraSettings(), vm.cameraSettings.value)
        assertEquals(OverlayConfig(), vm.overlayConfig.value)
        assertEquals(ProjectProfile(), vm.projectProfile.value)
        assertEquals(HomeScreensaverSettings.defaultSettings(), vm.homeScreensaverSettings.value)
        assertEquals(NavigationNotificationSettings.defaultSettings(), vm.navigationNotificationSettings.value)
        assertEquals(CtecsWidgetSettings.defaultSettings(), vm.widgetSettings.value)
        assertEquals(KmStoneAlertSettings.defaultSettings(), vm.kmStoneAlertSettings.value)
        assertNull(vm.preferredExternalNavAppId.value)
        assertNull(vm.selectedAlignmentId.value)
        assertTrue(vm.surveyShow200mLabels.value)
        assertTrue(vm.surveyShowCentreline.value)
        assertTrue(vm.surveyShowGpsTrack.value)
        assertTrue(vm.surveyShowLaneTrack.value)
        assertTrue(vm.surveyShowMarkers.value)
        assertEquals("standard", vm.surveyMapType.value)
    }

    @Test
    fun allApplicationSettingsPersistAcrossAppCloseReopenAndColdProcessRestart() = runBlocking {
        val vm1 = MainViewModel(application)

        // 1. General Theme, Map Theme/Type & Gallery prefs
        vm1.setThemeMode("dark")
        vm1.setMapTheme("dark")
        vm1.setMapType("satellite")
        vm1.updateGalleryPrefs(viewMode = "list", sortOption = "Chainage ascending", groupOption = "By Date")

        // 2. Project Profile & Defined Projects & Selected Alignment
        val customProfile = ProjectProfile(
            highwayId = "NH-709AD",
            packageSection = "Pkg-III Roorkee-Hardwar",
            startChainage = "12+500",
            endChainage = "64+200",
            workType = "RM",
            roadSide = RoadSide.LHS,
            lane = "O/L",
            activity = "BC",
            subActivity = "Prime Coat",
            customDescription = "Mainline Wearing Course",
            inspectorName = "R. Sharma",
            separator = "•"
        )
        vm1.saveProjectProfile(customProfile)
        vm1.addDefinedProject(DefinedProject("NH-709AD", "Pkg-III", "RM", "12+500", "64+200"))
        vm1.selectAlignment("align_lhs_2")

        // 3. Camera Settings & Hardware States (flash, front camera, capture mode, zoom, EV)
        val customCamera = CameraSettings(
            captureTimer = CaptureTimer.SEC_5,
            gridMode = CameraGridMode.GRID_4X4,
            showHorizonLevel = false,
            previewRatio = PreviewRatio.RATIO_16_9,
            captureSound = false,
            volumeButtonShutter = false,
            frontCameraMirror = true,
            jpegQuality = 85,
            saveOriginalAndStamped = true,
            preserveExif = false,
            videoQuality = VideoQualityOption.UHD_4K,
            videoFps = 60,
            recordAudio = false,
            gpsUpdateIntervalSec = 3,
            gpsAccuracyThresholdMeters = 8f,
            routeMatchingRadiusMeters = 140.0,
            coordinateFormat = CoordinateFormat.DMS,
            is24HourFormat = true,
            exposureCompensationStep = 2,
            cameraMode = CameraMode.LOCATION,
            speedUnit = SpeedUnit.MPH,
            defaultLensZoom = 2.5f,
            touchToCapture = true,
            alwaysDisplayZoomSlider = false,
            batterySaveMode = true,
            batterySaveBrightness = 0.25f,
            easyToggleCameras = false,
            whiteBalance = "cloudy",
            videoSegmentation = "5 min",
            customFilenamePrefix = "CTECS_QC",
            filenameFormatPreset = "Custom_Chainage",
            customStoragePath = "/storage/emulated/0/Documents/CTECS",
            autoRotateUI = false,
            photoResolution = "4000 x 3000",
            videoResolution = "3840 x 2160",
            photoAspectRatio = "16:9",
            videoAspectRatio = "4:3"
        )
        vm1.saveCameraSettings(customCamera)
        vm1.toggleFlash() // true
        vm1.toggleCameraFacing() // true
        vm1.setCaptureMode(CaptureMode.VIDEO)
        vm1.setExposureCompensation(2)

        // 4. Overlay & Timestamp & Watermark & MiniMap & DPR PDF defaults
        val customOverlay = OverlayConfig(
            template = OverlayTemplate.SITE_INSPECTION,
            timestampFormat = "yyyy-MM-dd HH:mm",
            coordinatePrecision = 6,
            showTimestamp = false,
            showCoordinates = true,
            showChainage = true,
            showMapInset = false,
            showProjectInfo = true,
            showCompass = false,
            showAltitude = false,
            showAccuracy = true,
            burnInTimestamp = false,
            showSpeedOnPreview = true,
            includeSpeedInCapture = true,
            showDirectionOnPreview = true,
            includeDirectionInCapture = true,
            showLogo = true,
            logoPreset = "nhai",
            logoUri = "/data/user/0/com.example/files/custom_logo.png",
            logoPosition = LogoPosition.TOP_RIGHT,
            logoSizeDp = 72,
            logoOpacity = 0.65f,
            logoShape = LogoShape.CIRCLE,
            logoCornerRadiusDp = 18,
            logoBorderWidthDp = 2,
            logoBorderColorHex = "#38BDF8",
            logoMarginXDp = 24,
            logoMarginYDp = 28,
            logoScale = 1.4f,
            logoRotationDegrees = 15f,
            fontSizeSp = 28f,
            fontSizePreset = "Large",
            hasTextShadow = false,
            textColorHex = "#FBBF24",
            textOpacity = 0.85f,
            fontFamily = "Monospace",
            fontStyle = "Italic",
            backgroundStyle = "Glass",
            backgroundOpacity = 0.6f,
            backgroundColorHex = "#0F172A",
            watermarkPosition = "Top Left",
            timePosition = "Time in below",
            resolutionOption = "4000 x 3000",
            locationAddressOption = "Custom",
            customAddressText = "Roorkee Bypass Km 196",
            customTag = "DBM_LAYER_2",
            gpsAccuracyMarginPreset = "±3m",
            saveOriginalImage = true,
            timestampMarginMode = "Compact",
            showIndexNumber = true,
            textAlignment = "Left",
            lineSpacingFactor = 0.55f,
            textStrokeWidthDp = 2.5f,
            showLocationName = false,
            lastDetectedLocationName = "Haridwar Bypass NH-58",
            useSamePaddingForPhotoAndVideo = false,
            photoPaddingTopDp = 10,
            photoPaddingBottomDp = 26,
            photoPaddingLeftDp = 12,
            photoPaddingRightDp = 18,
            videoPaddingTopDp = 14,
            videoPaddingBottomDp = 30,
            videoPaddingLeftDp = 20,
            videoPaddingRightDp = 22,
            pdfLogoUri = "/tmp/pdf_logo.png",
            includePdfLogoDefault = false,
            pdfSignatureUri = "/tmp/sig.png",
            includePdfSignatureDefault = false,
            pdfDefaultPreparedBy = "Sr. Highway QC Engineer",
            photoMiniMap = MiniMapStyleConfig(sizePreset = MiniMapSizePreset.LARGE, mapStyle = MiniMapStyle.DARK),
            videoMiniMap = MiniMapStyleConfig(sizePreset = MiniMapSizePreset.VERY_SMALL, zoomLevel = 16.5f)
        )
        vm1.saveOverlayConfig(customOverlay)

        // 5. Screensaver & Lock Screen Saver & MiniMap HUD settings
        vm1.updateScreensaverSettings {
            it.copy(
                masterEnabled = false,
                autoActivateEnabled = false,
                inactivityTimeoutSeconds = 120,
                isCustomTimeout = true,
                touchToDismiss = false,
                autoRestartTimer = false,
                displayMode = ScreensaverDisplayMode.MIRROR,
                autoOrientationEnabled = false,
                orientationLocked = true,
                lockedOrientationIsLandscape = true,
                keepScreenAwake = false,
                screensaverThemeMode = ScreensaverThemeMode.DARK,
                mirrorModePreferred = true,
                hudBrightness = 0.42f,
                miniMapVisible = false,
                miniMapOrientation = MiniMapOrientationMode.DIRECTION_UP,
                miniMapZoomLevel = 17.0f,
                miniMapStyle = MiniMapStyle.DARK
            )
        }

        // 6. Navigation Notifications, KM Stone Alerts, Widgets (+ instance override)
        vm1.updateNavigationNotificationSettings {
            it.copy(enabled = true, showSpeed = false, showOnLockScreen = false, liveNotificationEnabled = false)
        }
        vm1.updateKmStoneAlertSettings {
            it.copy(
                enabled = false,
                hapticFeedbackEnabled = false,
                vibrationPattern = KmVibrationPattern.TRIPLE_PULSE,
                vibrationIntensity = KmVibrationIntensity.MEDIUM,
                soundOption = KmAlertSoundOption.SOFT_ALERT,
                alertVolumePercent = 45,
                proximityThresholdMetres = 30,
                advanceWarningOption = KmAdvanceWarningOption.M_200,
                advanceWarningSoundEnabled = false
            )
        }
        vm1.updateWidgetSettings {
            it.copy(
                themeMode = WidgetThemeMode.LIGHT,
                backgroundStyle = WidgetBackgroundStyle.TRANSPARENT,
                accentColor = WidgetAccentColorPreset.AMBER,
                showSpeed = false,
                mapOrientation = MiniMapOrientationMode.NORTH_UP
            )
        }
        vm1.updateWidgetInstanceConfig(
            CtecsWidgetInstanceConfig(
                appWidgetId = 77,
                widgetType = CtecsWidgetType.NAVIGATION,
                themeMode = WidgetThemeMode.DARK,
                accentColor = WidgetAccentColorPreset.EMERALD,
                showLocation = false
            )
        )

        // 7. Chainage Calibration, Manual Override & Custom Chainages
        vm1.setManualChainage("196+494")
        vm1.addCustomChainagePreset("201+500")
        preferencesManager.setRouteCorrectionsEnabled(false)
        preferencesManager.saveRouteCorrectionPoint(
            ChainageCorrectionPoint(
                id = "cp1",
                routeId = "route_1",
                latitude = 29.855,
                longitude = 77.885,
                originalKmzMetres = 196_000L,
                correctedMetres = 196_120L,
                description = "KM 196 Ref"
            )
        )
        preferencesManager.saveCalibrationProfile(
            PermanentCalibrationProfile(
                profileId = "prof_1",
                profileName = "NH58 Permanent Profile",
                projectId = "NH-709AD",
                routeId = "route_1",
                originalFileName = "nh58.kmz",
                alignmentFingerprint = "fp_abc123",
                controlPoints = emptyList()
            )
        )

        // 8. Survey Map & External Navigation App Preferences
        vm1.setPreferredExternalNavAppId("com.mappls.app")
        vm1.updateSurveyMapPreferences(
            show200mLabels = false,
            showCentreline = false,
            showGpsTrack = false,
            showLaneTrack = false,
            showMarkers = false,
            mapType = "satellite"
        )

        awaitPersistenceDrain()

        // Simulate full app close & cold process restart
        PreferencesManager.clearMemoryCacheForProcessRestart()
        CtecsNavigationTelemetryHub.ensureInitialized(context, forceReload = true)
        val vm2 = MainViewModel(application)

        // Verify IMMEDIATELY (without awaiting any async Flow collectors) that all values restored cleanly
        assertEquals("dark", vm2.themeMode.value)
        assertEquals("dark", vm2.mapThemePreference.value)
        assertEquals("satellite", vm2.mapTypePreference.value)
        assertEquals("list", vm2.galleryViewMode.value)
        assertEquals("Chainage ascending", vm2.gallerySortOption.value)
        assertEquals("By Date", vm2.galleryGroupOption.value)

        assertEquals(customProfile, vm2.projectProfile.value)
        assertEquals(1, vm2.definedProjects.value.size)
        assertEquals("NH-709AD", vm2.definedProjects.value.first().name)
        assertEquals("align_lhs_2", vm2.selectedAlignmentId.value)

        assertEquals(customCamera, vm2.cameraSettings.value)
        assertTrue(vm2.isFlashOn.value)
        assertTrue(vm2.isFrontCamera.value)
        assertEquals(CaptureMode.VIDEO, vm2.captureMode.value)
        assertEquals(2.5f, vm2.currentZoomRatio.value, 0.001f)
        assertEquals(2, vm2.exposureCompensation.value)

        assertEquals(customOverlay, vm2.overlayConfig.value)
        assertEquals("Haridwar Bypass NH-58", vm2.currentLocationName.value)

        val restoredScreensaver = vm2.homeScreensaverSettings.value
        assertFalse(restoredScreensaver.masterEnabled)
        assertFalse(restoredScreensaver.autoActivateEnabled)
        assertEquals(120, restoredScreensaver.inactivityTimeoutSeconds)
        assertTrue(restoredScreensaver.isCustomTimeout)
        assertFalse(restoredScreensaver.touchToDismiss)
        assertFalse(restoredScreensaver.autoRestartTimer)
        assertEquals(ScreensaverDisplayMode.MIRROR, restoredScreensaver.displayMode)
        assertFalse(restoredScreensaver.autoOrientationEnabled)
        assertTrue(restoredScreensaver.orientationLocked)
        assertEquals(true, restoredScreensaver.lockedOrientationIsLandscape)
        assertFalse(restoredScreensaver.keepScreenAwake)
        assertEquals(ScreensaverThemeMode.DARK, restoredScreensaver.screensaverThemeMode)
        assertTrue(restoredScreensaver.mirrorModePreferred)
        assertEquals(0.42f, restoredScreensaver.hudBrightness, 0.001f)
        assertFalse(restoredScreensaver.miniMapVisible)
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, restoredScreensaver.miniMapOrientation)
        assertEquals(17.0f, restoredScreensaver.miniMapZoomLevel, 0.001f)
        assertEquals(MiniMapStyle.DARK, restoredScreensaver.miniMapStyle)

        assertTrue(vm2.navigationNotificationSettings.value.enabled)
        assertFalse(vm2.navigationNotificationSettings.value.showSpeed)
        assertEquals(KmVibrationPattern.TRIPLE_PULSE, vm2.kmStoneAlertSettings.value.vibrationPattern)
        assertEquals(45, vm2.kmStoneAlertSettings.value.alertVolumePercent)
        assertEquals(KmAdvanceWarningOption.M_200, vm2.kmStoneAlertSettings.value.advanceWarningOption)

        assertEquals(WidgetThemeMode.LIGHT, vm2.widgetSettings.value.themeMode)
        assertEquals(WidgetAccentColorPreset.AMBER, vm2.widgetSettings.value.accentColor)
        assertEquals(WidgetAccentColorPreset.EMERALD, vm2.widgetSettings.value.resolveForInstance(77).accentColor)

        assertEquals(Chainage(196_494L), vm2.manualChainage.value)
        assertEquals("196+494", vm2.manualChainageText.value)
        assertTrue(vm2.savedCustomChainages.value.contains("201+500"))
        assertFalse(vm2.isRouteCorrectionsEnabled.value)
        assertEquals(1, vm2.routeCorrectionsMap.value["route_1"]?.size)
        assertEquals(1, vm2.calibrationProfiles.value.size)

        assertEquals("com.mappls.app", vm2.preferredExternalNavAppId.value)
        assertFalse(vm2.surveyShow200mLabels.value)
        assertFalse(vm2.surveyShowCentreline.value)
        assertFalse(vm2.surveyShowGpsTrack.value)
        assertFalse(vm2.surveyShowLaneTrack.value)
        assertFalse(vm2.surveyShowMarkers.value)
        assertEquals("satellite", vm2.surveyMapType.value)

        // Verify CtecsNavigationTelemetryHub cold hydration matches persisted settings too
        assertEquals(customProfile, CtecsNavigationTelemetryHub.projectProfile.value)
        assertEquals(restoredScreensaver, CtecsNavigationTelemetryHub.screensaverSettings.value)
        assertEquals(vm2.widgetSettings.value, CtecsNavigationTelemetryHub.widgetSettings.value)
    }

    @Test
    fun partialUpdatesNeverOverwriteExistingSavedSettings() = runBlocking {
        val vm1 = MainViewModel(application)
        vm1.saveOverlayConfig(
            OverlayConfig(
                fontSizeSp = 32f,
                customTag = "BRIDGE_SPAN_4",
                showLogo = true,
                saveOriginalImage = true,
                showIndexNumber = true,
                timestampMarginMode = "Wide"
            )
        )
        vm1.saveCameraSettings(
            CameraSettings(
                photoResolution = "4000 x 3000",
                videoResolution = "3840 x 2160",
                recordAudio = false,
                jpegQuality = 98
            )
        )
        awaitPersistenceDrain()

        // Simulate reverse geocoding writing only lastDetectedLocationName
        preferencesManager.updateLastDetectedLocationName("Muzaffarnagar Bypass")
        awaitPersistenceDrain()

        // Cold restart and immediately invoke a single-field camera edit (cycleTimer)
        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm2 = MainViewModel(application)
        vm2.cycleTimer()
        awaitPersistenceDrain()

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm3 = MainViewModel(application)
        assertEquals(32f, vm3.overlayConfig.value.fontSizeSp, 0.001f)
        assertEquals("BRIDGE_SPAN_4", vm3.overlayConfig.value.customTag)
        assertTrue(vm3.overlayConfig.value.showLogo)
        assertTrue(vm3.overlayConfig.value.saveOriginalImage)
        assertTrue(vm3.overlayConfig.value.showIndexNumber)
        assertEquals("Wide", vm3.overlayConfig.value.timestampMarginMode)
        assertEquals("Muzaffarnagar Bypass", vm3.overlayConfig.value.lastDetectedLocationName)

        assertEquals("4000 x 3000", vm3.cameraSettings.value.photoResolution)
        assertEquals("3840 x 2160", vm3.cameraSettings.value.videoResolution)
        assertFalse(vm3.cameraSettings.value.recordAudio)
        assertEquals(98, vm3.cameraSettings.value.jpegQuality)
        assertEquals(CaptureTimer.SEC_3, vm3.cameraSettings.value.captureTimer)
    }

    @Test
    fun themeAndScreensaverSettingsSurviveRepeatedTogglesAndRestart() = runBlocking {
        val vm1 = MainViewModel(application)
        vm1.setThemeMode("light")
        vm1.setScreensaverActive(true)
        vm1.setScreensaverOrientationLocked(locked = true, currentIsLandscape = true)
        vm1.setScreensaverDisplayMode(ScreensaverDisplayMode.MIRROR)
        vm1.setScreensaverThemeMode(ScreensaverThemeMode.DARK)
        vm1.setScreensaverKeepScreenAwake(false)
        // Enter & exit screensaver repeatedly
        vm1.setScreensaverActive(false)
        vm1.setScreensaverActive(true)
        vm1.setScreensaverActive(false)
        awaitPersistenceDrain()

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm2 = MainViewModel(application)
        assertEquals("light", vm2.themeMode.value)
        assertTrue(vm2.homeScreensaverSettings.value.orientationLocked)
        assertEquals(true, vm2.homeScreensaverSettings.value.lockedOrientationIsLandscape)
        assertEquals(ScreensaverDisplayMode.MIRROR, vm2.homeScreensaverSettings.value.displayMode)
        assertEquals(ScreensaverThemeMode.DARK, vm2.homeScreensaverSettings.value.screensaverThemeMode)
        assertFalse(vm2.homeScreensaverSettings.value.keepScreenAwake)
    }

    @Test
    fun widgetDeletionPersistsAcrossReopenAndLegacyUpgradeMigrationPreservesValues() = runBlocking {
        val vm1 = MainViewModel(application)
        vm1.updateWidgetInstanceConfig(
            CtecsWidgetInstanceConfig(appWidgetId = 101, widgetType = CtecsWidgetType.COMPACT)
        )
        vm1.updateWidgetInstanceConfig(
            CtecsWidgetInstanceConfig(appWidgetId = 102, widgetType = CtecsWidgetType.DASHBOARD)
        )
        awaitPersistenceDrain()

        // Delete widget 101 via AppWidgetProvider callback
        CtecsCompactWidgetProvider().onDeleted(context, intArrayOf(101))
        awaitPersistenceDrain()

        // Seed legacy APK DataStore state: only camera_save_original_and_stamped (no save_original_image)
        // plus partial screensaver JSON missing newer fields
        preferencesManager.applyBackupEntriesTransaction(
            entries = listOf(
                TypedPrefEntry(
                    key = PreferencesManager.KEY_SAVE_ORIGINAL_AND_STAMPED.name,
                    type = PrefValueType.BOOLEAN,
                    value = true,
                    category = BackupCategory.PHOTO_VIDEO
                ),
                TypedPrefEntry(
                    key = PreferencesManager.KEY_HOME_SCREENSAVER_JSON.name,
                    type = PrefValueType.STRING,
                    value = """{"displayMode":"LANDSCAPE","keepScreenAwake":false}""",
                    category = BackupCategory.GENERAL
                )
            ),
            keysToRemove = setOf(PreferencesManager.KEY_SAVE_ORIGINAL_IMAGE.name)
        )
        awaitPersistenceDrain()

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm2 = MainViewModel(application)
        assertFalse(vm2.widgetSettings.value.instanceOverrides.containsKey(101))
        assertTrue(vm2.widgetSettings.value.instanceOverrides.containsKey(102))
        assertTrue(vm2.overlayConfig.value.saveOriginalImage)
        assertTrue(vm2.cameraSettings.value.saveOriginalAndStamped)
        assertEquals(ScreensaverDisplayMode.LANDSCAPE, vm2.homeScreensaverSettings.value.displayMode)
        assertFalse(vm2.homeScreensaverSettings.value.keepScreenAwake)
        // Missing newer keys in legacy JSON safely default without crashing
        assertTrue(vm2.homeScreensaverSettings.value.showChainage)
    }

    @Test
    fun explicitResetAllSettingsRestoresCleanDefaultsOnlyWhenInvoked() = runBlocking {
        val vm1 = MainViewModel(application)
        vm1.setThemeMode("dark")
        vm1.setPreferredExternalNavAppId("com.google.android.apps.maps")
        vm1.updateSurveyMapPreferences(show200mLabels = false, mapType = "satellite")
        vm1.saveCameraSettings(CameraSettings(jpegQuality = 70, videoFps = 60))
        vm1.selectAlignment("sub_1")
        awaitPersistenceDrain()

        // Verify persisted before reset
        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vmBeforeReset = MainViewModel(application)
        assertEquals("dark", vmBeforeReset.themeMode.value)
        assertEquals("com.google.android.apps.maps", vmBeforeReset.preferredExternalNavAppId.value)
        assertEquals("sub_1", vmBeforeReset.selectedAlignmentId.value)

        // Explicit reset
        vmBeforeReset.resetAllSettings()
        awaitPersistenceDrain()

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vmAfterReset = MainViewModel(application)
        assertEquals("system", vmAfterReset.themeMode.value)
        assertNull(vmAfterReset.preferredExternalNavAppId.value)
        assertNull(vmAfterReset.selectedAlignmentId.value)
        assertTrue(vmAfterReset.surveyShow200mLabels.value)
        assertEquals("standard", vmAfterReset.surveyMapType.value)
        assertEquals(92, vmAfterReset.cameraSettings.value.jpegQuality)
        assertEquals(30, vmAfterReset.cameraSettings.value.videoFps)
    }
}
