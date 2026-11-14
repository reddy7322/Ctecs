package com.example.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.camera.CameraResolutionHelper
import com.example.data.PreferencesManager
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.DefinedProject
import com.example.model.MiniMapStyleConfig
import com.example.model.PermanentCalibrationProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SettingsBackupManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        const val MAX_ARCHIVE_BYTES = 50L * 1024L * 1024L
        const val MAX_ENTRY_BYTES = 20L * 1024L * 1024L
        const val MAX_SUPPORTING_FILE_BYTES = 15L * 1024L * 1024L
        const val MAX_ZIP_ENTRIES = 64

        private const val ENTRY_MANIFEST = "manifest.json"
        private const val ENTRY_SETTINGS = "settings.json"
        private const val ENTRY_CALIBRATION = "calibration.json"
        private const val FILES_DIR_PREFIX = "files/"

        private val SENSITIVE_KEY_REGEX = Regex(
            "(token|password|secret|auth|api[_-]?key|credential|cookie|session|private[_-]?key)",
            RegexOption.IGNORE_CASE
        )

        private val SUPPORTING_FILE_KEYS = setOf(
            PreferencesManager.KEY_LOGO_URI.name,
            PreferencesManager.KEY_PDF_LOGO_URI.name,
            PreferencesManager.KEY_PDF_SIGNATURE_URI.name,
            PreferencesManager.KEY_CUSTOM_FONT_PATH.name
        )

        private val KNOWN_SUPPORTED_KEYS: Set<String> by lazy {
            setOf(
                // Highway Customisation
                PreferencesManager.KEY_HIGHWAY_ID.name,
                PreferencesManager.KEY_PACKAGE_SECTION.name,
                PreferencesManager.KEY_PROJECT_START_CHAINAGE.name,
                PreferencesManager.KEY_PROJECT_END_CHAINAGE.name,
                PreferencesManager.KEY_WORK_TYPE.name,
                PreferencesManager.KEY_ROAD_SIDE.name,
                PreferencesManager.KEY_LANE.name,
                PreferencesManager.KEY_ACTIVITY.name,
                PreferencesManager.KEY_SUB_ACTIVITY.name,
                PreferencesManager.KEY_DESCRIPTION.name,
                PreferencesManager.KEY_INSPECTOR.name,
                PreferencesManager.KEY_SEPARATOR.name,
                PreferencesManager.KEY_DEFINED_PROJECTS.name,
                PreferencesManager.KEY_ACTIVE_ROUTE_ID.name,
                // Timestamp & Watermark
                PreferencesManager.KEY_TEMPLATE.name,
                PreferencesManager.KEY_TIMESTAMP_FORMAT.name,
                PreferencesManager.KEY_COORDINATE_PRECISION.name,
                PreferencesManager.KEY_SHOW_TIMESTAMP.name,
                PreferencesManager.KEY_SHOW_COORDINATES.name,
                PreferencesManager.KEY_SHOW_CHAINAGE.name,
                PreferencesManager.KEY_SHOW_PROJECT_INFO.name,
                PreferencesManager.KEY_SHOW_COMPASS.name,
                PreferencesManager.KEY_SHOW_ALTITUDE.name,
                PreferencesManager.KEY_SHOW_ACCURACY.name,
                PreferencesManager.KEY_BURN_IN_TIMESTAMP.name,
                PreferencesManager.KEY_SHOW_SPEED_PREVIEW.name,
                PreferencesManager.KEY_INCLUDE_SPEED_CAPTURE.name,
                PreferencesManager.KEY_SHOW_DIRECTION_PREVIEW.name,
                PreferencesManager.KEY_INCLUDE_DIRECTION_CAPTURE.name,
                PreferencesManager.KEY_SHOW_LOGO.name,
                PreferencesManager.KEY_LOGO_PRESET.name,
                PreferencesManager.KEY_LOGO_URI.name,
                PreferencesManager.KEY_LOGO_POSITION.name,
                PreferencesManager.KEY_LOGO_SIZE_DP.name,
                PreferencesManager.KEY_LOGO_OPACITY.name,
                PreferencesManager.KEY_LOGO_SHAPE.name,
                PreferencesManager.KEY_LOGO_CORNER_RADIUS_DP.name,
                PreferencesManager.KEY_LOGO_BORDER_WIDTH_DP.name,
                PreferencesManager.KEY_LOGO_BORDER_COLOR_HEX.name,
                PreferencesManager.KEY_LOGO_MARGIN_X_DP.name,
                PreferencesManager.KEY_LOGO_MARGIN_Y_DP.name,
                PreferencesManager.KEY_LOGO_SCALE.name,
                PreferencesManager.KEY_LOGO_ROTATION.name,
                PreferencesManager.KEY_FONT_SIZE_SP.name,
                PreferencesManager.KEY_FONT_SIZE_PRESET.name,
                PreferencesManager.KEY_HAS_TEXT_SHADOW.name,
                PreferencesManager.KEY_TEXT_COLOR_HEX.name,
                PreferencesManager.KEY_TEXT_OPACITY.name,
                PreferencesManager.KEY_FONT_FAMILY.name,
                PreferencesManager.KEY_FONT_STYLE.name,
                PreferencesManager.KEY_BACKGROUND_STYLE.name,
                PreferencesManager.KEY_BACKGROUND_OPACITY.name,
                PreferencesManager.KEY_BACKGROUND_COLOR_HEX.name,
                PreferencesManager.KEY_WATERMARK_POSITION.name,
                PreferencesManager.KEY_TIME_POSITION.name,
                PreferencesManager.KEY_RESOLUTION_OPTION.name,
                PreferencesManager.KEY_LOCATION_ADDRESS_OPTION.name,
                PreferencesManager.KEY_CUSTOM_ADDRESS_TEXT.name,
                PreferencesManager.KEY_CUSTOM_TAG.name,
                PreferencesManager.KEY_GPS_ACCURACY_MARGIN_PRESET.name,
                PreferencesManager.KEY_CUSTOM_FONT_PATH.name,
                PreferencesManager.KEY_CUSTOM_FONT_NAME.name,
                PreferencesManager.KEY_TEXT_ALIGNMENT.name,
                PreferencesManager.KEY_LINE_SPACING_FACTOR.name,
                PreferencesManager.KEY_TEXT_STROKE_WIDTH_DP.name,
                PreferencesManager.KEY_SHOW_LOCATION_NAME.name,
                PreferencesManager.KEY_LAST_DETECTED_LOCATION_NAME.name,
                PreferencesManager.KEY_USE_SAME_PADDING_PHOTO_VIDEO.name,
                PreferencesManager.KEY_PHOTO_PADDING_TOP_DP.name,
                PreferencesManager.KEY_PHOTO_PADDING_BOTTOM_DP.name,
                PreferencesManager.KEY_PHOTO_PADDING_LEFT_DP.name,
                PreferencesManager.KEY_PHOTO_PADDING_RIGHT_DP.name,
                PreferencesManager.KEY_VIDEO_PADDING_TOP_DP.name,
                PreferencesManager.KEY_VIDEO_PADDING_BOTTOM_DP.name,
                PreferencesManager.KEY_VIDEO_PADDING_LEFT_DP.name,
                PreferencesManager.KEY_VIDEO_PADDING_RIGHT_DP.name,
                // Mini Map
                PreferencesManager.KEY_SHOW_MAP_INSET.name,
                PreferencesManager.KEY_PHOTO_MINI_MAP_JSON.name,
                PreferencesManager.KEY_VIDEO_MINI_MAP_JSON.name,
                PreferencesManager.KEY_MAP_THEME.name,
                PreferencesManager.KEY_MAP_TYPE.name,
                // DPR
                PreferencesManager.KEY_PDF_LOGO_URI.name,
                PreferencesManager.KEY_INCLUDE_PDF_LOGO_DEFAULT.name,
                PreferencesManager.KEY_PDF_SIGNATURE_URI.name,
                PreferencesManager.KEY_INCLUDE_PDF_SIGNATURE_DEFAULT.name,
                PreferencesManager.KEY_PDF_DEFAULT_PREPARED_BY.name,
                // Camera
                PreferencesManager.KEY_CAPTURE_TIMER.name,
                PreferencesManager.KEY_GRID_MODE.name,
                PreferencesManager.KEY_SHOW_HORIZON_LEVEL.name,
                PreferencesManager.KEY_PREVIEW_RATIO.name,
                PreferencesManager.KEY_CAPTURE_SOUND.name,
                PreferencesManager.KEY_VOLUME_SHUTTER.name,
                PreferencesManager.KEY_FRONT_MIRROR.name,
                PreferencesManager.KEY_EXPOSURE_COMP.name,
                PreferencesManager.KEY_CAMERA_MODE.name,
                PreferencesManager.KEY_TOUCH_TO_CAPTURE.name,
                PreferencesManager.KEY_ALWAYS_DISPLAY_ZOOM_SLIDER.name,
                PreferencesManager.KEY_BATTERY_SAVE_MODE.name,
                PreferencesManager.KEY_BATTERY_SAVE_BRIGHTNESS.name,
                PreferencesManager.KEY_EASY_TOGGLE_CAMERAS.name,
                PreferencesManager.KEY_WHITE_BALANCE.name,
                PreferencesManager.KEY_AUTO_ROTATE_UI.name,
                // Photo & Video
                PreferencesManager.KEY_JPEG_QUALITY.name,
                PreferencesManager.KEY_SAVE_ORIGINAL_AND_STAMPED.name,
                PreferencesManager.KEY_PRESERVE_EXIF.name,
                PreferencesManager.KEY_VIDEO_QUALITY.name,
                PreferencesManager.KEY_VIDEO_FPS.name,
                PreferencesManager.KEY_RECORD_AUDIO.name,
                PreferencesManager.KEY_VIDEO_SEGMENTATION.name,
                PreferencesManager.KEY_CUSTOM_FILENAME_PREFIX.name,
                PreferencesManager.KEY_FILENAME_FORMAT_PRESET.name,
                PreferencesManager.KEY_CUSTOM_STORAGE_PATH.name,
                PreferencesManager.KEY_PHOTO_RESOLUTION.name,
                PreferencesManager.KEY_VIDEO_RESOLUTION.name,
                PreferencesManager.KEY_PHOTO_ASPECT_RATIO.name,
                PreferencesManager.KEY_VIDEO_ASPECT_RATIO.name,
                // General & GPS
                PreferencesManager.KEY_THEME_MODE.name,
                PreferencesManager.KEY_GPS_INTERVAL.name,
                PreferencesManager.KEY_GPS_ACCURACY_THRESHOLD.name,
                PreferencesManager.KEY_ROUTE_RADIUS.name,
                PreferencesManager.KEY_COORD_FORMAT.name,
                PreferencesManager.KEY_IS_24H.name,
                PreferencesManager.KEY_SPEED_UNIT.name,
                // Gallery
                PreferencesManager.KEY_GALLERY_VIEW_MODE.name,
                PreferencesManager.KEY_GALLERY_SORT_OPTION.name,
                PreferencesManager.KEY_GALLERY_GROUP_OPTION.name,
                // Chainage & Calibration
                PreferencesManager.KEY_ROUTE_CORRECTIONS_JSON.name,
                PreferencesManager.KEY_ROUTE_CORRECTIONS_ENABLED.name,
                PreferencesManager.KEY_MANUAL_CHAINAGE_OVERRIDE.name,
                PreferencesManager.KEY_MANUAL_CHAINAGE_CUSTOM_TEXT.name,
                PreferencesManager.KEY_SAVED_CUSTOM_CHAINAGES.name,
                PreferencesManager.KEY_CHAINAGE_AUDIT_LOG_JSON.name,
                PreferencesManager.KEY_CALIBRATION_PROFILES_JSON.name
            )
        }
    }

    private val backupsDirectory: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    fun generateDefaultBackupFileName(epochMs: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
        return "CTECS_Backup_${fmt.format(Date(epochMs))}.zip"
    }

    fun categorizeKey(key: String): BackupCategory = when (key) {
        PreferencesManager.KEY_THEME_MODE.name,
        PreferencesManager.KEY_SHOW_LOCATION_NAME.name,
        PreferencesManager.KEY_LAST_DETECTED_LOCATION_NAME.name,
        PreferencesManager.KEY_LOCATION_ADDRESS_OPTION.name,
        PreferencesManager.KEY_CUSTOM_ADDRESS_TEXT.name,
        PreferencesManager.KEY_CUSTOM_TAG.name,
        PreferencesManager.KEY_GPS_ACCURACY_MARGIN_PRESET.name,
        PreferencesManager.KEY_GPS_INTERVAL.name,
        PreferencesManager.KEY_GPS_ACCURACY_THRESHOLD.name,
        PreferencesManager.KEY_ROUTE_RADIUS.name,
        PreferencesManager.KEY_COORD_FORMAT.name,
        PreferencesManager.KEY_IS_24H.name,
        PreferencesManager.KEY_SPEED_UNIT.name -> BackupCategory.GENERAL

        PreferencesManager.KEY_CAPTURE_TIMER.name,
        PreferencesManager.KEY_GRID_MODE.name,
        PreferencesManager.KEY_SHOW_HORIZON_LEVEL.name,
        PreferencesManager.KEY_PREVIEW_RATIO.name,
        PreferencesManager.KEY_CAPTURE_SOUND.name,
        PreferencesManager.KEY_VOLUME_SHUTTER.name,
        PreferencesManager.KEY_FRONT_MIRROR.name,
        PreferencesManager.KEY_EXPOSURE_COMP.name,
        PreferencesManager.KEY_CAMERA_MODE.name,
        PreferencesManager.KEY_TOUCH_TO_CAPTURE.name,
        PreferencesManager.KEY_ALWAYS_DISPLAY_ZOOM_SLIDER.name,
        PreferencesManager.KEY_BATTERY_SAVE_MODE.name,
        PreferencesManager.KEY_BATTERY_SAVE_BRIGHTNESS.name,
        PreferencesManager.KEY_EASY_TOGGLE_CAMERAS.name,
        PreferencesManager.KEY_WHITE_BALANCE.name,
        PreferencesManager.KEY_AUTO_ROTATE_UI.name -> BackupCategory.CAMERA

        PreferencesManager.KEY_PHOTO_RESOLUTION.name,
        PreferencesManager.KEY_VIDEO_RESOLUTION.name,
        PreferencesManager.KEY_PHOTO_ASPECT_RATIO.name,
        PreferencesManager.KEY_VIDEO_ASPECT_RATIO.name,
        PreferencesManager.KEY_RESOLUTION_OPTION.name,
        PreferencesManager.KEY_JPEG_QUALITY.name,
        PreferencesManager.KEY_SAVE_ORIGINAL_AND_STAMPED.name,
        PreferencesManager.KEY_PRESERVE_EXIF.name,
        PreferencesManager.KEY_VIDEO_QUALITY.name,
        PreferencesManager.KEY_VIDEO_FPS.name,
        PreferencesManager.KEY_RECORD_AUDIO.name,
        PreferencesManager.KEY_VIDEO_SEGMENTATION.name,
        PreferencesManager.KEY_CUSTOM_FILENAME_PREFIX.name,
        PreferencesManager.KEY_FILENAME_FORMAT_PRESET.name,
        PreferencesManager.KEY_CUSTOM_STORAGE_PATH.name -> BackupCategory.PHOTO_VIDEO

        PreferencesManager.KEY_TEMPLATE.name,
        PreferencesManager.KEY_TIMESTAMP_FORMAT.name,
        PreferencesManager.KEY_COORDINATE_PRECISION.name,
        PreferencesManager.KEY_SHOW_TIMESTAMP.name,
        PreferencesManager.KEY_SHOW_COORDINATES.name,
        PreferencesManager.KEY_SHOW_CHAINAGE.name,
        PreferencesManager.KEY_SHOW_PROJECT_INFO.name,
        PreferencesManager.KEY_SHOW_COMPASS.name,
        PreferencesManager.KEY_SHOW_ALTITUDE.name,
        PreferencesManager.KEY_SHOW_ACCURACY.name,
        PreferencesManager.KEY_BURN_IN_TIMESTAMP.name,
        PreferencesManager.KEY_SHOW_SPEED_PREVIEW.name,
        PreferencesManager.KEY_INCLUDE_SPEED_CAPTURE.name,
        PreferencesManager.KEY_SHOW_DIRECTION_PREVIEW.name,
        PreferencesManager.KEY_INCLUDE_DIRECTION_CAPTURE.name,
        PreferencesManager.KEY_SHOW_LOGO.name,
        PreferencesManager.KEY_LOGO_PRESET.name,
        PreferencesManager.KEY_LOGO_URI.name,
        PreferencesManager.KEY_LOGO_POSITION.name,
        PreferencesManager.KEY_LOGO_SIZE_DP.name,
        PreferencesManager.KEY_LOGO_OPACITY.name,
        PreferencesManager.KEY_LOGO_SHAPE.name,
        PreferencesManager.KEY_LOGO_CORNER_RADIUS_DP.name,
        PreferencesManager.KEY_LOGO_BORDER_WIDTH_DP.name,
        PreferencesManager.KEY_LOGO_BORDER_COLOR_HEX.name,
        PreferencesManager.KEY_LOGO_MARGIN_X_DP.name,
        PreferencesManager.KEY_LOGO_MARGIN_Y_DP.name,
        PreferencesManager.KEY_LOGO_SCALE.name,
        PreferencesManager.KEY_LOGO_ROTATION.name,
        PreferencesManager.KEY_FONT_SIZE_SP.name,
        PreferencesManager.KEY_FONT_SIZE_PRESET.name,
        PreferencesManager.KEY_HAS_TEXT_SHADOW.name,
        PreferencesManager.KEY_TEXT_COLOR_HEX.name,
        PreferencesManager.KEY_TEXT_OPACITY.name,
        PreferencesManager.KEY_FONT_FAMILY.name,
        PreferencesManager.KEY_FONT_STYLE.name,
        PreferencesManager.KEY_BACKGROUND_STYLE.name,
        PreferencesManager.KEY_BACKGROUND_OPACITY.name,
        PreferencesManager.KEY_BACKGROUND_COLOR_HEX.name,
        PreferencesManager.KEY_WATERMARK_POSITION.name,
        PreferencesManager.KEY_TIME_POSITION.name,
        PreferencesManager.KEY_CUSTOM_FONT_PATH.name,
        PreferencesManager.KEY_CUSTOM_FONT_NAME.name,
        PreferencesManager.KEY_TEXT_ALIGNMENT.name,
        PreferencesManager.KEY_LINE_SPACING_FACTOR.name,
        PreferencesManager.KEY_TEXT_STROKE_WIDTH_DP.name,
        PreferencesManager.KEY_USE_SAME_PADDING_PHOTO_VIDEO.name,
        PreferencesManager.KEY_PHOTO_PADDING_TOP_DP.name,
        PreferencesManager.KEY_PHOTO_PADDING_BOTTOM_DP.name,
        PreferencesManager.KEY_PHOTO_PADDING_LEFT_DP.name,
        PreferencesManager.KEY_PHOTO_PADDING_RIGHT_DP.name,
        PreferencesManager.KEY_VIDEO_PADDING_TOP_DP.name,
        PreferencesManager.KEY_VIDEO_PADDING_BOTTOM_DP.name,
        PreferencesManager.KEY_VIDEO_PADDING_LEFT_DP.name,
        PreferencesManager.KEY_VIDEO_PADDING_RIGHT_DP.name -> BackupCategory.TIMESTAMP_WATERMARK

        PreferencesManager.KEY_HIGHWAY_ID.name,
        PreferencesManager.KEY_PACKAGE_SECTION.name,
        PreferencesManager.KEY_PROJECT_START_CHAINAGE.name,
        PreferencesManager.KEY_PROJECT_END_CHAINAGE.name,
        PreferencesManager.KEY_WORK_TYPE.name,
        PreferencesManager.KEY_ROAD_SIDE.name,
        PreferencesManager.KEY_LANE.name,
        PreferencesManager.KEY_ACTIVITY.name,
        PreferencesManager.KEY_SUB_ACTIVITY.name,
        PreferencesManager.KEY_DESCRIPTION.name,
        PreferencesManager.KEY_INSPECTOR.name,
        PreferencesManager.KEY_SEPARATOR.name,
        PreferencesManager.KEY_DEFINED_PROJECTS.name,
        PreferencesManager.KEY_ACTIVE_ROUTE_ID.name -> BackupCategory.HIGHWAY_CUSTOMISATION

        PreferencesManager.KEY_ROUTE_CORRECTIONS_JSON.name,
        PreferencesManager.KEY_ROUTE_CORRECTIONS_ENABLED.name,
        PreferencesManager.KEY_MANUAL_CHAINAGE_OVERRIDE.name,
        PreferencesManager.KEY_MANUAL_CHAINAGE_CUSTOM_TEXT.name,
        PreferencesManager.KEY_SAVED_CUSTOM_CHAINAGES.name,
        PreferencesManager.KEY_CHAINAGE_AUDIT_LOG_JSON.name,
        PreferencesManager.KEY_CALIBRATION_PROFILES_JSON.name -> BackupCategory.CHAINAGE_CALIBRATION

        PreferencesManager.KEY_SHOW_MAP_INSET.name,
        PreferencesManager.KEY_PHOTO_MINI_MAP_JSON.name,
        PreferencesManager.KEY_VIDEO_MINI_MAP_JSON.name,
        PreferencesManager.KEY_MAP_THEME.name,
        PreferencesManager.KEY_MAP_TYPE.name -> BackupCategory.MINI_MAP

        PreferencesManager.KEY_GALLERY_VIEW_MODE.name,
        PreferencesManager.KEY_GALLERY_SORT_OPTION.name,
        PreferencesManager.KEY_GALLERY_GROUP_OPTION.name -> BackupCategory.GALLERY

        PreferencesManager.KEY_PDF_LOGO_URI.name,
        PreferencesManager.KEY_INCLUDE_PDF_LOGO_DEFAULT.name,
        PreferencesManager.KEY_PDF_SIGNATURE_URI.name,
        PreferencesManager.KEY_INCLUDE_PDF_SIGNATURE_DEFAULT.name,
        PreferencesManager.KEY_PDF_DEFAULT_PREPARED_BY.name -> BackupCategory.DPR

        else -> BackupCategory.OTHER
    }

    /**
     * Collects all persisted DataStore settings plus materialized effective defaults so every setting
     * across all categories is exported without missing untouched preferences or relying on a partial list.
     */
    suspend fun collectCompleteExportEntries(): List<TypedPrefEntry> {
        val collected = linkedMapOf<String, TypedPrefEntry>()

        fun putEntry(key: String, type: PrefValueType, value: Any) {
            if (SENSITIVE_KEY_REGEX.containsMatchIn(key)) return
            collected[key] = TypedPrefEntry(
                key = key,
                type = type,
                value = value,
                category = categorizeKey(key)
            )
        }

        // 1. Materialize effective domain models first so even untouched defaults are portable cross-device
        val project = preferencesManager.projectProfileFlow.first()
        val definedProjects = preferencesManager.definedProjectsFlow.first()
        val overlay = preferencesManager.overlayConfigFlow.first()
        val camera = preferencesManager.cameraSettingsFlow.first()
        val themeMode = preferencesManager.themeModeFlow.first()
        val mapTheme = preferencesManager.mapThemeFlow.first()
        val mapType = preferencesManager.mapTypeFlow.first()
        val galleryView = preferencesManager.galleryViewModeFlow.first()
        val gallerySort = preferencesManager.gallerySortOptionFlow.first()
        val galleryGroup = preferencesManager.galleryGroupOptionFlow.first()
        val correctionsEnabled = preferencesManager.routeCorrectionsEnabledFlow.first()
        val savedCustomChainages = preferencesManager.savedCustomChainagesFlow.first()
        val activeRouteId = preferencesManager.activeRouteIdFlow.first()

        putEntry(PreferencesManager.KEY_HIGHWAY_ID.name, PrefValueType.STRING, project.highwayId)
        putEntry(PreferencesManager.KEY_PACKAGE_SECTION.name, PrefValueType.STRING, project.packageSection)
        putEntry(PreferencesManager.KEY_PROJECT_START_CHAINAGE.name, PrefValueType.STRING, project.startChainage)
        putEntry(PreferencesManager.KEY_PROJECT_END_CHAINAGE.name, PrefValueType.STRING, project.endChainage)
        putEntry(PreferencesManager.KEY_WORK_TYPE.name, PrefValueType.STRING, project.workType)
        putEntry(PreferencesManager.KEY_ROAD_SIDE.name, PrefValueType.STRING, project.roadSide.label)
        putEntry(PreferencesManager.KEY_LANE.name, PrefValueType.STRING, project.lane)
        putEntry(PreferencesManager.KEY_ACTIVITY.name, PrefValueType.STRING, project.activity)
        putEntry(PreferencesManager.KEY_SUB_ACTIVITY.name, PrefValueType.STRING, project.subActivity)
        putEntry(PreferencesManager.KEY_DESCRIPTION.name, PrefValueType.STRING, project.customDescription)
        putEntry(PreferencesManager.KEY_INSPECTOR.name, PrefValueType.STRING, project.inspectorName)
        putEntry(PreferencesManager.KEY_SEPARATOR.name, PrefValueType.STRING, project.separator)
        putEntry(PreferencesManager.KEY_DEFINED_PROJECTS.name, PrefValueType.STRING, serializeDefinedProjects(definedProjects))
        if (!activeRouteId.isNullOrBlank()) {
            putEntry(PreferencesManager.KEY_ACTIVE_ROUTE_ID.name, PrefValueType.STRING, activeRouteId)
        }

        // Overlay & Timestamp & Mini Map & DPR
        putEntry(PreferencesManager.KEY_TEMPLATE.name, PrefValueType.STRING, overlay.template.name)
        putEntry(PreferencesManager.KEY_TIMESTAMP_FORMAT.name, PrefValueType.STRING, overlay.timestampFormat)
        putEntry(PreferencesManager.KEY_COORDINATE_PRECISION.name, PrefValueType.INT, overlay.coordinatePrecision)
        putEntry(PreferencesManager.KEY_SHOW_TIMESTAMP.name, PrefValueType.BOOLEAN, overlay.showTimestamp)
        putEntry(PreferencesManager.KEY_SHOW_COORDINATES.name, PrefValueType.BOOLEAN, overlay.showCoordinates)
        putEntry(PreferencesManager.KEY_SHOW_CHAINAGE.name, PrefValueType.BOOLEAN, overlay.showChainage)
        putEntry(PreferencesManager.KEY_SHOW_MAP_INSET.name, PrefValueType.BOOLEAN, overlay.showMapInset)
        putEntry(PreferencesManager.KEY_SHOW_PROJECT_INFO.name, PrefValueType.BOOLEAN, overlay.showProjectInfo)
        putEntry(PreferencesManager.KEY_SHOW_COMPASS.name, PrefValueType.BOOLEAN, overlay.showCompass)
        putEntry(PreferencesManager.KEY_SHOW_ALTITUDE.name, PrefValueType.BOOLEAN, overlay.showAltitude)
        putEntry(PreferencesManager.KEY_SHOW_ACCURACY.name, PrefValueType.BOOLEAN, overlay.showAccuracy)
        putEntry(PreferencesManager.KEY_BURN_IN_TIMESTAMP.name, PrefValueType.BOOLEAN, overlay.burnInTimestamp)
        putEntry(PreferencesManager.KEY_SHOW_SPEED_PREVIEW.name, PrefValueType.BOOLEAN, overlay.showSpeedOnPreview)
        putEntry(PreferencesManager.KEY_INCLUDE_SPEED_CAPTURE.name, PrefValueType.BOOLEAN, overlay.includeSpeedInCapture)
        putEntry(PreferencesManager.KEY_SHOW_DIRECTION_PREVIEW.name, PrefValueType.BOOLEAN, overlay.showDirectionOnPreview)
        putEntry(PreferencesManager.KEY_INCLUDE_DIRECTION_CAPTURE.name, PrefValueType.BOOLEAN, overlay.includeDirectionInCapture)
        putEntry(PreferencesManager.KEY_SHOW_LOGO.name, PrefValueType.BOOLEAN, overlay.showLogo)
        putEntry(PreferencesManager.KEY_LOGO_PRESET.name, PrefValueType.STRING, overlay.logoPreset)
        overlay.logoUri?.let { putEntry(PreferencesManager.KEY_LOGO_URI.name, PrefValueType.STRING, it) }
        putEntry(PreferencesManager.KEY_LOGO_POSITION.name, PrefValueType.STRING, overlay.logoPosition.name)
        putEntry(PreferencesManager.KEY_LOGO_SIZE_DP.name, PrefValueType.INT, overlay.logoSizeDp)
        putEntry(PreferencesManager.KEY_LOGO_OPACITY.name, PrefValueType.FLOAT, overlay.logoOpacity)
        putEntry(PreferencesManager.KEY_LOGO_SHAPE.name, PrefValueType.STRING, overlay.logoShape.name)
        putEntry(PreferencesManager.KEY_LOGO_CORNER_RADIUS_DP.name, PrefValueType.INT, overlay.logoCornerRadiusDp)
        putEntry(PreferencesManager.KEY_LOGO_BORDER_WIDTH_DP.name, PrefValueType.INT, overlay.logoBorderWidthDp)
        putEntry(PreferencesManager.KEY_LOGO_BORDER_COLOR_HEX.name, PrefValueType.STRING, overlay.logoBorderColorHex)
        putEntry(PreferencesManager.KEY_LOGO_MARGIN_X_DP.name, PrefValueType.INT, overlay.logoMarginXDp)
        putEntry(PreferencesManager.KEY_LOGO_MARGIN_Y_DP.name, PrefValueType.INT, overlay.logoMarginYDp)
        putEntry(PreferencesManager.KEY_LOGO_SCALE.name, PrefValueType.FLOAT, overlay.logoScale)
        putEntry(PreferencesManager.KEY_LOGO_ROTATION.name, PrefValueType.FLOAT, overlay.logoRotationDegrees)
        putEntry(PreferencesManager.KEY_FONT_SIZE_SP.name, PrefValueType.FLOAT, overlay.fontSizeSp)
        putEntry(PreferencesManager.KEY_FONT_SIZE_PRESET.name, PrefValueType.STRING, overlay.fontSizePreset)
        putEntry(PreferencesManager.KEY_HAS_TEXT_SHADOW.name, PrefValueType.BOOLEAN, overlay.hasTextShadow)
        putEntry(PreferencesManager.KEY_TEXT_COLOR_HEX.name, PrefValueType.STRING, overlay.textColorHex)
        putEntry(PreferencesManager.KEY_TEXT_OPACITY.name, PrefValueType.FLOAT, overlay.textOpacity)
        putEntry(PreferencesManager.KEY_FONT_FAMILY.name, PrefValueType.STRING, overlay.fontFamily)
        putEntry(PreferencesManager.KEY_FONT_STYLE.name, PrefValueType.STRING, overlay.fontStyle)
        putEntry(PreferencesManager.KEY_BACKGROUND_STYLE.name, PrefValueType.STRING, overlay.backgroundStyle)
        putEntry(PreferencesManager.KEY_BACKGROUND_OPACITY.name, PrefValueType.FLOAT, overlay.backgroundOpacity)
        putEntry(PreferencesManager.KEY_BACKGROUND_COLOR_HEX.name, PrefValueType.STRING, overlay.backgroundColorHex)
        putEntry(PreferencesManager.KEY_WATERMARK_POSITION.name, PrefValueType.STRING, overlay.watermarkPosition)
        putEntry(PreferencesManager.KEY_TIME_POSITION.name, PrefValueType.STRING, overlay.timePosition)
        putEntry(PreferencesManager.KEY_RESOLUTION_OPTION.name, PrefValueType.STRING, overlay.resolutionOption)
        putEntry(PreferencesManager.KEY_LOCATION_ADDRESS_OPTION.name, PrefValueType.STRING, overlay.locationAddressOption)
        putEntry(PreferencesManager.KEY_CUSTOM_ADDRESS_TEXT.name, PrefValueType.STRING, overlay.customAddressText)
        putEntry(PreferencesManager.KEY_CUSTOM_TAG.name, PrefValueType.STRING, overlay.customTag)
        putEntry(PreferencesManager.KEY_GPS_ACCURACY_MARGIN_PRESET.name, PrefValueType.STRING, overlay.gpsAccuracyMarginPreset)
        overlay.customFontPath?.let { putEntry(PreferencesManager.KEY_CUSTOM_FONT_PATH.name, PrefValueType.STRING, it) }
        overlay.customFontName?.let { putEntry(PreferencesManager.KEY_CUSTOM_FONT_NAME.name, PrefValueType.STRING, it) }
        putEntry(PreferencesManager.KEY_TEXT_ALIGNMENT.name, PrefValueType.STRING, overlay.textAlignment)
        putEntry(PreferencesManager.KEY_LINE_SPACING_FACTOR.name, PrefValueType.FLOAT, overlay.lineSpacingFactor)
        putEntry(PreferencesManager.KEY_TEXT_STROKE_WIDTH_DP.name, PrefValueType.FLOAT, overlay.textStrokeWidthDp)
        putEntry(PreferencesManager.KEY_SHOW_LOCATION_NAME.name, PrefValueType.BOOLEAN, overlay.showLocationName)
        putEntry(PreferencesManager.KEY_LAST_DETECTED_LOCATION_NAME.name, PrefValueType.STRING, overlay.lastDetectedLocationName)
        putEntry(PreferencesManager.KEY_USE_SAME_PADDING_PHOTO_VIDEO.name, PrefValueType.BOOLEAN, overlay.useSamePaddingForPhotoAndVideo)
        putEntry(PreferencesManager.KEY_PHOTO_PADDING_TOP_DP.name, PrefValueType.INT, overlay.photoPaddingTopDp)
        putEntry(PreferencesManager.KEY_PHOTO_PADDING_BOTTOM_DP.name, PrefValueType.INT, overlay.photoPaddingBottomDp)
        putEntry(PreferencesManager.KEY_PHOTO_PADDING_LEFT_DP.name, PrefValueType.INT, overlay.photoPaddingLeftDp)
        putEntry(PreferencesManager.KEY_PHOTO_PADDING_RIGHT_DP.name, PrefValueType.INT, overlay.photoPaddingRightDp)
        putEntry(PreferencesManager.KEY_VIDEO_PADDING_TOP_DP.name, PrefValueType.INT, overlay.videoPaddingTopDp)
        putEntry(PreferencesManager.KEY_VIDEO_PADDING_BOTTOM_DP.name, PrefValueType.INT, overlay.videoPaddingBottomDp)
        putEntry(PreferencesManager.KEY_VIDEO_PADDING_LEFT_DP.name, PrefValueType.INT, overlay.videoPaddingLeftDp)
        putEntry(PreferencesManager.KEY_VIDEO_PADDING_RIGHT_DP.name, PrefValueType.INT, overlay.videoPaddingRightDp)
        overlay.pdfLogoUri?.let { putEntry(PreferencesManager.KEY_PDF_LOGO_URI.name, PrefValueType.STRING, it) }
        putEntry(PreferencesManager.KEY_INCLUDE_PDF_LOGO_DEFAULT.name, PrefValueType.BOOLEAN, overlay.includePdfLogoDefault)
        overlay.pdfSignatureUri?.let { putEntry(PreferencesManager.KEY_PDF_SIGNATURE_URI.name, PrefValueType.STRING, it) }
        putEntry(PreferencesManager.KEY_INCLUDE_PDF_SIGNATURE_DEFAULT.name, PrefValueType.BOOLEAN, overlay.includePdfSignatureDefault)
        putEntry(PreferencesManager.KEY_PDF_DEFAULT_PREPARED_BY.name, PrefValueType.STRING, overlay.pdfDefaultPreparedBy)
        putEntry(PreferencesManager.KEY_PHOTO_MINI_MAP_JSON.name, PrefValueType.STRING, overlay.photoMiniMap.toJsonString())
        putEntry(PreferencesManager.KEY_VIDEO_MINI_MAP_JSON.name, PrefValueType.STRING, overlay.videoMiniMap.toJsonString())

        // Camera & Photo/Video
        putEntry(PreferencesManager.KEY_CAPTURE_TIMER.name, PrefValueType.INT, camera.captureTimer.seconds)
        putEntry(PreferencesManager.KEY_GRID_MODE.name, PrefValueType.STRING, camera.gridMode.name)
        putEntry(PreferencesManager.KEY_SHOW_HORIZON_LEVEL.name, PrefValueType.BOOLEAN, camera.showHorizonLevel)
        putEntry(PreferencesManager.KEY_PREVIEW_RATIO.name, PrefValueType.STRING, camera.previewRatio.name)
        putEntry(PreferencesManager.KEY_CAPTURE_SOUND.name, PrefValueType.BOOLEAN, camera.captureSound)
        putEntry(PreferencesManager.KEY_VOLUME_SHUTTER.name, PrefValueType.BOOLEAN, camera.volumeButtonShutter)
        putEntry(PreferencesManager.KEY_FRONT_MIRROR.name, PrefValueType.BOOLEAN, camera.frontCameraMirror)
        putEntry(PreferencesManager.KEY_JPEG_QUALITY.name, PrefValueType.INT, camera.jpegQuality)
        putEntry(PreferencesManager.KEY_SAVE_ORIGINAL_AND_STAMPED.name, PrefValueType.BOOLEAN, camera.saveOriginalAndStamped)
        putEntry(PreferencesManager.KEY_PRESERVE_EXIF.name, PrefValueType.BOOLEAN, camera.preserveExif)
        putEntry(PreferencesManager.KEY_VIDEO_QUALITY.name, PrefValueType.STRING, camera.videoQuality.name)
        putEntry(PreferencesManager.KEY_VIDEO_FPS.name, PrefValueType.INT, camera.videoFps)
        putEntry(PreferencesManager.KEY_RECORD_AUDIO.name, PrefValueType.BOOLEAN, camera.recordAudio)
        putEntry(PreferencesManager.KEY_GPS_INTERVAL.name, PrefValueType.INT, camera.gpsUpdateIntervalSec)
        putEntry(PreferencesManager.KEY_GPS_ACCURACY_THRESHOLD.name, PrefValueType.FLOAT, camera.gpsAccuracyThresholdMeters)
        putEntry(PreferencesManager.KEY_ROUTE_RADIUS.name, PrefValueType.FLOAT, camera.routeMatchingRadiusMeters.toFloat())
        putEntry(PreferencesManager.KEY_COORD_FORMAT.name, PrefValueType.STRING, camera.coordinateFormat.name)
        putEntry(PreferencesManager.KEY_IS_24H.name, PrefValueType.BOOLEAN, camera.is24HourFormat)
        putEntry(PreferencesManager.KEY_EXPOSURE_COMP.name, PrefValueType.INT, camera.exposureCompensationStep)
        putEntry(PreferencesManager.KEY_CAMERA_MODE.name, PrefValueType.STRING, camera.cameraMode.name)
        putEntry(PreferencesManager.KEY_SPEED_UNIT.name, PrefValueType.STRING, camera.speedUnit.name)
        putEntry(PreferencesManager.KEY_TOUCH_TO_CAPTURE.name, PrefValueType.BOOLEAN, camera.touchToCapture)
        putEntry(PreferencesManager.KEY_ALWAYS_DISPLAY_ZOOM_SLIDER.name, PrefValueType.BOOLEAN, camera.alwaysDisplayZoomSlider)
        putEntry(PreferencesManager.KEY_BATTERY_SAVE_MODE.name, PrefValueType.BOOLEAN, camera.batterySaveMode)
        putEntry(PreferencesManager.KEY_BATTERY_SAVE_BRIGHTNESS.name, PrefValueType.FLOAT, camera.batterySaveBrightness)
        putEntry(PreferencesManager.KEY_EASY_TOGGLE_CAMERAS.name, PrefValueType.BOOLEAN, camera.easyToggleCameras)
        putEntry(PreferencesManager.KEY_WHITE_BALANCE.name, PrefValueType.STRING, camera.whiteBalance)
        putEntry(PreferencesManager.KEY_VIDEO_SEGMENTATION.name, PrefValueType.STRING, camera.videoSegmentation)
        putEntry(PreferencesManager.KEY_CUSTOM_FILENAME_PREFIX.name, PrefValueType.STRING, camera.customFilenamePrefix)
        putEntry(PreferencesManager.KEY_FILENAME_FORMAT_PRESET.name, PrefValueType.STRING, camera.filenameFormatPreset)
        putEntry(PreferencesManager.KEY_CUSTOM_STORAGE_PATH.name, PrefValueType.STRING, camera.customStoragePath)
        putEntry(PreferencesManager.KEY_AUTO_ROTATE_UI.name, PrefValueType.BOOLEAN, camera.autoRotateUI)
        putEntry(PreferencesManager.KEY_PHOTO_RESOLUTION.name, PrefValueType.STRING, camera.photoResolution)
        putEntry(PreferencesManager.KEY_VIDEO_RESOLUTION.name, PrefValueType.STRING, camera.videoResolution)
        putEntry(PreferencesManager.KEY_PHOTO_ASPECT_RATIO.name, PrefValueType.STRING, camera.photoAspectRatio)
        putEntry(PreferencesManager.KEY_VIDEO_ASPECT_RATIO.name, PrefValueType.STRING, camera.videoAspectRatio)

        // Theme, Map Theme/Type & Gallery
        putEntry(PreferencesManager.KEY_THEME_MODE.name, PrefValueType.STRING, themeMode)
        putEntry(PreferencesManager.KEY_MAP_THEME.name, PrefValueType.STRING, mapTheme)
        putEntry(PreferencesManager.KEY_MAP_TYPE.name, PrefValueType.STRING, mapType)
        putEntry(PreferencesManager.KEY_GALLERY_VIEW_MODE.name, PrefValueType.STRING, galleryView)
        putEntry(PreferencesManager.KEY_GALLERY_SORT_OPTION.name, PrefValueType.STRING, gallerySort)
        putEntry(PreferencesManager.KEY_GALLERY_GROUP_OPTION.name, PrefValueType.STRING, galleryGroup)

        // Calibration defaults
        putEntry(PreferencesManager.KEY_ROUTE_CORRECTIONS_ENABLED.name, PrefValueType.BOOLEAN, correctionsEnabled)
        if (savedCustomChainages.isNotEmpty()) {
            putEntry(PreferencesManager.KEY_SAVED_CUSTOM_CHAINAGES.name, PrefValueType.STRING, savedCustomChainages.joinToString("||"))
        }

        // 2. Overlay EVERY live persisted key directly from ctecs_user_preferences DataStore dynamically
        val rawSnapshot = preferencesManager.readRawPreferencesSnapshot()
        rawSnapshot.forEach { (key, typedPair) ->
            putEntry(key, typedPair.first, typedPair.second)
        }

        return collected.values.toList()
    }

    suspend fun exportBackup(
        destinationUri: Uri? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): BackupExportResult = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val fileName = resolveUniqueBackupFileName(nowMs)
        val tempFile = File(backupsDirectory, "$fileName.tmp")
        val finalLocalFile = File(backupsDirectory, fileName)

        try {
            onProgress?.invoke(0.15f, "Inspecting application settings...")
            val entries = collectCompleteExportEntries()
            val profiles = preferencesManager.calibrationProfilesFlow.first()
            val corrections = preferencesManager.routeCorrectionsFlow.first()
            val correctionsEnabled = preferencesManager.routeCorrectionsEnabledFlow.first()
            val savedChainages = preferencesManager.savedCustomChainagesFlow.first()
            val manualText = preferencesManager.manualChainageTextFlow.first()
            val manualChainage = preferencesManager.manualChainageOverrideFlow.first()

            onProgress?.invoke(0.35f, "Packaging settings & calibration data...")
            val settingsJsonBytes = buildSettingsJson(entries).toString(2).toByteArray(Charsets.UTF_8)
            val calibrationJsonBytes = buildCalibrationJson(
                correctionsEnabled = correctionsEnabled,
                profiles = profiles,
                corrections = corrections,
                savedCustomChainages = savedChainages,
                manualChainageOverride = manualChainage?.format(),
                manualChainageCustomText = manualText
            ).toString(2).toByteArray(Charsets.UTF_8)

            val supportingPayloads = linkedMapOf<String, ByteArray>()
            val supportingSpecs = mutableListOf<BackupSupportingFile>()

            onProgress?.invoke(0.55f, "Verifying supporting logos & fonts...")
            entries.filter { it.key in SUPPORTING_FILE_KEYS && it.type == PrefValueType.STRING }.forEach { entry ->
                val rawPath = entry.value.toString().trim()
                if (rawPath.isNotBlank()) {
                    val file = File(rawPath)
                    if (file.exists() && file.isFile && file.length() in 1L..MAX_SUPPORTING_FILE_BYTES) {
                        val safeBase = sanitizeSupportFileName(entry.key, file.name)
                        val zipPath = "$FILES_DIR_PREFIX$safeBase"
                        val bytes = file.readBytes()
                        val sha = sha256Hex(bytes)
                        supportingPayloads[zipPath] = bytes
                        supportingSpecs.add(
                            BackupSupportingFile(
                                zipEntryPath = zipPath,
                                prefKey = entry.key,
                                originalFileName = file.name,
                                sha256 = sha,
                                sizeBytes = bytes.size.toLong()
                            )
                        )
                    }
                }
            }

            val entryChecksums = linkedMapOf<String, String>()
            entryChecksums[ENTRY_SETTINGS] = sha256Hex(settingsJsonBytes)
            entryChecksums[ENTRY_CALIBRATION] = sha256Hex(calibrationJsonBytes)
            supportingPayloads.forEach { (zipPath, bytes) ->
                entryChecksums[zipPath] = sha256Hex(bytes)
            }

            val categoryCounts = entries.groupingBy { it.category.id }.eachCount()
            val controlPointCount = corrections.values.sumOf { it.size }
            val createdIso = formatIsoTimestamp(nowMs)
            val integrityHash = computeManifestIntegrityHash(
                schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
                createdAtIso = createdIso,
                entryChecksums = entryChecksums
            )

            val manifest = BackupManifest(
                createdAtIso = createdIso,
                createdAtEpochMs = nowMs,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                deviceManufacturer = Build.MANUFACTURER ?: "Android",
                deviceModel = Build.MODEL ?: "Device",
                androidSdk = Build.VERSION.SDK_INT,
                totalSettingsCount = entries.size,
                categoryCounts = categoryCounts,
                calibrationProfileCount = profiles.size,
                controlPointCount = controlPointCount,
                supportingFiles = supportingSpecs,
                entryChecksums = entryChecksums,
                manifestIntegrityHash = integrityHash
            )
            val manifestBytes = manifest.toJson().toString(2).toByteArray(Charsets.UTF_8)

            onProgress?.invoke(0.75f, "Writing encrypted checksum archive...")
            ZipOutputStream(FileOutputStream(tempFile).buffered()).use { zos ->
                writeZipEntry(zos, ENTRY_MANIFEST, manifestBytes)
                writeZipEntry(zos, ENTRY_SETTINGS, settingsJsonBytes)
                writeZipEntry(zos, ENTRY_CALIBRATION, calibrationJsonBytes)
                supportingPayloads.forEach { (zipPath, bytes) ->
                    writeZipEntry(zos, zipPath, bytes)
                }
            }

            onProgress?.invoke(0.88f, "Validating exported backup integrity...")
            val validation = inspectBackupFile(tempFile)
            if (!validation.isRestorable) {
                tempFile.delete()
                return@withContext BackupExportResult(
                    success = false,
                    fileName = fileName,
                    errorMessage = "Backup validation failed before write: ${validation.validationMessage}"
                )
            }

            if (finalLocalFile.exists()) {
                finalLocalFile.delete()
            }
            if (!tempFile.renameTo(finalLocalFile)) {
                tempFile.copyTo(finalLocalFile, overwrite = true)
                tempFile.delete()
            }

            if (destinationUri != null) {
                onProgress?.invoke(0.95f, "Saving to selected device location...")
                val output = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("Unable to open destination file for writing")
                output.use { outStream ->
                    FileInputStream(finalLocalFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }

            onProgress?.invoke(1.0f, "Backup completed & verified")
            BackupExportResult(
                success = true,
                fileName = fileName,
                localBackupFilePath = finalLocalFile.absolutePath,
                externalUriString = destinationUri?.toString(),
                sizeBytes = finalLocalFile.length(),
                totalSettingsCount = entries.size,
                calibrationProfileCount = profiles.size,
                controlPointCount = controlPointCount,
                supportingFilesCount = supportingSpecs.size
            )
        } catch (e: Exception) {
            tempFile.delete()
            BackupExportResult(
                success = false,
                fileName = fileName,
                errorMessage = e.message ?: "Backup export failed"
            )
        }
    }

    suspend fun inspectBackupUri(uri: Uri): BackupPreviewInfo = withContext(Dispatchers.IO) {
        val label = resolveUriDisplayName(uri) ?: "CTECS_Backup.zip"
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupPreviewInfo(
                    sourceLabel = label,
                    sourceUriString = uri.toString(),
                    validationStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                    validationMessage = "Cannot open selected backup file."
                )
            val bytes = stream.use { readBoundedBytes(it, MAX_ARCHIVE_BYTES) }
                ?: return@withContext BackupPreviewInfo(
                    sourceLabel = label,
                    sourceUriString = uri.toString(),
                    validationStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                    validationMessage = "Backup file exceeds maximum supported size (50 MB)."
                )
            inspectBackupBytes(
                archiveBytes = bytes,
                sourceLabel = label,
                sourceUriString = uri.toString(),
                sourceFilePath = null
            )
        } catch (e: Exception) {
            BackupPreviewInfo(
                sourceLabel = label,
                sourceUriString = uri.toString(),
                validationStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                validationMessage = "Failed to inspect backup: ${e.message ?: "Unreadable archive"}"
            )
        }
    }

    suspend fun inspectBackupFile(file: File): BackupPreviewInfo = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            return@withContext BackupPreviewInfo(
                sourceLabel = file.name,
                sourceFilePath = file.absolutePath,
                validationStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                validationMessage = "Backup file not found."
            )
        }
        try {
            if (file.length() > MAX_ARCHIVE_BYTES) {
                return@withContext BackupPreviewInfo(
                    sourceLabel = file.name,
                    sourceFilePath = file.absolutePath,
                    validationStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                    validationMessage = "Backup file exceeds maximum supported size (50 MB)."
                )
            }
            val bytes = file.readBytes()
            inspectBackupBytes(
                archiveBytes = bytes,
                sourceLabel = file.name,
                sourceUriString = null,
                sourceFilePath = file.absolutePath
            )
        } catch (e: Exception) {
            BackupPreviewInfo(
                sourceLabel = file.name,
                sourceFilePath = file.absolutePath,
                validationStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                validationMessage = "Corrupted archive: ${e.message}"
            )
        }
    }

    private suspend fun inspectBackupBytes(
        archiveBytes: ByteArray,
        sourceLabel: String,
        sourceUriString: String?,
        sourceFilePath: String?
    ): BackupPreviewInfo {
        val parsed = readAndVerifyZipPayload(archiveBytes)
        if (parsed.errorStatus != null) {
            return BackupPreviewInfo(
                sourceLabel = sourceLabel,
                sourceUriString = sourceUriString,
                sourceFilePath = sourceFilePath,
                manifest = parsed.manifest,
                validationStatus = parsed.errorStatus,
                validationMessage = parsed.errorMessage ?: "Invalid backup archive",
                archiveSizeBytes = archiveBytes.size.toLong()
            )
        }

        val entries = parsed.entries
        val availableCats = entries.map { it.category }.toSet()
        val warnings = mutableListOf<String>()
        val unsupportedKeys = mutableListOf<String>()
        val adaptationPreview = mutableListOf<String>()

        if (parsed.migratedFromLegacySchema) {
            warnings.add("Migrated from older backup schema cleanly.")
        }
        entries.forEach { entry ->
            if (entry.key !in KNOWN_SUPPORTED_KEYS) {
                unsupportedKeys.add(entry.key)
            }
        }
        if (unsupportedKeys.isNotEmpty()) {
            warnings.add("${unsupportedKeys.size} unrecognized setting(s) will be handled safely.")
        }

        val manifest = parsed.manifest!!
        if (manifest.deviceModel.isNotBlank() && !manifest.deviceModel.equals(Build.MODEL, ignoreCase = true)) {
            warnings.add("Cross-device backup from ${manifest.deviceModel} — device-specific paths/resolutions will be validated.")
        }

        val dryRunAdapted = previewCrossDeviceAdaptations(entries)
        adaptationPreview.addAll(dryRunAdapted)

        val status = if (warnings.isNotEmpty() || adaptationPreview.isNotEmpty()) {
            BackupValidationStatus.VALID_WITH_WARNINGS
        } else {
            BackupValidationStatus.VALID
        }

        return BackupPreviewInfo(
            sourceLabel = sourceLabel,
            sourceUriString = sourceUriString,
            sourceFilePath = sourceFilePath,
            manifest = manifest,
            validationStatus = status,
            validationMessage = if (status == BackupValidationStatus.VALID) {
                "Verified • ${manifest.totalSettingsCount} settings ready to restore"
            } else {
                "Verified • Ready to restore (${warnings.size + adaptationPreview.size} compatibility note(s))"
            },
            availableCategories = availableCats,
            warnings = warnings,
            unsupportedKeys = unsupportedKeys,
            adaptedSettingsPreview = adaptationPreview,
            archiveSizeBytes = archiveBytes.size.toLong()
        )
    }

    suspend fun restoreFromPreview(
        preview: BackupPreviewInfo,
        mode: RestoreMode,
        selectedCategories: Set<BackupCategory>,
        supportedPhotoResolutionsOverride: List<String>? = null,
        supportedVideoResolutionsOverride: List<String>? = null,
        onBeforeCommitHookForTest: (() -> Unit)? = null
    ): BackupRestoreReport = withContext(Dispatchers.IO) {
        val targetCategories = if (mode == RestoreMode.COMPLETE) {
            BackupCategory.entries.toSet()
        } else {
            selectedCategories
        }
        if (targetCategories.isEmpty()) {
            return@withContext BackupRestoreReport(
                success = false,
                mode = mode,
                restoredCategories = emptySet(),
                errorMessage = "Select at least one category to restore."
            )
        }

        val archiveBytes: ByteArray = try {
            when {
                !preview.sourceFilePath.isNullOrBlank() -> File(preview.sourceFilePath).readBytes()
                !preview.sourceUriString.isNullOrBlank() -> {
                    val uri = Uri.parse(preview.sourceUriString)
                    context.contentResolver.openInputStream(uri)?.use { readBoundedBytes(it, MAX_ARCHIVE_BYTES) }
                        ?: throw IllegalStateException("Unable to read backup file from URI")
                }
                else -> throw IllegalStateException("No backup source specified")
            }
        } catch (e: Exception) {
            return@withContext BackupRestoreReport(
                success = false,
                mode = mode,
                restoredCategories = emptySet(),
                errorMessage = e.message ?: "Failed to read backup file"
            )
        }

        val verified = readAndVerifyZipPayload(archiveBytes)
        if (verified.errorStatus != null || verified.manifest == null) {
            return@withContext BackupRestoreReport(
                success = false,
                mode = mode,
                restoredCategories = emptySet(),
                errorMessage = verified.errorMessage ?: "Backup verification failed"
            )
        }

        // Create transactional rollback snapshot BEFORE mutating any settings or files
        val prefsSnapshotBefore = preferencesManager.readRawPreferencesSnapshot()
        val supportingFilesSnapshotBefore = snapshotExistingSupportingFiles()

        val adaptedSettings = mutableListOf<String>()
        val skippedSettings = mutableListOf<String>()

        try {
            val filteredEntries = verified.entries.filter { it.category in targetCategories }
            val existingProfiles = preferencesManager.calibrationProfilesFlow.first()
            val existingCorrections = preferencesManager.routeCorrectionsFlow.first()

            val supportingTempRestored = mutableListOf<Pair<String, String>>() // prefKey -> targetAbsolutePath
            val extractedFilesCount = restoreSupportingFilesForSelection(
                targetCategories = targetCategories,
                manifest = verified.manifest,
                supportingPayloads = verified.supportingPayloads,
                entries = filteredEntries,
                outRewrittenPaths = supportingTempRestored,
                adaptedSettings = adaptedSettings
            )
            val rewrittenPathByKey = supportingTempRestored.toMap()

            val supportedPhotoLabels = supportedPhotoResolutionsOverride ?: safeSupportedPhotoLabels()
            val supportedVideoLabels = supportedVideoResolutionsOverride ?: safeSupportedVideoLabels()

            val finalMutations = mutableListOf<TypedPrefEntry>()
            val keysToRemove = mutableSetOf<String>()

            // Inject merged calibration JSON if CHAINAGE_CALIBRATION selected & present
            var restoredProfilesCount = 0
            var restoredControlPointsCount = 0
            if (BackupCategory.CHAINAGE_CALIBRATION in targetCategories && verified.calibrationJson != null) {
                val calMerge = buildMergedCalibrationEntries(
                    calibrationJson = verified.calibrationJson,
                    existingProfiles = existingProfiles,
                    existingCorrections = existingCorrections
                )
                restoredProfilesCount = calMerge.restoredProfileCount
                restoredControlPointsCount = calMerge.restoredPointCount

                filteredEntries.forEach { entry ->
                    when (entry.key) {
                        PreferencesManager.KEY_CALIBRATION_PROFILES_JSON.name -> {
                            finalMutations.add(entry.copy(value = calMerge.mergedProfilesJson))
                        }
                        PreferencesManager.KEY_ROUTE_CORRECTIONS_JSON.name -> {
                            finalMutations.add(entry.copy(value = calMerge.mergedCorrectionsJson))
                        }
                        else -> {
                            processSingleRestoreEntry(
                                entry = entry,
                                rewrittenPathByKey = rewrittenPathByKey,
                                supportedPhotoLabels = supportedPhotoLabels,
                                supportedVideoLabels = supportedVideoLabels,
                                finalMutations = finalMutations,
                                keysToRemove = keysToRemove,
                                adaptedSettings = adaptedSettings,
                                skippedSettings = skippedSettings
                            )
                        }
                    }
                }
                // Ensure merged profiles/points are still applied even if settings.json didn't have raw keys (e.g. schema v0)
                if (finalMutations.none { it.key == PreferencesManager.KEY_CALIBRATION_PROFILES_JSON.name } && calMerge.restoredProfileCount > 0) {
                    finalMutations.add(
                        TypedPrefEntry(
                            key = PreferencesManager.KEY_CALIBRATION_PROFILES_JSON.name,
                            type = PrefValueType.STRING,
                            value = calMerge.mergedProfilesJson,
                            category = BackupCategory.CHAINAGE_CALIBRATION
                        )
                    )
                }
                if (finalMutations.none { it.key == PreferencesManager.KEY_ROUTE_CORRECTIONS_JSON.name } && calMerge.restoredPointCount > 0) {
                    finalMutations.add(
                        TypedPrefEntry(
                            key = PreferencesManager.KEY_ROUTE_CORRECTIONS_JSON.name,
                            type = PrefValueType.STRING,
                            value = calMerge.mergedCorrectionsJson,
                            category = BackupCategory.CHAINAGE_CALIBRATION
                        )
                    )
                }
            } else {
                filteredEntries.forEach { entry ->
                    processSingleRestoreEntry(
                        entry = entry,
                        rewrittenPathByKey = rewrittenPathByKey,
                        supportedPhotoLabels = supportedPhotoLabels,
                        supportedVideoLabels = supportedVideoLabels,
                        finalMutations = finalMutations,
                        keysToRemove = keysToRemove,
                        adaptedSettings = adaptedSettings,
                        skippedSettings = skippedSettings
                    )
                }
            }

            // Apply atomically into DataStore (with rollback on error / simulated interruption)
            preferencesManager.applyBackupEntriesTransaction(
                entries = finalMutations,
                keysToRemove = keysToRemove,
                beforeCommitHook = onBeforeCommitHookForTest
            )

            BackupRestoreReport(
                success = true,
                mode = mode,
                restoredCategories = filteredEntries.map { it.category }.toSet().ifEmpty { targetCategories },
                restoredSettingsCount = finalMutations.size,
                restoredSupportingFilesCount = extractedFilesCount,
                restoredCalibrationProfilesCount = restoredProfilesCount,
                restoredControlPointsCount = restoredControlPointsCount,
                adaptedSettings = adaptedSettings.distinct(),
                skippedSettings = skippedSettings.distinct(),
                rolledBack = false
            )
        } catch (e: Exception) {
            // Automatic rollback to pre-restore snapshot
            try {
                restoreExistingSupportingFilesSnapshot(supportingFilesSnapshotBefore)
                preferencesManager.restoreRawPreferencesSnapshot(prefsSnapshotBefore)
            } catch (_: Exception) {}

            BackupRestoreReport(
                success = false,
                mode = mode,
                restoredCategories = emptySet(),
                rolledBack = true,
                errorMessage = "Restore failed and rolled back cleanly: ${e.message ?: "Unexpected error"}"
            )
        }
    }

    private fun processSingleRestoreEntry(
        entry: TypedPrefEntry,
        rewrittenPathByKey: Map<String, String>,
        supportedPhotoLabels: List<String>,
        supportedVideoLabels: List<String>,
        finalMutations: MutableList<TypedPrefEntry>,
        keysToRemove: MutableSet<String>,
        adaptedSettings: MutableList<String>,
        skippedSettings: MutableList<String>
    ) {
        if (SENSITIVE_KEY_REGEX.containsMatchIn(entry.key)) {
            skippedSettings.add("${entry.key}: stripped for security")
            return
        }

        // Supporting file references
        if (entry.key in SUPPORTING_FILE_KEYS) {
            val localPath = rewrittenPathByKey[entry.key]
            if (!localPath.isNullOrBlank()) {
                finalMutations.add(entry.copy(value = localPath))
            } else {
                keysToRemove.add(entry.key)
                adaptedSettings.add("${entry.key}: bundled file unavailable, cleared broken path")
            }
            return
        }

        // Cross-device validation & bounds clamping
        when (entry.key) {
            PreferencesManager.KEY_CUSTOM_STORAGE_PATH.name -> {
                val raw = entry.value.toString().trim()
                val valid = validateAndAdaptStoragePath(raw)
                if (valid != raw) {
                    adaptedSettings.add("Storage path '$raw' adapted to '$valid'")
                }
                finalMutations.add(entry.copy(value = valid))
            }
            PreferencesManager.KEY_PHOTO_RESOLUTION.name,
            PreferencesManager.KEY_RESOLUTION_OPTION.name -> {
                val raw = entry.value.toString().trim()
                val adapted = validateAndAdaptResolution(raw, supportedPhotoLabels, "1920 x 1080")
                if (adapted != raw) {
                    adaptedSettings.add("Photo resolution '$raw' adapted to '$adapted' for this device")
                }
                finalMutations.add(entry.copy(value = adapted))
            }
            PreferencesManager.KEY_VIDEO_RESOLUTION.name -> {
                val raw = entry.value.toString().trim()
                val adapted = validateAndAdaptResolution(raw, supportedVideoLabels, "1920 x 1080")
                if (adapted != raw) {
                    adaptedSettings.add("Video resolution '$raw' adapted to '$adapted' for this device")
                }
                finalMutations.add(entry.copy(value = adapted))
            }
            PreferencesManager.KEY_THEME_MODE.name -> {
                val raw = entry.value.toString().trim().lowercase(Locale.US)
                val valid = if (raw in setOf("system", "light", "dark")) raw else {
                    adaptedSettings.add("Theme mode '$raw' reset to 'system'")
                    "system"
                }
                finalMutations.add(entry.copy(value = valid))
            }
            PreferencesManager.KEY_MAP_THEME.name -> {
                val raw = entry.value.toString().trim().lowercase(Locale.US)
                val valid = if (raw in setOf("system", "light", "dark")) raw else "system"
                finalMutations.add(entry.copy(value = valid))
            }
            PreferencesManager.KEY_MAP_TYPE.name -> {
                val raw = entry.value.toString().trim().lowercase(Locale.US)
                val valid = if (raw in setOf("standard", "satellite")) raw else "standard"
                finalMutations.add(entry.copy(value = valid))
            }
            PreferencesManager.KEY_JPEG_QUALITY.name -> {
                val v = (entry.value as? Number)?.toInt() ?: 92
                val clamped = v.coerceIn(50, 100)
                if (clamped != v) adaptedSettings.add("JPEG quality $v clamped to $clamped%")
                finalMutations.add(entry.copy(value = clamped))
            }
            PreferencesManager.KEY_GPS_INTERVAL.name -> {
                val v = (entry.value as? Number)?.toInt() ?: 1
                val clamped = v.coerceIn(1, 60)
                if (clamped != v) adaptedSettings.add("GPS interval ${v}s clamped to ${clamped}s")
                finalMutations.add(entry.copy(value = clamped))
            }
            PreferencesManager.KEY_FONT_SIZE_SP.name -> {
                val v = (entry.value as? Number)?.toFloat() ?: 24f
                val clamped = v.coerceIn(10f, 64f)
                if (clamped != v) adaptedSettings.add("Font size ${v}sp clamped to ${clamped}sp")
                finalMutations.add(entry.copy(value = clamped))
            }
            PreferencesManager.KEY_LOGO_OPACITY.name,
            PreferencesManager.KEY_TEXT_OPACITY.name,
            PreferencesManager.KEY_BACKGROUND_OPACITY.name -> {
                val v = (entry.value as? Number)?.toFloat() ?: 1f
                finalMutations.add(entry.copy(value = v.coerceIn(0f, 1f)))
            }
            PreferencesManager.KEY_PHOTO_PADDING_TOP_DP.name,
            PreferencesManager.KEY_PHOTO_PADDING_BOTTOM_DP.name,
            PreferencesManager.KEY_PHOTO_PADDING_LEFT_DP.name,
            PreferencesManager.KEY_PHOTO_PADDING_RIGHT_DP.name,
            PreferencesManager.KEY_VIDEO_PADDING_TOP_DP.name,
            PreferencesManager.KEY_VIDEO_PADDING_BOTTOM_DP.name,
            PreferencesManager.KEY_VIDEO_PADDING_LEFT_DP.name,
            PreferencesManager.KEY_VIDEO_PADDING_RIGHT_DP.name -> {
                val v = (entry.value as? Number)?.toInt() ?: 16
                val clamped = v.coerceIn(0, 120)
                if (clamped != v) adaptedSettings.add("${entry.key} padding ${v}dp clamped to ${clamped}dp")
                finalMutations.add(entry.copy(value = clamped))
            }
            PreferencesManager.KEY_PHOTO_MINI_MAP_JSON.name,
            PreferencesManager.KEY_VIDEO_MINI_MAP_JSON.name -> {
                val rawJson = entry.value.toString()
                val normalized = MiniMapStyleConfig.fromJsonString(rawJson).toJsonString()
                finalMutations.add(entry.copy(value = normalized))
            }
            else -> {
                finalMutations.add(entry)
            }
        }
    }

    suspend fun listBackupHistory(): List<BackupHistoryItem> = withContext(Dispatchers.IO) {
        val dir = backupsDirectory
        val files = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".zip", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        files.map { file ->
            val preview = inspectBackupFile(file)
            BackupHistoryItem(
                fileName = file.name,
                filePath = file.absolutePath,
                createdAtEpochMs = preview.manifest?.createdAtEpochMs?.takeIf { it > 0L } ?: file.lastModified(),
                appVersionName = preview.manifest?.appVersionName ?: "Unknown",
                sizeBytes = file.length(),
                validationStatus = preview.validationStatus,
                validationSummary = preview.validationMessage,
                totalSettingsCount = preview.manifest?.totalSettingsCount ?: 0
            )
        }
    }

    fun deleteBackupFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val canonicalDir = backupsDirectory.canonicalPath
            val canonicalFile = file.canonicalPath
            if (!canonicalFile.startsWith("$canonicalDir${File.separator}")) {
                return false
            }
            file.exists() && file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun getShareUriForBackup(filePath: String): Uri? {
        return try {
            val file = File(filePath)
            val canonicalDir = backupsDirectory.canonicalPath
            if (!file.canonicalPath.startsWith("$canonicalDir${File.separator}") || !file.exists()) {
                return null
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private data class VerifiedZipPayload(
        val manifest: BackupManifest? = null,
        val entries: List<TypedPrefEntry> = emptyList(),
        val calibrationJson: JSONObject? = null,
        val supportingPayloads: Map<String, ByteArray> = emptyMap(),
        val migratedFromLegacySchema: Boolean = false,
        val errorStatus: BackupValidationStatus? = null,
        val errorMessage: String? = null
    )

    private fun readAndVerifyZipPayload(archiveBytes: ByteArray): VerifiedZipPayload {
        if (archiveBytes.isEmpty()) {
            return VerifiedZipPayload(
                errorStatus = BackupValidationStatus.EMPTY_ARCHIVE,
                errorMessage = "Backup file is empty."
            )
        }

        val entryBytesMap = linkedMapOf<String, ByteArray>()
        var totalUncompressed = 0L
        var entryCount = 0

        try {
            ZipInputStream(ByteArrayInputStream(archiveBytes).buffered()).use { zis ->
                var zipEntry: ZipEntry? = zis.nextEntry
                while (zipEntry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        return VerifiedZipPayload(
                            errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                            errorMessage = "Backup exceeds maximum allowed file entries."
                        )
                    }
                    val entryName = zipEntry.name
                    if (!isSafeZipEntryName(entryName)) {
                        return VerifiedZipPayload(
                            errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                            errorMessage = "Blocked unsafe archive path: $entryName"
                        )
                    }
                    if (!zipEntry.isDirectory) {
                        val content = readBoundedBytes(zis, MAX_ENTRY_BYTES)
                            ?: return VerifiedZipPayload(
                                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                                errorMessage = "Entry '$entryName' exceeds maximum safe size."
                            )
                        totalUncompressed += content.size.toLong()
                        if (totalUncompressed > MAX_ARCHIVE_BYTES) {
                            return VerifiedZipPayload(
                                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                                errorMessage = "Uncompressed backup exceeds 50 MB limit."
                            )
                        }
                        entryBytesMap[entryName] = content
                    }
                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return VerifiedZipPayload(
                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                errorMessage = "Not a valid ZIP backup archive: ${e.message ?: "Read error"}"
            )
        }

        if (entryBytesMap.isEmpty()) {
            return VerifiedZipPayload(
                errorStatus = BackupValidationStatus.EMPTY_ARCHIVE,
                errorMessage = "Backup archive contains no data."
            )
        }

        val manifestBytes = entryBytesMap[ENTRY_MANIFEST]
            ?: return VerifiedZipPayload(
                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                errorMessage = "Missing manifest.json inside backup."
            )
        val settingsBytes = entryBytesMap[ENTRY_SETTINGS]
            ?: return VerifiedZipPayload(
                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                errorMessage = "Missing settings.json inside backup."
            )

        val manifest = try {
            BackupManifest.fromJson(JSONObject(manifestBytes.toString(Charsets.UTF_8)))
        } catch (e: Exception) {
            return VerifiedZipPayload(
                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                errorMessage = "Corrupted manifest.json: ${e.message}"
            )
        }

        if (manifest.format != BackupManifest.FORMAT_IDENTIFIER) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                errorMessage = "Unrecognized backup format: '${manifest.format}'"
            )
        }

        if (manifest.formatVersion > BackupManifest.CURRENT_FORMAT_VERSION ||
            manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION
        ) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.UNSUPPORTED_VERSION,
                errorMessage = "Backup uses newer schema v${manifest.schemaVersion} (format v${manifest.formatVersion}). Please update CTECS before restoring."
            )
        }
        if (manifest.schemaVersion < 0 || manifest.formatVersion < 1) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.UNSUPPORTED_VERSION,
                errorMessage = "Invalid backup schema version (${manifest.schemaVersion})."
            )
        }

        // Verify SHA-256 checksums for every file listed + settings/calibration
        val actualChecksums = linkedMapOf<String, String>()
        entryBytesMap.forEach { (name, bytes) ->
            if (name != ENTRY_MANIFEST) {
                actualChecksums[name] = sha256Hex(bytes)
            }
        }
        if (manifest.entryChecksums.isEmpty()) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.CORRUPTED_CHECKSUM,
                errorMessage = "Backup manifest is missing integrity checksums."
            )
        }
        for ((expectedPath, expectedHash) in manifest.entryChecksums) {
            val actualHash = actualChecksums[expectedPath]
            if (actualHash == null || !actualHash.equals(expectedHash, ignoreCase = true)) {
                return VerifiedZipPayload(
                    manifest = manifest,
                    errorStatus = BackupValidationStatus.CORRUPTED_CHECKSUM,
                    errorMessage = "Integrity verification failed for '$expectedPath'. File may be corrupted or modified."
                )
            }
        }
        val expectedManifestIntegrity = computeManifestIntegrityHash(
            schemaVersion = manifest.schemaVersion,
            createdAtIso = manifest.createdAtIso,
            entryChecksums = manifest.entryChecksums
        )
        if (!manifest.manifestIntegrityHash.equals(expectedManifestIntegrity, ignoreCase = true)) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.CORRUPTED_CHECKSUM,
                errorMessage = "Manifest integrity hash mismatch. Backup archive cannot be trusted."
            )
        }

        val settingsJson = try {
            JSONObject(settingsBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                errorMessage = "Corrupted settings.json: ${e.message}"
            )
        }

        val calibrationJson = entryBytesMap[ENTRY_CALIBRATION]?.let { bytes ->
            try {
                JSONObject(bytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                return VerifiedZipPayload(
                    manifest = manifest,
                    errorStatus = BackupValidationStatus.MALFORMED_ARCHIVE,
                    errorMessage = "Corrupted calibration.json: ${e.message}"
                )
            }
        }

        val migrated = manifest.schemaVersion < BackupManifest.CURRENT_SCHEMA_VERSION
        val entries = parseSettingsJson(settingsJson, manifest.schemaVersion)
        if (entries.isEmpty()) {
            return VerifiedZipPayload(
                manifest = manifest,
                errorStatus = BackupValidationStatus.EMPTY_ARCHIVE,
                errorMessage = "Backup contains zero restorable settings."
            )
        }

        val supportingPayloads = entryBytesMap.filterKeys { it.startsWith(FILES_DIR_PREFIX) }

        return VerifiedZipPayload(
            manifest = manifest,
            entries = entries,
            calibrationJson = calibrationJson,
            supportingPayloads = supportingPayloads,
            migratedFromLegacySchema = migrated
        )
    }

    private fun parseSettingsJson(settingsJson: JSONObject, schemaVersion: Int): List<TypedPrefEntry> {
        val result = mutableListOf<TypedPrefEntry>()
        if (schemaVersion == 0) {
            // Migrate legacy v0 flat key-value settings JSON cleanly
            val flatObj = settingsJson.optJSONObject("settings") ?: settingsJson
            val keys = flatObj.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                if (rawKey == "schemaVersion" || SENSITIVE_KEY_REGEX.containsMatchIn(rawKey)) continue
                val value = flatObj.opt(rawKey) ?: continue
                val type = when (value) {
                    is Boolean -> PrefValueType.BOOLEAN
                    is Int -> PrefValueType.INT
                    is Long -> PrefValueType.LONG
                    is Float -> PrefValueType.FLOAT
                    is Double -> PrefValueType.FLOAT
                    is JSONArray -> PrefValueType.STRING_SET
                    else -> PrefValueType.STRING
                }
                val finalVal: Any = if (type == PrefValueType.STRING_SET && value is JSONArray) {
                    buildSet {
                        for (i in 0 until value.length()) add(value.optString(i))
                    }
                } else {
                    value
                }
                result.add(
                    TypedPrefEntry(
                        key = rawKey,
                        type = type,
                        value = finalVal,
                        category = categorizeKey(rawKey)
                    )
                )
            }
            return result
        }

        val categoriesObj = settingsJson.optJSONObject("categories") ?: return emptyList()
        val catKeys = categoriesObj.keys()
        while (catKeys.hasNext()) {
            val catId = catKeys.next()
            val category = BackupCategory.fromId(catId)
            val arr = categoriesObj.optJSONArray(catId) ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val parsed = TypedPrefEntry.fromJson(obj, category) ?: continue
                if (!SENSITIVE_KEY_REGEX.containsMatchIn(parsed.key)) {
                    result.add(parsed)
                }
            }
        }
        return result
    }

    private fun buildSettingsJson(entries: List<TypedPrefEntry>): JSONObject {
        val categoriesObj = JSONObject()
        val grouped = entries.groupBy { it.category }
        BackupCategory.entries.forEach { cat ->
            val list = grouped[cat].orEmpty()
            if (list.isNotEmpty()) {
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                categoriesObj.put(cat.id, arr)
            }
        }
        return JSONObject().apply {
            put("schemaVersion", BackupManifest.CURRENT_SCHEMA_VERSION)
            put("categories", categoriesObj)
        }
    }

    private fun buildCalibrationJson(
        correctionsEnabled: Boolean,
        profiles: List<PermanentCalibrationProfile>,
        corrections: Map<String, List<ChainageCorrectionPoint>>,
        savedCustomChainages: List<String>,
        manualChainageOverride: String?,
        manualChainageCustomText: String?
    ): JSONObject {
        val profilesArr = JSONArray().apply {
            profiles.forEach { put(it.toJson()) }
        }
        val correctionsObj = JSONObject().apply {
            corrections.forEach { (routeId, pts) ->
                val arr = JSONArray()
                pts.forEach { arr.put(it.toJson()) }
                put(routeId, arr)
            }
        }
        val savedArr = JSONArray().apply {
            savedCustomChainages.forEach { put(it) }
        }
        return JSONObject().apply {
            put("schemaVersion", BackupManifest.CURRENT_SCHEMA_VERSION)
            put("routeCorrectionsEnabled", correctionsEnabled)
            put("calibrationProfiles", profilesArr)
            put("routeCorrections", correctionsObj)
            put("savedCustomChainages", savedArr)
            if (!manualChainageOverride.isNullOrBlank()) put("manualChainageOverride", manualChainageOverride)
            if (!manualChainageCustomText.isNullOrBlank()) put("manualChainageCustomText", manualChainageCustomText)
        }
    }

    private data class CalibrationMergeResult(
        val mergedProfilesJson: String,
        val mergedCorrectionsJson: String,
        val restoredProfileCount: Int,
        val restoredPointCount: Int
    )

    private fun buildMergedCalibrationEntries(
        calibrationJson: JSONObject,
        existingProfiles: List<PermanentCalibrationProfile>,
        existingCorrections: Map<String, List<ChainageCorrectionPoint>>
    ): CalibrationMergeResult {
        val importedProfiles = mutableListOf<PermanentCalibrationProfile>()
        val profilesArr = calibrationJson.optJSONArray("calibrationProfiles") ?: JSONArray()
        for (i in 0 until profilesArr.length()) {
            profilesArr.optJSONObject(i)?.let { importedProfiles.add(PermanentCalibrationProfile.fromJson(it)) }
        }

        // Merge profiles preserving all existing device profiles + applying imported profiles cleanly
        val mergedProfiles = existingProfiles.toMutableList()
        importedProfiles.forEach { imp ->
            val idx = mergedProfiles.indexOfFirst {
                it.profileId == imp.profileId ||
                    (it.alignmentFingerprint.isNotBlank() && it.alignmentFingerprint == imp.alignmentFingerprint)
            }
            if (idx >= 0) {
                mergedProfiles[idx] = imp
            } else {
                mergedProfiles.add(imp)
            }
        }

        val importedCorrections = mutableMapOf<String, List<ChainageCorrectionPoint>>()
        val corrObj = calibrationJson.optJSONObject("routeCorrections") ?: JSONObject()
        val routeKeys = corrObj.keys()
        while (routeKeys.hasNext()) {
            val rId = routeKeys.next()
            val arr = corrObj.optJSONArray(rId) ?: continue
            val list = mutableListOf<ChainageCorrectionPoint>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(ChainageCorrectionPoint.fromJson(it)) }
            }
            importedCorrections[rId] = list
        }

        // Merge control points per route without deleting existing control points on target device
        val mergedCorrections = existingCorrections.mapValues { it.value.toMutableList() }.toMutableMap()
        importedCorrections.forEach { (routeId, pts) ->
            val targetList = mergedCorrections.getOrPut(routeId) { mutableListOf() }
            pts.forEach { impPt ->
                val idx = targetList.indexOfFirst { it.id == impPt.id }
                if (idx >= 0) {
                    targetList[idx] = impPt
                } else {
                    targetList.add(impPt)
                }
            }
            targetList.sortBy { it.originalKmzMetres }
        }

        val mergedProfilesJson = JSONArray().apply {
            mergedProfiles.forEach { put(it.toJson()) }
        }.toString()

        val mergedCorrectionsJson = JSONObject().apply {
            mergedCorrections.forEach { (routeId, pts) ->
                val arr = JSONArray()
                pts.forEach { arr.put(it.toJson()) }
                put(routeId, arr)
            }
        }.toString()

        return CalibrationMergeResult(
            mergedProfilesJson = mergedProfilesJson,
            mergedCorrectionsJson = mergedCorrectionsJson,
            restoredProfileCount = importedProfiles.size,
            restoredPointCount = importedCorrections.values.sumOf { it.size }
        )
    }

    private fun restoreSupportingFilesForSelection(
        targetCategories: Set<BackupCategory>,
        manifest: BackupManifest,
        supportingPayloads: Map<String, ByteArray>,
        entries: List<TypedPrefEntry>,
        outRewrittenPaths: MutableList<Pair<String, String>>,
        adaptedSettings: MutableList<String>
    ): Int {
        var count = 0
        val filesDirCanonical = context.filesDir.canonicalPath
        val entryKeys = entries.map { it.key }.toSet()

        manifest.supportingFiles.forEach { spec ->
            if (spec.prefKey !in entryKeys) return@forEach
            if (categorizeKey(spec.prefKey) !in targetCategories) return@forEach
            val bytes = supportingPayloads[spec.zipEntryPath]
            if (bytes == null) {
                adaptedSettings.add("${spec.prefKey}: supporting asset missing in backup")
                return@forEach
            }
            val targetName = targetLocalFileNameForKey(spec.prefKey, spec.originalFileName)
            val targetFile = File(context.filesDir, targetName)
            if (!targetFile.canonicalPath.startsWith("$filesDirCanonical${File.separator}")) {
                adaptedSettings.add("${spec.prefKey}: blocked unsafe file destination")
                return@forEach
            }
            targetFile.outputStream().use { it.write(bytes) }
            outRewrittenPaths.add(spec.prefKey to targetFile.absolutePath)
            count++
        }
        return count
    }

    private fun snapshotExistingSupportingFiles(): Map<String, ByteArray> {
        val snapshot = linkedMapOf<String, ByteArray>()
        val candidates = listOf("custom_user_logo.png", "pdf_default_logo.png", "dpr_signature.png")
        candidates.forEach { name ->
            val f = File(context.filesDir, name)
            if (f.exists() && f.isFile) {
                snapshot[f.absolutePath] = f.readBytes()
            }
        }
        context.filesDir.listFiles { f ->
            f.isFile && (f.name.startsWith("custom_font_") || f.name.startsWith("font_"))
        }?.forEach { fontFile ->
            snapshot[fontFile.absolutePath] = fontFile.readBytes()
        }
        return snapshot
    }

    private fun restoreExistingSupportingFilesSnapshot(snapshot: Map<String, ByteArray>) {
        snapshot.forEach { (path, bytes) ->
            try {
                File(path).outputStream().use { it.write(bytes) }
            } catch (_: Exception) {}
        }
    }

    private fun previewCrossDeviceAdaptations(entries: List<TypedPrefEntry>): List<String> {
        val notes = mutableListOf<String>()
        val supportedPhotos = safeSupportedPhotoLabels()
        val supportedVideos = safeSupportedVideoLabels()

        entries.forEach { entry ->
            when (entry.key) {
                PreferencesManager.KEY_CUSTOM_STORAGE_PATH.name -> {
                    val raw = entry.value.toString().trim()
                    val adapted = validateAndAdaptStoragePath(raw)
                    if (adapted != raw) {
                        notes.add("Custom storage path will be adapted for this device")
                    }
                }
                PreferencesManager.KEY_PHOTO_RESOLUTION.name -> {
                    val raw = entry.value.toString().trim()
                    val adapted = validateAndAdaptResolution(raw, supportedPhotos, "1920 x 1080")
                    if (adapted != raw) {
                        notes.add("Photo resolution '$raw' will adapt to '$adapted'")
                    }
                }
                PreferencesManager.KEY_VIDEO_RESOLUTION.name -> {
                    val raw = entry.value.toString().trim()
                    val adapted = validateAndAdaptResolution(raw, supportedVideos, "1920 x 1080")
                    if (adapted != raw) {
                        notes.add("Video resolution '$raw' will adapt to '$adapted'")
                    }
                }
            }
        }
        return notes.distinct()
    }

    private fun validateAndAdaptStoragePath(raw: String): String {
        val defaultPath = "/storage/emulated/0/DCIM/Camera"
        if (raw.isBlank() || raw.contains("..") || !raw.startsWith("/")) {
            return defaultPath
        }
        if (raw.startsWith("/storage/emulated/0/") || raw.startsWith(context.filesDir.absolutePath)) {
            return raw
        }
        val dir = File(raw)
        return if (dir.exists() && dir.canWrite()) raw else defaultPath
    }

    private fun validateAndAdaptResolution(
        raw: String,
        supportedLabels: List<String>,
        fallback: String
    ): String {
        val parsed = parseResolutionDims(raw) ?: return fallback
        if (parsed.first !in 320..8192 || parsed.second !in 240..8192) {
            return fallback
        }
        if (supportedLabels.isEmpty()) {
            return "${parsed.first} x ${parsed.second}"
        }
        val exact = supportedLabels.firstOrNull { it.equals(raw, ignoreCase = true) }
        if (exact != null) return exact

        val targetArea = parsed.first.toLong() * parsed.second.toLong()
        val withDims = supportedLabels.mapNotNull { label ->
            parseResolutionDims(label)?.let { dims -> label to (dims.first.toLong() * dims.second.toLong()) }
        }
        if (withDims.isEmpty()) return fallback

        val notExceeding = withDims.filter { it.second <= targetArea }.maxByOrNull { it.second }
        return notExceeding?.first ?: withDims.minByOrNull { kotlin.math.abs(it.second - targetArea) }?.first ?: fallback
    }

    private fun parseResolutionDims(label: String): Pair<Int, Int>? {
        val parts = label.lowercase(Locale.US).split("x").map { it.trim().toIntOrNull() }
        if (parts.size != 2) return null
        val w = parts[0] ?: return null
        val h = parts[1] ?: return null
        return w to h
    }

    private fun safeSupportedPhotoLabels(): List<String> = try {
        CameraResolutionHelper.getSupportedPhotoResolutions(context, false).map { it.label }
    } catch (_: Exception) {
        emptyList()
    }

    private fun safeSupportedVideoLabels(): List<String> = try {
        CameraResolutionHelper.getSupportedVideoResolutions(context, false).map { it.label }
    } catch (_: Exception) {
        emptyList()
    }

    private fun targetLocalFileNameForKey(prefKey: String, originalFileName: String): String {
        val sanitizedOrig = originalFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return when (prefKey) {
            PreferencesManager.KEY_LOGO_URI.name -> "custom_user_logo.png"
            PreferencesManager.KEY_PDF_LOGO_URI.name -> "pdf_default_logo.png"
            PreferencesManager.KEY_PDF_SIGNATURE_URI.name -> "dpr_signature.png"
            PreferencesManager.KEY_CUSTOM_FONT_PATH.name -> "custom_font_$sanitizedOrig"
            else -> "restored_$sanitizedOrig"
        }
    }

    private fun sanitizeSupportFileName(prefKey: String, originalFileName: String): String {
        val ext = originalFileName.substringAfterLast('.', "").replace("[^a-zA-Z0-9]".toRegex(), "")
        val suffix = if (ext.isNotBlank()) ".$ext" else ".bin"
        val cleanBase = when (prefKey) {
            PreferencesManager.KEY_LOGO_URI.name -> "custom_user_logo"
            PreferencesManager.KEY_PDF_LOGO_URI.name -> "pdf_default_logo"
            PreferencesManager.KEY_PDF_SIGNATURE_URI.name -> "dpr_signature"
            PreferencesManager.KEY_CUSTOM_FONT_PATH.name -> "custom_font"
            else -> prefKey.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        }
        return "$cleanBase$suffix"
    }

    private fun isSafeZipEntryName(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains("..") || name.contains('\u0000')) {
            return false
        }
        if (name == ENTRY_MANIFEST || name == ENTRY_SETTINGS || name == ENTRY_CALIBRATION) {
            return true
        }
        if (name.startsWith(FILES_DIR_PREFIX)) {
            val sub = name.removePrefix(FILES_DIR_PREFIX)
            return sub.isNotBlank() && !sub.contains("/")
        }
        return false
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Long): ByteArray? {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            baos.write(buffer, 0, read)
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun resolveUniqueBackupFileName(epochMs: Long): String {
        val base = generateDefaultBackupFileName(epochMs)
        var candidate = File(backupsDirectory, base)
        if (!candidate.exists()) return base
        val stem = base.removeSuffix(".zip")
        var counter = 1
        while (candidate.exists() && counter < 100) {
            val alt = "${stem}_$counter.zip"
            candidate = File(backupsDirectory, alt)
            if (!candidate.exists()) return alt
            counter++
        }
        return "${stem}_${System.currentTimeMillis() % 10000}.zip"
    }

    private fun resolveUriDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return c.getString(idx)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
    }

    private fun serializeDefinedProjects(projects: List<DefinedProject>): String {
        val arr = JSONArray()
        projects.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("stretch", p.stretch)
                put("workType", p.workType)
                put("startChainage", p.startChainage)
                put("endChainage", p.endChainage)
            })
        }
        return arr.toString()
    }

    private fun formatIsoTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeManifestIntegrityHash(
        schemaVersion: Int,
        createdAtIso: String,
        entryChecksums: Map<String, String>
    ): String {
        val canonical = buildString {
            append(schemaVersion).append('|').append(createdAtIso)
            entryChecksums.toSortedMap().forEach { (k, v) ->
                append('|').append(k).append('=').append(v)
            }
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }
}
