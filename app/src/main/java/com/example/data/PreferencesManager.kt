package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ctecs_user_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_HIGHWAY_ID = stringPreferencesKey("highway_id")
        val KEY_PACKAGE_SECTION = stringPreferencesKey("package_section")
        val KEY_ROAD_SIDE = stringPreferencesKey("road_side")
        val KEY_LANE = stringPreferencesKey("lane")
        val KEY_ACTIVITY = stringPreferencesKey("activity")
        val KEY_DESCRIPTION = stringPreferencesKey("description")
        val KEY_INSPECTOR = stringPreferencesKey("inspector")
        val KEY_SEPARATOR = stringPreferencesKey("separator")

        val KEY_TEMPLATE = stringPreferencesKey("overlay_template")
        val KEY_TIMESTAMP_FORMAT = stringPreferencesKey("timestamp_format")
        val KEY_COORDINATE_PRECISION = intPreferencesKey("coordinate_precision")
        val KEY_SHOW_TIMESTAMP = booleanPreferencesKey("show_timestamp")
        val KEY_SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val KEY_SHOW_CHAINAGE = booleanPreferencesKey("show_chainage")
        val KEY_SHOW_MAP_INSET = booleanPreferencesKey("show_map_inset")
        val KEY_SHOW_PROJECT_INFO = booleanPreferencesKey("show_project_info")
        val KEY_SHOW_COMPASS = booleanPreferencesKey("show_compass")
        val KEY_SHOW_ALTITUDE = booleanPreferencesKey("show_altitude")
        val KEY_SHOW_ACCURACY = booleanPreferencesKey("show_accuracy")
        val KEY_BURN_IN_TIMESTAMP = booleanPreferencesKey("burn_in_timestamp")
        val KEY_AUTO_ROTATE_UI = booleanPreferencesKey("auto_rotate_ui")

        // Logo manual settings keys
        val KEY_SHOW_LOGO = booleanPreferencesKey("show_logo")
        val KEY_LOGO_PRESET = stringPreferencesKey("logo_preset")
        val KEY_LOGO_URI = stringPreferencesKey("logo_uri")
        val KEY_LOGO_POSITION = stringPreferencesKey("logo_position")
        val KEY_LOGO_SIZE_DP = intPreferencesKey("logo_size_dp")
        val KEY_LOGO_OPACITY = floatPreferencesKey("logo_opacity")
        val KEY_LOGO_SHAPE = stringPreferencesKey("logo_shape")
        val KEY_LOGO_CORNER_RADIUS_DP = intPreferencesKey("logo_corner_radius_dp")
        val KEY_LOGO_BORDER_WIDTH_DP = intPreferencesKey("logo_border_width_dp")
        val KEY_LOGO_BORDER_COLOR_HEX = stringPreferencesKey("logo_border_color_hex")
        val KEY_LOGO_MARGIN_X_DP = intPreferencesKey("logo_margin_x_dp")
        val KEY_LOGO_MARGIN_Y_DP = intPreferencesKey("logo_margin_y_dp")

        val KEY_FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val KEY_HAS_TEXT_SHADOW = booleanPreferencesKey("has_text_shadow")
        val KEY_ACTIVE_ROUTE_ID = stringPreferencesKey("active_route_id")

        // Camera settings keys
        val KEY_CAPTURE_TIMER = intPreferencesKey("camera_capture_timer")
        val KEY_GRID_MODE = stringPreferencesKey("camera_grid_mode")
        val KEY_SHOW_HORIZON_LEVEL = booleanPreferencesKey("camera_horizon_level")
        val KEY_PREVIEW_RATIO = stringPreferencesKey("camera_preview_ratio")
        val KEY_CAPTURE_SOUND = booleanPreferencesKey("camera_capture_sound")
        val KEY_VOLUME_SHUTTER = booleanPreferencesKey("camera_volume_shutter")
        val KEY_FRONT_MIRROR = booleanPreferencesKey("camera_front_mirror")
        val KEY_JPEG_QUALITY = intPreferencesKey("camera_jpeg_quality")
        val KEY_SAVE_ORIGINAL_AND_STAMPED = booleanPreferencesKey("camera_save_original_and_stamped")
        val KEY_PRESERVE_EXIF = booleanPreferencesKey("camera_preserve_exif")
        val KEY_VIDEO_QUALITY = stringPreferencesKey("camera_video_quality")
        val KEY_VIDEO_FPS = intPreferencesKey("camera_video_fps")
        val KEY_RECORD_AUDIO = booleanPreferencesKey("camera_record_audio")
        val KEY_GPS_INTERVAL = intPreferencesKey("camera_gps_interval")
        val KEY_GPS_ACCURACY_THRESHOLD = floatPreferencesKey("camera_gps_accuracy_threshold")
        val KEY_ROUTE_RADIUS = floatPreferencesKey("camera_route_radius")
        val KEY_COORD_FORMAT = stringPreferencesKey("camera_coord_format")
        val KEY_IS_24H = booleanPreferencesKey("camera_is_24h")
        val KEY_EXPOSURE_COMP = intPreferencesKey("camera_exposure_comp")
        val KEY_CAMERA_MODE = stringPreferencesKey("camera_mode")
        val KEY_SPEED_UNIT = stringPreferencesKey("speed_unit")
    }

    val cameraSettingsFlow: Flow<com.example.model.CameraSettings> = context.dataStore.data.map { prefs ->
        com.example.model.CameraSettings(
            captureTimer = com.example.model.CaptureTimer.fromSeconds(prefs[KEY_CAPTURE_TIMER] ?: 0),
            gridMode = com.example.model.CameraGridMode.fromString(prefs[KEY_GRID_MODE]),
            showHorizonLevel = prefs[KEY_SHOW_HORIZON_LEVEL] ?: true,
            previewRatio = com.example.model.PreviewRatio.fromString(prefs[KEY_PREVIEW_RATIO]),
            captureSound = prefs[KEY_CAPTURE_SOUND] ?: true,
            volumeButtonShutter = prefs[KEY_VOLUME_SHUTTER] ?: true,
            frontCameraMirror = prefs[KEY_FRONT_MIRROR] ?: false,
            jpegQuality = prefs[KEY_JPEG_QUALITY] ?: 92,
            saveOriginalAndStamped = prefs[KEY_SAVE_ORIGINAL_AND_STAMPED] ?: false,
            preserveExif = prefs[KEY_PRESERVE_EXIF] ?: true,
            videoQuality = com.example.model.VideoQualityOption.fromString(prefs[KEY_VIDEO_QUALITY]),
            videoFps = prefs[KEY_VIDEO_FPS] ?: 30,
            recordAudio = prefs[KEY_RECORD_AUDIO] ?: true,
            gpsUpdateIntervalSec = prefs[KEY_GPS_INTERVAL] ?: 1,
            gpsAccuracyThresholdMeters = prefs[KEY_GPS_ACCURACY_THRESHOLD] ?: 15f,
            routeMatchingRadiusMeters = (prefs[KEY_ROUTE_RADIUS] ?: 100f).toDouble(),
            coordinateFormat = com.example.model.CoordinateFormat.fromString(prefs[KEY_COORD_FORMAT]),
            is24HourFormat = prefs[KEY_IS_24H] ?: false,
            exposureCompensationStep = prefs[KEY_EXPOSURE_COMP] ?: 0,
            cameraMode = com.example.model.CameraMode.fromString(prefs[KEY_CAMERA_MODE]),
            speedUnit = com.example.model.SpeedUnit.fromString(prefs[KEY_SPEED_UNIT])
        )
    }

    val projectProfileFlow: Flow<ProjectProfile> = context.dataStore.data.map { prefs ->
        ProjectProfile(
            highwayId = prefs[KEY_HIGHWAY_ID] ?: "MH",
            packageSection = prefs[KEY_PACKAGE_SECTION] ?: "MM",
            roadSide = RoadSide.fromString(prefs[KEY_ROAD_SIDE]),
            lane = prefs[KEY_LANE] ?: "I/L",
            activity = prefs[KEY_ACTIVITY] ?: "DBM",
            customDescription = prefs[KEY_DESCRIPTION] ?: "",
            inspectorName = prefs[KEY_INSPECTOR] ?: "",
            separator = prefs[KEY_SEPARATOR] ?: "||"
        )
    }

    val overlayConfigFlow: Flow<OverlayConfig> = context.dataStore.data.map { prefs ->
        val templateName = prefs[KEY_TEMPLATE] ?: OverlayTemplate.HIGHWAY_CONSTRUCTION.name
        val template = try { OverlayTemplate.valueOf(templateName) } catch (e: Exception) { OverlayTemplate.HIGHWAY_CONSTRUCTION }
        OverlayConfig(
            template = template,
            timestampFormat = prefs[KEY_TIMESTAMP_FORMAT] ?: "dd MMMM yyyy h:mm a",
            coordinatePrecision = prefs[KEY_COORDINATE_PRECISION] ?: 4,
            showTimestamp = prefs[KEY_SHOW_TIMESTAMP] ?: true,
            showCoordinates = prefs[KEY_SHOW_COORDINATES] ?: true,
            showChainage = prefs[KEY_SHOW_CHAINAGE] ?: true,
            showMapInset = prefs[KEY_SHOW_MAP_INSET] ?: true,
            showProjectInfo = prefs[KEY_SHOW_PROJECT_INFO] ?: true,
            showCompass = prefs[KEY_SHOW_COMPASS] ?: true,
            showAltitude = prefs[KEY_SHOW_ALTITUDE] ?: true,
            showAccuracy = prefs[KEY_SHOW_ACCURACY] ?: true,
            burnInTimestamp = prefs[KEY_BURN_IN_TIMESTAMP] ?: true,

            // Logo manual settings
            showLogo = prefs[KEY_SHOW_LOGO] ?: true,
            logoPreset = prefs[KEY_LOGO_PRESET] ?: "cubetech",
            logoUri = prefs[KEY_LOGO_URI],
            logoPosition = LogoPosition.fromString(prefs[KEY_LOGO_POSITION]),
            logoSizeDp = prefs[KEY_LOGO_SIZE_DP] ?: 58,
            logoOpacity = prefs[KEY_LOGO_OPACITY] ?: 0.90f,
            logoShape = LogoShape.fromString(prefs[KEY_LOGO_SHAPE]),
            logoCornerRadiusDp = prefs[KEY_LOGO_CORNER_RADIUS_DP] ?: 10,
            logoBorderWidthDp = prefs[KEY_LOGO_BORDER_WIDTH_DP] ?: 0,
            logoBorderColorHex = prefs[KEY_LOGO_BORDER_COLOR_HEX] ?: "#FFFFFF",
            logoMarginXDp = prefs[KEY_LOGO_MARGIN_X_DP] ?: 16,
            logoMarginYDp = prefs[KEY_LOGO_MARGIN_Y_DP] ?: 20,

            fontSizeSp = prefs[KEY_FONT_SIZE_SP] ?: 24f,
            hasTextShadow = prefs[KEY_HAS_TEXT_SHADOW] ?: true
        )
    }

    val activeRouteIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ROUTE_ID]
    }

    suspend fun updateProjectProfile(profile: ProjectProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGHWAY_ID] = profile.highwayId
            prefs[KEY_PACKAGE_SECTION] = profile.packageSection
            prefs[KEY_ROAD_SIDE] = profile.roadSide.name
            prefs[KEY_LANE] = profile.lane
            prefs[KEY_ACTIVITY] = profile.activity
            prefs[KEY_DESCRIPTION] = profile.customDescription
            prefs[KEY_INSPECTOR] = profile.inspectorName
            prefs[KEY_SEPARATOR] = profile.separator
        }
    }

    suspend fun updateOverlayConfig(config: OverlayConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEMPLATE] = config.template.name
            prefs[KEY_TIMESTAMP_FORMAT] = config.timestampFormat
            prefs[KEY_COORDINATE_PRECISION] = config.coordinatePrecision
            prefs[KEY_SHOW_TIMESTAMP] = config.showTimestamp
            prefs[KEY_SHOW_COORDINATES] = config.showCoordinates
            prefs[KEY_SHOW_CHAINAGE] = config.showChainage
            prefs[KEY_SHOW_MAP_INSET] = config.showMapInset
            prefs[KEY_SHOW_PROJECT_INFO] = config.showProjectInfo
            prefs[KEY_SHOW_COMPASS] = config.showCompass
            prefs[KEY_SHOW_ALTITUDE] = config.showAltitude
            prefs[KEY_SHOW_ACCURACY] = config.showAccuracy
            prefs[KEY_BURN_IN_TIMESTAMP] = config.burnInTimestamp

            // Logo settings
            prefs[KEY_SHOW_LOGO] = config.showLogo
            prefs[KEY_LOGO_PRESET] = config.logoPreset
            if (config.logoUri != null) {
                prefs[KEY_LOGO_URI] = config.logoUri
            } else {
                prefs.remove(KEY_LOGO_URI)
            }
            prefs[KEY_LOGO_POSITION] = config.logoPosition.name
            prefs[KEY_LOGO_SIZE_DP] = config.logoSizeDp
            prefs[KEY_LOGO_OPACITY] = config.logoOpacity
            prefs[KEY_LOGO_SHAPE] = config.logoShape.name
            prefs[KEY_LOGO_CORNER_RADIUS_DP] = config.logoCornerRadiusDp
            prefs[KEY_LOGO_BORDER_WIDTH_DP] = config.logoBorderWidthDp
            prefs[KEY_LOGO_BORDER_COLOR_HEX] = config.logoBorderColorHex
            prefs[KEY_LOGO_MARGIN_X_DP] = config.logoMarginXDp
            prefs[KEY_LOGO_MARGIN_Y_DP] = config.logoMarginYDp

            prefs[KEY_FONT_SIZE_SP] = config.fontSizeSp
            prefs[KEY_HAS_TEXT_SHADOW] = config.hasTextShadow
        }
    }

    suspend fun updateCameraSettings(settings: com.example.model.CameraSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CAPTURE_TIMER] = settings.captureTimer.seconds
            prefs[KEY_GRID_MODE] = settings.gridMode.name
            prefs[KEY_SHOW_HORIZON_LEVEL] = settings.showHorizonLevel
            prefs[KEY_PREVIEW_RATIO] = settings.previewRatio.name
            prefs[KEY_CAPTURE_SOUND] = settings.captureSound
            prefs[KEY_VOLUME_SHUTTER] = settings.volumeButtonShutter
            prefs[KEY_FRONT_MIRROR] = settings.frontCameraMirror
            prefs[KEY_JPEG_QUALITY] = settings.jpegQuality
            prefs[KEY_SAVE_ORIGINAL_AND_STAMPED] = settings.saveOriginalAndStamped
            prefs[KEY_PRESERVE_EXIF] = settings.preserveExif
            prefs[KEY_VIDEO_QUALITY] = settings.videoQuality.name
            prefs[KEY_VIDEO_FPS] = settings.videoFps
            prefs[KEY_RECORD_AUDIO] = settings.recordAudio
            prefs[KEY_GPS_INTERVAL] = settings.gpsUpdateIntervalSec
            prefs[KEY_GPS_ACCURACY_THRESHOLD] = settings.gpsAccuracyThresholdMeters
            prefs[KEY_ROUTE_RADIUS] = settings.routeMatchingRadiusMeters.toFloat()
            prefs[KEY_COORD_FORMAT] = settings.coordinateFormat.name
            prefs[KEY_IS_24H] = settings.is24HourFormat
            prefs[KEY_EXPOSURE_COMP] = settings.exposureCompensationStep
            prefs[KEY_CAMERA_MODE] = settings.cameraMode.name
            prefs[KEY_SPEED_UNIT] = settings.speedUnit.name
        }
    }

    suspend fun resetAllSettings() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun setActiveRouteId(routeId: String?) {
        context.dataStore.edit { prefs ->
            if (routeId != null) {
                prefs[KEY_ACTIVE_ROUTE_ID] = routeId
            } else {
                prefs.remove(KEY_ACTIVE_ROUTE_ID)
            }
        }
    }
}
