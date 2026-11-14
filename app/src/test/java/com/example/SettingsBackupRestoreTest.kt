package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupCategory
import com.example.backup.BackupManifest
import com.example.backup.BackupValidationStatus
import com.example.backup.PrefValueType
import com.example.backup.RestoreMode
import com.example.backup.SettingsBackupManager
import com.example.backup.TypedPrefEntry
import com.example.data.AppDatabase
import com.example.data.HighwayProjectEntity
import com.example.data.PhotoRecordEntity
import com.example.data.PreferencesManager
import com.example.data.RouteEntity
import com.example.model.CameraGridMode
import com.example.model.CameraMode
import com.example.model.CameraSettings
import com.example.model.CaptureTimer
import com.example.model.ChainageCorrectionPoint
import com.example.model.DefinedProject
import com.example.model.MiniMapCornerShape
import com.example.model.MiniMapSizePreset
import com.example.model.MiniMapStyleConfig
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.PermanentCalibrationProfile
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.VideoQualityOption
import com.example.ui.MainViewModel
import com.example.ui.settings.BackupRestoreContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsBackupRestoreTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var backupManager: SettingsBackupManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        preferencesManager = PreferencesManager(context)
        backupManager = SettingsBackupManager(context, preferencesManager)
        runBlocking {
            preferencesManager.resetAllSettings()
        }
        File(context.filesDir, "backups").deleteRecursively()
        File(context.filesDir, "custom_user_logo.png").delete()
        File(context.filesDir, "pdf_default_logo.png").delete()
        File(context.filesDir, "dpr_signature.png").delete()
    }

    @After
    fun tearDown() {
        runBlocking {
            preferencesManager.resetAllSettings()
        }
        File(context.filesDir, "backups").deleteRecursively()
    }

    private suspend fun populateRichSettingsFixture(includeUnknownKey: Boolean = false) {
        val logoFile = File(context.filesDir, "custom_user_logo.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4))
        }
        val pdfLogoFile = File(context.filesDir, "pdf_default_logo.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 5, 6, 7, 8))
        }
        val sigFile = File(context.filesDir, "dpr_signature.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 10, 11, 12))
        }

        preferencesManager.updateThemeMode("dark")
        preferencesManager.updateMapTheme("dark")
        preferencesManager.updateMapType("satellite")
        preferencesManager.updateGalleryPreferences("list", "Oldest first", "Route")

        preferencesManager.updateProjectProfile(
            ProjectProfile(
                highwayId = "NH-58 Express",
                packageSection = "Pkg-04 Muzaffarnagar",
                startChainage = "190+000",
                endChainage = "240+500",
                workType = "MM",
                roadSide = RoadSide.LHS,
                lane = "2L",
                activity = "DBM",
                subActivity = "Layer-1",
                customDescription = "Median widening",
                inspectorName = "R. Sharma",
                separator = "::"
            )
        )
        preferencesManager.updateDefinedProjects(
            listOf(
                DefinedProject("NH-58 Express", "Pkg-04", "MM", "190+000", "240+500"),
                DefinedProject("NH-44 Bypass", "Pkg-02", "RM", "10+000", "45+000")
            )
        )
        preferencesManager.updateCameraSettings(
            CameraSettings(
                captureTimer = CaptureTimer.SEC_3,
                gridMode = CameraGridMode.GRID_4X4,
                showHorizonLevel = false,
                captureSound = false,
                jpegQuality = 95,
                videoQuality = VideoQualityOption.UHD_4K,
                videoFps = 60,
                gpsUpdateIntervalSec = 2,
                cameraMode = CameraMode.HIGHWAY,
                customFilenamePrefix = "CTECS_QC_",
                photoResolution = "1920 x 1080",
                videoResolution = "1920 x 1080"
            )
        )
        preferencesManager.updateOverlayConfig(
            OverlayConfig(
                template = OverlayTemplate.QUALITY_CONTROL,
                showLogo = true,
                logoUri = logoFile.absolutePath,
                fontSizeSp = 28f,
                textColorHex = "#FFEB3B",
                photoPaddingTopDp = 24,
                videoPaddingBottomDp = 32,
                useSamePaddingForPhotoAndVideo = false,
                pdfLogoUri = pdfLogoFile.absolutePath,
                includePdfLogoDefault = true,
                pdfSignatureUri = sigFile.absolutePath,
                includePdfSignatureDefault = true,
                pdfDefaultPreparedBy = "Engr. K. Verma",
                photoMiniMap = MiniMapStyleConfig(
                    enabled = true,
                    sizePreset = MiniMapSizePreset.LARGE,
                    cornerShape = MiniMapCornerShape.SQUARE,
                    opacity = 0.85f,
                    routeColorHex = "#38BDF8"
                ),
                videoMiniMap = MiniMapStyleConfig(
                    enabled = true,
                    sizePreset = MiniMapSizePreset.MEDIUM,
                    cornerShape = MiniMapCornerShape.ROUNDED,
                    opacity = 0.72f,
                    routeColorHex = "#22C55E"
                )
            )
        )
        val cp1 = ChainageCorrectionPoint(
            id = "cp-1",
            routeId = "route-nh58",
            latitude = 29.500,
            longitude = 77.700,
            originalKmzMetres = 200_000L,
            correctedMetres = 200_120L,
            description = "KM 200 Verified",
            isEnabled = true
        )
        preferencesManager.saveCalibrationProfile(
            PermanentCalibrationProfile(
                profileId = "prof-nh58",
                profileName = "NH-58 Calibrated Main",
                projectId = "NH-58 Express",
                routeId = "route-nh58",
                originalFileName = "NH-58.kmz",
                alignmentFingerprint = "fp-nh58-001",
                controlPoints = listOf(cp1)
            )
        )
        preferencesManager.saveRouteCorrectionPointsList(
            routeId = "route-nh58",
            points = listOf(cp1)
        )
        preferencesManager.saveCustomChainagesList(listOf("200+120", "205+500"))
        val extraEntries = buildList {
            if (includeUnknownKey) {
                add(TypedPrefEntry("custom_future_pref_flag", PrefValueType.BOOLEAN, true, BackupCategory.OTHER))
            }
            add(TypedPrefEntry("auth_access_token", PrefValueType.STRING, "SECRET_DO_NOT_EXPORT", BackupCategory.OTHER))
        }
        preferencesManager.applyBackupEntriesTransaction(entries = extraEntries)
    }

    @Test
    fun `test 1 exporting all settings creates structured verified archive and strips secrets`() = runBlocking {
        populateRichSettingsFixture(includeUnknownKey = true)

        val result = backupManager.exportBackup()
        assertTrue("Export must succeed: ${result.errorMessage}", result.success)
        assertTrue(result.fileName.startsWith("CTECS_Backup_"))
        assertTrue(result.fileName.endsWith(".zip"))
        assertTrue(result.totalSettingsCount >= 90)
        assertEquals(1, result.calibrationProfileCount)
        assertEquals(1, result.controlPointCount)
        assertEquals(3, result.supportingFilesCount)

        val zipFile = File(result.localBackupFilePath!!)
        assertTrue(zipFile.exists())

        ZipFile(zipFile).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            assertNotNull(zip.getEntry("settings.json"))
            assertNotNull(zip.getEntry("calibration.json"))
            assertNotNull(zip.getEntry("files/custom_user_logo.png"))
            assertNotNull(zip.getEntry("files/pdf_default_logo.png"))
            assertNotNull(zip.getEntry("files/dpr_signature.png"))

            val settingsText = zip.getInputStream(zip.getEntry("settings.json")).bufferedReader().readText()
            assertTrue("Unknown non-secret pref must be dynamically exported", settingsText.contains("custom_future_pref_flag"))
            assertFalse("Secret/token keys must never be exported", settingsText.contains("SECRET_DO_NOT_EXPORT"))
            assertFalse("Secret key name must not appear", settingsText.contains("auth_access_token"))
        }
    }

    @Test
    fun `test 2 4 10 11 12 13 export and complete restore on same device persists across fresh PreferencesManager`() = runBlocking {
        populateRichSettingsFixture()
        val export = backupManager.exportBackup()
        assertTrue(export.success)
        val backupFile = File(export.localBackupFilePath!!)

        // Wipe everything + delete local files to simulate clean reset / reinstall
        preferencesManager.resetAllSettings()
        File(context.filesDir, "custom_user_logo.png").delete()
        File(context.filesDir, "pdf_default_logo.png").delete()
        File(context.filesDir, "dpr_signature.png").delete()

        val preview = backupManager.inspectBackupFile(backupFile)
        assertEquals(BackupValidationStatus.VALID, preview.validationStatus)
        assertTrue(preview.isRestorable)

        val report = backupManager.restoreFromPreview(
            preview = preview,
            mode = RestoreMode.COMPLETE,
            selectedCategories = BackupCategory.entries.toSet()
        )
        assertTrue("Restore must succeed: ${report.errorMessage}", report.success)
        assertFalse(report.rolledBack)
        assertEquals(1, report.restoredCalibrationProfilesCount)
        assertEquals(1, report.restoredControlPointsCount)
        assertEquals(3, report.restoredSupportingFilesCount)

        // Simulate app restart with a fresh PreferencesManager instance
        val restartedPrefs = PreferencesManager(context)

        // Test 12: Light/Dark theme
        assertEquals("dark", restartedPrefs.themeModeFlow.first())
        assertEquals("dark", restartedPrefs.mapThemeFlow.first())
        assertEquals("satellite", restartedPrefs.mapTypeFlow.first())

        // Test 11: Camera & Mini Map
        val cam = restartedPrefs.cameraSettingsFlow.first()
        assertEquals(CaptureTimer.SEC_3, cam.captureTimer)
        assertEquals(CameraGridMode.GRID_4X4, cam.gridMode)
        assertEquals(95, cam.jpegQuality)
        assertEquals("CTECS_QC_", cam.customFilenamePrefix)

        val overlay = restartedPrefs.overlayConfigFlow.first()
        assertEquals(OverlayTemplate.QUALITY_CONTROL, overlay.template)
        assertEquals(MiniMapCornerShape.SQUARE, overlay.photoMiniMap.cornerShape)
        assertEquals(MiniMapSizePreset.LARGE, overlay.photoMiniMap.sizePreset)
        assertEquals(MiniMapCornerShape.ROUNDED, overlay.videoMiniMap.cornerShape)
        assertEquals("#22C55E", overlay.videoMiniMap.routeColorHex)
        assertEquals("Engr. K. Verma", overlay.pdfDefaultPreparedBy)
        assertTrue(File(overlay.logoUri!!).exists())
        assertTrue(File(overlay.pdfLogoUri!!).exists())
        assertTrue(File(overlay.pdfSignatureUri!!).exists())

        // Test 10: Highway, Calibration Profiles, Control Points & Custom Chainages
        val project = restartedPrefs.projectProfileFlow.first()
        assertEquals("NH-58 Express", project.highwayId)
        assertEquals(RoadSide.LHS, project.roadSide)
        assertEquals("MM", project.workType)
        assertEquals(2, restartedPrefs.definedProjectsFlow.first().size)

        val profiles = restartedPrefs.calibrationProfilesFlow.first()
        assertEquals(1, profiles.size)
        assertEquals("NH-58 Calibrated Main", profiles.first().profileName)
        val controlPoints = restartedPrefs.routeCorrectionsFlow.first()["route-nh58"].orEmpty()
        assertEquals(1, controlPoints.size)
        assertEquals(200_120L, controlPoints.first().correctedMetres)
        assertEquals(listOf("200+120", "205+500"), restartedPrefs.savedCustomChainagesFlow.first())
    }

    @Test
    fun `test 3 cross-device restore adapts unsupported storage path resolution and out-of-range dimensions`() = runBlocking {
        populateRichSettingsFixture()
        preferencesManager.updateCameraSettings(
            CameraSettings(
                photoResolution = "7680 x 4320", // 8K unsupported on target device
                videoResolution = "3840 x 2160",
                jpegQuality = 95,
                customStoragePath = "/storage/DEAD-BEEF/SDCard/CTECS" // Invalid SD path on target
            )
        )
        preferencesManager.updateOverlayConfig(
            OverlayConfig(
                fontSizeSp = 150f, // Out of target range -> clamped
                photoPaddingTopDp = 400 // Out of range -> clamped
            )
        )

        val export = backupManager.exportBackup()
        assertTrue(export.success)

        // Reset before restoring on simulated different device (max 1920x1080)
        preferencesManager.resetAllSettings()
        val preview = backupManager.inspectBackupFile(File(export.localBackupFilePath!!))
        val report = backupManager.restoreFromPreview(
            preview = preview,
            mode = RestoreMode.COMPLETE,
            selectedCategories = BackupCategory.entries.toSet(),
            supportedPhotoResolutionsOverride = listOf("1920 x 1080", "1280 x 720"),
            supportedVideoResolutionsOverride = listOf("1920 x 1080", "1280 x 720")
        )

        assertTrue(report.success)
        assertTrue("Expected cross-device adaptations reported: ${report.adaptedSettings}", report.adaptedSettings.size >= 3)

        val restoredCam = preferencesManager.cameraSettingsFlow.first()
        assertEquals("/storage/emulated/0/DCIM/Camera", restoredCam.customStoragePath)
        assertEquals("1920 x 1080", restoredCam.photoResolution)
        assertEquals("1920 x 1080", restoredCam.videoResolution)

        val restoredOverlay = preferencesManager.overlayConfigFlow.first()
        assertEquals(64f, restoredOverlay.fontSizeSp, 0.01f)
        assertEquals(120, restoredOverlay.photoPaddingTopDp)
    }

    @Test
    fun `test 5 selective restore updates only selected categories and preserves others`() = runBlocking {
        populateRichSettingsFixture()
        val export = backupManager.exportBackup()
        assertTrue(export.success)
        val backupFile = File(export.localBackupFilePath!!)

        // Modify target device state after export
        preferencesManager.updateThemeMode("light")
        preferencesManager.updateProjectProfile(ProjectProfile(highwayId = "LOCAL-NH-99", roadSide = RoadSide.RHS))
        preferencesManager.updateCameraSettings(CameraSettings(jpegQuality = 65))

        // Select ONLY MINI_MAP and CAMERA
        val preview = backupManager.inspectBackupFile(backupFile)
        val report = backupManager.restoreFromPreview(
            preview = preview,
            mode = RestoreMode.SELECTIVE,
            selectedCategories = setOf(BackupCategory.MINI_MAP, BackupCategory.CAMERA)
        )

        assertTrue(report.success)
        assertEquals(setOf(BackupCategory.MINI_MAP, BackupCategory.CAMERA), report.restoredCategories)
        // Camera & Mini Map were restored
        assertEquals(CaptureTimer.SEC_3, preferencesManager.cameraSettingsFlow.first().captureTimer)
        assertEquals("satellite", preferencesManager.mapTypeFlow.first())
        // Theme (GENERAL) & Project (HIGHWAY_CUSTOMISATION) were NOT overwritten
        assertEquals("light", preferencesManager.themeModeFlow.first())
        assertEquals("LOCAL-NH-99", preferencesManager.projectProfileFlow.first().highwayId)
        assertEquals(RoadSide.RHS, preferencesManager.projectProfileFlow.first().roadSide)
    }

    @Test
    fun `test 6 9 corrupted backup and zip-slip archives are rejected without modifying settings`() = runBlocking {
        preferencesManager.updateThemeMode("dark")
        preferencesManager.updateProjectProfile(ProjectProfile(highwayId = "SAFE-PROJECT"))

        val export = backupManager.exportBackup()
        assertTrue(export.success)
        val validFile = File(export.localBackupFilePath!!)

        // Tamper settings.json inside ZIP so SHA-256 checksum fails
        val tamperedFile = File(context.cacheDir, "tampered.zip")
        ZipOutputStream(FileOutputStream(tamperedFile)).use { zos ->
            ZipFile(validFile).use { src ->
                val entries = src.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val bytes = if (e.name == "settings.json") {
                        (src.getInputStream(e).readBytes().toString(Charsets.UTF_8) + " ").toByteArray()
                    } else {
                        src.getInputStream(e).readBytes()
                    }
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }

        val tamperedPreview = backupManager.inspectBackupFile(tamperedFile)
        assertEquals(BackupValidationStatus.CORRUPTED_CHECKSUM, tamperedPreview.validationStatus)
        assertFalse(tamperedPreview.isRestorable)

        val failedRestore = backupManager.restoreFromPreview(tamperedPreview, RestoreMode.COMPLETE, emptySet())
        assertFalse(failedRestore.success)
        assertEquals("dark", preferencesManager.themeModeFlow.first())
        assertEquals("SAFE-PROJECT", preferencesManager.projectProfileFlow.first().highwayId)

        // Zip-Slip path traversal archive must also be rejected
        val zipSlipFile = File(context.cacheDir, "zipslip.zip")
        ZipOutputStream(FileOutputStream(zipSlipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        val zipSlipPreview = backupManager.inspectBackupFile(zipSlipFile)
        assertEquals(BackupValidationStatus.MALFORMED_ARCHIVE, zipSlipPreview.validationStatus)
        assertFalse(zipSlipPreview.isRestorable)
    }

    @Test
    fun `test 7 unsupported future version is rejected cleanly and older schema v0 migrates safely`() = runBlocking {
        preferencesManager.updateProjectProfile(ProjectProfile(highwayId = "KEEP-ME"))

        // Future version backup
        val futureZip = File(context.cacheDir, "future_v99.zip")
        val dummySettings = "{}".toByteArray()
        val futureManifest = BackupManifest(
            schemaVersion = 99,
            formatVersion = 1,
            createdAtIso = "2030-01-01T00:00:00Z",
            createdAtEpochMs = 1893456000000L,
            appVersionName = "9.9",
            appVersionCode = 99,
            deviceManufacturer = "Test",
            deviceModel = "FuturePhone",
            androidSdk = 40,
            totalSettingsCount = 0,
            categoryCounts = emptyMap(),
            calibrationProfileCount = 0,
            controlPointCount = 0,
            supportingFiles = emptyList(),
            entryChecksums = mapOf("settings.json" to backupManager.sha256Hex(dummySettings)),
            manifestIntegrityHash = backupManager.computeManifestIntegrityHash(
                99, "2030-01-01T00:00:00Z", mapOf("settings.json" to backupManager.sha256Hex(dummySettings))
            )
        ).toJson().toString().toByteArray()

        ZipOutputStream(FileOutputStream(futureZip)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json")); zos.write(futureManifest); zos.closeEntry()
            zos.putNextEntry(ZipEntry("settings.json")); zos.write(dummySettings); zos.closeEntry()
        }

        val futurePreview = backupManager.inspectBackupFile(futureZip)
        assertEquals(BackupValidationStatus.UNSUPPORTED_VERSION, futurePreview.validationStatus)
        assertFalse(futurePreview.isRestorable)
        assertEquals("KEEP-ME", preferencesManager.projectProfileFlow.first().highwayId)

        // Legacy schema v0 flat backup migrates cleanly
        val legacySettingsBytes = JSONObject().apply {
            put("highway_id", "MIGRATED-V0-HIGHWAY")
            put("theme_mode", "dark")
        }.toString().toByteArray()
        val v0Checksums = mapOf("settings.json" to backupManager.sha256Hex(legacySettingsBytes))
        val v0Manifest = BackupManifest(
            schemaVersion = 0,
            formatVersion = 1,
            createdAtIso = "2024-01-01T00:00:00Z",
            createdAtEpochMs = 1704067200000L,
            appVersionName = "0.9",
            appVersionCode = 1,
            deviceManufacturer = "Legacy",
            deviceModel = "OldPhone",
            androidSdk = 30,
            totalSettingsCount = 2,
            categoryCounts = mapOf("general" to 1, "highway_customisation" to 1),
            calibrationProfileCount = 0,
            controlPointCount = 0,
            supportingFiles = emptyList(),
            entryChecksums = v0Checksums,
            manifestIntegrityHash = backupManager.computeManifestIntegrityHash(0, "2024-01-01T00:00:00Z", v0Checksums)
        ).toJson().toString().toByteArray()

        val v0Zip = File(context.cacheDir, "legacy_v0.zip")
        ZipOutputStream(FileOutputStream(v0Zip)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json")); zos.write(v0Manifest); zos.closeEntry()
            zos.putNextEntry(ZipEntry("settings.json")); zos.write(legacySettingsBytes); zos.closeEntry()
        }

        val v0Preview = backupManager.inspectBackupFile(v0Zip)
        assertTrue(v0Preview.isRestorable)
        val v0Report = backupManager.restoreFromPreview(v0Preview, RestoreMode.COMPLETE, emptySet())
        assertTrue(v0Report.success)
        assertEquals("MIGRATED-V0-HIGHWAY", preferencesManager.projectProfileFlow.first().highwayId)
        assertEquals("dark", preferencesManager.themeModeFlow.first())
    }

    @Test
    fun `test 8 9 interrupted restore automatically rolls back settings and supporting files cleanly`() = runBlocking {
        val originalLogo = File(context.filesDir, "custom_user_logo.png").apply {
            writeBytes(byteArrayOf(11, 22, 33))
        }
        preferencesManager.updateThemeMode("light")
        preferencesManager.updateProjectProfile(ProjectProfile(highwayId = "ORIGINAL-BEFORE-RESTORE"))
        preferencesManager.updateOverlayConfig(OverlayConfig(logoUri = originalLogo.absolutePath))

        val tempBackupZip = File(context.cacheDir, "interrupted_source.zip")
        val differentPrefsLogo = File(context.filesDir, "pdf_default_logo.png").apply { writeBytes(byteArrayOf(44, 55, 66)) }
        preferencesManager.updateThemeMode("dark")
        preferencesManager.updateProjectProfile(ProjectProfile(highwayId = "NEW-SHOULD-ROLLBACK"))
        preferencesManager.updateOverlayConfig(OverlayConfig(logoUri = originalLogo.absolutePath, pdfLogoUri = differentPrefsLogo.absolutePath))
        val export = backupManager.exportBackup()
        File(export.localBackupFilePath!!).copyTo(tempBackupZip, overwrite = true)

        originalLogo.writeBytes(byteArrayOf(11, 22, 33))
        preferencesManager.updateThemeMode("light")
        preferencesManager.updateProjectProfile(ProjectProfile(highwayId = "ORIGINAL-BEFORE-RESTORE"))
        preferencesManager.updateOverlayConfig(OverlayConfig(logoUri = originalLogo.absolutePath, pdfLogoUri = null))

        val preview = backupManager.inspectBackupFile(tempBackupZip)
        val report = backupManager.restoreFromPreview(
            preview = preview,
            mode = RestoreMode.COMPLETE,
            selectedCategories = BackupCategory.entries.toSet(),
            onBeforeCommitHookForTest = {
                throw RuntimeException("Simulated sudden power loss during restore commit!")
            }
        )

        assertFalse(report.success)
        assertTrue("Rollback flag must be true when restore fails mid-flight", report.rolledBack)
        assertEquals("light", preferencesManager.themeModeFlow.first())
        assertEquals("ORIGINAL-BEFORE-RESTORE", preferencesManager.projectProfileFlow.first().highwayId)
        assertTrue(originalLogo.readBytes().contentEquals(byteArrayOf(11, 22, 33)))
    }

    @Test
    fun `test 14 existing photos videos Room projects KMZ files and control points are never deleted during backup or restore`() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val existingPhoto = File(context.filesDir, "sample_site_photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val existingKmz = File(context.filesDir, "NH58_alignment.kmz").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val insertedPhotoId = db.photoRecordDao().insertPhoto(
            PhotoRecordEntity(
                filePath = existingPhoto.absolutePath,
                fileName = "sample_site_photo.jpg",
                timestamp = 1000L,
                latitude = 29.5,
                longitude = 77.7,
                accuracy = 4.2f,
                chainageMetres = 200000L,
                highwayId = "NH-58",
                packageSection = "Pkg-1",
                roadSide = "LHS",
                lane = "2L",
                activity = "DBM"
            )
        )
        db.routeDao().insertRoute(
            RouteEntity(
                id = "route-keep",
                name = "NH58_alignment.kmz",
                fileName = "NH58_alignment.kmz",
                markerCount = 10,
                startChainageMetres = 0L,
                endChainageMetres = 10000L,
                minLat = 29.0,
                maxLat = 30.0,
                minLng = 77.0,
                maxLng = 78.0,
                localFilePath = existingKmz.absolutePath
            )
        )
        db.highwayProjectDao().insertProject(
            HighwayProjectEntity(id = "proj-keep", name = "Existing Project")
        )

        // Existing permanent control point for route-keep (NOT in backup!)
        preferencesManager.saveRouteCorrectionPoint(
            ChainageCorrectionPoint(
                id = "keep-cp-local",
                routeId = "route-keep",
                latitude = 29.1,
                longitude = 77.1,
                originalKmzMetres = 5000L,
                correctedMetres = 5050L,
                description = "Existing Verified Point",
                isEnabled = true
            )
        )

        val backupOnlyNh58 = run {
            preferencesManager.saveRouteCorrectionPointsList(
                "route-nh58",
                listOf(
                    ChainageCorrectionPoint(
                        id = "cp-nh58",
                        routeId = "route-nh58",
                        latitude = 29.5,
                        longitude = 77.7,
                        originalKmzMetres = 200000L,
                        correctedMetres = 200080L,
                        description = "NH58 Stone",
                        isEnabled = true
                    )
                )
            )
            val exp = backupManager.exportBackup()
            File(exp.localBackupFilePath!!)
        }

        val preview = backupManager.inspectBackupFile(backupOnlyNh58)
        val report = backupManager.restoreFromPreview(preview, RestoreMode.COMPLETE, emptySet())
        assertTrue(report.success)

        // Verify ALL media, Room records, KMZ file, and existing permanent control point remain untouched!
        assertTrue(existingPhoto.exists())
        assertTrue(existingKmz.exists())
        assertNotNull(db.photoRecordDao().getPhotoById(insertedPhotoId))
        assertNotNull(db.routeDao().getRouteById("route-keep"))
        assertNotNull(db.highwayProjectDao().getProjectById("proj-keep"))
        val allCorrections = preferencesManager.routeCorrectionsFlow.first()
        assertTrue(allCorrections["route-keep"]?.any { it.id == "keep-cp-local" } == true)
        assertTrue(allCorrections["route-nh58"]?.any { it.id == "cp-nh58" } == true)
    }

    @Test
    fun `backup history list share uri and explicit delete obey safety rules`() = runBlocking {
        populateRichSettingsFixture()
        val export = backupManager.exportBackup()
        assertTrue(export.success)

        val history = backupManager.listBackupHistory()
        assertEquals(1, history.size)
        assertEquals(export.fileName, history.first().fileName)
        assertEquals(BackupValidationStatus.VALID, history.first().validationStatus)

        val shareUri = backupManager.getShareUriForBackup(history.first().filePath)
        assertNotNull("Share URI via FileProvider must be generated", shareUri)

        assertFalse(backupManager.deleteBackupFile(File(context.filesDir, "custom_user_logo.png").absolutePath))
        assertTrue(backupManager.deleteBackupFile(history.first().filePath))
        assertTrue(backupManager.listBackupHistory().isEmpty())
    }

    @Test
    fun `ui renders Backup & Restore controls history and actions`() {
        runBlocking {
            populateRichSettingsFixture()
            backupManager.exportBackup()
        }

        val vm = MainViewModel(application)
        vm.refreshBackupHistory()

        composeTestRule.setContent {
            MaterialTheme {
                BackupRestoreContent(
                    viewModel = vm,
                    accentColor = Color(0xFF0284C7),
                    accentContentColor = Color.White
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("backup_export_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Last Backup Info").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restore Options").assertIsDisplayed()
        composeTestRule.onNodeWithTag("restore_mode_complete_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("restore_mode_selective_chip").assertIsDisplayed()
    }
}
